package dev.manifold.network.packets;

import dev.manifold.Constant;
import dev.manifold.ConstructContainerMenu;
import dev.manifold.ConstructManager;
import dev.manifold.mixin.accessor.BlockBehaviourAccessor;
import dev.manifold.mixin.accessor.ServerPlayerAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record UseConstructBlockC2SPacket(UUID constructId, BlockPos relPos, InteractionHand hand,
                                         Direction hitSide) implements CustomPacketPayload {
    public static final Type<UseConstructBlockC2SPacket> TYPE = new Type<>(Constant.id("use_construct_block"));

    public static final StreamCodec<FriendlyByteBuf, UseConstructBlockC2SPacket> CODEC =
            StreamCodec.of(UseConstructBlockC2SPacket::write, UseConstructBlockC2SPacket::read);

    public static void write(FriendlyByteBuf buf, UseConstructBlockC2SPacket packet) {
        buf.writeUUID(packet.constructId());
        buf.writeBlockPos(packet.relPos());
        buf.writeEnum(packet.hand());
        buf.writeEnum(packet.hitSide());
    }

    public static UseConstructBlockC2SPacket read(FriendlyByteBuf buf) {
        UUID constructId = buf.readUUID();
        BlockPos pos = buf.readBlockPos();
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        Direction hitSide = buf.readEnum(Direction.class);
        return new UseConstructBlockC2SPacket(constructId, pos, hand, hitSide);
    }

    public static void handle(UseConstructBlockC2SPacket payload, ServerPlayNetworking.Context context) {
        var player = context.player();
        var simLevel = ConstructManager.INSTANCE.getSimDimension();

        ConstructManager.INSTANCE.getSimOrigin(payload.constructId()).ifPresent(origin -> {
            BlockPos absolutePos = payload.relPos();
            BlockState state = simLevel.getBlockState(absolutePos);
            if (state.isAir()) return;

            var fakeHit = new BlockHitResult(player.position(), payload.hitSide(), absolutePos, false);
            var heldItem = player.getItemInHand(payload.hand());

            // perform interaction
            ItemInteractionResult itemResult = state.useItemOn(heldItem, simLevel, player, payload.hand(), fakeHit);
            boolean consumes = itemResult.consumesAction();

            if (itemResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && payload.hand() == InteractionHand.MAIN_HAND) {
                InteractionResult fallback = state.useWithoutItem(simLevel, player, fakeHit);
                consumes = fallback.consumesAction();
            }

            if (!player.isShiftKeyDown() && consumes) {
                Block block = state.getBlock();
                MenuProvider provider = ((BlockBehaviourAccessor) block).manifold$invokeGetMenuProvider(state, simLevel, absolutePos);
                if (provider != null) {
                    var accessor = (ServerPlayerAccessor) player;
                    accessor.manifold$invokeNextContainerCounter();
                    int containerId = accessor.getContainerCounter();

                    AbstractContainerMenu original = provider.createMenu(containerId, player.getInventory(), player);
                    if (original != null) {
                        player.openMenu(new MenuProvider() {
                            @Override
                            public AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                                return new ConstructContainerMenu(original);
                            }

                            @Override
                            public net.minecraft.network.chat.@NotNull Component getDisplayName() {
                                return provider.getDisplayName();
                            }
                        });

                        if (player instanceof ServerPlayer serverPlayer) {
                            int animationId = payload.hand() == InteractionHand.MAIN_HAND ? 0 : 3;
                            serverPlayer.connection.send(new ClientboundAnimatePacket(player, animationId));
                        }
                    }
                }
                return;
            }

            tryPlaceInConstruct(player, payload.hand(), fakeHit, origin, payload.constructId());
        });
    }

    private static void tryPlaceInConstruct(Player player, InteractionHand hand, BlockHitResult hit, BlockPos origin, UUID id) {
        ItemStack held = player.getItemInHand(hand);
        Block block = Block.byItem(held.getItem());
        if (block == Blocks.AIR) return;

        BlockPos rel = hit.getBlockPos().relative(hit.getDirection()).subtract(origin);

        BlockState state = block.defaultBlockState(); // Later can improve with context (rotation, water logging etc.)

        ConstructManager.INSTANCE.placeBlockInConstruct(
                id,
                rel,
                state
        );

        // Play hand swing animation
        if (player instanceof ServerPlayer serverPlayer) {
            int animationId = hand == InteractionHand.MAIN_HAND ? 0 : 3;
            serverPlayer.connection.send(new ClientboundAnimatePacket(player, animationId));
        }

    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}