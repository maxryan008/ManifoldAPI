package dev.manifold.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.manifold.physics.collision.RotatedCollisionHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract Level level();

    @ModifyReturnValue(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN")
    )
    private Vec3 onCollideReturn(Vec3 original) {
        Entity self = (Entity) (Object) this;
        return RotatedCollisionHandler.collideWithConstructs(this.level().dimension(), original, getBoundingBox());
    }
}

