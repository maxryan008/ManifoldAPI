package dev.manifold;

import dev.manifold.render.ManifoldRenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockGetter implements BlockAndTintGetter, LightChunkGetter {
    private final ManifoldRenderChunkRegion region;

    public BlockGetter(ManifoldRenderChunkRegion region) {
        this.region = region;
    }

    @Override
    public int getBrightness(LightLayer lightLayer, BlockPos blockPos) {
        return region.getBrightness(lightLayer, blockPos);
    }

    @Override
    public int getRawBrightness(BlockPos blockPos, int i) {
        return region.getRawBrightness(blockPos, i);
    }

    @Override
    public @NotNull BlockState getBlockState(BlockPos pos) {
        return region.getBlockState(pos);
    }

    @Nullable
    @Override
    public LightChunk getChunkForLighting(int x, int z) {
        return region.getChunkForLighting(x, z);
    }

    @Override
    public BlockGetter getLevel() {
        return this;
    }

    @Override
    public @NotNull FluidState getFluidState(BlockPos pos) {
        return region.getFluidState(pos);
    }

    @Override
    public float getShade(Direction direction, boolean ambientDarkening) {
        return region.getShade(direction, ambientDarkening);
    }

    @Override
    public @NotNull LevelLightEngine getLightEngine() {
        return region.getLightEngine();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return region.getBlockEntity(pos);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return region.getBlockTint(pos, colorResolver);
    }

    @Override
    public int getMinBuildHeight() {
        return region.getMinBuildHeight();
    }

    @Override
    public int getHeight() {
        return region.getHeight();
    }
}
