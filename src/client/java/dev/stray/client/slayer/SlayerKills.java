package dev.stray.client.slayer;

import dev.stray.client.util.Text;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How many of each mob in the current slayer area are still needed to spawn the boss, from the
 * quest XP left and the XP per mob boosted by combat wisdom.
 */
public final class SlayerKills {
	public record Line(SlayerData.Mob mob, double xp, long kills, boolean overkill) {
	}

	public record Snapshot(String title, List<Line> lines, double missing, boolean wisdomKnown) {
	}

	private static final Pattern PROGRESS = Pattern.compile("\\(([\\d,.]+[kKmMbB]?)/([\\d,.]+[kKmMbB]?)\\)");
	private static final Pattern COMBO_EXPIRED = Pattern.compile("^Your Kill Combo has expired! You reached a .* Kill Combo!");
	private static final Pattern COMBO_WISDOM = Pattern.compile("^\\+\\d+ Kill Combo \\+(\\d+)☯ Combat Wisdom");
	private static final Pattern TAB_WISDOM = Pattern.compile("Combat Wisdom:\\s*☯?\\s*([\\d.,]+)");

	private static double missing = -1;
	private static double max = -1;
	private static int comboWisdom;
	private static double tabWisdom = -1;
	private static int tick;

	private SlayerKills() {
	}

	public static void onProgress(String progress) {
		Matcher m = PROGRESS.matcher(progress == null ? "" : progress);
		if (m.find()) {
			double current = SlayerApi.parseShort(m.group(1));
			max = SlayerApi.parseShort(m.group(2));
			missing = max - current;
		} else {
			missing = -1;
		}
	}

	public static void onChat(String text) {
		if (COMBO_EXPIRED.matcher(text).find()) {
			comboWisdom = 0;
		}
		Matcher m = COMBO_WISDOM.matcher(text);
		if (m.find()) {
			comboWisdom += Integer.parseInt(m.group(1));
		}
	}

	public static void tick(Minecraft client) {
		if (++tick % 40 != 0 || client.getConnection() == null) {
			return;
		}
		for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
			Component display = info.getTabListDisplayName();
			if (display == null) {
				continue;
			}
			Matcher m = TAB_WISDOM.matcher(Text.strip(display.getString()));
			if (m.find()) {
				tabWisdom = SlayerApi.parseShort(m.group(1));
				return;
			}
		}
	}

	public static Snapshot snapshot() {
		SlayerType type = SlayerApi.type();
		SlayerType area = SlayerApi.areaType() != null ? SlayerApi.areaType() : type;
		if (area == null || type == null || missing < 0 || max <= 0) {
			return new Snapshot("", List.of(), -1, tabWisdom >= 0);
		}
		List<SlayerData.Mob> mobs = new ArrayList<>(SlayerData.normalMobs(area, SkyblockLocation.poi));
		mobs.addAll(SlayerData.miniBosses(type, SlayerApi.tier()));
		double wisdomMult = 1 + 0.01 * combatWisdom(type);
		double mult = SlayerData.arbitraryMultiplier() * (1 + championBonus());
		List<Line> lines = new ArrayList<>();
		for (SlayerData.Mob mob : mobs) {
			double xp = mob.xp() * wisdomMult * mult;
			boolean overkill = xp > max;
			// A single kill can give at most 75% of the quest.
			xp = Math.min(xp, max * 0.75);
			long kills = (long) Math.ceil(missing / xp);
			lines.add(new Line(mob, xp, kills, overkill));
		}
		lines.sort(Comparator.comparingLong(Line::kills).reversed());
		return new Snapshot("Remaining " + area.displayName + " " + SlayerApi.tier() + " kills", lines, missing, tabWisdom >= 0);
	}

	private static double combatWisdom(SlayerType type) {
		double wisdom = Math.max(0, tabWisdom) + comboWisdom;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return wisdom;
		}
		ItemStack held = client.player.getMainHandItem();
		int weapon = SlayerData.weaponWisdom(type, ItemIds.skyblockId(held));
		if (weapon >= 0) {
			wisdom += weapon;
			int habanero = 0;
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				int level = NeuIds.enchant(client.player.getItemBySlot(slot), "ultimate_habanero_tactics");
				if (level == 4) {
					habanero += 1;
				} else if (level >= 5) {
					habanero += 2;
				}
			}
			wisdom += habanero * SlayerData.habaneroPerLevel();
		}
		return wisdom;
	}

	private static double championBonus() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0;
		}
		return SlayerData.champion(NeuIds.enchant(client.player.getMainHandItem(), "champion"));
	}
}
