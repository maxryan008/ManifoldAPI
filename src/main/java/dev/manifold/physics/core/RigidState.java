package dev.manifold.physics.core;

import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;

public final class RigidState {
    private V3 p;   // position
    private M3 R;   // rotation
    private V3 v;   // linear velocity
    private V3 w;   // angular velocity
    private double invMass;
    private M3 invInertiaWorld;

    public void set(V3 p, M3 R, V3 v, V3 w, double invMass, M3 invI) {
        this.p = p; this.R = R; this.v = v; this.w = w;
        this.invMass = invMass; this.invInertiaWorld = invI;
    }

    public V3 getPosition() { return p; }
    public M3 getRotation() { return R; }
    public V3 getLinearVelocity() { return v; }
    public V3 getAngularVelocity() { return w; }
    public double getInvMass() { return invMass; }
    public M3 getInvInertiaWorld() { return invInertiaWorld; }

    // Convenience (used in manager/engine)
    public final V3 p(){ return p; }
    public final M3 R(){ return R; }
    public final V3 v(){ return v; }
    public final V3 w(){ return w; }
}