package dev.manifold.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.manifold.physics.collision.ConstructCollisionEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityConstructCollisionMixin {
    @Shadow public abstract AABB getBoundingBox();
    @Shadow public abstract Level level();
    @Shadow public abstract float maxUpStep();

    /**
     * After vanilla has finished colliding with world blocks and entities,
     * we further clip the returned motion against any moving/rotating constructs.
     * <p>
     * We only ever reduce/redirect motion here – never increase it – so vanilla guarantees stay intact.
     */
    @ModifyReturnValue(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN")
    )
    private Vec3 manifold$applyConstructCollisions(Vec3 vanillaResolved) {
        final Entity self = (Entity) (Object) this;
        final AABB startBB = this.getBoundingBox();
        final float stepHeight = Math.max(0.0f, this.maxUpStep());
        return ConstructCollisionEngine.resolveCollisions(vanillaResolved, self, startBB, stepHeight);
    }
}
