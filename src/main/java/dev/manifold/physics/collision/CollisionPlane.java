package dev.manifold.physics.collision;

import java.util.ArrayList;
import java.util.List;

/**
 * A 2D representation of exposed faces on one of the 6 axis planes.
 * All coordinates are in construct-local block units.
 *
 * For a given plane, (u, v) are the in-plane axes and "depth" is the fixed
 * coordinate along the plane normal where the rectangle lives:
 *
 *  UP   : u = +X, v = +Z, depth = Y+1   (top face of a block)
 *  DOWN : u = +X, v = +Z, depth = Y     (bottom face)
 *  NORTH: u = +X, v = +Y, depth = Z     (north/−Z face)
 *  SOUTH: u = +X, v = +Y, depth = Z+1   (south/+Z face)
 *  WEST : u = +Z, v = +Y, depth = X     (west/−X face)
 *  EAST : u = +Z, v = +Y, depth = X+1   (east/+X face)
 */
public final class CollisionPlane {

    public enum Face {
        NORTH, EAST, SOUTH, WEST, UP, DOWN
    }

    public static final class Rect {
        public final int u0, v0, u1, v1; // half-open: [u0,u1) x [v0,v1)
        public final int depth;          // constant coord along the plane normal
        public final float friction;     // merged area friction tag

        public Rect(int u0, int v0, int u1, int v1, int depth, float friction) {
            this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
            this.depth = depth;
            this.friction = friction;
        }

        @Override public String toString() {
            return "Rect{u0=" + u0 + ", v0=" + v0 + ", u1=" + u1 + ", v1=" + v1 +
                    ", depth=" + depth + ", mu=" + friction + "}";
        }
    }

    private final Face face;
    final List<Rect> rects = new ArrayList<>();

    public CollisionPlane(Face face) {
        this.face = face;
    }

    public Face face() { return face; }

    public List<Rect> rects() { return rects; }

    public void add(Rect r) { rects.add(r); }

    public void clear() { rects.clear(); }
}