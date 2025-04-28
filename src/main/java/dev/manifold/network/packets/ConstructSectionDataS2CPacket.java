package dev.manifold.network;

import dev.manifold.Constant;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ConstructSectionDataS2CPacket(
        UUID constructId,
        BlockPos origin,
        int minChunkX,
        int minChunkZ,
        int chunkSizeX,
        int chunkSizeZ,
        List<CompoundTag> chunkNbtList
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConstructSectionDataS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(Constant.id("construct_section"));

    public static final StreamCodec<FriendlyByteBuf, ConstructSectionDataS2CPacket> CODEC =
            StreamCodec.of(
                    ConstructSectionDataS2CPacket::writeToBuf,
                    ConstructSectionDataS2CPacket::readFromBuf
            );

    public static ConstructSectionDataS2CPacket readFromBuf(FriendlyByteBuf buf) {
        UUID constructId = buf.readUUID();
        BlockPos origin = buf.readBlockPos();
        int minChunkX = buf.readVarInt();
        int minChunkZ = buf.readVarInt();
        int chunkSizeX = buf.readVarInt();
        int chunkSizeZ = buf.readVarInt();

        int listSize = buf.readVarInt();
        List<CompoundTag> chunkNbtList = new ArrayList<>(listSize);
        for (int i = 0; i < listSize; i++) {
            chunkNbtList.add(buf.readNbt());
        }

        return new ConstructSectionDataS2CPacket(
                constructId, origin, minChunkX, minChunkZ, chunkSizeX, chunkSizeZ, chunkNbtList
        );
    }

    public static void writeToBuf(FriendlyByteBuf buf, ConstructSectionDataS2CPacket packet) {
        buf.writeUUID(packet.constructId());
        buf.writeBlockPos(packet.origin());
        buf.writeVarInt(packet.minChunkX());
        buf.writeVarInt(packet.minChunkZ());
        buf.writeVarInt(packet.chunkSizeX());
        buf.writeVarInt(packet.chunkSizeZ());

        buf.writeVarInt(packet.chunkNbtList().size());
        for (CompoundTag tag : packet.chunkNbtList()) {
            buf.writeNbt(tag);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}