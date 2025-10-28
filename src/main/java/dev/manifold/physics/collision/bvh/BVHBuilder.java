package dev.manifold.physics.collision.bvh;

import dev.manifold.physics.core.OBB;
import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;
import dev.manifold.physics.collision.bvh.BVH.AABB;
import dev.manifold.physics.collision.bvh.BVH.Node;

import java.util.*;

/** Minimal LBVH builder using Morton codes over OBB centers (local space). */
public final class BVHBuilder {

    public static BVH buildLBVH(List<OBB> obbs) {
        // Handle empty set: make a degenerate BVH with one dummy node
        if (obbs == null || obbs.isEmpty()) {
            ArrayList<Node> nn = new ArrayList<>(1);
            Node n = new Node();
            n.box = new AABB(new V3(0,0,0), new V3(0,0,0));
            nn.add(n);
            return new BVH(nn, 0, n.box);
        }

        final int n = obbs.size();

        // Compute Morton codes
        long[] morton = new long[n];
        Integer[] order = new Integer[n];
        V3 min = new V3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        V3 max = new V3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (int i = 0; i < n; i++) {
            V3 c = obbs.get(i).c;
            min = new V3(Math.min(min.x, c.x), Math.min(min.y, c.y), Math.min(min.z, c.z));
            max = new V3(Math.max(max.x, c.x), Math.max(max.y, c.y), Math.max(max.z, c.z));
        }
        V3 size = max.sub(min);
        double sx = size.x > 1e-9 ? size.x : 1.0;
        double sy = size.y > 1e-9 ? size.y : 1.0;
        double sz = size.z > 1e-9 ? size.z : 1.0;

        for (int i = 0; i < n; i++) {
            order[i] = i;
            V3 c = obbs.get(i).c;
            double nx = (c.x - min.x) / sx;
            double ny = (c.y - min.y) / sy;
            double nz = (c.z - min.z) / sz;
            morton[i] = morton3D(nx, ny, nz);
        }

        Arrays.sort(order, Comparator.comparingLong(i -> morton[i]));

        // Create leaf nodes (one per OBB) in node list
        ArrayList<Node> nodes = new ArrayList<>(2 * n);
        int[] leafIdx = new int[n]; // map sorted position -> node index in "nodes"
        for (int i = 0; i < n; i++) {
            Node leaf = new Node();
            OBB b = obbs.get(order[i]);
            // Local oriented boxes are identity oriented, so leaf AABB is just [c-e, c+e]
            leaf.box = AABB.of(b.c.sub(b.e), b.c.add(b.e));
            leaf.leafIndex = order[i];
            leaf.left = -1;
            leaf.right = -1;
            leaf.parent = -1;
            leafIdx[i] = nodes.size();
            nodes.add(leaf);
        }

        // Recursively build internal nodes over range [0, n)
        int root = buildRange(nodes, leafIdx, morton, order, 0, n);

        // Post-pass to compute boxes upward (parents already linked)
        computeBoxesUp(nodes, root);

        return new BVH(nodes, root, nodes.get(root).box);
    }

    /** Recursively builds a subtree over leaves in [first, last), returns node index of the subtree root. */
    private static int buildRange(ArrayList<Node> nodes,
                                  int[] leafIdx,
                                  long[] morton, Integer[] order,
                                  int first, int last) {
        int count = last - first;
        if (count == 1) {
            // Single leaf — return its node index
            return leafIdx[first];
        }

        // Determine split point using longest-common-prefix of Morton codes
        int split = findSplit(morton, order, first, last);

        int leftChild  = buildRange(nodes, leafIdx, morton, order, first, split + 1);
        int rightChild = buildRange(nodes, leafIdx, morton, order, split + 1, last);

        Node parent = new Node();
        parent.left = leftChild;
        parent.right = rightChild;
        parent.parent = -1; // set after adding
        parent.leafIndex = -1;
        parent.box = new AABB(new V3(0,0,0), new V3(0,0,0)); // filled in computeBoxesUp
        int parentIdx = nodes.size();
        nodes.add(parent);

        nodes.get(leftChild).parent = parentIdx;
        nodes.get(rightChild).parent = parentIdx;

        return parentIdx;
    }

    private static void computeBoxesUp(ArrayList<Node> nodes, int root) {
        // Iterative post-order traversal from root to leaves and back up
        Deque<Integer> stack = new ArrayDeque<>();
        List<Integer> post = new ArrayList<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            int i = stack.pop();
            post.add(i);
            Node n = nodes.get(i);
            if (n.left >= 0) stack.push(n.left);
            if (n.right >= 0) stack.push(n.right);
        }
        for (int k = post.size() - 1; k >= 0; k--) {
            int i = post.get(k);
            Node n = nodes.get(i);
            if (n.isLeaf()) continue;
            AABB a = nodes.get(n.left).box;
            AABB b = nodes.get(n.right).box;
            V3 min = a.min(); V3 max = a.max();
            V3 bmin = b.min(); V3 bmax = b.max();
            min = new V3(Math.min(min.x, bmin.x), Math.min(min.y, bmin.y), Math.min(min.z, bmin.z));
            max = new V3(Math.max(max.x, bmax.x), Math.max(max.y, bmax.y), Math.max(max.z, bmax.z));
            n.box = AABB.of(min, max);
        }
    }

    private static int buildHierarchy(ArrayList<Node> nodes, int[] leafNodeIndex, long[] morton, Integer[] order) {
        int n = order.length;
        if (n==1) return 0; // single leaf already in nodes[0]

        // map from leaf sequence index to internal node index
        int firstInternal = nodes.size();
        for (int i=0;i<n-1;i++) {
            Node internal = new Node();
            internal.box = new BVH.AABB(new V3(0,0,0), new V3(0,0,0));
            nodes.add(internal);
        }

        // build tree by linking leaves
        for (int i=0;i<n-1;i++){
            int iL = longestCommonPrefix(morton, order, i, i+1)> longestCommonPrefix(morton, order, i, i-1)? i : i-1;
            int dir = (iL < i)? 1 : -1;
            int minL = Math.min(i, iL);
            int maxR = Math.max(i, iL) + 1;

            int split = findSplit(morton, order, minL, maxR);

            int left = (split == minL)? leafNodeIndex[split] : (firstInternal + split);
            int right = (split+1 == maxR-1)? leafNodeIndex[split+1] : (firstInternal + split + 1);

            int parent = firstInternal + i;
            Node p = nodes.get(parent);
            p.left = left;
            p.right = right;

            nodes.get(left).parent = parent;
            nodes.get(right).parent = parent;
        }
        return firstInternal + (n-2);
    }

    private static int longestCommonPrefix(long[] morton, Integer[] order, int i, int j) {
        if (j<0 || j>=order.length) return -1;
        long a = morton[order[i]];
        long b = morton[order[j]];
        if (a==b) {
            // tie-breaker by index
            return 64 + Integer.numberOfLeadingZeros(i ^ j);
        }
        return Long.numberOfLeadingZeros(a ^ b);
    }

    private static int findSplit(long[] morton, Integer[] order, int first, int last) {
        long firstCode = morton[order[first]];
        long lastCode = morton[order[last-1]];
        if (firstCode == lastCode) return (first+last-1)/2;

        int commonPrefix = Long.numberOfLeadingZeros(firstCode ^ lastCode);
        int split = first; int step = last-first;
        do {
            step = (step + 1) >> 1;
            int newSplit = split + step;
            if (newSplit < last) {
                long splitCode = morton[order[newSplit]];
                int splitPrefix = Long.numberOfLeadingZeros(firstCode ^ splitCode);
                if (splitPrefix > commonPrefix) split = newSplit;
            }
        } while (step > 1);
        return split;
    }

    private static long morton3D(double x, double y, double z){
        // 21 bits each -> 63 bits
        long xx = part(x); long yy = part(y); long zz = part(z);
        return (xx) | (yy<<1) | (zz<<2);
    }
    private static long part(double v){
        long x = Math.max(0, Math.min(0x1fffff, (int)(v * (double)(1<<21))));
        x = (x | (x<<32)) & 0x1f00000000ffffL;
        x = (x | (x<<16)) & 0x1f0000ff0000ffL;
        x = (x | (x<<8))  & 0x100f00f00f00f00fL;
        x = (x | (x<<4))  & 0x10c30c30c30c30c3L;
        x = (x | (x<<2))  & 0x1249249249249249L;
        return x;
    }

    public static M3 absWithEps(M3 R, double eps) {
        return new M3(Math.abs(R.m00)+eps, Math.abs(R.m01)+eps, Math.abs(R.m02)+eps,
                Math.abs(R.m10)+eps, Math.abs(R.m11)+eps, Math.abs(R.m12)+eps,
                Math.abs(R.m20)+eps, Math.abs(R.m21)+eps, Math.abs(R.m22)+eps);
    }
}