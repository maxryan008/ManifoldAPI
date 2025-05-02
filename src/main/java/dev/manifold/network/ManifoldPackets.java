package dev.manifold.network;

import dev.manifold.ConstructContainerMenu;
import dev.manifold.ConstructManager;
import dev.manifold.mixin.accessor.BlockBehaviourAccessor;
import dev.manifold.mixin.accessor.ServerPlayerAccessor;
import dev.manifold.network.packets.BreakInConstructC2SPacket;
import dev.manifold.network.packets.PickConstructBlockWithDataC2SPacket;
import dev.manifold.network.packets.PickConstructBlockWithDataS2CPacket;
import dev.manifold.network.packets.UseConstructBlockC2SPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

public class ManifoldPackets {
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(BreakInConstructC2SPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ConstructManager.INSTANCE.breakBlockInConstruct(
                        payload
                );
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UseConstructBlockC2SPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
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
                                    public net.minecraft.network.chat.Component getDisplayName() {
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
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PickConstructBlockWithDataC2SPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level simLevel = ConstructManager.INSTANCE.getSimDimension();
                UUID id = payload.constructId();
                BlockPos origin = ConstructManager.INSTANCE.getSimOrigin(id).orElse(null);
                if (origin == null) return;

                BlockPos absolutePos = payload.relPos();
                BlockState state = simLevel.getBlockState(absolutePos);
                if (state.isAir()) return;

                ItemStack pick = state.getBlock().getCloneItemStack(simLevel, absolutePos, state);
                if (pick.isEmpty()) return;

                if (state.hasBlockEntity()) {
                    BlockEntity be = simLevel.getBlockEntity(absolutePos);
                    addCustomNbtData(pick, be, simLevel.registryAccess());
                }

                // Serialize the ItemStack
                Tag itemTag = pick.save(player.level().registryAccess());

                PickConstructBlockWithDataS2CPacket response = new PickConstructBlockWithDataS2CPacket(itemTag);
                ServerPlayNetworking.send(player, response);
            });
        });
    }

    private static boolean tryPlaceInConstruct(Player player, InteractionHand hand, BlockHitResult hit, BlockPos origin, UUID id) {
        ItemStack held = player.getItemInHand(hand);
        Block block = Block.byItem(held.getItem());
        if (block == Blocks.AIR) return false;

        BlockPos rel = hit.getBlockPos().relative(hit.getDirection()).subtract(origin);

        BlockState state = block.defaultBlockState(); // Later you can improve with context (rotation, waterlogging etc)

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

        return true;
    }

    private static void addCustomNbtData(ItemStack itemStack, BlockEntity blockEntity, RegistryAccess registryAccess) {
        CompoundTag compoundTag = blockEntity.saveCustomAndMetadata(registryAccess);
        blockEntity.removeComponentsFromTag(compoundTag);
        BlockItem.setBlockEntityData(itemStack, blockEntity.getType(), compoundTag);
        itemStack.applyComponents(blockEntity.collectComponents());
    }
}