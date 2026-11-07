package dev.stray.client.dungeon;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudLayout;
import dev.stray.client.util.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bonzo Mask, Spirit Mask and Phoenix Pet cooldowns, after NoammAddons' MaskTimers (CC0) as it
 * was before masks off cooldown were hidden in every style. Only the Zyryon look is kept: one line
 * per mask, always shown, with a green arrow while that mask (or pet) is worn and "Ready" once it
 * is off cooldown. Optional invulnerability timer, proc / ready titles and a party message.
 */
public final class MaskTimers {
	private static final Pattern LORE_COOLDOWN = Pattern.compile("^Cooldown: ([\\d.]+)s$");
	private static final Pattern SUMMON = Pattern.compile("^You (summoned|despawned) your (.+?)(?: ✦)?!$");
	private static final Pattern AUTOPET = Pattern.compile("^Autopet equipped your \\[Lvl \\d+] (.+?)! VIEW RULE$");
	private static final Pattern PET_MENU = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Pets(?: \\(\\d+/\\d+\\))?$");
	private static final float LINE = 10f;

	private enum Mask {
		BONZO("Bonzo", "Mask", "§9", 180_000L, 3_000L, Pattern.compile("^Your (?:.+ )?Bonzo's Mask saved your life!$")),
		SPIRIT("Spirit", "Mask", "§f", 30_000L, 3_000L, Pattern.compile("^Second Wind Activated! Your Spirit Mask saved your life!$")),
		PHOENIX("Phoenix", "Pet", "§c", 60_000L, 4_000L, Pattern.compile("^Your Phoenix Pet saved you from certain death!$"));

		final String name;
		final String suffix;
		final String color;
		final long cooldownMs;
		final long invulnMs;
		final Pattern proc;

		long readyAt;
		long invulnUntil;
		boolean worn;
		boolean notifiedReady = true;

		Mask(String name, String suffix, String color, long cooldownMs, long invulnMs, Pattern proc) {
			this.name = name;
			this.suffix = suffix;
			this.color = color;
			this.cooldownMs = cooldownMs;
			this.invulnMs = invulnMs;
			this.proc = proc;
		}

		void reset() {
			readyAt = 0L;
			invulnUntil = 0L;
			worn = false;
			notifiedReady = true;
		}
	}

	/** Name of the summoned pet, from chat and the Pets menu; empty when none or not known yet. */
	private static String pet = "";

	private MaskTimers() {
	}

	private static boolean shown() {
		StrayConfig config = StrayConfig.get();
		return config.maskTimers && SkyblockLocation.inSkyblock && (!config.maskTimersDungeonsOnly || SkyblockLocation.inDungeon);
	}

	// ------------------------------------------------------------------ input

	public static void tick(Minecraft client) {
		if (!StrayConfig.get().maskTimers || client.player == null || !SkyblockLocation.inSkyblock) {
			return;
		}
		readPetMenu(client);
		String head = ItemIds.skyblockId(client.player.getItemBySlot(EquipmentSlot.HEAD));
		Mask.BONZO.worn = head != null && head.contains("BONZO_MASK");
		Mask.SPIRIT.worn = head != null && head.contains("SPIRIT_MASK");
		Mask.PHOENIX.worn = pet.contains("Phoenix");

		long now = System.currentTimeMillis();
		for (Mask mask : Mask.values()) {
			if (mask.readyAt > now) {
				mask.notifiedReady = false;
			} else if (!mask.notifiedReady) {
				mask.notifiedReady = true;
				if (StrayConfig.get().maskTimersReadyTitle && shown()) {
					title(mask.color + mask.name + " is Ready!");
				}
			}
		}
	}

	public static void onChat(String plain) {
		if (plain == null) {
			return;
		}
		Matcher summon = SUMMON.matcher(plain);
		if (summon.matches()) {
			pet = summon.group(1).equals("summoned") ? summon.group(2) : "";
			return;
		}
		Matcher autopet = AUTOPET.matcher(plain);
		if (autopet.matches()) {
			pet = autopet.group(1);
			return;
		}
		if (!shown()) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		Minecraft client = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		for (Mask mask : Mask.values()) {
			if (!mask.proc.matcher(plain).matches()) {
				continue;
			}
			long cooldown = mask.cooldownMs;
			if (mask == Mask.BONZO && client.player != null) {
				// Bonzo's cooldown depends on the mask's stars; the helmet's lore says how long.
				long fromLore = loreCooldown(client.player.getItemBySlot(EquipmentSlot.HEAD));
				if (fromLore > 0L) {
					cooldown = fromLore;
				}
			}
			mask.readyAt = now + cooldown;
			mask.notifiedReady = false;
			if (config.maskTimersInvuln) {
				mask.invulnUntil = now + mask.invulnMs;
			}
			if (config.maskTimersProcTitle) {
				title(mask.color + mask.name + " Procced!");
			}
			if (config.maskTimersPartyMessage && client.player != null) {
				client.player.connection.sendCommand("pc Popped " + mask.name + " " + mask.suffix + "!");
			}
		}
	}

	public static void reset() {
		for (Mask mask : Mask.values()) {
			mask.reset();
		}
	}

	/** In the Pets menu, the pet whose lore says "Click to despawn!" is the one summoned. */
	private static void readPetMenu(Minecraft client) {
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		if (!PET_MENU.matcher(Text.strip(screen.getTitle().getString()).trim()).matches()) {
			return;
		}
		String found = null;
		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !loreContains(stack, "click to despawn!")) {
				continue;
			}
			found = Text.strip(stack.getHoverName().getString());
			break;
		}
		if (found != null) {
			pet = found;
		}
	}

	private static long loreCooldown(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return 0L;
		}
		for (Component line : lore.lines()) {
			Matcher m = LORE_COOLDOWN.matcher(Text.strip(line.getString()).trim());
			if (m.matches()) {
				try {
					return Math.round(Double.parseDouble(m.group(1)) * 1000.0);
				} catch (NumberFormatException ignored) {
					return 0L;
				}
			}
		}
		return 0L;
	}

	private static boolean loreContains(ItemStack stack, String needle) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		for (Component line : lore.lines()) {
			if (Text.strip(line.getString()).toLowerCase(Locale.ROOT).contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private static void title(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null) {
			return;
		}
		client.gui.hud.setTimes(5, 40, 10);
		client.gui.hud.setSubtitle(Component.empty());
		client.gui.hud.setTitle(Component.literal(text));
	}

	// ------------------------------------------------------------------ drawing

	private static String line(Mask mask, boolean example, long now) {
		long left = example ? mask.cooldownMs / 2 : mask.readyAt - now;
		String arrow = mask.worn || example ? "§a>" : "§c>";
		if (left > 0L) {
			return mask.color + mask.name + " " + arrow + " §e" + String.format(Locale.ROOT, "%.2f", left / 1000.0);
		}
		return mask.color + mask.name + " " + arrow + " §aReady";
	}

	public static float width(Font font) {
		boolean example = HudLayout.editorOpen();
		long now = System.currentTimeMillis();
		int max = 0;
		for (Mask mask : Mask.values()) {
			max = Math.max(max, font.width(line(mask, example, now)));
		}
		return max;
	}

	public static float height() {
		return Mask.values().length * LINE;
	}

	public static void extract(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!StrayConfig.get().maskTimers || client.player == null || client.gui.hud.isHidden()) {
			return;
		}
		boolean example = HudLayout.editorOpen();
		if (!example && !shown()) {
			return;
		}
		Font font = client.font;
		long now = System.currentTimeMillis();
		HudLayout.apply(graphics, font, HudLayout.Id.MASK_TIMERS, () -> {
			float y = 0f;
			for (Mask mask : Mask.values()) {
				GuiDraw.text(graphics, font, Component.literal(line(mask, example, now)), 0f, y, 0xFFFFFFFF, true);
				y += LINE;
			}
		});
		if (StrayConfig.get().maskTimersInvuln && !example) {
			invulnerability(graphics, font, now);
		}
	}

	/** The longest running invulnerability, big in the middle of the screen. */
	private static void invulnerability(GuiGraphicsExtractor graphics, Font font, long now) {
		Mask active = null;
		for (Mask mask : Mask.values()) {
			if (mask.invulnUntil > now && (active == null || mask.invulnUntil > active.invulnUntil)) {
				active = mask;
			}
		}
		if (active == null) {
			return;
		}
		long left = active.invulnUntil - now;
		String color = left < 1000L ? "§c" : "§a";
		Component text = Component.literal(active.color + active.name + ": " + color + String.format(Locale.ROOT, "%.1f", left / 1000.0));
		float scale = 1.5f;
		float x = graphics.guiWidth() * 0.5f - font.width(text) * scale * 0.5f;
		float y = graphics.guiHeight() / 3f;
		GuiDraw.text(graphics, font, text, x, y, scale, 0xFFFFFFFF, true);
	}
}
