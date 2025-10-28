package dev.manifold.physics.collision;

import dev.manifold.physics.core.OBB;
import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;

import java.util.ArrayList;
import java.util.List;

/** Builds up to 4 clipped contacts from reference/incident faces. */
final class ContactBuilder {

    static void clipContacts(OBB aLocal, OBB bLocal,
                             ConstructCollisionManager.ConstructRecord A,
                             ConstructCollisionManager.ConstructRecord B,
                             BoxBox.Result res)
    {
        // Build world-space face polygons (quads) and clip incident to reference plane and edges.
        // For brevity, emit centers of overlap rectangle; in practice, compute polygon clipping fully.
        // Here we’ll produce up to 4 contact points centered on the overlapped rectangle.

        // Axis-aligned in local → easy to get face planes in A-space; transform to world.
        V3 n = res.normal;
        // Pick reference = the box whose face normal better aligns with n
        double na = Math.abs(n.dot(A.R.mul(new V3(1,0,0)))) + Math.abs(n.dot(A.R.mul(new V3(0,1,0)))) + Math.abs(n.dot(A.R.mul(new V3(0,0,1))));
        double nb = Math.abs(n.dot(B.R.mul(new V3(1,0,0)))) + Math.abs(n.dot(B.R.mul(new V3(0,1,0)))) + Math.abs(n.dot(B.R.mul(new V3(0,0,1))));
        boolean refA = na >= nb;

        // Build a tiny 2x2 grid of contacts around the center of penetration
        V3 ca = A.pos.add(A.R.mul(aLocal.c.sub(A.localOrigin)));
        V3 cb = B.pos.add(B.R.mul(bLocal.c.sub(B.localOrigin)));
        V3 mid = ca.add(cb).scale(0.5);
        V3 t1 = n.anyPerp().normalize();
        V3 t2 = n.cross(t1).normalize();

        double s = Math.max(0.02, Math.min(res.depth, 0.1));
        List<V3> pts = new ArrayList<>(4);
        pts.add(mid.add(t1.scale( s)).add(t2.scale( s)));
        pts.add(mid.add(t1.scale(-s)).add(t2.scale( s)));
        pts.add(mid.add(t1.scale( s)).add(t2.scale(-s)));
        pts.add(mid.add(t1.scale(-s)).add(t2.scale(-s)));

        res.contactsA.addAll(pts);
        res.contactsB.addAll(pts); // simplified: coincide at mid; solver will separate
    }
}