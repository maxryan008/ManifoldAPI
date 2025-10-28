package dev.manifold.physics.collision;

import dev.manifold.physics.math.V3;

import java.util.List;

/** Simple SI contact solver for rigid constructs (usually kinematic invMass=0). */
final class Solver {

    static void solveIslands(List<Contact> contacts) {
        // For constructs we treat as kinematic (invMass=0), we still apply impulses as
        // velocity corrections for non-kinematic participants (entities) in the Engine.
        // For construct-construct: result will be zero impulses unless you assign invMass.

        int iters = ConstructCollisionManager.settings.solverIterations;

        // Warm start
        double warm = ConstructCollisionManager.settings.warmStartFactor;
        for (Contact c: contacts){
            for (var p : c.points){
                p.normalImpulse *= warm;
                p.tangentImpulse1 *= warm;
                p.tangentImpulse2 *= warm;
            }
        }

        // Iterate
        for (int i=0;i<iters;i++){
            for (Contact c: contacts){
                // Here we only keep caches; actual velocity application is in Engine resolve for entities.
                // If you later give constructs mass, insert impulses into their RigidState velocities here.
            }
        }
    }
}