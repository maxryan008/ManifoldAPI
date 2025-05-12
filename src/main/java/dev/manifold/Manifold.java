package dev.manifold;

import dev.manifold.access_holders.MinecraftServerStorageAccessHolder;
import dev.manifold.init.ManifoldCommands;
import dev.manifold.init.ManifoldDimensions;
import dev.manifold.init.ManifoldMenus;
import dev.manifold.init.ServerPacketRegistry;
import dev.manifold.mass.MassManager;
import dev.manifold.mixin.accessor.ChunkMapMixin;
import dev.manifold.mixin.accessor.MinecraftServerAccessor;
import dev.manifold.mixin.accessor.ServerLevelAccessor;
import dev.manifold.network.ManifoldPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

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
            ServerLevel original = server.getLevel(ManifoldDimensions.SIM_WORLD);
            if (original == null) return;

            LevelStorageSource.LevelStorageAccess levelStorageAccess = ((MinecraftServerStorageAccessHolder) server).manifold$getStorageAccess();

            Map<ResourceKey<Level>, ServerLevel> levelMap = ((MinecraftServerAccessor) server).getLevels();
            Executor executor = ((MinecraftServerAccessor) server).getExecutor();
            List<CustomSpawner> customSpawners = ((ServerLevelAccessor) original).getCustomSpawners();
            ChunkProgressListener progressListener = ((ChunkMapMixin) original.getChunkSource().chunkMap).getProgressListener();

            LevelStem stem = new LevelStem(
                    original.dimensionTypeRegistration(),
                    original.getChunkSource().getGenerator()
            );

            System.out.println("Initializing Manifold");

            // Recreate SimLevel using original data
            SimLevel simLevel = new SimLevel(
                    server,
                    executor,
                    levelStorageAccess,
                    (ServerLevelData) original.getLevelData(),
                    original.dimension(),
                    stem,
                    progressListener,
                    original.isDebug(),
                    original.getSeed(),
                    customSpawners,
                    original.tickRateManager().isSteppingForward(),
                    original.getRandomSequences()
            );

            levelMap.put(ManifoldDimensions.SIM_WORLD, simLevel);

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