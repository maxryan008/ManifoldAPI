package dev.manifold.access_holders;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;

public interface LayerLightStorageBridge {
    DataLayerStorageMap<?> manifold$getUpdatingData();
    Long2ObjectMap<DataLayer> manifold$getQueuedSections();
}