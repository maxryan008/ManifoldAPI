package dev.manifold.network;

import dev.manifold.Constant;
import dev.manifold.network.packets.ConstructSectionDataS2CPacket;
import dev.manifold.network.packets.PlaceInConstructC2SPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class PacketTypes {
    public static final CustomPacketPayload.Type<ConstructSectionDataS2CPacket> CONSTRUCT_SECTION =
            new CustomPacketPayload.Type<>(Constant.id("construct_section"));

    public static final CustomPacketPayload.Type<PlaceInConstructC2SPacket> PLACE_IN_CONSTRUCT =
            new CustomPacketPayload.Type<>(Constant.id("place_in_construct"));
}
