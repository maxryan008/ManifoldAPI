package dev.manifold.physics.collision;

import dev.manifold.physics.core.OBB;
import dev.manifold.physics.core.RigidState;
import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Holds each construct's LOCAL-space OBBs + live rigid state (pos/rot/vel/angVel).
 * We keep OBBs in LOCAL coordinates so we don't rebuild them each tick.
 * The engine will transform them to world-space on demand using the state's transform.
 */
public final class ConstructCollisionManager {

    public record ConstructRef(UUID id, List<OBB> localObbs, RigidState state, V3 localOrigin, VoxelSDF sdf) {}

    // --- Storage (replace with your construct system / capability access) ---
    private static final Map<UUID, ConstructRef> CONSTRUCTS = new HashMap<>();

    /** Register/refresh a construct, including its local-space SDF. */
    public static void upsertConstruct(UUID id, List<OBB> localObbs, RigidState initialState, V3 localOrigin, VoxelSDF sdf) {
        CONSTRUCTS.put(id, new ConstructRef(id, localObbs, initialState, localOrigin, sdf));
    }

    /** Update live state (per tick) from your ship controller. */
    public static void updateState(UUID id, RigidState newState) {
        ConstructRef ref = CONSTRUCTS.get(id);
        if (ref != null) CONSTRUCTS.put(id, new ConstructRef(id, ref.localObbs, newState, ref.localOrigin, ref.sdf));
    }

    /** Remove when destroyed / unloaded. */
    public static void remove(UUID id) {
        CONSTRUCTS.remove(id);
    }

    /**
     * Query constructs near a swept AABB (player movement bounds). You should replace this
     * naive scan with your spatial index (chunk map / BVH) for performance.
     */
    public static List<ConstructRef> queryNearby(Level level, AABB sweptBounds) {
        // TODO: replace with fast index; for now return all.
        return new ArrayList<>(CONSTRUCTS.values());
    }
}