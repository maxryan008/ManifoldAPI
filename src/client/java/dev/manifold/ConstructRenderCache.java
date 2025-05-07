package dev.manifold;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.manifold.mixin.accessor.SectionRenderDispatcher_CompiledSectionAccessor;
import dev.manifold.mixin.accessor.SectionRenderDispatcher_RenderSectionAccessor;
import dev.manifold.render.ManifoldRenderChunk;
import dev.manifold.render.ManifoldRenderChunkRegion;
import dev.manifold.render.ManifoldRenderSection;
import dev.manifold.render.ManifoldSectionCompiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ConstructRenderCache {

    private final Minecraft MC = Minecraft.getInstance();
    private final SectionRenderDispatcher dispatcher = MC.levelRenderer.getSectionRenderDispatcher();
    private final ManifoldSectionCompiler compiler = new ManifoldSectionCompiler(MC.getBlockRenderer(), MC.getBlockEntityRenderDispatcher());
    private final RenderBuffers buffers = MC.renderBuffers();
    private final HashMap<UUID, CachedConstruct> renderSections = new HashMap<>();
    private final ArrayList<UUID> markedForRemoval = new ArrayList<>();

    private static void renderShape(
            PoseStack poseStack, VertexConsumer vertexConsumer, VoxelShape voxelShape, double d, double e, double f, float r, float g, float b, float a
    ) {
        PoseStack.Pose pose = poseStack.last();
        voxelShape.forAllEdges((k, l, m, n, o, p) -> {
            float q = (float) (n - k);
            float c = (float) (o - l);
            float s = (float) (p - m);
            float t = Mth.sqrt(q * q + c * c + s * s);
            q /= t;
            c /= t;
            s /= t;
            vertexConsumer.addVertex(pose, (float) (k + d), (float) (l + e), (float) (m + f)).setColor(r, g, b, a).setNormal(pose, q, c, s);
            vertexConsumer.addVertex(pose, (float) (n + d), (float) (o + e), (float) (p + f)).setColor(r, g, b, a).setNormal(pose, q, c, s);
        });
    }

    @SuppressWarnings("DataFlowIssue")
    public void uploadMesh(UUID id, BlockPos origin, ManifoldRenderChunkRegion region) {
        List<ManifoldRenderSection> sectionList = new ArrayList<>();

        for (ManifoldRenderChunk renderChunk : region.getChunks().values()) {
            LevelChunk chunk = renderChunk.getWrappedChunk();
            LevelChunkSection[] sectionsArray = chunk.getSections();

            for (int y = 0; y < sectionsArray.length; y++) {
                LevelChunkSection section = sectionsArray[y];
                if (section == null || section.hasOnlyAir()) continue; // Skip air-only sections

                // This sections exists, so compile it
                int sectionX = chunk.getPos().x;
                int sectionY = y + chunk.getMinSection(); // VERY IMPORTANT to offset
                int sectionZ = chunk.getPos().z;

                BlockPos sectionOrigin = new BlockPos(
                        SectionPos.sectionToBlockCoord(sectionX),
                        SectionPos.sectionToBlockCoord(sectionY),
                        SectionPos.sectionToBlockCoord(sectionZ)
                );

                BlockPos sectionOffset = new BlockPos(sectionOrigin.getX() - origin.getX(), sectionOrigin.getY() - origin.getY(), sectionOrigin.getZ() - origin.getZ());

                SectionPos sectionPos = SectionPos.of(sectionOrigin);

                SectionRenderDispatcher.RenderSection renderSection = dispatcher.new RenderSection(0, sectionOrigin.getX(), sectionOrigin.getY(), sectionOrigin.getZ());

                SectionCompiler.Results results = compiler.compile(
                        sectionPos,
                        new BlockGetter(region),
                        ((SectionRenderDispatcher_RenderSectionAccessor) renderSection).manifold$createVertexSorting(),
                        buffers.fixedBufferPack()
                );

                SectionRenderDispatcher.CompiledSection compiled = new SectionRenderDispatcher.CompiledSection();
                ((SectionRenderDispatcher_CompiledSectionAccessor) compiled).manifold$setVisibilitySet(results.visibilitySet);
                ((SectionRenderDispatcher_CompiledSectionAccessor) compiled).manifold$getRenderableBlockEntities().addAll(results.blockEntities);
                ((SectionRenderDispatcher_CompiledSectionAccessor) compiled).manifold$setTransparencyState(results.transparencyState);
                ((SectionRenderDispatcher_CompiledSectionAccessor) compiled).manifold$getHasBlocks().addAll(results.renderedLayers.keySet());

                for (Map.Entry<RenderType, MeshData> entry : results.renderedLayers.entrySet()) {
                    dispatcher.uploadSectionLayer(entry.getValue(), renderSection.getBuffer(entry.getKey()));
                }

                AtomicReference<SectionRenderDispatcher.CompiledSection> ref =
                        ((SectionRenderDispatcher_RenderSectionAccessor) renderSection).getCompiled();
                ref.set(compiled);

                sectionList.add(new ManifoldRenderSection(renderSection, sectionOffset));
            }
        }

        Optional<Vec3> renderPositionOptional = ConstructManager.INSTANCE.getPosition(id);
        Optional<Quaternionf> renderRotationOptional = ConstructManager.INSTANCE.getRotation(id);
        Optional<Vec3> centerOfMassOptional = ConstructManager.INSTANCE.getCenterOfMass(id);

        if (renderPositionOptional.isPresent() && renderRotationOptional.isPresent() && centerOfMassOptional.isPresent()) {
            Vec3 newPos = renderPositionOptional.get();
            Quaternionf newRot = renderRotationOptional.get();
            Vec3 newCOM = centerOfMassOptional.get();

            CachedConstruct prev = renderSections.get(id);

            if (prev != null) {
                boolean comChanged = !prev.centerOfMass.equals(newCOM);

                renderSections.put(id, new CachedConstruct(
                        id,
                        origin,
                        sectionList,
                        comChanged ? newPos : prev.currentPosition,
                        newPos,
                        prev.currentRotation,
                        newRot,
                        newCOM
                ));
            } else {
                renderSections.put(id, new CachedConstruct(
                        id,
                        origin,
                        sectionList,
                        newPos,
                        newPos,
                        newRot,
                        newRot,
                        newCOM
                ));
            }

            ManifoldClient.lastServerUpdateTime = System.currentTimeMillis();
        }
    }

    public void renderSections(PoseStack stack, Vec3 camPos, float deltaTicks) {
        Quaternionf cameraRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Quaternionf cameraInverseRot = cameraRot.invert();

        if (!markedForRemoval.isEmpty()) {
            renderSections.keySet().removeAll(markedForRemoval);
            markedForRemoval.clear();
        }
        Collection<CachedConstruct> constructs = renderSections.values();

        for (CachedConstruct cached : constructs) {
            List<ManifoldRenderSection> sectionArray = cached.sections();
            for (ManifoldRenderSection section : sectionArray) {
                SectionRenderDispatcher.CompiledSection compiled = ((SectionRenderDispatcher_RenderSectionAccessor) section.section()).getCompiled().get();

                Vec3 interpolatedPos = cached.prevPosition.lerp(cached.currentPosition, deltaTicks);
                Quaternionf interpolatedRot = new Quaternionf(cached.prevRotation).slerp(cached.currentRotation, deltaTicks);
                Vec3 com = cached.centerOfMass;
                Vec3 sectionPos = Vec3.atLowerCornerOf(section.offset());

                stack.pushPose();
                stack.mulPose(cameraInverseRot);

                // Translate to world pos minus COM
                stack.translate(interpolatedPos.x - camPos.x, interpolatedPos.y - camPos.y, interpolatedPos.z - camPos.z);
                stack.mulPose(interpolatedRot);
                stack.translate(sectionPos.x - com.x, sectionPos.y - com.y, sectionPos.z - com.z);


                RenderSystem.enableDepthTest();
                RenderSystem.enableBlend();
                for (RenderType type : RenderType.chunkBufferLayers()) {
                    if (!compiled.isEmpty(type)) {
                        VertexBuffer buffer = section.section().getBuffer(type);

                        type.setupRenderState(); // This binds the correct shader and sets projection
                        Matrix4f poseMatrix = stack.last().pose();

                        buffer.bind();
                        buffer.drawWithShader(poseMatrix, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                        VertexBuffer.unbind();

                        type.clearRenderState();
                    }
                }

                VertexBuffer.unbind();
                stack.popPose();
            }
        }
    }

    public void renderOutline(PoseStack stack, Vec3 camPos, float deltaTicks) {
        stack.pushPose();

        if (ManifoldClient.lastConstructHit != null) {
            ConstructBlockHitResult hitResult = ManifoldClient.lastConstructHit;

            CachedConstruct cached = hitResult.getConstruct();
            Vec3 interpolatedPosition = cached.prevPosition.lerp(cached.currentPosition, deltaTicks);
            Quaternionf interpolatedRotation = new Quaternionf(cached.prevRotation).slerp(cached.currentRotation, deltaTicks);
            Vec3 com = cached.centerOfMass;
            Vec3 blockOffset = Vec3.atLowerCornerOf(hitResult.getBlockPos().subtract(hitResult.getConstruct().origin));

            stack.translate(interpolatedPosition.x - camPos.x, interpolatedPosition.y - camPos.y, interpolatedPosition.z - camPos.z);
            stack.mulPose(interpolatedRotation);
            stack.translate(blockOffset.x - com.x, blockOffset.y - com.y, blockOffset.z - com.z);

            VertexConsumer consumer = MC.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            renderShape(
                    stack,
                    consumer,
                    hitResult.getShape(),
                    0, 0, 0, // Already translated
                    1.0F, 0.0F, 0.0F, 1.0F
            );

            MC.renderBuffers().outlineBufferSource().endOutlineBatch();
        }

        stack.popPose();
    }

    public HashMap<UUID, CachedConstruct> getRenderedConstructs() {
        return this.renderSections;
    }

    public void markForRemoval(UUID uuid) {
        this.markedForRemoval.add(uuid);
    }

    public record CachedConstruct(
            UUID id,
            BlockPos origin,
            List<ManifoldRenderSection> sections,
            Vec3 prevPosition,
            Vec3 currentPosition,
            Quaternionf prevRotation,
            Quaternionf currentRotation,
            Vec3 centerOfMass
    ) {
    }
}
