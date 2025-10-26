package dev.manifold.physics.collision;

import dev.manifold.physics.math.V3;

public final class VoxelSDF {
    public final float[][][] phi;   // narrow-band or full; values in blocks (+outside, -inside)
    public final int nx, ny, nz;    // dimensions
    public final V3 origin;         // local-space position of grid cell (0,0,0) corner
    public final double h = 1.0;    // voxel size (block)

    public VoxelSDF(float[][][] phi, V3 origin){
        this.phi = phi;
        this.nx = phi.length;
        this.ny = phi[0].length;
        this.nz = phi[0][0].length;
        this.origin = origin;
    }

    // Trilinear sample
    public double sample(V3 xLocal){
        double fx = (xLocal.x - origin.x)/h;
        double fy = (xLocal.y - origin.y)/h;
        double fz = (xLocal.z - origin.z)/h;
        int i = (int)Math.floor(fx), j = (int)Math.floor(fy), k = (int)Math.floor(fz);
        double tx = fx - i, ty = fy - j, tz = fz - k;
        if (i<0||i+1>=nx||j<0||j+1>=ny||k<0||k+1>=nz) return +1e3; // outside SDF band
        double c000 = phi[i  ][j  ][k  ];
        double c100 = phi[i+1][j  ][k  ];
        double c010 = phi[i  ][j+1][k  ];
        double c110 = phi[i+1][j+1][k  ];
        double c001 = phi[i  ][j  ][k+1];
        double c101 = phi[i+1][j  ][k+1];
        double c011 = phi[i  ][j+1][k+1];
        double c111 = phi[i+1][j+1][k+1];
        double c00 = c000*(1-tx) + c100*tx;
        double c01 = c001*(1-tx) + c101*tx;
        double c10 = c010*(1-tx) + c110*tx;
        double c11 = c011*(1-tx) + c111*tx;
        double c0 = c00*(1-tz) + c01*tz;
        double c1 = c10*(1-tz) + c11*tz;
        return c0*(1-ty) + c1*ty;
    }

    // Central-diff gradient (trilinear-based; h=1 => scale ~1)
    public V3 grad(V3 xLocal){
        double eps = 0.5; // one-half voxel for smoother gradient
        V3 dx = new V3(eps,0,0), dy=new V3(0,eps,0), dz=new V3(0,0,eps);
        double gx = sample(new V3(xLocal.x+dx.x, xLocal.y, xLocal.z)) - sample(new V3(xLocal.x-dx.x, xLocal.y, xLocal.z));
        double gy = sample(new V3(xLocal.x, xLocal.y+dy.y, xLocal.z)) - sample(new V3(xLocal.x, xLocal.y-dy.y, xLocal.z));
        double gz = sample(new V3(xLocal.x, xLocal.y, xLocal.z+dz.z)) - sample(new V3(xLocal.x, xLocal.y, xLocal.z-dz.z));
        return new V3(gx, gy, gz).scl(1.0/(2.0*eps));
    }
}