package dev.manifold;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.manifold.init.PacketRegistry;
import dev.manifold.network.ConstructSectionDataS2CPacket;
import dev.manifold.render.ManifoldRenderChunk;
import dev.manifold.render.ManifoldRenderChunkRegion;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.mixin.registry.sync.RegistriesAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

@Environment(EnvType.CLIENT)
public class ManifoldClient implements ClientModInitializer {
	private static ConstructRenderCache renderer = null;
	public static long lastServerUpdateTime = System.currentTimeMillis();
	public static final long SERVER_TICK_MS = 50L;

	@Override
	public void onInitializeClient() {
		Manifold.LOGGER.info("Starting Client Initialization");

		PacketRegistry.register();

		// Initialize renderer later once levelRenderer is non-null
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (renderer == null && client.level != null) {
				renderer = new ConstructRenderCache();
				Manifold.LOGGER.info("ConstructRenderCache initialized.");
			}
		});

		// Register world rendering hook
		WorldRenderEvents.END.register(context -> {
			if (Minecraft.getInstance().player == null || renderer == null) return;
			Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
			Vec3 camPos = camera.getPosition();
			long now = System.currentTimeMillis();
			float alpha = Mth.clamp((now - lastServerUpdateTime) / (float) SERVER_TICK_MS, 0f, 1f);
			renderer.renderAll(context.matrixStack(), camPos, alpha);
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
						ManifoldRenderChunk[] chunkArray = new ManifoldRenderChunk[(countX) * (countZ)];

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
                                        Manifold.LOGGER.warn("Failed to load section y={} for chunk {}", yStr, pos, e);
                                    }
                                }
                            }

							chunkArray[i] = new ManifoldRenderChunk(chunk);
                        }

						ManifoldRenderChunkRegion region = new ManifoldRenderChunkRegion(
								level, packet.minChunkX(), packet.minChunkZ(), countX, countZ, chunkArray
						);

						renderer.uploadMesh(packet.constructId(), packet.origin(), region);
					});
				}
		);
	}

	public static ConstructRenderCache getRenderer() {
		return renderer;
	}
}