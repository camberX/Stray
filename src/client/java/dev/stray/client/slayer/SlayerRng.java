package dev.stray.client.slayer;

import dev.stray.client.config.SlayerSettings;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slayer RNG meter: the stored XP from the chat line after each boss, the selected item (read
 * from the RNG meter menu or Maddox's slayer menu) and how many more bosses it takes.
 */
public final class SlayerRng {
	public record Snapshot(String slayer, String item, long meter, long needed, long gainPerBoss, String message) {
		public float fraction() {
			return needed <= 0 || meter < 0 ? 0f : (float) Math.min(1d, (double) meter / needed);
		}

		public long bossesLeft() {
			if (needed <= 0 || meter < 0 || gainPerBoss <= 0) {
				return -1;
			}
			long missing = needed - meter;
			return Math.max(1, (long) Math.ceil((double) missing / gainPerBoss));
		}
	}

	private static final Pattern METER = Pattern.compile("^\\s*RNG Meter - ([\\d,]+) Stored XP");
	private static final Pattern SET_ITEM = Pattern.compile("^You set your .* RNG Meter to drop .*!");
	private static final Pattern MENU = Pattern.compile("^(.*) RNG Meter$");
	private static final Pattern BOOK = Pattern.compile("^Enchanted Book \\((.*)\\)$");
	private static long droppedAt;
	private static int scanTick;

	private SlayerRng() {
	}

	private static SlayerSettings settings() {
		return StrayConfig.get().slayer;
	}

	/** The RNG meter key: slayer name without tier, NEU's name for the Bloodfiend. */
	public static String slayerKey() {
		String name = SlayerApi.slayerName();
		return name.equals("Bloodfiend") ? "Riftstalker Bloodfiend" : name;
	}

	private static SlayerSettings.Rng storage() {
		String key = slayerKey();
		if (key.isEmpty()) {
			return null;
		}
		return settings().rngData.computeIfAbsent(key, k -> new SlayerSettings.Rng());
	}

	public static void onCategoryChange() {
	}

	/** Returns false when the line should be hidden. */
	public static boolean onChat(Component message, String text) {
		if (!settings().rng) {
			return true;
		}
		if (settings().rngHideChat && SlayerApi.inCorrectArea() && SET_ITEM.matcher(text).find()) {
			return false;
		}
		Matcher m = METER.matcher(text);
		if (!m.find()) {
			return true;
		}
		long current = SlayerApi.parseShort(m.group(1));
		SlayerSettings.Rng rng = storage();
		if (rng == null) {
			return true;
		}
		long old = rng.meter;
		rng.meter = current;
		boolean hide = settings().rngHideChat;
		if (old >= 0) {
			boolean selected = rng.item != null && !rng.item.isEmpty();
			if (!selected && settings().rngWarnEmpty) {
				SlayerAlerts.title("No RNG meter item!", 0xFF5555, 40);
				SlayerAlerts.chat("No slayer RNG meter item is selected!");
			}
			long diff = current - old;
			if (diff > 0) {
				rng.gainPerBoss = diff;
			} else {
				// The meter emptied: the item dropped.
				long needed = rng.needed;
				double pct = needed > 0 ? Math.min(1d, (double) old / needed) * 100d : 0d;
				SlayerAlerts.chat(String.format("§dRNG meter §7dropped at §e%.2f%% §7XP (%,d/%,d)", pct, old, needed));
				rng.meter = 0;
				droppedAt = System.currentTimeMillis();
				hide = false;
			}
			if (!selected) {
				hide = false;
			}
		}
		return !hide;
	}

	/** Polls open menus for the selected RNG item. */
	public static void tick(Minecraft client) {
		if (!settings().rng || !(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		if (++scanTick % 10 != 0) {
			return;
		}
		String title = screen.getTitle().getString().replaceAll("§.", "").trim();
		Matcher menu = MENU.matcher(title);
		if (menu.find()) {
			if (!menu.group(1).trim().equals(slayerKey()) && !menu.group(1).trim().equals(SlayerApi.slayerName())) {
				return;
			}
			for (Slot slot : screen.getMenu().slots) {
				ItemStack stack = slot.getItem();
				if (stack.isEmpty()) {
					continue;
				}
				for (String line : lore(stack)) {
					if (line.contains("SELECTED")) {
						setGoal(NeuIds.of(stack), stack.getHoverName().getString().replaceAll("§.", ""));
						return;
					}
				}
			}
			return;
		}
		if (title.equals("Slayer") && screen.getMenu().slots.size() > 35) {
			ItemStack stack = screen.getMenu().slots.get(35).getItem();
			List<String> lore = lore(stack);
			if (lore.isEmpty() || !lore.get(0).equals(SlayerApi.slayerName()) && !lore.get(0).equals(slayerKey())) {
				return;
			}
			for (int i = 0; i + 1 < lore.size(); i++) {
				if (lore.get(i).equals("Selected Drop")) {
					String name = lore.get(i + 1);
					Matcher book = BOOK.matcher(name);
					String plain = book.find() ? book.group(1) : name;
					String id = SkyblockItems.idFromName(plain);
					if (id == null && book.matches()) {
						id = bookId(plain);
					}
					setGoal(id, plain);
					return;
				}
			}
		}
	}

	private static String bookId(String name) {
		String[] parts = name.trim().split(" ");
		if (parts.length < 2) {
			return null;
		}
		String level = parts[parts.length - 1];
		int n = switch (level) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			case "V" -> 5;
			case "VI" -> 6;
			case "VII" -> 7;
			default -> 0;
		};
		if (n == 0) {
			return null;
		}
		String base = String.join("_", java.util.Arrays.copyOf(parts, parts.length - 1)).toUpperCase();
		return base + ";" + n;
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> out = new ArrayList<>();
		if (lore == null) {
			return out;
		}
		for (Component line : lore.lines()) {
			out.add(line.getString().replaceAll("§.", "").trim());
		}
		return out;
	}

	private static void setGoal(String id, String name) {
		SlayerSettings.Rng rng = storage();
		if (rng == null) {
			return;
		}
		if (id == null) {
			rng.item = "";
			rng.itemName = "";
			rng.needed = -1;
			return;
		}
		rng.item = id;
		rng.itemName = name == null ? SlayerProfit.nameOf(id) : name;
		rng.needed = SlayerData.rngScore(slayerKey(), id);
	}

	public static Snapshot snapshot() {
		SlayerSettings.Rng rng = storage();
		String slayer = slayerKey();
		if (rng == null) {
			return new Snapshot(slayer, "", -1, -1, -1, "No slayer quest");
		}
		if (rng.needed < 0 && rng.item != null && !rng.item.isEmpty()) {
			rng.needed = SlayerData.rngScore(slayer, rng.item);
		}
		String message = null;
		if (rng.item == null || rng.item.isEmpty()) {
			message = System.currentTimeMillis() - droppedAt < 4000 ? "RNG item dropped!" : "No RNG item selected";
		} else if (rng.meter < 0 || rng.gainPerBoss < 0) {
			message = "Kill the boss twice to start";
		}
		return new Snapshot(slayer, rng.itemName == null ? "" : rng.itemName, rng.meter, rng.needed, rng.gainPerBoss, message);
	}
}
