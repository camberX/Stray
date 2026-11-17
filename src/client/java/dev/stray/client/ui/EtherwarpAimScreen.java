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
	private static final float PREVIEW_W = 190f;
	/** How tall the settings were last frame. */
	private float contentH = 400f;

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

		// Settings on the left, scrolling when they don't fit (a colour picker open, a short window).
		float visible = Math.max(80f, Math.min(contentH, room(y, BUTTON + 12f)));
		scrollArea("settings", x, y, lw, visible, (sx, sy, sw) -> {
			float bottom = settings(config, sx, sy, sw);
			contentH = bottom - sy;
			return bottom;
		});
		float ly = y + visible;

		// The preview on the right.
		float ph = previewHeight(pw);
		drawPreview(x + lw + gap, top + 16f, pw, ph, config);
		section("Preview", x + lw + gap, top);
		GuiDraw.fill(graphics, x + lw + gap * 0.5f, top, 0.5f, Math.max(ly, top + 16f + ph) - top, 0x18FFFFFF);

		float bottom = Math.max(ly, top + 16f + ph) + 12f;
		return buttons(x, bottom, w, new Object[][] {{"Browse routes", Style.SECONDARY, (Runnable) () -> minecraft.gui.setScreen(new EtherwarpRoutesScreen(this))}}, new Object[][] {{"Done", Style.PRIMARY, (Runnable) this::onClose}});
	}

	/** Everything on the left; scrolls when the window is short. Returns the bottom. */
	private float settings(StrayConfig config, float x, float y, float lw) {
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

		ly = toggleRow("Auto swap", config.etherwarpAimAutoSwap, x, ly, lw, v -> config.etherwarpAimAutoSwap = v);
		if (config.etherwarpAimAutoSwap) {
			ly = toggleRow("Swap back", config.etherwarpAimSwapBack, x + 10f, ly, lw - 10f, v -> config.etherwarpAimSwapBack = v);
			if (config.etherwarpAimSwapBack) {
				int ticks = config.etherwarpAimSwapBackTicks;
				ly = slider("After", ticks + (ticks == 1 ? " tick" : " ticks"), (ticks - 1) / 19f, x + 10f, ly + 2f, lw - 10f,
					t -> config.etherwarpAimSwapBackTicks = 1 + Math.round((float) t * 19f));
			}
		}
		ly = divider(x, ly + 4f, lw);

		ly = section("Points", x, ly);
		ly = toggleRow("Edit mode", config.etherwarpAimEdit, x, ly, lw, v -> config.etherwarpAimEdit = v);
		ly = toggleRow("Only the next two", config.etherwarpAimNextTwo, x, ly, lw, v -> config.etherwarpAimNextTwo = v);
		ly = toggleRow("Numbers", config.etherwarpAimNumbers, x, ly, lw, v -> config.etherwarpAimNumbers = v);
		ly = toggleRow("Hide when secrets are done", config.etherwarpAimHideDone, x, ly, lw, v -> config.etherwarpAimHideDone = v);
		// How the points are drawn.
		text("Style", x, ly + 2f, 18f, MUTED);
		float cx = x + 56f;
		String[] styles = {"Outline + fill", "Outline", "Glow + fill", "Glow"};
		float cy = ly + 2f;
		for (int i = 0; i < styles.length; i++) {
			int style = i;
			if (cx + chipWidth(styles[i]) > x + lw) {
				cx = x + 56f;
				cy += 21f;
			}
			cx += chip(styles[i], cx, cy, config.etherwarpAimStyle == i, () -> config.etherwarpAimStyle = style) + 4f;
		}
		ly = cy + 18f + 6f;
		ly = colorPicker("points", "Points", config.etherwarpAimRgb, config.etherwarpAimAlpha, x, ly + 2f, lw,
			v -> config.etherwarpAimRgb = v == 0 ? 0x010101 : v, v -> config.etherwarpAimAlpha = v);
		ly = colorPicker("next", "Next", config.etherwarpAimNextRgb, config.etherwarpAimNextAlpha, x, ly, lw,
			v -> config.etherwarpAimNextRgb = v == 0 ? 0x010101 : v, v -> config.etherwarpAimNextAlpha = v) + 6f;
		int here = EtherwarpAim.count();
		DungeonRooms.Room room = DungeonRooms.current();
		String place = room == null ? "Not in a room" : room.name();
		text(fit(place, lw - 170f), x, ly, BUTTON, MUTED);
		String clear = here == 0 ? "No points" : "Clear " + here;
		float cw = buttonWidth(clear);
		button(clear, x + lw - cw, ly, cw, Style.DANGER, here == 0 ? null : EtherwarpAim::clearHere);
		float rw = buttonWidth("Restart");
		button("Restart", x + lw - cw - GAP - rw, ly, rw, Style.SECONDARY, here == 0 ? null : EtherwarpAim::resetRoute);
		ly += BUTTON;

		return ly;
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
