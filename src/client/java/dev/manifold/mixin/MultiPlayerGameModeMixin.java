package dev.manifold.mixin;

import dev.manifold.ConstructBlockHitResult;
import dev.manifold.ConstructRenderCache;
import dev.manifold.network.packets.BreakInConstructC2SPacket;
import dev.manifold.network.packets.PlaceInConstructC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void injectUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (hitResult instanceof ConstructBlockHitResult constructHit) {
            // Try placing in construct
            if (tryPlaceInConstruct(player, hand, constructHit)) {
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }

    private boolean tryPlaceInConstruct(Player player, InteractionHand hand, ConstructBlockHitResult hit) {
        if (!(player instanceof LocalPlayer localPlayer)) return false;

        Minecraft client = Minecraft.getInstance();
        ItemStack held = player.getItemInHand(hand);
        Block block = Block.byItem(held.getItem());
        if (block == Blocks.AIR) return false;

        ConstructRenderCache.CachedConstruct construct = hit.getConstruct();
        BlockPos rel = hit.getBlockPos().relative(hit.getDirection()).subtract(construct.origin());

        BlockState state = block.defaultBlockState(); // Later you can improve with context (rotation, waterlogging etc)

        ClientPlayNetworking.send(
                new PlaceInConstructC2SPacket(
                        construct.id(),
                        rel,
                        state
                )
        );

        // Play hand swing animation
        localPlayer.swing(hand);

        return true;
    }
}
