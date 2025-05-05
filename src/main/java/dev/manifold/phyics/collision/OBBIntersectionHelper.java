package dev.manifold.phyics.collision;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

public class OBBIntersectionHelper {
    public static boolean AABBIntersectsOBB(AABB aabb, OBB obb) {
        Vec3 center = aabb.getCenter();
        Vec3 halfSize = new Vec3(
                (aabb.maxX - aabb.minX) / 2,
                (aabb.maxY - aabb.minY) / 2,
                (aabb.maxZ - aabb.minZ) / 2
        );
        OBB aabbOBB = new OBB(center, halfSize, new Matrix3f());
        return intersects(aabbOBB, obb);
    }

    public static boolean intersects(OBB a, OBB b) {
        // SAT algorithm
        Matrix3f Ra = new Matrix3f(a.rotation);
        Matrix3f Rb = new Matrix3f(b.rotation);
        float[][] R = new float[3][3];
        float[][] AbsR = new float[3][3];

        for (int i = 0; i < 3; i++) {
            Vector3f ai = Ra.getColumn(i, new Vector3f());
            for (int j = 0; j < 3; j++) {
                Vector3f bj = Rb.getColumn(j, new Vector3f());
                R[i][j] = ai.dot(bj);
                AbsR[i][j] = Math.abs(R[i][j]) + 1e-6f;
            }
        }

        Vector3f t = new Vector3f((float)(b.center.x - a.center.x), (float)(b.center.y - a.center.y), (float)(b.center.z - a.center.z));
        Ra.transpose(new Matrix3f()).transform(t);

        for (int i = 0; i < 3; i++) {
            float ra = get(a.halfSize, i);
            float rb = get(b.halfSize, 0) * AbsR[i][0] + get(b.halfSize, 1) * AbsR[i][1] + get(b.halfSize, 2) * AbsR[i][2];
            if (Math.abs(t.get(i)) > ra + rb) return false;
        }

        for (int i = 0; i < 3; i++) {
            float ra = get(a.halfSize, 0) * AbsR[0][i] + get(a.halfSize, 1) * AbsR[1][i] + get(a.halfSize, 2) * AbsR[2][i];
            float rb = get(b.halfSize, i);
            float proj = Math.abs(t.x * R[0][i] + t.y * R[1][i] + t.z * R[2][i]);
            if (proj > ra + rb) return false;
        }

        return true;
    }

    public static Vec3 resolvePenetrationAABBtoOBB(AABB aabb, OBB obb) {
        // A simplified method that computes a minimum translation vector
        Vec3 centerA = aabb.getCenter();
        Vec3 halfA = new Vec3((aabb.maxX - aabb.minX) / 2, (aabb.maxY - aabb.minY) / 2, (aabb.maxZ - aabb.minZ) / 2);
        Vec3 delta = centerA.subtract(obb.center);

        // Axis-aligned fallback for now: pick shallowest overlap axis
        double xOverlap = halfA.x + obb.halfSize.x - Math.abs(delta.x);
        double yOverlap = halfA.y + obb.halfSize.y - Math.abs(delta.y);
        double zOverlap = halfA.z + obb.halfSize.z - Math.abs(delta.z);

        if (xOverlap <= 0 || yOverlap <= 0 || zOverlap <= 0) return Vec3.ZERO;

        if (xOverlap < yOverlap && xOverlap < zOverlap) {
            return new Vec3(xOverlap * Math.signum(delta.x), 0, 0);
        } else if (yOverlap < zOverlap) {
            return new Vec3(0, yOverlap * Math.signum(delta.y), 0);
        } else {
            return new Vec3(0, 0, zOverlap * Math.signum(delta.z));
        }
    }

    private static float get(Vec3 v, int axis) {
        return switch (axis) {
            case 0 -> (float) v.x;
            case 1 -> (float) v.y;
            case 2 -> (float) v.z;
            default -> 0f;
        };
    }
}