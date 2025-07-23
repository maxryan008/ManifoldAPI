package dev.manifold.mixin.accessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DataLayerStorageMap.class) // interface
public interface DataLayerStorageMapAccessor {
    @Accessor("map")
    Long2ObjectOpenHashMap<DataLayer> manifold$getMap();
}
