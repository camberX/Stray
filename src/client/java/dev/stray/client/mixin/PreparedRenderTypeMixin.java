package dev.stray.client.mixin;

import dev.stray.client.cs2.render.KnifeGpu;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CS2 knives: draw the knife from its GPU mesh instead of the batch's placeholder triangle. */
@Mixin(PreparedRenderType.class)
public class PreparedRenderTypeMixin {
	@Inject(method = "drawFromBuffer", at = @At("HEAD"), cancellable = true)
	private void stray$cs2KnifeDraw(StagedVertexBuffer.ExecuteInfo info, CallbackInfo ci) {
		PreparedRenderType self = (PreparedRenderType) (Object) this;
		if (KnifeGpu.handles(self)) {
			KnifeGpu.draw(self);
			ci.cancel();
		}
	}
}
