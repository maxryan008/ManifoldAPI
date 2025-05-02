package dev.manifold.network.packets;

import dev.manifold.Constant;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}