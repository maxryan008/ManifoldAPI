package dev.manifold.physics.collision;

import dev.manifold.physics.core.OBB;
import dev.manifold.physics.math.V3;

import java.util.*;

/**
 * Merge axis-aligned unit voxels (OBBs with R=I, e=0.5) into larger boxes by
 * sweeping runs along X, then slab along Y, then stacks along Z.
 * Assumes all OBBs share identity orientation in LOCAL space.
 */
public final class PatchMerger {

    public static List<OBB> mergeVoxels(List<OBB> vox) {
        if (vox.isEmpty()) return Collections.emptyList();

        // Bucket voxels by integer cell coords
        HashSet<Long> cells = new HashSet<>(vox.size()*2);
        for (OBB o: vox) {
            int x = (int)Math.floor(o.c.x);
            int y = (int)Math.floor(o.c.y);
            int z = (int)Math.floor(o.c.z);
            cells.add(key(x,y,z));
        }

        ArrayList<OBB> out = new ArrayList<>();
        // Greedy consume: build maximal boxes
        while(!cells.isEmpty()){
            long k = cells.iterator().next();
            int x0 = (int)(k & 0x1FFFFFL);
            int y0 = (int)((k>>21) & 0x1FFFFFL);
            int z0 = (int)((k>>42) & 0x1FFFFFL);
            if ((x0 & (1<<20))!=0) x0 |= ~0x1FFFFF;
            if ((y0 & (1<<20))!=0) y0 |= ~0x1FFFFF;
            if ((z0 & (1<<20))!=0) z0 |= ~0x1FFFFF;

            // grow X
            int x1=x0;
            while(cells.contains(key(x1+1,y0,z0))) x1++;

            // for each x-run cell, attempt grow Y uniformly
            int y1=y0;
            outerY: while(true){
                for(int x=x0;x<=x1;x++){
                    if (!cells.contains(key(x,y1+1,z0))) break outerY;
                }
                y1++;
            }

            // grow Z uniformly on the X×Y rectangle
            int z1=z0;
            outerZ: while(true){
                for(int y=y0;y<=y1;y++){
                    for(int x=x0;x<=x1;x++){
                        if (!cells.contains(key(x,y,z1+1))) break outerZ;
                    }
                }
                z1++;
            }

            // consume all cells in box
            for(int z=z0;z<=z1;z++)
                for(int y=y0;y<=y1;y++)
                    for(int x=x0;x<=x1;x++)
                        cells.remove(key(x,y,z));

            // emit merged OBB (still axis aligned, local)
            double cx = (x0+x1+1)*0.5;
            double cy = (y0+y1+1)*0.5;
            double cz = (z0+z1+1)*0.5;
            double ex = (x1-x0+1)*0.5;
            double ey = (y1-y0+1)*0.5;
            double ez = (z1-z0+1)*0.5;

            OBB b = new OBB();
            b.c = new V3(cx,cy,cz);
            b.e = new V3(ex,ey,ez);
            b.R = b.R.identity();
            b.id = -1; b.mu = 0.6;
            out.add(b);
        }

        return out;
    }

    private static long key(int x,int y,int z){
        // pack signed 21 bits each into 63 bits
        long xx = x & 0x1FFFFF; long yy = y & 0x1FFFFF; long zz = z & 0x1FFFFF;
        return (xx) | (yy<<21) | (zz<<42);
    }
}