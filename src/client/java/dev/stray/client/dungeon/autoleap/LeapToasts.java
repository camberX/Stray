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
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
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
		final String who;
		final long born = System.currentTimeMillis();
		float y = Float.NaN;

		Toast(String source, String body, Kind kind, String who) {
			this.source = source;
			this.who = who;
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
		push(source, text, null);
	}

	/** {@link #push(String, String)} with the player it was about, whose face the banner shows. */
	public static void push(String source, String text, String who) {
		Kind kind = text.startsWith("§c") ? Kind.FAIL : text.startsWith("§e") ? Kind.WAIT : Kind.OK;
		String body = text.replaceAll("§.", "");
		synchronized (toasts) {
			toasts.add(0, new Toast(source == null ? "Leap" : source, body, kind, who == null || who.isBlank() ? null : who));
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

	/** The skin of an online player by name, or null when they are not in the tab list. */
	private static PlayerSkin skin(String name) {
		Minecraft client = Minecraft.getInstance();
		if (name == null || client.getConnection() == null) {
			return null;
		}
		PlayerInfo info = client.getConnection().getPlayerInfo(name);
		return info == null ? null : info.getSkin();
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, Toast t, float x, float y) {
		int tone = t.kind == Kind.FAIL ? 0xFFE5484D : t.kind == Kind.WAIT ? 0xFFF5C542 : Theme.ACCENT;
		// The free inside of the card: below the uid? strip and inside its bands, else the whole card.
		float top = y;
		float bottom = y + H;
		if (Looks.uid()) {
			top = y + UidLook.frameTop(graphics, x, y, W, H, 0xF0);
			bottom = y + H - UidLook.frameInset(graphics);
		} else if (Looks.ex()) {
			ExLook.surface(graphics, x, y, W, H, H / 2f, ExColors.alpha(ExColors.surfaceHigh, 0.97f), false);
		} else {
			ShadowLook.panel(graphics, x, y, W, H, 10f, Theme.HUD_WINDOW);
		}
		float room = bottom - top;
		float icon = Math.min(20f, room - (Looks.uid() ? 4f : 10f));
		float ix = x + (Looks.uid() ? UidLook.frameInset(graphics) + 2f : (H - icon) / 2f);
		float iy = top + (room - icon) / 2f;
		PlayerSkin skin = skin(t.who);
		if (Looks.uid()) {
			UidLook.control(graphics, ix, iy, icon, icon, false, false);
			float p = GuiDraw.pixel() / GuiDraw.poseScale(graphics);
			GuiDraw.fillSmooth(graphics, ix + p, iy + icon - 2f * p, icon - 2f * p, p, t.kind == Kind.OK ? UidMenu.accent() : tone);
		} else if (Looks.ex()) {
			int bg = t.kind == Kind.FAIL ? ExColors.errorContainer : t.kind == Kind.WAIT ? ExColors.tertiaryContainer : ExColors.primaryContainer;
			ExLook.rounded(graphics, ix, iy, icon, icon, icon / 2f, bg);
		} else {
			GuiDraw.roundedFine(graphics, ix, iy, icon, icon, Looks.panels() ? 3f : Looks.minecraft() ? 0f : 6f, Looks.panels() ? dev.stray.client.ui.PanelLook.G2 : Theme.withAlpha(tone, 0x40));
		}
		float face = icon - (Looks.uid() ? 5f : 6f);
		float fx = ix + (icon - face) / 2f;
		float fy = iy + (icon - face) / 2f - (Looks.uid() ? 0.5f : 0f);
		if (skin != null) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(fx, fy);
			graphics.pose().scale(face / 8f, face / 8f);
			PlayerFaceExtractor.extractRenderState(graphics, skin, 0, 0, 8);
			graphics.pose().popMatrix();
		} else {
			graphics.pose().pushMatrix();
			graphics.pose().translate(fx, fy);
			graphics.pose().scale(face / 16f, face / 16f);
			graphics.item(PEARL, 0, 0);
			graphics.pose().popMatrix();
		}
		float tx = ix + icon + 6f;
		int muted = Looks.ex() ? ExColors.onSurfaceVariant : ShadowLook.MUTED;
		int text = Looks.ex() ? ExColors.onSurface : ShadowLook.TEXT;
		// Two 9-tall lines a unit apart, the pair centred in the free inside (per-font nudge for ink).
		float mid = top + room / 2f;
		float headY = mid - 9.5f + HEAD_NUDGE[look()];
		float bodyY = mid + 0.5f + BODY_NUDGE[look()];
		Component head = MenuFont.shadowSmall(t.source);
		GuiDraw.text(graphics, font, head, tx, headY, t.kind == Kind.OK ? muted : tone, false);
		Component now = MenuFont.shadowSmall("now");
		float right = x + W - (Looks.uid() ? UidLook.frameInset(graphics) + 4f : 8f);
		GuiDraw.text(graphics, font, now, right - font.width(now), headY, muted, false);
		String body = t.body;
		float max = right - tx;
		Component line = MenuFont.shadow(body);
		while (font.width(line) > max && body.length() > 2) {
			body = body.substring(0, body.length() - 2);
			line = MenuFont.shadow(body + "…");
		}
		GuiDraw.text(graphics, font, line, tx, bodyY, text, false);
	}

	/** Shadow, uid?, Expressive: how far each font's ink sits from its 9-tall line box. */
	private static final float[] HEAD_NUDGE = {-1f, 0f, -1f};
	private static final float[] BODY_NUDGE = {-1f, 0f, -1f};

	private static int look() {
		return Looks.uid() ? 1 : Looks.ex() ? 2 : 0;
	}
}
