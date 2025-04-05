package dev.manifold.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.manifold.mixin.accessor.SectionCompilerAccessor;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.Map;

@Environment(EnvType.CLIENT)
public class ManifoldSectionCompiler extends SectionCompiler {

    public ManifoldSectionCompiler(BlockRenderDispatcher blockRenderer, BlockEntityRenderDispatcher blockEntityRenderer) {
        super(blockRenderer, blockEntityRenderer);
    }

    public SectionCompiler.Results compile(
            SectionPos sectionPos, ManifoldRenderChunkRegion renderChunkRegion, VertexSorting vertexSorting, SectionBufferBuilderPack sectionBufferBuilderPack
    ) {
        SectionCompiler.Results results = new SectionCompiler.Results();
        BlockPos blockPos = sectionPos.origin();
        BlockPos blockPos2 = blockPos.offset(15, 15, 15);
        VisGraph visGraph = new VisGraph();
        PoseStack poseStack = new PoseStack();
        ModelBlockRenderer.enableCaching();
        Map<RenderType, BufferBuilder> map = new Reference2ObjectArrayMap<>(RenderType.chunkBufferLayers().size());
        RandomSource randomSource = RandomSource.create();

        for (BlockPos blockPos3 : BlockPos.betweenClosed(blockPos, blockPos2)) {
            BlockState blockState = renderChunkRegion.getBlockState(blockPos3);
            if (blockState.isSolidRender(renderChunkRegion, blockPos3)) {
                visGraph.setOpaque(blockPos3);
            }

            if (blockState.hasBlockEntity()) {
                BlockEntity blockEntity = renderChunkRegion.getBlockEntity(blockPos3);
                if (blockEntity != null) {
                    ((SectionCompilerAccessor) this).manifold$handleBlockEntity(results, blockEntity);
                }
            }

            FluidState fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                RenderType renderType = ItemBlockRenderTypes.getRenderLayer(fluidState);
                BufferBuilder bufferBuilder = ((SectionCompilerAccessor) this).manifold$getOrBeginLayer(map, sectionBufferBuilderPack, renderType);
                ((SectionCompilerAccessor) this).manifold$getBlockRenderer().renderLiquid(blockPos3, renderChunkRegion, bufferBuilder, blockState, fluidState);
            }

            if (blockState.getRenderShape() == RenderShape.MODEL) {
                RenderType renderType = ItemBlockRenderTypes.getChunkRenderType(blockState);
                BufferBuilder bufferBuilder = ((SectionCompilerAccessor) this).manifold$getOrBeginLayer(map, sectionBufferBuilderPack, renderType);
                poseStack.pushPose();
                poseStack.translate(
                        (float)SectionPos.sectionRelative(blockPos3.getX()),
                        (float)SectionPos.sectionRelative(blockPos3.getY()),
                        (float)SectionPos.sectionRelative(blockPos3.getZ())
                );
                ((SectionCompilerAccessor) this).manifold$getBlockRenderer().renderBatched(blockState, blockPos3, renderChunkRegion, poseStack, bufferBuilder, true, randomSource);
                poseStack.popPose();
            }
        }

        for (Map.Entry<RenderType, BufferBuilder> entry : map.entrySet()) {
            RenderType renderType2 = (RenderType)entry.getKey();
            MeshData meshData = ((BufferBuilder)entry.getValue()).build();
            if (meshData != null) {
                if (renderType2 == RenderType.translucent()) {
                    results.transparencyState = meshData.sortQuads(sectionBufferBuilderPack.buffer(RenderType.translucent()), vertexSorting);
                }

                results.renderedLayers.put(renderType2, meshData);
            }
        }

        ModelBlockRenderer.clearCache();
        results.visibilitySet = visGraph.resolve();
        return results;
    }
}