package dev.manifold;

import dev.manifold.network.packets.BreakInConstructC2SPacket;
import dev.manifold.network.packets.ConstructSectionDataS2CPacket;
import dev.manifold.phyics.collision.ConstructCollisionManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
    private final ConstructCollisionManager collisionManager = new ConstructCollisionManager();

    public ConstructManager(ServerLevel simDimension) {
        this.simDimension = simDimension;
    }

    public ConstructCollisionManager getCollisionManager() {
        return collisionManager;
    }

    public void loadFromSave(ConstructSaveData saveData) {
        for (DynamicConstruct construct : saveData.getConstructs().values()) {
            constructs.put(construct.getId(), construct);
            regionOwners.put(getRegionIndex(construct.getSimOrigin()), construct.getId());
            collisionManager.updateCollision(construct.getId(), simDimension, construct.getSimOrigin(), construct.getNegativeBounds(), construct.getPositiveBounds());
        }
    }

    public ConstructSaveData toSaveData() {
        return new ConstructSaveData(constructs);
    }

    public UUID createConstruct(BlockState state) {
        Vector2i region = findFreeRegion();
        BlockPos center = regionCenterToWorld(region);
        UUID uuid = UUID.randomUUID();

        DynamicConstruct construct = new DynamicConstruct(uuid, simDimension.dimension(), center);
        simDimension.setBlock(center, state, 3);

        constructs.put(uuid, construct);
        regionOwners.put(region, uuid);
        return uuid;
    }

    public void removeConstruct(UUID id) {
        DynamicConstruct construct = constructs.remove(id);
        if (construct != null) {
            clearConstructArea(construct);
            regionOwners.remove(getRegionIndex(construct.getSimOrigin()));
            collisionManager.remove(id);
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

    public void placeBlockInConstruct(UUID uuid, BlockPos rel, BlockState state) {
        DynamicConstruct construct = constructs.get(uuid);
        if (construct == null) return;

        BlockPos absolute = construct.getSimOrigin().offset(rel);
        simDimension.setBlock(absolute, state, 3);

        this.expandBounds(uuid, rel);

        // -- Mass and COM update --
        Vec3 oldCOM = construct.getCenterOfMass();
        int oldMass = construct.getMass();
        int blockMass = 1000;

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
    }

    public void breakBlockInConstruct(BreakInConstructC2SPacket packet) {
        DynamicConstruct construct = constructs.get(packet.constructId());
        if (construct == null) return;

        BlockState oldState = simDimension.getBlockState(packet.blockHitPos());
        simDimension.setBlock(packet.blockHitPos(), Blocks.AIR.defaultBlockState(), 3);
        simDimension.levelEvent(2001, packet.blockHitPos(), Block.getId(oldState)); // Show break effect

        BlockPos rel = packet.blockHitPos().subtract(construct.getSimOrigin());
        this.expandBounds(packet.constructId(), rel);

        // Only update COM if the block was actually removed
        if (!oldState.isAir()) {
            Vec3 removedCOM = new Vec3(rel.getX() + 0.5, rel.getY() + 0.5, rel.getZ() + 0.5);
            Vec3 oldCOM = construct.getCenterOfMass();
            int oldMass = construct.getMass();
            int blockMass = 1000;

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
        }
    }

    public void expandBounds(UUID id, BlockPos rel) {
        DynamicConstruct construct = constructs.get(id);
        if (construct == null) return;
        System.out.println("Expanding construct with relative pos " + rel);

        BlockPos neg = construct.getNegativeBounds();
        BlockPos pos = construct.getPositiveBounds();

        System.out.println("Negative Bounds " + neg);
        System.out.println("Positive Bounds " + pos);

        int newNegX = Math.min(neg.getX(), rel.getX() - 1);
        int newNegY = Math.min(neg.getY(), rel.getY() - 1);
        int newNegZ = Math.min(neg.getZ(), rel.getZ() - 1);

        int newPosX = Math.max(pos.getX(), rel.getX() + 1);
        int newPosY = Math.max(pos.getY(), rel.getY() + 1);
        int newPosZ = Math.max(pos.getZ(), rel.getZ() + 1);

        System.out.println("New Negative Bounds " + new BlockPos(newNegX, newNegY, newNegZ));
        System.out.println("New Positive Bounds " + new BlockPos(newPosX, newPosY, newPosZ));

        construct.setNegativeBounds(new BlockPos(newNegX, newNegY, newNegZ));
        construct.setPositiveBounds(new BlockPos(newPosX, newPosY, newPosZ));

        collisionManager.updateCollision(id, simDimension, construct.getSimOrigin(), construct.getNegativeBounds(), construct.getPositiveBounds());
    }

    public void tick(MinecraftServer server) {
        for (DynamicConstruct construct : constructs.values()) {
            //call physics tick
            construct.physicsTick();

            AABB box = construct.getBoundingBox();
            ChunkPos minChunk = new ChunkPos(Mth.floor(box.minX) >> 4, Mth.floor(box.minZ) >> 4);
            ChunkPos maxChunk = new ChunkPos(Mth.ceil(box.maxX) >> 4, Mth.ceil(box.maxZ) >> 4);

            minChunk = new ChunkPos(minChunk.x, minChunk.z);
            maxChunk = new ChunkPos(maxChunk.x, maxChunk.z);

            // Force-load chunks for ticking
            for (int x = minChunk.x; x <= maxChunk.x; x++) {
                for (int z = minChunk.z; z <= maxChunk.z; z++) {
                    simDimension.setChunkForced(x, z, true);
                }
            }

            // Send section data to players regardless of dimension
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.connection.isAcceptingMessages()) continue;

                Vec3 playerPos = player.position();
                if (construct.getRenderBoundingBox().intersects(
                        playerPos.x - 128, playerPos.y - 128, playerPos.z - 128,
                        playerPos.x + 128, playerPos.y + 128, playerPos.z + 128)) {

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
                LevelChunkSection[] sections = chunk.getSections();

                CompoundTag chunkTag = new CompoundTag();
                chunkTag.putInt("xPos", cx);
                chunkTag.putInt("zPos", cz);

                CompoundTag sectionsTag = new CompoundTag();

                for (int y = 0; y < sections.length; y++) {
                    LevelChunkSection section = sections[y];
                    if (section != null && !section.hasOnlyAir()) {
                        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                        section.write(buf);
                        sectionsTag.putByteArray(Integer.toString(y + chunk.getMinSection()), buf.array());
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
                sectionNBTs
        );

        ServerPlayNetworking.send(player, packet);
    }

    public List<DynamicConstruct> getNearbyConstructs(Vec3 center, int chunkRadius) {
        int radiusBlocks = chunkRadius * 16;
        return constructs.values().stream()
                .filter(construct -> {
                    AABB box = construct.getRenderBoundingBox();
                    return box.intersects(center.x - radiusBlocks, center.y - radiusBlocks, center.z - radiusBlocks,
                            center.x + radiusBlocks, center.y + radiusBlocks, center.z + radiusBlocks);
                })
                .toList();
    }

    public Level getSimDimension() {
        return this.simDimension;
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
}
