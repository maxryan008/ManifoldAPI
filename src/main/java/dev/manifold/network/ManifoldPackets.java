package dev.manifold.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import dev.manifold.ConstructManager;

public class MyPackets {
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(PlaceInConstructC2SPacket.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ConstructManager.INSTANCE.placeBlockInConstruct(
                        payload.constructId(),
                        payload.relPos(),
                        payload.blockState()
                );
            });
        });
    }
}