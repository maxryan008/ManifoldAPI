package dev.manifold.init;

import dev.manifold.Constant;
import dev.manifold.network.ConstructSectionDataS2CPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class PacketRegistry {
    public static final ResourceLocation CONSTRUCT_SECTION_ID = Constant.id("construct_section");

    public static final CustomPacketPayload.Type<ConstructSectionDataS2CPacket> CONSTRUCT_SECTION =
            new CustomPacketPayload.Type<>(CONSTRUCT_SECTION_ID);

    public static void register() {
        PayloadTypeRegistry.playS2C().register(
                ConstructSectionDataS2CPacket.TYPE,
                ConstructSectionDataS2CPacket.CODEC
        );
    }
}
