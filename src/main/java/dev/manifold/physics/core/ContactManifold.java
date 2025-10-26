package dev.manifold.physics.core;

import java.util.ArrayList;
import java.util.List;

public final class ContactManifold {
    public final List<ContactPoint> points = new ArrayList<>(4);
    public void add(ContactPoint cp){ points.add(cp); }
    public boolean isEmpty(){ return points.isEmpty(); }
}
