package dev.manifold.mixin.accessor;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {
    @Invoker("nextContainerCounter")
    void manifold$invokeNextContainerCounter();

    @Accessor("containerCounter")
    int getContainerCounter();
}