package dev.stray.client.dungeon.m4;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.stray.Stray;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * BirdAddon's Spirit Bear personal bests and splits, and Extra Stats: a clickable per-bear
 * breakdown, the run time in ticks and the last bear breakdown after Thorn dies.
 */
public final class M4Stats {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Float> personalBests = new HashMap<>();
	private static boolean loaded;

	record Split(int bear, int spawnStart, int spawn, int death, int kill) {
	}

	static final List<Split> completedSplits = new ArrayList<>();

	private record Run(List<Integer> spawnStarts, List<Integer> spawns, List<Integer> kills, Integer bowReleased, Integer bowDrawn, int timer) {
	}

	private static final Map<UUID, Run> completedRuns = new LinkedHashMap<>();
	private static Runnable delayed;
	private static long delayedAt;

	private M4Stats() {
	}

	static Path file(String name) {
		return FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("m4").resolve(name);
	}

	// ---------------------------------------------------------------- personal bests

	private static void loadPersonalBests() {
		loaded = true;
		Path path = file("personalbests.json");
		if (!Files.isRegularFile(path)) {
			// BirdAddon keeps its own next to Odin's config; start from those.
			path = FabricLoader.getInstance().getConfigDir().resolve("odin").resolve("addons").resolve("m4personalbests.json");
		}
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			Map<String, Float> read = GSON.fromJson(Files.readString(path), new TypeToken<Map<String, Float>>() { }.getType());
			if (read != null) {
				personalBests.clear();
				personalBests.putAll(read);
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read M4 personal bests", exception);
		}
	}

	private static void savePersonalBests() {
		Path path = file("personalbests.json");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(personalBests));
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not save M4 personal bests", exception);
		}
	}

	static void onBearSpawn() {
		if (!M4.config().spiritBear) {
			return;
		}
		int bear = M4.spawnStartTimes.size();
		if (bear < 1) {
			return;
		}
		if (!loaded) {
			loadPersonalBests();
		}
		String key = (M4.master() ? "M4" : "F4") + " Bear " + bear;
		float seconds = M4.timer / 20f;
		float old = personalBests.getOrDefault(key, 9999f);
		String pb;
		if (old > seconds) {
			personalBests.put(key, seconds);
			savePersonalBests();
			pb = "§7(§d§lNew PB§r§7) Old PB was §8" + fixed(old, 2) + "s";
		} else {
			pb = "§8(§7" + fixed(old, 2) + "§8)s";
		}
		message("§d" + key + " §7took §b" + fixed(seconds, 2) + " §7" + pb);
	}

	static void onBearKill() {
		int index = M4.killTimes.size() - 1;
		if (index < 0) {
			return;
		}
		int spawnStart = index < M4.spawnStartTimes.size() ? M4.spawnStartTimes.get(index) : 0;
		int spawn = index < M4.spawnTimes.size() ? M4.spawnTimes.get(index) : spawnStart;
		int death = M4.killTimes.get(index);
		completedSplits.add(new Split(index + 1, spawnStart, spawn, death, death - spawn));
	}

	// ---------------------------------------------------------------- extra stats

	static void onEnd() {
		UUID runId = UUID.randomUUID();
		Run run = new Run(List.copyOf(M4.spawnStartTimes), List.copyOf(M4.spawnTimes), List.copyOf(M4.killTimes), M4.bowRelease, M4.bowDraw, M4.timer);
		completedRuns.put(runId, run);
		if (!M4.config().extraStats) {
			return;
		}
		Integer pickup = M4.bowPickup;
		int timer = M4.timer;
		delayedAt = System.currentTimeMillis() + 1000L;
		delayed = () -> {
			M4Config config = M4.config();
			if (config.extraStatsMenu) {
				statsMenu(runId, run);
			}
			if (config.extraStatsRunTime) {
				message("Thorn Defeated in " + fixed(timer / 20.0, 2) + "s");
			}
			int last = run.kills.size() - 1;
			if (config.extraStatsLastBear && last >= 0 && last < run.spawns.size()
				&& pickup != null && run.bowDrawn != null && run.bowReleased != null) {
				int kill = run.kills.get(last);
				int spawn = run.spawns.get(last);
				message("Spawn " + spawn / 20.0 + "s → Kill +" + (kill - spawn) / 20.0
					+ "s → Pickup +" + (pickup - kill) / 20.0
					+ "s → Hold +" + (run.bowDrawn - pickup) / 20.0
					+ "s → Release +" + (run.bowReleased - run.bowDrawn) / 20.0
					+ "s → End +" + (timer - run.bowReleased) / 20.0
					+ "s (" + (timer - spawn) / 20.0 + "s after spawn)");
			}
		};
	}

	static void clientTick() {
		if (delayed != null && System.currentTimeMillis() >= delayedAt) {
			Runnable run = delayed;
			delayed = null;
			run.run();
		}
	}

	private static void statsMenu(UUID runId, Run run) {
		MutableComponent line = Component.literal("Click to view stats: ").withStyle(ChatFormatting.YELLOW);
		int count = run.kills.size();
		for (int i = 1; i <= count; i++) {
			int bear = i;
			line.append(Component.literal("Bear " + bear).withStyle(style -> style
				.withColor(ChatFormatting.GOLD)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand("/bearstats " + runId + " " + bear))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to view Bear " + bear + " stats").withStyle(ChatFormatting.GRAY)))));
			if (i < count) {
				line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		message(line);
	}

	/** {@code /bearstats <run> <bear>}, from the clickable menu. */
	public static void showBear(String runId, int bear) {
		Run run;
		try {
			run = completedRuns.get(UUID.fromString(runId));
		} catch (IllegalArgumentException exception) {
			run = null;
		}
		int id = bear - 1;
		if (run == null || id < 0 || id >= run.kills.size() || id >= run.spawns.size() || id >= run.spawnStarts.size()) {
			message("No stats for that run or bear.");
			return;
		}
		message("Bear " + bear + " Stats:");
		message("Spawn Start: " + run.spawnStarts.get(id) / 20.0 + "s");
		message("Bear spawned: " + run.spawns.get(id) / 20.0 + "s");
		message("Bear Killed: " + run.kills.get(id) / 20.0 + "s (took " + (run.kills.get(id) - run.spawns.get(id)) / 20.0 + "s)");
		if (bear == run.kills.size()) {
			if (run.bowReleased != null && run.bowDrawn != null) {
				message("Last bow shot " + (run.bowDrawn - run.kills.get(id)) / 20.0 + "s after bear died (held down for " + (run.bowReleased - run.bowDrawn) / 20.0 + "s)");
			}
			if (run.bowReleased != null) {
				message("Thorn Died " + fixed((run.timer - run.kills.get(id)) / 20.0, 2) + "s after last bear kill, "
					+ fixed((run.timer - run.spawns.get(id)) / 20.0, 2) + "s after last bear spawn, "
					+ fixed((run.timer - run.bowReleased) / 20.0, 2) + "s after bow release");
			}
		}
	}

	static void onWorldLoad() {
		completedSplits.clear();
		loadPersonalBests();
	}

	// ---------------------------------------------------------------- helpers

	static String fixed(double value, int decimals) {
		return String.format(Locale.ROOT, "%." + Math.max(0, decimals) + "f", value);
	}

	public static void message(String text) {
		message(Component.literal(text));
	}

	public static void message(Component text) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.gui != null) {
				client.gui.hud.getChat().addClientSystemMessage(Component.literal("§bStray §8» §r").append(text));
			}
		});
	}
}
