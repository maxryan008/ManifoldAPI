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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ConstructRenderCache {

    private static final Logger log = LoggerFactory.getLogger(ConstructRenderCache.class);
    private final Minecraft MC = Minecraft.getInstance();
    private final SectionRenderDispatcher dispatcher = MC.levelRenderer.getSectionRenderDispatcher();
    private final ManifoldSectionCompiler compiler = new ManifoldSectionCompiler(MC.getBlockRenderer(), MC.getBlockEntityRenderDispatcher());
    private final RenderBuffers buffers = MC.renderBuffers();
    private final HashMap<UUID, CachedConstruct> renderSections = new HashMap<>();

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

    private static AABB rotateAABB(AABB box, Vec3 center, Quaternionf rotation) {
        Vector3f[] corners = {
                new Vector3f((float) box.minX, (float) box.minY, (float) box.minZ),
                new Vector3f((float) box.maxX, (float) box.minY, (float) box.minZ),
                new Vector3f((float) box.minX, (float) box.maxY, (float) box.minZ),
                new Vector3f((float) box.minX, (float) box.minY, (float) box.maxZ),
                new Vector3f((float) box.maxX, (float) box.maxY, (float) box.minZ),
                new Vector3f((float) box.minX, (float) box.maxY, (float) box.maxZ),
                new Vector3f((float) box.maxX, (float) box.minY, (float) box.maxZ),
                new Vector3f((float) box.maxX, (float) box.maxY, (float) box.maxZ)
        };

        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);

        for (Vector3f corner : corners) {
            corner.sub(new Vector3f((float) center.x, (float) center.y, (float) center.z));
            corner.rotate(rotation);
            corner.add(new Vector3f((float) center.x, (float) center.y, (float) center.z));
            min.min(corner);
            max.max(corner);
        }

        return new AABB(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

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

        Optional<Vec3> renderPosOptional = ConstructManager.INSTANCE.getPosition(id);
        if (renderPosOptional.isPresent()) {
            if (renderSections.containsKey(id)) {
                renderSections.put(id, new CachedConstruct(id, origin, sectionList, renderSections.get(id).currentPosition, renderPosOptional.get()));
            } else {
                renderSections.put(id, new CachedConstruct(id, origin, sectionList, renderPosOptional.get(), renderPosOptional.get()));
            }
        }
        // update global timer
        ManifoldClient.lastServerUpdateTime = System.currentTimeMillis();
    }

    public void renderSections(PoseStack stack, Vec3 camPos, float deltaTicks) {
        Quaternionf cameraRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Quaternionf cameraInverseRot = cameraRot.invert();
        Collection<CachedConstruct> constructs = renderSections.values();

        for (CachedConstruct cached : constructs) {
            List<ManifoldRenderSection> sectionArray = cached.sections();
            for (ManifoldRenderSection section : sectionArray) {
                SectionRenderDispatcher.CompiledSection compiled = ((SectionRenderDispatcher_RenderSectionAccessor) section.section()).getCompiled().get();

                Vec3 renderPos = cached.prevPosition.lerp(cached.currentPosition, deltaTicks).add(Vec3.atLowerCornerOf(section.offset()));
                stack.pushPose();

                stack.mulPose(cameraInverseRot);
                stack.mulPose(ConstructManager.INSTANCE.getRotation(cached.id));
                stack.translate(renderPos.x - camPos.x, renderPos.y - camPos.y, renderPos.z - camPos.z);

                RenderSystem.enableDepthTest();
                RenderSystem.enableBlend();
                RenderSystem.disableCull(); // Optional
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
        Quaternionf cameraRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        stack.pushPose();

        if (ManifoldClient.lastConstructHit != null) {
            ConstructBlockHitResult hitResult = ManifoldClient.lastConstructHit;

            Vec3 renderPos = hitResult.getConstruct().prevPosition.lerp(hitResult.getConstruct().currentPosition, deltaTicks).add(Vec3.atLowerCornerOf(hitResult.getBlockPos())).subtract(Vec3.atLowerCornerOf(hitResult.getConstruct().origin));

            try {
                stack.mulPose(ConstructManager.INSTANCE.getRotation(hitResult.getConstruct().id()));
                stack.translate(-camPos.x, -camPos.y, -camPos.z);

                RenderType type = RenderType.lines();

                VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(type);

                renderShape(
                        stack,
                        consumer,
                        hitResult.getShape(),
                        renderPos.x,
                        renderPos.y,
                        renderPos.z,
                        1.0F,
                        0.0F,
                        0.0F,
                        1.0F
                );

            } catch (Throwable e) {
                log.error(String.valueOf(e));
            }

            Minecraft.getInstance().renderBuffers().outlineBufferSource().endOutlineBatch();
        }
        stack.popPose();
    }

    public HashMap<UUID, CachedConstruct> getRenderedConstructs() {
        return this.renderSections;
    }

    public record CachedConstruct(UUID id, BlockPos origin, List<ManifoldRenderSection> sections, Vec3 prevPosition,
                                  Vec3 currentPosition) {
        public Matrix4f transform(float deltaTicks, Vec3 camPos) {
            // Interpolate render position
            Vec3 renderPos = prevPosition.lerp(currentPosition, deltaTicks);

            // Construct the transform matrix
            Matrix4f transform = new Matrix4f();

            // Apply rotation from ConstructManager
            transform.rotate(ConstructManager.INSTANCE.getRotation(id));

            // Apply translation (renderPos - camPos)
            transform.translate(new Vector3f(
                    (float) (renderPos.x - camPos.x),
                    (float) (renderPos.y - camPos.y),
                    (float) (renderPos.z - camPos.z)
            ));

            return transform;
        }

        public AABB getTransformedAABB(float alpha) {
            Vec3 interpPos = prevPosition.lerp(currentPosition, alpha);
            Quaternionf rotation = ConstructManager.INSTANCE.getRotation(id);
            AABB localBounds = ConstructManager.INSTANCE.getRenderAABB(id);
            AABB rotated = rotateAABB(localBounds, localBounds.getCenter(), rotation);
            return localBounds;
        }
    }
}
