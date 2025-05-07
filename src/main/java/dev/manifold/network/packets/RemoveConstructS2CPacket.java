package dev.manifold.network.packets;

import dev.manifold.Constant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record RemoveConstructS2CPacket(UUID constructId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RemoveConstructS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(Constant.id("remove_construct"));

    public static final StreamCodec<FriendlyByteBuf, RemoveConstructS2CPacket> CODEC =
            StreamCodec.of(
                    RemoveConstructS2CPacket::writeToBuf,
                    RemoveConstructS2CPacket::readFromBuf
            );

    public static RemoveConstructS2CPacket readFromBuf(FriendlyByteBuf buf) {
        return new RemoveConstructS2CPacket(buf.readUUID());
    }

    public static void writeToBuf(FriendlyByteBuf buf, RemoveConstructS2CPacket packet) {
        buf.writeUUID(packet.constructId());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}