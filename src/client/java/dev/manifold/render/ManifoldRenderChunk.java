package dev.manifold.render;

import com.google.common.collect.ImmutableMap;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ManifoldRenderChunk {
    private final Map<BlockPos, BlockEntity> blockEntities;
    @Nullable
    private final List<PalettedContainer<BlockState>> sections;
    private final boolean debug;
    private final LevelChunk wrapped;

    public ManifoldRenderChunk(LevelChunk chunk) {
        this.wrapped = chunk;
        Level level = chunk.getLevel();
        this.debug = level.isDebug();
        this.blockEntities = ImmutableMap.copyOf(chunk.getBlockEntities());

        if (chunk instanceof EmptyLevelChunk) {
            this.sections = null;
        } else {
            LevelChunkSection[] levelChunkSections = chunk.getSections();
            this.sections = new ArrayList<>(levelChunkSections.length);

            for (LevelChunkSection section : levelChunkSections) {
                this.sections.add(section.hasOnlyAir() ? null : section.getStates().copy());
            }
        }
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    public BlockState getBlockState(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (debug) {
            BlockState state = null;
            if (y == 60) state = Blocks.BARRIER.defaultBlockState();
            if (y == 70) state = DebugLevelSource.getBlockStateFor(x, z);
            return state != null ? state : Blocks.AIR.defaultBlockState();
        }

        if (sections == null) return Blocks.AIR.defaultBlockState();

        try {
            int sectionIndex = wrapped.getSectionIndex(y);
            if (sectionIndex >= 0 && sectionIndex < sections.size()) {
                PalettedContainer<BlockState> container = sections.get(sectionIndex);
                if (container != null) {
                    return container.get(x & 15, y & 15, z & 15);
                }
            }
            return Blocks.AIR.defaultBlockState();
        } catch (Throwable t) {
            CrashReport report = CrashReport.forThrowable(t, "Getting block state");
            CrashReportCategory category = report.addCategory("Block being got");
            category.setDetail("Location", () -> CrashReportCategory.formatLocation(wrapped, x, y, z));
            throw new ReportedException(report);
        }
    }

    public LevelChunk getWrappedChunk() {
        return wrapped;
    }
}
