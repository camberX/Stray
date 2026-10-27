package dev.stray.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.sdl.SDLMouse;

public final class MouseButtons {
	private MouseButtons() {
	}

	public static boolean left(MouseButtonEvent event) {
		return event != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT;
	}

	public static boolean right(MouseButtonEvent event) {
		return event != null && event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
	}

	public static boolean middle(MouseButtonEvent event) {
		return event != null && event.button() == InputConstants.MOUSE_BUTTON_MIDDLE;
	}

	/**
	 * Slot-click packets still use GLFW numbering: left 0, right 1.
	 * 26.3 mouse events use SDL numbering: left 1, right 3.
	 */
	public static int containerButton(MouseButtonEvent event) {
		if (left(event)) {
			return 0;
		}
		if (right(event)) {
			return 1;
		}
		return event == null ? 0 : event.button();
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
