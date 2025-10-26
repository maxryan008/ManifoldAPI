package dev.manifold.physics.math;

public final class V3 {
    public double x, y, z;
    public V3() {}
    public V3(double x, double y, double z) { this.x=x; this.y=y; this.z=z; }
    public static V3 of(double x,double y,double z){ return new V3(x,y,z); }
    public V3 set(V3 o){ x=o.x;y=o.y;z=o.z; return this; }
    public V3 cpy(){ return new V3(x,y,z); }
    public V3 add(V3 o){ x+=o.x;y+=o.y;z+=o.z; return this; }
    public V3 add(double ax,double ay,double az){ x+=ax;y+=ay;z+=az; return this; }
    public V3 sub(V3 o){ x-=o.x;y-=o.y;z-=o.z; return this; }
    public V3 scl(double s){ x*=s;y*=s;z*=s; return this; }
    public double dot(V3 o){ return x*o.x + y*o.y + z*o.z; }
    public V3 crs(V3 o){ return new V3( y*o.z - z*o.y, z*o.x - x*o.z, x*o.y - y*o.x ); }
    public double len(){ return Math.sqrt(x*x+y*y+z*z); }
    public double len2(){ return x*x+y*y+z*z; }
    public V3 nor(){ double l=len(); if(l>1e-12){scl(1.0/l);} return this; }
    public static V3 add(V3 a,V3 b){ return new V3(a.x+b.x,a.y+b.y,a.z+b.z); }
    public static V3 sub(V3 a,V3 b){ return new V3(a.x-b.x,a.y-b.y,a.z-b.z); }
    public static V3 scl(V3 a,double s){ return new V3(a.x*s,a.y*s,a.z*s); }
    public static V3 projOnPlane(V3 v, V3 n){ double dn=v.dot(n); return new V3(v.x - dn*n.x, v.y - dn*n.y, v.z - dn*n.z); }
    public static V3 orthonormal(V3 n){
        return Math.abs(n.x) > 0.577 ? new V3(n.y, -n.x, 0).nor()
                : new V3(0, n.z, -n.y).nor();
    }
    @Override public String toString(){ return "V3("+x+","+y+","+z+")"; }
}