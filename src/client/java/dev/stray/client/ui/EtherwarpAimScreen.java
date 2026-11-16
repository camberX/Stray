package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DungeonRooms;
import dev.stray.client.dungeon.EtherwarpAim;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.screens.Screen;

import java.util.Locale;

/**
 * Etherwarp aim settings, with a preview of your screen: the crosshair and the circle in which
 * the aim assist starts pulling, drawn to scale for your field of view and window shape.
 */
public class EtherwarpAimScreen extends SubScreen {
	private static final int[] SWATCHES = {
		0xB57CFF, 0x2FB5FF, 0x52E08A, 0xFFD34F, 0xFF7A1A, 0xE8414B, 0xFF4F8B, 0xF4F6FF
	};
	private static final float PREVIEW_W = 190f;

	public EtherwarpAimScreen(Screen parent) {
		super("Etherwarp aim", parent, 470);
	}

	@Override
	protected void save() {
		StrayConfig.get().save();
	}

	@Override
	protected float body(float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		float gap = 16f;
		float pw = PREVIEW_W;
		float lw = w - pw - gap;
		float top = y;

		// Settings on the left.
		float ly = section("Aim", x, y);
		ly = toggleRow("Aim assist", config.etherwarpAimAssist, x, ly, lw, v -> config.etherwarpAimAssist = v);
		ly = slider("Speed", Integer.toString(config.etherwarpAimSpeed), (config.etherwarpAimSpeed - 1) / 99f, x, ly + 2f, lw,
			t -> config.etherwarpAimSpeed = Math.round(1 + (float) t * 99f));
		// Squared, so the small angles you aim with most get most of the slider.
		ly = slider("Range", rangeLabel(config.etherwarpAimRange), (float) Math.sqrt((config.etherwarpAimRange - 0.5f) / 179.5f), x, ly + 2f, lw,
			t -> {
				float range = 0.5f + (float) (t * t) * 179.5f;
				config.etherwarpAimRange = range < 10f ? Math.round(range * 2f) / 2f : Math.round(range);
			});
		ly = slider("Delay", config.etherwarpAimDelay + "ms", config.etherwarpAimDelay / 300f, x, ly + 2f, lw,
			t -> config.etherwarpAimDelay = Math.round((float) t * 60f) * 5);
		ly = divider(x, ly + 4f, lw);

		ly = section("Points", x, ly);
		ly = toggleRow("Edit mode", config.etherwarpAimEdit, x, ly, lw, v -> config.etherwarpAimEdit = v);
		ly = swatches("Points", config.etherwarpAimRgb, v -> config.etherwarpAimRgb = v, x, ly + 4f, lw);
		ly = swatches("Next", config.etherwarpAimNextRgb, v -> config.etherwarpAimNextRgb = v, x, ly + 4f, lw) + 6f;
		int here = EtherwarpAim.count();
		DungeonRooms.Room room = DungeonRooms.current();
		String place = room == null ? "Not in a room" : room.name();
		text(fit(place, lw - 90f), x, ly, BUTTON, MUTED);
		String clear = here == 0 ? "No points" : "Clear " + here;
		float cw = buttonWidth(clear);
		button(clear, x + lw - cw, ly, cw, Style.DANGER, here == 0 ? null : EtherwarpAim::clearHere);
		ly += BUTTON;

		// The preview on the right.
		float ph = previewHeight(pw);
		drawPreview(x + lw + gap, top + 16f, pw, ph, config);
		section("Preview", x + lw + gap, top);
		GuiDraw.fill(graphics, x + lw + gap * 0.5f, top, 0.5f, Math.max(ly, top + 16f + ph) - top, 0x18FFFFFF);

		float bottom = Math.max(ly, top + 16f + ph) + 12f;
		return buttons(x, bottom, w, new Object[][] {}, new Object[][] {{"Done", Style.PRIMARY, (Runnable) this::onClose}});
	}

	/** A label and a row of colours to pick from. Returns the next y. */
	private float swatches(String label, int current, java.util.function.IntConsumer set, float x, float y, float w) {
		float size = 14f;
		text(label, x, y, size, MUTED);
		float sx = x + Math.max(56f, textWidth(label) + 12f);
		for (int color : SWATCHES) {
			if (sx + size > x + w) {
				break;
			}
			boolean on = (current & 0xFFFFFF) == color;
			boolean hot = hovered(sx, y, size, size);
			if (on || hot) {
				GuiDraw.roundedFine(graphics, sx - 2f, y - 2f, size + 4f, size + 4f, 5f, on ? 0xFFEEF1F5 : 0x60FFFFFF);
			}
			GuiDraw.roundedFine(graphics, sx, y, size, size, 4f, 0xFF000000 | color);
			hit(sx, y, size, size, () -> set.accept(color));
			sx += size + 6f;
		}
		return y + size + 4f;
	}

	private static String rangeLabel(float range) {
		return range < 10f ? String.format(Locale.ROOT, "%.1f°", range) : Math.round(range) + "°";
	}

	/** The preview has the shape of your game window. */
	private float previewHeight(float pw) {
		float aspect = minecraft == null || minecraft.getWindow().getHeight() == 0 ? 16f / 9f
			: (float) minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight();
		return pw / Math.max(0.5f, aspect);
	}

	private void drawPreview(float x, float y, float w, float h, StrayConfig config) {
		GuiDraw.roundedFine(graphics, x, y, w, h, ShadowLook.CONTROL_R, 0x40000000);
		GuiDraw.roundedOutline(graphics, x, y, w, h, ShadowLook.CONTROL_R, 0x22FFFFFF, 1f);
		float cx = x + w * 0.5f;
		float cy = y + h * 0.5f;
		// How far the range reaches on screen: tan(range) over tan(half the vertical FOV), of half the height.
		double fov = minecraft == null ? 70.0 : minecraft.options.fov().get();
		// How far the range reaches on screen: tan(range) over tan(half the vertical FOV), of half the height.
		int rgb = config.etherwarpAimRgb & 0xFFFFFF;
		double range = config.etherwarpAimRange;
		if (range >= 89.5) {
			// Past 90° it reaches behind you: all of the view.
			GuiDraw.roundedFine(graphics, x, y, w, h, ShadowLook.CONTROL_R, 0x22000000 | rgb);
		} else {
			double reach = Math.tan(Math.toRadians(range)) / Math.tan(Math.toRadians(fov * 0.5));
			float r = (float) Math.min(reach * h * 0.5, 4000.0);
			boolean clip = GuiDraw.scissor(graphics, x, y, w, h);
			try {
				GuiDraw.circle(graphics, cx, cy, r, 0x22000000 | rgb);
				GuiDraw.roundedOutline(graphics, cx - r, cy - r, r * 2f, r * 2f, r, 0xC0000000 | rgb, 1f);
			} finally {
				if (clip) {
					GuiDraw.disableScissor(graphics);
				}
			}
		}
		// The crosshair.
		float arm = 4.5f;
		GuiDraw.fill(graphics, cx - arm, cy - 0.5f, arm * 2f, 1f, 0xFFEEF1F5);
		GuiDraw.fill(graphics, cx - 0.5f, cy - arm, 1f, arm * 2f, 0xFFEEF1F5);
		String label = rangeLabel(config.etherwarpAimRange) + " · FOV " + Math.round(fov);
		GuiDraw.small(graphics, font, label, x + 6f, y + h - 11f, FAINT);
	}
}
