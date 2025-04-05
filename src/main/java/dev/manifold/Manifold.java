package dev.manifold;

import dev.manifold.init.ManifoldCommands;
import dev.manifold.init.ManifoldDimensions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Manifold implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(Constant.MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Starting Main Initialization");

		ManifoldDimensions.register();
		CommandRegistrationCallback.EVENT.register(ManifoldCommands::register);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel simLevel = server.getLevel(ManifoldDimensions.SIM_WORLD);
			if (simLevel == null) return;

			ConstructManager.INSTANCE = new ConstructManager(simLevel);
			ConstructSaveData saveData = simLevel.getDataStorage().computeIfAbsent(
					ConstructSaveData.FACTORY,
					"manifold_constructs"
			);
			ConstructManager.INSTANCE.loadFromSave(saveData);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ConstructManager.INSTANCE != null) {
				ConstructManager.INSTANCE.tick(server);
			}
		});
	}
}