package dev.stray.client.input;

import com.mojang.blaze3d.platform.InputConstants;
//? if <26.3 {
/*import net.minecraft.client.Minecraft;*///?}
import net.minecraft.client.input.MouseButtonEvent;
//? if >=26.3 {
import org.lwjgl.sdl.SDLMouse;
//?} else {
/*import org.lwjgl.glfw.GLFW;*///?}

//? if <26.3 {
/*/^* Mouse button helpers. 26.2 still uses GLFW numbering everywhere (left 0, right 1, middle 2). ^/*///?}
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

	//? if >=26.3 {
	/**
	 * Slot-click packets still use GLFW numbering: left 0, right 1.
	 * 26.3 mouse events use SDL numbering: left 1, right 3.
	 */
	//?} else {
	/*/^* Slot-click packets use GLFW numbering, which is what 26.2 mouse events already carry. ^/*///?}
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

	public static boolean down(int button) {
		//? if >=26.3 {
		int sdl = button == 0 ? InputConstants.MOUSE_BUTTON_LEFT : button;
		if (sdl <= 0) {
		//?} else {
		/*if (button < 0) {*///?}
			return false;
		}
		//? if >=26.3 {
		int state = SDLMouse.SDL_GetMouseState(null, null);
		return (state & (1 << (sdl - 1))) != 0;
		//?} else {
		/*return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), button) == GLFW.GLFW_PRESS;*///?}
	}
}
