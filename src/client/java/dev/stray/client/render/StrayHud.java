package dev.stray.client.render;

import dev.stray.Stray;
import dev.stray.client.combat.Hitmarker;
import dev.stray.client.farming.FarmingHud;
import dev.stray.client.hunting.LassoReel;
import dev.stray.client.mining.CrystalHollowsMap;
import dev.stray.client.mining.CrystalHollowsRenderer;
import dev.stray.client.mining.MetalDetector;
import dev.stray.client.movement.CommandRings;
import dev.stray.client.movement.MovementRings;
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

	public static void init() {
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.BOSS_BAR,
			Stray.id("hud"),
			StrayHud::extract
		);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
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
		Hitmarker.extract(graphics, delta);
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
		CommandRings.extract(graphics, delta);
		HudChrome.element(null);
		MovementRings.extract(graphics, delta);
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
		UpdateToast.extract(graphics);
	}
}
