package dev.manifold.physics.collision;

import dev.manifold.physics.math.V3;

final class Speculative {
    static void addSpeculative(ConstructCollisionManager.ConstructRecord A,
                               ConstructCollisionManager.ConstructRecord B,
                               BoxBox.Result res){
        // Project relative velocity onto normal and create shallow bias if approaching
        V3 relV = B.state.getLinearVelocity().sub(A.state.getLinearVelocity());
        double vn = relV.dot(res.normal);
        if (vn < 0){
            double slop = ConstructCollisionManager.settings.speculativeSlop;
            res.depth = Math.max(res.depth, -vn * 0.016 + slop);
        }
    }
}