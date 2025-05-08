package dev.manifold.network;

import dev.manifold.ConstructManager;
import dev.manifold.mass.MassManager;
import dev.manifold.network.packets.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ManifoldPackets {
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(BreakInConstructC2SPacket.TYPE, (payload, context) -> context.server().execute(() -> ConstructManager.INSTANCE.breakBlockInConstruct(payload)));

        ServerPlayNetworking.registerGlobalReceiver(UseConstructBlockC2SPacket.TYPE, (payload, context) -> context.server().execute(() -> UseConstructBlockC2SPacket.handle(payload, context)));

        ServerPlayNetworking.registerGlobalReceiver(PickConstructBlockWithDataC2SPacket.TYPE, (payload, context) -> context.server().execute(() -> PickConstructBlockWithDataC2SPacket.handle(payload, context)));

        ServerPlayNetworking.registerGlobalReceiver(MassReloadC2SPacket.TYPE, (packet, context) -> context.server().execute(() -> {MassManager.recalculateMasses(context.player(), context.player().server);MassManager.save(context.player().server);}));

        ServerPlayNetworking.registerGlobalReceiver(MassUpdateC2SPacket.TYPE, (packet, context) -> context.server().execute(() -> MassUpdateC2SPacket.handle(packet, context.server())));

    }
}