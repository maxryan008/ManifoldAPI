package dev.manifold.mixin.accessor;

import dev.manifold.access_holders.LayerLightStorageBridge;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LayerLightSectionStorage.class)
public abstract class LayerLightStorageMixin implements LayerLightStorageBridge {
    @Accessor("updatingSectionData")
    @Override
    public abstract DataLayerStorageMap<?> manifold$getUpdatingData();

    @Accessor("queuedSections")
    public abstract Long2ObjectMap<DataLayer> manifold$getQueuedSections();
}
