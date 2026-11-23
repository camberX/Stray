package dev.stray.client.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudLayout;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Dungeon run splits, timed the way NoammAddons' RunSplits and DungeonListener do it (CC0):
 * Blood Open (Mort's map line to the Watcher's first line), Blood Clear (to "You have proven
 * yourself"), Portal Entry (to entering the boss room), Boss Entry (total so far), then the
 * floor's boss splits from their boss lines (F7/M7 Necron ends when Necron dies, which is the
 * wither hurt sound after "ARGH!"), Cleared (to the "Defeated" line), Overall and Estimated.
 * Drawn like Blade Addons' splits (CC0): the coloured split name on the left and, flush right,
 * the real time in green with the server-tick time (or the lag, real minus ticks) in grey
 * brackets, no panel, the game's font with its shadow. Every row is always listed.
 * Estimated adds the optimal row times (F7/M7) to the time so far; on other floors it uses your
 * fastest finished run per floor, saved in {@code config/stray/runsplits.json}.
 */
public final class RunSplits {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("runsplits.json");
	private static final String MORT = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";
	private static final Pattern WATCHER = Pattern.compile("^\\[BOSS] The Watcher: .+$");
	private static final String WATCHER_CLEAR = "[BOSS] The Watcher: You have proven yourself. You may pass.";
	/** Hypixel's dungeon scoreboard: "Time Elapsed: 05m 32s" (or "1h 02m 03s"). */
	private static final Pattern ELAPSED = Pattern.compile("Time Elapsed:\\s*(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(\\d+)s");
	private static final Pattern RUN_END = Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$");
	private static final int LINE = 10;

	private record Split(String name, Predicate<String> ends) {
		static Split at(String name, String message) {
			return new Split(name, message == null ? m -> false : message::equals);
		}
	}

	private record Boss(String start, List<Split> splits) {
	}

	/** One row: its label and when it starts and ends, in ms and in server ticks (0 = not yet). */
	private static final class Row {
		final String name;
		long start;
		long end;
		long startTick;
		long endTick;

		Row(String name) {
			this.name = name;
		}

		void setStart(long ms) {
			start = ms;
			startTick = tickAt(ms);
		}

		void setEnd(long ms) {
			end = ms;
			endTick = tickAt(ms);
		}

		boolean started() {
			return start > 0L;
		}

		long time(long now) {
			return !started() ? 0L : (end > 0L ? end : now) - start;
		}

		long ticks() {
			return !started() ? 0L : Math.max(0L, (end > 0L ? endTick : serverTicks) - startTick);
		}
	}

	/**
	 * Server ticks from {@link dev.stray.client.net.ServerTicks}, counted like NoammAddons. Real time
	 * minus tick time is the time the server lagged.
	 */
	private static long serverTicks;

	public static void serverTick() {
		serverTicks++;
	}

	/** The tick at {@code ms}: now, or for a time in the past, now minus that many ticks. */
	private static long tickAt(long ms) {
		long ago = System.currentTimeMillis() - ms;
		return ago <= 0L ? serverTicks : serverTicks - Math.round(ago / 50.0);
	}

	public static final class Best {
		public long total;
		public Map<String, Long> segments = new LinkedHashMap<>();
	}

	private static Map<String, Best> bests;

	// The run in progress.
	private static long runStart;
	private static long runEnd;
	private static long runStartTick;
	private static long runEndTick;
	private static long bossEntryTick;
	/** Picked up partway from the scoreboard: the early rows are guesses, so it is never a personal best. */
	private static boolean late;
	private static long bossEntry;
	private static boolean necronStart;
	private static final List<Row> rows = new ArrayList<>();
	private static Row bloodOpen;
	private static Row bloodClear;
	private static Row portal;
	private static Row cleared;
	private static final List<Row> boss = new ArrayList<>();
	private static Boss bossDef;

	private RunSplits() {
	}

	// ================================================================ input

	public static void onChat(String message) {
		if (!StrayConfig.get().runSplits || message == null) {
			return;
		}
		String msg = message.trim();
		long now = System.currentTimeMillis();
		if (msg.equals(MORT) && runStart == 0L) {
			start(now);
			return;
		}
		if (runStart == 0L || runEnd > 0L) {
			return;
		}
		if (WATCHER_CLEAR.equals(msg) && bloodClear.end == 0L) {
			if (!bloodClear.started()) {
				bloodOpen.setEnd(now);
				bloodClear.setStart(now);
			}
			bloodClear.setEnd(now);
			portal.setStart(now);
		} else if (WATCHER.matcher(msg).matches() && bloodOpen.end == 0L) {
			bloodOpen.setEnd(now);
			bloodClear.setStart(now);
		}
		if (bossDef == null) {
			bossDef = bossFor();
			if (bossDef != null && boss.isEmpty()) {
				for (Split split : bossDef.splits) {
					boss.add(new Row(split.name));
				}
			}
		}
		if (bossDef != null) {
			if (msg.equals(bossDef.start) && !boss.isEmpty() && !boss.getFirst().started()) {
				boss.getFirst().setStart(now);
			}
			for (int i = 0; i < boss.size(); i++) {
				Row row = boss.get(i);
				if (row.started() && row.end == 0L && bossDef.splits.get(i).ends.test(msg)) {
					endBossRow(i, now);
					break;
				}
			}
		}
		if ("[BOSS] Necron: ARGH!".equals(msg)) {
			necronStart = true;
		}
		if (RUN_END.matcher(msg).matches()) {
			end(now);
		}
	}

	/** F7/M7: Necron dies on this sound after "ARGH!" (NoammAddons' F7Listener). */
	public static void onSound(SoundEvent sound, float volume, float pitch) {
		if (!necronStart || runEnd > 0L || sound != SoundEvents.WITHER_HURT || volume != 15f || pitch != 1f) {
			return;
		}
		necronStart = false;
		for (int i = 0; i < boss.size(); i++) {
			Row row = boss.get(i);
			if (row.name.contains("Necron") && row.started() && row.end == 0L) {
				endBossRow(i, System.currentTimeMillis());
				return;
			}
		}
	}

	public static void tick() {
		if (runStart == 0L) {
			pickUpRun();
			return;
		}
		if (runEnd > 0L || bossEntry > 0L || !SkyblockLocation.inBoss) {
			return;
		}
		long now = System.currentTimeMillis();
		bossEntry = now;
		bossEntryTick = tickAt(now);
		if (!portal.started()) {
			// Blood was skipped or the Watcher lines were missed: close what is open.
			if (bloodOpen.end == 0L) {
				bloodOpen.setEnd(now);
			}
			portal.setStart(now);
		}
		portal.setEnd(now);
	}

	private static int pickUpTicks;

	/**
	 * Turned on (or joined) after Mort's line: start from the scoreboard's "Time Elapsed" so the
	 * splits still show, with Overall right and the rows from here on.
	 */
	private static void pickUpRun() {
		if (!StrayConfig.get().runSplits || !SkyblockLocation.inDungeon || ++pickUpTicks % 20 != 0) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		for (String line : dev.stray.client.slayer.SlayerApi.sidebar(client)) {
			java.util.regex.Matcher m = ELAPSED.matcher(line);
			if (!m.find()) {
				continue;
			}
			long seconds = (m.group(1) == null ? 0L : Long.parseLong(m.group(1)) * 3600L)
				+ (m.group(2) == null ? 0L : Long.parseLong(m.group(2)) * 60L)
				+ Long.parseLong(m.group(3));
			if (seconds <= 0L) {
				return;
			}
			long now = System.currentTimeMillis();
			start(now - seconds * 1000L);
			late = true;
			if (SkyblockLocation.inBoss) {
				// Already past the clear: only the boss rows and Overall can be timed from here.
				bloodOpen.setEnd(bloodOpen.start);
				bloodClear.setStart(bloodOpen.start);
				bloodClear.setEnd(bloodOpen.start);
				portal.setStart(bloodOpen.start);
				portal.setEnd(bloodOpen.start);
				bossEntry = now;
		bossEntryTick = tickAt(now);
			}
			return;
		}
	}

	public static void reset() {
		late = false;
		runStart = 0L;
		runEnd = 0L;
		bossEntry = 0L;
		necronStart = false;
		rows.clear();
		boss.clear();
		bossDef = null;
	}

	private static void start(long now) {
		reset();
		runStart = now;
		runStartTick = tickAt(now);
		bloodOpen = new Row("Blood Open");
		bloodClear = new Row("Blood Clear");
		portal = new Row("Portal Entry");
		cleared = new Row("Cleared");
		bloodOpen.setStart(now);
	}

	private static void endBossRow(int i, long now) {
		boss.get(i).setEnd(now);
		if (i + 1 < boss.size()) {
			boss.get(i + 1).setStart(now);
		} else {
			cleared.setStart(now);
		}
	}

	private static void end(long now) {
		runEnd = now;
		runEndTick = serverTicks;
		for (int i = 0; i < boss.size(); i++) {
			Row row = boss.get(i);
			if (row.started() && row.end == 0L) {
				endBossRow(i, now);
			}
		}
		if (cleared.started() && cleared.end == 0L) {
			cleared.setEnd(now);
		}
		saveIfBest();
	}

	// ================================================================ personal bests

	private static String floorKey() {
		int floor = SkyblockLocation.dungeonFloor;
		if (floor <= 0) {
			return "E";
		}
		return (SkyblockLocation.dungeonMaster ? "M" : "F") + floor;
	}

	private static Map<String, Best> bests() {
		if (bests == null) {
			bests = new HashMap<>();
			if (Files.isRegularFile(FILE)) {
				try {
					Map<String, Best> read = GSON.fromJson(Files.readString(FILE), new TypeToken<Map<String, Best>>() { }.getType());
					if (read != null) {
						bests.putAll(read);
					}
				} catch (Exception exception) {
					Stray.LOGGER.warn("Could not read run splits", exception);
				}
			}
		}
		return bests;
	}

	private static void saveIfBest() {
		long total = runEnd - runStart;
		if (total <= 0L || late) {
			return;
		}
		String key = floorKey();
		Best old = bests().get(key);
		if (old != null && old.total > 0L && old.total <= total) {
			return;
		}
		Best best = new Best();
		best.total = total;
		for (Row row : segments()) {
			if (row.started() && row.end > 0L) {
				best.segments.put(row.name, row.end - row.start);
			}
		}
		bests.put(key, best);
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(bests));
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not save run splits", exception);
		}
	}

	/** Forgets the personal bests of every floor. */
	public static void clearBests() {
		bests().clear();
		try {
			Files.deleteIfExists(FILE);
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not delete run splits", exception);
		}
	}

	// ================================================================ drawing

	/** The timed segments in order (what Estimated adds up). */
	private static List<Row> segments() {
		List<Row> out = new ArrayList<>();
		if (runStart == 0L) {
			return out;
		}
		out.add(bloodOpen);
		out.add(bloodClear);
		out.add(portal);
		out.addAll(boss);
		out.add(cleared);
		return out;
	}

	/** One row: label, its colour, real time and server-tick time. */
	private record Line(String name, int color, long ms, long ticks) {
	}

	private static List<Line> lines(boolean example) {
		if (example) {
			return List.of(
				new Line("Blood Open", 0xAA0000, 12_790L, 251L),
				new Line("Blood Clear", 0xFF5555, 54_160L, 1_016L),
				new Line("Portal", 0xFF55FF, 14_560L, 282L),
				new Line("Boss Entry", 0x5555FF, 81_500L, 1_549L),
				new Line("Maxor", 0xAA00AA, 13_230L, 264L),
				new Line("Storm", 0x00AAAA, 47_070L, 868L),
				new Line("Terminals", 0xFFFF55, 26_650L, 524L),
				new Line("Goldor", 0xFFAA00, 10_150L, 200L),
				new Line("Necron", 0xFF5555, 7_750L, 152L),
				new Line("Run Time", 0x178D17, 186_340L, 3_543L)
			);
		}
		if (runStart == 0L) {
			return List.of();
		}
		if (bossDef == null) {
			bossDef = bossFor();
			if (bossDef != null && boss.isEmpty()) {
				for (Split split : bossDef.splits) {
					boss.add(new Row(split.name));
				}
			}
		}
		long now = runEnd > 0L ? runEnd : System.currentTimeMillis();
		long nowTick = runEnd > 0L ? runEndTick : serverTicks;
		Best best = bests().get(floorKey());
		StrayConfig config = StrayConfig.get();
		List<Row> all = segments();
		List<Line> out = new ArrayList<>();
		out.add(row(bloodOpen, now));
		out.add(row(bloodClear, now));
		out.add(row(portal, now));
		long entry = (bossEntry > 0L ? bossEntry : now) - runStart;
		long entryTicks = (bossEntry > 0L ? bossEntryTick : nowTick) - runStartTick;
		out.add(new Line("Boss Entry", colorOf("Boss Entry"), entry, entryTicks));
		for (Row row : boss) {
			out.add(row(row, now));
		}
		out.add(row(cleared, now));

		long overall = now - runStart;
		long overallTicks = Math.max(0L, nowTick - runStartTick);
		out.add(new Line("Run Time", colorOf("Run Time"), overall, overallTicks));
		if (config.runSplitsEstimated && runEnd == 0L) {
			// Time so far, plus the rest of the open row and every row still to come at the target
			// times (the optimal times for the floor; your personal best where there are none).
			Map<String, Long> target = target(best);
			long estimate = overall;
			for (Row row : all) {
				Long goal = target.get(row.name);
				if (goal == null || row.end > 0L) {
					continue;
				}
				estimate += row.started() ? Math.max(0L, goal - row.time(now)) : goal;
			}
			long lost = overall - overallTicks * 50L;
			out.add(new Line("Estimated", colorOf("Estimated"), estimate, Math.max(0L, (estimate - lost) / 50L)));
		}
		return out;
	}

	/**
	 * Optimal row times (real time, ms) Estimated aims for, per floor. M7's Dragons time is on
	 * Cleared, which is where the time after Necron is counted.
	 */
	private static final Map<String, Map<String, Long>> OPTIMAL = Map.of(
		"F7", Map.of(
			"Blood Open", 0L, "Blood Clear", 48_300L, "Portal Entry", 3_170L,
			"Maxor", 13_740L, "Storm", 43_130L, "Terminals", 25_270L, "Goldor", 6_580L, "Necron", 23_760L,
			"Cleared", 0L),
		"M7", Map.of(
			"Blood Open", 0L, "Blood Clear", 48_300L, "Portal Entry", 3_170L,
			"Maxor", 13_740L, "Storm", 43_130L, "Terminals", 25_270L, "Goldor", 6_580L, "Necron", 23_760L,
			"Cleared", 27_170L)
	);

	private static Map<String, Long> target(Best best) {
		Map<String, Long> optimal = OPTIMAL.get(floorKey());
		if (optimal != null) {
			return optimal;
		}
		return best == null ? Map.of() : best.segments;
	}

	private static Line row(Row row, long now) {
		String label = "Portal Entry".equals(row.name) ? "Portal" : row.name;
		return new Line(label, colorOf(row.name), row.time(now), row.ticks());
	}

	/** Blade Addons' split colours (CC0); boss parts without one of their own are dark aqua. */
	private static int colorOf(String name) {
		return switch (name) {
			case "Blood Open", "Dragons" -> 0xAA0000;
			case "Blood Clear", "Necron", "Cleared" -> 0xFF5555;
			case "Portal Entry", "Portal" -> 0xFF55FF;
			case "Boss Entry" -> 0x5555FF;
			case "Maxor" -> 0xAA00AA;
			case "Terminals", "Terracottas" -> 0xFFFF55;
			case "Goldor", "Giants" -> 0xFFAA00;
			case "Run Time" -> 0x178D17;
			case "Estimated" -> 0x55FFFF;
			default -> 0x00AAAA;
		};
	}

	// ---------------------------------------------------------------- drawing (Blade Addons' look)

	/** Blade Addons' splits are 165 wide: the name on the left, the times flush right. */
	private static final int SPLIT_W = 165;
	private static final int REAL = 0xFF55FF55;
	private static final int SERVER = 0xFFAAAAAA;
	private static final int PAREN = 0xFF555555;

	/** 12.34s, or 1m 2.34s from a minute (Blade Addons' format). */
	private static String format(long ms) {
		double seconds = Math.max(0L, ms) / 1000.0;
		String rest = String.format(Locale.ROOT, "%.2fs", seconds % 60.0);
		return seconds >= 60.0 ? (int) (seconds / 60.0) + "m " + rest : rest;
	}

	/** "1m 2.34s (1m 1.90s)": real time in green, then the tick time or the lag in grey brackets. */
	private static Component timeText(Line line) {
		StrayConfig config = StrayConfig.get();
		MutableComponent out = Component.literal(format(line.ms)).withColor(REAL & 0xFFFFFF);
		String inner = null;
		if (config.runSplitsBest) {
			inner = format(line.ticks * 50L);
		} else if (config.runSplitsDiff) {
			double diff = (line.ms - line.ticks * 50L) / 1000.0;
			inner = (diff > 0 ? "+" : "") + String.format(Locale.ROOT, "%.2fs", diff);
		}
		if (inner != null) {
			out.append(Component.literal(" (").withColor(PAREN & 0xFFFFFF))
				.append(Component.literal(inner).withColor(SERVER & 0xFFFFFF))
				.append(Component.literal(")").withColor(PAREN & 0xFFFFFF));
		}
		return out;
	}

	private static Component nameText(Line line) {
		return Component.literal(line.name + " ").withColor(line.color);
	}

	public static boolean shown() {
		return StrayConfig.get().runSplits && (SkyblockLocation.inDungeon && runStart > 0L || HudLayout.editorOpen());
	}

	private static int widthOf(Font font, List<Line> lines) {
		int w = SPLIT_W;
		for (Line line : lines) {
			w = Math.max(w, font.width(nameText(line)) + font.width(timeText(line)));
		}
		return w;
	}

	public static float width(Font font) {
		List<Line> lines = lines(HudLayout.editorOpen() && runStart == 0L);
		return lines.isEmpty() ? 0f : widthOf(font, lines);
	}

	public static float height() {
		List<Line> lines = lines(HudLayout.editorOpen() && runStart == 0L);
		return lines.isEmpty() ? 0f : lines.size() * LINE - 1;
	}

	public static void extract(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!shown() || client.player == null || client.gui.hud.isHidden()) {
			return;
		}
		List<Line> lines = lines(HudLayout.editorOpen() && runStart == 0L);
		if (lines.isEmpty()) {
			return;
		}
		Font font = client.font;
		int width = widthOf(font, lines);
		HudLayout.apply(graphics, font, HudLayout.Id.RUN_SPLITS, () -> {
			// No panel, like Blade Addons, unless Background is on.
			if (StrayConfig.get().runSplitsPanel) {
				GuiDraw.fill(graphics, -2, -2, width + 4, lines.size() * LINE + 3, 0x80000000);
			}
			int y = 0;
			for (Line line : lines) {
				Component time = timeText(line);
				graphics.text(font, nameText(line), 0, y, 0xFFFFFFFF, true);
				graphics.text(font, time, width - font.width(time), y, 0xFFFFFFFF, true);
				y += LINE;
			}
		});
	}

	// ================================================================ floors (NoammAddons' splits)

	private static Boss bossFor() {
		String key = floorKey();
		Boss boss = FLOORS.get(key);
		return boss != null ? boss : FLOORS.get(key.replace('M', 'F'));
	}

	private static final Map<String, Boss> FLOORS = new HashMap<>();

	static {
		FLOORS.put("F1", new Boss("[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.", List.of(
			Split.at("First Kill", "[BOSS] Bonzo: Oh noes, you got me.. what ever will I do?!"),
			Split.at("Second Kill", "[BOSS] Bonzo: Alright, maybe I'm just weak after all.."),
			Split.at("End Dialogue", null))));
		FLOORS.put("F2", new Boss("[BOSS] Scarf: This is where the journey ends for you, Adventurers.", List.of(
			Split.at("Undeads", "[BOSS] Scarf: Those toys are not strong enough I see."),
			Split.at("Scarf", null))));
		FLOORS.put("F3", new Boss("[BOSS] The Professor: I was burdened with terrible news recently...", List.of(
			Split.at("Guardians", "[BOSS] The Professor: Oh? You found my Guardians' one weakness?"),
			Split.at("The Professor", "[BOSS] The Professor: I see. You have forced me to use my ultimate technique."),
			Split.at("Guardifessor", null))));
		FLOORS.put("F4", new Boss("[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!", List.of(
			Split.at("Dialogue", "[BOSS] Thorn: Dance! Dance with my Spirit animals! And may you perish in a delightful way!"),
			Split.at("Thorn Kill", null))));
		Pattern livid = Pattern.compile("\\[BOSS] \\w+ Livid: Impossible! How did you figure out which one I was\\?!");
		FLOORS.put("F5", new Boss("[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.", List.of(
			Split.at("Start Dialogue", "[BOSS] Livid: I respect you for making it to here, but I'll be your undoing."),
			new Split("Livid Kill", m -> livid.matcher(m).matches()),
			Split.at("End Dialogue", null))));
		FLOORS.put("F6", new Boss("[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!", List.of(
			Split.at("Terracottas", "[BOSS] Sadan: ENOUGH!"),
			Split.at("Giants", "[BOSS] Sadan: You did it. I understand now, you have earned my respect."),
			Split.at("Sadan", null))));
		List<Split> f7 = List.of(
			Split.at("Maxor", "[BOSS] Storm: Pathetic Maxor, just like expected."),
			Split.at("Storm", "[BOSS] Goldor: Who dares trespass into my domain?"),
			Split.at("Terminals", "The Core entrance is opening!"),
			Split.at("Goldor", "[BOSS] Necron: You went further than any human before, congratulations."),
			Split.at("Necron", null));
		String maxor = "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!";
		FLOORS.put("F7", new Boss(maxor, f7));
		// M7: no separate Dragons row; Cleared covers the time after Necron.
		FLOORS.put("M7", new Boss(maxor, f7));
	}
}
