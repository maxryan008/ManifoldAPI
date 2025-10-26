package dev.manifold.physics.collision;

import dev.manifold.physics.core.OBB;
import dev.manifold.physics.core.RigidState;
import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla-compatible: returns a motion vector (never increases vanilla), after
 * colliding the player's OBB(s) against any nearby constructs with CCD, friction,
 * and platform-relative motion.  Ships' OBBs are kept in LOCAL space and transformed
 * to WORLD at the exact TOI inside the engine.
 */
public final class ConstructCollisionEngine {

    private static final double TICK_DT = 1.0 / 20.0;

    public static Vec3 resolveCollisions(Vec3 vanillaResolved, Entity self, AABB startBB, float stepHeight) {
        // 0) Preserve identity when vanilla gave zero
        if (vanillaResolved.lengthSqr() <= 1e-18) return vanillaResolved;

        final double dt = TICK_DT;

        // 1) Player OBB at end pose (we’ll project from here)
        OBB playerEnd = playerObbFromAabb(startBB.move(vanillaResolved.x, vanillaResolved.y, vanillaResolved.z));

        // 2) Nearby constructs
        AABB swept = startBB.expandTowards(vanillaResolved.x, vanillaResolved.y, vanillaResolved.z);
        List<ConstructCollisionManager.ConstructRef> near = ConstructCollisionManager.queryNearby(self.level(), swept);
        if (near.isEmpty()) return vanillaResolved;

        // 3) Build end-of-tick world poses for constructs
        List<WorldConstruct> worldTargets = new ArrayList<>(near.size());
        for (ConstructCollisionManager.ConstructRef ref : near) {
            RigidState endPose = integratePose(ref.state(), dt);
            worldTargets.add(new WorldConstruct(ref, endPose));
        }

        // 4) Platform carry (unchanged logic; includes rotation carry)
        V3 platformCarry = estimatePlatformCarry(playerEnd, worldTargets, dt);
        if (platformCarry.len2() > 0) {
            playerEnd.c.add(platformCarry);
        }

        // 5) Gather SDF contacts (entity vs each construct)
        ArrayList<Contact> cons = new ArrayList<>(32);
        for (WorldConstruct wc : worldTargets) {
            gatherSdfContactsEntityVsConstruct(playerEnd, wc, cons);
        }

        // 6) Min-norm push d that satisfies: n_i · d >= depth_i  (no magnitude bias)
        V3 d = solveMinNormFromContacts(cons);

        // 7) Compose final result = vanilla + carry + push
        double rx = vanillaResolved.x + platformCarry.x + d.x;
        double ry = vanillaResolved.y + platformCarry.y + d.y;
        double rz = vanillaResolved.z + platformCarry.z + d.z;

        // 8) Preserve Y identity if numerically equal to avoid vanilla vertical reset
        if (Math.abs(ry - vanillaResolved.y) <= 1e-12
                && Math.abs(platformCarry.y) <= 1e-12
                && Math.abs(d.y) <= 1e-12) {
            ry = vanillaResolved.y;
        }

        // 9) If unchanged, return original instance
        if (rx == vanillaResolved.x && ry == vanillaResolved.y && rz == vanillaResolved.z) {
            return vanillaResolved;
        }
        return new Vec3(rx, ry, rz);
    }

    /** Contact constraint primitive for min-norm projection. */
    private static final class Contact {
        final V3 n;      // world normal, A->B
        final double d;  // penetration depth (>=0)
        Contact(V3 n, double d){ this.n = n; this.d = d; }
    }

    /** Sample corners + face-centers of the player OBB against a construct’s SDF and emit contacts. */
    private static void gatherSdfContactsEntityVsConstruct(OBB playerEnd,
                                                           WorldConstruct wc,
                                                           List<Contact> out)
    {
        if (wc.ref.sdf() == null) return;

        // Sample set on player OBB
        ArrayList<V3> pts = new ArrayList<>(14);
        int[] S = { -1, +1 };
        for (int sx : S) for (int sy : S) for (int sz : S)
            pts.add(new V3(playerEnd.c.x + sx*playerEnd.e.x,
                    playerEnd.c.y + sy*playerEnd.e.y,
                    playerEnd.c.z + sz*playerEnd.e.z));
        pts.add(new V3(playerEnd.c.x + playerEnd.e.x, playerEnd.c.y,                playerEnd.c.z               ));
        pts.add(new V3(playerEnd.c.x - playerEnd.e.x, playerEnd.c.y,                playerEnd.c.z               ));
        pts.add(new V3(playerEnd.c.x,                 playerEnd.c.y + playerEnd.e.y,playerEnd.c.z               ));
        pts.add(new V3(playerEnd.c.x,                 playerEnd.c.y - playerEnd.e.y,playerEnd.c.z               ));
        pts.add(new V3(playerEnd.c.x,                 playerEnd.c.y,                playerEnd.c.z + playerEnd.e.z));
        pts.add(new V3(playerEnd.c.x,                 playerEnd.c.y,                playerEnd.c.z - playerEnd.e.z));

        // World->Local transforms
        VoxelSDF sdf = wc.ref.sdf();
        M3 R = wc.endPose.rot;      // world-from-local
        M3 Rt = R.transpose();      // local-from-world
        V3 pos = wc.endPose.pos;
        V3 o   = wc.ref.localOrigin();

        for (V3 xW : pts) {
            V3 xWL = V3.sub(xW, pos);
            V3 xL  = Rt.mul(xWL).add(o);

            double phi = sdf.sample(xL);
            if (phi >= 0.0) continue; // outside

            V3 gL = sdf.grad(xL);
            if (gL.len2() < 1e-12) continue;
            V3 nW = R.mul(gL).nor();           // A->B world normal

            double depth = -phi;
            out.add(new Contact(nW, depth));
        }
    }

    /** Min-norm solution: find d with minimum ||d|| s.t. n_i·d >= depth_i for all contacts. */
    private static V3 solveMinNormFromContacts(List<Contact> cons){
        if (cons.isEmpty()) return new V3();

        // Active-set with tiny systems: grow by most violated, solve normal equations, project.
        final int MAX_PASSES = 8;

        // active set storage
        ArrayList<Contact> active = new ArrayList<>(8);
        V3 d = new V3();

        for (int pass=0; pass<MAX_PASSES; pass++){
            // Find most violated
            double maxGap = 0.0; int worst = -1;
            for (int i=0;i<cons.size();i++){
                Contact c = cons.get(i);
                double gap = c.d - c.n.dot(d);
                if (gap > maxGap + 1e-9){ maxGap = gap; worst = i; }
            }
            if (worst < 0) break; // all satisfied

            // Add if not parallel duplicate
            Contact add = cons.get(worst);
            boolean dup = false;
            for (Contact a : active){ if (a.n.dot(add.n) > 0.999) { dup=true; break; } }
            if (!dup) active.add(add);

            // Solve (M M^T) λ = depths, d = M^T λ
            int k = active.size();
            double[][] G = new double[k][k];
            double[]    b = new double[k];
            for (int i=0;i<k;i++){
                b[i] = active.get(i).d;
                for (int j=0;j<k;j++){
                    G[i][j] = active.get(i).n.dot(active.get(j).n);
                }
                G[i][i] += 1e-8; // regularization
            }
            double[] lam = gaussianSolve(G, b);

            // If any λ < 0, drop the most negative (KKT enforcement) and recompute.
            int guard=0;
            while (hasNegative(lam) && guard++ < k+2) {
                int worstIdx = argMostNegative(lam);
                active.remove(worstIdx);
                k = active.size();
                if (k==0){ d = new V3(); break; }

                G = new double[k][k];
                b = new double[k];
                for (int i=0;i<k;i++){
                    b[i] = active.get(i).d;
                    for (int j=0;j<k;j++){
                        G[i][j] = active.get(i).n.dot(active.get(j).n);
                    }
                    G[i][i] += 1e-8;
                }
                lam = gaussianSolve(G, b);
            }

            // Build d
            d = new V3();
            for (int i=0;i<active.size();i++){
                d.add( V3.scl(active.get(i).n.cpy(), lam[i]) );
            }
        }
        return d;
    }

    /** Tiny dense Gaussian solver (with partial pivot). */
    private static double[] gaussianSolve(double[][] A, double[] b){
        int n = b.length;
        double[][] M = new double[n][n+1];
        for (int i=0;i<n;i++){
            System.arraycopy(A[i], 0, M[i], 0, n);
            M[i][n] = b[i];
        }
        int row=0;
        for (int col=0; col<n && row<n; col++){
            int piv=row;
            for (int r=row+1;r<n;r++) if (Math.abs(M[r][col]) > Math.abs(M[piv][col])) piv=r;
            if (Math.abs(M[piv][col]) < 1e-12) continue;
            double[] tmp = M[row]; M[row]=M[piv]; M[piv]=tmp;

            double inv = 1.0 / M[row][col];
            for (int c=col;c<=n;c++) M[row][c] *= inv;

            for (int r=0;r<n;r++){
                if (r==row) continue;
                double f = M[r][col];
                if (Math.abs(f) < 1e-20) continue;
                for (int c=col;c<=n;c++) M[r][c] -= f * M[row][c];
            }
            row++;
        }
        double[] x = new double[n];
        for (int i=0;i<n;i++) x[i] = M[i][n];
        return x;
    }
    private static boolean hasNegative(double[] a){ for (double v : a) if (v < -1e-9) return true; return false; }
    private static int argMostNegative(double[] a){ int idx=0; double mn=a[0]; for (int i=1;i<a.length;i++){ if (a[i] < mn){ mn=a[i]; idx=i; } } return idx; }

    private static RigidState cloneState(RigidState s){
        RigidState r = new RigidState();
        r.set(s.pos.cpy(), new M3().set(s.rot.c0, s.rot.c1, s.rot.c2),
                s.vLin.cpy(), s.vAng.cpy(), s.invMass, s.invInertiaWorld);
        return r;
    }

    private static OBB playerObbFromAabb(AABB bb) {
        OBB o = new OBB();
        o.c = aabbCenter(bb);
        o.e = new V3((bb.maxX - bb.minX) * 0.5, (bb.maxY - bb.minY) * 0.5, (bb.maxZ - bb.minZ) * 0.5);
        o.R = M3.identity(); // player box axis-aligned for now
        o.mu = 0.8;
        return o;
    }

    private static V3 aabbCenter(AABB bb) {
        return new V3((bb.minX + bb.maxX) * 0.5, (bb.minY + bb.maxY) * 0.5, (bb.minZ + bb.maxZ) * 0.5);
        // could add: + small epsilon tweaks if Minecraft AABB has odd rounding
    }

    /** End-of-tick small-angle integration for a kinematic/rigid pose. */
    private static RigidState integratePose(RigidState s, double dt) {
        RigidState r = cloneState(s);
        // translate by v*dt
        r.pos.add(V3.scl(r.vLin.cpy(), dt));
        // small-angle rotate basis by ω×axis*dt
        if (r.vAng.len2() > 1e-18) {
            V3 wx = r.vAng;
            V3 X = r.rot.c0.cpy(), Y = r.rot.c1.cpy(), Z = r.rot.c2.cpy();
            r.rot.c0.add(wx.crs(X).scl(dt)).nor();
            r.rot.c1.add(wx.crs(Y).scl(dt)).nor();
            r.rot.c2.add(wx.crs(Z).scl(dt)).nor();
        }
        return r;
    }

    /** Carry from constructs touching/under the player at end pose. Includes linear + rotational. */
    private static V3 estimatePlatformCarry(OBB playerEnd, List<WorldConstruct> worldTargets, double dt) {
        // Consider support when the MTV (A->B normal) points sufficiently downward.
        // Using -0.2 handles rotated/sloped platforms reliably.
        final double DOWN_THRESHOLD = -0.2;

        V3 sum = new V3();
        int cnt = 0;

        for (WorldConstruct wc : worldTargets) {
            // very cheap broad check
            if (V3.sub(wc.centerApprox(), playerEnd.c).len2() > 256.0) continue;

            for (OBB bLocal : wc.ref.localObbs()) {
                OBB bWorld = obbAtPoseB_local(bLocal, wc.endPose, wc.ref.localOrigin());
                dev.manifold.physics.narrowphase.ObbSat.OverlapResult r =
                        dev.manifold.physics.narrowphase.ObbSat.test(playerEnd, bWorld);
                if (!r.overlaps || r.depth <= 1e-9) continue;

                if (r.nWorld.y < DOWN_THRESHOLD) {
                    // Evaluate platform velocity at player center (good enough for carry)
                    V3 rB = V3.sub(playerEnd.c, wc.endPose.pos);
                    V3 vPoint = V3.add(wc.endPose.vLin.cpy(), wc.endPose.vAng.cpy().crs(rB));
                    sum.add(vPoint);
                    cnt++;
                    break; // one OBB per construct is enough for carry
                }
            }
        }

        if (cnt == 0) return new V3();
        sum.scl(1.0 / cnt);
        return V3.scl(sum, dt); // return displacement for this tick
    }

    /** Transform B’s LOCAL OBB by its world pose and local origin: world = pos + R*(local - origin). */
    private static OBB obbAtPoseB_local(OBB local, RigidState pose, V3 localOrigin){
        OBB w = new OBB();

        // world center = pos + R * (local.c - origin)
        V3 lc = V3.sub(local.c, localOrigin);
        V3 wx = pose.rot.c0, wy = pose.rot.c1, wz = pose.rot.c2;
        V3 wc = new V3(
                pose.pos.x + wx.x*lc.x + wy.x*lc.y + wz.x*lc.z,
                pose.pos.y + wx.y*lc.x + wy.y*lc.y + wz.y*lc.z,
                pose.pos.z + wx.z*lc.x + wy.z*lc.y + wz.z*lc.z
        );

        // world rotation = pose.rot * local.R
        V3 lx = local.R.c0, ly = local.R.c1, lz = local.R.c2;
        V3 rx = new V3(
                wx.x*lx.x + wy.x*lx.y + wz.x*lx.z,
                wx.y*lx.x + wy.y*lx.y + wz.y*lx.z,
                wx.z*lx.x + wy.z*lx.y + wz.z*lx.z
        );
        V3 ry = new V3(
                wx.x*ly.x + wy.x*ly.y + wz.x*ly.z,
                wx.y*ly.x + wy.y*ly.y + wz.y*ly.z,
                wx.z*ly.x + wy.z*ly.y + wz.z*ly.z
        );
        V3 rz = new V3(
                wx.x*lz.x + wy.x*lz.y + wz.x*lz.z,
                wx.y*lz.x + wy.y*lz.y + wz.y*lz.z,
                wx.z*lz.x + wy.z*lz.y + wz.z*lz.z
        );

        w.c = wc;
        w.R.set(rx, ry, rz);
        w.e.set(local.e);
        w.mu = local.mu; w.restitution = local.restitution; w.id = local.id;
        return w;
    }


    /**
     * Minimal wrapper so we keep ref + computed end pose together.
     */
        private record WorldConstruct(ConstructCollisionManager.ConstructRef ref, RigidState endPose) {
        V3 centerApprox() {
            return endPose.pos;
        }
        }
}