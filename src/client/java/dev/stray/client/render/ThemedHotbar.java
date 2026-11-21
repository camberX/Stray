package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
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
 * <li>Modern: flat grey slots, the picked one outlined in the accent; XP is a thin accent line
 * along the bottom and the level the number in the accent above.</li>
 * <li>Skeet: the watermark's banded frame around square slots; its colour strip is the XP bar
 * (full colour up to your progress, grey after); the level in a small box above.</li>
 * </ul>
 * Minecraft keeps the game's own hotbar.
 * Items, counts, durability and the pick-up "pop" are vanilla's; the picked slot glides with
 * the smooth hotbar. Hearts, food and armour stay vanilla, above it.
 */
public final class ThemedHotbar {

	private static final float SLOT = 18f;
	private static final float PITCH = 20f;
	private static long startNs = System.nanoTime();

	private ThemedHotbar() {
	}

	public static boolean active() {
		StrayConfig config = StrayConfig.get();
		Minecraft client = Minecraft.getInstance();
		// Minecraft: the game's own hotbar, untouched.
		return config != null && config.themedHotbar && !dev.stray.client.ui.Looks.minecraft() && client.player != null && client.gameMode != null
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
		} else {
			panels(graphics, delta, player);
		}
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
