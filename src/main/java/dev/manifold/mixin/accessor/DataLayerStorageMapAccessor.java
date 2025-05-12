package dev.manifold.mixin.accessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.lighting.DataLayerStorageMap") // interface
public interface DataLayerStorageMapAccessor {
    @Accessor("map")
    Long2ObjectOpenHashMap<DataLayer> manifold$getMap();
}
