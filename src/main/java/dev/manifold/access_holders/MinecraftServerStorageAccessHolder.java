package dev.manifold.access_holders;

import net.minecraft.world.level.storage.LevelStorageSource;

public interface MinecraftServerStorageAccessHolder {
    void manifold$setStorageAccess(LevelStorageSource.LevelStorageAccess access);
    LevelStorageSource.LevelStorageAccess manifold$getStorageAccess();
}
