package dev.manifold.physics.collision;

import dev.manifold.DynamicConstruct;
import dev.manifold.mass.MassManager;
import dev.manifold.physics.core.OBB;
import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores per-construct local OBBs for collision.
 *
 * - OBBs are in construct-local block coordinates.
 * - World transform is applied at collision time using:
 *      world = worldPos + R * (local - COM)
 */
public final class ConstructCollisionManager {

    private static final Map<UUID, List<OBB>> LOCAL_OBBS = new ConcurrentHashMap<>();

    private ConstructCollisionManager() {}

    // Called from ConstructManager.loadFromSave/createConstruct/place/break/expandBounds/updateConstructBounds/updateConstructCOMS
    public static void rebuild(DynamicConstruct construct, ServerLevel simLevel) {
        List<OBB> obbs = buildLocalObbsForConstruct(construct, simLevel);
        LOCAL_OBBS.put(construct.getId(), obbs);
    }

    public static void remove(UUID id) {
        LOCAL_OBBS.remove(id);
    }

    /**
     * Returns the local-space OBBs for this construct.
     * These are immutable from the collider's perspective; do NOT modify the list.
     */
    public static List<OBB> getLocalObbs(UUID id) {
        List<OBB> list = LOCAL_OBBS.get(id);
        return list != null ? list : List.of();
    }

    // ------------------------------------------------------------------------
    // Internal: build local OBBs from sim dimension
    // ------------------------------------------------------------------------

    private static List<OBB> buildLocalObbsForConstruct(DynamicConstruct construct, ServerLevel simLevel) {
        List<OBB> out = new ArrayList<>();

        BlockPos simOrigin = construct.getSimOrigin();
        BlockPos neg = construct.getNegativeBounds();
        BlockPos pos = construct.getPositiveBounds();

        int id = 0;

        for (int x = neg.getX(); x <= pos.getX(); x++) {
            for (int y = neg.getY(); y <= pos.getY(); y++) {
                for (int z = neg.getZ(); z <= pos.getZ(); z++) {
                    BlockPos rel = new BlockPos(x, y, z);
                    BlockPos abs = simOrigin.offset(rel);
                    BlockState state = simLevel.getBlockState(abs);
                    if (state.isAir()) continue;

                    // Axis-aligned unit cube in local space [x,x+1] etc.
                    OBB obb = new OBB();
                    obb.c = new V3(x + 0.5, y + 0.5, z + 0.5);   // local center in construct-space
                    obb.e = new V3(0.5, 0.5, 0.5);               // half extents of block
                    obb.R = M3.identity();                        // local axes = construct local axes
                    obb.mu = estimateMu(state);                   // friction (unused by collider, but nice to have)
                    obb.id = id++;

                    out.add(obb);
                }
            }
        }

        if (out.isEmpty()) {
            // Sentinel so we never have an empty list (avoids null checks).
            OBB sentinel = new OBB();
            sentinel.c = new V3(0, 0, 0);
            sentinel.e = new V3(1e-4, 1e-4, 1e-4);
            sentinel.R = M3.identity();
            sentinel.mu = 0.6;
            sentinel.id = -1;
            out.add(sentinel);
        }

        return out;
    }

    /**
     * Rough friction estimate; you can refine this or reuse your MassManager if you like.
     */
    private static double estimateMu(BlockState state) {
        try {
            float f = state.getBlock().getFriction();
            // Map vanilla slipperiness-ish to μ in a plausible range.
            return Math.max(0.02, Math.min(1.2, 1.4 - f * 1.2));
        } catch (Throwable ignored) {
        }
        return 0.6;
    }
}