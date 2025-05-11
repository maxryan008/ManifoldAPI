package dev.manifold.mixin;

import com.mojang.datafixers.DataFixer;
import dev.manifold.access_holders.MinecraftServerStorageAccessHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin implements MinecraftServerStorageAccessHolder {
    private LevelStorageSource.LevelStorageAccess manifold$storageAccess;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureStorageAccess(Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer dataFixer, Services services, ChunkProgressListenerFactory chunkProgressListenerFactory, CallbackInfo ci) {
        this.manifold$storageAccess = levelStorageAccess;
    }

    @Override
    public void manifold$setStorageAccess(LevelStorageSource.LevelStorageAccess access) {
        this.manifold$storageAccess = access;
    }

    @Override
    public LevelStorageSource.LevelStorageAccess manifold$getStorageAccess() {
        return this.manifold$storageAccess;
    }
}
