package dev.manifold.network.packets;

import dev.manifold.Constant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

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
        List<CompoundTag> chunkNbtList,
        ResourceKey<Level> worldKey
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

        // NEW: read the target render dimension
        ResourceKey<Level> worldKey = buf.readResourceKey(Registries.DIMENSION);

        return new ConstructSectionDataS2CPacket(
                constructId, origin, minChunkX, minChunkZ, chunkSizeX, chunkSizeZ, chunkNbtList, worldKey
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

        // NEW: write the target render dimension
        buf.writeResourceKey(packet.worldKey());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}