package dev.manifold.init;

import dev.manifold.network.PacketTypes;
import dev.manifold.network.packets.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ServerPacketRegistry {
    public static void register() {
        PayloadTypeRegistry.playS2C().register(
                PacketTypes.CONSTRUCT_SECTION,
                ConstructSectionDataS2CPacket.CODEC
        );

        PayloadTypeRegistry.playS2C().register(
                PacketTypes.PICK_CONSTRUCT_BLOCK_S2C,
                PickConstructBlockWithDataS2CPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                PacketTypes.BREAK_IN_CONSTRUCT,
                BreakInConstructC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                PacketTypes.USE_CONSTRUCT_BLOCK,
                UseConstructBlockC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                PacketTypes.PICK_CONSTRUCT_BLOCK_C2S,
                PickConstructBlockWithDataC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                PacketTypes.MASS_RELOAD,
                MassReloadC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                PacketTypes.MASS_UPDATE,
                MassUpdateC2SPacket.CODEC
        );

        PayloadTypeRegistry.playS2C().register(
                PacketTypes.MASS_GUI_DATA,
                MassGuiDataS2CPacket.CODEC
        );

        PayloadTypeRegistry.playS2C().register(
                PacketTypes.MASS_GUI_DATA_REFRESH,
                MassGuiDataRefreshS2CPacket.CODEC
        );
    }
}
