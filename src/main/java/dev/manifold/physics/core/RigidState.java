package dev.manifold.physics.core;

import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;

public final class RigidState {
    public V3 pos = new V3();      // body origin
    public M3 rot = new M3();      // body orientation
    public V3 vLin = new V3();     // linear velocity
    public V3 vAng = new V3();     // angular velocity (rad/s)
    public double invMass = 0.0;   // 0 => kinematic
    public M3 invInertiaWorld = M3.identity(); // world-space inverse inertia (0-matrix if kinematic)

    public RigidState set(V3 pos, M3 rot, V3 vLin, V3 vAng, double invMass, M3 invInertiaWorld){
        this.pos.set(pos); this.rot.set(rot.c0,rot.c1,rot.c2);
        this.vLin.set(vLin); this.vAng.set(vAng);
        this.invMass = invMass; this.invInertiaWorld = invInertiaWorld;
        return this;
    }

    public void integrate(double dt) {
        // For kinematic bodies you typically set vLin/vAng externally each tick and still integrate pose:
        pos.add(V3.scl(vLin, dt));
        // Simple angular integration (small-angle): pos stays; rot columns rotate by ω×axis*dt
        V3 wx = vAng; // alias only
        if (wx.len2() > 1e-18) {
            // rotate basis columns by small-angle approx
            V3[] cols = { rot.c0.cpy(), rot.c1.cpy(), rot.c2.cpy() };
            rot.c0.add(wx.crs(cols[0]).scl(dt)).nor();
            rot.c1.add(wx.crs(cols[1]).scl(dt)).nor();
            rot.c2.add(wx.crs(cols[2]).scl(dt)).nor();
        }
    }
}