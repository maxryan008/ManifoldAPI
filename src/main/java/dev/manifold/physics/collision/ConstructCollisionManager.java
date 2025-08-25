package dev.manifold.physics.collision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds six greedy-meshed planes using each block's VoxelShape.
 * All coordinates are in sub-voxels (1 unit = 1/16 block).
 *
 * Coordinate conventions per plane:
 *  - UP/DOWN:    u = X*16.., v = Z*16.., depth = Y*16 (+0..16 inside the block)
 *  - NORTH/SOUTH: u = X*16.., v = Y*16.., depth = Z*16 (+0..16)
 *  - WEST/EAST:   u = Z*16.., v = Y*16.., depth = X*16 (+0..16)
 */
public final class ConstructCollisionManager {

    public static final int GRID = 16;           // sub-voxels per block per axis
    private static final double EPS = 1e-7;      // geometric epsilon for overlaps

    public record Planes(
            CollisionPlane up,
            CollisionPlane down,
            CollisionPlane north,
            CollisionPlane south,
            CollisionPlane west,
            CollisionPlane east
    ) {
        public List<CollisionPlane> all() { return List.of(up, down, north, south, west, east); }

        public CollisionPlane faces(Direction d) {
            return switch (d) {
                case DOWN -> down;
                case UP -> up;
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                case EAST -> east;
            };
        }

        public List<CollisionPlane.Rect> rects(Direction d) {
            return switch (d) {
                case DOWN -> down.rects;
                case UP -> up.rects;
                case NORTH -> north.rects;
                case SOUTH -> south.rects;
                case WEST -> west.rects;
                case EAST -> east.rects;
            };
        }
    }

    private static final Map<UUID, Planes> BY_ID = new ConcurrentHashMap<>();
    private ConstructCollisionManager() {}

    public static void clear(UUID id) { BY_ID.remove(id); }
    public static Optional<Planes> get(UUID id) { return Optional.ofNullable(BY_ID.get(id)); }

    /**
     * Rebuild all 6 planes from the block region [min..max] around origin (block-aligned).
     * Emits rectangles in 1/16th units.
     */
    public static void rebuild(UUID id, Level level, BlockPos origin, BlockPos min, BlockPos max) {
        final CollisionPlane up    = new CollisionPlane(CollisionPlane.Face.UP);
        final CollisionPlane down  = new CollisionPlane(CollisionPlane.Face.DOWN);
        final CollisionPlane north = new CollisionPlane(CollisionPlane.Face.NORTH);
        final CollisionPlane south = new CollisionPlane(CollisionPlane.Face.SOUTH);
        final CollisionPlane west  = new CollisionPlane(CollisionPlane.Face.WEST);
        final CollisionPlane east  = new CollisionPlane(CollisionPlane.Face.EAST);

        meshHorizontal(level, origin, min, max, true,  up);
        meshHorizontal(level, origin, min, max, false, down);
        meshZ(level, origin, min, max, false, north);
        meshZ(level, origin, min, max, true,  south);
        meshX(level, origin, min, max, false, west);
        meshX(level, origin, min, max, true,  east);

        BY_ID.put(id, new Planes(up, down, north, south, west, east));
    }

    /* ---------------------------- Meshing per plane ---------------------------- */

    private static void meshHorizontal(Level level, BlockPos origin, BlockPos min, BlockPos max, boolean up, CollisionPlane out) {
        // plane grid (u, v) spans X and Z in sub-voxels
        final int u0 = min.getX() * GRID, u1 = (max.getX() + 1) * GRID;
        final int v0 = min.getZ() * GRID, v1 = (max.getZ() + 1) * GRID;
        final int U = u1 - u0, V = v1 - v0;

        // We'll process each Y slice independently, building a boolean mask of exposed cells at depth.
        for (int y = min.getY(); y <= max.getY(); y++) {
            // depth (in sub-voxels) for this horizontal plane inside the block
            // UP  : faces are at y + localMaxY
            // DOWN: faces are at y + localMinY
            // We'll rasterize coverage for all blocks on this Y.
            Raster ras = new Raster(U, V);
            int depthSentinel = Integer.MIN_VALUE; // we'll push rects at their exact depth later

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;

                    // neighbor above/below for occlusion
                    BlockPos npos = origin.offset(x, y + (up ? 1 : -1), z);
                    VoxelShape neigh = level.getBlockState(npos).getCollisionShape(level, npos);

                    // For each AABB in the shape
                    for (AABB aabb : shape.toAabbs()) {
                        // sub-voxel local bounds
                        int lx0 = floorSV(aabb.minX), lx1 = ceilSV(aabb.maxX);
                        int lz0 = floorSV(aabb.minZ), lz1 = ceilSV(aabb.maxZ);
                        int ly = (up ? ceilSV(aabb.maxY) : floorSV(aabb.minY));

                        // World sub-voxel coords in plane space
                        int uBeg = x * GRID + lx0 - u0, uEnd = x * GRID + lx1 - u0;
                        int vBeg = z * GRID + lz0 - v0, vEnd = z * GRID + lz1 - v0;
                        int depth = y * GRID + ly;

                        // Occlusion: mark coverage from neighbor that touches this plane,
                        // then we add our coverage and subtract occluded cells.
                        ras.ensureDepth(depth);

                        // Rasterize our face coverage first into a temp mask
                        ras.fillTemp(uBeg, vBeg, uEnd, vEnd);

                        // Cull cells where neighbor occupies the touching opposite face
                        if (!neigh.isEmpty()) {
                            for (AABB nb : neigh.toAabbs()) {
                                // neighbor touching if its minY (when up) or maxY (when down) equals our plane depth
                                int nLy = (up ? floorSV(nb.minY) : ceilSV(nb.maxY));
                                int nDepth = (y + (up ? 1 : -1)) * GRID + nLy;
                                if (nDepth != depth) continue;

                                int nu0 = x * GRID + floorSV(nb.minX) - u0;
                                int nu1 = x * GRID + ceilSV(nb.maxX) - u0;
                                int nv0 = z * GRID + floorSV(nb.minZ) - v0;
                                int nv1 = z * GRID + ceilSV(nb.maxZ) - v0;
                                ras.cullTemp(nu0, nv0, nu1, nv1);
                            }
                        }

                        ras.commitTemp(depth);
                        depthSentinel = depth; // any valid depth this slice used
                    }
                }
            }

            // Greedy mesh for every depth we touched this y-slice
            ras.greedy((du0, dv0, du1, dv1, d) -> {
                // convert back to sub-voxel world coords
                int U0 = u0 + du0, V0 = v0 + dv0, U1 = u0 + du1, V1 = v0 + dv1;
                out.add(new CollisionPlane.Rect(U0, V0, U1, V1, d, frictionDefault())); // friction per-block is optional here
            });
        }
    }

    private static void meshZ(Level level, BlockPos origin, BlockPos min, BlockPos max, boolean south, CollisionPlane out) {
        // plane grid (u, v) spans X and Y in sub-voxels; depth is Z in sub-voxels
        final int u0 = min.getX() * GRID, u1 = (max.getX() + 1) * GRID;
        final int v0 = min.getY() * GRID, v1 = (max.getY() + 1) * GRID;
        final int U = u1 - u0, V = v1 - v0;

        for (int z = min.getZ(); z <= max.getZ(); z++) {
            Raster ras = new Raster(U, V);

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;

                    BlockPos npos = origin.offset(x, y, z + (south ? 1 : -1));
                    VoxelShape neigh = level.getBlockState(npos).getCollisionShape(level, npos);

                    for (AABB aabb : shape.toAabbs()) {
                        int lx0 = floorSV(aabb.minX), lx1 = ceilSV(aabb.maxX);
                        int ly0 = floorSV(aabb.minY), ly1 = ceilSV(aabb.maxY);
                        int lz = (south ? ceilSV(aabb.maxZ) : floorSV(aabb.minZ));

                        int uBeg = x * GRID + lx0 - u0, uEnd = x * GRID + lx1 - u0;
                        int vBeg = y * GRID + ly0 - v0, vEnd = y * GRID + ly1 - v0;
                        int depth = z * GRID + lz;

                        ras.ensureDepth(depth);
                        ras.fillTemp(uBeg, vBeg, uEnd, vEnd);

                        if (!neigh.isEmpty()) {
                            for (AABB nb : neigh.toAabbs()) {
                                int nLz = (south ? floorSV(nb.minZ) : ceilSV(nb.maxZ));
                                int nDepth = (z + (south ? 1 : -1)) * GRID + nLz;
                                if (nDepth != depth) continue;

                                int nu0 = x * GRID + floorSV(nb.minX) - u0;
                                int nu1 = x * GRID + ceilSV(nb.maxX) - u0;
                                int nv0 = y * GRID + floorSV(nb.minY) - v0;
                                int nv1 = y * GRID + ceilSV(nb.maxY) - v0;
                                ras.cullTemp(nu0, nv0, nu1, nv1);
                            }
                        }

                        ras.commitTemp(depth);
                    }
                }
            }

            ras.greedy((du0, dv0, du1, dv1, d) -> {
                int U0 = u0 + du0, V0 = v0 + dv0, U1 = u0 + du1, V1 = v0 + dv1;
                out.add(new CollisionPlane.Rect(U0, V0, U1, V1, d, frictionDefault()));
            });
        }
    }

    private static void meshX(Level level, BlockPos origin, BlockPos min, BlockPos max, boolean east, CollisionPlane out) {
        // plane grid (u, v) spans Z and Y in sub-voxels; depth is X in sub-voxels
        final int u0 = min.getZ() * GRID, u1 = (max.getZ() + 1) * GRID;
        final int v0 = min.getY() * GRID, v1 = (max.getY() + 1) * GRID;
        final int U = u1 - u0, V = v1 - v0;

        for (int x = min.getX(); x <= max.getX(); x++) {
            Raster ras = new Raster(U, V);

            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;

                    BlockPos npos = origin.offset(x + (east ? 1 : -1), y, z);
                    VoxelShape neigh = level.getBlockState(npos).getCollisionShape(level, npos);

                    for (AABB aabb : shape.toAabbs()) {
                        int lz0 = floorSV(aabb.minZ), lz1 = ceilSV(aabb.maxZ);
                        int ly0 = floorSV(aabb.minY), ly1 = ceilSV(aabb.maxY);
                        int lx = (east ? ceilSV(aabb.maxX) : floorSV(aabb.minX));

                        int uBeg = z * GRID + lz0 - u0, uEnd = z * GRID + lz1 - u0;
                        int vBeg = y * GRID + ly0 - v0, vEnd = y * GRID + ly1 - v0;
                        int depth = x * GRID + lx;

                        ras.ensureDepth(depth);
                        ras.fillTemp(uBeg, vBeg, uEnd, vEnd);

                        if (!neigh.isEmpty()) {
                            for (AABB nb : neigh.toAabbs()) {
                                int nLx = (east ? floorSV(nb.minX) : ceilSV(nb.maxX));
                                int nDepth = (x + (east ? 1 : -1)) * GRID + nLx;
                                if (nDepth != depth) continue;

                                int nu0 = z * GRID + floorSV(nb.minZ) - u0;
                                int nu1 = z * GRID + ceilSV(nb.maxZ) - u0;
                                int nv0 = y * GRID + floorSV(nb.minY) - v0;
                                int nv1 = y * GRID + ceilSV(nb.maxY) - v0;
                                ras.cullTemp(nu0, nv0, nu1, nv1);
                            }
                        }

                        ras.commitTemp(depth);
                    }
                }
            }

            ras.greedy((du0, dv0, du1, dv1, d) -> {
                int U0 = u0 + du0, V0 = v0 + dv0, U1 = u0 + du1, V1 = v0 + dv1;
                out.add(new CollisionPlane.Rect(U0, V0, U1, V1, d, frictionDefault()));
            });
        }
    }

    /* ---------------------------- Helpers ---------------------------- */

    private static int floorSV(double v) {             // world->subvoxel floor
        return (int)Math.floor(v * GRID + EPS);
    }
    private static int ceilSV(double v) {              // world->subvoxel ceil
        return (int)Math.ceil(v * GRID - EPS);
    }

    private static float frictionDefault() { return 0.6f; }

    /**
     * Light-weight raster collector for many depths per slice.
     * We build per-depth boolean masks by using a temp bitmap + commit.
     */
    private static final class Raster {
        final int U, V;
        // depth -> mask
        final Map<Integer, boolean[]> layers = new HashMap<>();
        boolean[] temp; // temp coverage for current rect
        boolean[] tempCull; // temp cull mask (reuse array)
        int tempU0, tempV0, tempU1, tempV1;

        Raster(int U, int V) {
            this.U = U; this.V = V;
            this.temp = new boolean[U * V];
            this.tempCull = new boolean[U * V];
        }

        void ensureDepth(int depth) {
            layers.computeIfAbsent(depth, d -> new boolean[U * V]);
        }

        void fillTemp(int u0, int v0, int u1, int v1) {
            u0 = clamp(u0, 0, U); u1 = clamp(u1, 0, U);
            v0 = clamp(v0, 0, V); v1 = clamp(v1, 0, V);
            tempU0 = u0; tempV0 = v0; tempU1 = u1; tempV1 = v1;
            Arrays.fill(temp, false);
            Arrays.fill(tempCull, false);
            for (int v = v0; v < v1; v++) {
                int row = v * U;
                for (int u = u0; u < u1; u++) temp[row + u] = true;
            }
        }

        void cullTemp(int u0, int v0, int u1, int v1) {
            u0 = clamp(u0, 0, U); u1 = clamp(u1, 0, U);
            v0 = clamp(v0, 0, V); v1 = clamp(v1, 0, V);
            for (int v = v0; v < v1; v++) {
                int row = v * U;
                for (int u = u0; u < u1; u++) {
                    tempCull[row + u] = true;
                }
            }
        }

        void commitTemp(int depth) {
            boolean[] layer = layers.get(depth);
            for (int v = tempV0; v < tempV1; v++) {
                int row = v * U;
                for (int u = tempU0; u < tempU1; u++) {
                    int idx = row + u;
                    if (temp[idx] && !tempCull[idx]) layer[idx] = true;
                }
            }
        }

        interface Emit {
            void rect(int u0, int v0, int u1, int v1, int depth);
        }

        void greedy(Emit emit) {
            // Greedy mesh each depth independently
            boolean[] used = new boolean[U * V];
            for (Map.Entry<Integer, boolean[]> e : layers.entrySet()) {
                int depth = e.getKey();
                boolean[] mask = e.getValue();
                Arrays.fill(used, false);

                for (int v = 0; v < V; v++) {
                    int row = v * U;
                    for (int u = 0; u < U; u++) {
                        int idx = row + u;
                        if (used[idx] || !mask[idx]) continue;

                        // grow width
                        int w = 1;
                        while (u + w < U && !used[row + u + w] && mask[row + u + w]) w++;

                        // grow height
                        int h = 1;
                        outer:
                        while (v + h < V) {
                            int r2 = (v + h) * U;
                            for (int uu = 0; uu < w; uu++) {
                                if (used[r2 + u + uu] || !mask[r2 + u + uu]) break outer;
                            }
                            h++;
                        }

                        // mark used
                        for (int dv = 0; dv < h; dv++) {
                            int r3 = (v + dv) * U;
                            for (int du = 0; du < w; du++) used[r3 + u + du] = true;
                        }

                        emit.rect(u, v, u + w, v + h, depth);
                    }
                }
            }
        }

        private static int clamp(int x, int lo, int hi) {
            return x < lo ? lo : Math.min(x, hi);
        }
    }

    /* -------- old 2D float greedy kept for reference; unused now -------- */
}