package dev.stray.client.mixin;

//? if >=26.3 {
import com.mojang.blaze3d.platform.Window;
import dev.stray.client.render.overlay.window.Streamproof;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.3: the game handles every SDL window event as its own; the streamproof overlay's
// (moves, resizes, show/hide) are dropped here. Full edition only.
@Mixin(Window.class)
public class OverlayWindowEventMixin {
	@Inject(method = "handleEvent", at = @At("HEAD"), cancellable = true, require = 0)
	private void stray$skipOverlay(SDL_Event event, CallbackInfo ci) {
		if (Streamproof.ownsWindow(SDLEvents.SDL_GetWindowFromEvent(event))) {
			ci.cancel();
		}
	}
}
//?}
