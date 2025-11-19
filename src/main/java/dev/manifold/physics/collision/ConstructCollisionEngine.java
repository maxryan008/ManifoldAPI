package dev.manifold.physics.collision;

import dev.manifold.ConstructManager;
import dev.manifold.DynamicConstruct;
import dev.manifold.physics.core.OBB;
import dev.manifold.physics.math.V3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Entity-vs-construct collision using world-space OBBs.
 *
 * - Entity is a world-space AABB.
 * - Each construct block is an OBB in local space.
 * - Construct has world pose (position = COM in world, rotation about COM).
 * - We:
 *   * Take vanilla-resolved motion.
 *   * For each nearby construct:
 *       - Broadphase against construct render AABB.
 *       - For that construct, test entity AABB vs each local OBB (transformed into world space).
 *       - Use SAT to compute MTV (minimum translation vector) per overlapping OBB.
 *       - Accumulate MTV and apply to motion, without increasing magnitude vs vanilla.
 */
public final class ConstructCollisionEngine {

    private static final boolean DEBUG = false;

    private ConstructCollisionEngine() {}

    /**
     * Hook from EntityMixin:
     *
     * @ModifyReturnValue(
     *   method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
     *   at = @At("RETURN")
     * )
     * private Vec3 manifold$applyConstructCollisions(Vec3 vanillaResolved) {
     *     final Entity self = (Entity)(Object)this;
     *     final AABB startBB = this.getBoundingBox();
     *     final float stepHeight = Math.max(0.0f, this.maxUpStep());
     *     return ConstructCollisionEngine.resolveMotion(self, startBB, vanillaResolved, stepHeight);
     * }
     */
    public static Vec3 resolveMotion(Entity entity,
                                     AABB startBB,
                                     Vec3 vanillaResolved,
                                     float stepHeight) {

        if (ConstructManager.INSTANCE == null) {
            return vanillaResolved;
        }

        Level level = entity.level();

        // Broad-phase: find constructs in this dimension near the entity
        List<DynamicConstruct> nearby = ConstructManager.INSTANCE.getNearbyConstructs(
                level.dimension(),
                entity.position(),
                4
        );
        if (nearby.isEmpty()) return vanillaResolved;

        Vec3 bestMotion = vanillaResolved;

        for (DynamicConstruct construct : nearby) {
            bestMotion = clipAgainstConstructObbs(construct, startBB, bestMotion);
        }

        return bestMotion;
    }

    // ------------------------------------------------------------------------
    // Core: world-space OBB vs entity AABB
    // ------------------------------------------------------------------------

    private static Vec3 clipAgainstConstructObbs(DynamicConstruct construct,
                                                 AABB startBB,
                                                 Vec3 motion) {

        if (motion.lengthSqr() == 0.0) {
            return motion;
        }

        // Swept AABB of entity in world space (for broadphase against whole ship)
        AABB swept = startBB.expandTowards(motion);

        // Broad-phase: approximate construct hull as its render bounding box (world-space)
        AABB hull = construct.getRenderBoundingBox();
        if (!swept.intersects(hull)) {
            return motion;
        }

        // World pose of construct
        Vec3 worldPos = construct.getPosition();       // world-space COM
        Quaternionf q = construct.getRotation();       // rotation about COM
        Vec3 comLocal = construct.getCenterOfMass();   // COM in local (block) coords

        // World-space axes of the construct (columns of rotation matrix)
        Vector3f axis0 = new Vector3f(1, 0, 0).rotate(q); // ship local X in world
        Vector3f axis1 = new Vector3f(0, 1, 0).rotate(q); // local Y
        Vector3f axis2 = new Vector3f(0, 0, 1).rotate(q); // local Z

        // Entity's end AABB after vanilla + previous constructs
        AABB endBB = startBB.move(motion);
        Vec3 entityCenter = new Vec3(
                (endBB.minX + endBB.maxX) * 0.5,
                (endBB.minY + endBB.maxY) * 0.5,
                (endBB.minZ + endBB.maxZ) * 0.5
        );
        Vec3 entityHalfExtents = new Vec3(
                (endBB.maxX - endBB.minX) * 0.5,
                (endBB.maxY - endBB.minY) * 0.5,
                (endBB.maxZ - endBB.minZ) * 0.5
        );

        // Fetch local-space OBBs for this construct
        List<OBB> localObbs = ConstructCollisionManager.getLocalObbs(construct.getId());
        if (localObbs == null || localObbs.isEmpty()) {
            return motion;
        }

        Vec3 totalCorrection = Vec3.ZERO;
        AABB currentBB = endBB;

        for (OBB obb : localObbs) {
            // World-space center of this OBB
            Vec3 worldCenter = localToWorld(obb.c, comLocal, worldPos, q);

            // Quick AABB broadphase for this OBB vs entity
            AABB obbAabb = obbWorldAabb(worldCenter, obb.e, axis0, axis1, axis2);
            if (!obbAabb.intersects(currentBB)) {
                continue;
            }

            Vec3 mtv = obbVsAabbMTV(
                    worldCenter,
                    obb.e,
                    axis0, axis1, axis2,
                    entityCenter.add(totalCorrection),
                    entityHalfExtents
            );

            if (mtv != null) {
                // Apply correction to entity AABB and track it
                totalCorrection = totalCorrection.add(mtv);
                currentBB = currentBB.move(mtv.x, mtv.y, mtv.z);
            }
        }

        if (totalCorrection.lengthSqr() == 0.0) {
            return motion;
        }

        // Don't allow this collision system to increase the magnitude of motion.
        Vec3 proposed = motion.add(totalCorrection);
        return clampMotion(motion, proposed);
    }

    // Transform a local point (block coords) into world space using COM pivot.
    private static Vec3 localToWorld(V3 local,
                                     Vec3 comLocal,
                                     Vec3 worldPos,
                                     Quaternionf q) {
        double lx = local.x - comLocal.x;
        double ly = local.y - comLocal.y;
        double lz = local.z - comLocal.z;

        Vector3f v = new Vector3f((float) lx, (float) ly, (float) lz);
        v.rotate(q);

        return new Vec3(
                worldPos.x + v.x,
                worldPos.y + v.y,
                worldPos.z + v.z
        );
    }

    // Compute a conservative world-space AABB for an OBB (for broadphase).
    private static AABB obbWorldAabb(Vec3 center,
                                     V3 halfExtents,
                                     Vector3f a0,
                                     Vector3f a1,
                                     Vector3f a2) {

        // For an OBB, the AABB half-extent along world axes is:
        // eAABB = |a0|*e0 + |a1|*e1 + |a2|*e2 (component-wise abs).
        double ex = Math.abs(a0.x) * halfExtents.x +
                Math.abs(a1.x) * halfExtents.y +
                Math.abs(a2.x) * halfExtents.z;
        double ey = Math.abs(a0.y) * halfExtents.x +
                Math.abs(a1.y) * halfExtents.y +
                Math.abs(a2.y) * halfExtents.z;
        double ez = Math.abs(a0.z) * halfExtents.x +
                Math.abs(a1.z) * halfExtents.y +
                Math.abs(a2.z) * halfExtents.z;

        return new AABB(
                center.x - ex, center.y - ey, center.z - ez,
                center.x + ex, center.y + ey, center.z + ez
        );
    }

    // ------------------------------------------------------------------------
    // SAT: OBB vs AABB -> MTV in world space
    // ------------------------------------------------------------------------

    /**
     * General SAT test for:
     *   A = OBB (center ca, half extents ea, axes a0,a1,a2)
     *   B = axis-aligned box (center cb, half extents eb along world X,Y,Z)
     *
     * Returns MTV (vector to move B out of A) in world-space, or null if no overlap.
     */
    private static Vec3 obbVsAabbMTV(Vec3 ca,
                                     V3 ea,
                                     Vector3f a0,
                                     Vector3f a1,
                                     Vector3f a2,
                                     Vec3 cb,
                                     Vec3 eb) {

        // Box B world axes
        Vector3f bx = new Vector3f(1, 0, 0);
        Vector3f by = new Vector3f(0, 1, 0);
        Vector3f bz = new Vector3f(0, 0, 1);

        // Relative center from A to B
        Vector3f T = new Vector3f(
                (float) (cb.x - ca.x),
                (float) (cb.y - ca.y),
                (float) (cb.z - ca.z)
        );

        // Candidate axes (not normalized; we'll account for length).
        Vector3f[] axes = new Vector3f[] {
                new Vector3f(bx), new Vector3f(by), new Vector3f(bz),
                new Vector3f(a0), new Vector3f(a1), new Vector3f(a2),
                cross(bx, a0), cross(bx, a1), cross(bx, a2),
                cross(by, a0), cross(by, a1), cross(by, a2),
                cross(bz, a0), cross(bz, a1), cross(bz, a2)
        };

        double minOverlap = Double.POSITIVE_INFINITY;
        Vector3f bestAxis = null;
        boolean bestAxisPointsFromAToB = true;

        for (Vector3f axis : axes) {
            float len2 = axis.lengthSquared();
            if (len2 < 1e-6f) continue; // degenerate axis

            // Projection radii on this axis; axis is not normalized
            double rA = projectedRadiusOBB(ea, a0, a1, a2, axis);
            double rB = projectedRadiusAABB(eb, axis);

            double dist = dot(T, axis); // signed distance from A center to B along axis (scaled by |axis|)
            double distAbs = Math.abs(dist);

            double overlap = rA + rB - distAbs;
            if (overlap <= 0.0) {
                // Separating axis found -> no collision
                return null;
            }

            // For MTV we want smallest overlap along the *normalized* axis:
            double axisLen = Math.sqrt(len2);
            double overlapNorm = overlap / axisLen;

            if (overlapNorm < minOverlap) {
                minOverlap = overlapNorm;
                bestAxis = new Vector3f(axis);
                bestAxisPointsFromAToB = dist > 0.0;
            }
        }

        if (bestAxis == null) {
            return null;
        }

        // Normalize best axis
        bestAxis.normalize();

        double sign = bestAxisPointsFromAToB ? 1.0 : -1.0;
        // MTV moves B (entity) out of A along bestAxis
        return new Vec3(
                bestAxis.x * minOverlap * sign,
                bestAxis.y * minOverlap * sign,
                bestAxis.z * minOverlap * sign
        );
    }

    private static Vector3f cross(Vector3f a, Vector3f b) {
        Vector3f out = new Vector3f();
        a.cross(b, out);
        return out;
    }

    private static double dot(Vector3f a, Vector3f b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    // Projection radius of OBB on an arbitrary axis (axis not normalized).
    private static double projectedRadiusOBB(V3 e,
                                             Vector3f a0,
                                             Vector3f a1,
                                             Vector3f a2,
                                             Vector3f axis) {
        double ax = Math.abs(a0.dot(axis));
        double ay = Math.abs(a1.dot(axis));
        double az = Math.abs(a2.dot(axis));
        return e.x * ax + e.y * ay + e.z * az;
    }

    // Projection radius of an axis-aligned box on an arbitrary axis.
    private static double projectedRadiusAABB(Vec3 e,
                                              Vector3f axis) {
        double ax = Math.abs(axis.x);
        double ay = Math.abs(axis.y);
        double az = Math.abs(axis.z);
        return e.x * ax + e.y * ay + e.z * az;
    }

    // ------------------------------------------------------------------------
    // Utility: keep motion from exceeding vanilla per-axis magnitude
    // ------------------------------------------------------------------------

    private static Vec3 clampMotion(Vec3 base, Vec3 proposed) {
        double x = clampAxis(base.x, proposed.x);
        double y = clampAxis(base.y, proposed.y);
        double z = clampAxis(base.z, proposed.z);
        return new Vec3(x, y, z);
    }

    private static double clampAxis(double base, double proposed) {
        if (base == 0.0D) return 0.0D;
        double absBase = Math.abs(base);
        double absProp = Math.abs(proposed);
        if (absProp > absBase + 1e-7) {
            return base;
        }
        return proposed;
    }
}