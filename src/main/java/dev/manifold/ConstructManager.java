package dev.manifold;

import dev.manifold.access_holders.LayerLightStorageBridge;
import dev.manifold.mass.MassManager;
import dev.manifold.mixin.accessor.DataLayerStorageMapAccessor;
import dev.manifold.mixin.accessor.LightEngineAccessor;
import dev.manifold.network.packets.BreakInConstructC2SPacket;
import dev.manifold.network.packets.ConstructSectionDataS2CPacket;
import dev.manifold.network.packets.RemoveConstructS2CPacket;
import dev.manifold.physics.collision.ConstructCollisionManager; // NEW API (local-space OBBs)
import dev.manifold.physics.collision.VoxelSDF;
import dev.manifold.physics.core.OBB;
import dev.manifold.physics.core.RigidState;
import dev.manifold.physics.math.M3;
import dev.manifold.physics.math.V3;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector2i;
import org.joml.Vector3f;

import java.util.*;

public class ConstructManager {
    private static final BlockPos REGION_CENTER = new BlockPos(256, 256, 256);
    private static final int REGION_SIZE = 512;
    public static ConstructManager INSTANCE;
    private final ServerLevel simDimension;
    private final Map<UUID, DynamicConstruct> constructs = new HashMap<>();
    private final Map<Vector2i, UUID> regionOwners = new HashMap<>();

    public ConstructManager(ServerLevel simDimension) {
        this.simDimension = simDimension;
    }

    public static VoxelSDF buildLocalSdfFromSimArea(ServerLevel sim,
                                                     BlockPos simOrigin,
                                                     BlockPos neg, BlockPos pos) {
        // Collect all solid block centers in local space
        ArrayList<V3> solids = new ArrayList<>();
        for (int x = neg.getX(); x <= pos.getX(); x++) {
            for (int y = neg.getY(); y <= pos.getY(); y++) {
                for (int z = neg.getZ(); z <= pos.getZ(); z++) {
                    BlockPos rel = new BlockPos(x, y, z);
                    BlockPos abs = simOrigin.offset(rel);
                    if (!sim.getBlockState(abs).isAir()) {
                        // local center of the unit block at (x,y,z)
                        solids.add(new V3(x + 0.5, y + 0.5, z + 0.5));
                    }
                }
            }
        }

        // Define a node grid on integer coordinates that spans the [neg..pos] block region,
        // plus a 1-cell shell so trilinear samples near the boundary are well-defined.
        int nx = (pos.getX() - neg.getX() + 1) + 1;
        int ny = (pos.getY() - neg.getY() + 1) + 1;
        int nz = (pos.getZ() - neg.getZ() + 1) + 1;

        V3 origin = new V3(neg.getX(), neg.getY(), neg.getZ()); // grid node (0,0,0) is at local (negX,negY,negZ)
        float[][][] phi = new float[nx][ny][nz];

        // Precompute signed distance at each grid node to the union of blocks.
        // SDF to union of boxes is: phi(p) = min_i sdf_box(p, center_i, e=(0.5,0.5,0.5))
        // Positive outside, negative inside.
        V3 e = new V3(0.5, 0.5, 0.5);

        for (int i = 0; i < nx; i++) {
            double px = origin.x + i; // node coordinate (integer)
            for (int j = 0; j < ny; j++) {
                double py = origin.y + j;
                for (int k = 0; k < nz; k++) {
                    double pz = origin.z + k;

                    double best = Double.POSITIVE_INFINITY;
                    // If no solids, keep it "outside"
                    for (int s = 0; s < solids.size(); s++) {
                        V3 c = solids.get(s);
                        double d = sdfBox(px, py, pz, c, e); // exact SDF to axis-aligned box
                        if (d < best) best = d;
                    }
                    // If there were no solids, best stays +INF; clamp to a large positive
                    if (best == Double.POSITIVE_INFINITY) best = 1e3;
                    phi[i][j][k] = (float) best;
                }
            }
        }

        return new VoxelSDF(phi, origin);
    }

    /** Exact signed distance to an axis-aligned box centered at c with half-extents e. */
    private static double sdfBox(double px, double py, double pz, V3 c, V3 e) {
        double dx = Math.abs(px - c.x) - e.x;
        double dy = Math.abs(py - c.y) - e.y;
        double dz = Math.abs(pz - c.z) - e.z;
        double ax = Math.max(dx, 0.0);
        double ay = Math.max(dy, 0.0);
        double az = Math.max(dz, 0.0);
        double outside = Math.sqrt(ax*ax + ay*ay + az*az);
        double inside = Math.min(Math.max(dx, Math.max(dy, dz)), 0.0);
        return outside + inside; // positive outside, negative inside
    }

    public void loadFromSave(ConstructSaveData saveData) {
        for (DynamicConstruct construct : saveData.getConstructs().values()) {
            constructs.put(construct.getId(), construct);
            regionOwners.put(getRegionIndex(construct.getSimOrigin()), construct.getId());

            // NEW: rebuild local-space OBBs + upsert to collision manager
            rebuildCollisionForConstruct(construct);
        }
    }

    public ConstructSaveData toSaveData() {
        return new ConstructSaveData(constructs);
    }

    public UUID createConstruct(BlockState state, ServerLevel level) {
        Vector2i region = findFreeRegion();
        BlockPos center = regionCenterToWorld(region);
        UUID uuid = UUID.randomUUID();

        DynamicConstruct construct = new DynamicConstruct(uuid, level.dimension(), center);
        construct.setMass((int) MassManager.getMassOrDefault(state.getBlock().asItem()));
        simDimension.setBlock(center, state, 3);

        constructs.put(uuid, construct);
        regionOwners.put(region, uuid);

        // NEW: initial OBBs + upsert
        rebuildCollisionForConstruct(construct);

        return uuid;
    }

    public void removeConstruct(UUID id) {
        DynamicConstruct construct = constructs.remove(id);
        if (construct != null) {
            clearConstructArea(construct);
            regionOwners.remove(getRegionIndex(construct.getSimOrigin()));

            // NEW: remove from collision system
            ConstructCollisionManager.remove(id);

            // notify only players in the construct's render dimension
            for (ServerPlayer player : simDimension.getServer().getPlayerList().getPlayers()) {
                if (player.serverLevel().dimension().equals(construct.getWorldKey())) {
                    ServerPlayNetworking.send(player, new RemoveConstructS2CPacket(id));
                }
            }
        }
    }

    public void setPosition(UUID id, Vec3 position) {
        Optional.ofNullable(constructs.get(id)).ifPresent(c -> c.setPosition(position));
    }

    public void setVelocity(UUID id, Vec3 velocity) {
        Optional.ofNullable(constructs.get(id)).ifPresent(c -> c.setVelocity(velocity));
    }

    public void addVelocity(UUID id, Vec3 delta) {
        Optional.ofNullable(constructs.get(id)).ifPresent(c -> c.addVelocity(delta));
    }

    public void setRotation(UUID id, Quaternionf rotation) {
        Optional.ofNullable(constructs.get(id)).ifPresent(c -> c.setRotation(rotation));
    }

    public void addRotationalVelocity(UUID id, Quaternionf delta) {
        Optional.ofNullable(constructs.get(id)).ifPresent(c -> c.addAngularVelocity(delta));
    }

    private void clearConstructArea(DynamicConstruct construct) {
        AABB box = construct.getBoundingBox();
        BlockPos min = new BlockPos((int) box.minX, (int) box.minY, (int) box.minZ);
        BlockPos max = new BlockPos((int) box.maxX, (int) box.maxY, (int) box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            simDimension.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private Vector2i findFreeRegion() {
        for (int x = 0; x < 2048; x++) {
            for (int z = 0; z < 2048; z++) {
                Vector2i key = new Vector2i(x, z);
                if (!regionOwners.containsKey(key)) return key;
            }
        }
        throw new IllegalStateException("No free region available for construct.");
    }

    private BlockPos regionCenterToWorld(Vector2i region) {
        return new BlockPos(
                region.x * REGION_SIZE + REGION_CENTER.getX(),
                REGION_CENTER.getY(),
                region.y * REGION_SIZE + REGION_CENTER.getZ()
        );
    }

    private Vector2i getRegionIndex(BlockPos pos) {
        return new Vector2i(pos.getX() / REGION_SIZE, pos.getZ() / REGION_SIZE);
    }

    public Map<UUID, DynamicConstruct> getConstructs() {
        return constructs;
    }

    public boolean hasConstruct(UUID uuid) {
        return constructs.containsKey(uuid);
    }

    public void placeBlockInConstruct(UUID uuid, BlockPos rel, BlockState state) {
        DynamicConstruct construct = constructs.get(uuid);
        if (construct == null) return;

        BlockPos absolute = construct.getSimOrigin().offset(rel);
        simDimension.setBlock(absolute, state, 3);

        this.expandBounds(uuid, rel);

        // -- Mass and COM update --
        Vec3 oldCOM = construct.getCenterOfMass();
        int oldMass = construct.getMass();
        int blockMass = (int) MassManager.getMassOrDefault(state.getBlock().asItem());

        Vec3 newBlockCOM = new Vec3(rel.getX() + 0.5, rel.getY() + 0.5, rel.getZ() + 0.5);
        Vec3 newCOM = oldCOM.scale(oldMass).add(newBlockCOM.scale(blockMass)).scale(1.0 / (oldMass + blockMass));

        // Compute inverse-rotated COM delta to preserve world transform
        Vec3 deltaCOM = newCOM.subtract(oldCOM);
        Vector3f localShift = new Vector3f((float) deltaCOM.x, (float) deltaCOM.y, (float) deltaCOM.z);
        localShift = localShift.rotate(construct.getRotation());

        // Apply the position shift to compensate for pivot change
        construct.setPosition(construct.getPosition().add(new Vec3(localShift)));

        construct.setCenterOfMass(newCOM);
        construct.setMass(oldMass + blockMass);

        // -- Connectivity graph --
        construct.getConnectivityGraph().addBlock(rel);

        // NEW: OBBs are local; COM changed ⇒ update collision upsert (new localOrigin)
        rebuildCollisionForConstruct(construct);
    }

    public void breakBlockInConstruct(BreakInConstructC2SPacket packet, ServerPlayNetworking.Context context) {
        DynamicConstruct construct = constructs.get(packet.constructId());
        if (construct == null) return;

        BlockState oldState = simDimension.getBlockState(packet.blockHitPos());
        if (!context.player().isCreative() || oldState.getBlock() instanceof ShulkerBoxBlock) {
            simDimension.destroyBlock(packet.blockHitPos(), true, context.player());
        } else {
            simDimension.destroyBlock(packet.blockHitPos(), false, context.player());
        }
        simDimension.levelEvent(2001, packet.blockHitPos(), Block.getId(oldState)); // Show break effect

        BlockPos rel = packet.blockHitPos().subtract(construct.getSimOrigin());
        this.expandBounds(packet.constructId(), rel);

        // Only update COM if the block was actually removed
        if (!oldState.isAir()) {
            Vec3 removedCOM = new Vec3(rel.getX() + 0.5, rel.getY() + 0.5, rel.getZ() + 0.5);
            Vec3 oldCOM = construct.getCenterOfMass();
            int oldMass = construct.getMass();
            int blockMass = (int) MassManager.getMassOrDefault(oldState.getBlock().asItem());

            if (oldMass <= blockMass) {
                removeConstruct(construct.getId());
                return;
            }

            Vec3 newCOM = oldCOM.scale(oldMass)
                    .subtract(removedCOM.scale(blockMass))
                    .scale(1.0 / (oldMass - blockMass));

            // Compute inverse-rotated COM delta to preserve world transform
            Vec3 deltaCOM = newCOM.subtract(oldCOM);
            Vector3f localShift = new Vector3f((float) deltaCOM.x, (float) deltaCOM.y, (float) deltaCOM.z);
            localShift = localShift.rotate(construct.getRotation());

            // Apply the position shift to compensate for pivot change
            construct.setPosition(construct.getPosition().add(new Vec3(localShift)));

            // Commit changes
            construct.setCenterOfMass(newCOM);
            construct.setMass(oldMass - blockMass);

            // Connectivity graph
            construct.getConnectivityGraph().removeBlock(rel);

            // NEW: COM changed ⇒ refresh collision upsert
            rebuildCollisionForConstruct(construct);
        }
    }

    public void expandBounds(UUID id, BlockPos rel) {
        DynamicConstruct construct = constructs.get(id);
        if (construct == null) return;

        BlockPos neg = construct.getNegativeBounds();
        BlockPos pos = construct.getPositiveBounds();

        int newNegX = Math.min(neg.getX(), rel.getX() - 1);
        int newNegY = Math.min(neg.getY(), rel.getY() - 1);
        int newNegZ = Math.min(neg.getZ(), rel.getZ() - 1);

        int newPosX = Math.max(pos.getX(), rel.getX() + 1);
        int newPosY = Math.max(pos.getY(), rel.getY() + 1);
        int newPosZ = Math.max(pos.getZ(), rel.getZ() + 1);

        construct.setNegativeBounds(new BlockPos(newNegX, newNegY, newNegZ));
        construct.setPositiveBounds(new BlockPos(newPosX, newPosY, newPosZ));

        // NEW: bounds changed ⇒ rebuild local OBBs + upsert
        rebuildCollisionForConstruct(construct);
    }

    public void tick(MinecraftServer server) {
        for (DynamicConstruct construct : constructs.values()) {
            // physics update
            construct.physicsTick();

            // NEW: push updated pose/vel to collision system (no OBB rebuild needed here)
            ConstructCollisionManager.updateState(construct.getId(), rigidFromConstruct(construct));

            // compute sim chunks to keep loaded (in sim dimension)
            AABB box = construct.getBoundingBox();
            ChunkPos minChunk = new ChunkPos(Mth.floor(box.minX) >> 4, Mth.floor(box.minZ) >> 4);
            ChunkPos maxChunk = new ChunkPos(Mth.ceil(box.maxX)  >> 4, Mth.ceil(box.maxZ)  >> 4);

            for (int x = minChunk.x; x <= maxChunk.x; x++) {
                for (int z = minChunk.z; z <= maxChunk.z; z++) {
                    simDimension.setChunkForced(x, z, true);
                }
            }

            // send section data ONLY to players in the construct's render dimension
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.connection.isAcceptingMessages()) continue;

                // *** Dimension gate ***
                if (!player.serverLevel().dimension().equals(construct.getWorldKey())) {
                    continue;
                }

                // optional distance cull in render space (world coords)
                Vec3 p = player.position();
                if (construct.getRenderBoundingBox().intersects(
                        p.x - 128, p.y - 128, p.z - 128,
                        p.x + 128, p.y + 128, p.z + 128)) {

                    sendChunkDataToPlayer(player, construct, minChunk, maxChunk);
                }
            }
        }
    }

    private void sendChunkDataToPlayer(ServerPlayer player, DynamicConstruct construct, ChunkPos minChunk, ChunkPos maxChunk) {
        List<CompoundTag> sectionNBTs = new ArrayList<>();

        for (int cx = minChunk.x; cx <= maxChunk.x; cx++) {
            for (int cz = minChunk.z; cz <= maxChunk.z; cz++) {
                LevelChunk chunk = simDimension.getChunk(cx, cz);

                CompoundTag chunkTag = new CompoundTag();
                chunkTag.putInt("xPos", cx);
                chunkTag.putInt("zPos", cz);

                CompoundTag sectionsTag = new CompoundTag();

                ChunkPos pos = chunk.getPos();
                LevelLightEngine lightEngine = simDimension.getLightEngine();

                for (int y = 0; y < chunk.getSections().length; y++) {
                    LevelChunkSection section = chunk.getSections()[y];
                    int sectionY = y + chunk.getMinSection();
                    SectionPos sectionPos = SectionPos.of(pos, sectionY);
                    long sectionKey = sectionPos.asLong();

                    // === Save block data ===
                    if (section != null && !section.hasOnlyAir()) {
                        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                        section.write(buf);
                        sectionsTag.putByteArray("Section_" + sectionY, buf.array()); // Store as Section_12, etc.
                    }

                    // === Save light data ===
                    for (LightLayer layer : LightLayer.values()) {
                        LayerLightEventListener listener = lightEngine.getLayerListener(layer);
                        if (!(listener instanceof LightEngine<?, ?> engine)) continue;

                        LayerLightSectionStorage<?> storage = ((LightEngineAccessor) engine).manifold$getStorage();
                        DataLayerStorageMap<?> dataMap = ((LayerLightStorageBridge) storage).manifold$getUpdatingData();
                        Long2ObjectMap<DataLayer> map = ((DataLayerStorageMapAccessor) dataMap).manifold$getMap();

                        DataLayer data = map.get(sectionKey);
                        if (data != null) {
                            sectionsTag.putByteArray(layer.name() + "Light_" + sectionY, data.getData());
                        }
                    }
                }

                chunkTag.put("Sections", sectionsTag);
                sectionNBTs.add(chunkTag);
            }
        }

        ConstructSectionDataS2CPacket packet = new ConstructSectionDataS2CPacket(
                construct.getId(),
                construct.getSimOrigin(),
                minChunk.x, minChunk.z,
                maxChunk.x - minChunk.x + 1,
                maxChunk.z - minChunk.z + 1,
                sectionNBTs,
                construct.getWorldKey()
        );

        ServerPlayNetworking.send(player, packet);
    }

    public List<DynamicConstruct> getNearbyConstructs(ResourceKey<Level> worldKey, Vec3 center, int chunkRadius) {
        int r = chunkRadius * 16;
        return constructs.values().stream()
                .filter(c -> c.getWorldKey().equals(worldKey))
                .filter(c -> {
                    AABB box = c.getRenderBoundingBox();
                    return box.intersects(center.x - r, center.y - r, center.z - r,
                            center.x + r, center.y + r, center.z + r);
                })
                .toList();
    }

    public static ServerLevel getSimDimension() {
        return INSTANCE.simDimension;
    }

    public Optional<Vec3> getPosition(UUID id) {
        DynamicConstruct construct = this.constructs.get(id);
        if (construct == null) {
            return Optional.empty();
        } else {
            return Optional.of(construct.getPosition());
        }
    }

    public Optional<BlockPos> getSimOrigin(UUID id) {
        DynamicConstruct construct = this.constructs.get(id);
        if (construct == null) {
            return Optional.empty();
        } else {
            return Optional.of(construct.getSimOrigin());
        }
    }

    public Optional<Quaternionf> getRotation(UUID id) {
        DynamicConstruct construct = this.constructs.get(id);
        if (construct == null) {
            return Optional.empty();
        } else {
            return Optional.of(construct.getRotation());
        }
    }

    public Optional<Vec3> getCenterOfMass(UUID id) {
        DynamicConstruct construct = this.constructs.get(id);
        if (construct == null) {
            return Optional.empty();
        } else {
            return Optional.of(construct.getCenterOfMass());
        }
    }

    public AABB getAABB(UUID id) {
        return constructs.get(id).getBoundingBox();
    }

    public AABB getRenderAABB(UUID id) {
        return constructs.get(id).getRenderBoundingBox();
    }

    public void setRotationalVelocity(UUID uuid, Quaternionf quaternionf) {
        DynamicConstruct construct = this.constructs.get(uuid);
        construct.setAngularVelocity(quaternionf);
    }

    public void updateConstructCOMS(MassManager.ChangedItem changedItem) {
        Item targetItem = changedItem.item();
        double oldMass = changedItem.oldMass();
        double newBlockMass = changedItem.newMass();
        double deltaPerBlock = newBlockMass - oldMass;

        for (DynamicConstruct construct : constructs.values()) {
            BlockPos origin = construct.getSimOrigin();
            AABB box = construct.getBoundingBox();

            BlockPos min = new BlockPos((int) box.minX, (int) box.minY, (int) box.minZ);
            BlockPos max = new BlockPos((int) box.maxX, (int) box.maxY, (int) box.maxZ);

            int totalAffected = 0;
            Vec3 totalWeightedCOM = construct.getCenterOfMass().scale(construct.getMass());

            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                BlockState state = simDimension.getBlockState(pos);
                if (state.getBlock().asItem() == targetItem) {
                    // Block found, calculate rel pos
                    BlockPos rel = pos.subtract(origin);
                    Vec3 comPoint = new Vec3(rel.getX() + 0.5, rel.getY() + 0.5, rel.getZ() + 0.5);

                    // Apply delta mass * position
                    totalWeightedCOM = totalWeightedCOM.add(comPoint.scale(deltaPerBlock));
                    totalAffected++;
                }
            }

            if (totalAffected > 0) {
                int newConstructMass = construct.getMass() + (int) Math.round(totalAffected * deltaPerBlock);
                Vec3 newCOM = totalWeightedCOM.scale(1.0 / newConstructMass);

                // Apply COM correction to keep world position stable
                Vec3 oldCOM = construct.getCenterOfMass();
                Vec3 deltaCOM = newCOM.subtract(oldCOM);
                Vector3f localShift = new Vector3f((float) deltaCOM.x, (float) deltaCOM.y, (float) deltaCOM.z);
                localShift = localShift.rotate(construct.getRotation());

                // Apply the position shift to compensate for pivot change
                construct.setPosition(construct.getPosition().add(new Vec3(localShift)));
                construct.setCenterOfMass(newCOM);
                construct.setMass(newConstructMass);

                // NEW: COM changed ⇒ refresh collision upsert
                rebuildCollisionForConstruct(construct);
            }
        }
    }

    public Optional<UUID> getConstructAt(Vec3 position) {
        int regionX = Mth.floor((position.x + REGION_SIZE / 2.0 - REGION_CENTER.getX()) / REGION_SIZE);
        int regionZ = Mth.floor((position.z + REGION_SIZE / 2.0 - REGION_CENTER.getZ()) / REGION_SIZE);
        return Optional.ofNullable(regionOwners.get(new Vector2i(regionX, regionZ)));
    }

    public Optional<ServerLevel> getRenderLevel(UUID uuid) {
        DynamicConstruct construct = constructs.get(uuid);
        if (construct == null) return Optional.empty();

        MinecraftServer server = simDimension.getServer();
        if (server == null) return Optional.empty();

        return Optional.ofNullable(server.getLevel(construct.getWorldKey()));
    }

    public Vec3 getRenderPosFromSim(UUID uuid, Vec3 simPosition) {
        DynamicConstruct construct = constructs.get(uuid);
        if (construct == null) throw new IllegalArgumentException("No construct with id " + uuid);

        BlockPos simOrigin = construct.getSimOrigin();
        Vec3 com = construct.getCenterOfMass();
        Quaternionf rotation = construct.getRotation();
        Vec3 worldPos = construct.getPosition();

        // simPosition - simOrigin - com
        Vec3 localHit = simPosition.subtract(Vec3.atLowerCornerOf(simOrigin)).subtract(com);

        // rotate localHit by rotation
        Vector3f rotated = new Vector3f((float) localHit.x, (float) localHit.y, (float) localHit.z);
        rotated.rotate(rotation);

        // Add construct's world position
        return new Vec3(rotated.x, rotated.y, rotated.z).add(worldPos);
    }

    public BlockPos getSimPosFromRender(UUID uuid, Vec3 renderPos) {
        DynamicConstruct construct = constructs.get(uuid);
        if (construct == null) throw new IllegalArgumentException("No construct with id " + uuid);

        BlockPos simOrigin = construct.getSimOrigin();
        Vec3 com = construct.getCenterOfMass();
        Quaternionf rotation = new Quaternionf(construct.getRotation()).invert(); // inverse rotation
        Vec3 worldPos = construct.getPosition();

        // Step 1: Subtract world position
        Vec3 localRender = renderPos.subtract(worldPos);

        // Step 2: Inverse rotate
        Vector3f unrotated = new Vector3f((float) localRender.x, (float) localRender.y, (float) localRender.z);
        unrotated.rotate(rotation);

        // Step 3–4: Add back center of mass and sim origin
        Vec3 simPos = new Vec3(unrotated.x, unrotated.y, unrotated.z).add(com).add(Vec3.atLowerCornerOf(simOrigin));

        return BlockPos.containing(simPos);
    }

    public boolean trySeparateBlocks(UUID constructId, List<SeparatorRecord> separators) {
        DynamicConstruct construct = constructs.get(constructId);
        if (construct == null) return false;

        BlockUnionGraph graph = construct.getConnectivityGraph();

        // Temporarily remove edges between separator blocks
        List<BlockPos> roots = new ArrayList<>();
        for (SeparatorRecord r : separators) {
            graph.disconnect(r.a(), r.b());
            roots.add(r.a());
            roots.add(r.b());
        }

        // Check if any roots now belong to different connected components
        BlockPos reference = roots.get(0);
        for (int i = 1; i < roots.size(); i++) {
            if (!graph.connected(reference, roots.get(i))) {
                // Found a separation
                Set<BlockPos> component = graph.getGroup(roots.get(i));
                UUID newId = ConstructManager.INSTANCE.splitConstruct(constructId, component);
                return newId != null;
            }
        }

        // If not separated, reconnect edges (optional)
        for (SeparatorRecord r : separators) {
            graph.union(r.a(), r.b());
        }

        return false;
    }

    public UUID splitConstruct(UUID originalId, Set<BlockPos> extractedRelBlocks) {
        DynamicConstruct original = constructs.get(originalId);
        if (original == null) return null;

        UUID newId = createConstruct(Blocks.AIR.defaultBlockState(), simDimension); // temp block

        DynamicConstruct newConstruct = constructs.get(newId);
        BlockPos originalOrigin = original.getSimOrigin();
        BlockPos newOrigin = newConstruct.getSimOrigin();

        // Copy and move blocks
        int totalMass = 0;
        Vec3 weightedCOM = Vec3.ZERO;

        for (BlockPos rel : extractedRelBlocks) {
            BlockPos abs = originalOrigin.offset(rel);
            BlockState state = simDimension.getBlockState(abs);
            if (state.isAir()) continue;

            BlockPos newRel = rel; // can offset if origin shifting is needed
            BlockPos newAbs = newOrigin.offset(newRel);

            simDimension.setBlock(newAbs, state, 3);
            simDimension.setBlock(abs, Blocks.AIR.defaultBlockState(), 3);

            newConstruct.getConnectivityGraph().addBlock(newRel);

            int mass = (int) MassManager.getMassOrDefault(state.getBlock().asItem());
            totalMass += mass;
            weightedCOM = weightedCOM.add(new Vec3(newRel.getX() + 0.5, newRel.getY() + 0.5, newRel.getZ() + 0.5).scale(mass));
        }

        if (totalMass == 0) return null;

        Vec3 newCOM = weightedCOM.scale(1.0 / totalMass);
        newConstruct.setMass(totalMass);
        newConstruct.setCenterOfMass(newCOM);
        newConstruct.setPosition(original.getPosition()); // inherit world pos

        // Shrink bounds of both constructs
        updateConstructBounds(original);
        updateConstructBounds(newConstruct);

        return newId;
    }

    private void updateConstructBounds(DynamicConstruct construct) {
        BlockUnionGraph graph = construct.getConnectivityGraph();
        Set<BlockPos> allBlocks = graph.getGroup(graph.findAny());

        if (allBlocks.isEmpty()) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : allBlocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());

            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        construct.setNegativeBounds(new BlockPos(minX - 1, minY - 1, minZ - 1));
        construct.setPositiveBounds(new BlockPos(maxX + 1, maxY + 1, maxZ + 1));

        // NEW: rebuild OBBs + upsert to collision manager
        rebuildCollisionForConstruct(construct);
    }

    // =========================
    // ===== NEW HELPERS =======
    // =========================

    /** Rebuilds local-space OBBs for the given construct and upsserts into collision manager with current state. */
    private void rebuildCollisionForConstruct(DynamicConstruct construct) {
        // 1) Local OBBs from the sim area (per solid block)
        List<OBB> localObbs = buildLocalObbsFromSimArea(
                simDimension,
                construct.getSimOrigin(),
                construct.getNegativeBounds(),
                construct.getPositiveBounds(),
                /* default μ */ 0.6
        );

        // 2) Local SDF from same block set (signed distance to union of unit cubes)
        VoxelSDF sdf = buildLocalSdfFromSimArea(
                simDimension,
                construct.getSimOrigin(),
                construct.getNegativeBounds(),
                construct.getPositiveBounds()
        );

        // 3) Upsert with localOrigin = COM (world = pos + rot * (local - COM))
        V3 localOrigin = new V3(
                construct.getCenterOfMass().x,
                construct.getCenterOfMass().y,
                construct.getCenterOfMass().z
        );

        ConstructCollisionManager.upsertConstruct(
                construct.getId(),
                localObbs,
                rigidFromConstruct(construct),
                localOrigin,
                sdf
        );
    }

    /** Builds simple per-block local OBBs for all non-air blocks in [neg..pos] around simOrigin. */
    public static List<OBB> buildLocalObbsFromSimArea(ServerLevel sim,
                                                      BlockPos simOrigin,
                                                      BlockPos neg, BlockPos pos,
                                                      double defaultMu) {
        ArrayList<OBB> out = new ArrayList<>();
        int id = 0;
        for (int x = neg.getX(); x <= pos.getX(); x++) {
            for (int y = neg.getY(); y <= pos.getY(); y++) {
                for (int z = neg.getZ(); z <= pos.getZ(); z++) {
                    BlockPos rel = new BlockPos(x, y, z);
                    BlockPos abs = simOrigin.offset(rel);
                    BlockState bs = sim.getBlockState(abs);
                    if (bs.isAir()) continue;

                    // Local-space AABB block => local OBB
                    OBB o = new OBB();
                    o.c = new V3(x + 0.5, y + 0.5, z + 0.5);
                    o.e = new V3(0.5, 0.5, 0.5);
                    o.R = M3.identity();
                    // TODO: use real material μ from block if desired
                    o.mu = defaultMu;
                    o.id = id++;
                    out.add(o);
                }
            }
        }
        return out;
    }

    /** Build a RigidState from DynamicConstruct’s pose + velocities. */
    public static RigidState rigidFromConstruct(DynamicConstruct c) {
        // position (world)
        V3 pos = new V3(c.getPosition().x, c.getPosition().y, c.getPosition().z);
        // rotation (world)
        M3 rot = fromQuaternion(c.getRotation());
        // linear velocity (world)
        Vec3 v = c.getVelocity(); // assuming you have it; else Vec3.ZERO
        V3 vLin = new V3(v.x, v.y, v.z);
        // angular velocity (world): if stored as Quaternion delta per tick, convert; otherwise 0.
        V3 vAng = new V3(0, 0, 0); // TODO: map your angular-velocity representation

        // Kinematic constructs for now (invMass=0)
        RigidState rs = new RigidState();
        rs.set(pos, rot, vLin, vAng, /*invMass*/ 0.0, M3.identity());
        return rs;
    }

    /** Convert JOML Quaternionf to our M3 rotation (columns are basis vectors). */
    private static M3 fromQuaternion(Quaternionf q) {
        // JOML stores unit quaternion; build 3x3 matrix
        float xx = q.x * q.x, yy = q.y * q.y, zz = q.z * q.z;
        float xy = q.x * q.y, xz = q.x * q.z, yz = q.y * q.z;
        float wx = q.w * q.x, wy = q.w * q.y, wz = q.w * q.z;

        V3 x = new V3(1f - 2f*(yy + zz), 2f*(xy + wz),       2f*(xz - wy));
        V3 y = new V3(2f*(xy - wz),       1f - 2f*(xx + zz), 2f*(yz + wx));
        V3 z = new V3(2f*(xz + wy),       2f*(yz - wx),      1f - 2f*(xx + yy));
        M3 m = new M3(); m.set(x, y, z);
        return m;
    }
}