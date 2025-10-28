package dev.manifold.physics.collision;

import java.util.*;

/** LRU cache of overlapping leaf pairs between constructs for temporal coherence. */
final class PairCache {
    private static final class Key {
        final UUID a,b;
        final int leafA, leafB;
        Key(UUID a, UUID b, int la, int lb){
            if (a.compareTo(b)<0){ this.a=a; this.b=b; this.leafA=la; this.leafB=lb; }
            else { this.a=b; this.b=a; this.leafA=lb; this.leafB=la; }
        }
        public boolean equals(Object o){
            if(!(o instanceof Key k)) return false;
            return a.equals(k.a) && b.equals(k.b) && leafA==k.leafA && leafB==k.leafB;
        }
        public int hashCode(){ return a.hashCode()*31*31 + b.hashCode()*31 + leafA*17 + leafB; }
    }

    private final LinkedHashMap<Key, Long> lru;
    private final int capacity;

    PairCache(int cap){
        this.capacity = cap;
        this.lru = new LinkedHashMap<>(cap, 0.75f, true){
            protected boolean removeEldestEntry(Map.Entry<Key, Long> e){
                return size()>capacity;
            }
        };
    }

    public void touch(UUID a, UUID b, int la, int lb, long frame) {
        lru.put(new Key(a,b,la,lb), frame);
    }

    public boolean contains(UUID a, UUID b, int la, int lb){
        return lru.containsKey(new Key(a,b,la,lb));
    }

    public void removeForConstruct(UUID id){
        lru.entrySet().removeIf(e -> e.getKey().a.equals(id) || e.getKey().b.equals(id));
    }
}