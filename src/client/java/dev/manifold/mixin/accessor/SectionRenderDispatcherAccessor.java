package dev.manifold.mixin.accessor;

import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionRenderDispatcher.class)
public interface SectionRenderDispatcherAccessor {
    @Accessor("sectionCompiler")
    SectionCompiler getSectionCompiler();
}
