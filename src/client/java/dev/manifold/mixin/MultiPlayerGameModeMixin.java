package dev.manifold.mixin;

import dev.manifold.ConstructBlockHitResult;
import dev.manifold.ConstructRenderCache;
import dev.manifold.network.packets.UseConstructBlockC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void injectUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (hitResult instanceof ConstructBlockHitResult constructHit) {
            if (tryUseConstructBlock(player, hand, constructHit)) {
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }

    @Unique
    private boolean tryUseConstructBlock(LocalPlayer player, InteractionHand hand, ConstructBlockHitResult hit) {
        if (!(player instanceof LocalPlayer localPlayer)) return false;
        ConstructRenderCache.CachedConstruct construct = hit.getConstruct();
        BlockPos blockPos = hit.getBlockPos();
        Direction hitSide = hit.getDirection();

        // Send to server only if it would consume action
        ClientPlayNetworking.send(
                new UseConstructBlockC2SPacket(
                        construct.id(),
                        blockPos,
                        hand,
                        hitSide
                )
        );
        return false;
    }
}
