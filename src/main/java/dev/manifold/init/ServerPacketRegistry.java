package dev.manifold.init;

import dev.manifold.network.PacketTypes;
import dev.manifold.network.packets.BreakInConstructC2SPacket;
import dev.manifold.network.packets.ConstructSectionDataS2CPacket;
import dev.manifold.network.packets.PlaceInConstructC2SPacket;
import dev.manifold.network.packets.UseConstructBlockC2SPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ServerPacketRegistry {
    public static void register() {
        PayloadTypeRegistry.playS2C().register(
                PacketTypes.CONSTRUCT_SECTION,
                ConstructSectionDataS2CPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                PacketTypes.PLACE_IN_CONSTRUCT,
                PlaceInConstructC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                PacketTypes.BREAK_IN_CONSTRUCT,
                BreakInConstructC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                PacketTypes.USE_CONSTRUCT_BLOCK,
                UseConstructBlockC2SPacket.CODEC
        );
    }
}
