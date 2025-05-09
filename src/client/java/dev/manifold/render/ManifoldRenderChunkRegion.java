package dev.manifold.render;

import dev.manifold.Manifold;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class ManifoldRenderChunkRegion implements BlockAndTintGetter {
    protected final Map<Long, ManifoldRenderChunk> chunks;
    protected final Level level;
    private final int chunkCountX;
    private final int chunkCountZ;
    private final int minChunkX;
    private final int minChunkZ;

    public ManifoldRenderChunkRegion(Level level, int minChunkX, int minChunkZ, int chunkCountX, int chunkCountZ, Map<Long, ManifoldRenderChunk> chunks) {
        this.level = level;
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.chunkCountX = chunkCountX;
        this.chunkCountZ = chunkCountZ;
        this.chunks = chunks;
    }

    public static long chunkPosToLong(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    @Override
    public @NotNull BlockState getBlockState(BlockPos pos) {
        Optional<ManifoldRenderChunk> chunk = getChunk(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())
        );
        return chunk.map(manifoldRenderChunk -> manifoldRenderChunk.getBlockState(pos)).orElse(Blocks.AIR.defaultBlockState());
    }

    @Override
    public @NotNull FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public float getShade(Direction direction, boolean ambientDarkening) {
        return level.getShade(direction, ambientDarkening);
    }

    @Override
    public @NotNull LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        Optional<ManifoldRenderChunk> chunk = getChunk(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())
        );
        return chunk.map(manifoldRenderChunk -> manifoldRenderChunk.getBlockEntity(pos)).orElse(null);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return level.getBlockTint(pos, colorResolver);
    }

    @Override
    public int getMinBuildHeight() {
        return level.getMinBuildHeight();
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    private Optional<ManifoldRenderChunk> getChunk(int chunkX, int chunkZ) {
        int dx = chunkX - minChunkX;
        int dz = chunkZ - minChunkZ;
        if (dx < 0 || dx >= chunkCountX || dz < 0 || dz >= chunkCountZ) {
            Manifold.LOGGER.warn("Chunk index out of bounds: {}, {}", chunkX, chunkZ);
            return Optional.empty();
        }
        return Optional.of(chunks.get(chunkPosToLong(chunkX, chunkZ)));
    }

    public Map<Long, ManifoldRenderChunk> getChunks() {
        return chunks;
    }
}
