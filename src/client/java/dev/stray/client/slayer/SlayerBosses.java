package dev.stray.client.slayer;

import dev.stray.client.config.SlayerSettings;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-boss logic: kill times and personal bests, boss phases for the slayer HUD, Inferno
 * hellion shields, daggers, fire pits and fire pillars, Tarantula invincibility, the gummy
 * warning, and wrong/missing-quest checks when hitting slayer mobs.
 */
public final class SlayerBosses {
	public enum Shield {
		AURIC("Auric", 0xFFFF55), ASHEN("Ashen", 0x555555), SPIRIT("Spirit", 0xFFFFFF), CRYSTAL("Crystal", 0x55FFFF);

		public final String label;
		public final int rgb;

		Shield(String label, int rgb) {
			this.label = label;
			this.rgb = rgb;
		}
	}

	private enum Dagger {
		TWILIGHT(new String[] {"Twilight Dagger", "Mawdredge Dagger", "Deathripper Dagger"}, Shield.SPIRIT, Shield.CRYSTAL),
		FIREDUST(new String[] {"Firedust Dagger", "Kindlebane Dagger", "Pyrochaos Dagger"}, Shield.ASHEN, Shield.AURIC);

		final String[] names;
		final Shield[] shields;
		Shield active;
		boolean known;

		Dagger(String[] names, Shield first, Shield second) {
			this.names = names;
			this.shields = new Shield[] {first, second};
			this.active = first;
		}

		Dagger other() {
			return this == TWILIGHT ? FIREDUST : TWILIGHT;
		}
	}

	/** One line of the boss phase readout. */
	public record Phase(String text, int rgb) {
	}

	private static final Pattern HITS = Pattern.compile("(\\d+) Hits?");
	private static final Pattern PILLAR = Pattern.compile("^(\\d+(?:\\.\\d+)?)s 8 hits");
	private static final Pattern ATTUNEMENT = Pattern.compile("^Strike using the .+ attunement on your dagger!");
	private static final String HELLION_REDUCED = "Your hit was reduced by Hellion Shield!";
	private static final String HATCHLINGS = "You need to kill the Broodfather's hatchlings before it can be damaged again!";

	private static long spawnMs;
	private static SlayerType spawnType;
	private static int spawnTier;
	private static long lastHealth = -1;
	private static long firePitsAt;
	private static boolean stuck;
	private static double stuckX;
	private static double stuckZ;
	private static long lastAttack;
	private static long lastGummy;
	private static int pillarId = -1;
	private static String pillar;
	private static long pillarSeen;
	private static String tabFooter = "";
	private static long gummyAte;
	private static int tick;

	private SlayerBosses() {
	}

	private static SlayerSettings settings() {
		return StrayConfig.get().slayer;
	}

	// ---------------------------------------------------------------- lifecycle

	/** A new boss fight began (first boss entity or "Slay the boss!"), not every entity swap. */
	public static void onFightStart() {
		spawnMs = SlayerApi.fightStartMs();
		spawnType = SlayerApi.type();
		spawnTier = SlayerApi.tier();
		lastHealth = -1;
	}

	public static void onBossSpawn(LivingEntity boss) {
		stuck = false;
	}

	public static void onBossGone(SlayerType type, int tier, long aliveMs, SlayerApi.State state) {
		stuck = false;
	}

	/** The quest went to "Boss slain": report the kill time. */
	public static void onSlain() {
		if (spawnMs == 0 || spawnType == null) {
			return;
		}
		long took = System.currentTimeMillis() - spawnMs;
		SlayerType type = spawnType;
		int tier = spawnTier;
		spawnMs = 0;
		SlayerSettings s = settings();
		if (!s.alerts || (!s.timeToKill && !s.timePb)) {
			return;
		}
		String name = type.displayName + " " + roman(tier);
		String key = type.name() + " " + tier;
		Long pb = s.personalBests.get(key);
		boolean newPb = pb == null || took < pb;
		if (newPb) {
			s.personalBests.put(key, took);
		}
		String time = formatTime(took);
		if (s.timeToKill) {
			SlayerAlerts.chat(s.timeCompact ? name + " §etook §b" + time : "It took §b" + time + "§e to kill " + name);
		}
		if (s.timePb) {
			if (newPb) {
				SlayerAlerts.chat(pb == null
					? (s.timeCompact ? "§e§lNEW PB! §r§e" + name + " in §a" + time : "§e§lNEW PERSONAL BEST! §a" + time + " §efor " + name)
					: (s.timeCompact ? "§e§lNEW PB! §r§e" + name + " §c" + formatTime(pb) + " §e-> §a" + time
						: "§e§lNEW PERSONAL BEST! §a" + time + " §7(previous " + formatTime(pb) + ") §efor " + name));
			} else {
				SlayerAlerts.chat(name + (s.timeCompact ? " §ePB: §6" : " §epersonal best: §6") + formatTime(pb));
			}
		}
	}

	// ---------------------------------------------------------------- chat, titles, attacks

	/** Returns false to hide the line. */
	public static boolean onChat(String text) {
		SlayerSettings s = settings();
		if (text.startsWith("You ate a Re-heated Gummy Polar Bear")) {
			gummyAte = System.currentTimeMillis();
		}
		if (text.startsWith(HATCHLINGS) && SlayerApi.boss() != null) {
			stuck = true;
			stuckX = SlayerApi.boss().getX();
			stuckZ = SlayerApi.boss().getZ();
		}
		if (s.inferno && s.hideDaggerChat && (ATTUNEMENT.matcher(text).find() || text.startsWith(HELLION_REDUCED))) {
			return false;
		}
		return true;
	}

	/** Titles from the server; returns false to hide them. */
	public static boolean onTitle(Component title) {
		SlayerSettings s = settings();
		if (!s.inferno || !s.daggers || title == null) {
			return true;
		}
		String text = title.getString().replaceAll("§.", "").trim();
		for (Shield shield : Shield.values()) {
			if (text.equals(shield.name())) {
				for (Dagger dagger : Dagger.values()) {
					if (dagger.shields[0] == shield || dagger.shields[1] == shield) {
						dagger.active = shield;
						dagger.known = true;
					}
				}
				return false;
			}
		}
		return true;
	}

	/** Right click with a dagger swaps its attunement (until the server confirms). */
	public static void onUse(ItemStack held) {
		Dagger dagger = dagger(held);
		if (dagger != null) {
			dagger.active = dagger.active == dagger.shields[0] ? dagger.shields[1] : dagger.shields[0];
		}
	}

	public static void onAttack(Entity target) {
		lastAttack = System.currentTimeMillis();
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !SkyblockLocation.inSkyblock) {
			return;
		}
		SlayerType area = SlayerApi.areaType();
		if (area != null && area.matches(target) && target.distanceTo(client.player) < 6
			&& !target.getName().getString().equals("Oubliette Guard")) {
			SlayerAlerts.onSlayerMobHit(area);
		}
		SlayerWorld.onAttack(target);
		gummy(client);
	}

	private static void gummy(Minecraft client) {
		SlayerSettings s = settings();
		SlayerType type = SlayerApi.type();
		if (!s.alerts || !s.gummyWarning || type == null || client.player == null) {
			return;
		}
		if (SlayerData.weaponWisdom(type, ItemIds.skyblockId(client.player.getMainHandItem())) < 0) {
			return;
		}
		boolean habanero = false;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (NeuIds.enchant(client.player.getItemBySlot(slot), "ultimate_habanero_tactics") > 0) {
				habanero = true;
			}
		}
		String poi = SkyblockLocation.poi;
		boolean smolderingArea = "Smoldering Tomb".equals(poi) || "The Wasteland".equals(poi);
		if (!habanero && !smolderingArea) {
			return;
		}
		if (smoldering(client)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastGummy < 10_000) {
			return;
		}
		lastGummy = now;
		SlayerAlerts.sound(SoundEvents.ANVIL_LAND, 1f, 0.5f);
		SlayerAlerts.title("No polar bear active!", 0xAA0000, 60);
		SlayerAlerts.chat("You do not have an active Re-Heated Gummy Polar Bear!");
	}

	/** Smoldering Polarization (Re-heated Gummy Polar Bear) is active: tab footer, tab list or eaten this hour. */
	private static boolean smoldering(Minecraft client) {
		if (System.currentTimeMillis() - gummyAte < 3_600_000L) {
			return true;
		}
		if (tabFooter.contains("Smoldering Polarization")) {
			return true;
		}
		for (String line : dev.stray.client.farming.PestCooldown.tabLines(client)) {
			if (line.contains("Smoldering Polarization")) {
				return true;
			}
		}
		return false;
	}

	public static void onTabFooter(Component footer) {
		tabFooter = footer == null ? "" : footer.getString().replaceAll("§.", "");
	}

	public static long lastAttackMs() {
		return lastAttack;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(Minecraft client) {
		tick++;
		SlayerSettings s = settings();
		LivingEntity boss = SlayerApi.boss();
		if (stuck && boss != null && (Math.abs(boss.getX() - stuckX) > 0.3 || Math.abs(boss.getZ() - stuckZ) > 0.3)) {
			stuck = false;
		}
		if (boss == null) {
			stuck = false;
		}
		// Fire pits: Inferno III/IV dropping below a third of its health.
		long health = SlayerApi.bossHealth();
		long max = SlayerApi.bossMaxHealth();
		if (s.inferno && s.firePits && boss != null && SlayerApi.type() == SlayerType.INFERNO && SlayerApi.tier() >= 3 && max > 0) {
			double third = max * 0.33;
			if (lastHealth > third && health >= 0 && health < third) {
				firePitsAt = System.currentTimeMillis();
				SlayerAlerts.title("Fire pits!", 0xFF5555, 40);
			}
		}
		lastHealth = health;
		if (firePitsAt != 0 && System.currentTimeMillis() - firePitsAt < 2000 && tick % 10 == 0) {
			SlayerAlerts.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
		}
		if (s.inferno && s.pillar && client.level != null && client.player != null && tick % 2 == 0) {
			scanPillar(client);
		}
	}

	private static void scanPillar(Minecraft client) {
		String found = null;
		int foundId = -1;
		for (SlayerTags.Tag tag : SlayerTags.all()) {
			for (String line : tag.lines()) {
				Matcher m = PILLAR.matcher(line);
				if (m.find()) {
					found = m.group(1);
					foundId = tag.entity().getId();
					break;
				}
			}
			if (found != null) {
				break;
			}
		}
		if (found != null) {
			pillar = found;
			pillarId = foundId;
			pillarSeen = System.currentTimeMillis();
		} else if (System.currentTimeMillis() - pillarSeen > 500) {
			pillar = null;
			pillarId = -1;
		}
	}

	/** Seconds left on a fire pillar ("2.5"), or null. */
	public static String pillar() {
		return pillar;
	}

	// ---------------------------------------------------------------- readouts

	/** The hellion shield the own boss has now, or null. */
	public static Shield bossShield() {
		return shieldOf(SlayerApi.bossTags());
	}

	/** The hellion shield named in a mob's tags ("ASHEN ♨3"), or null. */
	public static Shield shieldOf(java.util.List<String> tags) {
		for (String tag : tags) {
			for (Shield shield : Shield.values()) {
				if (tag.contains(shield.name()) && tag.contains("♨")) {
					return shield;
				}
			}
		}
		return null;
	}

	public static String shieldNumber() {
		for (String tag : SlayerApi.bossTags()) {
			int at = tag.indexOf('♨');
			if (at >= 0 && at + 1 < tag.length()) {
				return tag.substring(at + 1).trim().split(" ")[0];
			}
		}
		return "";
	}

	/** Phase lines for the own boss (empty when there is nothing to say). */
	public static java.util.List<Phase> phases() {
		java.util.List<Phase> out = new java.util.ArrayList<>();
		LivingEntity boss = SlayerApi.boss();
		SlayerType type = SlayerApi.type();
		if (boss == null || type == null) {
			return out;
		}
		long health = SlayerApi.bossHealth();
		long max = SlayerApi.bossMaxHealth();
		int tier = SlayerApi.tier();
		switch (type) {
			case INFERNO -> {
				int steps = tier >= 3 ? 3 : 2;
				out.add(healthPhase(health, max, steps));
				Shield shield = bossShield();
				if (shield != null) {
					out.add(new Phase(shield.name() + " " + shieldNumber(), shield.rgb));
				}
			}
			case VOID -> {
				int steps = tier >= 4 ? 6 : 3;
				out.add(healthPhase(health, max, steps));
				for (String tag : SlayerApi.bossTags()) {
					Matcher m = HITS.matcher(tag);
					if (tag.contains("Hit") && m.find()) {
						int maxHits = switch (tier) {
							case 1 -> 15;
							case 2 -> 30;
							case 3 -> 60;
							default -> 100;
						};
						int hits = Integer.parseInt(m.group(1));
						out.add(new Phase(hits + " hits", percentColor(hits, maxHits)));
						break;
					}
				}
				Entity vehicle = boss.getVehicle();
				if (vehicle != null) {
					double left = Math.max(0, 8.2 - vehicle.tickCount / 20.0);
					out.add(new Phase(String.format(Locale.ROOT, "Laser %.1fs", left), 0xFF55FF));
				}
			}
			case TARANTULA -> {
				if (tier >= 5) {
					boolean second = boss.getName().getString().contains("Conjoined");
					for (String tag : SlayerApi.bossTags()) {
						if (tag.contains("Conjoined")) {
							second = true;
						}
					}
					out.add(new Phase(second ? "Phase 2/2" : "Phase 1/2", 0xFFFF55));
				}
				if (stuck) {
					out.add(new Phase("Kill the hatchlings!", 0xFFFF55));
				}
			}
			case REVENANT -> {
				if (SlayerApi.bossTag("Boom!") != null) {
					out.add(new Phase("BOOM!", 0xFFFFFF));
				}
			}
			case SVEN -> {
				if (SlayerApi.bossTag("Calling the pups") != null) {
					out.add(new Phase("Pups!", 0xFFAA00));
				}
			}
			case VAMPIRE -> {
				if (max > 0 && health >= 0) {
					out.add(new Phase(String.format(Locale.ROOT, "%.0f%%", 100.0 * health / max), 0xFFFF55));
					double untilSteak = health - max * 0.2;
					if (untilSteak <= 0) {
						out.add(new Phase("Steak!", 0xFF5555));
					} else if (untilSteak < 300) {
						out.add(new Phase(String.format(Locale.ROOT, "%,.0f HP till steak", untilSteak), 0xFF5555));
					}
				}
				Entity vehicle = boss.getVehicle();
				if (vehicle != null && vehicle.tickCount > 40) {
					double left = (20 * 26 - vehicle.tickCount) / 20.0;
					out.add(new Phase(String.format(Locale.ROOT, "Mania circles %.1fs", left), 0x55FFFF));
				}
				String claws = SlayerApi.bossTag("TWINCLAWS");
				if (claws != null) {
					out.add(new Phase("TWINCLAWS", 0xFFAA00));
				}
			}
		}
		return out;
	}

	private static Phase healthPhase(long health, long max, int steps) {
		if (health < 0 || max <= 0) {
			return new Phase("", 0xFFFFFF);
		}
		long step = Math.max(1, max / steps);
		int phase = (int) Math.min(steps, Math.max(1, steps - (health - 1) / step));
		int rgb = phase == steps ? 0x55FF55 : phase == 1 ? 0xFF5555 : 0xFFFF55;
		return new Phase("Phase " + phase + "/" + steps, rgb);
	}

	private static int percentColor(long have, long max) {
		double f = max <= 0 ? 0 : (double) have / max;
		if (f > 0.75) {
			return 0x55FF55;
		}
		if (f > 0.5) {
			return 0xFFFF55;
		}
		if (f > 0.25) {
			return 0xFFAA00;
		}
		return 0xFF5555;
	}

	/** Dagger helper lines (top, bottom) while holding a blaze dagger, or null. */
	public static String[] daggerLines() {
		SlayerSettings s = settings();
		Minecraft client = Minecraft.getInstance();
		if (!s.inferno || !s.daggers || client.player == null) {
			return null;
		}
		Dagger held = dagger(client.player.getMainHandItem());
		if (held == null) {
			return null;
		}
		readFromInventory(client);
		Shield nearest = s.markShield ? bossShield() : null;
		Dagger first = Dagger.values()[Math.max(0, Math.min(1, s.firstDagger))];
		Dagger second = first.other();
		String top = format(held, true, first, nearest) + "  " + format(held, true, second, nearest);
		String bottom = format(held, false, first, nearest) + "  " + format(held, false, second, nearest);
		return new String[] {top, bottom};
	}

	private static String format(Dagger holding, boolean active, Dagger column, Shield nearest) {
		boolean inHand = holding == column;
		Shield shield = active ? column.active : (column.active == column.shields[0] ? column.shields[1] : column.shields[0]);
		String color = switch (shield) {
			case AURIC -> "§e";
			case ASHEN -> "§8";
			case SPIRIT -> "§f";
			case CRYSTAL -> "§b";
		};
		if (inHand && active) {
			if (nearest == null) {
				return "§7[" + color + shield.label + "§7]";
			}
			return shield == nearest ? "§a[" + color + shield.label.toUpperCase(Locale.ROOT) + "§a]" : "§c[§m" + color + shield.label + "§c]";
		}
		return shield == nearest ? "§6[" + color + shield.label + "§6]" : color + shield.label;
	}

	private static Dagger dagger(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		String name = stack.getHoverName().getString();
		for (Dagger dagger : Dagger.values()) {
			for (String n : dagger.names) {
				if (name.contains(n)) {
					return dagger;
				}
			}
		}
		return null;
	}

	private static void readFromInventory(Minecraft client) {
		for (Dagger dagger : Dagger.values()) {
			if (dagger.known) {
				continue;
			}
			for (ItemStack stack : client.player.getInventory().getNonEquipmentItems()) {
				if (dagger(stack) != dagger) {
					continue;
				}
				var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
				if (lore == null) {
					continue;
				}
				for (Component line : lore.lines()) {
					String text = line.getString();
					if (!text.contains("Attuned: ")) {
						continue;
					}
					for (Shield shield : dagger.shields) {
						if (text.contains(shield.label)) {
							dagger.active = shield;
							dagger.known = true;
						}
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	public static String formatTime(long ms) {
		long totalSeconds = ms / 1000;
		long minutes = totalSeconds / 60;
		long seconds = totalSeconds % 60;
		long millis = ms % 1000;
		if (minutes > 0) {
			return String.format(Locale.ROOT, "%dm %d.%03ds", minutes, seconds, millis);
		}
		return String.format(Locale.ROOT, "%d.%03ds", seconds, millis);
	}

	public static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(n);
		};
	}

	public static boolean stuck() {
		return stuck;
	}
}
