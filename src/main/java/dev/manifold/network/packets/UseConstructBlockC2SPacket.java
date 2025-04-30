package dev.manifold.network.packets;

import dev.manifold.Constant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

import java.util.UUID;

public record UseConstructBlockC2SPacket(UUID constructId, BlockPos relPos, InteractionHand hand, Direction hitSide) implements CustomPacketPayload {
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}