package dev.manifold.network.packets;

import dev.manifold.Constant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record MassReloadC2SPacket() implements CustomPacketPayload {
    public static final Type<MassReloadC2SPacket> TYPE = new Type<>(Constant.id("mass_reload"));

    public static final StreamCodec<FriendlyByteBuf, MassReloadC2SPacket> CODEC =
            StreamCodec.unit(new MassReloadC2SPacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
