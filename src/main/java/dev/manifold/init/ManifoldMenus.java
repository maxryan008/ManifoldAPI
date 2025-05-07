package dev.manifold.init;

import dev.manifold.Constant;
import dev.manifold.gui.MassScreenHandler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public class ManifoldMenus {
    public static void register() {
        MassScreenHandler.TYPE = Registry.register(
                BuiltInRegistries.MENU,
                Constant.id("mass_screen_handler"),
                new MenuType<>(
                        MassScreenHandler::new,
                        FeatureFlags.VANILLA_SET
                )
        );
    }
}
