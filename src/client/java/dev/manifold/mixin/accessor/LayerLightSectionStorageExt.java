package dev.manifold.mixin.accessor;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LayerLightSectionStorage.class)
public interface LayerLightSectionStorageExt {
    @Invoker("updateSectionStatus")
    void manifold$updateSectionStatus(long sectionPos, boolean isEmpty);

    @Invoker("markNewInconsistencies")
    void manifold$markNewInconsistencies(LightEngine<?, ?> engine);

    @Invoker("swapSectionMap")
    void manifold$swapSectionMap();
}
