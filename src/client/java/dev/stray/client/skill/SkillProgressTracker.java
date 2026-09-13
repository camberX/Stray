package dev.stray.client.skill;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.mixin.GuiAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads skill XP from Hypixel's action bar, including percent mode:
 * {@code +9.2 Farming (22.49%)} and {@code +12.3 Farming (12,345/20,000)}.
 */
public final class SkillProgressTracker {
	private static final long SHOW_MS = 4_500L;
	private static final String NAMES = SkillKind.ACTION_BAR_NAMES;
	private static final Pattern GAIN = Pattern.compile(
		"[+＋]\\s*([\\d,.]+(?:[kmb])?)\\s*(?:[^A-Za-z0-9]+\\s*)?"
			+ "(" + NAMES + ")\\s*\\(([^)]+)\\)",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern RATIO = Pattern.compile(
		"^([\\d,.]+(?:[kmb])?)\\s*/\\s*([\\d,.]+(?:[kmb])?)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern PERCENT = Pattern.compile("^([\\d.]+)\\s*%$");
	private static final Pattern TAB_LEVEL = Pattern.compile(
		"^[^A-Za-z]*(" + NAMES + ")\\s+(\\d{1,2})(?:\\D.*)?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern TAB_LEVEL_COLON = Pattern.compile(
		"^[^A-Za-z]*(" + NAMES + ")\\s*:\\s*(\\d{1,2})\\b",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern MENU_LEVEL = Pattern.compile(
		"(?:^|\\b)level\\s*:?\\s*(\\d{1,2})\\b",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern BLOB_SKILL = Pattern.compile(
		"\\b(" + NAMES + ")\\b",
		Pattern.CASE_INSENSITIVE
	);
	private static final Comparator<PlayerInfo> TAB_ORDER = Comparator
		.comparingInt((PlayerInfo info) -> -info.getTabListOrder())
		.thenComparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
		.thenComparing(info -> {
			PlayerTeam team = info.getTeam();
			return team == null ? "" : team.getName();
		})
		.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER);

	private static final Map<SkillKind, Integer> LEVELS = new EnumMap<>(SkillKind.class);

	private static Snapshot snapshot = Snapshot.empty();
	private static long lastNeeded = -1L;
	private static SkillKind lastKind;
	private static int lastLevelTick = Integer.MIN_VALUE;

	private SkillProgressTracker() {
	}

	public static void tick(Minecraft client) {
		if (client == null || client.player == null || !StrayConfig.get().skillProgressHudEnabled) {
			return;
		}
		poll(client);
	}

	public static void poll(Minecraft client) {
		if (client == null) {
			return;
		}
		refreshLevels(client);
		if (client.gui instanceof GuiAccessor accessor && accessor.stray$overlayMessageTime() > 0) {
			onActionBar(accessor.stray$overlayMessage());
		}
	}

	public static void onActionBar(Component message) {
		if (message == null) {
			return;
		}
		onActionBar(plain(message));
	}

	public static void onActionBar(String raw) {
		String text = clean(raw);
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
			remember(kind, cap);
			long needed = SkillXp.neededFor(kind, cap);
			push(kind, Math.max(0L, needed), Math.max(0L, needed), cap, cap, 100d);
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
			int level = next > 0 ? next - 1 : LEVELS.getOrDefault(kind, -1);
			remember(kind, level);
			lastKind = kind;
			lastNeeded = needed;
			double pct = needed > 0L ? (current * 100d) / needed : -1d;
			push(kind, current, needed, level, next, pct);
			return;
		}
		Matcher percent = PERCENT.matcher(progress);
		if (percent.matches()) {
			try {
				fromPercent(kind, Double.parseDouble(percent.group(1)));
			} catch (NumberFormatException ignored) {
			}
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
		LEVELS.clear();
		lastLevelTick = Integer.MIN_VALUE;
	}

	private static void fromPercent(SkillKind kind, double pct) {
		int level = LEVELS.getOrDefault(kind, -1);
		long needed = -1L;
		int next = -1;
		if (kind == lastKind && lastNeeded > 0L) {
			needed = lastNeeded;
			next = SkillXp.nextLevel(kind, needed);
			if (next > 0) {
				level = next - 1;
			}
		}
		if (needed <= 0L && level >= 0) {
			if (level >= kind.maxLevel()) {
				remember(kind, kind.maxLevel());
				long cap = SkillXp.neededFor(kind, kind.maxLevel());
				push(kind, Math.max(0L, cap), Math.max(0L, cap), kind.maxLevel(), kind.maxLevel(), 100d);
				return;
			}
			needed = SkillXp.neededAfter(kind, level);
			next = level + 1;
		}
		if (needed > 0L) {
			lastKind = kind;
			lastNeeded = needed;
			long current = Math.round(Math.max(0d, Math.min(1d, pct / 100d)) * needed);
			remember(kind, level);
			push(kind, current, needed, level, next, pct);
			return;
		}
		push(kind, 0L, 0L, level, -1, pct);
	}

	private static void refreshLevels(Minecraft client) {
		if (client.player == null) {
			return;
		}
		int tick = client.player.tickCount;
		if (lastLevelTick != Integer.MIN_VALUE && tick >= lastLevelTick && tick - lastLevelTick < 10) {
			return;
		}
		lastLevelTick = tick;
		readTab(client);
		readSkillsMenu(client);
	}

	private static void readTab(Minecraft client) {
		if (client.player == null) {
			return;
		}
		ClientPacketListener connection = client.player.connection;
		if (connection == null) {
			return;
		}
		List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
		infos.sort(TAB_ORDER);
		int limit = Math.min(80, infos.size());
		for (int i = 0; i < limit; i++) {
			String line = clean(tabName(infos.get(i)));
			if (line.isEmpty()) {
				continue;
			}
			Matcher named = TAB_LEVEL.matcher(line);
			if (!named.find()) {
				named = TAB_LEVEL_COLON.matcher(line);
				if (!named.find()) {
					continue;
				}
			}
			SkillKind kind = SkillKind.parse(named.group(1));
			if (kind == null) {
				continue;
			}
			try {
				remember(kind, Integer.parseInt(named.group(2)));
			} catch (NumberFormatException ignored) {
			}
		}
	}

	private static void readSkillsMenu(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		String title = clean(screen.getTitle()).toLowerCase(Locale.ROOT);
		if (!title.equals("your skills") && !title.equals("skills") && !title.endsWith(" skills")) {
			return;
		}
		AbstractContainerMenu menu = screen.getMenu();
		int chest = Math.max(0, menu.slots.size() - 36);
		for (int i = 0; i < chest; i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			readSkillItem(stack);
		}
	}

	private static void readSkillItem(ItemStack stack) {
		boolean prior = ItemAppearance.suppress();
		try {
			if (stack == null || stack.isEmpty()) {
				return;
			}
			String blob = itemBlob(stack);
			SkillKind kind = skillIn(blob);
			if (kind == null) {
				return;
			}
			Matcher level = MENU_LEVEL.matcher(blob);
			if (level.find()) {
				remember(kind, Integer.parseInt(level.group(1)));
				return;
			}
			Matcher named = TAB_LEVEL.matcher(clean(stack.getHoverName()));
			if (named.find()) {
				remember(kind, Integer.parseInt(named.group(2)));
			}
		} catch (NumberFormatException ignored) {
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static SkillKind skillIn(String blob) {
		if (blob == null || blob.isEmpty()) {
			return null;
		}
		Matcher named = BLOB_SKILL.matcher(blob);
		if (!named.find()) {
			return null;
		}
		return SkillKind.parse(named.group(1));
	}

	private static String itemBlob(ItemStack stack) {
		StringBuilder out = new StringBuilder();
		append(out, stack.get(DataComponents.CUSTOM_NAME));
		append(out, stack.getHoverName());
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				append(out, line);
			}
			for (Component line : lore.styledLines()) {
				append(out, line);
			}
		}
		return clean(out.toString());
	}

	private static void append(StringBuilder out, Component component) {
		if (component == null) {
			return;
		}
		out.append('\n');
		component.visit((style, text) -> {
			if (text != null) {
				out.append(text);
			}
			return Optional.empty();
		}, Style.EMPTY);
	}

	private static Component tabName(PlayerInfo info) {
		Component display = info.getTabListDisplayName();
		return display != null
			? display
			: PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
	}

	private static void remember(SkillKind kind, int level) {
		if (kind == null || level < 0) {
			return;
		}
		LEVELS.put(kind, Math.min(level, kind.maxLevel()));
	}

	private static void push(SkillKind kind, long current, long needed, int level, int nextLevel, double percent) {
		snapshot = new Snapshot(true, kind, current, needed, level, nextLevel, percent, System.currentTimeMillis());
	}

	private static String plain(Component message) {
		StringBuilder out = new StringBuilder();
		message.visit((style, text) -> {
			if (text != null) {
				out.append(text);
			}
			return Optional.empty();
		}, Style.EMPTY);
		String visited = out.toString();
		if (!visited.isBlank()) {
			return visited;
		}
		return message.getString();
	}

	private static String clean(Component component) {
		return component == null ? "" : clean(plain(component));
	}

	private static String clean(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		return text.replaceAll("§.", "")
			.replace('\u00A0', ' ')
			.replace('\u202F', ' ')
			.replace('＋', '+')
			.replaceAll("[^\\p{Alnum}\\p{Punct}\\s]+", " ")
			.replaceAll("\\s+", " ")
			.trim();
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
		double percent,
		long atMs
	) {
		private static Snapshot empty() {
			return new Snapshot(false, SkillKind.FARMING, 0L, 0L, -1, -1, -1d, 0L);
		}
	}
}
