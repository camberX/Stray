package dev.stray.client.slayer;

import dev.stray.client.config.SlayerSettings;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slayer alerts: boss spawning soon, wrong or missing quest, cocooned boss, kill times and
 * personal bests, and the alert panel those (and the boss features) show.
 */
public final class SlayerAlerts {
	public record Title(String text, int color, long until) {
	}

	private static final Pattern PROGRESS = Pattern.compile("\\(?([\\d,.]+[kK]?)/([\\d,.]+[kK]?)\\)?\\s");
	private static Title title;
	private static boolean warnedSoon;
	private static double lastCompletion;
	private static boolean needQuest;
	private static String needReason = "";
	private static long lastWarning;
	private static long slainAt;
	private static int pendingSounds;
	private static int soundTick;
	private static SoundEvent pendingSound;

	private SlayerAlerts() {
	}

	private static SlayerSettings settings() {
		return StrayConfig.get().slayer;
	}

	// ---------------------------------------------------------------- shared output

	/** Shows the alert panel for a number of ticks. */
	public static void title(String text, int rgb, int ticks) {
		title = new Title(text, 0xFF000000 | rgb, System.currentTimeMillis() + ticks * 50L);
	}

	public static Title currentTitle() {
		Title t = title;
		return t == null || System.currentTimeMillis() > t.until ? null : t;
	}

	public static void chat(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("§bStray §8» §e" + text));
		}
	}

	public static void sound(SoundEvent event, float pitch, float volume) {
		Minecraft client = Minecraft.getInstance();
		client.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
	}

	/** Plays a sound several times, a few ticks apart. */
	public static void repeatSound(SoundEvent event, int times) {
		pendingSound = event;
		pendingSounds = times;
		soundTick = 0;
	}

	public static void tick() {
		if (pendingSounds > 0 && pendingSound != null && soundTick++ % 2 == 0) {
			sound(pendingSound, 1.5f, 0.8f);
			pendingSounds--;
		}
		if (slainAt != 0 && System.currentTimeMillis() - slainAt > 5000) {
			slainAt = 0;
			if (SlayerApi.state() == SlayerApi.State.SLAIN) {
				needQuest = true;
				needReason = "You have no Auto-Slayer active!";
			}
		}
	}

	// ---------------------------------------------------------------- inputs

	public static void onProgress(String progress) {
		SlayerSettings s = settings();
		if (!s.alerts || !s.spawnSoon || !SlayerApi.plausibleArea() || !SlayerApi.hasQuest()) {
			return;
		}
		Matcher m = PROGRESS.matcher(progress + " ");
		if (!m.find()) {
			return;
		}
		double total = SlayerApi.parseShort(m.group(2));
		if (total <= 0) {
			return;
		}
		double completion = SlayerApi.parseShort(m.group(1)) / total;
		if (completion > s.spawnSoonPercent / 100.0) {
			if (!warnedSoon || (s.spawnSoonRepeat && completion != lastCompletion)) {
				sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.8f);
				chat("Slayer boss soon!");
				title("Slayer boss soon!", 0xFFFF55, 40);
				warnedSoon = true;
			}
		} else {
			warnedSoon = false;
		}
		lastCompletion = completion;
	}

	public static void onState(SlayerApi.State state) {
		SlayerSettings s = settings();
		switch (state) {
			case GRINDING -> needQuest = false;
			case FAILED -> {
				needQuest = true;
				needReason = "The old slayer quest has failed!";
			}
			case SLAIN -> slainAt = System.currentTimeMillis();
			case COCOONED -> {
				if (s.alerts && s.cocoonTitle) {
					title("Slayer boss cocooned!", 0xFFAA00, 50);
				}
				if (s.alerts && s.cocoonSound) {
					repeatSound(SoundEvents.NOTE_BLOCK_PLING.value(), 10);
				}
			}
			default -> {
			}
		}
	}

	public static void onQuestComplete() {
		SlayerSettings s = settings();
		long start = SlayerApi.questStartMs();
		if (!s.alerts || !s.questTime || start == 0) {
			return;
		}
		String time = SlayerBosses.formatTime(System.currentTimeMillis() - start);
		chat(s.timeCompact ? "Quest took §b" + time + "§e in total." : "Slayer quest took §b" + time + "§e to complete.");
	}

	/** A hit landed on a mob near the player: warn about a missing or wrong quest. */
	public static void onSlayerMobHit(SlayerType areaType) {
		SlayerSettings s = settings();
		if (!s.alerts || !s.questWarning) {
			return;
		}
		SlayerType active = SlayerApi.type();
		if (active != null && active != areaType) {
			warn("Wrong slayer!", "Wrong slayer selected! You have " + active.displayName + " selected and you are in a " + areaType.displayName + " area!");
			return;
		}
		if (needQuest) {
			warn("New slayer quest!", "Start a new slayer quest! " + needReason);
		}
	}

	private static void warn(String titleText, String message) {
		long now = System.currentTimeMillis();
		if (now - lastWarning < 10_000) {
			return;
		}
		lastWarning = now;
		chat(message);
		if (settings().questWarningTitle) {
			title(titleText, 0xFFFF55, 40);
		}
	}
}
