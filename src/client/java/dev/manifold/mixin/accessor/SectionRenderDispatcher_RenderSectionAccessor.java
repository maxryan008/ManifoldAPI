package dev.manifold.mixin.accessor;

import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public interface SectionRenderDispatcher_RenderSectionAccessor {
    @Accessor("compiled")
    AtomicReference<SectionRenderDispatcher.CompiledSection> getCompiled();

    @Invoker("createVertexSorting")
    VertexSorting manifold$createVertexSorting();
}