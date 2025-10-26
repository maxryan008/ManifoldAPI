package dev.manifold.physics.solver;

import dev.manifold.physics.core.ContactManifold;
import dev.manifold.physics.core.ContactPoint;
import dev.manifold.physics.core.RigidState;
import dev.manifold.physics.math.V3;

import java.util.List;

/** Iterative PGS solver with stick/slip friction + tangent motor target. */
public final class ObbContactSolver {

    public int iterations = 12;
    public double baumgarte = 0.15;
    public double penetrationSlop = 0.0015; // ~1.5mm

    // Player’s desired ground-plane velocity relative to platform (from input)
    public V3 desiredTangentVelocity = new V3(); // set per tick

    public void solve(List<ContactManifold> manifolds, RigidState A, RigidState B, double dt){
        for(int it=0; it<iterations; it++){
            // normal first
            for(ContactManifold m : manifolds){
                for(ContactPoint c : m.points){
                    solveNormal(c, A, B, dt);
                }
            }
            // then friction/motor
            for(ContactManifold m : manifolds){
                for(ContactPoint c : m.points){
                    solveFrictionMotor(c, A, B, dt);
                }
            }
        }
    }

    private void solveNormal(ContactPoint c, RigidState A, RigidState B, double dt){
        V3 n = c.n;
        V3 rA = V3.sub(c.p, A.pos);
        V3 rB = V3.sub(c.p, B.pos);

        V3 vRel = V3.sub(
                V3.add(A.vLin.cpy().add(A.vAng.crs(rA)), new V3()),
                V3.add(B.vLin.cpy().add(B.vAng.crs(rB)), new V3())
        );
        double relN = vRel.dot(n);

        // Bias toward small "rest" separation using per-point penetration
        double sepErr = Math.max(0.0, c.penetration - penetrationSlop); // penetration beyond slop
        double bias = (sepErr > 0.0 ? (baumgarte/dt) * sepErr : 0.0);

        // If separating and no bias demand, skip (prevents jitter on near-miss)
        if (relN > 0.0 && bias <= 1e-12) return;

        double Kn = effectiveMassAlong(n, A, rA, B, rB);
        double delta = -(relN + bias) / (Kn + 1e-8);

        double prev = c.lambdaN;
        double lambda = Math.max(0.0, prev + delta);
        double change = lambda - prev;
        applyImpulse(A, B, rA, rB, V3.scl(n.cpy(), change));
        c.lambdaN = lambda;
    }

    private void solveFrictionMotor(ContactPoint c, RigidState A, RigidState B, double dt){
        // Tangent basis
        V3 t1 = V3.orthonormal(c.n);
        V3 t2 = c.n.crs(t1).nor();

        V3 rA = V3.sub(c.p, A.pos);
        V3 rB = V3.sub(c.p, B.pos);

        V3 vRel = V3.sub(
                V3.add(A.vLin.cpy().add(A.vAng.crs(rA)), new V3()),
                V3.add(B.vLin.cpy().add(B.vAng.crs(rB)), new V3())
        );

        V3 vT = V3.projOnPlane(vRel, c.n);
        V3 vTargetRelT = V3.projOnPlane(desiredTangentVelocity, c.n);
        V3 dv = V3.sub(vTargetRelT, vT);

        double jn = c.lambdaN;
        double mu = c.muPair; // μA * μB (per-OBB pair)
        double maxStick = mu * jn;

        // solve along t1
        double Kt1 = effectiveMassAlong(t1, A, rA, B, rB);
        double jtFree1 = dv.dot(t1) / (Kt1 + 1e-8);
        double jt1 = clamp(c.lambdaT1 + jtFree1, -maxStick, +maxStick);
        double d1 = jt1 - c.lambdaT1;
        applyImpulse(A, B, rA, rB, V3.scl(t1.cpy(), d1));
        c.lambdaT1 = jt1;

        // solve along t2
        double Kt2 = effectiveMassAlong(t2, A, rA, B, rB);
        double jtFree2 = dv.dot(t2) / (Kt2 + 1e-8);
        double jt2 = clamp(c.lambdaT2 + jtFree2, -maxStick, +maxStick);
        double d2 = jt2 - c.lambdaT2;
        applyImpulse(A, B, rA, rB, V3.scl(t2.cpy(), d2));
        c.lambdaT2 = jt2;
    }

    private static double clamp(double v,double a,double b){ return (v<b)?((v>a)?v:a):b; }

    private static double effectiveMassAlong(V3 d, RigidState A, V3 rA, RigidState B, V3 rB){
        V3 raXd = rA.crs(d);
        V3 rbXd = rB.crs(d);
        double inv =
                A.invMass + B.invMass +
                        raXd.dot(A.invInertiaWorld.mul(raXd)) +
                        rbXd.dot(B.invInertiaWorld.mul(rbXd));
        return 1.0 / (inv + 1e-12);
    }

    private static void applyImpulse(RigidState A, RigidState B, V3 rA, V3 rB, V3 J){
        A.vLin.add(V3.scl(J.cpy(), A.invMass));
        A.vAng.add(A.invInertiaWorld.mul(rA.crs(J)));
        if (B.invMass > 0.0) {
            B.vLin.sub(V3.scl(J.cpy(), B.invMass));
            B.vAng.sub(B.invInertiaWorld.mul(rB.crs(J)));
        }
    }
}