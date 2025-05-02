package dev.manifold.network.packets;

import dev.manifold.Constant;
import dev.manifold.ConstructManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PickConstructBlockWithDataC2SPacket(UUID constructId, BlockPos relPos) implements CustomPacketPayload {
    public static final Type<PickConstructBlockWithDataC2SPacket> TYPE =
            new Type<>(Constant.id("pick_construct_block"));

    public static final StreamCodec<FriendlyByteBuf, PickConstructBlockWithDataC2SPacket> CODEC =
            StreamCodec.of(PickConstructBlockWithDataC2SPacket::write, PickConstructBlockWithDataC2SPacket::read);

    public static void write(FriendlyByteBuf buf, PickConstructBlockWithDataC2SPacket packet) {
        buf.writeUUID(packet.constructId());
        buf.writeBlockPos(packet.relPos());
    }

    public static PickConstructBlockWithDataC2SPacket read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        BlockPos pos = buf.readBlockPos();
        return new PickConstructBlockWithDataC2SPacket(id, pos);
    }

    public static void handle(PickConstructBlockWithDataC2SPacket payload, ServerPlayNetworking.Context context) {
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
            assert be != null;
            addCustomNbtData(pick, be, simLevel.registryAccess());
        }

        // Serialize the ItemStack
        Tag itemTag = pick.save(player.level().registryAccess());

        PickConstructBlockWithDataS2CPacket response = new PickConstructBlockWithDataS2CPacket(itemTag);
        ServerPlayNetworking.send(player, response);
    }

    private static void addCustomNbtData(ItemStack itemStack, BlockEntity blockEntity, RegistryAccess registryAccess) {
        CompoundTag compoundTag = blockEntity.saveCustomAndMetadata(registryAccess);
        //noinspection deprecation
        blockEntity.removeComponentsFromTag(compoundTag);
        BlockItem.setBlockEntityData(itemStack, blockEntity.getType(), compoundTag);
        itemStack.applyComponents(blockEntity.collectComponents());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}