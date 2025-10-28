package dev.manifold.physics.collision;

import dev.manifold.physics.math.V3;

/** SDF-assisted culling/normal seeding. */
final class Hybrid {
    static boolean cullBySDF(ConstructCollisionManager.ConstructRecord A,
                             ConstructCollisionManager.ConstructRecord B,
                             V3 worldPoint, double margin){
        if (A.sdf==null || B.sdf==null) return false;
        double dA = A.sdf.sampleWorld(A, worldPoint);
        double dB = B.sdf.sampleWorld(B, worldPoint);
        return (dA>margin) || (dB>margin);
    }

    static V3 normalFromSDF(ConstructCollisionManager.ConstructRecord A,
                            ConstructCollisionManager.ConstructRecord B,
                            V3 worldPoint, V3 fallback){
        V3 gA = (A.sdf!=null)? A.sdf.gradientWorld(A, worldPoint) : null;
        V3 gB = (B.sdf!=null)? B.sdf.gradientWorld(B, worldPoint) : null;
        V3 g = null;
        if (gA!=null && gB!=null) g = gA.add(gB).normalize();
        else if (gA!=null) g = gA.normalize();
        else if (gB!=null) g = gB.neg().normalize(); // outward from B
        return (g!=null)? g : fallback;
    }
}