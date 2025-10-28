package dev.manifold.physics.collision;

import dev.manifold.physics.core.OBB;
import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;

import java.util.ArrayList;
import java.util.List;

/** SAT for OBB vs OBB with face-clipping manifold & optional SDF normal seeding. */
public final class BoxBox {

    public static final class Result {
        public boolean overlap;
        public V3 normal;    // world, from A->B
        public double depth; // penetration depth along normal
        public List<V3> contactsA = new ArrayList<>();
        public List<V3> contactsB = new ArrayList<>();
    }

    public static Result collide(OBB aLocal, OBB bLocal,
                                 ConstructCollisionManager.ConstructRecord A,
                                 ConstructCollisionManager.ConstructRecord B) {

        // Build world transforms: x_world = p + R * (x_local - localOrigin)
        V3 ca = A.pos.add(A.R.mul(aLocal.c.sub(A.localOrigin)));
        V3 cb = B.pos.add(B.R.mul(bLocal.c.sub(B.localOrigin)));
        M3 Ra = A.R.mul(aLocal.R); // but aLocal.R == I for our merged boxes
        M3 Rb = B.R.mul(bLocal.R);

        // Relative transform T from A to B: R = Ra^T * Rb; t = Ra^T * (cb - ca)
        M3 RaT = Ra.transpose();
        M3 R = RaT.mul(Rb);
        V3 t = RaT.mul(cb.sub(ca));

        // Precompute AbsR with epsilon
        double eps = 1e-7;
        M3 AbsR = new M3(Math.abs(R.m00)+eps, Math.abs(R.m01)+eps, Math.abs(R.m02)+eps,
                Math.abs(R.m10)+eps, Math.abs(R.m11)+eps, Math.abs(R.m12)+eps,
                Math.abs(R.m20)+eps, Math.abs(R.m21)+eps, Math.abs(R.m22)+eps);

        V3 Ae = aLocal.e; V3 Be = bLocal.e;

        double minPen = Double.POSITIVE_INFINITY;
        int bestAxis = -1;  // 0..2 = A's axes, 3..5 = B's axes in A-space, 6..14 = edge-edge

        // Axes L = A0,A1,A2
        double r, ra, rb, dist;
        // A0
        ra = Ae.x; rb = Be.x*AbsR.m00 + Be.y*AbsR.m01 + Be.z*AbsR.m02;
        dist = Math.abs(t.x); r = ra + rb;
        if (dist>r) return sep();
        minPen = r - dist; bestAxis = 0;

        // A1
        ra = Ae.y; rb = Be.x*AbsR.m10 + Be.y*AbsR.m11 + Be.z*AbsR.m12;
        dist = Math.abs(t.y); r = ra + rb;
        if (dist>r) return sep();
        if (r-dist < minPen){ minPen = r-dist; bestAxis = 1; }

        // A2
        ra = Ae.z; rb = Be.x*AbsR.m20 + Be.y*AbsR.m21 + Be.z*AbsR.m22;
        dist = Math.abs(t.z); r = ra + rb;
        if (dist>r) return sep();
        if (r-dist < minPen){ minPen = r-dist; bestAxis = 2; }

        // Axes L = B0,B1,B2
        // compute t' = R^T * t
        V3 tB = new V3(R.m00*t.x + R.m10*t.y + R.m20*t.z,
                R.m01*t.x + R.m11*t.y + R.m21*t.z,
                R.m02*t.x + R.m12*t.y + R.m22*t.z);

        // B0
        ra = Ae.x*AbsR.m00 + Ae.y*AbsR.m10 + Ae.z*AbsR.m20; rb = Be.x;
        dist = Math.abs(tB.x); r = ra + rb;
        if (dist>r) return sep();
        if (r-dist < minPen){ minPen = r-dist; bestAxis = 3; }

        // B1
        ra = Ae.x*AbsR.m01 + Ae.y*AbsR.m11 + Ae.z*AbsR.m21; rb = Be.y;
        dist = Math.abs(tB.y); r = ra + rb;
        if (dist>r) return sep();
        if (r-dist < minPen){ minPen = r-dist; bestAxis = 4; }

        // B2
        ra = Ae.x*AbsR.m02 + Ae.y*AbsR.m12 + Ae.z*AbsR.m22; rb = Be.z;
        dist = Math.abs(tB.z); r = ra + rb;
        if (dist>r) return sep();
        if (r-dist < minPen){ minPen = r-dist; bestAxis = 5; }

        // Edge-edge axes (A_i x B_j)
        // For voxel/merged boxes with R=I in local, this is rarely the winner; still do for completeness.
        int[][] pairs = {{0,0},{0,1},{0,2},{1,0},{1,1},{1,2},{2,0},{2,1},{2,2}};
        for (int i=0;i<9;i++){
            int ai = pairs[i][0], bj = pairs[i][1];

            // axis = A_ai × B_bj in A-space => vector components are rows/columns of R
            V3 axis = crossAxb(ai, bj, R);
            double len2 = axis.x*axis.x + axis.y*axis.y + axis.z*axis.z;
            if (len2 < 1e-12) continue; // nearly parallel
            double invLen = 1.0/Math.sqrt(len2);
            V3 L = axis.scale(invLen);

            ra = Math.abs(L.x)*Ae.x + Math.abs(L.y)*Ae.y + Math.abs(L.z)*Ae.z;

            // project B extents: |(R^T L)| ⋅ Be
            V3 Lb = new V3(
                    R.m00*L.x + R.m10*L.y + R.m20*L.z,
                    R.m01*L.x + R.m11*L.y + R.m21*L.z,
                    R.m02*L.x + R.m12*L.y + R.m22*L.z
            );
            rb = Math.abs(Lb.x)*Be.x + Math.abs(Lb.y)*Be.y + Math.abs(Lb.z)*Be.z;

            dist = Math.abs(dot(L, t));
            r = ra + rb;
            if (dist>r) return sep();
            if (r - dist < minPen) { minPen = r - dist; bestAxis = 6 + i; }
        }

        // Build manifold: pick reference/incident faces and clip
        Result res = new Result(); res.overlap = true;

        // Normal in WORLD space (A->B)
        V3 nA;
        if (bestAxis <= 2) {
            nA = axisOfA(bestAxis);
            if (dot(nA, t) > 0) nA = nA.neg();
            res.normal = A.R.mul(nA); // world
        } else if (bestAxis <= 5) {
            int bi = bestAxis - 3;
            V3 nBinA = colOf(R, bi); // B axis in A-space
            if (dot(nBinA, t) < 0) nBinA = nBinA.neg();
            res.normal = A.R.mul(nBinA); // world
        } else {
            // Edge-edge: use SDF hybrid normal if available, else A0
            V3 guess = A.R.mul(new V3(1,0,0));
            if (A.sdf!=null && ConstructCollisionManager.settings.useSDFHybrid) {
                // Sample at mid of centers
                V3 mid = ca.add(cb).scale(0.5);
                V3 n = Hybrid.normalFromSDF(A, B, mid, guess);
                if (n != null) guess = n;
            }
            res.normal = guess;
        }
        res.depth = minPen;

        // Clip face polygons to get up to 4 contact points (in world)
        ContactBuilder.clipContacts(aLocal, bLocal, A, B, res);

        // If speculative enabled, push shallow prospective points
        if (ConstructCollisionManager.settings.speculativeContacts) {
            Speculative.addSpeculative(A, B, res);
        }

        return res;
    }

    private static Result sep(){ Result r = new Result(); r.overlap=false; return r; }

    private static V3 axisOfA(int idx){
        return switch (idx) { case 0 -> new V3(1,0,0); case 1 -> new V3(0,1,0); default -> new V3(0,0,1); };
    }
    private static V3 colOf(M3 R, int col){
        return switch (col) {
            case 0 -> new V3(R.m00, R.m10, R.m20);
            case 1 -> new V3(R.m01, R.m11, R.m21);
            default -> new V3(R.m02, R.m12, R.m22);
        };
    }
    private static V3 crossAxb(int ai, int bj, M3 R){
        // A_ai in A-space is unit axis; B_bj in A-space is column of R
        V3 Aaxis = axisOfA(ai);
        V3 Baxis = colOf(R, bj);
        return Aaxis.cross(Baxis);
    }
    private static double dot(V3 a, V3 b){ return a.x*b.x + a.y*b.y + a.z*b.z; }
}