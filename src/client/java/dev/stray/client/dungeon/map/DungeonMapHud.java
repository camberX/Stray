package dev.stray.client.dungeon.map;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.render.HudLayout;
import dev.stray.client.render.overlay.Canvas;
import dev.stray.client.render.overlay.GuiCanvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Puts the map on the HUD, or in the streamproof overlay. */
public final class DungeonMapHud {
	private DungeonMapHud() {
	}

	private static boolean example() {
		return DungeonMap.previewActive() || !SkyblockLocation.inDungeon;
	}

	private static boolean showing() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden()) {
			return false;
		}
		return MapRenderer.showing(DungeonMap.previewActive() || HudLayout.editorOpen());
	}

	/** The overlay draws it instead (never while the HUD editor is open, so it can be moved). */
	private static boolean streamproof() {
		return !dev.stray.Edition.LEGIT && StrayConfig.get().dungeonMapStreamproof && !HudLayout.editorOpen();
	}

	public static boolean wantsOverlay() {
		return showing() && streamproof();
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!showing() || streamproof()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		float partial = delta.getGameTimeDeltaPartialTick(false);
		GuiCanvas canvas = new GuiCanvas(graphics, client.font);
		HudLayout.apply(graphics, client.font, HudLayout.Id.DUNGEON_MAP, () -> MapRenderer.draw(canvas, example(), partial));
	}

	/** Overlay pass: the canvas is in GUI units already. */
	public static void overlay(Canvas canvas, int guiW, int guiH, float partial) {
		if (!wantsOverlay()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		HudLayout.Box box = HudLayout.box(HudLayout.Id.DUNGEON_MAP, client.font, guiW, guiH);
		float scale = HudLayout.scale(HudLayout.Id.DUNGEON_MAP);
		canvas.push();
		canvas.translate(box.x(), box.y());
		canvas.scale(scale);
		MapRenderer.draw(canvas, example(), partial);
		canvas.pop();
	}
}
