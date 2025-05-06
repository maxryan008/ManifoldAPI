package dev.manifold.mixin;

import dev.manifold.*;
import dev.manifold.network.packets.PickConstructBlockWithDataC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

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
            ConstructBreaker.getInstance().resetDelay();
            return;
        }

        if (hit instanceof ConstructBlockHitResult constructHit) {
            BlockPos hitBlockPos = constructHit.getBlockPos();

            ConstructBreaker.getInstance().tick(client.player, constructHit.getConstruct().id(), hitBlockPos);
        } else {
            // ✅ Not looking at a construct anymore, reset progress
            ConstructBreaker.getInstance().reset();
        }
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void injectPickBlock(CallbackInfo ci) {
        if (Minecraft.getInstance().hitResult instanceof ConstructBlockHitResult hit) {
            UUID id = hit.getConstruct().id();
            BlockPos blockPos = hit.getBlockPos();
            Level simLevel = ConstructManager.INSTANCE.getSimDimension();
            BlockPos origin = ConstructManager.INSTANCE.getSimOrigin(id).orElse(null);
            Minecraft minecraft = Minecraft.getInstance();
            if (origin == null) return;

            BlockState blockState = simLevel.getBlockState(blockPos);
            if (blockState.isAir()) return;

            ItemStack pick = blockState.getBlock().getCloneItemStack(simLevel, blockPos, blockState);
            if (pick.isEmpty()) return;

            if (blockState.isAir()) {
                return;
            }

            Block block = blockState.getBlock();
            ItemStack itemStack = block.getCloneItemStack(simLevel, blockPos, blockState);
            if (itemStack.isEmpty()) {
                return;
            }

            LocalPlayer player = minecraft.player;

            assert player != null;
            boolean bl = player.getAbilities().instabuild;

            if (bl && Screen.hasControlDown() && blockState.hasBlockEntity()) {
                ClientPlayNetworking.send(new PickConstructBlockWithDataC2SPacket(id, blockPos));
                ci.cancel(); // Stop vanilla from continuing
                return; // Don't continue with the rest of the code and wait for a server response instead
            }

            if (itemStack.isEmpty()) {
                String string = BuiltInRegistries.BLOCK.getKey(simLevel.getBlockState(blockPos).getBlock()).toString();
                Manifold.LOGGER.warn("Picking on: [Block] {} gave null item", string);
            } else {
                ManifoldClient.handlePickItem(minecraft, player, bl, itemStack);
            }

            ci.cancel(); // Stop vanilla from running its version
        }
    }
}
