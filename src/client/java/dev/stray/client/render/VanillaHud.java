package dev.stray.client.render;

import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.LoadoutsScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;

public final class VanillaHud {
	private VanillaHud() {
	}

	public static void init() {
		attach(VanillaHudElements.HOTBAR, "hud/hotbar", (graphics, delta) -> {
			if (customHotbar()) {
				HotbarHudRenderer.extract(graphics, delta);
			}
		});
		attach(VanillaHudElements.HEALTH_BAR, "hud/health", (graphics, delta) -> {
			if (customHealth()) {
				StatusHudRenderer.extractHealth(graphics, delta);
			}
		});
		attach(VanillaHudElements.FOOD_BAR, "hud/hunger", (graphics, delta) -> {
			if (customHunger()) {
				StatusHudRenderer.extractHunger(graphics, delta);
			}
		});
		attach(VanillaHudElements.ARMOR_BAR, "hud/armor", (graphics, delta) -> {
			if (customArmor()) {
				StatusHudRenderer.extractArmor(graphics, delta);
			}
		});
		attach(VanillaHudElements.AIR_BAR, "hud/air", (graphics, delta) -> {
			if (customAir()) {
				StatusHudRenderer.extractAir(graphics, delta);
			}
		});
		attach(VanillaHudElements.MOUNT_HEALTH, "hud/mount", (graphics, delta) -> {
			if (customMount()) {
				StatusHudRenderer.extractMount(graphics, delta);
			}
		});
		attach(VanillaHudElements.INFO_BAR, "hud/experience", (graphics, delta) -> {
			if (customExperience()) {
				StatusHudRenderer.extractExperience(graphics, delta);
			}
		});
		attach(VanillaHudElements.SCOREBOARD, "hud/scoreboard", (graphics, delta) -> {
			if (customScoreboard()) {
				ScoreboardHudRenderer.extract(graphics, delta);
			}
		});
		attach(VanillaHudElements.BOSS_BAR, "hud/boss", (graphics, delta) -> {
			if (customBossBar()) {
				BossBarHudRenderer.extract(graphics, delta);
			}
		});
		attach(VanillaHudElements.MOB_EFFECTS, "hud/effects", (graphics, delta) -> {
			if (customEffects()) {
				EffectsHudRenderer.extract(graphics, delta);
			}
		});
		attach(VanillaHudElements.HELD_ITEM_TOOLTIP, "hud/held_item", (graphics, delta) -> {
			if (customHeldItem()) {
				HeldItemHudRenderer.extract(graphics, delta);
			}
		});
	}

	private static void attach(Identifier vanilla, String path, HudElement element) {
		HudElementRegistry.attachElementBefore(vanilla, Stray.id(path), element);
	}

	public static boolean hidden() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return true;
		}
		return (client.screen instanceof AbstractContainerScreen<?> || client.screen instanceof LoadoutsScreen)
			&& !HudLayout.editorOpen();
	}

	public static boolean spectator() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.isSpectator();
	}

	public static boolean survivalBars() {
		Minecraft client = Minecraft.getInstance();
		return client.gameMode != null && client.gameMode.canHurtPlayer() && !spectator();
	}

	public static boolean hasExperience() {
		Minecraft client = Minecraft.getInstance();
		return client.gameMode != null && client.gameMode.hasExperience() && !spectator();
	}

	public static boolean customHotbar() {
		return custom(StrayConfig.get().hudHotbar) && !spectator();
	}

	public static boolean customHealth() {
		return custom(StrayConfig.get().hudHealth);
	}

	public static boolean customHunger() {
		return custom(StrayConfig.get().hudHunger);
	}

	public static boolean customArmor() {
		return custom(StrayConfig.get().hudArmor);
	}

	public static boolean customAir() {
		return custom(StrayConfig.get().hudAir);
	}

	public static boolean customMount() {
		return custom(StrayConfig.get().hudMountHealth);
	}

	public static boolean customExperience() {
		return custom(StrayConfig.get().hudExperience) && !jumpBar();
	}

	public static boolean customScoreboard() {
		return custom(StrayConfig.get().hudScoreboard);
	}

	public static boolean customBossBar() {
		return custom(StrayConfig.get().hudBossBar);
	}

	public static boolean customEffects() {
		return custom(StrayConfig.get().hudEffects);
	}

	public static boolean customHeldItem() {
		return custom(StrayConfig.get().hudHeldItem);
	}

	public static LivingEntity mount() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		Entity vehicle = player.getVehicle();
		while (vehicle != null) {
			if (vehicle instanceof LivingEntity living && living.showVehicleHealth() && living.getMaxHealth() > 1f) {
				return living;
			}
			vehicle = vehicle.getVehicle();
		}
		return null;
	}

	public static float hotbarTop(int guiH) {
		if (StrayConfig.get().hudHotbar && !spectator()) {
			return guiH - HotbarHudRenderer.HEIGHT - 3;
		}
		return guiH - 22;
	}

	private static boolean custom(boolean enabled) {
		return enabled && !hidden();
	}

	public static boolean jumpBar() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		Entity vehicle = player.getControlledVehicle();
		return vehicle instanceof PlayerRideableJumping jumping && jumping.canJump();
	}
}
