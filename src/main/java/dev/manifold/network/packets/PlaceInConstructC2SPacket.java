package dev.manifold.network.packets;

import dev.manifold.Constant;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public record PlaceInConstructC2SPacket(UUID constructId, BlockPos relPos, BlockState blockState) implements CustomPacketPayload {
    public static final Type<PlaceInConstructC2SPacket> TYPE =
            new Type<>(Constant.id("place_in_construct"));

    public static final StreamCodec<FriendlyByteBuf, PlaceInConstructC2SPacket> CODEC =
            StreamCodec.of(PlaceInConstructC2SPacket::write, PlaceInConstructC2SPacket::read);

    public static void write(FriendlyByteBuf buf, PlaceInConstructC2SPacket packet) {
        buf.writeUUID(packet.constructId());
        buf.writeBlockPos(packet.relPos());
        buf.writeVarInt(Block.getId(packet.blockState()));
    }

    public static PlaceInConstructC2SPacket read(FriendlyByteBuf buf) {
        UUID constructId = buf.readUUID();
        BlockPos pos = buf.readBlockPos();
        BlockState state = Block.stateById(buf.readVarInt());
        return new PlaceInConstructC2SPacket(constructId, pos, state);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}