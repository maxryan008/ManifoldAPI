package dev.manifold.physics.collision;

import dev.manifold.physics.math.V3;

public class VoxelSDF {
    public final float[][][] phi;
    public final V3 origin;
    public VoxelSDF(float[][][] phi, V3 origin){ this.phi=phi; this.origin=origin; }

    public double sampleLocal(double x, double y, double z){
        int i = (int)Math.floor(x - origin.x);
        int j = (int)Math.floor(y - origin.y);
        int k = (int)Math.floor(z - origin.z);
        i = clamp(i, 0, phi.length-1);
        j = clamp(j, 0, phi[0].length-1);
        k = clamp(k, 0, phi[0][0].length-1);
        return phi[i][j][k];
    }

    public double sampleWorld(ConstructCollisionManager.ConstructRecord rec, V3 world){
        // x_local = localOrigin + R^T*(world - p)
        V3 lw = rec.state.getRotation().transpose()
                .mul(world.sub(rec.state.getPosition()))
                .add(rec.localOrigin);
        return sampleLocal(lw.x, lw.y, lw.z);
    }

    public V3 gradientWorld(ConstructCollisionManager.ConstructRecord rec, V3 world){
        double h = 0.5;
        double d0 = sampleWorld(rec, world.add(h,0,0));
        double d1 = sampleWorld(rec, world.add(-h,0,0));
        double d2 = sampleWorld(rec, world.add(0,h,0));
        double d3 = sampleWorld(rec, world.add(0,-h,0));
        double d4 = sampleWorld(rec, world.add(0,0,h));
        double d5 = sampleWorld(rec, world.add(0,0,-h));
        V3 g = new V3((d0-d1)/(2*h), (d2-d3)/(2*h), (d4-d5)/(2*h));
        double L = g.length();
        return (L<1e-9) ? null : g.scale(1.0/L);
    }

    private static int clamp(int v,int lo,int hi){ return (v<lo)? lo : (v>hi? hi : v); }
}