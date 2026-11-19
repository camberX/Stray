package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
//? if >=26.3 {
import dev.stray.client.input.MouseButtons;
//?}
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.TitleBackdrop;
import dev.stray.client.render.WatermarkRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Asked once, on the first start: which look Stray uses. A Shadow window with the three
 * looks side by side, each name centred above a preview drawn with that look's own pieces
 * (its menu, its watermark, its buttons). Picking one switches everything to it.
 */
public final class ThemePickerScreen extends Screen {
	private final Screen parent;
	private int hovered = -1;
	private final float[] lift = new float[3];
	private final float[][] boxes = new float[3][4];
	private long lastNs = System.nanoTime();

	public ThemePickerScreen(Screen parent) {
		super(Component.literal("Pick a theme"));
		this.parent = parent;
	}

	/** Opens the picker instead of {@code parent} when it hasn't been answered yet. */
	public static boolean ask(net.minecraft.client.Minecraft client, Screen parent) {
		StrayConfig config = StrayConfig.get();
		if (config == null || config.themeChosen) {
			return false;
		}
		client.gui.setScreen(new ThemePickerScreen(parent));
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Looks.force(Looks.SHADOW);
		try {
			TitleBackdrop.draw(graphics, width, height);
		} finally {
			Looks.force(null);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min(0.05f, (now - lastNs) / 1.0e9f);
		lastNs = now;
		Font font = minecraft.font;
		float pad = 14f;
		float gap = 10f;
		float winW = Math.min(width - 24f, 700f);
		float colW = (winW - pad * 2 - gap * 2) / 3f;
		float previewH = colW * 1.08f;
		float winH = 44f + 16f + previewH + 28f + pad;
		float winX = (width - winW) / 2f;
		float winY = Math.max(8f, (height - winH) / 2f);
		Looks.force(Looks.SHADOW);
		try {
			ShadowLook.window(graphics, winX, winY, winW, winH, ShadowLook.WINDOW_R, ShadowLook.FILL);
			GuiDraw.title(graphics, font, "Pick a theme", winX + pad, winY + 12, ShadowLook.TEXT);
			String sub = "Stray's menu, HUD and every screen will look like this. Change it any time in Misc > Look.";
			GuiDraw.small(graphics, font, GuiDraw.ellipsize(font, sub, winW - pad * 2, true), winX + pad, winY + 28, ShadowLook.MUTED);
		} finally {
			Looks.force(null);
		}
		hovered = -1;
		for (int i = 0; i < 3; i++) {
			float cx = winX + pad + i * (colW + gap);
			float top = winY + 44f;
			boolean hot = GuiDraw.hovered(mouseX, mouseY, cx - 4, top - 4, colW + 8, 16f + previewH + 30f);
			if (hot) {
				hovered = i;
			}
			lift[i] += ((hot ? 1f : 0f) - lift[i]) * Math.min(1f, dt * 14f);
			boxes[i][0] = cx - 4;
			boxes[i][1] = top - 4;
			boxes[i][2] = colW + 8;
			boxes[i][3] = 16f + previewH + 30f;
			Looks.force(Looks.SHADOW);
			try {
				if (lift[i] > 0.02f) {
					GuiDraw.roundedFine(graphics, cx - 4, top - 4, colW + 8, 16f + previewH + 30f, 10f, Theme.withAlpha(0xFFFFFF, Math.round(0x14 * lift[i])));
				}
				String name = Looks.LABELS[i];
				float nw = GuiDraw.titleWidth(font, name);
				GuiDraw.title(graphics, font, name, cx + (colW - nw) / 2f, top, hot ? 0xFFFFFFFF : ShadowLook.TEXT);
			} finally {
				Looks.force(null);
			}
			Looks.force(Looks.ALL[i]);
			try {
				preview(graphics, font, i, cx, top + 16f, colW, previewH);
			} finally {
				Looks.force(null);
			}
			Looks.force(Looks.SHADOW);
			try {
				String hint = hot ? "Use " + Looks.LABELS[i] : "";
				float hw = GuiDraw.menuWidth(font, hint);
				GuiDraw.menu(graphics, font, hint, cx + (colW - hw) / 2f, top + 16f + previewH + 10f, Theme.ACCENT);
			} finally {
				Looks.force(null);
			}
		}
	}

	/** One look's preview: a small menu, its watermark and a vanilla button, all in that look. */
	private void preview(GuiGraphicsExtractor graphics, Font font, int look, float x, float y, float w, float h) {
		double gui = minecraft.getWindow().getGuiScale();
		// The menu part is drawn in the look's own pixels, as its real menu is.
		double dpi = look == 1 ? UidMenu.dpiScale() : look == 2 ? ExpressiveMenu.dpiScale() : gui;
		float k = (float) (dpi / gui);
		float menuH = h - 46f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(k, k);
		float uw = w / k;
		float uh = menuH / k;
		switch (look) {
			case 1 -> uidMenu(graphics, font, uw, uh);
			case 2 -> {
				ExLook.textScale = dpi;
				exMenu(graphics, font, uw, uh);
			}
			default -> shadowMenu(graphics, font, uw, uh);
		}
		graphics.pose().popMatrix();
		// The watermark, as the HUD would draw it.
		float wy = y + menuH + 6f;
		float ww = WatermarkRenderer.width(font);
		float ws = Math.min(1f, w / Math.max(1f, ww));
		WatermarkRenderer.draw(graphics, font, x, wy, ws);
		// A vanilla button.
		float by = wy + 22f;
		MenuChrome.box(graphics, x, by, w, 18f, false, true);
		Component label = MenuChrome.bodyLabel(Component.literal("Singleplayer"), true);
		float lw = font.width(label);
		GuiDraw.text(graphics, font, label, x + (w - lw) / 2f, GuiDraw.middle(by, 18f), 0xFFFFFFFF, false);
	}

	private void shadowMenu(GuiGraphicsExtractor graphics, Font font, float w, float h) {
		ShadowLook.window(graphics, 0, 0, w, h, ShadowLook.WINDOW_R, ShadowLook.FILL);
		GuiDraw.menu(graphics, font, "Leap menu", 10, 9, ShadowLook.TEXT);
		float y = 26;
		String[] rows = {"Keys 1-4 leap", "Announce leap", "Tint dead players"};
		boolean[] on = {true, false, true};
		for (int i = 0; i < rows.length; i++) {
			ShadowLook.card(graphics, 6, y, w - 12, 16, ShadowLook.CONTROL_R, false, false);
			GuiDraw.menu(graphics, font, rows[i], 12, GuiDraw.middle(y, 16), ShadowLook.TEXT);
			float sx = w - 12 - 18;
			GuiDraw.roundedFine(graphics, sx, y + 4, 16, 8, 4, on[i] ? Theme.ACCENT : 0x30FFFFFF);
			GuiDraw.circle(graphics, on[i] ? sx + 12 : sx + 4, y + 8, 3f, 0xFFFFFFFF);
			y += 19;
		}
		GuiDraw.menu(graphics, font, "Card size", 10, y + 2, ShadowLook.MUTED);
		y += 13;
		GuiDraw.roundedFine(graphics, 10, y, w - 20, 3, 1.5f, 0x22FFFFFF);
		GuiDraw.roundedFine(graphics, 10, y, (w - 20) * 0.45f, 3, 1.5f, Theme.ACCENT);
		GuiDraw.circle(graphics, 10 + (w - 20) * 0.45f, y + 1.5f, 3.5f, 0xFFFFFFFF);
	}

	private void uidMenu(GuiGraphicsExtractor graphics, Font font, float w, float h) {
		UidLook.window(graphics, 0, 0, w, h);
		UidLook.strip(graphics, 0, 0, w);
		float gx = 20;
		float gy = 26;
		float gw = w - 40;
		UidLook.group(graphics, font, gx, gy, gw, h - gy - 14, "Leap menu");
		float y = gy + 14;
		int accent = UidMenu.accent();
		String[] rows = {"Keys 1-4 leap", "Announce leap", "Tint dead players"};
		boolean[] on = {true, false, true};
		for (int i = 0; i < rows.length && y + 14 < h - 30; i++) {
			UidLook.checkbox(graphics, gx + 10, y + 3, on[i], false, accent, true);
			UidLook.text(graphics, font, rows[i], gx + 30, y + 1, UidLook.LABEL);
			y += 18;
		}
		if (y + 26 < h - 14) {
			UidLook.text(graphics, font, "Card size", gx + 30, y + 1, UidLook.LABEL);
			UidLook.slider(graphics, gx + 30, y + 16, gw - 50, 0.45f, false, accent);
		}
	}

	private void exMenu(GuiGraphicsExtractor graphics, Font font, float w, float h) {
		ExLook.surface(graphics, 0, 0, w, h, 24f, ExColors.alpha(ExColors.surface, ExColors.opacity()), false);
		// A sliver of the rail with its pill.
		ExLook.rounded(graphics, 8, 8, 44, h - 16, 16f, ExColors.surfaceLow);
		ExLook.rounded(graphics, 12, 22, 36, 22, 11f, ExColors.secondaryContainer);
		for (int i = 0; i < 3; i++) {
			ExLook.rounded(graphics, 22, 58 + i * 30, 16, 16, i == 1 ? 5f : 8f, ExColors.alpha(ExColors.onSurfaceVariant, 0.45f));
		}
		float cx = 62;
		float cw = w - cx - 10;
		ExLook.rounded(graphics, cx, 10, cw, h - 20, 20f, ExColors.surfaceHigh);
		ExLook.text(graphics, font, ExLook.BOLD, "Leap menu", cx + 12, 22, ExColors.onSurface);
		ExLook.toggle(graphics, cx + cw - 12 - 36, 18, 1f, 1f, false);
		float y = 52;
		String[] rows = {"Keys 1-4 leap", "Announce leap"};
		float[] on = {1f, 0f};
		for (int i = 0; i < rows.length && y + 26 < h - 20; i++) {
			ExLook.text(graphics, font, ExLook.BODY, ExLook.fit(font, ExLook.BODY, rows[i], cw - 70), cx + 12, y + 6, ExColors.onSurface);
			ExLook.toggle(graphics, cx + cw - 12 - 36, y, 1f, on[i], false);
			y += 30;
		}
		if (y + 44 < h - 14) {
			ExLook.text(graphics, font, ExLook.BODY, "Order", cx + 12, y + 2, ExColors.onSurface);
			float sy = y + 20;
			float sw = (cw - 24 - 4) / 3f;
			String[] opts = {"Odin", "A-Z", "None"};
			for (int i = 0; i < 3; i++) {
				float bx = cx + 12 + i * (sw + 2);
				ExLook.segment(graphics, bx, sy, sw, 26, i == 0, i == 2, i == 0, 0f);
				float tw = ExLook.width(font, i == 0 ? ExLook.BOLD : ExLook.BODY, opts[i]);
				ExLook.text(graphics, font, i == 0 ? ExLook.BOLD : ExLook.BODY, opts[i], bx + (sw - tw) / 2f, sy + 7, i == 0 ? ExColors.onPrimary : ExColors.onSurface);
			}
			y = sy + 34;
		}
		if (y + 24 < h - 14) {
			ExLook.slider(graphics, cx + 12, y, cw - 24, 0.45f, false, 1f);
		}
	}

	private void pick(int look) {
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
		StrayConfig config = StrayConfig.get();
		config.themeChosen = true;
		Looks.set(Looks.ALL[Mth.clamp(look, 0, 2)]);
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		//? if >=26.3 {
		if (!MouseButtons.left(event)) {
		//?} else {
		/*if (event.button() != 0) {*///?}
			return super.mouseClicked(event, doubled);
		}
		for (int i = 0; i < 3; i++) {
			float[] b = boxes[i];
			if (event.x() >= b[0] && event.x() <= b[0] + b[2] && event.y() >= b[1] && event.y() <= b[1] + b[3]) {
				pick(i);
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}
}
