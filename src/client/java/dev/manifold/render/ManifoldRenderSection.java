package dev.manifold.render;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.Vec3i;

public record ManifoldRenderSection(SectionRenderDispatcher.RenderSection section, Vec3i offset) {
}