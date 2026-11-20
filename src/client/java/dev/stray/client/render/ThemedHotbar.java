package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.ExColors;
import dev.stray.client.ui.ExLook;
import dev.stray.client.ui.Looks;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.ShadowLook;
import dev.stray.client.ui.Theme;
import dev.stray.client.ui.UidLook;
import dev.stray.client.ui.UidMenu;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The hotbar, its XP bar and level drawn in the theme's look instead of vanilla's sprites:
 * <ul>
 * <li>Shadow: a frosted panel of faint rounded slots, the picked one ringed in the accent; XP is
 * a thin glowing accent line along its bottom and the level a small pill above.</li>
 * <li>uid?: the watermark's banded frame around square slots; its colour strip is the XP bar
 * (full colour up to your progress, grey after); the level in a small uid? box above.</li>
 * <li>Expressive: a tonal pill of rounded slots, the picked one a scalloped shape in primary
 * container; XP is Material's wavy progress line above it, the level a primary pill.</li>
 * </ul>
 * Items, counts, durability and the pick-up "pop" are vanilla's; the picked slot glides with
 * the smooth hotbar. Hearts, food and armour stay vanilla, above it.
 */
public final class ThemedHotbar {
	/** The level last drawn, to pop the Expressive pill on a level up. */
	private static int lastLevel = -1;

	private static final float SLOT = 18f;
	private static final float PITCH = 20f;
	private static long startNs = System.nanoTime();

	private ThemedHotbar() {
	}

	public static boolean active() {
		StrayConfig config = StrayConfig.get();
		Minecraft client = Minecraft.getInstance();
		return config != null && config.themedHotbar && client.player != null && client.gameMode != null
			&& !client.player.isSpectator();
	}

	/** Where the picked slot is drawn: the smooth hotbar's glide, in slot steps. */
	private static float picked(Player player) {
		int selected = player.getInventory().getSelectedSlot();
		float offset = SmoothHotbar.offset(selected);
		return selected + offset / 20f;
	}

	private static float seconds() {
		return (System.nanoTime() - startNs) / 1.0e9f;
	}

	public static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta, Player player) {
		Theme.refresh();
		if (Looks.uid()) {
			uid(graphics, delta, player);
		} else if (Looks.ex()) {
			ex(graphics, delta, player);
		} else if (Looks.panels()) {
			panels(graphics, delta, player);
		} else {
			shadow(graphics, delta, player);
		}
	}

	// ------------------------------------------------------------------ Shadow

	private static void shadow(GuiGraphicsExtractor graphics, DeltaTracker delta, Player player) {
		Minecraft client = Minecraft.getInstance();
		float pad = 3f;
		float w = 9 * PITCH - (PITCH - SLOT) + pad * 2f;
		float h = SLOT + pad * 2f + 3f;
		float x = Math.round(graphics.guiWidth() / 2f - w / 2f);
		float y = graphics.guiHeight() - h - 1f;
		ShadowLook.panel(graphics, x, y, w, h, 8f, Theme.HUD_WINDOW);
		float sel = picked(player);
		for (int i = 0; i < 9; i++) {
			GuiDraw.roundedFine(graphics, x + pad + i * PITCH, y + pad, SLOT, SLOT, 4f, 0x0EFFFFFF);
		}
		float sx = x + pad + sel * PITCH;
		GuiDraw.roundedFine(graphics, sx, y + pad, SLOT, SLOT, 4f, 0x22FFFFFF);
		GuiDraw.hairline(graphics, sx, y + pad, SLOT, SLOT, 4f, Theme.ACCENT);
		items(graphics, delta, player, x + pad + 1f, y + pad + 1f);
		// XP: a thin accent line with a soft glow along the bottom.
		float lx = x + 5f;
		float lw = w - 10f;
		float ly = y + h - 3.5f;
		float p = xp(player);
		GuiDraw.roundedFine(graphics, lx, ly, lw, 1.5f, 0.75f, 0x1AFFFFFF);
		if (p > 0f) {
			ShadowLook.glow(graphics, lx, ly, lw * p, 1.5f, 0.75f, 4, Theme.withAlpha(Theme.ACCENT, 0x70));
			GuiDraw.roundedFine(graphics, lx, ly, lw * p, 1.5f, 0.75f, Theme.ACCENT);
		}
		int level = level(player);
		if (level > 0) {
			Component text = MenuFont.shadow(Integer.toString(level));
			float tw = client.font.width(text);
			float pw = tw + 10f;
			float ph = 10f;
			float px = Math.round(graphics.guiWidth() / 2f - pw / 2f);
			float py = y - ph - 2f;
			ShadowLook.panel(graphics, px, py, pw, ph, ph / 2f, Theme.HUD_WINDOW);
			GuiDraw.text(graphics, client.font, text, px + 5f, GuiDraw.middle(py, ph), Theme.ACCENT, false);
		}
		offhand(graphics, delta, player, x, y, w, h, (ox, oy, os) -> {
			ShadowLook.panel(graphics, ox, oy, os, os, 6f, Theme.HUD_WINDOW);
		});
	}

	// ------------------------------------------------------------------ Panels

	private static void panels(GuiGraphicsExtractor graphics, DeltaTracker delta, Player player) {
		Minecraft client = Minecraft.getInstance();
		float pad = 3f;
		float w = 9 * PITCH - (PITCH - SLOT) + pad * 2f;
		float h = SLOT + pad * 2f + 2f;
		float x = Math.round(graphics.guiWidth() / 2f - w / 2f);
		float y = graphics.guiHeight() - h - 1f;
		dev.stray.client.ui.PanelLook.panel(graphics, x, y, w, h, 0xF01C1C1C);
		float sel = picked(player);
		for (int i = 0; i < 9; i++) {
			GuiDraw.roundedFine(graphics, x + pad + i * PITCH, y + pad, SLOT, SLOT, 3f, dev.stray.client.ui.PanelLook.G2);
		}
		float sx = x + pad + sel * PITCH;
		GuiDraw.roundedFine(graphics, sx, y + pad, SLOT, SLOT, 3f, dev.stray.client.ui.PanelLook.G3);
		GuiDraw.hairline(graphics, sx, y + pad, SLOT, SLOT, 3f, dev.stray.client.ui.PanelLook.accent());
		items(graphics, delta, player, x + pad + 1f, y + pad + 1f);
		// XP: one thin accent line along the bottom, grey after your progress.
		float lx = x + pad;
		float lw = w - pad * 2f;
		float ly = y + h - 2.5f;
		float p = xp(player);
		GuiDraw.fillSmooth(graphics, lx, ly, lw, 1f, dev.stray.client.ui.PanelLook.EDGE);
		if (p > 0f) {
			GuiDraw.fillSmooth(graphics, lx, ly, lw * p, 1f, dev.stray.client.ui.PanelLook.accent());
		}
		int level = level(player);
		if (level > 0) {
			Component text = MenuFont.shadow(Integer.toString(level));
			float tw = client.font.width(text);
			float lvx = Math.round(graphics.guiWidth() / 2f - tw / 2f);
			float ty = y - 9f;
			GuiDraw.text(graphics, client.font, text, lvx + 1f, ty + 1f, 0xFF000000, false);
			GuiDraw.text(graphics, client.font, text, lvx, ty, dev.stray.client.ui.PanelLook.accent(), false);
		}
		offhand(graphics, delta, player, x, y, w, h, (ox, oy, os) -> dev.stray.client.ui.PanelLook.panel(graphics, ox, oy, os, os, 0xF01C1C1C));
	}

	// ------------------------------------------------------------------ uid?

	private static void uid(GuiGraphicsExtractor graphics, DeltaTracker delta, Player player) {
		Minecraft client = Minecraft.getInstance();
		float px = GuiDraw.pixel();
		float band = px * WatermarkRenderer.uidBandPx();
		float frame = band * 7f;
		float barH = 2f * px * WatermarkRenderer.uidBarPx();
		float pad = 2f * px;
		float gap = 2f * px;
		float slot = SLOT;
		float pitch = slot + gap;
		float w = frame * 2f + pad * 2f + 9 * slot + 8 * gap;
		float h = frame * 2f + barH + pad * 2f + slot;
		float x = Math.round(graphics.guiWidth() / 2f - w / 2f);
		float y = graphics.guiHeight() - h;
		float in = UidLook.frameOnly(graphics, x, y, w, h, 0xF0);
		// The colour strip is the XP bar: colour up to your progress, grey after, lower half darker.
		float bx = x + in;
		float bw = w - in * 2f;
		float p = xp(player);
		float fill = bw * p;
		// The whole strip in colour, the part past your progress dimmed so it still reads as the strip.
		UidLook.rainbow(graphics, bx, y + in, bw, barH / 2f);
		if (fill < bw) {
			GuiDraw.fillSmooth(graphics, bx + fill, y + in, bw - fill, barH, 0x99000000);
		}
		float sy = y + in + barH + pad;
		float sx0 = x + in + pad;
		float sel = picked(player);
		int selected = player.getInventory().getSelectedSlot();
		for (int i = 0; i < 9; i++) {
			UidLook.control(graphics, sx0 + i * pitch, sy, slot, slot, false, false);
		}
		float selX = sx0 + sel * pitch;
		UidLook.control(graphics, selX, sy, slot, slot, false, true);
		GuiDraw.fillSmooth(graphics, selX + px, sy + slot - 3f * px, slot - 2f * px, 2f * px, UidMenu.accent());
		itemsAt(graphics, delta, player, sx0 + 1f, sy + 1f, pitch);
		int level = level(player);
		if (level > 0) {
			Component num = MenuFont.shadow(Integer.toString(level));
			float tw = client.font.width(num);
			// Just the number, in the gap between hearts and food where vanilla puts the level.
			float lx = Math.round(graphics.guiWidth() / 2f - tw / 2f);
			float ty = y - 9f;
			GuiDraw.text(graphics, client.font, num, lx + px, ty + px, 0xFF000000, false);
			GuiDraw.text(graphics, client.font, num, lx, ty, UidMenu.accent(), false);
		}
		if (selected < 0) {
			return;
		}
		offhand(graphics, delta, player, x, y, w, h, (ox, oy, os) -> UidLook.frameOnly(graphics, ox, oy, os, os, 0xF0));
	}

	// ------------------------------------------------------------------ Expressive

	private static void ex(GuiGraphicsExtractor graphics, DeltaTracker delta, Player player) {
		Minecraft client = Minecraft.getInstance();
		float pad = 3f;
		float slot = SLOT + 1f;
		float gap = 2f;
		float pitch = slot + gap;
		float w = 9 * slot + 8 * gap + pad * 2f;
		float h = slot + pad * 2f;
		float x = Math.round(graphics.guiWidth() / 2f - w / 2f);
		float y = graphics.guiHeight() - h - 1f;
		ShadowLook.shadow(graphics, x, y, w, h, h / 2f, 6, 0x40);
		ExLook.rounded(graphics, x, y, w, h, h / 2f, ExColors.alpha(ExColors.surfaceMid, Math.max(0.9f, ExColors.opacity())));
		float sel = picked(player);
		for (int i = 0; i < 9; i++) {
			float r = i == 0 || i == 8 ? slot / 2f : 6f;
			float sx = x + pad + i * pitch;
			if (i == 0) {
				ExLook.sides(graphics, sx, y + pad, slot, slot, r, 6f, ExColors.surfaceHigh);
			} else if (i == 8) {
				ExLook.sides(graphics, sx, y + pad, slot, slot, 6f, r, ExColors.surfaceHigh);
			} else {
				ExLook.rounded(graphics, sx, y + pad, slot, slot, r, ExColors.surfaceHigh);
			}
		}
		// The selection slides to the new slot on a bouncy spring, squashing a little on the way.
		float at = dev.stray.client.ui.ExMotion.spring("ex-hb-sel", sel, dev.stray.client.ui.ExMotion.SPATIAL_FAST);
		float travel = Math.min(1f, Math.abs(at - sel));
		cookie(graphics, x + pad + at * pitch + slot / 2f, y + pad + slot / 2f, (slot / 2f + 1f) * (1f - 0.12f * travel), ExColors.primaryContainer);
		itemsAt(graphics, delta, player, x + pad + 1.5f, y + pad + 1.5f, pitch);
		// XP: Material's wavy progress line above the pill, then a flat track and a stop dot.
		// The line grows as XP comes in (from empty again after a level up) instead of jumping.
		int lvNow = level(player);
		if (lvNow != lastLevel) {
			if (lastLevel >= 0 && lvNow > lastLevel) {
				dev.stray.client.ui.ExMotion.snap("ex-hb-xp", 0f);
				dev.stray.client.ui.ExMotion.snap("ex-hb-lv", 1.35f);
			}
			lastLevel = lvNow;
		}
		float p = Math.max(0f, Math.min(1f, dev.stray.client.ui.ExMotion.spring("ex-hb-xp", xp(player), dev.stray.client.ui.ExMotion.EFFECTS_SLOW)));
		float lw = w - 36f;
		float lx = x + 18f;
		// In the gap between the pill and vanilla's hearts (which sit 30 above the bottom).
		float ly = y - 2.5f;
		float fill = lw * p;
		float phase = seconds() * 6f;
		if (fill > 3f) {
			// The same points as before (one per unit), drawn as one ribbon.
			int count = 1 + (int) Math.floor(fill - 1.5f);
			float[] pts = new float[count * 2];
			for (int i = 0; i < count; i++) {
				pts[i * 2] = lx + i;
				pts[i * 2 + 1] = ly + (float) Math.sin(i / 7f * Math.PI * 2 + phase) * 1.1f;
			}
			GuiDraw.polyline(graphics, pts, count, 1.6f, ExColors.primary);
		}
		if (fill < lw - 4f) {
			GuiDraw.stroke(graphics, lx + fill + 3f, ly, lx + lw - 2f, ly, 1.6f, ExColors.secondaryContainer);
		}
		GuiDraw.circle(graphics, lx + lw - 1f, ly, 0.9f, ExColors.primary);
		int level = level(player);
		if (level > 0) {
			Component text = MenuFont.shadowTitle(Integer.toString(level));
			float tw = client.font.width(text);
			float pw = tw + 10f;
			float ph = 11f;
			float px = Math.round(graphics.guiWidth() / 2f - pw / 2f);
			float py = ly - ph - 2f;
			// A level up pops the pill and lets it spring back.
			float pop = dev.stray.client.ui.ExMotion.spring("ex-hb-lv", 1f, dev.stray.client.ui.ExMotion.SPATIAL_FAST);
			graphics.pose().pushMatrix();
			graphics.pose().translate(px + pw / 2f, py + ph / 2f);
			graphics.pose().scale(pop, pop);
			graphics.pose().translate(-(px + pw / 2f), -(py + ph / 2f));
			ExLook.rounded(graphics, px, py, pw, ph, ph / 2f, ExColors.primary);
			// Digits have no descenders: centred on the line box they sit high, so a unit lower
			// (measured: 3 px over them and 7 under before, even after).
			GuiDraw.text(graphics, client.font, text, px + 5f, GuiDraw.middle(py, ph) + 1.25f, ExColors.onPrimary, false);
			graphics.pose().popMatrix();
		}
		offhand(graphics, delta, player, x, y, w, h, (ox, oy, os) -> {
			ShadowLook.shadow(graphics, ox, oy, os, os, os / 2f, 6, 0x40);
			ExLook.rounded(graphics, ox, oy, os, os, os / 2f, ExColors.alpha(ExColors.surfaceMid, Math.max(0.9f, ExColors.opacity())));
		});
	}

	/** Material's scalloped "cookie": a disc with nine round lobes on its edge, turning slowly. */
	private static void cookie(GuiGraphicsExtractor graphics, float cx, float cy, float r, int color) {
		float lobes = 9f;
		float spin = seconds() * 0.4f;
		GuiDraw.circle(graphics, cx, cy, r * 0.84f, color);
		for (int i = 0; i < lobes; i++) {
			double a = spin + i * Math.PI * 2 / lobes;
			GuiDraw.circle(graphics, cx + (float) Math.cos(a) * r * 0.74f, cy + (float) Math.sin(a) * r * 0.74f, r * 0.26f, color);
		}
	}

	// ------------------------------------------------------------------ shared

	@FunctionalInterface
	private interface Tile {
		void draw(float x, float y, float size);
	}

	private static void items(GuiGraphicsExtractor graphics, DeltaTracker delta, Player player, float x, float y) {
		itemsAt(graphics, delta, player, x, y, PITCH);
	}

	private static void itemsAt(GuiGraphicsExtractor graphics, DeltaTracker delta, Player player, float x, float y, float pitch) {
		int seed = 1;
		for (int i = 0; i < 9; i++) {
			slot(graphics, delta, player, player.getInventory().getItem(i), x + i * pitch, y, seed++);
		}
	}

	/** The offhand in a tile of the look, on the side vanilla puts it. */
	private static void offhand(GuiGraphicsExtractor graphics, DeltaTracker delta, Player player, float x, float y, float w, float h, Tile tile) {
		ItemStack offhand = player.getOffhandItem();
		if (offhand.isEmpty()) {
			return;
		}
		float size = Math.min(h, SLOT + 6f);
		float oy = y + h - size;
		float ox = player.getMainArm().getOpposite() == HumanoidArm.LEFT ? x - size - 4f : x + w + 4f;
		tile.draw(ox, oy, size);
		slot(graphics, delta, player, offhand, ox + (size - 16f) / 2f, oy + (size - 16f) / 2f, 10);
	}

	/** One item as vanilla draws it: the pick-up squash, the item, its count and durability. */
	private static void slot(GuiGraphicsExtractor graphics, DeltaTracker delta, Player player, ItemStack stack, float x, float y, int seed) {
		if (stack.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x - Math.round(x), y - Math.round(y));
		int ix = Math.round(x);
		int iy = Math.round(y);
		float pop = stack.getPopTime() - delta.getGameTimeDeltaPartialTick(false);
		if (pop > 0f) {
			float squeeze = 1f + pop / 5f;
			graphics.pose().translate(ix + 8, iy + 12);
			graphics.pose().scale(1f / squeeze, (squeeze + 1f) / 2f);
			graphics.pose().translate(-(ix + 8), -(iy + 12));
		}
		graphics.item(player, stack, ix, iy, seed);
		graphics.pose().popMatrix();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x - ix, y - iy);
		graphics.itemDecorations(font, stack, ix, iy);
		graphics.pose().popMatrix();
	}

	private static float xp(Player player) {
		Minecraft client = Minecraft.getInstance();
		if (client.gameMode == null || !client.gameMode.hasExperience()) {
			return 0f;
		}
		return Math.max(0f, Math.min(1f, player.experienceProgress));
	}

	private static int level(Player player) {
		Minecraft client = Minecraft.getInstance();
		return client.gameMode != null && client.gameMode.hasExperience() ? player.experienceLevel : 0;
	}
}
