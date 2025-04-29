package dev.manifold;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ConstructBlockHitResult extends BlockHitResult {
    private final ConstructRenderCache.CachedConstruct construct;
    private final VoxelShape shape;

    public ConstructBlockHitResult(ConstructRenderCache.CachedConstruct construct, VoxelShape shape, BlockHitResult base) {
        super(base.getLocation(), base.getDirection(), base.getBlockPos(), base.isInside());
        this.shape = shape;
        this.construct = construct;
    }

    public ConstructRenderCache.CachedConstruct getConstruct() {
        return construct;
    }

    public VoxelShape getShape() {
        return shape;
    }

    @Override
    public Type getType() {
        return Type.BLOCK;
    }
}
