package dev.stray.client.mixin;

import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.3 {
import dev.stray.Stray;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.oit.OitStage;
import com.mojang.renderpearl.api.commands.RenderPass;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
//?}

/**
 * With Improved Transparency (Transparency: shader) on, 26.3 draws translucent render types in
 * an order-independent transparency pass and throws if a render type has no OIT pipelines.
 * Some mods register such types (Athen's Parallax: parallax_debug_filled), which crashed the
 * game mid-frame. Skip just that draw instead; everything else renders as usual.
 * Only in the 26.3 mixin config.
 */
@Mixin(PreparedRenderType.class)
public class OitGuardMixin {
	//? if >=26.3 {
	@Unique
	private static final Set<String> stray$warned = ConcurrentHashMap.newKeySet();

	@Inject(method = "drawFromBufferOit", at = @At("HEAD"), cancellable = true)
	private void stray$skipMissingOit(StagedVertexBuffer.ExecuteInfo info, OitStage stage, RenderPass pass, CallbackInfo ci) {
		PreparedRenderType self = (PreparedRenderType) (Object) this;
		if (self.oitPipelineSet() == null) {
			if (stray$warned.add(self.name())) {
				Stray.LOGGER.warn("Render type {} has no OIT pipelines (another mod's); skipped it in the transparency pass instead of crashing", self.name());
			}
			ci.cancel();
		}
	}
	//?}
}
