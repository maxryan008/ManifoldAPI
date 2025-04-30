package dev.manifold;

import dev.manifold.render.ManifoldRenderChunkRegion;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ConstructBlockHitResult extends BlockHitResult {
    private final ConstructRenderCache.CachedConstruct construct;
    private final VoxelShape shape;
    private final ManifoldRenderChunkRegion region;

    public ConstructBlockHitResult(ConstructRenderCache.CachedConstruct construct, VoxelShape shape, BlockHitResult base, ManifoldRenderChunkRegion region) {
        super(base.getLocation(), base.getDirection(), base.getBlockPos(), base.isInside());
        this.shape = shape;
        this.construct = construct;
        this.region = region;
    }

    public ConstructRenderCache.CachedConstruct getConstruct() {
        return construct;
    }

    public VoxelShape getShape() {
        return shape;
    }

    public ManifoldRenderChunkRegion getRegion() {
        return region;
    }

    @Override
    public Type getType() {
        return Type.BLOCK;
    }
}
