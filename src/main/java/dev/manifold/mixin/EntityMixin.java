package dev.manifold.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.manifold.phyics.collision.RotatedCollisionHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow public abstract AABB getBoundingBox();

    @ModifyReturnValue(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN")
    )
    private Vec3 onCollideReturn(Vec3 original) {
        Entity self = (Entity) (Object) this;
        return RotatedCollisionHandler.collideWithConstructs(original, getBoundingBox());
    }
}

