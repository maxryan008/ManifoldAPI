package dev.manifold.physics.collision;

import dev.manifold.ConstructManager;
import dev.manifold.DynamicConstruct;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ConstructCollisionEngine {
    private static final double SKIN = 1e-6;
    private static final double LEDGE_EPS = 1e-3; // lets you leave platform edges without snagging

    public static Vec3 resolveCollisions(
            Vec3 vanillaResolved,
            Entity self,
            AABB startBB,
            float stepHeight
    ) {
        Vec3 outWorld = vanillaResolved;
        AABB workingBB = startBB;

        List<DynamicConstruct> nearby = ConstructManager.INSTANCE
                .getNearbyConstructs(self.level().dimension(), self.position(), 3);

        for (DynamicConstruct construct : nearby) {
            Optional<ConstructCollisionManager.Planes> opt = ConstructCollisionManager.get(construct.getId());
            if (opt.isEmpty()) continue;

            if (!expandByMotion(workingBB, outWorld).intersects(construct.getRenderBoundingBox())) continue;

            // World -> local
            Quaternionf invRot = new Quaternionf(construct.getRotation()).invert();
//            AABB localBB = rotateAABB(workingBB.move(construct.getPosition().scale(-1)), invRot);
//            Vec3 desiredLocal = rotateVec(outWorld, invRot);
            AABB localBB = workingBB.move(ConstructManager.INSTANCE.getRenderPosFromSim(construct.getId(),new Vec3(construct.getSimOrigin().getX(), construct.getSimOrigin().getY(), construct.getSimOrigin().getZ())).scale(-1));
            Vec3 desiredLocal = outWorld;

            // Push-out even if desiredLocal == 0
            Vec3 correctedLocal = resolveAgainstConstruct(construct, desiredLocal, self, localBB, stepHeight);

            if (!correctedLocal.equals(desiredLocal)) {
                Vec3 correctedWorld = rotateVec(correctedLocal, construct.getRotation());
                Vec3 delta = correctedWorld.subtract(outWorld);
                if (!delta.equals(Vec3.ZERO)) {
                    workingBB = workingBB.move(delta.x, delta.y, delta.z);
                    outWorld = correctedWorld;
                }
            }
        }
        return outWorld;
    }

    public static Vec3 resolveAgainstConstruct(
            DynamicConstruct construct,
            Vec3 desiredLocal,     // request in construct-local space
            Entity self,
            AABB startBBLocal,     // AABB already translated+rotated into construct-local
            float stepHeight
    ) {
        Optional<ConstructCollisionManager.Planes> opt = ConstructCollisionManager.get(construct.getId());
        if (opt.isEmpty()) return desiredLocal;
        ConstructCollisionManager.Planes planes = opt.get();
        // 1) Pre-advance to the requested position
        AABB moved = startBBLocal;
        Vec3 deltaMovement = self.getDeltaMovement();

        // 2) One-shot anti-penetration opposite to the incoming velocity
        //    (prevents “prefer Y” when flying into a 1-block-tall platform side)
        double penX = pushOutX_independent(moved, planes, true, deltaMovement.x);
        double penY = pushOutY_independent(moved, planes, deltaMovement.y);
        double penZ = pushOutZ_independent(moved, planes, true, deltaMovement.z);

        double antiX = 0.0, antiY = 0.0, antiZ = 0.0;
        if (penX != 0.0 && Math.signum(penX) == -Math.signum(desiredLocal.x)) antiX = penX;
        if (penY != 0.0 && Math.signum(penY) == -Math.signum(desiredLocal.y)) antiY = penY;
        if (penZ != 0.0 && Math.signum(penZ) == -Math.signum(desiredLocal.z)) antiZ = penZ;

        if (antiX != 0.0 || antiY != 0.0 || antiZ != 0.0) {
            // Move geometry out of penetration...
            moved = moved.move(antiX, antiY, antiZ);
            // ...but pull the *requested* motion back so the end pose is unchanged.
            desiredLocal = new Vec3(desiredLocal.x - antiX, desiredLocal.y - antiY, desiredLocal.z - antiZ);
        }

        // 3) Split motion into components and resolve each independently
        double outX = 0.0, outY = 0.0, outZ = 0.0;

        // X component
        if (desiredLocal.x != 0.0) {
            AABB afterX = moved.move(desiredLocal.x, 0.0, 0.0);
            double pushX = pushOutX_independent(afterX, planes, /*useLedgeEps*/ true, deltaMovement.x);
            // Only accept a push that negates penetration along the direction we moved
            if (pushX != 0.0 && Math.signum(pushX) == -Math.signum(desiredLocal.x)) {
                outX = desiredLocal.x + pushX;
                moved = afterX.move(pushX, 0.0, 0.0);
            } else {
                outX = desiredLocal.x;
                moved = afterX; // no correction needed
            }
        }

        // Y component (usually what keeps you on top, already unbiased by the split)
        if (desiredLocal.y != 0.0) {
            AABB afterY = moved.move(0.0, desiredLocal.y, 0.0);
            double pushY = pushOutY_independent(afterY, planes, deltaMovement.y);
            if (pushY != 0.0 && Math.signum(pushY) == -Math.signum(desiredLocal.y)) {
                outY = desiredLocal.y + pushY;
                moved = afterY.move(0.0, pushY, 0.0);
            } else {
                outY = desiredLocal.y;
                moved = afterY;
            }
        }

        // Z component
        if (desiredLocal.z != 0.0) {
            AABB afterZ = moved.move(0.0, 0.0, desiredLocal.z);
            double pushZ = pushOutZ_independent(afterZ, planes, /*useLedgeEps*/ true, deltaMovement.z);
            if (pushZ != 0.0 && Math.signum(pushZ) == -Math.signum(desiredLocal.z)) {
                outZ = desiredLocal.z + pushZ;
                moved = afterZ.move(0.0, 0.0, pushZ);
            } else {
                outZ = desiredLocal.z;
                moved = afterZ;
            }
        }

        return new Vec3(outX, outY, outZ);
    }

    /* ======================= Push-out helpers (handle zero-motion too) ======================= */

    /** Choose the non-zero with the smaller magnitude; if only one is non-zero, return it; else 0. */
    private static double pickBestSigned(double pos, double neg) {
        if (pos != 0.0 && neg != 0.0) return Math.abs(pos) <= Math.abs(neg) ? pos : neg;
        return pos != 0.0 ? pos : (neg != 0.0 ? neg : 0.0);
    }

    /** Minimal signed Y displacement to separate 'bb' from UP/DOWN planes. */
    private static double pushOutY_independent(AABB bb, ConstructCollisionManager.Planes planes, double yDelta) {
        double pushUp = 0.0;  // +Y
        double pushDn = 0.0;  // -Y

        // UP (depth = Y+1), in-plane (u,v)=(X,Z)
        for (CollisionPlane.Rect r : planes.rects(Direction.UP)) {
            final double y = r.depth;
            if (y <= bb.minY || y >= bb.maxY - yDelta) continue;
            if (!overlap1D(bb.minX, bb.maxX, r.u0, r.u1)) continue;
            if (!overlap1D(bb.minZ, bb.maxZ, r.v0, r.v1)) continue;

            double candUp = (y - bb.minY) + SKIN;  // > 0
            double candDn = (y - (bb.maxY + yDelta)) - SKIN;  // < 0
            if (candUp > 0.0 && (pushUp == 0.0 || candUp < pushUp)) pushUp = candUp;
            if (candDn < 0.0 && (pushDn == 0.0 || candDn > pushDn)) pushDn = candDn;
        }

        // DOWN (depth = Y), in-plane (u,v)=(X,Z)
        for (CollisionPlane.Rect r : planes.rects(Direction.DOWN)) {
            final double y = r.depth;
            if (y <= bb.minY - yDelta || y >= bb.maxY) continue;
            if (!overlap1D(bb.minX, bb.maxX, r.u0, r.u1)) continue;
            if (!overlap1D(bb.minZ, bb.maxZ, r.v0, r.v1)) continue;

            double candUp = (y - (bb.minY + yDelta)) + SKIN;
            double candDn = (y - bb.maxY) - SKIN;
            if (candUp > 0.0 && (pushUp == 0.0 || candUp < pushUp)) pushUp = candUp;
            if (candDn < 0.0 && (pushDn == 0.0 || candDn > pushDn)) pushDn = candDn;
        }

        return pickBestSigned(pushUp, pushDn);
    }

    /** Minimal signed X displacement to separate 'bb' from EAST/WEST planes. */
    private static double pushOutX_independent(AABB bb, ConstructCollisionManager.Planes planes, boolean useLedgeEps, double xDelta) {
        double pushRt = 0.0;  // +X
        double pushLt = 0.0;  // -X
        final double yMinForSide = useLedgeEps ? (bb.minY + LEDGE_EPS) : bb.minY;

        // EAST (depth=X+1), in-plane (u,v)=(Z,Y)
        for (CollisionPlane.Rect r : planes.rects(Direction.EAST)) {
            final double x = r.depth;
            if (x <= bb.minX || x >= bb.maxX - xDelta) continue;
            if (!overlap1D(bb.minZ, bb.maxZ, r.u0, r.u1)) continue;
            if (!overlap1D(yMinForSide, bb.maxY, r.v0, r.v1)) continue; // << ledge slip here

            double candRt = (x - bb.minX) + SKIN;  // > 0
            double candLt = (x - (bb.maxX + xDelta)) - SKIN;  // < 0
            if (candRt > 0.0 && (pushRt == 0.0 || candRt < pushRt)) pushRt = candRt;
            if (candLt < 0.0 && (pushLt == 0.0 || candLt > pushLt)) pushLt = candLt;
        }

        // WEST (depth=X), in-plane (u,v)=(Z,Y)
        for (CollisionPlane.Rect r : planes.rects(Direction.WEST)) {
            final double x = r.depth;
            if (x <= bb.minX - xDelta || x >= bb.maxX) continue;
            if (!overlap1D(bb.minZ, bb.maxZ, r.u0, r.u1)) continue;
            if (!overlap1D(yMinForSide, bb.maxY, r.v0, r.v1)) continue; // << ledge slip here

            double candRt = (x - (bb.minX + xDelta)) + SKIN;
            double candLt = (x - bb.maxX) - SKIN;
            if (candRt > 0.0 && (pushRt == 0.0 || candRt < pushRt)) pushRt = candRt;
            if (candLt < 0.0 && (pushLt == 0.0 || candLt > pushLt)) pushLt = candLt;
        }

        return pickBestSigned(pushRt, pushLt);
    }

    /** Minimal signed Z displacement to separate 'bb' from SOUTH/NORTH planes. */
    private static double pushOutZ_independent(AABB bb, ConstructCollisionManager.Planes planes, boolean useLedgeEps, double zDelta) {
        double pushFwd = 0.0; // +Z
        double pushBak = 0.0; // -Z
        final double yMinForSide = useLedgeEps ? (bb.minY + LEDGE_EPS) : bb.minY;

        // SOUTH (depth=Z+1), in-plane (u,v)=(X,Y)
        for (CollisionPlane.Rect r : planes.rects(Direction.SOUTH)) {
            final double z = r.depth;
            if (z <= bb.minZ || z >= bb.maxZ - zDelta) continue;
            if (!overlap1D(bb.minX, bb.maxX, r.u0, r.u1)) continue;
            if (!overlap1D(yMinForSide, bb.maxY, r.v0, r.v1)) continue; // << ledge slip here

            double candF = (z - bb.minZ) + SKIN;  // > 0
            double candB = (z - (bb.maxZ + zDelta)) - SKIN;  // < 0
            if (candF > 0.0 && (pushFwd == 0.0 || candF < pushFwd)) pushFwd = candF;
            if (candB < 0.0 && (pushBak == 0.0 || candB > pushBak)) pushBak = candB;
        }

        // NORTH (depth=Z), in-plane (u,v)=(X,Y)
        for (CollisionPlane.Rect r : planes.rects(Direction.NORTH)) {
            final double z = r.depth;
            if (z <= bb.minZ - zDelta || z >= bb.maxZ) continue;
            if (!overlap1D(bb.minX, bb.maxX, r.u0, r.u1)) continue;
            if (!overlap1D(yMinForSide, bb.maxY, r.v0, r.v1)) continue; // << ledge slip here

            double candF = (z - (bb.minZ + zDelta)) + SKIN;
            double candB = (z - bb.maxZ) - SKIN;
            if (candF > 0.0 && (pushFwd == 0.0 || candF < pushFwd)) pushFwd = candF;
            if (candB < 0.0 && (pushBak == 0.0 || candB > pushBak)) pushBak = candB;
        }

        return pickBestSigned(pushFwd, pushBak);
    }

    /* -------- small utilities (unchanged) -------- */

    /** 1D interval overlap, treating rects as half-open [b0,b1) and the AABB as closed. */
    private static boolean overlap1D(double a0, double a1, int b0, int b1) {
        return a1 > b0 && b1 > a0;
    }

    private static AABB expandByMotion(AABB box, Vec3 motion) {
        double minX = motion.x < 0 ? box.minX + motion.x : box.minX;
        double maxX = motion.x > 0 ? box.maxX + motion.x : box.maxX;
        double minY = motion.y < 0 ? box.minY + motion.y : box.minY;
        double maxY = motion.y > 0 ? box.maxY + motion.y : box.maxY;
        double minZ = motion.z < 0 ? box.minZ + motion.z : box.minZ;
        double maxZ = motion.z > 0 ? box.maxZ + motion.z : box.maxZ;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Vec3 rotateVec(Vec3 v, Quaternionf q) {
        org.joml.Vector3f t = new org.joml.Vector3f((float) v.x, (float) v.y, (float) v.z);
        t.rotate(q);
        return new Vec3(t.x, t.y, t.z);
    }

    public static AABB rotateAABB(AABB box, Quaternionf q) {
        Vec3[] corners = {
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ),
        };
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 c : corners) {
            org.joml.Vector3f v = new org.joml.Vector3f((float) c.x, (float) c.y, (float) c.z);
            v.rotate(q);
            if (v.x < minX) minX = v.x;
            if (v.x > maxX) maxX = v.x;
            if (v.y < minY) minY = v.y;
            if (v.y > maxY) maxY = v.y;
            if (v.z < minZ) minZ = v.z;
            if (v.z > maxZ) maxZ = v.z;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}