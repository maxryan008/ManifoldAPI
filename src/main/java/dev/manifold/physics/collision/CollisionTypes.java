package dev.manifold.physics.collision;

import dev.manifold.physics.math.V3;
import java.util.ArrayList;

final class CollisionTypes {
    static final class Contact {
        V3 point = new V3(0,0,0);  // world
        double depth = 0.0;
    }

    static final class Manifold {
        V3 normal = new V3(0,0,0); // world, from A to B
        double depth = 0.0;
        ArrayList<Contact> points = new ArrayList<>(4);
        double mu = 0.6;

        void addPoint(V3 p, double d){
            for (Contact c : points) {
                if (c.point.sub(p).lengthSq() < 1e-8) return;
            }
            Contact c = new Contact();
            c.point = p; c.depth = d;
            points.add(c);
            if (d > depth) depth = d;
        }
    }
}