package dev.manifold;

import dev.manifold.mixin.accessor.EntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

public class SimLevel extends ServerLevel {
    public SimLevel(MinecraftServer minecraftServer, Executor executor, LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData, ResourceKey<Level> resourceKey, LevelStem levelStem, ChunkProgressListener chunkProgressListener, boolean bl, long l, List<CustomSpawner> list, boolean bl2, @Nullable RandomSequences randomSequences) {
        super(minecraftServer, executor, levelStorageAccess, serverLevelData, resourceKey, levelStem, chunkProgressListener, bl, l, list, bl2, randomSequences);
    }

    @Override
    public boolean setBlock(BlockPos blockPos, BlockState blockState, int i, int j) {
        return super.setBlock(blockPos, blockState, i, j);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        Optional<UUID> uuid = ConstructManager.INSTANCE.getConstructAt(entity.position());
        if (uuid.isPresent()) {
            UUID construct = uuid.get();
            Optional<ServerLevel> level = ConstructManager.INSTANCE.getRenderLevel(construct);
            Optional<Quaternionf> rotation = ConstructManager.INSTANCE.getRotation(construct);
            if (level.isPresent() && rotation.isPresent()) {
                ((EntityAccessor) entity).setLevel(level.get());
                entity.setPos(ConstructManager.INSTANCE.getRenderPosFromSim(construct, entity.position()));
                Vec3 velocity = entity.getDeltaMovement();
                Vector3f rotatedVelocity = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
                rotatedVelocity.rotate(rotation.get());
                entity.setDeltaMovement(new Vec3(rotatedVelocity));
                level.get().addFreshEntity(entity);
                return true;
            }
        }
        return false;
    }

    @Override
    public int getBrightness(LightLayer lightLayer, BlockPos blockPos) {
        Optional<UUID> uuid = ConstructManager.INSTANCE.getConstructAt(Vec3.atLowerCornerOf(blockPos));
        if (uuid.isPresent()) {
            UUID construct = uuid.get();
            Optional<ServerLevel> level = ConstructManager.INSTANCE.getRenderLevel(construct);
            if (level.isPresent()) {
                Vec3 position = ConstructManager.INSTANCE.getRenderPosFromSim(construct, Vec3.atLowerCornerOf(blockPos));
                if (lightLayer == LightLayer.SKY) {
                    return level.get().getLightEngine().getLayerListener(lightLayer).getLightValue(BlockPos.containing(position));
                } else if (lightLayer == LightLayer.BLOCK) {
                    return ConstructManager.INSTANCE.getSimDimension().getLightEngine().getLayerListener(lightLayer).getLightValue(blockPos);
                }
            }
        }
        return 0;
    }
}
