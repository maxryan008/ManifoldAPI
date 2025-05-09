package dev.manifold;

import dev.manifold.init.ManifoldCommands;
import dev.manifold.init.ManifoldDimensions;
import dev.manifold.init.ManifoldMenus;
import dev.manifold.init.ServerPacketRegistry;
import dev.manifold.mass.MassManager;
import dev.manifold.network.ManifoldPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Manifold implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(Constant.MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Starting Main Initialization");

        MassManager.init(Path.of("config/manifold"));

        ManifoldMenus.register();

        ManifoldDimensions.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> ManifoldCommands.register(dispatcher, context));
        ServerPacketRegistry.register();
        ManifoldPackets.registerC2SPackets();


        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel simLevel = server.getLevel(ManifoldDimensions.SIM_WORLD);
            if (simLevel == null) return;

            ConstructManager.INSTANCE = new ConstructManager(simLevel);
            ConstructSaveData saveData = simLevel.getDataStorage().computeIfAbsent(
                    ConstructSaveData.FACTORY,
                    "manifold_constructs"
            );
            ConstructManager.INSTANCE.loadFromSave(saveData);

            MassManager.load(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ConstructManager.INSTANCE != null) {
                ConstructManager.INSTANCE.tick(server);
            }
        });
    }
}