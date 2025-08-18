package dev.manifold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A union-find-based structure that incrementally tracks connected groups of BlockPos.
 * Allows block addition, removal, and explicit disconnection (breaking links).
 */
public class BlockUnionGraph {
    private final Map<BlockPos, BlockPos> parent = new HashMap<>();
    private final Map<BlockPos, Set<BlockPos>> adjacency = new HashMap<>();

    public void addBlock(BlockPos pos) {
        if (parent.containsKey(pos)) return;

        parent.put(pos, pos);
        adjacency.put(pos, new HashSet<>());

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (parent.containsKey(neighbor)) {
                // Mutual linking
                adjacency.get(pos).add(neighbor);
                adjacency.get(neighbor).add(pos);
                union(pos, neighbor);
            }
        }
    }

    public void removeBlock(BlockPos pos) {
        if (!parent.containsKey(pos)) return;

        // Clean up adjacency
        Set<BlockPos> neighbors = adjacency.remove(pos);
        if (neighbors != null) {
            for (BlockPos neighbor : neighbors) {
                Set<BlockPos> set = adjacency.get(neighbor);
                if (set != null) {
                    set.remove(pos);
                }
            }
        }

        parent.remove(pos);

        // Optionally: flag entire component as dirty for rebuild (if needed)
    }

    public void disconnect(BlockPos a, BlockPos b) {
        if (!adjacency.containsKey(a) || !adjacency.containsKey(b)) return;

        adjacency.get(a).remove(b);
        adjacency.get(b).remove(a);

        // If they were connected in the same component, we need to recheck that component
        if (connected(a, b)) {
            // Rebuild both components from scratch
            Set<BlockPos> groupA = bfs(a);
            Set<BlockPos> groupB = bfs(b);

            // Reset parent entries for both groups
            for (BlockPos p : groupA) parent.put(p, p);
            for (BlockPos p : groupB) parent.put(p, p);

            // Re-union within each group
            reUnionGroup(groupA);
            reUnionGroup(groupB);
        }
    }

    private void reUnionGroup(Set<BlockPos> group) {
        for (BlockPos pos : group) {
            for (BlockPos neighbor : adjacency.getOrDefault(pos, Collections.emptySet())) {
                if (group.contains(neighbor)) {
                    union(pos, neighbor);
                }
            }
        }
    }

    private Set<BlockPos> bfs(BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (BlockPos neighbor : adjacency.getOrDefault(current, Collections.emptySet())) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return visited;
    }

    public BlockPos find(BlockPos pos) {
        BlockPos p = parent.get(pos);
        if (p == null) return null;
        if (!p.equals(pos)) {
            BlockPos root = find(p);
            parent.put(pos, root);
            return root;
        }
        return p;
    }

    public void union(BlockPos a, BlockPos b) {
        BlockPos rootA = find(a);
        BlockPos rootB = find(b);
        if (rootA != null && rootB != null && !rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }

    public boolean connected(BlockPos a, BlockPos b) {
        BlockPos rootA = find(a);
        BlockPos rootB = find(b);
        return rootA != null && rootA.equals(rootB);
    }

    public Set<BlockPos> getGroup(BlockPos root) {
        BlockPos canonical = find(root);
        if (canonical == null) return Collections.emptySet();

        Set<BlockPos> group = new HashSet<>();
        for (BlockPos pos : parent.keySet()) {
            if (canonical.equals(find(pos))) {
                group.add(pos);
            }
        }
        return group;
    }

    public BlockPos findAny() {
        return parent.keySet().stream().findFirst().orElse(null);
    }
}