package dev.manifold.mass;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.List;

public record MassEntry(Item item, Double mass, boolean isOverridden) {
    public static List<MassEntry> collect(MinecraftServer server) {
        return server.registryAccess().registryOrThrow(Registries.ITEM)
                .stream()
                .map(i -> new MassEntry(i, MassManager.getMassOrDefault(i), MassManager.isOverridden(i)))
                .toList();
    }
}