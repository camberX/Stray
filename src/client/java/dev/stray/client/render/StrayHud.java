package dev.stray.client.render;

import dev.stray.Stray;
import dev.stray.client.combat.Hitmarker;
import dev.stray.client.farming.FarmingHud;
import dev.stray.client.mining.CrystalHollowsMap;
import dev.stray.client.mining.CrystalHollowsRenderer;
import dev.stray.client.mining.MetalDetector;
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
		VanillaHud.extract(graphics, delta);
		Hitmarker.extract(graphics, delta);
		FarmingHud.extract(graphics, delta);
		NametagRenderer.extract(graphics, delta);
		EntityHealthBars.extract(graphics, delta);
		CrystalHollowsRenderer.extract(graphics, delta);
		CrystalHollowsMap.extract(graphics, delta);
		MetalDetector.extract(graphics, delta);
		WatermarkRenderer.extract(graphics, delta);
		MusicHudRenderer.extract(graphics, delta);
		RawmatsHudRenderer.extract(graphics, delta);
		PickupLogRenderer.extract(graphics, delta);
		InventoryHudRenderer.extract(graphics, delta);
		NodeHudRenderer.extract(graphics, delta);
		MiningHudRenderer.extract(graphics, delta);
		JacobContestHudRenderer.extract(graphics, delta);
		ComposterHudRenderer.extract(graphics, delta);
		UpdateToast.extract(graphics);
	}
}
