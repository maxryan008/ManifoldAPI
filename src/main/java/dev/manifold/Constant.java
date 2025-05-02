package dev.manifold;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface Constant {
    String MOD_ID = "manifold";

    @Contract(value = "_ -> new", pure = true)
    static @NotNull ResourceLocation id(String id) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
    }
}
