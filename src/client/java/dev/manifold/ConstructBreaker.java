package dev.manifold;

import dev.manifold.network.packets.BreakInConstructC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class ConstructBreaker {
    private static final ConstructBreaker INSTANCE = new ConstructBreaker();
    private UUID lastConstruct;
    private BlockPos lastPos;
    private float progress;
    private int ticks;
    private int delay;

    public static ConstructBreaker getInstance() {
        return INSTANCE;
    }

    public void tick(Player player, UUID constructId, BlockPos hitBlockPos) {
        Minecraft mc = Minecraft.getInstance();

        if (!constructId.equals(lastConstruct) || !hitBlockPos.equals(lastPos)) {
            this.lastConstruct = constructId;
            this.lastPos = hitBlockPos;
            this.progress = 0;
            this.ticks = 0;
            assert mc.player != null;
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        // Skip delay if cooldown active
        if (delay > 0) {
            delay--;
            return;
        }

        ClientLevel level = mc.level;
        assert ManifoldClient.currentConstructRegion != null;
        BlockState state = ManifoldClient.currentConstructRegion.getBlockState(hitBlockPos);
        if (state.isAir()) return;

        float hardness = state.getDestroyProgress(player, level, hitBlockPos);
        progress += hardness;
        ticks++;

        assert ManifoldClient.lastConstructHit != null;
        BlockPos constructOrigin = ManifoldClient.lastConstructHit.getConstruct().origin();
        Vec3 constructPosition = ManifoldClient.lastConstructHit.getConstruct().currentPosition();

        BlockPos relativePosition = hitBlockPos.subtract(constructOrigin).offset((int) constructPosition.x, (int) constructPosition.y, (int) constructPosition.z);

        //Breaking sound
        if (ticks % 4 == 0) {
            SoundType sound = state.getSoundType();
            mc.getSoundManager().play(
                    new SimpleSoundInstance(
                            sound.getHitSound(),
                            SoundSource.BLOCKS,
                            (sound.getVolume() + 1.0F) / 8.0F,
                            sound.getPitch() * 0.5F,
                            SoundInstance.createUnseededRandom(),
                            relativePosition
                    )
            );
        }

        if (progress >= 1.0f || player.isCreative()) {
            if (delay == 0) {
                // Send packet to break the block
                ClientPlayNetworking.send(new BreakInConstructC2SPacket(constructId, hitBlockPos));
                assert Minecraft.getInstance().player != null;
                Minecraft.getInstance().player.swing(InteractionHand.MAIN_HAND);

                //Break sound
                SoundType sound = state.getSoundType();
                mc.getSoundManager().play(
                        new SimpleSoundInstance(
                                sound.getBreakSound(),
                                SoundSource.BLOCKS,
                                sound.getVolume(),
                                sound.getPitch(),
                                SoundInstance.createUnseededRandom(),
                                relativePosition
                        )
                );

                // Reset and apply creative cooldown
                reset();
                delay = player.isCreative() ? 5 : 0; // 5-tick cooldown for creative
            }
        }
    }

    public void reset() {
        lastConstruct = null;
        lastPos = null;
        progress = 0;
        ticks = 0;
    }

    public void resetDelay() {
        delay = 0;
    }
}
