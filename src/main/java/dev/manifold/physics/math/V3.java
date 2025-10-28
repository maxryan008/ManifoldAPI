package dev.manifold.physics.math;

public final class V3 {
    public final double x, y, z;

    public V3(double x,double y,double z){ this.x=x; this.y=y; this.z=z; }

    public V3() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    public V3 add(V3 b){ return new V3(x+b.x, y+b.y, z+b.z); }
    public V3 add(double ax,double ay,double az){ return new V3(x+ax, y+ay, z+az); }
    public V3 sub(V3 b){ return new V3(x-b.x, y-b.y, z-b.z); }
    public V3 sub(double ax,double ay,double az){ return new V3(x-ax, y-ay, z-az); }
    public V3 scale(double s){ return new V3(x*s, y*s, z*s); }
    public V3 neg(){ return new V3(-x,-y,-z); }

    public double dot(V3 b){ return x*b.x + y*b.y + z*b.z; }
    public V3 cross(V3 b){ return new V3(y*b.z - z*b.y, z*b.x - x*b.z, x*b.y - y*b.x); }

    public V3 abs(){ return new V3(Math.abs(x), Math.abs(y), Math.abs(z)); }
    public double length(){ return Math.sqrt(x*x + y*y + z*z); }
    public V3 normalize(){ double L=length(); return (L<1e-12)? new V3(0,0,0) : new V3(x/L, y/L, z/L); }

    /** Any perpendicular (stable enough for contact basis). */
    public V3 anyPerp() {
        // Pick the smallest component to avoid degeneracy.
        return (Math.abs(x) < Math.abs(y) && Math.abs(x) < Math.abs(z))
                ? new V3(0, -z, y)
                : (Math.abs(y) < Math.abs(z) ? new V3(-z, 0, x) : new V3(-y, x, 0));
    }

    public double lengthSq(){ return x*x + y*y + z*z; }
}