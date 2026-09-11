package dev.stray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SkyRenderer.class)
public interface SkyRendererInvoker {
	@Invoker("renderStars")
	void stray$renderStars(float brightness, PoseStack pose);
}
