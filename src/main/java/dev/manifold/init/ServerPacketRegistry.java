package dev.manifold.init;

import dev.manifold.Constant;
import dev.manifold.network.ConstructSectionDataS2CPacket;
import dev.manifold.network.PlaceInConstructC2SPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class ClientPacketRegistry {
    public static final ResourceLocation CONSTRUCT_SECTION_ID = Constant.id("construct_section");
    public static final ResourceLocation PLACE_IN_CONSTRUCT_ID = Constant.id("place_in_construct");

    public static final CustomPacketPayload.Type<ConstructSectionDataS2CPacket> CONSTRUCT_SECTION =
            new CustomPacketPayload.Type<>(CONSTRUCT_SECTION_ID);
    public static final CustomPacketPayload.Type<ConstructSectionDataS2CPacket> PLACE_IN_CONSTRUCT =
            new CustomPacketPayload.Type<>(PLACE_IN_CONSTRUCT_ID);

    public static void register() {
        PayloadTypeRegistry.playS2C().register(
                ConstructSectionDataS2CPacket.TYPE,
                ConstructSectionDataS2CPacket.CODEC
        );

        PayloadTypeRegistry.playS2C().register(
                PlaceInConstructC2SPacket.TYPE,
                PlaceInConstructC2SPacket.CODEC
        );
    }
}
