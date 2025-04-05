package dev.manifold.mixin.accessor;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Set;

@Mixin(SectionRenderDispatcher.CompiledSection.class)
public interface SectionRenderDispatcher_CompiledSectionAccessor {
    @Accessor("visibilitySet")
    void manifold$setVisibilitySet(VisibilitySet visibilitySet);

    @Accessor("renderableBlockEntities")
    List<BlockEntity> manifold$getRenderableBlockEntities();

    @Accessor("transparencyState")
    void manifold$setTransparencyState(MeshData.SortState sortState);

    @Accessor("hasBlocks")
    Set<RenderType> manifold$getHasBlocks();
}
