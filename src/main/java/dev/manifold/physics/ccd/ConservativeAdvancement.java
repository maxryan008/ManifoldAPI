package dev.manifold.physics.ccd;

import dev.manifold.physics.core.OBB;
import dev.manifold.physics.core.RigidState;
import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;
import dev.manifold.physics.narrowphase.ObbSat;

/**
 * Conservative Advancement that actually tests the OBBs at the current integrated poses.
 * This variant assumes: A is defined in WORLD at t0, B is defined in LOCAL coords
 * and needs (pose, localOrigin) to become WORLD.
 */
public final class ConservativeAdvancement {

    /** CCD parameters – keep cheap but stable. */
    private static final int   MAX_ITERS   = 12;
    private static final double MIN_STEP   = 1e-6;
    private static final double SAFETY_EPS = 1e-7;

    /**
     * Returns TOI in [0, dt] using CA on OBBs where:
     *  - aBaseWorld: A’s OBB at the *current* A pose (t0)
     *  - bBaseLocal: B’s OBB in LOCAL coordinates (axis-aligned in local by default)
     *  - aPose, bPose: current poses/velocities at t0 (will be cloned internally)
     *  - bLocalOrigin: local origin for B (e.g., COM) so world = pos + R*(local - origin)
     */
    public static double toiLocalB(
            OBB aBaseWorld,
            OBB bBaseLocal,
            RigidState aPose,
            RigidState bPose,
            V3 bLocalOrigin,
            double dt
    ){
        // Clone working poses (we will integrate them inside this routine)
        RigidState a = cloneState(aPose);
        RigidState b = cloneState(bPose);

        double t = 0.0;
        double remain = dt;

        // Precompute A’s reference position at t0 (so we can follow translation only)
        final V3 a0 = aPose.pos.cpy();

        for (int it = 0; it < MAX_ITERS && remain > MIN_STEP; it++) {

            // Build *current* world OBBs at the tentative time
            OBB Acur = obbAtPoseA_world(aBaseWorld, a, a0);
            OBB Bcur = obbAtPoseB_local(bBaseLocal, b, bLocalOrigin);

            ObbSat.OverlapResult sep = ObbSat.test(Acur, Bcur);

            // If overlapping already (or extremely close), we're at impact
            if (sep.overlaps && sep.depth > SAFETY_EPS) {
                return t;
            }

            // Estimate a conservative gap using the winning axis (or a small floor)
            double gap = sep.overlaps ? (ObbSat.CONTACT_OFFSET * 0.5) : 0.01;
            gap = Math.max(gap, 1e-4);

            // Conservative relative speed along candidate normal
            V3 n = sep.nWorld.len2() > 0 ? sep.nWorld : new V3(0, 1, 0);
            double sLin = Math.abs(b.vLin.dot(n) - a.vLin.dot(n));

            // Conservative angular contribution using max radius
            double maxRadA = Math.max(Acur.e.x, Math.max(Acur.e.y, Acur.e.z));
            double maxRadB = Math.max(Bcur.e.x, Math.max(Bcur.e.y, Bcur.e.z));
            double sAng = a.vAng.len()*maxRadA + b.vAng.len()*maxRadB;

            double speed = sLin + sAng + 1e-6;
            double step = Math.min(remain, gap / speed);
            if (step < MIN_STEP) break;

            // Integrate poses by 'step' and loop
            a.integrate(step);
            b.integrate(step);
            t      += step;
            remain -= step;
        }

        // No hit within dt
        return t;
    }

    // ---------- helpers (copied from engine, kept local to avoid cycles) ----------

    /** A’s OBBs were given in WORLD at t0; translate centers by (a.pos - a0) so they follow the temporary pose. */
    private static OBB obbAtPoseA_world(OBB src, RigidState aPose, V3 a0){
        OBB o = new OBB();
        V3 delta = V3.sub(aPose.pos, a0);
        o.c.set(V3.add(src.c, delta));
        // Keep player box axis-aligned (src.R). If you later add player rotation, compose here.
        o.R.set(src.R.c0, src.R.c1, src.R.c2);
        o.e.set(src.e);
        o.mu = src.mu; o.restitution = src.restitution; o.id = src.id;
        return o;
    }

    /** Transform B’s LOCAL OBB by pose and origin: world = pos + R*(local - origin). */
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

    private static RigidState cloneState(RigidState s){
        RigidState r = new RigidState();
        r.set(s.pos.cpy(), new M3().set(s.rot.c0, s.rot.c1, s.rot.c2),
                s.vLin.cpy(), s.vAng.cpy(), s.invMass, s.invInertiaWorld);
        return r;
    }
}