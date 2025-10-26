package dev.manifold.physics.core;

import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;

public final class OBB {
    // World pose
    public V3 c = new V3();        // center
    public M3 R = new M3();        // rotation (columns = local axes in world)
    public V3 e = new V3(0.5,0.5,0.5); // half-extents

    // Dynamics (for the aggregate owner body)
    // NOTE: for aggregate lists we read these from the owning RigidState
    // but we keep them here for convenience in narrowphase math.
    public double mu = 0.6;     // friction for this OBB (material)
    public double restitution = 0.0;

    public int id = 0; // optional id for warm starting keys

    public OBB set(V3 center, M3 rot, V3 halfExt, double mu, double eRest, int id){
        this.c.set(center); this.R.set(rot.c0, rot.c1, rot.c2); this.e.set(halfExt);
        this.mu = mu; this.restitution = eRest; this.id = id; return this;
    }
}