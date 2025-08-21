package dev.manifold.physics.collision;

import dev.manifold.ConstructManager;
import dev.manifold.DynamicConstruct;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;

import java.util.List;

public class RotatedCollisionHandler {
    private static final double MIN_MOVE = 1e-7;

    public static Vec3 collideWithConstructs(ResourceKey<Level> world, Vec3 entityMovement, AABB entityBoundingBoxWorld) {
        if (entityMovement.lengthSqr() < MIN_MOVE * MIN_MOVE) return entityMovement;

        Vec3 totalCorrection = entityMovement;
        Vec3 boxCenter = entityBoundingBoxWorld.getCenter();

        List<DynamicConstruct> constructs = ConstructManager.INSTANCE.getNearbyConstructs(world, boxCenter, 8);

        for (DynamicConstruct construct : constructs) {
            totalCorrection = collideWithConstruct(totalCorrection, entityBoundingBoxWorld, construct);
        }

        return totalCorrection;
    }

    private static Vec3 collideWithConstruct(Vec3 movement, AABB worldBox, DynamicConstruct construct) {
        Vec3 com = construct.getCenterOfMass();
        Vec3 constructPos = construct.getPosition();
        AABB localBox = worldBox.move(-constructPos.x, -constructPos.y, -constructPos.z);
        List<ConstructCollisionManager.CollisionEntry> entries =
                ConstructManager.INSTANCE.getCollisionManager().getCollisionShapesWithFriction(construct.getId());

        double moveX = movement.x;
        double moveY = movement.y;
        double moveZ = movement.z;

        // --- X axis ---
        if (moveX != 0) {
            AABB movedBox = localBox.move(moveX, 0, 0);
            boolean hadCorrection;
            do {
                hadCorrection = false;
                for (ConstructCollisionManager.CollisionEntry entry : entries) {
                    OBB obb = OBB.fromAABB(entry.shapeBounds.move(com.scale(-1)), new Matrix3f().rotate(construct.getRotation()));
                    if (OBBIntersectionHelper.AABBIntersectsOBB(movedBox, obb)) {
                        Vec3 correction = OBBIntersectionHelper.resolvePenetrationAABBtoOBB(movedBox, obb);
                        if (!correction.equals(Vec3.ZERO)) {
                            moveX += correction.x;
                            movedBox = movedBox.move(correction);
                            hadCorrection = true;
                        }
                    }
                }
            } while (hadCorrection && Math.signum(moveX) == Math.signum(movement.x));
            localBox = localBox.move(moveX, 0, 0);
        }

        // --- Y axis ---
        if (moveY != 0) {
            AABB movedBox = localBox.move(0, moveY, 0);
            boolean hadCorrection;
            do {
                hadCorrection = false;
                for (ConstructCollisionManager.CollisionEntry entry : entries) {
                    OBB obb = OBB.fromAABB(entry.shapeBounds.move(com.scale(-1)), new Matrix3f().rotate(construct.getRotation()));
                    if (OBBIntersectionHelper.AABBIntersectsOBB(movedBox, obb)) {
                        Vec3 correction = OBBIntersectionHelper.resolvePenetrationAABBtoOBB(movedBox, obb);
                        if (!correction.equals(Vec3.ZERO)) {
                            moveY += correction.y;
                            movedBox = movedBox.move(correction);
                            hadCorrection = true;
                        }
                    }
                }
            } while (hadCorrection && Math.signum(moveY) == Math.signum(movement.y));
            localBox = localBox.move(0, moveY, 0);
        }

        // --- Z axis ---
        if (moveZ != 0) {
            AABB movedBox = localBox.move(0, 0, moveZ);
            boolean hadCorrection;
            do {
                hadCorrection = false;
                for (ConstructCollisionManager.CollisionEntry entry : entries) {
                    OBB obb = OBB.fromAABB(entry.shapeBounds.move(com.scale(-1)), new Matrix3f().rotate(construct.getRotation()));
                    if (OBBIntersectionHelper.AABBIntersectsOBB(movedBox, obb)) {
                        Vec3 correction = OBBIntersectionHelper.resolvePenetrationAABBtoOBB(movedBox, obb);
                        if (!correction.equals(Vec3.ZERO)) {
                            moveZ += correction.z;
                            movedBox = movedBox.move(correction);
                            hadCorrection = true;
                        }
                    }
                }
            } while (hadCorrection && Math.signum(moveX) == Math.signum(movement.x));
            localBox = localBox.move(0, 0, moveZ);
        }

        return new Vec3(moveX, moveY, moveZ);
    }
}