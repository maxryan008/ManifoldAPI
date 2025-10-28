package dev.manifold.physics.collision.bvh;

import dev.manifold.physics.math.V3;

import java.util.List;

/** Simple BVH over a set of OBB indices with axis-aligned nodes in local space. */
public final class BVH {
    public static final class AABB {
        public V3 center, extents;
        public AABB(V3 c, V3 e) { center = c; extents = e; }
        public static AABB of(V3 min, V3 max) {
            V3 c = new V3((min.x+max.x)*0.5, (min.y+max.y)*0.5, (min.z+max.z)*0.5);
            V3 e = new V3((max.x-min.x)*0.5, (max.y-min.y)*0.5, (max.z-min.z)*0.5);
            return new AABB(c, e);
        }
        public void grow(AABB b) {
            V3 min = min(), max = max();
            V3 bmin = b.min(), bmax = b.max();
            min = new V3(Math.min(min.x,bmin.x), Math.min(min.y,bmin.y), Math.min(min.z,bmin.z));
            max = new V3(Math.max(max.x,bmax.x), Math.max(max.y,bmax.y), Math.max(max.z,bmax.z));
            V3 c = new V3((min.x+max.x)*0.5, (min.y+max.y)*0.5, (min.z+max.z)*0.5);
            V3 e = new V3((max.x-min.x)*0.5, (max.y-min.y)*0.5, (max.z-min.z)*0.5);
            center=c; extents=e;
        }
        public V3 min(){ return new V3(center.x-extents.x, center.y-extents.y, center.z-extents.z); }
        public V3 max(){ return new V3(center.x+extents.x, center.y+extents.y, center.z+extents.z); }
    }

    public static final class Node {
        public AABB box;
        public int left=-1, right=-1, parent=-1;
        public int leafIndex=-1; // index in the leaf array (merged OBB index)
        public boolean isLeaf() { return leafIndex>=0; }
    }

    public final List<Node> nodes;
    public final int root;
    public final AABB rootBounds;

    public BVH(List<Node> nodes, int root, AABB rootBounds) {
        this.nodes = nodes;
        this.root = root;
        this.rootBounds = rootBounds;
    }
}