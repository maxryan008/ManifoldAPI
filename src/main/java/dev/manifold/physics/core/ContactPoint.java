package dev.manifold.physics.core;

import dev.manifold.physics.math.V3;

public final class ContactPoint {
    public V3 p = new V3();   // world contact point
    public V3 n = new V3();   // normal from A->B (unit)
    public double penetration; // (>0 overlap amount)
    public int bObbIndex;     // which OBB of B we hit
    public double muPair;     // μA * μB (your rule)
    // Warm-start accumulators:
    public double lambdaN;
    public double lambdaT1, lambdaT2;
}

