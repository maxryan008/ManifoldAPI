package dev.manifold.mass;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.OptionalDouble;

public record MassEntry(Item item, OptionalDouble mass, boolean isOverridden) {
    public static List<MassEntry> collect(MinecraftServer server) {
        return server.registryAccess().registryOrThrow(Registries.ITEM)
                .stream()
                .map(i -> new MassEntry(i, MassManager.getMass(i), MassManager.isOverridden(i)))
                .toList();
    }
}