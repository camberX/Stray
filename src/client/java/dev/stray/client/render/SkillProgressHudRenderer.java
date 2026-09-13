package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.skill.SkillKind;
import dev.stray.client.skill.SkillProgressTracker;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;
import java.util.Locale;

public final class SkillProgressHudRenderer {
	private static final float PAD = 5f;
	private static final float ICON = 16f;
	private static final float HEIGHT = 26f;
	private static final NumberFormat AMOUNT = NumberFormat.getIntegerInstance(Locale.US);

	private SkillProgressHudRenderer() {
	}

	public static void init() {
	}

	public static float drawWidth() {
		return widthOf(SkillProgressTracker.snapshot());
	}

	public static float drawHeight() {
		return HEIGHT;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui || !StrayConfig.get().skillProgressHudEnabled) {
			return;
		}
		SkillProgressTracker.Snapshot snap = SkillProgressTracker.snapshot();
		if (!snap.present() && !HudLayout.editorOpen()) {
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.SKILL, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client.font, client.player, box.x(), box.y(), HudLayout.scale(HudLayout.Id.SKILL), snap);
	}

	public static void draw(
		GuiGraphicsExtractor graphics,
		Font font,
		LocalPlayer player,
		float x,
		float y,
		float scale,
		SkillProgressTracker.Snapshot value
	) {
		SkillProgressTracker.Snapshot snap = value.present() ? value : sample();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1f) {
			graphics.pose().scale(scale, scale);
		}
		float width = widthOf(snap);
		HudChrome.panel(graphics, 0, 0, width, HEIGHT, 5, Theme.HUD_WINDOW, Theme.HUD_LINE);
		ItemStack icon = snap.kind().icon();
		float iconX = PAD;
		float iconY = (HEIGHT - ICON) * 0.5f;
		if (player != null && icon != null && !icon.isEmpty()) {
			graphics.item(player, icon, Math.round(iconX), Math.round(iconY), 310);
		}
		String xp = AMOUNT.format(snap.current()) + "/" + AMOUNT.format(snap.needed());
		GuiDraw.small(graphics, font, xp, PAD + ICON + 4, (HEIGHT - 8) * 0.5f, Theme.TEXT);
		graphics.pose().popMatrix();
	}

	private static float widthOf(SkillProgressTracker.Snapshot snap) {
		SkillProgressTracker.Snapshot value = snap.present() ? snap : sample();
		String xp = AMOUNT.format(value.current()) + "/" + AMOUNT.format(value.needed());
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		return PAD + ICON + 4 + GuiDraw.smallWidth(font, xp) + PAD;
	}

	private static SkillProgressTracker.Snapshot sample() {
		return new SkillProgressTracker.Snapshot(true, SkillKind.FARMING, 12_345L, 20_000L, 14, 15, System.currentTimeMillis());
	}
}
