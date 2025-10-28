package dev.manifold.physics.collision;

import dev.manifold.physics.core.OBB;
import dev.manifold.physics.core.RigidState;
import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;
import dev.manifold.physics.collision.bvh.BVH;
import dev.manifold.physics.collision.bvh.BVHBuilder;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns per-construct collision data (local OBBs, merged patches, BVH, SDF),
 * the global pair cache, manifold cache, and solver scratch.
 */
public final class ConstructCollisionManager {

    /** Per-construct data keyed by constructId. */
    private static final Map<UUID, ConstructRecord> constructs = new ConcurrentHashMap<>();

    /** Persistent broadphase cache of overlapping BVH leaves between constructs. */
    private static final PairCache pairCache = new PairCache(8192);

    /** Persistent manifolds keyed by (A,B,leafA,leafB). */
    private static final ManifoldCache manifoldCache = new ManifoldCache(16384);

    /** Global settings. */
    public static final Settings settings = new Settings();

    public static final class Settings {
        public int bvhRebuildInterval = 12;     // frames
        public int solverIterations    = 8;     // SI iterations
        public boolean useCCD          = true;  // root-level conservative advancement
        public boolean useSDFHybrid    = true;  // use SDF to cull/seed normals
        public boolean speculativeContacts = true;
        public double speculativeSlop  = 0.03;  // meters
        public double warmStartFactor  = 0.9;
        public double allowedPenetration = 0.0015;
    }

    /** Opaque per-construct record. */
    public static final class ConstructRecord {
        public final UUID id;
        public final V3 localOrigin;        // COM in local space
        public final VoxelSDF sdf;          // optional hybrid (may be null)
        public final List<OBB> localObbs;   // raw per-voxel OBBs
        public final List<OBB> mergedObbs;  // merged coplanar boxes (patch merging)
        public BVH bvh;                     // built over mergedObbs indices
        public RigidState state;            // world transform + velocities
        public int frameCounter;            // for periodic rebuilds

        // Cached transforms for this frame (low-level win)
        public M3 R;       // rotation 3x3
        public M3 AbsR;    // |R| with eps
        public V3 pos;     // world pos

        public ConstructRecord(UUID id, List<OBB> localObbs, List<OBB> mergedObbs,
                               RigidState state, V3 localOrigin, VoxelSDF sdf) {
            this.id = id;
            this.localObbs = localObbs;
            this.mergedObbs = mergedObbs;
            this.state = state;
            this.localOrigin = localOrigin;
            this.sdf = sdf;
            this.frameCounter = 0;
        }
    }

    /** Create/replace a construct’s geometry + initial state. */
    public static void upsertConstruct(UUID id,
                                       List<OBB> localObbs,
                                       RigidState rigidState,
                                       V3 localOrigin,
                                       VoxelSDF sdf) {
        List<OBB> merged = PatchMerger.mergeVoxels(localObbs);

        // Fallbacks so BVH never explodes:
        if (merged == null || merged.isEmpty()) {
            // If there are still local boxes, use them; otherwise create a tiny placeholder
            merged = (localObbs != null && !localObbs.isEmpty()) ? localObbs : List.of(
                    new OBB(new V3(0,0,0), new V3(1e-4,1e-4,1e-4), M3.identity(), 0.6, -1)
            );
        }

        BVH bvh = BVHBuilder.buildLBVH(merged);

        ConstructRecord rec = new ConstructRecord(id, localObbs, merged, rigidState, localOrigin, sdf);
        rec.bvh = bvh;

        rec.R   = rigidState.getRotation();
        rec.AbsR= BVHBuilder.absWithEps(rec.R, 1e-7);
        rec.pos = rigidState.getPosition();

        constructs.put(id, rec);
    }

    /** Update pose/vel only (called each tick from ConstructManager.tick). */
    public static void updateState(UUID id, RigidState newState) {
        ConstructRecord rec = constructs.get(id);
        if (rec == null) return;
        rec.state = newState;
        rec.R = newState.getRotation();
        rec.AbsR = BVHBuilder.absWithEps(rec.R, 1e-7);
        rec.pos = newState.getPosition();

        // Periodically rebuild LBVH if geometry is highly dynamic (optional)
        if (++rec.frameCounter % settings.bvhRebuildInterval == 0) {
            rec.bvh = BVHBuilder.buildLBVH(rec.mergedObbs);
        }
    }

    public static void remove(UUID id) {
        constructs.remove(id);
        pairCache.removeForConstruct(id);
        manifoldCache.removeForConstruct(id);
    }

    /** Query read-only view for engine. */
    static Map<UUID, ConstructRecord> world() {
        return constructs;
    }

    static PairCache pairs() {
        return pairCache;
    }

    static ManifoldCache manifolds() {
        return manifoldCache;
    }

    /** Compute a conservative AABB for a construct in world space (root BVH box transformed). */
    public static AABB worldAabb(ConstructRecord rec) {
        // Tight-ish: transform each corner of the root AABB; here we do a fast box transform using AbsR.
        // Root AABB around mergedObbs’ bounds (already in BVH)
        var root = rec.bvh.rootBounds;
        V3 e = root.extents;
        V3 c = root.center;

        // world center = p + R * (c - localOrigin)
        V3 localC = c.sub(rec.localOrigin);
        V3 wc = rec.pos.add(rec.R.mul(localC));
        // world extents = AbsR * e
        V3 we = rec.AbsR.mul(e);

        return new AABB(
                wc.x - we.x, wc.y - we.y, wc.z - we.z,
                wc.x + we.x, wc.y + we.y, wc.z + we.z
        );
    }
}