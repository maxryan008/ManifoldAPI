package dev.manifold.network.packets;

import dev.manifold.mass.MassEntry;
import dev.manifold.Constant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public record MassGuiDataS2CPacket(List<MassEntry> entries) implements CustomPacketPayload {
    public static final Type<MassGuiDataS2CPacket> TYPE = new Type<>(Constant.id("mass_gui_data"));

    public static final StreamCodec<FriendlyByteBuf, MassGuiDataS2CPacket> CODEC =
            StreamCodec.of(MassGuiDataS2CPacket::write, MassGuiDataS2CPacket::read);

    private static void write(FriendlyByteBuf buf, MassGuiDataS2CPacket packet) {
        buf.writeVarInt(packet.entries.size());
        for (MassEntry entry : packet.entries) {
            buf.writeById(BuiltInRegistries.ITEM::getId, entry.item());
            buf.writeBoolean(entry.mass().isPresent());
            if (entry.mass().isPresent()) {
                buf.writeDouble(entry.mass().getAsDouble());
            }
            buf.writeBoolean(entry.isOverridden());
        }
    }

    private static MassGuiDataS2CPacket read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<MassEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Item item = buf.readById(BuiltInRegistries.ITEM::byId);
            boolean hasMass = buf.readBoolean();
            OptionalDouble mass = hasMass ? OptionalDouble.of(buf.readDouble()) : OptionalDouble.empty();
            boolean overridden = buf.readBoolean();
            entries.add(new MassEntry(item, mass, overridden));
        }
        return new MassGuiDataS2CPacket(entries);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}