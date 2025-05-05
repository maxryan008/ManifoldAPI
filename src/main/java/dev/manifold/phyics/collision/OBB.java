package dev.manifold.phyics.collision;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;

public class OBB {
    public Vec3 center;
    public Vec3 halfSize;
    public Matrix3f rotation;

    public OBB(Vec3 center, Vec3 halfSize, Matrix3f rotation) {
        this.center = center;
        this.halfSize = halfSize;
        this.rotation = new Matrix3f(rotation);
    }

    public static OBB fromAABB(AABB box, Matrix3f rotation) {
        double centerX = (box.minX + box.maxX) / 2.0;
        double centerY = (box.minY + box.maxY) / 2.0;
        double centerZ = (box.minZ + box.maxZ) / 2.0;

        double halfX = (box.maxX - box.minX) / 2.0;
        double halfY = (box.maxY - box.minY) / 2.0;
        double halfZ = (box.maxZ - box.minZ) / 2.0;

        return new OBB(new Vec3(centerX, centerY, centerZ), new Vec3(halfX, halfY, halfZ), rotation);
    }
}