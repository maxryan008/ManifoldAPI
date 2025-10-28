package dev.manifold.physics.math;

public final class M3 {
    // Public fields for fast access (row-major)
    public double m00, m01, m02;
    public double m10, m11, m12;
    public double m20, m21, m22;

    public M3() {
        this.m00=1; this.m01=0; this.m02=0;
        this.m10=0; this.m11=1; this.m12=0;
        this.m20=0; this.m21=0; this.m22=1;
    }

    public M3(double m00,double m01,double m02,
              double m10,double m11,double m12,
              double m20,double m21,double m22) {
        this.m00=m00; this.m01=m01; this.m02=m02;
        this.m10=m10; this.m11=m11; this.m12=m12;
        this.m20=m20; this.m21=m21; this.m22=m22;
    }

    /** Set from column vectors (matches your use in ConstructManager). */
    public static M3 ofColumns(V3 x, V3 y, V3 z) {
        return new M3(
                x.x, y.x, z.x,
                x.y, y.y, z.y,
                x.z, y.z, z.z
        );
    }

    /** Backwards-compat with code calling m.set(x,y,z). */
    public void set(V3 x, V3 y, V3 z) {
        M3 t = ofColumns(x,y,z);
        this.m00=t.m00; this.m01=t.m01; this.m02=t.m02;
        this.m10=t.m10; this.m11=t.m11; this.m12=t.m12;
        this.m20=t.m20; this.m21=t.m21; this.m22=t.m22;
    }

    public static M3 identity() {
        M3 m3 = new M3();
        m3.m00=1; m3.m01=0; m3.m02=0;
        m3.m10=0; m3.m11=1; m3.m12=0;
        m3.m20=0; m3.m21=0; m3.m22=1;
        return m3;
    }

    /** Matrix * vector. */
    public V3 mul(V3 v) {
        return new V3(
                m00*v.x + m01*v.y + m02*v.z,
                m10*v.x + m11*v.y + m12*v.z,
                m20*v.x + m21*v.y + m22*v.z
        );
    }

    /** Matrix * matrix. */
    public M3 mul(M3 b) {
        return new M3(
                m00*b.m00 + m01*b.m10 + m02*b.m20,
                m00*b.m01 + m01*b.m11 + m02*b.m21,
                m00*b.m02 + m01*b.m12 + m02*b.m22,

                m10*b.m00 + m11*b.m10 + m12*b.m20,
                m10*b.m01 + m11*b.m11 + m12*b.m21,
                m10*b.m02 + m11*b.m12 + m12*b.m22,

                m20*b.m00 + m21*b.m10 + m22*b.m20,
                m20*b.m01 + m21*b.m11 + m22*b.m21,
                m20*b.m02 + m21*b.m12 + m22*b.m22
        );
    }

    public M3 transpose() {
        return new M3(
                m00, m10, m20,
                m01, m11, m21,
                m02, m12, m22
        );
    }

    /** Componentwise absolute multiply: |this| * v . */
    public V3 mulAbs(V3 v) {
        return new V3(
                Math.abs(m00)*v.x + Math.abs(m01)*v.y + Math.abs(m02)*v.z,
                Math.abs(m10)*v.x + Math.abs(m11)*v.y + Math.abs(m12)*v.z,
                Math.abs(m20)*v.x + Math.abs(m21)*v.y + Math.abs(m22)*v.z
        );
    }
}