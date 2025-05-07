package dev.manifold.network.packets;

import dev.manifold.Constant;
import dev.manifold.mass.MassEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public record MassGuiDataRefreshS2CPacket(List<MassEntry> entries) implements CustomPacketPayload {
    public static final Type<MassGuiDataRefreshS2CPacket> TYPE = new Type<>(Constant.id("mass_gui_data_refresh"));

    public static final StreamCodec<FriendlyByteBuf, MassGuiDataRefreshS2CPacket> CODEC =
            StreamCodec.of(MassGuiDataRefreshS2CPacket::write, MassGuiDataRefreshS2CPacket::read);

    private static void write(FriendlyByteBuf buf, MassGuiDataRefreshS2CPacket packet) {
        buf.writeVarInt(packet.entries.size());
        for (MassEntry entry : packet.entries) {
            buf.writeById(BuiltInRegistries.ITEM::getId, entry.item());
            buf.writeBoolean(true); //todo fix up
            if (true) {
                buf.writeDouble(entry.mass());
            }
            buf.writeBoolean(entry.isOverridden());
        }
    }

    private static MassGuiDataRefreshS2CPacket read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<MassEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Item item = buf.readById(BuiltInRegistries.ITEM::byId);
            boolean hasMass = buf.readBoolean();
            Double mass = buf.readDouble();
            boolean overridden = buf.readBoolean();
            entries.add(new MassEntry(item, mass, overridden));
        }
        return new MassGuiDataRefreshS2CPacket(entries);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}