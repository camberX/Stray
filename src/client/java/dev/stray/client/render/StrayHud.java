package dev.stray.client.render;

import dev.stray.Stray;
import dev.stray.client.farming.FarmingHud;
import dev.stray.client.hunting.LassoReel;
import dev.stray.client.mining.CrystalHollowsMap;
import dev.stray.client.mining.CrystalHollowsRenderer;
import dev.stray.client.mining.MetalDetector;
import dev.stray.client.movement.CommandRings;
import dev.stray.client.net.LobbyPings;
import dev.stray.client.update.UpdateToast;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One HUD layer after the vanilla bars / boss bar. Attaching a layer to chat,
 * hotbar, or scoreboard made Fabric wrap those vanilla extracts and Spark
 * billed Stray's overlays (and empty before-hooks) as fabric-rendering-v1.
 */
public final class StrayHud {
	private StrayHud() {
	}

	/** Vanilla's chat renderer, moved so it draws before (under) the action bar. */
	private static net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement chat;

	public static void init() {
		// The chat draws after the action bar in vanilla, so long chat covered it. Move it in
		// front of the action bar instead: same renderer, drawn first.
		HudElementRegistry.replaceElement(VanillaHudElements.CHAT, original -> {
			chat = original;
			return (graphics, delta) -> {
			};
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, Stray.id("chat_under_action_bar"), (graphics, delta) -> {
			if (chat == null) {
				return;
			}
			net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
			// The world editor hides chat lines, until chat is opened.
			if (dev.stray.client.EditionGate.editorActive() && !(client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) {
				return;
			}
			chat.extractRenderState(graphics, delta);
		});
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.BOSS_BAR,
			Stray.id("hud"),
			StrayHud::extract
		);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (dev.stray.client.EditionGate.editorActive()) {
			// The editor workspace replaces the HUD while it is open; drawn outside the HUD pass so
			// it keeps the menu font.
			dev.stray.client.editor.EditorHud.extract(graphics, delta);
			return;
		}
		HudChrome.beginHud();
		try {
			extractHud(graphics, delta);
		} finally {
			HudChrome.endHud();
		}
	}

	private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		HudChrome.element(null);
		VanillaHud.extract(graphics, delta);
		HudChrome.element(null);
		dev.stray.client.dungeon.F7Titles.render(graphics, graphics.guiWidth(), graphics.guiHeight());
		HudChrome.element(null);
		FarmingHud.extract(graphics, delta);
		HudChrome.element(null);
		NametagRenderer.extract(graphics, delta);
		HudChrome.element(null);
		PestEspRenderer.extract(graphics);
		HudChrome.element(null);
		EntityBoxEsp.extract(graphics, delta);
		HudChrome.element(null);
		EntityHealthBars.extract(graphics, delta);
		HudChrome.element(null);
		CrystalHollowsRenderer.extract(graphics, delta);
		HudChrome.element(null);
		LobbyPings.extract(graphics, delta);
		HudChrome.element(null);
		CrystalHollowsMap.extract(graphics, delta);
		HudChrome.element(null);
		MetalDetector.extract(graphics, delta);
		HudChrome.element(null);
		BlockMarks.extract(graphics, delta);
		HudChrome.element(null);
		dev.stray.client.dungeon.TerminalHighlight.extract(graphics, delta);
		HudChrome.element(null);
		dev.stray.client.dungeon.MaskTimers.extract(graphics);
		HudChrome.element(null);
		dev.stray.client.dungeon.m4.M4Hud.extract(graphics);
		HudChrome.element(null);
		if (!dev.stray.Edition.LEGIT) { dev.stray.client.item.AutoHotbar.extract(graphics); }
		HudChrome.element(null);
		if (!dev.stray.Edition.LEGIT) { CommandRings.extract(graphics, delta); }
		HudChrome.element(null);
		HudChrome.element(null);
		dev.stray.client.editor.EditorHud.extract(graphics, delta);
		HudChrome.element(null);
		WatermarkRenderer.extract(graphics, delta);
		HudChrome.element(null);
		ArrayListHud.extract(graphics, delta);
		HudChrome.element(null);
		MusicHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		PipHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		TopDownHudRenderer.extract(graphics, delta);
		// Dungeon map (dev.stray.client.dungeon.map).
		dev.stray.client.dungeon.map.DungeonMapRenderer.extract(graphics, delta);
		HudChrome.element(null);
		RawmatsHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		PickupLogRenderer.extract(graphics, delta);
		HudChrome.element(null);
		InventoryHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		NodeHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		MiningHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		JacobContestHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		ComposterHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		GardenHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		LassoReel.extract(graphics);
		HudChrome.element(null);
		SkillProgressHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		SlayerHudRenderer.extract(graphics, delta);
		HudChrome.element(null);
		dev.stray.client.slayer.SlayerWorld.extract(graphics);
		HudChrome.element(null);
		UpdateToast.extract(graphics);
	}
}
