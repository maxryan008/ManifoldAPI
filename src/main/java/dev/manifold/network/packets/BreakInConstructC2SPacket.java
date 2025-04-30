package dev.manifold.network.packets;

import dev.manifold.Constant;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record BreakInConstructC2SPacket(UUID constructId, BlockPos blockHitPos) implements CustomPacketPayload {
    public static final Type<BreakInConstructC2SPacket> TYPE =
            new Type<>(Constant.id("break_in_construct"));

    public static final StreamCodec<FriendlyByteBuf, BreakInConstructC2SPacket> CODEC =
            StreamCodec.of(BreakInConstructC2SPacket::write, BreakInConstructC2SPacket::read);

    public static void write(FriendlyByteBuf buf, BreakInConstructC2SPacket packet) {
        buf.writeUUID(packet.constructId());
        buf.writeBlockPos(packet.blockHitPos);
    }

    public static BreakInConstructC2SPacket read(FriendlyByteBuf buf) {
        return new BreakInConstructC2SPacket(
                buf.readUUID(),
                buf.readBlockPos()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}