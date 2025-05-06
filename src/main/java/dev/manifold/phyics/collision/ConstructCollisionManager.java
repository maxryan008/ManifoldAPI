package dev.manifold.phyics.collision;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

public class ConstructCollisionManager {
    private final Map<UUID, List<CollisionEntry>> shapesByConstruct = new HashMap<>();

    public void updateCollision(UUID id, Level level, BlockPos origin, BlockPos min, BlockPos max) {
        List<CollisionEntry> shapes = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(origin), max.offset(origin))) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (!shape.isEmpty()) {
                    double friction = state.getBlock().getFriction();
                    shapes.add(new CollisionEntry(
                            shape.move(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ()),
                            shape.bounds().move(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ()),
                            friction
                    ));
                }
            }
        }
        shapesByConstruct.put(id, shapes);
    }

    public void remove(UUID id) {
        shapesByConstruct.remove(id);
    }

    public List<CollisionEntry> getCollisionShapesWithFriction(UUID id) {
        return shapesByConstruct.getOrDefault(id, List.of());
    }

    public static class CollisionEntry {
        public final VoxelShape shape;
        public final AABB shapeBounds;
        public final double friction;

        public CollisionEntry(VoxelShape shape, AABB bounds, double friction) {
            this.shape = shape;
            this.shapeBounds = bounds;
            this.friction = friction;
        }
    }
}