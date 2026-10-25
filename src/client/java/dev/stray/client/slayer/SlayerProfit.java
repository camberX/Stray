package dev.stray.client.slayer;

import dev.stray.client.config.SlayerSettings;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.SkyblockItems;
import dev.stray.client.profile.ProfileViewer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Slayer profit per quest category (e.g. "Voidgloom Seraph IV"): boss drops (inventory and sack
 * pickups of that boss's drops while its quest is active in its area), coins from mob kills,
 * quest costs and bosses killed. Kept for all time and for this session.
 */
public final class SlayerProfit {
	public record Row(String id, String name, long amount, long drops, double value) {
	}

	public record Snapshot(String title, List<Row> rows, long mobCoins, long spawnCost, long bosses, long uptimeMs,
		double itemValue, double profit, boolean session) {
		public double perBoss() {
			return bosses <= 0 ? 0 : profit / bosses;
		}

		public double perHour() {
			return uptimeMs < 60_000 ? 0 : profit / (uptimeMs / 3_600_000d);
		}
	}

	private static final Map<String, SlayerSettings.Profit> SESSION = new HashMap<>();
	private static long lastActiveMs;
	private static long pendingCost;
	private static long pendingCostAt;

	private SlayerProfit() {
	}

	private static SlayerSettings settings() {
		return StrayConfig.get().slayer;
	}

	private static boolean tracking() {
		return SlayerApi.hasQuest() && SlayerApi.inCorrectArea();
	}

	private static List<SlayerSettings.Profit> targets() {
		String category = SlayerApi.category();
		if (category.isEmpty()) {
			return List.of();
		}
		SlayerSettings.Profit total = settings().profitData.computeIfAbsent(category, k -> new SlayerSettings.Profit());
		SlayerSettings.Profit session = SESSION.computeIfAbsent(category, k -> new SlayerSettings.Profit());
		return List.of(total, session);
	}

	// ---------------------------------------------------------------- inputs

	public static void onCategoryChange(String old, String now) {
		lastActiveMs = 0;
	}

	public static void onQuestStart() {
		SlayerType type = SlayerApi.type();
		if (type != null) {
			// The real cost shows as a purse drop right after; the table is the fallback.
			pendingCost = SlayerData.spawnCost(type, SlayerApi.tier());
			pendingCostAt = System.currentTimeMillis();
		}
	}

	public static void onQuestComplete() {
		flushCost();
		for (SlayerSettings.Profit p : targets()) {
			p.bosses++;
		}
	}

	public static void addSpawnCost(long coins) {
		if (coins <= 0 || coins > 1_000_000) {
			return;
		}
		pendingCost = 0;
		for (SlayerSettings.Profit p : targets()) {
			p.spawnCost += coins;
		}
	}

	private static void flushCost() {
		if (pendingCost > 0 && System.currentTimeMillis() - pendingCostAt > 4000) {
			long cost = pendingCost;
			pendingCost = 0;
			for (SlayerSettings.Profit p : targets()) {
				p.spawnCost += cost;
			}
		}
	}

	/** A purse change from the scoreboard; {@code nearQuestStart} if right after a quest started. */
	public static void onPurseChange(long delta, boolean nearQuestStart) {
		if (delta < 0 && nearQuestStart && pendingCost > 0 && -delta <= pendingCost * 2 && -delta <= 1_000_000) {
			long cost = -delta;
			pendingCost = 0;
			for (SlayerSettings.Profit p : targets()) {
				p.spawnCost += cost;
			}
			return;
		}
		flushCost();
		if (delta > 0 && delta < 100_000 && tracking() && net.minecraft.client.Minecraft.getInstance().screen == null) {
			for (SlayerSettings.Profit p : targets()) {
				p.mobCoins += delta;
			}
		}
	}

	public static void onItemGain(ItemStack stack, int amount) {
		if (!tracking()) {
			return;
		}
		String id = NeuIds.of(stack);
		add(id, amount);
	}

	public static void onSackGain(String id, long amount) {
		if (!tracking()) {
			return;
		}
		add(id, amount);
	}

	private static void add(String id, long amount) {
		SlayerType type = SlayerApi.type();
		if (id == null || type == null || amount <= 0 || !SlayerData.isDrop(type, id)) {
			return;
		}
		for (SlayerSettings.Profit p : targets()) {
			p.items.merge(id, amount, Long::sum);
			p.drops.merge(id, 1L, Long::sum);
		}
	}

	/** Called every client tick: counts active time while grinding in the right area. */
	public static void tick() {
		flushCost();
		long now = System.currentTimeMillis();
		if (!tracking()) {
			lastActiveMs = 0;
			return;
		}
		if (lastActiveMs != 0) {
			long dt = Math.min(5000, now - lastActiveMs);
			for (SlayerSettings.Profit p : targets()) {
				p.uptimeMs += dt;
			}
		}
		lastActiveMs = now;
	}

	public static void reset(boolean all) {
		String category = SlayerApi.category();
		if (all) {
			settings().profitData.remove(category);
		}
		SESSION.remove(category);
	}

	// ---------------------------------------------------------------- display

	public static Snapshot snapshot() {
		String category = SlayerApi.category();
		boolean session = settings().profitSession;
		SlayerSettings.Profit data = category.isEmpty() ? null
			: session ? SESSION.get(category) : settings().profitData.get(category);
		if (data == null) {
			data = new SlayerSettings.Profit();
		}
		List<Row> rows = new ArrayList<>();
		double itemValue = 0;
		int min = Math.max(0, settings().profitMinPrice);
		for (Map.Entry<String, Long> e : data.items.entrySet()) {
			double each = ProfileViewer.price(e.getKey());
			double value = each * e.getValue();
			itemValue += value;
			if (value < min) {
				continue;
			}
			rows.add(new Row(e.getKey(), nameOf(e.getKey()), e.getValue(), data.drops.getOrDefault(e.getKey(), 0L), value));
		}
		rows.sort(Comparator.comparingDouble(Row::value).reversed());
		double profit = itemValue + data.mobCoins - data.spawnCost;
		String title = category.isEmpty() ? "Slayer profit" : category;
		return new Snapshot(title, rows, data.mobCoins, data.spawnCost, data.bosses, data.uptimeMs, itemValue, profit, session);
	}

	public static String nameOf(String id) {
		SkyblockItems.Entry entry = SkyblockItems.get(id);
		if (entry != null && entry.name() != null && !entry.name().isEmpty()) {
			return entry.name().replaceAll("§.", "");
		}
		String base = id;
		String suffix = "";
		int semi = id.indexOf(';');
		if (semi > 0) {
			base = id.substring(0, semi);
			suffix = " " + roman(id.substring(semi + 1));
		}
		StringBuilder out = new StringBuilder();
		for (String part : base.toLowerCase().split("_")) {
			if (part.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return out + suffix;
	}

	private static String roman(String n) {
		return switch (n) {
			case "1" -> "I";
			case "2" -> "II";
			case "3" -> "III";
			case "4" -> "IV";
			case "5" -> "V";
			case "6" -> "VI";
			case "7" -> "VII";
			default -> n;
		};
	}
}
