package dev.manifold.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.manifold.physics.collision.CollisionPlane;
import dev.manifold.physics.collision.ConstructCollisionEngine;
import dev.manifold.physics.collision.ConstructCollisionManager;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

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
        // Fast path: nothing to do if vanilla already zeroed out movement
        //if (vanillaResolved.lengthSqr() < 1e-12) return vanillaResolved;

        final Entity self = (Entity) (Object) this;
        final AABB startBB = this.getBoundingBox();
        final float stepHeight = Math.max(0.0f, this.maxUpStep());

        return ConstructCollisionEngine.resolveCollisions(vanillaResolved, self, startBB, stepHeight);
    }

    /**
     * @author t
     * @reason t
     */
    @Overwrite
    private static float[] collectCandidateStepUpHeights(
            AABB aabb,
            List<VoxelShape> vanillaShapes,
            float maxStep,
            float ignoreHeight
    ) {
        FloatSet set = new FloatArraySet(8);

        // --------- VANILLA ---------
        for (VoxelShape voxel : vanillaShapes) {
            for (double d : voxel.getCoords(Direction.Axis.Y)) {
                float h = (float)(d - aabb.minY);
                if (h < 0.0F || h == ignoreHeight) continue;
                if (h > maxStep) break;
                set.add(h);
            }
        }

        // --------- CONSTRUCTS ---------
        for (ConstructCollisionManager.Planes planes : ConstructCollisionManager.getAll()) {
            // Step-up heights come from UP-facing plane rects
            for (CollisionPlane.Rect r : planes.rects(Direction.UP)) {
                float h = (float)((r.depth / (double)ConstructCollisionManager.GRID) - aabb.minY);
                if (h < 0.0F || h == ignoreHeight) continue;
                if (h > maxStep) continue;
                set.add(h);
            }
        }

        float[] arr = set.toFloatArray();
        FloatArrays.unstableSort(arr);
        return arr;
    }
}
