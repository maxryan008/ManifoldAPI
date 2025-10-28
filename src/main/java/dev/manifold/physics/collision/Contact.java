package dev.manifold.physics.collision;

import dev.manifold.physics.math.V3;

import java.util.*;

/** Persistent contact manifold with warm-started impulses. */
public final class Contact {

    public static final class Point {
        public V3 rA, rB;          // offsets from COMs (world)
        public V3 pos;             // contact position (world)
        public double normalImpulse; // cached
        public double tangentImpulse1, tangentImpulse2;
        public double bias;        // baumgarte / restitution bias

        public Point copy() {
            Point p = new Point();
            p.rA = rA; p.rB = rB; p.pos = pos;
            p.normalImpulse = normalImpulse;
            p.tangentImpulse1 = tangentImpulse1; p.tangentImpulse2 = tangentImpulse2;
            p.bias = bias;
            return p;
        }
    }

    public UUID a,b;
    public int leafA, leafB;
    public V3 normal;          // world-space normal, from A→B
    public final ArrayList<Point> points = new ArrayList<>(4);
    public double friction;    // sqrt(muA*muB)
    public double restitution; // could be tuned per material

    public Contact keyClone(){
        Contact c = new Contact();
        c.a = a; c.b = b;
        c.leafA = leafA; c.leafB = leafB;
        c.normal = normal;
        c.friction = friction; c.restitution = restitution;
        c.points.addAll(points.stream().map(Point::copy).toList());
        return c;
    }
}

/** LRU manifold cache keyed by (A,B,leafA,leafB). */
final class ManifoldCache {
    private static final class Key {
        final UUID a,b; final int la,lb;
        Key(UUID a,UUID b,int la,int lb){
            if (a.compareTo(b)<0){ this.a=a; this.b=b; this.la=la; this.lb=lb; }
            else { this.a=b; this.b=a; this.la=lb; this.lb=la; }
        }
        public boolean equals(Object o){
            if(!(o instanceof Key k)) return false;
            return a.equals(k.a) && b.equals(k.b) && la==k.la && lb==k.lb;
        }
        public int hashCode(){ return a.hashCode()*31*31 + b.hashCode()*31 + la*17 + lb; }
    }

    private final LinkedHashMap<Key, Contact> lru;
    private final int capacity;

    ManifoldCache(int cap){
        capacity=cap;
        lru = new LinkedHashMap<>(cap,0.75f,true){
            protected boolean removeEldestEntry(Map.Entry<Key, Contact> e){ return size()>capacity; }
        };
    }

    public Contact get(UUID a, UUID b, int la, int lb){
        return lru.get(new Key(a,b,la,lb));
    }
    public void put(Contact c){
        lru.put(new Key(c.a,c.b,c.leafA,c.leafB), c);
    }
    public void removeForConstruct(UUID id){
        lru.entrySet().removeIf(e -> e.getKey().a.equals(id) || e.getKey().b.equals(id));
    }
}