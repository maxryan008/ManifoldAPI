package dev.manifold;

import dev.manifold.network.packets.BreakInConstructC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class ConstructBreaker {
    private static final ConstructBreaker INSTANCE = new ConstructBreaker();
    public static ConstructBreaker getInstance() { return INSTANCE; }

    private UUID lastConstruct;
    private BlockPos lastPos;
    private float progress;
    private int ticks;
    private int delay;

    public void tick(Player player, UUID constructId, BlockPos hitBlockPos, Direction direction) {
        if (!constructId.equals(lastConstruct) || !hitBlockPos.equals(lastPos)) {
            this.lastConstruct = constructId;
            this.lastPos = hitBlockPos;
            this.progress = 0;
            this.ticks = 0;
            this.delay = 0;
        }

        Minecraft mc = Minecraft.getInstance();

        // Skip delay if cooldown active
        if (delay > 0) {
            delay--;
            return;
        }

        ClientLevel level = mc.level;
        BlockState state = ManifoldClient.currentConstructRegion.getBlockState(hitBlockPos);
        if (state.isAir()) return;

        float hardness = state.getDestroyProgress(player, level, hitBlockPos);
        progress += hardness;
        ticks++;

        // Optional sound
        if (ticks % 4 == 0) {
            SoundType sound = state.getSoundType();
            mc.getSoundManager().play(
                    new SimpleSoundInstance(
                            sound.getHitSound(),
                            SoundSource.BLOCKS,
                            (sound.getVolume() + 1.0F) / 8.0F,
                            sound.getPitch() * 0.5F,
                            SoundInstance.createUnseededRandom(),
                            hitBlockPos
                    )
            );
        }

        // Visual crack (optional): use destroyBlockProgress()

        if (progress >= 1.0f || player.isCreative()) {
            // Send packet to break the block
            ClientPlayNetworking.send(new BreakInConstructC2SPacket(constructId, hitBlockPos));
            mc.player.swing(InteractionHand.MAIN_HAND);

            // Reset state
            reset();
        }
    }

    public void reset() {
        lastConstruct = null;
        lastPos = null;
        progress = 0;
        ticks = 0;
        delay = 5;
    }
}
