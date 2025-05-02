package dev.manifold;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.manifold.util.QuaternionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.UUID;

public class DynamicConstruct {
    public static final Codec<DynamicConstruct> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(DynamicConstruct::getId),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("world").forGetter(DynamicConstruct::getWorldKey),
            BlockPos.CODEC.fieldOf("sim_origin").forGetter(DynamicConstruct::getSimOrigin),
            BlockPos.CODEC.fieldOf("negative_bounds").forGetter(DynamicConstruct::getNegativeBounds),
            BlockPos.CODEC.fieldOf("positive_bounds").forGetter(DynamicConstruct::getPositiveBounds),
            Vec3.CODEC.fieldOf("position").forGetter(DynamicConstruct::getPosition),
            Vec3.CODEC.fieldOf("velocity").forGetter(DynamicConstruct::getVelocity),
            QuaternionData.CODEC.fieldOf("rotation").forGetter(dc -> new QuaternionData(dc.getRotation())),
            QuaternionData.CODEC.fieldOf("angular_velocity").forGetter(dc -> new QuaternionData(dc.getAngularVelocity()))
    ).apply(instance, (id, world, origin, neg, pos, posVec, vel, rot, angVel) -> {
        DynamicConstruct construct = new DynamicConstruct(id, world, origin);
        construct.setNegativeBounds(neg);
        construct.setPositiveBounds(pos);
        construct.setPosition(posVec);
        construct.setVelocity(vel);
        construct.setRotation(rot.toQuaternionf());
        construct.setAngularVelocity(angVel.toQuaternionf());
        return construct;
    }));


    // Unique ID for saving/syncing
    private final UUID id;
    // Physical origin in construct dimension
    private final BlockPos simOrigin;
    // The dimension it's rendered in
    private final ResourceKey<Level> world;
    // Bounding box around construct
    private BlockPos negativeBounds;
    private BlockPos positiveBounds;

    // Local position and physics
    private Vec3 position;
    private Vec3 velocity;

    private Quaternionf rotation;
    private Quaternionf angularVelocity;

    public DynamicConstruct(UUID id, ResourceKey<Level> world, BlockPos simOrigin) {
        this.id = id;
        this.world = world;
        this.simOrigin = simOrigin;

        this.negativeBounds = BlockPos.ZERO.offset(-1, -1, -1);
        this.positiveBounds = BlockPos.ZERO.offset(1, 1, 1);

        this.position = Vec3.ZERO;
        this.velocity = Vec3.ZERO;

        this.rotation = new Quaternionf(); // Identity rotation
        this.angularVelocity = new Quaternionf(); // No angular velocity
    }

    public UUID getId() {
        return id;
    }

    public ResourceKey<Level> getWorldKey() {
        return world;
    }

    public BlockPos getSimOrigin() {
        return simOrigin;
    }

    public AABB getBoundingBox() {
        BlockPos min = simOrigin.offset(negativeBounds.getX(), negativeBounds.getY(), negativeBounds.getZ());
        BlockPos max = simOrigin.offset(positiveBounds.getX(), positiveBounds.getY(), positiveBounds.getZ());
        return new AABB(new Vec3(min.getX(), min.getY(), min.getZ()), new Vec3(max.getX(), max.getY(), max.getZ()));
    }

    public AABB getRenderBoundingBox() {
        Vec3 min = position.add(negativeBounds.getX(), negativeBounds.getY(), negativeBounds.getZ());
        Vec3 max = position.add(positiveBounds.getX(), positiveBounds.getY(), positiveBounds.getZ()).add(1, 1, 1);
        return new AABB(new Vec3(min.x, min.y, min.z), new Vec3(max.x, max.y, max.z));
    }

    public BlockPos getNegativeBounds() {
        return this.negativeBounds;
    }

    public void setNegativeBounds(BlockPos negativeBounds) {
        this.negativeBounds = negativeBounds;
    }

    public BlockPos getPositiveBounds() {
        return this.positiveBounds;
    }

    public void setPositiveBounds(BlockPos positiveBounds) {
        this.positiveBounds = positiveBounds;
    }

    public Vec3 getPosition() {
        return position;
    }

    public void setPosition(Vec3 pos) {
        this.position = pos;
    }

    public Vec3 getVelocity() {
        return velocity;
    }

    public void setVelocity(Vec3 velocity) {
        this.velocity = velocity;
    }

    public void addVelocity(Vec3 delta) {
        this.velocity = this.velocity.add(delta);
    }

    public Quaternionf getRotation() {
        return rotation;
    }

    public void setRotation(Quaternionf rotation) {
        this.rotation = rotation;
    }

    public Quaternionf getAngularVelocity() {
        return angularVelocity;
    }

    public void setAngularVelocity(Quaternionf angularVelocity) {
        this.angularVelocity = angularVelocity;
    }

    public void addAngularVelocity(Quaternionf delta) {
        this.angularVelocity = this.angularVelocity.mul(delta);
    }

    public void physicsTick() {
        // Apply velocity to position
        this.position = this.position.add(velocity);

        // Apply rotation from angular velocity (assuming quaternion rotation)
        if (!angularVelocity.equals(new Quaternionf(0, 0, 0, 1))) {
            this.rotation = new Quaternionf(this.angularVelocity).mul(this.rotation);
            this.rotation.normalize();
        }

    }
}
