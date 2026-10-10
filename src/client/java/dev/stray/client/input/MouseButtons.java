package dev.stray.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLMouse;

public final class MouseButtons {
	private MouseButtons() {
	}

	public static boolean down(int button) {
		int sdl = button == 0 ? InputConstants.MOUSE_BUTTON_LEFT : button;
		if (sdl <= 0) {
			return false;
		}
		int state = SDLMouse.SDL_GetMouseState(null, null);
		return (state & (1 << (sdl - 1))) != 0;
	}
}
