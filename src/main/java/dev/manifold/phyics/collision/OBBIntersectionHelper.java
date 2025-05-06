package dev.manifold.phyics.collision;

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

        Vector3f t = new Vector3f((float) (b.center.x - a.center.x), (float) (b.center.y - a.center.y), (float) (b.center.z - a.center.z));
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
        Vec3 aCenter = aabb.getCenter();
        Vec3 aHalf = new Vec3(
                (aabb.maxX - aabb.minX) / 2,
                (aabb.maxY - aabb.minY) / 2,
                (aabb.maxZ - aabb.minZ) / 2
        );

        OBB aobb = new OBB(aCenter, aHalf, new Matrix3f());

        Vector3f[] axes = new Vector3f[6];
        for (int i = 0; i < 3; i++) {
            axes[i] = aobb.rotation.getColumn(i, new Vector3f());
            axes[i + 3] = obb.rotation.getColumn(i, new Vector3f());
        }

        double minOverlap = Double.POSITIVE_INFINITY;
        Vector3f bestAxis = null;

        Vector3f t = new Vector3f((float) (obb.center.x - aobb.center.x), (float) (obb.center.y - aobb.center.y), (float) (obb.center.z - aobb.center.z));

        for (Vector3f axis : axes) {
            if (axis.lengthSquared() < 1e-6f) continue;

            // Project half extents onto axis
            float aProj = projectOBBExtent(aobb, axis);
            float bProj = projectOBBExtent(obb, axis);
            float centerDist = Math.abs(t.dot(axis));

            float overlap = aProj + bProj - centerDist;
            if (overlap <= 0) return Vec3.ZERO; // No collision

            if (overlap < minOverlap) {
                minOverlap = overlap;
                bestAxis = new Vector3f(axis);
            }
        }

        if (bestAxis == null) return Vec3.ZERO;

        bestAxis.normalize();
        float direction = Math.signum(t.dot(bestAxis));
        return new Vec3(-bestAxis.x * minOverlap * direction, -bestAxis.y * minOverlap * direction, -bestAxis.z * minOverlap * direction);
    }

    private static float projectOBBExtent(OBB obb, Vector3f axis) {
        float projX = (float) obb.halfSize.x * Math.abs(axis.dot(obb.rotation.getColumn(0, new Vector3f())));
        float projY = (float) obb.halfSize.y * Math.abs(axis.dot(obb.rotation.getColumn(1, new Vector3f())));
        float projZ = (float) obb.halfSize.z * Math.abs(axis.dot(obb.rotation.getColumn(2, new Vector3f())));
        return projX + projY + projZ;
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