package dev.stray.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

/** Mouse button helpers. 26.2 still uses GLFW numbering everywhere (left 0, right 1, middle 2). */
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

	/** Slot-click packets use GLFW numbering, which is what 26.2 mouse events already carry. */
	public static int containerButton(MouseButtonEvent event) {
		return event == null ? 0 : event.button();
	}

	public static boolean down(int button) {
		if (button < 0) {
			return false;
		}
		return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), button) == GLFW.GLFW_PRESS;
	}
}
