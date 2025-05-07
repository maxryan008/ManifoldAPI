package dev.manifold.network;

import dev.manifold.Constant;
import dev.manifold.network.packets.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class PacketTypes {
    public static final CustomPacketPayload.Type<ConstructSectionDataS2CPacket> CONSTRUCT_SECTION =
            new CustomPacketPayload.Type<>(Constant.id("construct_section"));

    public static final CustomPacketPayload.Type<BreakInConstructC2SPacket> BREAK_IN_CONSTRUCT =
            new CustomPacketPayload.Type<>(Constant.id("break_in_construct"));

    public static final CustomPacketPayload.Type<UseConstructBlockC2SPacket> USE_CONSTRUCT_BLOCK =
            new CustomPacketPayload.Type<>(Constant.id("use_construct_block"));

    public static final CustomPacketPayload.Type<PickConstructBlockWithDataC2SPacket> PICK_CONSTRUCT_BLOCK_C2S =
            new CustomPacketPayload.Type<>(Constant.id("pick_construct_block"));

    public static final CustomPacketPayload.Type<PickConstructBlockWithDataS2CPacket> PICK_CONSTRUCT_BLOCK_S2C =
            new CustomPacketPayload.Type<>(Constant.id("pick_construct_block_response"));

    public static final CustomPacketPayload.Type<MassGuiDataS2CPacket> MASS_GUI_DATA =
            new CustomPacketPayload.Type<>(Constant.id("mass_gui_data"));

    public static final CustomPacketPayload.Type<MassGuiDataRefreshS2CPacket> MASS_GUI_DATA_REFRESH =
            new CustomPacketPayload.Type<>(Constant.id("mass_gui_data_refresh"));

    public static final CustomPacketPayload.Type<MassReloadC2SPacket> MASS_RELOAD =
            new CustomPacketPayload.Type<>(Constant.id("mass_reload"));

    public static final CustomPacketPayload.Type<MassUpdateC2SPacket> MASS_UPDATE =
            new CustomPacketPayload.Type<>(Constant.id("mass_update"));
}
