package dev.manifold.physics.core;

import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;

public final class OBB {
    public V3 c = new V3();     // center (local or world depending on context)
    public V3 e = new V3(0.5,0.5,0.5); // half extents (for blocks: 0.5)
    public M3 R = M3.identity();       // orientation
    public double mu = 0.6;     // friction coefficient
    public int id = -1;         // optional identifier

    public OBB() {}

    public OBB(V3 center, V3 halfExtents, M3 rotation, double mu, int id){
        this.c = center; this.e = halfExtents; this.R = rotation; this.mu = mu; this.id = id;
    }

    public OBB copy() {
        // Copy M3 by fields; don’t assume column vectors exist on M3
        M3 r = new M3(
                R.m00, R.m01, R.m02,
                R.m10, R.m11, R.m12,
                R.m20, R.m21, R.m22
        );
        return new OBB(new V3(c.x, c.y, c.z), new V3(e.x, e.y, e.z), r, mu, id);
    }
}