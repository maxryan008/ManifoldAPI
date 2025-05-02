package dev.manifold.mixin;

import dev.manifold.ConstructManager;
import dev.manifold.ConstructSaveData;
import dev.manifold.init.ManifoldDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelSaveMixin {
    @Inject(method = "save", at = @At("TAIL"))
    private void onSave(@Nullable ProgressListener progressListener, boolean flush, boolean savingChunks, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!level.dimension().equals(ManifoldDimensions.SIM_WORLD)) return;

        ConstructManager manager = ConstructManager.INSTANCE;
        if (manager == null) return;

        ConstructSaveData data = new ConstructSaveData(manager.getConstructs());
        level.getDataStorage().set("manifold_constructs", data);
    }
}