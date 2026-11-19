package dev.stray.client.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudChrome;
import dev.stray.client.render.HudLayout;
import dev.stray.client.ui.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
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
 *
 * <p>Each row shows the time, your personal best for it in brackets and the difference. The
 * personal best is the fastest finished run per floor, saved in {@code config/stray/runsplits.json}.
 */
public final class RunSplits {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("runsplits.json");
	private static final String MORT = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";
	private static final Pattern WATCHER = Pattern.compile("^\\[BOSS] The Watcher: .+$");
	private static final String WATCHER_CLEAR = "[BOSS] The Watcher: You have proven yourself. You may pass.";
	private static final Pattern RUN_END = Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$");
	private static final int LINE = 10;
	private static final int PAD = 5;
	private static final int GAP = 6;

	private record Split(String name, Predicate<String> ends) {
		static Split at(String name, String message) {
			return new Split(name, message == null ? m -> false : message::equals);
		}
	}

	private record Boss(String start, List<Split> splits) {
	}

	/** One row: its label and when it starts and ends (ms, 0 = not yet). */
	private static final class Row {
		final String name;
		long start;
		long end;

		Row(String name) {
			this.name = name;
		}

		boolean started() {
			return start > 0L;
		}

		long time(long now) {
			return !started() ? 0L : (end > 0L ? end : now) - start;
		}
	}

	public static final class Best {
		public long total;
		public Map<String, Long> segments = new LinkedHashMap<>();
	}

	private static Map<String, Best> bests;

	// The run in progress.
	private static long runStart;
	private static long runEnd;
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
		if (!StrayConfig.get().runSplits || message == null || !SkyblockLocation.inDungeon) {
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
				bloodOpen.end = now;
				bloodClear.start = now;
			}
			bloodClear.end = now;
			portal.start = now;
		} else if (WATCHER.matcher(msg).matches() && bloodOpen.end == 0L) {
			bloodOpen.end = now;
			bloodClear.start = now;
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
				boss.getFirst().start = now;
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
		if (runStart == 0L || runEnd > 0L || bossEntry > 0L || !SkyblockLocation.inBoss) {
			return;
		}
		long now = System.currentTimeMillis();
		bossEntry = now;
		if (!portal.started()) {
			// Blood was skipped or the Watcher lines were missed: close what is open.
			if (bloodOpen.end == 0L) {
				bloodOpen.end = now;
			}
			portal.start = now;
		}
		portal.end = now;
	}

	public static void reset() {
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
		bloodOpen = new Row("Blood Open");
		bloodClear = new Row("Blood Clear");
		portal = new Row("Portal Entry");
		cleared = new Row("Cleared");
		bloodOpen.start = now;
	}

	private static void endBossRow(int i, long now) {
		boss.get(i).end = now;
		if (i + 1 < boss.size()) {
			boss.get(i + 1).start = now;
		} else {
			cleared.start = now;
		}
	}

	private static void end(long now) {
		runEnd = now;
		for (int i = 0; i < boss.size(); i++) {
			Row row = boss.get(i);
			if (row.started() && row.end == 0L) {
				endBossRow(i, now);
			}
		}
		if (cleared.started() && cleared.end == 0L) {
			cleared.end = now;
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
		if (total <= 0L) {
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

	private record Line(String name, String time, String best, String diff) {
	}

	private static List<Line> lines(boolean example) {
		if (example) {
			return List.of(
				new Line("§aBlood Open", "12.79", "(12.55)", "§c+0.24"),
				new Line("§cBlood Clear", "54.16", "(50.80)", "§c+3.36"),
				new Line("§dPortal Entry", "14.56", "(14.10)", "§c+0.46"),
				new Line("§6Boss Entry", "1:21.50", "(1:16.65)", "§c+4.85"),
				new Line("§5Maxor", "13.23", "(13.23)", "§f+0.00"),
				new Line("§bStorm", "47.07", "(43.40)", "§c+3.67"),
				new Line("§eTerminals", "26.65", "(26.20)", "§c+0.45"),
				new Line("§7Goldor", "10.15", "(10.00)", "§c+0.15"),
				new Line("§cNecron", "7.75", "(7.60)", "§c+0.15"),
				new Line("§8Cleared", "0.00", "(0.00)", ""),
				new Line("§fOverall", "3:06.34", "(2:57.15)", "§c+9.19"),
				new Line("§bEstimated", "3:50.34", "(3:41.15)", "§c+9.19")
			);
		}
		if (runStart == 0L) {
			return List.of();
		}
		long now = runEnd > 0L ? runEnd : System.currentTimeMillis();
		Best best = bests().get(floorKey());
		StrayConfig config = StrayConfig.get();
		List<Line> out = new ArrayList<>();
		out.add(segment("§a", bloodOpen, best, now));
		out.add(segment("§c", bloodClear, best, now));
		out.add(segment("§d", portal, best, now));
		long entry = bossEntry > 0L ? bossEntry - runStart : now - runStart;
		Long bestEntry = best == null ? null : sum(best, bloodOpen.name, bloodClear.name, portal.name);
		out.add(line("§6Boss Entry", entry, bestEntry, bossEntry > 0L || runEnd == 0L));
		for (Row row : boss) {
			out.add(segment(bossColor(row.name), row, best, now));
		}
		out.add(segment("§8", cleared, best, now));

		long overall = now - runStart;
		// What is left of the personal best from here: the rest of the open row, then every row after it.
		long left = 0L;
		boolean known = best != null && best.total > 0L;
		if (known) {
			for (Row row : segments()) {
				Long pb = best.segments.get(row.name);
				if (pb == null || row.end > 0L) {
					continue;
				}
				left += row.started() ? Math.max(0L, pb - row.time(now)) : pb;
			}
		}
		Long overallBest = known ? Math.max(0L, best.total - left) : null;
		out.add(line("§fOverall", overall, overallBest, true));
		if (config.runSplitsEstimated && known && runEnd == 0L) {
			out.add(line("§bEstimated", overall + left, best.total, true));
		}
		return out;
	}

	private static Long sum(Best best, String... names) {
		long total = 0L;
		for (String name : names) {
			Long value = best.segments.get(name);
			if (value == null) {
				return null;
			}
			total += value;
		}
		return total;
	}

	private static Line segment(String color, Row row, Best best, long now) {
		Long pb = best == null ? null : best.segments.get(row.name);
		return line(color + row.name, row.time(now), pb, row.started());
	}

	private static Line line(String label, long ms, Long pb, boolean started) {
		StrayConfig config = StrayConfig.get();
		String best = config.runSplitsBest ? "(" + (pb == null ? "--" : format(pb)) + ")" : "";
		String diff = "";
		if (config.runSplitsDiff && started && pb != null) {
			long d = ms - pb;
			String color = d < -5L ? "§a" : d > 5L ? "§c" : "§f";
			diff = color + (d < 0L ? "-" : "+") + format(Math.abs(d));
		}
		return new Line(label, format(ms), best, diff);
	}

	private static String bossColor(String name) {
		return switch (name) {
			case "Maxor" -> "§5";
			case "Storm", "Thorn Kill", "Scarf", "Guardifessor" -> "§b";
			case "Terminals", "Dialogue", "Start Dialogue", "End Dialogue", "The Professor" -> "§e";
			case "Goldor" -> "§7";
			case "Giants" -> "§d";
			case "Terracottas" -> "§6";
			case "Guardians" -> "§9";
			case "Dragons" -> "§4";
			default -> "§c";
		};
	}

	/** 12.79, or 1:21.50 from a minute. */
	private static String format(long ms) {
		long hundredths = Math.max(0L, ms) / 10L;
		long seconds = hundredths / 100L;
		long rest = hundredths % 100L;
		if (seconds >= 60L) {
			return String.format(Locale.ROOT, "%d:%02d.%02d", seconds / 60L, seconds % 60L, rest);
		}
		return String.format(Locale.ROOT, "%d.%02d", seconds, rest);
	}

	public static boolean shown() {
		return StrayConfig.get().runSplits && (SkyblockLocation.inDungeon && runStart > 0L || HudLayout.editorOpen());
	}

	private static int[] columns(Font font, List<Line> lines) {
		int[] w = new int[4];
		for (Line line : lines) {
			w[0] = Math.max(w[0], font.width(line.name));
			w[1] = Math.max(w[1], font.width(line.time));
			w[2] = Math.max(w[2], font.width(line.best));
			w[3] = Math.max(w[3], font.width(line.diff));
		}
		return w;
	}

	private static float widthOf(int[] w) {
		float width = PAD * 2 + w[0] + GAP + w[1];
		if (w[2] > 0) {
			width += GAP + w[2];
		}
		if (w[3] > 0) {
			width += GAP + w[3];
		}
		return width;
	}

	public static float width(Font font) {
		List<Line> lines = lines(HudLayout.editorOpen() && runStart == 0L);
		return lines.isEmpty() ? 0f : widthOf(columns(font, lines));
	}

	public static float height() {
		List<Line> lines = lines(HudLayout.editorOpen() && runStart == 0L);
		return lines.isEmpty() ? 0f : PAD * 2 + lines.size() * LINE - 1;
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
		int[] w = columns(font, lines);
		float width = widthOf(w);
		float height = PAD * 2 + lines.size() * LINE - 1;
		HudLayout.apply(graphics, font, HudLayout.Id.RUN_SPLITS, () -> {
			HudChrome.panel(graphics, 0, 0, width, height, 5, Theme.HUD_WINDOW, Theme.HUD_LINE);
			float y = PAD;
			for (Line line : lines) {
				float x = PAD;
				text(graphics, font, line.name, x, y, 0xFFFFFFFF);
				x += w[0] + GAP;
				// Times line up on the right of their column.
				text(graphics, font, line.time, x + w[1] - font.width(line.time), y, 0xFFFFFFFF);
				x += w[1] + GAP;
				if (w[2] > 0) {
					text(graphics, font, line.best, x + w[2] - font.width(line.best), y, 0xFFAAAAAA);
					x += w[2] + GAP;
				}
				if (w[3] > 0 && !line.diff.isEmpty()) {
					text(graphics, font, line.diff, x + w[3] - font.width(line.diff), y, 0xFFFFFFFF);
				}
				y += LINE;
			}
		});
	}

	private static void text(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int color) {
		if (!text.isEmpty()) {
			GuiDraw.text(graphics, font, Component.literal(text), x, y, color, true);
		}
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
		List<Split> m7 = new ArrayList<>(f7);
		m7.add(Split.at("Dragons", null));
		FLOORS.put("M7", new Boss(maxor, List.copyOf(m7)));
	}
}
