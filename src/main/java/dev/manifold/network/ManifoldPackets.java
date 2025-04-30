package dev.manifold.network;

import dev.manifold.network.packets.BreakInConstructC2SPacket;
import dev.manifold.network.packets.PlaceInConstructC2SPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import dev.manifold.ConstructManager;

public class ManifoldPackets {
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(PlaceInConstructC2SPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ConstructManager.INSTANCE.placeBlockInConstruct(
                        payload.constructId(),
                        payload.relPos(),
                        payload.blockState()
                );
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(BreakInConstructC2SPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ConstructManager.INSTANCE.breakBlockInConstruct(
                        payload
                );
            });
        });
    }
}