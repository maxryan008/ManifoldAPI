package dev.manifold;

import dev.manifold.network.packets.ConstructSectionDataS2CPacket;
import dev.manifold.render.ManifoldRenderChunk;
import dev.manifold.render.ManifoldRenderChunkRegion;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.*;

@Environment(EnvType.CLIENT)
public class ManifoldClient implements ClientModInitializer {
	private static ConstructRenderCache renderer = null;
	public static @Nullable ManifoldRenderChunkRegion currentConstructRegion;
	public static long lastServerUpdateTime = System.currentTimeMillis();
	public static final long SERVER_TICK_MS = 50L;
	public static @Nullable ConstructBlockHitResult lastConstructHit;
	private static boolean wasUseKeyDown = false;

	private static Map<UUID, ManifoldRenderChunkRegion> regions = new HashMap<>();

	@Override
	public void onInitializeClient() {
		Manifold.LOGGER.info("Starting Client Initialization");

		// Initialize renderer later once levelRenderer is non-null
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (renderer == null && client.level != null) {
				renderer = new ConstructRenderCache();
				Manifold.LOGGER.info("ConstructRenderCache initialized.");
			}
		});

		// Register world rendering hook for sections
		WorldRenderEvents.END.register(context -> {
			if (Minecraft.getInstance().player == null || renderer == null) return;
			Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
			Vec3 camPos = camera.getPosition();
			long now = System.currentTimeMillis();
			float alpha = Mth.clamp((now - lastServerUpdateTime) / (float) SERVER_TICK_MS, 0f, 1f);
			renderer.renderSections(context.matrixStack(), camPos, alpha);
		});

		// Register world rendering hook for outline
		WorldRenderEvents.AFTER_ENTITIES.register((worldRenderContext) -> {
			if (Minecraft.getInstance().player == null || renderer == null) return;
			Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
			Vec3 camPos = camera.getPosition();
			long now = System.currentTimeMillis();
			float alpha = Mth.clamp((now - lastServerUpdateTime) / (float) SERVER_TICK_MS, 0f, 1f);
			renderer.renderOutline(worldRenderContext.matrixStack(), camPos, alpha);
        });

		ClientPlayNetworking.registerGlobalReceiver(
				ConstructSectionDataS2CPacket.TYPE,
				(packet, context) -> {
					context.client().execute(() -> {
						if (renderer == null) return; // Still waiting for renderer

						List<CompoundTag> chunkNbtList = packet.chunkNbtList();
						int countX = packet.chunkSizeX();
						int countZ = packet.chunkSizeZ();

						Level level = Minecraft.getInstance().level;
						Map<Long, ManifoldRenderChunk> chunkArray = new HashMap<Long, ManifoldRenderChunk>();

						for (int i = 0; i < chunkNbtList.size(); i++) {
							CompoundTag tag = chunkNbtList.get(i);
							ChunkPos pos = new ChunkPos(tag.getInt("xPos"), tag.getInt("zPos"));
                            LevelChunk chunk = new LevelChunk(ConstructManager.INSTANCE.getSimDimension(), pos);

                            if (tag.contains("Sections", Tag.TAG_COMPOUND)) {
                                CompoundTag sectionsTag = tag.getCompound("Sections");
                                for (String yStr : sectionsTag.getAllKeys()) {
                                    try {
                                        int y = Integer.parseInt(yStr);
                                        byte[] sectionBytes = sectionsTag.getByteArray(yStr);

                                        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(sectionBytes));
                                        assert level != null;
                                        LevelChunkSection section = new LevelChunkSection(level.registryAccess().registryOrThrow(Registries.BIOME));
                                        section.read(buf);

                                        chunk.getSections()[y - chunk.getMinSection()] = section;
                                    } catch (Exception e) {
                                        Manifold.LOGGER.warn("Failed to load sections y={} for chunk {}", yStr, pos, e);
                                    }
                                }
                            }

							chunkArray.put(ManifoldRenderChunkRegion.chunkPosToLong(pos.x, pos.z),new ManifoldRenderChunk(chunk));
                        }

						ManifoldRenderChunkRegion region = new ManifoldRenderChunkRegion(
								level, packet.minChunkX(), packet.minChunkZ(), countX, countZ, chunkArray
						);

						regions.put(packet.constructId(), region);

						renderer.uploadMesh(packet.constructId(), packet.origin(), region);
					});
				}
		);
	}

	public static ConstructRenderCache getRenderer() {
		return renderer;
	}

	public static Optional<ConstructBlockHitResult> pickConstructHit(Vec3 cameraPos, Vec3 viewVec, double maxDistance, Entity player) {
		if (renderer == null) return Optional.empty();
		Level simLevel = ConstructManager.INSTANCE.getSimDimension();
		if (simLevel == null) return null;
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		Vec3 camPos = camera.getPosition();
		long now = System.currentTimeMillis();
		float alpha = Mth.clamp((now - lastServerUpdateTime) / (float) SERVER_TICK_MS, 0f, 1f);

		Vec3 rayEnd = cameraPos.add(viewVec.scale(maxDistance));
		ConstructBlockHitResult closestHit = null;
		double closestDistSq = maxDistance * maxDistance;

		for (ConstructRenderCache.CachedConstruct construct : renderer.getRenderedConstructs().values()) {
			//Compute inverse rotation and position
			Vec3 interpolatedConstructPosition = construct.prevPosition().lerp(construct.currentPosition(), alpha);
			Quaternionf interpolatedRotation = new Quaternionf(construct.prevRotation()).slerp(construct.currentRotation(), alpha);
			Quaternionf inverseInterpolatedRotation = new Quaternionf(interpolatedRotation).invert();
			BlockPos simOrigin = construct.origin();

			//Transform ray into construct-local space (unrotated frame)
			Vec3 localStart = rotateVec(cameraPos.subtract(interpolatedConstructPosition), inverseInterpolatedRotation);
			Vec3 localEnd = rotateVec(rayEnd.subtract(interpolatedConstructPosition), inverseInterpolatedRotation);

			localStart = localStart.add(interpolatedConstructPosition);
			localEnd = localEnd.add(interpolatedConstructPosition);

			//Shift AABB to construct-local space
			AABB localAABB = ConstructManager.INSTANCE.getRenderAABB(construct.id());

			//Perform early rejection (stay in local space!)
			if (!localAABB.contains(localStart) && !localAABB.clip(localStart, localEnd).isPresent()) continue;

			//Transform ray back into sim-dimension world space
			Vec3 simStart = localStart.add(Vec3.atLowerCornerOf(simOrigin)).subtract(interpolatedConstructPosition);
			Vec3 simEnd = localEnd.add(Vec3.atLowerCornerOf(simOrigin)).subtract(interpolatedConstructPosition);

			//Offset used to convert sim hit -> world-space
			Vec3 offset = new Vec3(
					simOrigin.getX() - interpolatedConstructPosition.x,
					simOrigin.getY() - interpolatedConstructPosition.y,
					simOrigin.getZ() - interpolatedConstructPosition.z
			);

			//Perform real sim-dimension raycast
			BlockHitResult localHitResult = raycastIntoConstructDimension(construct, simStart, simEnd, player, simLevel);

			if (localHitResult.getType() == HitResult.Type.BLOCK) {
				Vec3 simHit = localHitResult.getLocation();
				Vec3 localHit = simHit.subtract(Vec3.atLowerCornerOf(simOrigin));
				Vec3 rotatedHit = rotateVec(localHit, interpolatedRotation);
				Vec3 worldHitPos = rotatedHit.add(interpolatedConstructPosition);
				double distSq = worldHitPos.distanceToSqr(cameraPos);
				if (distSq < closestDistSq) {
					closestDistSq = distSq;
					BlockState state = simLevel.getBlockState(localHitResult.getBlockPos());
					VoxelShape shape = state.getShape(simLevel, localHitResult.getBlockPos());
					ManifoldRenderChunkRegion region = regions.get(construct.id());
					closestHit = new ConstructBlockHitResult(construct, shape, localHitResult, region);
				}
			}
		}

		return Optional.ofNullable(closestHit);
	}

	private static Vec3 rotateVec(Vec3 vec, Quaternionf quat) {
		Vector3f v = new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
		v.rotate(quat);
		return new Vec3(v.x(), v.y(), v.z());
	}

	private static Vec3 transformVec3(Vec3 vec, Matrix4f transform) {
		Vector4f v = new Vector4f((float) vec.x, (float) vec.y, (float) vec.z, 1.0f);
		v.mul(transform);
		return new Vec3(v.x(), v.y(), v.z());
	}

	private static BlockHitResult raycastIntoConstructDimension(ConstructRenderCache.CachedConstruct construct, Vec3 localStart, Vec3 localEnd, Entity player, Level simLevel) {
		ClipContext context = new ClipContext(
				localStart,
				localEnd,
				ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE,
				player
		);

		return simLevel.clip(context);
	}
}