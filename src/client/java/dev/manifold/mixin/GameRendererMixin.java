package dev.manifold.mixin;

import dev.manifold.ConstructBlockHitResult;
import dev.manifold.ManifoldClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void injectConstructRaycast(Entity entity, double d, double e, float f, CallbackInfoReturnable<HitResult> cir) {
        HitResult vanillaHit = cir.getReturnValue();
        Vec3 cameraPos = entity.getEyePosition(f);
        double maxDistance = Math.max(d, e);

        Optional<ConstructBlockHitResult> constructHitOpt = ManifoldClient.pickConstructHit(cameraPos, entity.getViewVector(f), maxDistance, entity);
        if (constructHitOpt.isPresent()) {
            ConstructBlockHitResult constructHit = constructHitOpt.get();

            double vanillaDist = vanillaHit.getLocation().distanceToSqr(cameraPos);
            double constructDist = constructHit.getLocation().distanceToSqr(cameraPos);

            if (constructDist < vanillaDist || vanillaHit.getType() == HitResult.Type.MISS) {
                ManifoldClient.lastConstructHit = constructHit;
                Minecraft.getInstance().hitResult = constructHit;
                ManifoldClient.currentConstructRegion = constructHit.getRegion();
                cir.setReturnValue(constructHit);
            } else {
                ManifoldClient.lastConstructHit = null;
            }
        } else {
            ManifoldClient.lastConstructHit = null;
        }
    }
}