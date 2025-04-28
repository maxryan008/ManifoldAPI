package dev.manifold.mixin;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import dev.manifold.ManifoldClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "pick", at = @At("RETURN"), cancellable = true)
    private void injectConstructRaycast(Entity entity, double d, double e, float f, CallbackInfoReturnable<HitResult> cir) {
        HitResult vanillaHit = cir.getReturnValue();
        Vec3 cameraPos = entity.getEyePosition(f);
        double maxDistance = Math.max(d, e);

        Optional<ConstructBlockHitResult> constructHitOpt = ManifoldClient.pickConstructHit(cameraPos, entity.getViewVector(f), maxDistance);
        if (constructHitOpt.isPresent()) {
            ConstructBlockHitResult constructHit = constructHitOpt.get();

            double vanillaDist = vanillaHit.getLocation().distanceToSqr(cameraPos);
            double constructDist = constructHit.getLocation().distanceToSqr(cameraPos);

            if (constructDist < vanillaDist || vanillaHit.getType() == HitResult.Type.MISS) {
                cir.setReturnValue(constructHit); // override final result
            }
        }
    }
}