package dev.stray.client.input;

import com.mojang.blaze3d.platform.InputConstants;
//? if <26.3 {
/*import net.minecraft.client.Minecraft;*///?}
import net.minecraft.client.input.MouseButtonEvent;
//? if >=26.3 {
import org.lwjgl.sdl.SDLMouse;
//?} else {
/*import org.lwjgl.glfw.GLFW;*///?}

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
	 * Slot-click packets use GLFW numbering (left 0, right 1). 26.3 mouse events use
	 * SDL numbering (left 1, right 3); 26.2 events are already GLFW.
	 */
	public static int containerButton(MouseButtonEvent event) {
		//? if >=26.3 {
		if (left(event)) {
			return 0;
		}
		if (right(event)) {
			return 1;
		}
		//?}
		return event == null ? 0 : event.button();
	}

	/** Whether a mouse button (GLFW numbering: 0 left, 1 right, 2 middle) is held right now. */
	public static boolean down(int button) {
		//? if >=26.3 {
		// 26.3 reads the mouse through SDL, which numbers buttons from 1.
		int sdl = button == 0 ? InputConstants.MOUSE_BUTTON_LEFT : button;
		if (sdl <= 0) {
			return false;
		}
		int state = SDLMouse.SDL_GetMouseState(null, null);
		return (state & (1 << (sdl - 1))) != 0;
		//?} else {
		/*if (button < 0) {
			return false;
		}
		return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), button) == GLFW.GLFW_PRESS;
		*///?}
	}
}
