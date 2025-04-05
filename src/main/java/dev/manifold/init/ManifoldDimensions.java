package dev.manifold.init;

import dev.manifold.Constant;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

public class ManifoldDimensions {
    public static final ResourceKey<LevelStem> SIM_DIMENSION = ResourceKey.create(Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath(Constant.MOD_ID, "sim"));
    public static final ResourceKey<Level> SIM_WORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Constant.MOD_ID, "sim"));
    public static final ResourceKey<DimensionType> SIM_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath(Constant.MOD_ID, "sim"));

    public static void register() {
    }
}
