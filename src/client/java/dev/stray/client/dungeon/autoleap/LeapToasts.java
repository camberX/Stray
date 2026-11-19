package dev.stray.client.dungeon.autoleap;

import dev.stray.client.render.GuiDraw;
import dev.stray.client.ui.ExColors;
import dev.stray.client.ui.ExLook;
import dev.stray.client.ui.Looks;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.ShadowLook;
import dev.stray.client.ui.Theme;
import dev.stray.client.ui.UidLook;
import dev.stray.client.ui.UidMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Phone-style notifications for Auto and Fast Leap: a banner springs down from the top of the
 * screen (overshooting a little and settling), says which leap it was and what happened, and
 * slides back up after a moment. Newer ones push older ones down; at most three show. Drawn in
 * the theme's panel and font.
 */
public final class LeapToasts {
	private static final long SHOW_MS = 2600L;
	private static final long IN_MS = 420L;
	private static final long OUT_MS = 260L;
	private static final int MAX = 3;
	private static final float W = 196f;
	private static final float H = 30f;
	private static final float GAP = 4f;

	private enum Kind { OK, WAIT, FAIL }

	private static final class Toast {
		final String source;
		final String body;
		final Kind kind;
		final long born = System.currentTimeMillis();
		float y = Float.NaN;

		Toast(String source, String body, Kind kind) {
			this.source = source;
			this.body = body;
			this.kind = kind;
		}
	}

	private static final List<Toast> toasts = new ArrayList<>();
	private static final ItemStack PEARL = new ItemStack(Items.ENDER_PEARL);

	private LeapToasts() {
	}

	/** A leap note as it would go to chat ("§aLeaping to §fArcher"); its first colour picks the kind. */
	public static void push(String source, String text) {
		Kind kind = text.startsWith("§c") ? Kind.FAIL : text.startsWith("§e") ? Kind.WAIT : Kind.OK;
		String body = text.replaceAll("§.", "");
		synchronized (toasts) {
			toasts.add(0, new Toast(source == null ? "Leap" : source, body, kind));
			while (toasts.size() > MAX + 1) {
				toasts.remove(toasts.size() - 1);
			}
		}
	}

	public static void extract(GuiGraphicsExtractor graphics) {
		List<Toast> list;
		synchronized (toasts) {
			long now = System.currentTimeMillis();
			toasts.removeIf(t -> now - t.born > SHOW_MS + OUT_MS);
			if (toasts.isEmpty()) {
				return;
			}
			list = new ArrayList<>(toasts);
		}
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		Theme.refresh();
		float x = Math.round((graphics.guiWidth() - W) / 2f);
		float slotY = 6f;
		long now = System.currentTimeMillis();
		for (int i = 0; i < list.size() && i < MAX; i++) {
			Toast t = list.get(i);
			long age = now - t.born;
			float target = slotY + i * (H + GAP);
			// In: a spring from above the screen; then it follows its place in the stack smoothly.
			float in = Math.min(1f, age / (float) IN_MS);
			float spring = backOut(in);
			float y = -H - 4f + (target + H + 4f) * spring;
			if (in >= 1f) {
				t.y = Float.isNaN(t.y) ? target : t.y + (target - t.y) * 0.25f;
				y = t.y;
			}
			float alpha = 1f;
			if (age > SHOW_MS) {
				float out = Math.min(1f, (age - SHOW_MS) / (float) OUT_MS);
				float e = out * out;
				y -= (H + 8f) * e;
				alpha = 1f - e;
			}
			if (alpha <= 0.01f) {
				continue;
			}
			GuiDraw.alphaScale = alpha;
			try {
				draw(graphics, font, t, x, y);
			} finally {
				GuiDraw.alphaScale = 1f;
			}
		}
	}

	private static float backOut(float t) {
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, Toast t, float x, float y) {
		int tone = t.kind == Kind.FAIL ? 0xFFE5484D : t.kind == Kind.WAIT ? 0xFFF5C542 : Theme.ACCENT;
		float icon = 20f;
		float ix = x + 5f;
		float iy = y + (H - icon) / 2f;
		if (Looks.uid()) {
			UidLook.frame(graphics, x, y, W, H, 0xF0);
			UidLook.control(graphics, ix + 1f, iy, icon, icon, false, false);
			GuiDraw.fillSmooth(graphics, ix + 1f, iy + icon - 2f * GuiDraw.pixel(), icon, 2f * GuiDraw.pixel(), t.kind == Kind.OK ? UidMenu.accent() : tone);
		} else if (Looks.ex()) {
			ExLook.surface(graphics, x, y, W, H, H / 2f, ExColors.alpha(ExColors.surfaceHigh, 0.97f), false);
			int bg = t.kind == Kind.FAIL ? ExColors.errorContainer : t.kind == Kind.WAIT ? ExColors.tertiaryContainer : ExColors.primaryContainer;
			ExLook.rounded(graphics, ix, iy, icon, icon, icon / 2f, bg);
		} else {
			ShadowLook.panel(graphics, x, y, W, H, 10f, Theme.HUD_WINDOW);
			GuiDraw.roundedFine(graphics, ix, iy, icon, icon, 6f, Theme.withAlpha(tone, 0x40));
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(ix + (icon - 16f * 0.875f) / 2f + (Looks.uid() ? 1f : 0f), iy + (icon - 16f * 0.875f) / 2f);
		graphics.pose().scale(0.875f, 0.875f);
		graphics.item(PEARL, 0, 0);
		graphics.pose().popMatrix();
		float tx = ix + icon + 6f;
		int muted = Looks.ex() ? ExColors.onSurfaceVariant : ShadowLook.MUTED;
		int text = Looks.ex() ? ExColors.onSurface : ShadowLook.TEXT;
		Component head = MenuFont.shadowSmall(t.source);
		GuiDraw.text(graphics, font, head, tx, y + 6f, t.kind == Kind.OK ? muted : tone, false);
		Component now = MenuFont.shadowSmall("now");
		GuiDraw.text(graphics, font, now, x + W - 8f - font.width(now), y + 6f, muted, false);
		String body = t.body;
		float max = x + W - 8f - tx;
		Component line = MenuFont.shadow(body);
		while (font.width(line) > max && body.length() > 2) {
			body = body.substring(0, body.length() - 2);
			line = MenuFont.shadow(body + "…");
		}
		GuiDraw.text(graphics, font, line, tx, y + 16f, text, false);
	}
}
