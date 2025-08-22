package dev.manifold.physics.collision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores six greedy-meshed collision planes per construct.
 * All data are in construct-local coordinates (integers).
 */
public final class ConstructCollisionManager {

    public record Planes(
            CollisionPlane up,
            CollisionPlane down,
            CollisionPlane north,
            CollisionPlane south,
            CollisionPlane west,
            CollisionPlane east
    ) {
        public List<CollisionPlane> all() {
            return List.of(up, down, north, south, west, east);
        }

        public CollisionPlane faces(Direction direction) {
            return switch (direction) {
                case DOWN -> down;
                case UP -> up;
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                case EAST -> east;
            };
        }

        public List<CollisionPlane.Rect> rects(Direction direction) {
            return switch (direction) {
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

    public static Optional<Planes> get(UUID id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /**
     * Rebuild all 6 planes from the block region [min..max] around origin (construct-local).
     * origin, min, max are block-aligned in the simulation dimension.
     */
    public static void rebuild(UUID id, Level level, BlockPos origin, BlockPos min, BlockPos max) {
        final CollisionPlane up    = new CollisionPlane(CollisionPlane.Face.UP);
        final CollisionPlane down  = new CollisionPlane(CollisionPlane.Face.DOWN);
        final CollisionPlane north = new CollisionPlane(CollisionPlane.Face.NORTH);
        final CollisionPlane south = new CollisionPlane(CollisionPlane.Face.SOUTH);
        final CollisionPlane west  = new CollisionPlane(CollisionPlane.Face.WEST);
        final CollisionPlane east  = new CollisionPlane(CollisionPlane.Face.EAST);

        // —— UP/DOWN: iterate all (x,z), check exposed faces per y, mesh per depth slice
        meshHorizontalPlane(level, origin, min, max, /*up=*/true,  up);
        meshHorizontalPlane(level, origin, min, max, /*up=*/false, down);

        // —— NORTH/SOUTH: iterate (x,y) per z slice
        meshZPlane(level, origin, min, max, /*south=*/false, north); // Z face at z
        meshZPlane(level, origin, min, max, /*south=*/true,  south); // Z+1

        // —— WEST/EAST: iterate (z,y) per x slice
        meshXPlane(level, origin, min, max, /*east=*/false, west);   // X face at x
        meshXPlane(level, origin, min, max, /*east=*/true,  east);   // X+1

        BY_ID.put(id, new Planes(up, down, north, south, west, east));
    }

    // -----------------------------------------------------------------------------------------
    // Building slices & greedy meshing
    // -----------------------------------------------------------------------------------------

    private static void meshHorizontalPlane(Level level, BlockPos origin, BlockPos min, BlockPos max, boolean up, CollisionPlane out) {
        final int x0 = min.getX(), x1 = max.getX();
        final int y0 = min.getY(), y1 = max.getY();
        final int z0 = min.getZ(), z1 = max.getZ();

        final int U = x1 - x0 + 1;
        final int V = z1 - z0 + 1;

        // For each Y layer, build a u×v grid of exposed faces, greedy-mesh to rectangles.
        for (int y = y0; y <= y1; y++) {
            // depth coordinate on the plane (see class doc)
            final int depth = up ? y + 1 : y;

            // grid[u][v] = friction or NaN if no face here
            float[][] grid = new float[U][V];
            for (int u = 0; u < U; u++) Arrays.fill(grid[u], Float.NaN);

            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos b = origin.offset(x, y, z);
                    BlockPos n = origin.offset(x, y + (up ? 1 : -1), z);

                    if (hasSolidCollision(level, b) && isAiry(level, n)) {
                        float mu = friction(level, b);
                        grid[x - x0][z - z0] = mu;
                    }
                }
            }

            greedyMesh2D(grid, (u0, v0, u1, v1, mu) -> {
                // convert back to construct-local coords
                int U0 = x0 + u0, V0 = z0 + v0, U1 = x0 + u1, V1 = z0 + v1;
                out.add(new CollisionPlane.Rect(U0, V0, U1, V1, depth, mu));
            });
        }
    }

    private static void meshZPlane(Level level, BlockPos origin, BlockPos min, BlockPos max, boolean south, CollisionPlane out) {
        final int x0 = min.getX(), x1 = max.getX();
        final int y0 = min.getY(), y1 = max.getY();
        final int z0 = min.getZ(), z1 = max.getZ();

        final int U = x1 - x0 + 1;
        final int V = y1 - y0 + 1;

        for (int z = z0; z <= z1; z++) {
            final int depth = south ? z + 1 : z;

            float[][] grid = new float[U][V];
            for (int u = 0; u < U; u++) Arrays.fill(grid[u], Float.NaN);

            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    BlockPos b = origin.offset(x, y, z);
                    BlockPos n = origin.offset(x, y, z + (south ? 1 : -1));

                    if (hasSolidCollision(level, b) && isAiry(level, n)) {
                        float mu = friction(level, b);
                        grid[x - x0][y - y0] = mu;
                    }
                }
            }

            greedyMesh2D(grid, (u0, v0, u1, v1, mu) -> {
                int U0 = x0 + u0, V0 = y0 + v0, U1 = x0 + u1, V1 = y0 + v1;
                out.add(new CollisionPlane.Rect(U0, V0, U1, V1, depth, mu));
            });
        }
    }

    private static void meshXPlane(Level level, BlockPos origin, BlockPos min, BlockPos max, boolean east, CollisionPlane out) {
        final int x0 = min.getX(), x1 = max.getX();
        final int y0 = min.getY(), y1 = max.getY();
        final int z0 = min.getZ(), z1 = max.getZ();

        final int U = z1 - z0 + 1;
        final int V = y1 - y0 + 1;

        for (int x = x0; x <= x1; x++) {
            final int depth = east ? x + 1 : x;

            float[][] grid = new float[U][V];
            for (int u = 0; u < U; u++) Arrays.fill(grid[u], Float.NaN);

            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    BlockPos b = origin.offset(x, y, z);
                    BlockPos n = origin.offset(x + (east ? 1 : -1), y, z);

                    if (hasSolidCollision(level, b) && isAiry(level, n)) {
                        float mu = friction(level, b);
                        grid[z - z0][y - y0] = mu;
                    }
                }
            }

            greedyMesh2D(grid, (u0, v0, u1, v1, mu) -> {
                int U0 = z0 + u0, V0 = y0 + v0, U1 = z0 + u1, V1 = y0 + v1;
                out.add(new CollisionPlane.Rect(U0, V0, U1, V1, depth, mu));
            });
        }
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private static boolean hasSolidCollision(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return false;
        VoxelShape shape = s.getCollisionShape(level, pos);
        return !shape.isEmpty();
    }

    private static boolean isAiry(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return true;
        VoxelShape shape = s.getCollisionShape(level, pos);
        return shape.isEmpty();
    }

    private static float friction(Level level, BlockPos pos) {
        try {
            BlockState s = level.getBlockState(pos);
            // If you want to refine later, replace with the exact friction API you use in your mod.
            return s.getBlock().getFriction(); // fallback; adjust signature for your MC version if needed
        } catch (Throwable t) {
            return 0.6f; // vanilla-ish default
        }
    }

    // Greedy meshing for a 2D grid of floats where NaN means "no face".
    private static void greedyMesh2D(float[][] grid, RectConsumer out) {
        final int U = grid.length;
        if (U == 0) return;
        final int V = grid[0].length;

        boolean[][] used = new boolean[U][V];
        final float EPS = 1e-6f;

        for (int u = 0; u < U; u++) {
            for (int v = 0; v < V; v++) {
                if (used[u][v]) continue;
                float mu = grid[u][v];
                if (Float.isNaN(mu)) continue;

                // grow width
                int w = 1;
                while (u + w < U && !used[u + w][v] && almostEq(grid[u + w][v], mu, EPS)) w++;

                // grow height
                int h = 1;
                outer:
                while (v + h < V) {
                    for (int i = 0; i < w; i++) {
                        if (used[u + i][v + h] || !almostEq(grid[u + i][v + h], mu, EPS)) break outer;
                    }
                    h++;
                }

                // mark used and emit rect
                for (int du = 0; du < w; du++) {
                    for (int dv = 0; dv < h; dv++) used[u + du][v + dv] = true;
                }
                out.emit(u, v, u + w, v + h, mu);
            }
        }
    }

    private interface RectConsumer {
        void emit(int u0, int v0, int u1, int v1, float friction);
    }

    private static boolean almostEq(float a, float b, float eps) {
        return Math.abs(a - b) <= eps;
    }
}