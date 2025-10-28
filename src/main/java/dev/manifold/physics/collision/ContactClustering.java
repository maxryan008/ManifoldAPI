package dev.manifold.physics.collision;

import dev.manifold.physics.math.V3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reduce clusters of nearby contacts to improve stability & performance. */
final class ContactClustering {

    static ArrayList<Contact> cluster(List<Contact> in, double cell) {
        // Very simple grid clustering in contact position space per (A,B).
        record K(UUID a, UUID b, int la, int lb){}
        record BucketKey(K k, long gx, long gy, long gz){}

        var out = new ArrayList<Contact>(in.size());
        var map = new java.util.HashMap<BucketKey, Contact>();

        for (var c : in){
            for (var p : c.points){
                long gx = (long)Math.floor(p.pos.x / cell);
                long gy = (long)Math.floor(p.pos.y / cell);
                long gz = (long)Math.floor(p.pos.z / cell);

                var key = new BucketKey(new K(c.a,c.b,c.leafA,c.leafB), gx,gy,gz);
                Contact acc = map.get(key);
                if (acc==null){
                    acc = new Contact();
                    acc.a=c.a; acc.b=c.b; acc.leafA=c.leafA; acc.leafB=c.leafB;
                    acc.normal=c.normal; acc.friction=c.friction; acc.restitution=c.restitution;
                    map.put(key, acc);
                }
                acc.points.add(p);
            }
        }

        out.addAll(map.values());
        return out;
    }
}