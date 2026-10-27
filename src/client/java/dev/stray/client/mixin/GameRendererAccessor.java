package dev.stray.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor("resourcePool")
	CrossFrameResourcePool stray$resourcePool();

	@Accessor("fogRenderer")
	FogRenderer stray$fogRenderer();

	@Accessor("mainRenderTarget")
	RenderTarget stray$mainRenderTarget();

	@Accessor("mainRenderTarget")
	@Mutable
	void stray$mainRenderTarget(RenderTarget target);
}
