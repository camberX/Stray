package dev.stray.client.update;

import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudChrome;
import dev.stray.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Bottom-right update card. Slides in, holds, slides out. Click dismisses.
 */
public final class UpdateToast {
	private static final float WIDTH = 228f;
	private static final float HEIGHT = 52f;
	private static final float MARGIN = 14f;
	private static final float IN_SEC = 0.42f;
	private static final float OUT_SEC = 0.32f;
	private static final float HOLD_SEC = 5.6f;

	private enum Phase {
		IDLE, IN, HOLD, OUT
	}

	private static Phase phase = Phase.IDLE;
	private static float slide = 1f;
	private static float hold;
	private static long lastNs = System.nanoTime();
	private static String remote = "";
	private static String installed = "";
	private static float hitX;
	private static float hitY;
	private static float hitW;
	private static float hitH;
	private static boolean mouseWasDown;

	private UpdateToast() {
	}

	public static boolean visible() {
		return phase != Phase.IDLE;
	}

	public static void show(String next, String have) {
		if (next == null || next.isBlank()) {
			return;
		}
		if (phase != Phase.IDLE && next.equals(remote)) {
			return;
		}
		remote = next;
		installed = have == null || have.isBlank() ? "this version" : have;
		phase = Phase.IN;
		slide = 1f;
		hold = 0f;
		lastNs = System.nanoTime();
	}

	public static void extract(GuiGraphicsExtractor graphics) {
		if (phase == Phase.IDLE) {
			return;
		}
		long now = System.nanoTime();
		float dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		if (phase == Phase.IN) {
			slide = Math.max(0f, slide - dt / IN_SEC);
			if (slide <= 0f) {
				slide = 0f;
				phase = Phase.HOLD;
				hold = 0f;
			}
		} else if (phase == Phase.HOLD) {
			hold += dt;
			if (hold >= HOLD_SEC) {
				phase = Phase.OUT;
			}
		} else if (phase == Phase.OUT) {
			slide = Math.min(1f, slide + dt / OUT_SEC);
			if (slide >= 1f) {
				clear();
				return;
			}
		}

		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		Font font = client.font;
		float guiW = graphics.guiWidth();
		float guiH = graphics.guiHeight();
		float t = ease(slide);
		float x = guiW - MARGIN - WIDTH + t * (WIDTH + MARGIN + 8f);
		float y = guiH - MARGIN - HEIGHT + t * 18f;
		hitX = x;
		hitY = y;
		hitW = WIDTH;
		hitH = HEIGHT;

		HudChrome.panel(graphics, x, y, WIDTH, HEIGHT, 12f, Theme.HUD_WINDOW, Theme.HUD_LINE, Theme.ACCENT);
		GuiDraw.rounded(graphics, x, y + 10f, 3f, HEIGHT - 20f, 1.5f, Theme.ACCENT);

		GuiDraw.text(graphics, font, "STRAY  ·  UPDATE", x + 14f, y + 9f, Theme.TEXT, true);
		GuiDraw.text(graphics, font, remote + " is out", x + 14f, y + 22f, Theme.ACCENT, false);
		GuiDraw.text(graphics, font, "You have " + installed + "  ·  click to dismiss", x + 14f, y + 35f, Theme.MUTED, false);
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != 0) {
			return false;
		}
		return click(event.x(), event.y());
	}

	public static boolean click(double mouseX, double mouseY) {
		if (phase == Phase.IDLE || phase == Phase.OUT) {
			return false;
		}
		if (!GuiDraw.hovered(mouseX, mouseY, hitX, hitY, hitW, hitH)) {
			return false;
		}
		phase = Phase.OUT;
		return true;
	}

	public static void tickMouse(Minecraft client) {
		if (client == null || client.getWindow() == null || phase == Phase.IDLE || phase == Phase.OUT) {
			mouseWasDown = false;
			return;
		}
		if (client.screen != null) {
			mouseWasDown = false;
			return;
		}
		if (client.mouseHandler.isMouseGrabbed()) {
			mouseWasDown = false;
			return;
		}
		boolean down = GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (down && !mouseWasDown) {
			double mx = client.mouseHandler.getScaledXPos(client.getWindow());
			double my = client.mouseHandler.getScaledYPos(client.getWindow());
			click(mx, my);
		}
		mouseWasDown = down;
	}

	private static void clear() {
		phase = Phase.IDLE;
		slide = 1f;
		hold = 0f;
		hitW = 0f;
		hitH = 0f;
		remote = "";
		installed = "";
	}

	private static float ease(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * t;
	}
}
