package dev.manifold.mixin;

import dev.manifold.ConstructBlockHitResult;
import dev.manifold.ConstructBreaker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "continueAttack(Z)V", at = @At("HEAD"))
    private void onContinueAttack(boolean bl, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        HitResult hit = client.hitResult;
        if (
                client.screen != null ||
                        client.player == null ||
                        !client.options.keyAttack.isDown()
        ) {
            ConstructBreaker.getInstance().reset(); // Not attacking
            return;
        }

        if (hit instanceof ConstructBlockHitResult constructHit) {
            BlockPos hitBlockPos = constructHit.getBlockPos();
            Direction dir = constructHit.getDirection();

            ConstructBreaker.getInstance().tick(client.player, constructHit.getConstruct().id(), hitBlockPos, dir);
        } else {
            // ✅ Not looking at a construct anymore, reset progress
            ConstructBreaker.getInstance().reset();
        }
    }
}
