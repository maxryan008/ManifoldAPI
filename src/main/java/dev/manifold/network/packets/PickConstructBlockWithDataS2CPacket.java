package dev.manifold.network.packets;

import dev.manifold.Constant;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record PickConstructBlockWithDataS2CPacket(Tag itemTag) implements CustomPacketPayload {
    public static final Type<PickConstructBlockWithDataS2CPacket> TYPE =
            new Type<>(Constant.id("pick_construct_block_response"));

    public static final StreamCodec<FriendlyByteBuf, PickConstructBlockWithDataS2CPacket> CODEC =
            StreamCodec.of(PickConstructBlockWithDataS2CPacket::write, PickConstructBlockWithDataS2CPacket::read);

    public static void write(FriendlyByteBuf buf, PickConstructBlockWithDataS2CPacket packet) {
        buf.writeNbt(packet.itemTag);
    }

    public static PickConstructBlockWithDataS2CPacket read(FriendlyByteBuf buf) {
        Tag itemNbt = buf.readNbt();
        return new PickConstructBlockWithDataS2CPacket(itemNbt);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}