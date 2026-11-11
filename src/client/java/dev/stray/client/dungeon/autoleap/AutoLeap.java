package dev.stray.client.dungeon.autoleap;

import dev.stray.client.config.LeapPreset;
import dev.stray.client.config.LeapTargetConfig;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DevMode;
import dev.stray.client.dungeon.DungeonParty;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.mixin.BossHealthOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto Leap and Fast Leap, ported from quoi's autoleap module (jcnlk/quoi, used with
 * permission). Auto Leap leaps by itself on F7 events (crystals, Storm's crushes and death,
 * predev, I4, finished P3 sections, Necron, the relic, door openers during clear). Fast Leap
 * turns a left click with the Spirit Leap into a leap to the target set for where you
 * stand. Targets are a class or a player name ({@link StrayConfig#leapMode}). The leaping
 * itself is {@link LeapRunner}. Full edition only.
 */
public final class AutoLeap {
	/** One leap target: its id in the config, label and what it does in each mode. */
	public record Target(String id, String label, String autoDesc, String fastDesc, boolean auto, boolean fast, BooleanSupplier matches) {
	}

	private static final AABB MIDDLE = new AABB(47, 64, 69, 62, 75, 84);
	private static final AABB CRYSTAL_A = new AABB(78, 233, 46, 87, 243, 55);
	private static final AABB CRYSTAL_B = new AABB(60, 233, 46, 69, 243, 55);
	private static final AABB I4 = new AABB(62, 127, 34, 65, 130, 37);
	private static final AABB YELLOW = new AABB(24, 170, 86, 41, 172, 103);
	private static final AABB PURPLE = new AABB(106, 170, 86, 125, 172, 103);
	private static final AABB BETWEEN = new AABB(41, 163, 86, 106, 172, 103);
	private static final Set<String> RELICS = Set.of("GREEN_KING_RELIC", "PURPLE_KING_RELIC", "BLUE_KING_RELIC", "ORANGE_KING_RELIC", "RED_KING_RELIC");
	private static final Pattern DOOR = Pattern.compile("^(?:\\[\\w+] )?(\\w+) opened a (?:WITHER|Blood) door!");
	private static final Pattern PARTY = Pattern.compile("^Party > (?:\\[[^\\]]+\\] )?(\\w{1,16}): (.+)$");
	private static final Set<String> MELODY = Set.of("1/3", "2/3", "33%", "66%", "67%");

	/** Filled in by {@link #targets()}; the conditions read the player's spot at call time. */
	private static List<Target> targets;

	private static int crystalCount;
	private static int crushCount;
	private static boolean p4Leaped;
	private static boolean relicLeaped;
	private static boolean bloodOpen;
	private static String melodyTarget;
	private static String lastDoorOpener;
	private static long lastFastClick;
	private static ClientLevel lastLevel;
	private static boolean listening;

	private AutoLeap() {
	}

	public static List<Target> targets() {
		if (targets == null) {
			targets = List.of(
				new Target("door", "Door opener", "Leap when a teammate opens a Wither or Blood door.", "Leap to the last Wither or Blood door opener during clear.", true, true, () -> true),
				new Target("crystal", "Crystal", "Leap when you pick up an Energy Crystal.", "Leap from either P1 crystal platform.", true, true,
					() -> F7Phase.inPhase(F7Phase.Phase.P1) && (at(CRYSTAL_A) || at(CRYSTAL_B))),
				new Target("p1", "P1", "Leap on the second Energy Laser charge in P1.", "Leap from P1 (Crystal takes priority on the platforms).", true, true,
					() -> F7Phase.inPhase(F7Phase.Phase.P1) && F7Phase.inPhaseAt(F7Phase.Phase.P1) && !(crystalOn() && at(CRYSTAL_A, CRYSTAL_B))),
				new Target("crush1", "First crush", "Leap after Storm's first crush.", "", true, false, () -> F7Phase.inPhase(F7Phase.Phase.P2)),
				new Target("crush2", "Second crush", "Leap after Storm's second crush.", "", true, false, () -> F7Phase.inPhase(F7Phase.Phase.P2)),
				new Target("enrage", "Storm enrage", "Leap when Storm is enraged.", "", true, false, () -> F7Phase.inPhase(F7Phase.Phase.P2)),
				new Target("stormdeath", "Storm death", "Leap on Storm's death line.", "Leap from P2 outside the pads and the area between them.", true, true,
					() -> F7Phase.inPhase(F7Phase.Phase.P2) && (autoMode || F7Phase.inPhaseAt(F7Phase.Phase.P2) && !at(YELLOW) && !at(PURPLE) && !at(BETWEEN))),
				new Target("yellow", "Yellow pad", "", "Leap from the yellow pad.", false, true, () -> F7Phase.inPhase(F7Phase.Phase.P2) && at(YELLOW)),
				new Target("purple", "Purple pad", "", "Leap from the purple pad.", false, true, () -> F7Phase.inPhase(F7Phase.Phase.P2) && at(PURPLE)),
				new Target("between", "Between pads", "", "Leap from between the purple and yellow pads.", false, true, () -> F7Phase.inPhase(F7Phase.Phase.P2) && at(BETWEEN)),
				new Target("predev", "Predev", "Leap on Storm's lightning line while you are in P3.", "Leap from P3 while Maxor or Storm is still up.", true, true,
					() -> F7Phase.inPhaseAt(F7Phase.Phase.P3) && F7Phase.inPhase(F7Phase.Phase.P1, F7Phase.Phase.P2)),
				new Target("i4", "I4", "Leap after you finish I4 in section 1 or 2.", "Leap from the I4 device.", true, true,
					() -> F7Phase.inPhase(F7Phase.Phase.P3) && at(I4)),
				new Target("s1", "Section 1", "Leaving or finishing section 1.", "Your P3 section is 1.", true, true, () -> F7Phase.inPhase(F7Phase.Phase.P3)),
				new Target("s2", "Section 2", "Leaving or finishing section 2.", "Your P3 section is 2.", true, true, () -> F7Phase.inPhase(F7Phase.Phase.P3)),
				new Target("s3", "Section 3", "Leaving or finishing section 3.", "Your P3 section is 3.", true, true, () -> F7Phase.inPhase(F7Phase.Phase.P3)),
				new Target("s4", "Section 4", "Leaving or finishing section 4.", "Your P3 section is 4.", true, true, () -> F7Phase.inPhase(F7Phase.Phase.P3)),
				new Target("middle", "Middle", "Leap on Necron's 'Goodbye.' while outside the middle in P4.", "Leap from P4 outside the middle platform.", true, true,
					() -> F7Phase.inPhase(F7Phase.Phase.P4) && F7Phase.inPhaseAt(F7Phase.Phase.P4) && !at(MIDDLE)),
				new Target("p4", "P4", "Leap once at about 5% Necron health.", "Leap from the P4 middle platform.", true, true,
					() -> F7Phase.inPhase(F7Phase.Phase.P4) && F7Phase.inPhaseAt(F7Phase.Phase.P4) && (autoMode || at(MIDDLE))),
				new Target("relic", "Relic", "Leap once after you pick up a relic in M7 P5.", "Leap during M7 P5.", true, true,
					() -> F7Phase.inPhase(F7Phase.Phase.P5) && F7Phase.inPhaseAt(F7Phase.Phase.P5))
			);
		}
		return targets;
	}

	/** Set while a target condition is checked for Auto Leap (some conditions differ). */
	private static boolean autoMode;

	public static void init() {
		if (listening) {
			return;
		}
		listening = true;
		F7Phase.listen((stage, full) -> {
			StrayConfig config = StrayConfig.get();
			if (!config.autoLeap || !config.autoLeapP3 || config.autoLeapOnlyGate != full) {
				return;
			}
			if (F7Phase.inPhaseAt(F7Phase.Phase.P3) && !matches("i4", true)) {
				leapToSection(stage, true);
			}
		});
	}

	// ------------------------------------------------------------------ config access

	private static Map<String, LeapTargetConfig> map(boolean auto) {
		StrayConfig config = StrayConfig.get();
		return auto ? config.autoLeapTargets : config.fastLeapTargets;
	}

	public static LeapTargetConfig config(String id, boolean auto) {
		return map(auto).computeIfAbsent(id, k -> new LeapTargetConfig());
	}

	private static Target target(String id) {
		for (Target target : targets()) {
			if (target.id.equals(id)) {
				return target;
			}
		}
		return null;
	}

	private static boolean sectionTarget(String id) {
		return id.length() == 2 && id.charAt(0) == 's' && Character.isDigit(id.charAt(1));
	}

	/**
	 * On: a target is on once it has someone to leap to (a class, or a name in name mode);
	 * the section targets also need the P3 toggle. The door opener has its own toggle.
	 */
	public static boolean enabled(String id, boolean auto) {
		StrayConfig config = StrayConfig.get();
		LeapTargetConfig target = config(id, auto);
		if ("door".equals(id)) {
			return target.on;
		}
		boolean set = config.leapMode == 1 ? target.name != null && !target.name.isBlank() : target.clazz >= 0 && target.clazz < CLASSES.length;
		if (sectionTarget(id)) {
			return set && (auto ? config.autoLeapP3 : config.fastLeapP3);
		}
		return set;
	}

	public static boolean isSection(String id) {
		return sectionTarget(id);
	}

	private static boolean matches(String id, boolean auto) {
		Target target = target(id);
		if (target == null) {
			return false;
		}
		boolean prior = autoMode;
		autoMode = auto;
		try {
			return target.matches.getAsBoolean();
		} finally {
			autoMode = prior;
		}
	}

	private static boolean crystalOn() {
		return enabled("crystal", autoMode);
	}

	/** Leaps to this target's class or name when it is on and its condition holds. */
	private static boolean leap(String id, boolean auto) {
		if (!enabled(id, auto) || !matches(id, auto)) {
			return false;
		}
		StrayConfig config = StrayConfig.get();
		if ("i4".equals(id) && melodyTarget != null && (auto ? config.autoLeapMelody : config.fastLeapMelody)) {
			LeapRunner.leapToName(melodyTarget);
			return true;
		}
		LeapTargetConfig target = config(id, auto);
		if (config.leapMode == 1) {
			if (target.name == null || target.name.isBlank()) {
				return false;
			}
			LeapRunner.leapToName(target.name.trim());
			return true;
		}
		DungeonParty.DungeonClass clazz = classAt(target.clazz);
		if (clazz == null) {
			return false;
		}
		LeapRunner.leapToClass(clazz);
		return true;
	}

	public static final DungeonParty.DungeonClass[] CLASSES = {
		DungeonParty.DungeonClass.ARCHER, DungeonParty.DungeonClass.BERSERK, DungeonParty.DungeonClass.HEALER,
		DungeonParty.DungeonClass.MAGE, DungeonParty.DungeonClass.TANK
	};

	public static DungeonParty.DungeonClass classAt(int index) {
		return index >= 0 && index < CLASSES.length ? CLASSES[index] : null;
	}

	private static void leapToSection(int stage, boolean auto) {
		int at = F7Phase.stageAt(Minecraft.getInstance().player);
		if (at != 0 && at <= stage && stage >= 1 && stage <= 4) {
			leap("s" + stage, auto);
		}
	}

	private static boolean leapToDoorOpener(String name, boolean auto) {
		StrayConfig config = StrayConfig.get();
		Minecraft client = Minecraft.getInstance();
		if (!enabled("door", auto) || name == null || client.player == null
			|| name.equals(client.player.getGameProfile().name())
			|| bloodOpen && (auto ? config.autoLeapDoorAfterBlood : config.fastLeapDoorAfterBlood)) {
			return false;
		}
		LeapRunner.leapToName(name);
		return true;
	}

	private static boolean at(AABB... boxes) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		for (AABB box : boxes) {
			if (box.contains(player.position())) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ events

	private static boolean inDungeon() {
		return SkyblockLocation.inDungeon || DevMode.on();
	}

	public static void tick(Minecraft client) {
		if (client.level != lastLevel) {
			lastLevel = client.level;
			reset();
		}
		StrayConfig config = StrayConfig.get();
		if (!config.autoLeap || client.player == null || !F7Phase.inF7Boss()) {
			return;
		}
		// P4: once at about 5% Necron health.
		if (!p4Leaped && enabled("p4", true) && matches("p4", true)) {
			for (LerpingBossEvent event : ((BossHealthOverlayAccessor) client.gui.hud.getBossOverlay()).stray$events().values()) {
				float progress = event.getProgress();
				if (progress >= 0.001f && progress <= 0.051f && event.getName().getString().toLowerCase(java.util.Locale.ROOT).contains("necron")) {
					p4Leaped = true;
					leap("p4", true);
					break;
				}
			}
		}
		for (LerpingBossEvent event : ((BossHealthOverlayAccessor) client.gui.hud.getBossOverlay()).stray$events().values()) {
			F7Phase.onBossBar(event.getName().getString());
		}
		// Relic: once, when one is in the last hotbar slot.
		if (!relicLeaped && enabled("relic", true) && matches("relic", true)) {
			String id = ItemIds.skyblockId(client.player.getInventory().getItem(8));
			if (id != null && RELICS.contains(id)) {
				relicLeaped = leap("relic", true);
			}
		}
	}

	private static void reset() {
		crystalCount = 0;
		crushCount = 0;
		p4Leaped = false;
		relicLeaped = false;
		bloodOpen = false;
		melodyTarget = null;
		lastDoorOpener = null;
	}

	public static void onChat(String plain) {
		if (plain == null || !inDungeon()) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		Matcher party = PARTY.matcher(plain);
		if (party.matches()) {
			String content = party.group(2);
			if (!content.endsWith(")")) {
				for (String progress : MELODY) {
					if (content.contains(progress)) {
						melodyTarget = party.group(1);
						break;
					}
				}
			}
		}
		if ("The BLOOD DOOR has been opened!".equals(plain)) {
			bloodOpen = true;
		}
		Matcher door = DOOR.matcher(plain);
		if (door.find()) {
			lastDoorOpener = door.group(1);
			if (config.autoLeap && !SkyblockLocation.inBoss) {
				leapToDoorOpener(lastDoorOpener, true);
			}
		}
		if (!config.autoLeap || !F7Phase.inF7Boss()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		String self = client.player == null ? "" : client.player.getGameProfile().name();
		switch (plain) {
			case "[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!" -> {
				crushCount++;
				if (crushCount == 1) {
					leap("crush1", true);
				} else if (crushCount == 2) {
					leap("crush2", true);
				}
			}
			case "The Energy Laser is charging up!" -> {
				if (enabled("p1", true) && ++crystalCount == 2) {
					leap("p1", true);
				}
			}
			case "⚠ Storm is enraged! ⚠" -> leap("enrage", true);
			case "[BOSS] Storm: I'd be happy to show you what that's like!" -> leap("predev", true);
			case "[BOSS] Necron: Goodbye." -> leap("middle", true);
			case "[BOSS] Storm: I should have known that I stood no chance." -> leap("stormdeath", true);
			default -> {
				if (plain.equals(self + " picked up an Energy Crystal!")) {
					leap("crystal", true);
				} else if ((F7Phase.stage() == 1 || F7Phase.stage() == 2) && plain.startsWith(self + " completed a device!")) {
					leap("i4", true);
				}
			}
		}
	}

	/**
	 * Fast Leap, at tick start: a left click while holding the Spirit Leap becomes a leap to
	 * the target for where you stand. The click itself is dropped (it would do nothing with
	 * the leap anyway).
	 */
	public static void beforeKeybinds(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.fastLeap || client.player == null || client.gui.screen() != null || !inDungeon()) {
			return;
		}
		if (!LeapRunner.isLeap(client.player.getMainHandItem())) {
			return;
		}
		boolean clicked = false;
		while (client.options.keyAttack.consumeClick()) {
			clicked = true;
		}
		if (!clicked) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastFastClick < 250) {
			return;
		}
		lastFastClick = now;
		if (!attemptFastLeap() && config.fastLeapP3 && F7Phase.inF7Boss()) {
			leapToSection(F7Phase.stageAt(client.player), false);
		}
	}

	private static final String[] FAST_ORDER = {"crystal", "predev", "relic", "p1", "p4", "yellow", "purple", "between", "stormdeath", "middle", "i4"};

	private static boolean attemptFastLeap() {
		if (!SkyblockLocation.inBoss && !DevMode.on()) {
			DungeonParty.Member opener = DungeonParty.lastDoorOpener();
			String name = opener != null ? opener.name : lastDoorOpener;
			return leapToDoorOpener(name, false);
		}
		if (!F7Phase.inF7Boss()) {
			return false;
		}
		for (String id : FAST_ORDER) {
			if (enabled(id, false) && matches(id, false)) {
				return leap(id, false);
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ presets

	public static void savePreset(String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.isEmpty()) {
			message("§cEnter a preset name first.");
			return;
		}
		StrayConfig config = StrayConfig.get();
		String key = existingKey(name);
		LeapPreset preset = new LeapPreset();
		preset.mode = config.leapMode;
		preset.swapBack = config.leapSwapBack;
		preset.autoP3 = config.autoLeapP3;
		preset.fastP3 = config.fastLeapP3;
		preset.autoOnlyGate = config.autoLeapOnlyGate;
		preset.autoDoorAfterBlood = config.autoLeapDoorAfterBlood;
		preset.fastDoorAfterBlood = config.fastLeapDoorAfterBlood;
		preset.autoMelody = config.autoLeapMelody;
		preset.fastMelody = config.fastLeapMelody;
		config.autoLeapTargets.forEach((k, v) -> preset.auto.put(k, v.copy()));
		config.fastLeapTargets.forEach((k, v) -> preset.fast.put(k, v.copy()));
		config.leapPresets.put(key, preset);
		config.leapPresetName = key;
		config.save();
		message("§aSaved leap preset §f" + key + "§a.");
	}

	public static void loadPreset(String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.isEmpty()) {
			message("§cEnter a preset name first.");
			return;
		}
		StrayConfig config = StrayConfig.get();
		String key = existingKey(name);
		LeapPreset preset = config.leapPresets.get(key);
		if (preset == null) {
			message("§cLeap preset §f" + name + " §cdoesn't exist.");
			return;
		}
		config.leapMode = preset.mode;
		config.leapSwapBack = preset.swapBack;
		config.autoLeapP3 = preset.autoP3;
		config.fastLeapP3 = preset.fastP3;
		config.autoLeapOnlyGate = preset.autoOnlyGate;
		config.autoLeapDoorAfterBlood = preset.autoDoorAfterBlood;
		config.fastLeapDoorAfterBlood = preset.fastDoorAfterBlood;
		config.autoLeapMelody = preset.autoMelody;
		config.fastLeapMelody = preset.fastMelody;
		config.autoLeapTargets.clear();
		config.fastLeapTargets.clear();
		preset.auto.forEach((k, v) -> config.autoLeapTargets.put(k, v.copy()));
		preset.fast.forEach((k, v) -> config.fastLeapTargets.put(k, v.copy()));
		config.leapPresetName = key;
		config.save();
		message("§aLoaded leap preset §f" + key + "§a.");
	}

	/** Next saved preset name after the current one (for cycling in the menu). */
	public static String nextPreset(String current) {
		java.util.List<String> names = new java.util.ArrayList<>(StrayConfig.get().leapPresets.keySet());
		java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
		if (names.isEmpty()) {
			return current;
		}
		int i = current == null ? -1 : names.indexOf(existingKey(current));
		return names.get((i + 1) % names.size());
	}

	private static String existingKey(String name) {
		for (String key : StrayConfig.get().leapPresets.keySet()) {
			if (key.equalsIgnoreCase(name)) {
				return key;
			}
		}
		return name;
	}

	private static void message(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("§bStray §7» " + text));
		}
	}
}
