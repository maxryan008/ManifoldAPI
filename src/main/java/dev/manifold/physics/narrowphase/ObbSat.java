package dev.manifold.physics.narrowphase;

import dev.manifold.physics.core.ContactManifold;
import dev.manifold.physics.core.ContactPoint;
import dev.manifold.physics.core.OBB;
import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;

import java.util.ArrayList;
import java.util.List;

/** OBB↔OBB SAT test + manifold building with contact/rest offsets and per-point penetration. */
public final class ObbSat {

    /** Inflate collision shapes by this much when testing for overlap (pre-contact). */
    public static double CONTACT_OFFSET = 0.0030;  // ~3 mm
    /** Solver tries to keep this separation at rest (prevents hovering & chatter). */
    public static double REST_OFFSET    = 0.002;  // ~2 mm

    public static final class OverlapResult {
        public boolean overlaps;
        /** 0=A.face, 1=B.face, 2=edge–edge */
        public int axisType;
        /** Axis indices for winning axis (ia for A, ib for B/edge) */
        public int ia, ib;
        /** Minimum-penetration normal in WORLD space, pointing A->B. */
        public V3 nWorld = new V3();
        /** Penetration depth (with CONTACT_OFFSET inflation) along the winning axis. */
        public double depth;
    }

    /** Standard OBB SAT with half-extents inflated by CONTACT_OFFSET. */
    public static OverlapResult test(OBB A, OBB B){
        final double EPS = 1e-7;

        OverlapResult out = new OverlapResult();

        // Transform B into A’s local frame
        M3 RtA = A.R.transpose();
        M3 R   = RtA.mul(B.R);
        V3 t   = RtA.mul(V3.sub(B.c, A.c)); // B center in A frame

        // Absolute rotation matrix with epsilon for stability
        double[][] absR = new double[3][3];
        double[][] Rm = {
                {R.c0.x, R.c1.x, R.c2.x},
                {R.c0.y, R.c1.y, R.c2.y},
                {R.c0.z, R.c1.z, R.c2.z}
        };
        for (int i=0;i<3;i++){
            for(int j=0;j<3;j++){
                absR[i][j] = Math.abs(Rm[i][j]) + EPS;
            }
        }

        double minPen = Double.POSITIVE_INFINITY;
        int bestType = -1, bestIA = -1, bestIB = -1;
        V3 bestN = null;

        // Precompute “row sums” for inflated projection of B on A’s axes
        // If we add δ along each local axis of B, the additional radius along A_i is δ*sum_j |A_i · B_j|
        double[] rowSumAbsR = new double[3];
        for (int i=0;i<3;i++) rowSumAbsR[i] = absR[i][0] + absR[i][1] + absR[i][2];

        // A face axes (A’s local X,Y,Z mapped to world)
        for (int i=0;i<3;i++){
            double ra = get(A.e, i) + CONTACT_OFFSET;                       // inflate A directly on its local axis
            double rb = B.e.x*absR[i][0] + B.e.y*absR[i][1] + B.e.z*absR[i][2]
                    + CONTACT_OFFSET * rowSumAbsR[i];                      // inflate B projected on A_i
            double dist = Math.abs(get(t, i));
            double pen = (ra + rb) - dist;
            if (pen < 0){ out.overlaps=false; return out; }
            if (pen < minPen){
                minPen = pen; bestType = 0; bestIA = i;
                double s = Math.signum(get(t,i));
                V3 axisWorld = A.R.getCol(i).cpy().scl(s == 0 ? 1.0 : s);
                bestN = axisWorld;
            }
        }

        // B face axes (B’s local axes mapped to world)
        // Column sums for inflated projection of A on B’s axes
        double[] colSumAbsR = new double[3];
        for (int j=0;j<3;j++) colSumAbsR[j] = absR[0][j] + absR[1][j] + absR[2][j];

        for (int j=0;j<3;j++){
            double rb = get(B.e, j) + CONTACT_OFFSET;
            double ra = A.e.x*absR[0][j] + A.e.y*absR[1][j] + A.e.z*absR[2][j]
                    + CONTACT_OFFSET * colSumAbsR[j];
            double dist = Math.abs(t.x*Rm[0][j] + t.y*Rm[1][j] + t.z*Rm[2][j]);
            double pen = (ra + rb) - dist;
            if (pen < 0){ out.overlaps=false; return out; }
            if (pen < minPen){
                minPen = pen; bestType = 1; bestIB = j;
                double s = Math.signum(t.x*Rm[0][j] + t.y*Rm[1][j] + t.z*Rm[2][j]);
                V3 axisWorld = B.R.getCol(j).cpy().scl(s == 0 ? 1.0 : s);
                bestN = axisWorld;
            }
        }

        // Edge–edge axes (cross products). For inflation we use a simple, robust approximation:
        // add CONTACT_OFFSET to both projected radii => (ra+rb) += 2*CONTACT_OFFSET.
        V3 cAB = V3.sub(B.c, A.c);
        for (int i=0;i<3;i++){
            V3 Ai = A.R.getCol(i);
            double eAi1 = get(A.e, (i+1)%3), eAi2 = get(A.e, (i+2)%3);
            for (int j=0;j<3;j++){
                V3 Bj = B.R.getCol(j);

                V3 axis = Ai.crs(Bj);
                double len2 = axis.len2();
                if (len2 < 1e-7) continue; // nearly parallel
                axis.scl(1.0/Math.sqrt(len2));

                // Projected radii on this axis (standard formulas)
                double ra = eAi1*Math.abs(A.R.getCol((i+2)%3).dot(Bj))
                        + eAi2*Math.abs(A.R.getCol((i+1)%3).dot(Bj));
                double rb = get(B.e,(j+1)%3)*Math.abs(B.R.getCol((j+2)%3).dot(Ai))
                        + get(B.e,(j+2)%3)*Math.abs(B.R.getCol((j+1)%3).dot(Ai));

                // Inflate (approx): each box contributes +CONTACT_OFFSET
                ra += CONTACT_OFFSET;
                rb += CONTACT_OFFSET;

                double dist = Math.abs(cAB.dot(axis));
                double pen = (ra + rb) - dist;
                if (pen < 0){ out.overlaps=false; return out; }
                if (pen < minPen){
                    minPen = pen; bestType = 2; bestIA = i; bestIB = j;
                    double s = Math.signum(cAB.dot(axis));
                    bestN = axis.scl(s == 0 ? 1.0 : s);
                }
            }
        }

        out.overlaps = true;
        out.axisType = bestType;
        out.ia = bestIA;
        out.ib = bestIB;
        out.depth = minPen;
        out.nWorld.set(bestN.nor());
        return out;
    }

    private static double get(V3 v, int i){ return (i==0)?v.x:(i==1)?v.y:v.z; }

    /** Build a small manifold (1–4 pts). Face–face via clipped incident quad; edge–edge gives 1 point. */
    public static ContactManifold buildManifold(OBB A, OBB B, OverlapResult r, int bObbIndex, double muPair){
        ContactManifold m = new ContactManifold();
        if (!r.overlaps) return m;

        if (r.axisType == 0 || r.axisType == 1) {
            // Reference = face that won; Incident = the other
            final boolean refIsA = (r.axisType == 0);
            final OBB Ref = refIsA ? A : B;
            final OBB Inc = refIsA ? B : A;

            // Winning face index from SAT (do NOT recompute)
            final int k = refIsA ? r.ia : r.ib;

            // For per-point penetration we want a normal that points Ref -> Inc
            // r.nWorld is A->B. If Ref==A, keep; if Ref==B, flip.
            final V3 nRefToInc = refIsA ? r.nWorld.cpy() : r.nWorld.cpy().scl(-1);

            // Build incident quad (face most anti-parallel to nRefToInc), then clip to Ref's face rectangle
            final List<V3> quad    = incidentFaceQuad(Inc, nRefToInc.cpy().scl(-1));
            final List<V3> clipped = clipAgainstReferenceFace(Ref, k, quad);

            // Emit contacts against the *rest* plane. Penetration must be > 0 to count.
            for (int i = 0; i < clipped.size() && i < 4; i++) {
                V3 q = clipped.get(i);
                double pen = penetrationToRestPlane(Ref, k, q, nRefToInc); // correct sign
                if (pen <= 1e-9) continue;

                ContactPoint cp = new ContactPoint();
                cp.p.set(q);
                cp.n.set(r.nWorld);          // solver expects A->B normal
                cp.penetration = pen;        // > 0 only when actually intruding
                cp.bObbIndex = bObbIndex;
                cp.muPair = muPair;
                m.add(cp);
            }

            if (m.points.isEmpty()) {
                // Fallback: put a single point on the rest plane at face center
                ContactPoint cp = new ContactPoint();
                V3 fc = faceCenterOnPositiveSide(Ref, k, REST_OFFSET);
                cp.p.set(fc);
                cp.n.set(r.nWorld);
                cp.penetration = Math.max(0.0, r.depth); // still fine as a fallback
                cp.bObbIndex = bObbIndex;
                cp.muPair = muPair;
                m.add(cp);
            }
        } else {
            // Edge–edge: one point (midpoint of centers), depth from SAT
            ContactPoint cp = new ContactPoint();
            cp.p.set( V3.add(A.c, B.c).scl(0.5) );
            cp.n.set(r.nWorld);
            cp.penetration = Math.max(0.0, r.depth);
            cp.bObbIndex = bObbIndex;
            cp.muPair = muPair;
            m.add(cp);
        }
        return m;
    }

    // =========================
    // ===== Helper methods ====
    // =========================

    /** Distance along nWorld from q to the *rest* plane of Ref’s positive-k face.
     *  Positive when q is *past* (intruding beyond) the rest plane along nWorld. */
    private static double penetrationToRestPlane(OBB Ref, int k, V3 q, V3 nWorld){
        V3 fc = faceCenterOnPositiveSide(Ref, k, REST_OFFSET); // shift the plane inward by REST_OFFSET
        return V3.sub(q, fc).dot(nWorld);                      // <-- correct sign
    }

    /** Reference face center on its positive-k side, shifted inward by restOffset. */
    private static V3 faceCenterOnPositiveSide(OBB Ref, int k, double restOffset){
        V3 axis = Ref.R.getCol(k);
        double ek = get(Ref.e, k) - restOffset;
        return V3.add(Ref.c, V3.scl(axis.cpy(), ek));
    }

    /** Build incident face quad (4 corners) most anti-parallel to towardNormal. */
    private static List<V3> incidentFaceQuad(OBB box, V3 towardNormal){
        // Pick face whose normal is most anti-parallel to towardNormal
        double best = -1e9; int bestIdx = 0;
        for(int i=0;i<3;i++){
            double s = -Math.abs(box.R.getCol(i).dot(towardNormal));
            if (s > best) { best = s; bestIdx = i; }
        }
        // Construct two orthonormal in-plane directions
        V3 axis = box.R.getCol(bestIdx);
        V3 u = V3.orthonormal(axis);
        V3 v = axis.crs(u).nor();
        double ex = get(box.e, (bestIdx+1)%3);
        double ey = get(box.e, (bestIdx+2)%3);
        V3 c  = V3.add(box.c, V3.scl(axis.cpy(), get(box.e, bestIdx)));

        ArrayList<V3> quad = new ArrayList<>(4);
        quad.add( V3.add(c, V3.add(V3.scl(u.cpy(),  ex), V3.scl(v.cpy(),  ey))) );
        quad.add( V3.add(c, V3.add(V3.scl(u.cpy(), -ex), V3.scl(v.cpy(),  ey))) );
        quad.add( V3.add(c, V3.add(V3.scl(u.cpy(), -ex), V3.scl(v.cpy(), -ey))) );
        quad.add( V3.add(c, V3.add(V3.scl(u.cpy(),  ex), V3.scl(v.cpy(), -ey))) );
        return quad;
    }

    /** Clip world-space polygon against the 4 side planes of Ref’s positive-k face. */
    private static List<V3> clipAgainstReferenceFace(OBB ref, int k, List<V3> poly){
        V3 axis = ref.R.getCol(k);
        V3 u = V3.orthonormal(axis);
        V3 v = axis.crs(u).nor();

        double ex = get(ref.e, (k+1)%3);
        double ey = get(ref.e, (k+2)%3);

        // Face center on positive side (no rest here; clipping bounds are the actual rectangle)
        V3 fc = V3.add(ref.c, V3.scl(axis.cpy(), get(ref.e, k)));

        // Side planes: (p - (fc ± u*ex)) · (±u) <= 0 ; same for v
        poly = clip(poly,  u, V3.add(fc, V3.scl(u.cpy(),  ex)));
        poly = clip(poly, V3.scl(u.cpy(), -1), V3.add(fc, V3.scl(u.cpy(), -ex)));
        poly = clip(poly,  v, V3.add(fc, V3.scl(v.cpy(),  ey)));
        poly = clip(poly, V3.scl(v.cpy(), -1), V3.add(fc, V3.scl(v.cpy(), -ey)));
        return poly;
    }

    private static List<V3> clip(List<V3> poly, V3 n, V3 p0){
        ArrayList<V3> out = new ArrayList<>();
        int npts = poly.size();
        if(npts==0) return out;

        V3 prev = poly.get(npts-1);
        double dpPrev = dot(prev, n) - dot(p0, n);

        for(int i=0;i<npts;i++){
            V3 curr = poly.get(i);
            double dpCurr = dot(curr, n) - dot(p0, n);

            boolean inC = dpCurr <= 1e-8;
            boolean inP = dpPrev <= 1e-8;

            if (inP && inC){
                out.add(curr);
            } else if (inP && !inC){
                V3 s = segmentPlane(prev, curr, n, p0);
                if (s != null) out.add(s);
            } else if (!inP && inC){
                V3 s = segmentPlane(prev, curr, n, p0);
                if (s != null) out.add(s);
                out.add(curr);
            }
            prev = curr; dpPrev = dpCurr;
        }
        return out;
    }

    private static V3 segmentPlane(V3 a, V3 b, V3 n, V3 p0){
        V3 ab = V3.sub(b, a);
        double denom = ab.dot(n);
        if (Math.abs(denom) < 1e-10) return null;
        double t = (dot(p0, n) - dot(a, n)) / denom;
        t = Math.max(0.0, Math.min(1.0, t));
        return new V3(a.x + ab.x*t, a.y + ab.y*t, a.z + ab.z*t);
    }

    private static double dot(V3 p, V3 q){ return p.x*q.x + p.y*q.y + p.z*q.z; }
}