package dev.manifold.network.packets;

import dev.manifold.Constant;
import dev.manifold.mass.MassManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

public record MassUpdateC2SPacket(Item item, double mass, boolean isAuto) implements CustomPacketPayload {
    public static final Type<MassUpdateC2SPacket> TYPE = new Type<>(Constant.id("mass_update"));

    public static final StreamCodec<FriendlyByteBuf, MassUpdateC2SPacket> CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeById(BuiltInRegistries.ITEM::getId, pkt.item());
                buf.writeDouble(pkt.mass());
                buf.writeBoolean(pkt.isAuto());
            }, buf -> new MassUpdateC2SPacket(
                    buf.readById(BuiltInRegistries.ITEM::byId),
                    buf.readDouble(),
                    buf.readBoolean()
            ));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MassUpdateC2SPacket packet, MinecraftServer server) {
        server.execute(() -> {
            MassManager.setMass(packet.item, packet.mass, packet.isAuto);
            MassManager.save(server);
        });
    }
}