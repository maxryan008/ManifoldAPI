package dev.manifold.network;

import dev.manifold.ConstructManager;
import dev.manifold.network.packets.BreakInConstructC2SPacket;
import dev.manifold.network.packets.PickConstructBlockWithDataC2SPacket;
import dev.manifold.network.packets.UseConstructBlockC2SPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ManifoldPackets {
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(BreakInConstructC2SPacket.TYPE, (payload, context) -> context.server().execute(() -> ConstructManager.INSTANCE.breakBlockInConstruct(payload)));

        ServerPlayNetworking.registerGlobalReceiver(UseConstructBlockC2SPacket.TYPE, (payload, context) -> context.server().execute(() -> UseConstructBlockC2SPacket.handle(payload, context)));

        ServerPlayNetworking.registerGlobalReceiver(PickConstructBlockWithDataC2SPacket.TYPE, (payload, context) -> context.server().execute(() -> PickConstructBlockWithDataC2SPacket.handle(payload, context)));
    }
}