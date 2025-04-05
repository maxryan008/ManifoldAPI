package dev.manifold.mixin.accessor;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(SectionCompiler.class)
public interface SectionCompilerAccessor {

    @Accessor("blockRenderer")
    BlockRenderDispatcher manifold$getBlockRenderer();

    @Invoker("handleBlockEntity")
    void manifold$handleBlockEntity(SectionCompiler.Results results, BlockEntity blockEntity);

    @Invoker("getOrBeginLayer")
    BufferBuilder manifold$getOrBeginLayer(Map<RenderType, BufferBuilder> layerMap, SectionBufferBuilderPack pack, RenderType renderType);
}