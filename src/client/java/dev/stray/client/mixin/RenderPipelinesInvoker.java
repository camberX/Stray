package dev.stray.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesInvoker {
	@Invoker("register")
	static RenderPipeline stray$register(RenderPipeline pipeline) {
		throw new AssertionError();
	}
}
