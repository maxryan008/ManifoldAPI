package dev.manifold.physics.collision;

import dev.manifold.physics.core.OBB;
import dev.manifold.physics.math.V3;
import dev.manifold.physics.collision.bvh.BVH;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Drives the collision pass between ALL constructs, and between entities and constructs (via mixin hook).
 * - Broadphase: BVH-vs-BVH with persistent pair cache
 * - CCD: root-level conservative advancement (optional)
 * - Narrowphase: SAT + clipping manifolds
 * - Clustering: reduce many neighboring contacts
 * - Persistent manifolds + warm-started SI solver
 */
public final class ConstructCollisionEngine {

    private static long frame = 0;

    /** Called from your entity mixin: clip entity motion against constructs (no impulses to constructs). */
    public static Vec3 resolveCollisions(Vec3 vanillaResolved, Entity entity, AABB startBB, float stepHeight) {
        // Start from vanilla’s already-clipped motion (vs terrain/entities). We only *reduce/redirect*.
        if (vanillaResolved.lengthSqr() == 0.0) return vanillaResolved;

        // We'll attempt: (1) move by vanilla vector; (2) statically resolve vs construct OBBs;
        // (3) apply slope sliding if surface is steep enough for the contacted material (μ).
        AABB targetBB = startBB.move(vanillaResolved.x, vanillaResolved.y, vanillaResolved.z);

        // Represent the entity as an AABB-oriented OBB (identity rotation in world)
        final Vec3 aCenter = new Vec3(
                (targetBB.minX + targetBB.maxX) * 0.5,
                (targetBB.minY + targetBB.maxY) * 0.5,
                (targetBB.minZ + targetBB.maxZ) * 0.5
        );
        final Vec3 aHalf = new Vec3(
                (targetBB.maxX - targetBB.minX) * 0.5,
                (targetBB.maxY - targetBB.minY) * 0.5,
                (targetBB.maxZ - targetBB.minZ) * 0.5
        );

        // Accumulated correction to apply to vanillaResolved
        Vec3 correction = Vec3.ZERO;
        boolean grounded = false;
        Vec3 groundNormal = null;
        double groundMu = 0.6;

        // Broadphase against each construct’s root AABB first
        for (var rec : ConstructCollisionManager.world().values()) {
            // Quick reject: if the moved AABB doesn’t intersect the construct’s world AABB, skip.
            if (!targetBB.intersects(ConstructCollisionManager.worldAabb(rec))) continue;

            // Traverse leaves and resolve against those overlapping in broadphase XZ/Y
            for (var node : rec.bvh.nodes) {
                if (!node.isLeaf()) continue;

                // Compute WORLD OBB for this leaf (center/axes/extents)
                var leafLocal = node.box;
                WorldOBB b = toWorldOBB(rec, leafLocal);

                // Broadphase test: AABB vs OBB via fast extents (AbsR bound)
                if (!aabbOverlapsObb(targetBB, b)) continue;

                // Narrow-phase SAT on axes: world axes (X,Y,Z) + the obb's 3 axes (u0,u1,u2).
                SATResult sat = satAabbVsObb(aCenter, aHalf, b);
                if (!sat.overlap) continue;

                // Apply minimum-penetration correction along normal (A <- push opposite the normal)
                Vec3 n = sat.normal; // world, pointing from entity to box
                double d = sat.depth;

                // Integrate correction
                correction = correction.subtract(n.scale(d));

                // If the contact normal points mostly up, mark grounded
                if (n.y > 0.55) {
                    grounded = true;
                    groundNormal = n;
                    groundMu = b.mu;
                }
            }
        }

        // If nothing to correct, return the vanilla motion.
        if (correction.equals(Vec3.ZERO)) {
            return vanillaResolved;
        }

        // Apply correction to the vanilla motion
        Vec3 adjusted = vanillaResolved.add(correction);

        // If grounded on a tilted surface, enable sliding depending on μ and slope.
        if (grounded && groundNormal != null) {
            // Slope angle α from normal: cos α = n·up
            double cosAlpha = groundNormal.normalize().dot(new Vec3(0, 1, 0));
            cosAlpha = Math.max(-1.0, Math.min(1.0, cosAlpha));
            double alpha = Math.acos(cosAlpha);  // [0..π]
            double tanAlpha = Math.tan(alpha);

            // Coulomb threshold: slide if tan(α) > μ (i.e., gravity component along plane overcomes friction)
            if (tanAlpha > groundMu) {
                // Project the velocity onto the contact plane and keep only the tangential component
                Vec3 n = groundNormal.normalize();
                Vec3 v = adjusted;
                Vec3 vNormal = n.scale(v.dot(n));
                Vec3 vTangent = v.subtract(vNormal);

                // Add a small downhill “gravity along plane” to avoid sticky feeling
                // downhill = (I - nnᵀ) * (0, -g*dt, 0)
                final double g = 0.08;  // vanilla-ish per-tick gravity magnitude
                final double dt = 1.0;  // per-collision pass; tuned empirically
                Vec3 gravity = new Vec3(0, -g * dt, 0);
                Vec3 gravityAlongPlane = gravity.subtract(n.scale(gravity.dot(n)));

                adjusted = vTangent.add(gravityAlongPlane.scale(0.5));
            } else {
                // Not steep enough to slide significantly: zero out the into-surface velocity component to “stick”
                Vec3 n = groundNormal.normalize();
                Vec3 v = adjusted;
                adjusted = v.subtract(n.scale(Math.min(0.0, v.dot(n))));
            }

            // Reset fall if we’ve grounded and were descending
            var dv = entity.getDeltaMovement();
            if (dv.y < 0.0) {
                entity.setDeltaMovement(dv.x, 0.0, dv.z);
                entity.fallDistance = 0.0F;
                entity.setOnGround(true);
                entity.verticalCollision = true;
            }
        }

        // Optional: simple “step-up” if we hit a short ledge and have horizontal motion.
        if (!grounded && stepHeight > 0.001f && (vanillaResolved.x != 0 || vanillaResolved.z != 0)) {
            Vec3 stepTry = tryStepUp(startBB, adjusted, stepHeight);
            if (stepTry != null) adjusted = stepTry;
        }

        // Never increase motion beyond vanilla; clamp each component toward zero if our correction flipped it
        adjusted = clampNotAboveVanilla(vanillaResolved, adjusted);

        return adjusted;
    }

    // ——— Helper types ———
    private static final class WorldOBB {
        Vec3 c;       // center (world)
        Vec3 u0, u1, u2; // orthonormal axes (world)
        Vec3 e;       // half extents
        double mu;    // friction coeff for this leaf/material
    }

    private static final class SATResult {
        boolean overlap;
        Vec3 normal;   // world, from entity -> box
        double depth;
    }

    // ——— Build a WORLD OBB from a construct record + local leaf AABB ———
    private static WorldOBB toWorldOBB(ConstructCollisionManager.ConstructRecord rec, dev.manifold.physics.collision.bvh.BVH.AABB leafLocal) {
        WorldOBB b = new WorldOBB();

        // rec.R is 3x3 rotation; extract columns as world axes
        b.u0 = new Vec3(rec.R.m00, rec.R.m10, rec.R.m20);
        b.u1 = new Vec3(rec.R.m01, rec.R.m11, rec.R.m21);
        b.u2 = new Vec3(rec.R.m02, rec.R.m12, rec.R.m22);

        // Local center relative to local origin (COM), then to world
        V3 lc = leafLocal.center.sub(rec.localOrigin);
        V3 wc = rec.pos.add(rec.R.mul(lc));
        b.c = new Vec3(wc.x, wc.y, wc.z);

        // Half extents are the leaf extents
        b.e = new Vec3(leafLocal.extents.x, leafLocal.extents.y, leafLocal.extents.z);

        // Friction: average of merged OBBs represented by this leaf; we don’t have that mapping here,
        // so approximate using a median μ by sampling the SDF gradient + a default. Use  rec.localObbs’ default μ.
        b.mu = 0.6;
        try {
            // If your PatchMerger keeps material μ the same, you can keep 0.6 or lift it from a representative voxel.
            b.mu = 0.6;
        } catch (Throwable ignored) { }

        return b;
    }

    // ——— Fast broadphase: AABB vs OBB using AbsR bounds ———
    private static boolean aabbOverlapsObb(AABB aabb, WorldOBB b) {
        // AABB center/extents
        Vec3 ca = new Vec3((aabb.minX + aabb.maxX) * 0.5, (aabb.minY + aabb.maxY) * 0.5, (aabb.minZ + aabb.maxZ) * 0.5);
        Vec3 ea = new Vec3((aabb.maxX - aabb.minX) * 0.5, (aabb.maxY - aabb.minY) * 0.5, (aabb.maxZ - aabb.minZ) * 0.5);

        Vec3 d = new Vec3(b.c.x - ca.x, b.c.y - ca.y, b.c.z - ca.z);

        // Project AABB extents onto OBB axes
        double ra0 = Math.abs(b.u0.x) * ea.x + Math.abs(b.u0.y) * ea.y + Math.abs(b.u0.z) * ea.z;
        double ra1 = Math.abs(b.u1.x) * ea.x + Math.abs(b.u1.y) * ea.y + Math.abs(b.u1.z) * ea.z;
        double ra2 = Math.abs(b.u2.x) * ea.x + Math.abs(b.u2.y) * ea.y + Math.abs(b.u2.z) * ea.z;

        // Separating axes test on OBB axes only (good enough for voxels)
        double dist0 = Math.abs(d.x * b.u0.x + d.y * b.u0.y + d.z * b.u0.z);
        if (dist0 > ra0 + b.e.x) return false;
        double dist1 = Math.abs(d.x * b.u1.x + d.y * b.u1.y + d.z * b.u1.z);
        if (dist1 > ra1 + b.e.y) return false;
        double dist2 = Math.abs(d.x * b.u2.x + d.y * b.u2.y + d.z * b.u2.z);
        if (dist2 > ra2 + b.e.z) return false;

        // Quick axis-aligned test on world axes (X,Y,Z)
        if (Math.abs(d.x) > ea.x + Math.abs(b.u0.x) * b.e.x + Math.abs(b.u1.x) * b.e.y + Math.abs(b.u2.x) * b.e.z) return false;
        if (Math.abs(d.y) > ea.y + Math.abs(b.u0.y) * b.e.x + Math.abs(b.u1.y) * b.e.y + Math.abs(b.u2.y) * b.e.z) return false;
        if (Math.abs(d.z) > ea.z + Math.abs(b.u0.z) * b.e.x + Math.abs(b.u1.z) * b.e.y + Math.abs(b.u2.z) * b.e.z) return false;

        return true;
    }

    // ——— Narrow-phase SAT on 6 principal axes (world XYZ + obb u0/u1/u2) ———
    private static SATResult satAabbVsObb(Vec3 aCenter, Vec3 aHalf, WorldOBB b) {
        SATResult out = new SATResult();
        out.overlap = true;
        out.depth = Double.POSITIVE_INFINITY;
        out.normal = new Vec3(0, 1, 0);

        // Axes to test
        Vec3[] axes = new Vec3[] {
                new Vec3(1,0,0), new Vec3(0,1,0), new Vec3(0,0,1), // world axes
                b.u0, b.u1, b.u2                                // obb axes
        };

        // Center delta a->b
        Vec3 d = new Vec3(b.c.x - aCenter.x, b.c.y - aCenter.y, b.c.z - aCenter.z);

        for (Vec3 a : axes) {
            // Normalize axis (be robust against degenerate)
            double len = Math.sqrt(a.x*a.x + a.y*a.y + a.z*a.z);
            if (len < 1e-9) continue;
            Vec3 axis = new Vec3(a.x / len, a.y / len, a.z / len);

            // Project AABB: radius = |axis·X|*hx + |axis·Y|*hy + |axis·Z|*hz
            double rA = Math.abs(axis.x) * aHalf.x + Math.abs(axis.y) * aHalf.y + Math.abs(axis.z) * aHalf.z;

            // Project OBB: radius = |axis·u0|*ex + |axis·u1|*ey + |axis·u2|*ez
            double rB =
                    Math.abs(axis.dot(b.u0)) * b.e.x +
                            Math.abs(axis.dot(b.u1)) * b.e.y +
                            Math.abs(axis.dot(b.u2)) * b.e.z;

            double dist = Math.abs(d.dot(axis));
            double overlap = rA + rB - dist;

            if (overlap <= 0) {
                out.overlap = false;
                return out;
            }

            if (overlap < out.depth) {
                out.depth = overlap;
                // Normal points from entity -> box (sign is direction of d projected on axis)
                double sign = (d.dot(axis) >= 0) ? 1.0 : -1.0;
                out.normal = new Vec3(axis.x * sign, axis.y * sign, axis.z * sign);
            }
        }

        return out;
    }

    private static Vec3 tryStepUp(AABB start, Vec3 motion, float stepHeight) {
        if (stepHeight <= 0) return null;

        // Try lifting by stepHeight, then moving horizontally, then letting Y settle
        Vec3 up = new Vec3(0, stepHeight, 0);
        AABB lifted = start.move(up.x, up.y, up.z);
        Vec3 horiz = new Vec3(motion.x, 0, motion.z);

        // Check lightweight collisions against constructs’ root bounds
        AABB target = lifted.move(horiz.x, horiz.y, horiz.z);
        for (var rec : ConstructCollisionManager.world().values()) {
            if (target.intersects(ConstructCollisionManager.worldAabb(rec))) {
                // Not clear, can't step here
                return null;
            }
        }
        // Accept: keep horizontal + up, keep original vertical intent if descending is safe (we can't know here)
        return up.add(horiz).add(new Vec3(0, Math.min(0, motion.y), 0));
    }

    private static Vec3 clampNotAboveVanilla(Vec3 vanilla, Vec3 adjusted) {
        // Component-wise: if adjusted increased magnitude beyond vanilla away from zero, clamp back
        double ax = Math.abs(adjusted.x) > Math.abs(vanilla.x) && Math.signum(adjusted.x) == Math.signum(vanilla.x) ? vanilla.x : adjusted.x;
        double ay = Math.abs(adjusted.y) > Math.abs(vanilla.y) && Math.signum(adjusted.y) == Math.signum(vanilla.y) ? vanilla.y : adjusted.y;
        double az = Math.abs(adjusted.z) > Math.abs(vanilla.z) && Math.signum(adjusted.z) == Math.signum(vanilla.z) ? vanilla.z : adjusted.z;
        return new Vec3(ax, ay, az);
    }

    // Small Vec3 helpers (since Minecraft's Vec3 has no dot())
    private static double dot(Vec3 a, Vec3 b) { return a.x*b.x + a.y*b.y + a.z*b.z; }

    private static AABB worldAabbOfLeaf(ConstructCollisionManager.ConstructRecord rec, dev.manifold.physics.collision.bvh.BVH.AABB local)
    {
        // Local center/extent:
        V3 lc = local.center;
        V3 le = local.extents;

        // World center: p + R * (lc - localOrigin)
        V3 wc = rec.pos.add(rec.R.mul(lc.sub(rec.localOrigin)));
        // World extents per axis: AbsR * le (already precomputed AbsR = |R|+eps)
        V3 we = rec.AbsR.mul(le);

        return new AABB(
                wc.x - we.x, wc.y - we.y, wc.z - we.z,
                wc.x + we.x, wc.y + we.y, wc.z + we.z
        );
    }

    private static double computeVerticalPenetrationY(AABB moved, ConstructCollisionManager.ConstructRecord rec) {
        // Quick reject by root bounds:
        if (!moved.intersects(ConstructCollisionManager.worldAabb(rec))) return 0.0;

        double bestTopY = Double.NEGATIVE_INFINITY;

        // Traverse only leaves that horizontally overlap our moved AABB.
        // For now, iterate merged leaves (ok unless you have huge counts; BVH traversal later if needed).
        var nodes = rec.bvh.nodes;
        // You can do a small stack-based traversal; here's a simple full-leaf scan for clarity:
        for (var n : nodes) {
            if (!n.isLeaf()) continue;

            var leafLocal = n.box; // BVH.AABB in local space
            AABB leafWorld = worldAabbOfLeaf(rec, leafLocal);

            // Require XZ overlap (like vanilla ground resolution)
            boolean xzOverlap = leafWorld.maxX > moved.minX && leafWorld.minX < moved.maxX
                    && leafWorld.maxZ > moved.minZ && leafWorld.minZ < moved.maxZ;
            if (!xzOverlap) continue;

            // We only care about floors under/at feet
            // Vanilla-like check: topY must be <= feet + small cap and >= feet - step-ish slack
            double topY = leafWorld.maxY;
            if (topY <= moved.minY + 0.6 && topY >= moved.minY - 0.6) {
                if (topY > bestTopY) bestTopY = topY;
            }
        }

        if (bestTopY == Double.NEGATIVE_INFINITY) return 0.0;

        double penetration = bestTopY - moved.minY;
        return penetration > 0.0 ? penetration : 0.0;
    }

    /** Run world-wide construct vs construct contacts (call once per tick if you want physical response). */
    public static List<Contact> computeConstructContacts() {
        ArrayList<Contact> contacts = new ArrayList<>(256);

        var map = ConstructCollisionManager.world();
        ArrayList<ConstructCollisionManager.ConstructRecord> recs = new ArrayList<>(map.values());

        // Root-level CCD early reject (optional)
        for (int i=0;i<recs.size();i++){
            for (int j=i+1;j<recs.size();j++){
                var A = recs.get(i); var B = recs.get(j);
                if (ConstructCollisionManager.settings.useCCD &&
                        !ccdRootAccept(A, B)) continue;

                traverseBVHs(A, B, contacts);
            }
        }

        // Cluster contacts to reduce count
        contacts = ContactClustering.cluster(contacts, 0.08);

        // Warm-started solver (does nothing unless you assign masses)
        Solver.solveIslands(contacts);

        frame++;
        return contacts;
    }

    private static boolean ccdRootAccept(ConstructCollisionManager.ConstructRecord A,
                                         ConstructCollisionManager.ConstructRecord B){
        // Conservative advancement test on root AABBs; if relative motion within dt cannot reach, skip
        // Here we just dilate by relative speed * dt
        V3 relV = B.state.getLinearVelocity().sub(A.state.getLinearVelocity());
        double r = relV.length() * 0.016 + 0.05; // dt=1/60, safety margin
        AABB a = ConstructCollisionManager.worldAabb(A);
        AABB b = ConstructCollisionManager.worldAabb(B);
        AABB aDil = new AABB(a.minX - r, a.minY - r, a.minZ - r, a.maxX + r, a.maxY + r, a.maxZ + r);
        return aDil.intersects(b);
    }

    private static void traverseBVHs(ConstructCollisionManager.ConstructRecord A,
                                     ConstructCollisionManager.ConstructRecord B,
                                     List<Contact> outContacts){
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{A.bvh.root, B.bvh.root});

        while(!stack.isEmpty()){
            int[] pair = stack.pop();
            BVH.Node na = A.bvh.nodes.get(pair[0]);
            BVH.Node nb = B.bvh.nodes.get(pair[1]);

            if (!overlapLocalAABBs(A, na.box, B, nb.box)) continue;

            if (na.isLeaf() && nb.isLeaf()) {
                int la = na.leafIndex, lb = nb.leafIndex;

                // Pair cache short-circuit: if we had it last frame, test first this frame (we’re already here)
                // Build contact manifold
                OBB oa = A.mergedObbs.get(la);
                OBB ob = B.mergedObbs.get(lb);

                // Optional SDF macro-cull at mid
                V3 mid = A.pos.add(B.pos).scale(0.5);
                if (ConstructCollisionManager.settings.useSDFHybrid &&
                        Hybrid.cullBySDF(A,B,mid, 0.2)) continue;

                var res = BoxBox.collide(oa, ob, A, B);
                if (!res.overlap) continue;

                // Build/Update persistent manifold
                var cache = ConstructCollisionManager.manifolds();
                Contact man = cache.get(A.id, B.id, la, lb);
                if (man == null) man = new Contact();
                man.a = A.id; man.b = B.id; man.leafA = la; man.leafB = lb;
                man.normal = res.normal;
                man.friction = Math.sqrt(oa.mu * ob.mu);
                man.restitution = 0.02; // small bounce for big masses

                man.points.clear();
                for (int k=0;k<Math.min(4, res.contactsA.size());k++){
                    var p = new Contact.Point();
                    p.pos = res.contactsA.get(k);
                    p.rA = p.pos.sub(A.state.getPosition());
                    p.rB = p.pos.sub(B.state.getPosition());
                    double penetration = res.depth;
                    double baum = 0.2; // baumgarte
                    p.bias = Math.max(penetration - ConstructCollisionManager.settings.allowedPenetration, 0.0) * baum;
                    man.points.add(p);
                }

                cache.put(man);
                outContacts.add(man);

                // Touch the pair cache
                ConstructCollisionManager.pairs().touch(A.id, B.id, la, lb, frame);

            } else {
                // Descend: pick the child pair with higher SAH first (cheap heuristic)
                if (na.isLeaf()){
                    stack.push(new int[]{pair[0], nb.left});
                    stack.push(new int[]{pair[0], nb.right});
                } else if (nb.isLeaf()){
                    stack.push(new int[]{na.left, pair[1]});
                    stack.push(new int[]{na.right, pair[1]});
                } else {
                    // push both combinations
                    stack.push(new int[]{na.left, nb.left});
                    stack.push(new int[]{na.left, nb.right});
                    stack.push(new int[]{na.right, nb.left});
                    stack.push(new int[]{na.right, nb.right});
                }
            }
        }
    }

    private static boolean overlapLocalAABBs(ConstructCollisionManager.ConstructRecord A, BVH.AABB a,
                                             ConstructCollisionManager.ConstructRecord B, BVH.AABB b){
        // Transform b’s AABB into A-space via AbsR bound
        V3 cB_inA = A.R.transpose().mul(B.pos.add(B.R.mul(b.center.sub(B.localOrigin))).sub(A.pos.add(A.R.mul(a.center.sub(A.localOrigin)))));
        // extents in A: Abs(Ra^T Rb) * eB + eA
        V3 eB_inA = A.AbsR.mul(b.extents); // NOTE: A.AbsR = |Ra^T Rb| * eps; precomputed per construct
        V3 eA = a.extents;
        V3 d = cB_inA.abs().sub(eA.add(eB_inA));
        return d.x <= 0 && d.y <= 0 && d.z <= 0;
    }

    /** Entity vs construct: make a handful of contacts around entity AABB vs merged boxes. */
    private static void gatherEntityContacts(Entity e, ConstructCollisionManager.ConstructRecord B, List<Contact> out) {
        // Approx: sample 6 rays (down & sides) and produce speculative contact points
        var bb = e.getBoundingBox();
        V3 c = new V3((bb.minX+bb.maxX)*0.5, bb.minY, (bb.minZ+bb.maxZ)*0.5);
        V3 nUp = new V3(0,1,0);

        // Simple ground probe: if B root is beneath entity, add one contact
        var rootAabb = ConstructCollisionManager.worldAabb(B);
        if (rootAabb.maxY < bb.minY - 2.0) return;

        Contact man = new Contact();
        man.a = new UUID(0,0); // "entity"
        man.b = B.id; man.leafA = -1; man.leafB = -1;
        man.normal = nUp; man.friction = 0.8; man.restitution = 0.0;

        Contact.Point p = new Contact.Point();
        p.pos = c.add(0, -0.05, 0);
        p.rA = new V3(0,0,0);
        p.rB = p.pos.sub(B.state.getPosition());
        p.bias = 0.03;
        man.points.add(p);
        out.add(man);
    }
}