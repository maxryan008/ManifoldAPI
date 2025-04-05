package dev.manifold;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.manifold.mixin.accessor.SectionRenderDispatcher_CompiledSectionAccessor;
import dev.manifold.mixin.accessor.SectionRenderDispatcher_RenderSectionAccessor;
import dev.manifold.render.ManifoldRenderChunkRegion;
import dev.manifold.render.ManifoldSectionCompiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ConstructRenderCache {
    record CachedConstruct(UUID id, BlockPos origin, SectionRenderDispatcher.RenderSection section, Vec3 prevPosition, Vec3 currentPosition) {}

    private final Minecraft MC = Minecraft.getInstance();
    private final SectionRenderDispatcher dispatcher = MC.levelRenderer.getSectionRenderDispatcher();
    private final ManifoldSectionCompiler compiler = new ManifoldSectionCompiler(MC.getBlockRenderer(), MC.getBlockEntityRenderDispatcher());
    private final RenderBuffers buffers = MC.renderBuffers();

    private final HashMap<UUID, CachedConstruct> renderSections = new HashMap<>();

    public void uploadMesh(UUID id, BlockPos origin, ManifoldRenderChunkRegion region) {
        SectionPos sectionPos = SectionPos.of(origin);
        SectionRenderDispatcher.RenderSection section = dispatcher.new RenderSection(0, origin.getX(), origin.getY(), origin.getZ());

        SectionCompiler.Results results = compiler.compile(
                sectionPos,
                region,
                ((SectionRenderDispatcher_RenderSectionAccessor) section).manifold$createVertexSorting(),
                buffers.fixedBufferPack()
        );

        SectionRenderDispatcher.CompiledSection compiled = new SectionRenderDispatcher.CompiledSection();
        ((SectionRenderDispatcher_CompiledSectionAccessor) compiled).manifold$setVisibilitySet(results.visibilitySet);
        ((SectionRenderDispatcher_CompiledSectionAccessor) compiled).manifold$getRenderableBlockEntities().addAll(results.blockEntities);
        ((SectionRenderDispatcher_CompiledSectionAccessor) compiled).manifold$setTransparencyState(results.transparencyState);
        ((SectionRenderDispatcher_CompiledSectionAccessor) compiled).manifold$getHasBlocks().addAll(results.renderedLayers.keySet());

        for (Map.Entry<RenderType, MeshData> entry : results.renderedLayers.entrySet()) {
            dispatcher.uploadSectionLayer(entry.getValue(), section.getBuffer(entry.getKey()));
        }

        AtomicReference<SectionRenderDispatcher.CompiledSection> ref =
                ((SectionRenderDispatcher_RenderSectionAccessor) section).getCompiled();
        ref.set(compiled);

        Optional<Vec3> renderPosOptional = ConstructManager.INSTANCE.getPosition(id);
        if (renderPosOptional.isPresent()) {
            if (renderSections.containsKey(id)) {
                renderSections.put(id, new CachedConstruct(id, origin, section, renderSections.get(id).currentPosition, renderPosOptional.get()));
            } else {
                renderSections.put(id, new CachedConstruct(id, origin, section, renderPosOptional.get(), renderPosOptional.get()));
            }
        }
        // update global timer
        ManifoldClient.lastServerUpdateTime = System.currentTimeMillis();
    }

    public void renderAll(PoseStack stack, Vec3 camPos, float deltaTicks) {
        Quaternionf cameraRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Quaternionf cameraInverseRot = cameraRot.invert();
        Collection<CachedConstruct> constructs = renderSections.values();
        for (CachedConstruct cached : constructs) {
            SectionRenderDispatcher.RenderSection section = cached.section();
            SectionRenderDispatcher.CompiledSection compiled = ((SectionRenderDispatcher_RenderSectionAccessor) section).getCompiled().get();

            Vec3 renderPos = cached.prevPosition.lerp(cached.currentPosition, deltaTicks);
            stack.pushPose();

            stack.mulPose(cameraInverseRot);
            stack.mulPose(ConstructManager.INSTANCE.getRotation(cached.id));
            stack.translate(renderPos.x - camPos.x, renderPos.y - camPos.y, renderPos.z - camPos.z);

            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.disableCull(); // Optional
            for (RenderType type : RenderType.chunkBufferLayers()) {
                if (!compiled.isEmpty(type)) {
                    VertexBuffer buffer = section.getBuffer(type);

                    type.setupRenderState(); // This binds the correct shader and sets projection
                    Matrix4f poseMatrix = stack.last().pose();

                    buffer.bind();
                    buffer.drawWithShader(poseMatrix, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                    VertexBuffer.unbind();

                    type.clearRenderState();
                }
            }
            stack.popPose();
            stack.pushPose();

            LevelRenderer.renderLineBox(
                    stack, buffers.bufferSource().getBuffer(RenderType.lines()),
                    AABB.ofSize(renderPos, 1.0, 1.0, 1.0),
                    1.0f, 1.0f, 1.0f, 1.0f
            );

            VertexBuffer.unbind();
            stack.popPose();
        }
    }
}
