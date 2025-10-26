package dev.manifold.physics.math;

public final class M3 {
    // Columns are basis vectors (world axes of the OBB’s local x,y,z)
    public V3 c0 = new V3(1,0,0);
    public V3 c1 = new V3(0,1,0);
    public V3 c2 = new V3(0,0,1);

    public M3 set(V3 x,V3 y,V3 z){ c0.set(x); c1.set(y); c2.set(z); return this; }
    public V3 getCol(int i){ return (i==0)?c0:(i==1)?c1:c2; }

    public V3 mul(V3 v){ // M * v
        return new V3(c0.x*v.x + c1.x*v.y + c2.x*v.z,
                c0.y*v.x + c1.y*v.y + c2.y*v.z,
                c0.z*v.x + c1.z*v.y + c2.z*v.z);
    }

    public M3 transpose(){
        M3 m = new M3();
        m.c0.x=c0.x; m.c0.y=c1.x; m.c0.z=c2.x;
        m.c1.x=c0.y; m.c1.y=c1.y; m.c1.z=c2.y;
        m.c2.x=c0.z; m.c2.y=c1.z; m.c2.z=c2.z;
        return m;
    }

    public M3 mul(M3 b){ // this * b
        M3 t = this.transpose();
        M3 r = new M3();
        // r columns = this * b.columns
        r.c0 = this.mul(b.c0);
        r.c1 = this.mul(b.c1);
        r.c2 = this.mul(b.c2);
        return r;
    }

    public static M3 identity(){ return new M3(); }
}