package dev.stray.client.skill;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads skill XP from Hypixel's action bar: {@code +12.3 Farming (12,345/20,000)}. */
public final class SkillProgressTracker {
	private static final long SHOW_MS = 4_500L;
	private static final Pattern GAIN = Pattern.compile(
		"\\+\\s*([\\d,.]+(?:[kmb])?)\\s+(?:[^A-Za-z0-9]+\\s+)?"
			+ "(Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Taming|Carpentry|Runecrafting|Social|Dungeoneering|Catacombs)\\s+"
			+ "\\(([^)]+)\\)",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern RATIO = Pattern.compile(
		"^([\\d,.]+(?:[kmb])?)\\s*/\\s*([\\d,.]+(?:[kmb])?)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern PERCENT = Pattern.compile("^([\\d.]+)\\s*%$");

	private static Snapshot snapshot = Snapshot.empty();
	private static long lastNeeded = -1L;
	private static SkillKind lastKind;

	private SkillProgressTracker() {
	}

	public static void onActionBar(Component message) {
		if (message == null) {
			return;
		}
		String text = message.getString().replaceAll("§.", "").replace('\u00A0', ' ').trim();
		if (text.isEmpty()) {
			return;
		}
		Matcher gain = GAIN.matcher(text);
		if (!gain.find()) {
			return;
		}
		SkillKind kind = SkillKind.parse(gain.group(2));
		if (kind == null) {
			return;
		}
		String progress = gain.group(3).trim();
		if (progress.equalsIgnoreCase("MAXED") || progress.equalsIgnoreCase("MAX")) {
			int cap = kind.maxLevel();
			long needed = SkillXp.neededFor(kind, cap);
			if (needed < 0L) {
				needed = 0L;
			}
			push(kind, needed, needed, cap, cap);
			return;
		}
		Matcher ratio = RATIO.matcher(progress);
		if (ratio.matches()) {
			long current = parseAmount(ratio.group(1));
			long needed = parseAmount(ratio.group(2));
			if (current < 0L || needed <= 0L) {
				return;
			}
			int next = SkillXp.nextLevel(kind, needed);
			int level = next > 0 ? next - 1 : -1;
			lastKind = kind;
			lastNeeded = needed;
			push(kind, current, needed, level, next);
			return;
		}
		Matcher percent = PERCENT.matcher(progress);
		if (percent.matches() && kind == lastKind && lastNeeded > 0L) {
			double pct = Double.parseDouble(percent.group(1)) / 100d;
			long current = Math.round(Math.max(0d, Math.min(1d, pct)) * lastNeeded);
			int next = SkillXp.nextLevel(kind, lastNeeded);
			int level = next > 0 ? next - 1 : -1;
			push(kind, current, lastNeeded, level, next);
		}
	}

	public static Snapshot snapshot() {
		if (!snapshot.present()) {
			return snapshot;
		}
		if (System.currentTimeMillis() - snapshot.atMs > SHOW_MS) {
			snapshot = Snapshot.empty();
		}
		return snapshot;
	}

	public static void reset() {
		snapshot = Snapshot.empty();
		lastNeeded = -1L;
		lastKind = null;
	}

	private static void push(SkillKind kind, long current, long needed, int level, int nextLevel) {
		snapshot = new Snapshot(true, kind, current, needed, level, nextLevel, System.currentTimeMillis());
	}

	private static long parseAmount(String value) {
		if (value == null) {
			return -1L;
		}
		String text = value.toLowerCase(Locale.ROOT).replace(",", "").trim();
		double multiplier = 1d;
		if (text.endsWith("k")) {
			multiplier = 1_000d;
			text = text.substring(0, text.length() - 1);
		} else if (text.endsWith("m")) {
			multiplier = 1_000_000d;
			text = text.substring(0, text.length() - 1);
		} else if (text.endsWith("b")) {
			multiplier = 1_000_000_000d;
			text = text.substring(0, text.length() - 1);
		}
		try {
			return Math.max(0L, Math.round(Double.parseDouble(text) * multiplier));
		} catch (NumberFormatException ignored) {
			return -1L;
		}
	}

	public record Snapshot(
		boolean present,
		SkillKind kind,
		long current,
		long needed,
		int level,
		int nextLevel,
		long atMs
	) {
		private static Snapshot empty() {
			return new Snapshot(false, SkillKind.FARMING, 0L, 0L, -1, -1, 0L);
		}
	}
}
