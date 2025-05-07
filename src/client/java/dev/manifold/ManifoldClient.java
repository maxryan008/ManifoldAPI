package dev.manifold;

import dev.manifold.gui.MassScreen;
import dev.manifold.mass.MassManager;
import dev.manifold.network.packets.*;
import dev.manifold.render.ManifoldRenderChunk;
import dev.manifold.render.ManifoldRenderChunkRegion;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.w3c.dom.Text;

import java.util.*;

@Environment(EnvType.CLIENT)
public class ManifoldClient implements ClientModInitializer {
    public static final long SERVER_TICK_MS = 50L;
    private static final Map<UUID, ManifoldRenderChunkRegion> regions = new HashMap<>();
    public static @Nullable ManifoldRenderChunkRegion currentConstructRegion;
    public static long lastServerUpdateTime = System.currentTimeMillis();
    public static @Nullable ConstructBlockHitResult lastConstructHit;
    private static ConstructRenderCache renderer = null;

    public static void handlePickItem(Minecraft minecraft, LocalPlayer player, boolean creative, ItemStack itemStack) {
        Inventory inventory = player.getInventory();
        int i = inventory.findSlotMatchingItem(itemStack);

        if (creative) {
            inventory.setPickedItem(itemStack);
            assert minecraft.gameMode != null;
            minecraft.gameMode.handleCreativeModeItemAdd(player.getItemInHand(InteractionHand.MAIN_HAND), 36 + inventory.selected);
        } else if (i != -1) {
            if (Inventory.isHotbarSlot(i)) {
                inventory.selected = i;
            } else {
                assert minecraft.gameMode != null;
                minecraft.gameMode.handlePickItem(i);
            }
        }
    }

    public static Optional<ConstructBlockHitResult> pickConstructHit(Vec3 cameraPos, Vec3 viewVec, double maxDistance, Entity player) {
        if (renderer == null) return Optional.empty();
        Level simLevel = ConstructManager.INSTANCE.getSimDimension();
        if (simLevel == null) return Optional.empty();
        long now = System.currentTimeMillis();
        float alpha = Mth.clamp((now - lastServerUpdateTime) / (float) SERVER_TICK_MS, 0f, 1f);

        Vec3 rayEnd = cameraPos.add(viewVec.scale(maxDistance));
        ConstructBlockHitResult closestHit = null;
        double closestDistSq = maxDistance * maxDistance;

        for (ConstructRenderCache.CachedConstruct construct : renderer.getRenderedConstructs().values()) {
            UUID uuid = construct.id();

            // Skip constructs that have been removed to prevent a crash
            if (!ConstructManager.INSTANCE.hasConstruct(uuid)) continue;

            // Compute inverse rotation and position
            Vec3 interpolatedConstructPosition = construct.prevPosition().lerp(construct.currentPosition(), alpha);
            Quaternionf interpolatedRotation = new Quaternionf(construct.prevRotation()).slerp(construct.currentRotation(), alpha);
            Quaternionf inverseInterpolatedRotation = new Quaternionf(interpolatedRotation).invert();
            BlockPos simOrigin = construct.origin();
            Vec3 com = construct.centerOfMass();

            // Transform ray into construct-local space (un-rotated frame)
            Vec3 localStart = rotateVec(cameraPos.subtract(interpolatedConstructPosition), inverseInterpolatedRotation);
            Vec3 localEnd = rotateVec(rayEnd.subtract(interpolatedConstructPosition), inverseInterpolatedRotation);

            localStart = localStart.add(interpolatedConstructPosition).add(com);
            localEnd = localEnd.add(interpolatedConstructPosition).add(com);

            // Shift AABB to construct-local space
            AABB localAABB = ConstructManager.INSTANCE.getRenderAABB(uuid);

            // Perform early rejection (stay in local space!)
            if (!localAABB.contains(localStart) && localAABB.clip(localStart, localEnd).isEmpty()) continue;

            // Transform ray back into sim-dimension world space
            Vec3 simStart = localStart.add(Vec3.atLowerCornerOf(simOrigin)).subtract(interpolatedConstructPosition);
            Vec3 simEnd = localEnd.add(Vec3.atLowerCornerOf(simOrigin)).subtract(interpolatedConstructPosition);

            // Perform real sim-dimension raycast
            BlockHitResult localHitResult = raycastIntoConstructDimension(simStart, simEnd, player, simLevel);

            if (localHitResult.getType() == HitResult.Type.BLOCK) {
                Vec3 simHit = localHitResult.getLocation();
                Vec3 localHit = simHit.subtract(Vec3.atLowerCornerOf(simOrigin)).subtract(com);
                Vec3 rotatedHit = rotateVec(localHit, interpolatedRotation);
                Vec3 worldHitPos = rotatedHit.add(interpolatedConstructPosition);
                double distSq = worldHitPos.distanceToSqr(cameraPos);
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    BlockState state = simLevel.getBlockState(localHitResult.getBlockPos());
                    VoxelShape shape = state.getShape(simLevel, localHitResult.getBlockPos());
                    ManifoldRenderChunkRegion region = regions.get(uuid);
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

    private static BlockHitResult raycastIntoConstructDimension(Vec3 localStart, Vec3 localEnd, Entity player, Level simLevel) {
        ClipContext context = new ClipContext(
                localStart,
                localEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        );

        return simLevel.clip(context);
    }

    @Override
    public void onInitializeClient() {
        Manifold.LOGGER.info("Starting Client Initialization");

        // Reinitialize renderer whenever the level changes
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            renderer = new ConstructRenderCache();
            Manifold.LOGGER.info("ConstructRenderCache initialized.");
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
            renderer.renderOutline(Objects.requireNonNull(worldRenderContext.matrixStack()), camPos, alpha);
        });

        ClientPlayNetworking.registerGlobalReceiver(ConstructSectionDataS2CPacket.TYPE, (packet, context) ->
                context.client().execute(() -> handleConstructSectionData(packet))
        );

        ClientPlayNetworking.registerGlobalReceiver(PickConstructBlockWithDataS2CPacket.TYPE, (packet, context) ->
                context.client().execute(() -> handlePickConstructData(packet))
        );

        ClientPlayNetworking.registerGlobalReceiver(RemoveConstructS2CPacket.TYPE, (packet, context) ->
                context.client().execute(() -> handleRemoveConstruct(packet))
        );

        ClientPlayNetworking.registerGlobalReceiver(MassGuiDataS2CPacket.TYPE, (packet, context) ->
                context.client().execute(() -> {
                    Minecraft.getInstance().setScreen(new MassScreen(packet.entries()));
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(MassGuiDataRefreshS2CPacket.TYPE, (packet, context) ->
                context.client().execute(() -> {
                    if (Minecraft.getInstance().screen instanceof MassScreen screen) {
                        screen.refreshEntries(packet.entries());
                    }
                })
        );

        ItemTooltipCallback.EVENT.register((ItemStack stack, Item.TooltipContext context, TooltipFlag type, List<Component> lines) -> {
            if (type.isAdvanced()) {
                MassManager.getMass(stack.getItem()).ifPresentOrElse(mass -> {
                    lines.add(1, Component.literal("Mass: " + mass + " kg")
                            .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY))
                    );
                }, () -> {
                    lines.add(1, Component.literal("Mass: 1000 kg")
                            .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY))
                    );
                });
            }
        });
    }

    private void handlePickConstructData(PickConstructBlockWithDataS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        boolean creative = player.getAbilities().instabuild;
        assert minecraft.level != null;
        ItemStack itemStack = ItemStack.parse(minecraft.level.registryAccess(), packet.itemTag()).orElse(ItemStack.EMPTY);

        if (itemStack.isEmpty()) {
            Manifold.LOGGER.warn("Picking construct block with data returned empty item: {}", packet.itemTag().toString());
            return;
        }

        handlePickItem(minecraft, player, creative, itemStack);
    }

    private void handleConstructSectionData(ConstructSectionDataS2CPacket packet) {
        if (renderer == null) return; // Still waiting for renderer

        List<CompoundTag> chunkNbtList = packet.chunkNbtList();
        int countX = packet.chunkSizeX();
        int countZ = packet.chunkSizeZ();

        Level level = Minecraft.getInstance().level;
        Map<Long, ManifoldRenderChunk> chunkArray = new HashMap<>();

        for (CompoundTag tag : chunkNbtList) {
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

            chunkArray.put(ManifoldRenderChunkRegion.chunkPosToLong(pos.x, pos.z), new ManifoldRenderChunk(chunk));
        }

        ManifoldRenderChunkRegion region = new ManifoldRenderChunkRegion(
                level, packet.minChunkX(), packet.minChunkZ(), countX, countZ, chunkArray
        );

        regions.put(packet.constructId(), region);

        renderer.uploadMesh(packet.constructId(), packet.origin(), region);
    }

    private void handleRemoveConstruct(RemoveConstructS2CPacket packet) {
        renderer.markForRemoval(packet.constructId());
    }
}