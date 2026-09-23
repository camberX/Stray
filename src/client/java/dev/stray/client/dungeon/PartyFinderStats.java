package dev.stray.client.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.profile.ProfileViewer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stats card when someone joins from the dungeon party finder.
 * {@code /stray pf <name>} prints the same card without a join.
 */
public final class PartyFinderStats {
	private static final Pattern JOIN = Pattern.compile(
		"^Party Finder > (\\w{1,16}) joined the dungeon group! \\((\\w+) Level (\\d+)\\)$"
	);
	private static final long[] CATA_XP = {
		0L, 50L, 125L, 235L, 395L, 625L, 955L, 1425L, 2095L, 3045L,
		4385L, 6275L, 8940L, 12700L, 17960L, 25340L, 35640L, 50040L, 70040L, 97640L,
		135640L, 188140L, 259640L, 356640L, 488640L, 668640L, 911640L, 1239640L, 1684640L, 2284640L,
		3084640L, 4149640L, 5559640L, 7459640L, 9959640L, 13259640L, 17559640L, 23159640L, 30359640L, 39559640L,
		51559640L, 66559640L, 85559640L, 109559640L, 139559640L, 177559640L, 225559640L, 285559640L, 360559640L, 453559640L,
		569809640L
	};
	private static final long CATA_OVERFLOW = 200_000_000L;
	/** Cumulative shards syphoned to reach each attribute level. Index is the level. */
	private static final int[] RARE_SYPHON = {0, 1, 3, 6, 9, 13, 17, 22, 28, 36, 48};
	private static final int[] EPIC_SYPHON = {0, 1, 2, 4, 6, 9, 12, 16, 20, 25, 32};
	private static final Style BAR = Style.EMPTY.withColor(0x2FB5FF);
	private static final Style TEXT = Style.EMPTY.withColor(0xF4F7FB);
	private static final Style DIM = Style.EMPTY.withColor(0x8B93A7);
	private static final Style DOT = Style.EMPTY.withColor(0x5C6570);
	private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

	private PartyFinderStats() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("pf")
			.executes(context -> {
				show(selfName(), null, true);
				return 1;
			})
			.then(ClientCommands.argument("username", StringArgumentType.word())
				.executes(context -> {
					show(StringArgumentType.getString(context, "username"), null, true);
					return 1;
				}));
	}

	public static void onChat(Component message) {
		if (message == null || !StrayConfig.get().partyFinderStats) {
			return;
		}
		Matcher match = JOIN.matcher(plain(message.getString()));
		if (!match.find()) {
			return;
		}
		String name = match.group(1);
		if (isSelf(name)) {
			return;
		}
		show(name, match.group(2) + " " + match.group(3), false);
	}

	public static void show(String raw, String role, boolean announce) {
		String name = raw == null ? "" : raw.trim();
		if (name.isBlank()) {
			name = selfName();
		}
		if (name.isBlank()) {
			say(line("Need a username."));
			return;
		}
		String key = name.toLowerCase(Locale.ROOT);
		if (!PENDING.add(key)) {
			return;
		}
		if (announce) {
			say(line("Looking up ").append(Component.literal(name).setStyle(TEXT)).append(Component.literal("…").setStyle(DIM)));
		}
		String lookup = name;
		Util.nonCriticalIoPool().execute(() -> {
			try {
				ProfileViewer.RawMember member = ProfileViewer.rawMember(lookup);
				if (member == null || member.member() == null) {
					say(line("No Skyblock profile for ").append(Component.literal(lookup).setStyle(TEXT)).append(Component.literal(".").setStyle(DIM)));
					return;
				}
				print(member.name().isBlank() ? lookup : member.name(), role, member.member());
			} finally {
				PENDING.remove(key);
			}
		});
	}

	private static void print(String name, String role, JsonObject member) {
		JsonObject dungeons = object(member, "dungeons");
		JsonObject types = object(dungeons, "dungeon_types");
		JsonObject catacombs = object(types, "catacombs");
		JsonObject master = object(types, "master_catacombs");
		int cata = level(num(catacombs, "experience"));
		double classAvg = classAverage(object(dungeons, "player_classes"));
		int secrets = (int) num(dungeons, "secrets");
		if (secrets == 0) {
			secrets = (int) num(object(object(member, "player_stats"), "dungeons"), "secrets_found");
		}
		List<Floor> normal = floors(catacombs, false);
		List<Floor> masterFloors = floors(master, true);
		int runs = completions(normal) + completions(masterFloors);
		int mp = magicalPower(member);
		Integer blood = bloodMobs(member);
		int murkbat = shardLevel(member, "rekindle", RARE_SYPHON);
		int prince = shardLevel(member, "reborn", EPIC_SYPHON);
		List<ProfileViewer.SlotItem> armor = ProfileViewer.storedItems(member, "inv_armor", "armor");
		boolean apiOff = armor.isEmpty() && mp <= 0;

		List<Component> lines = new ArrayList<>();
		MutableComponent header = line("");
		header.append(Component.literal(name).setStyle(TEXT.withBold(true)));
		if (role != null && !role.isBlank()) {
			header.append(Component.literal("  ").setStyle(DIM));
			header.append(Component.literal(role).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
		} else {
			String selected = pretty(string(dungeons, "selected_dungeon_class"));
			if (!selected.isBlank()) {
				header.append(Component.literal("  ").setStyle(DIM));
				header.append(Component.literal(selected).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
			}
		}
		lines.add(header);
		lines.add(stats(
			label("Cata", ChatFormatting.RED), value(Integer.toString(cata)),
			label("Class", ChatFormatting.LIGHT_PURPLE), value(String.format(Locale.ROOT, "%.1f", classAvg)),
			label("MP", ChatFormatting.DARK_PURPLE), value(apiOff ? "API off" : Integer.toString(mp))
		));
		lines.add(stats(
			label("Secrets", ChatFormatting.GREEN), value(secrets + average(secrets, runs)),
			label("Blood", ChatFormatting.DARK_RED), value(blood == null ? "—" : Integer.toString(blood))
		));
		lines.add(stats(
			label("Murkbat", ChatFormatting.GOLD), value(shard(murkbat)),
			label("Prince", ChatFormatting.YELLOW), value(shard(prince))
		));
		lines.add(pets(member));
		lines.add(stats(
			label("Power", ChatFormatting.BLUE), value(power(member)),
			label("Arrow", ChatFormatting.GOLD), value(arrow(member))
		));
		lines.add(armorLine(armor, apiOff));
		lines.add(floorLine("Catacombs", ChatFormatting.GREEN, normal));
		lines.add(floorLine("Master", ChatFormatting.DARK_RED, masterFloors));
		for (Component row : lines) {
			say(row);
		}
	}

	private static MutableComponent pets(JsonObject member) {
		MutableComponent line = line("");
		line.append(label("Pets", ChatFormatting.GRAY));
		JsonArray pets = array(object(member, "pets_data"), "pets");
		if (pets == null) {
			pets = array(member, "pets");
		}
		List<JsonObject> shown = new ArrayList<>();
		if (pets != null) {
			for (JsonElement element : pets) {
				if (element != null && element.isJsonObject()) {
					JsonObject pet = element.getAsJsonObject();
					if (!string(pet, "type").isBlank()) {
						if (bool(pet, "active")) {
							shown.add(0, pet);
						} else {
							shown.add(pet);
						}
					}
				}
			}
		}
		if (shown.isEmpty()) {
			line.append(Component.literal("None").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
			return line;
		}
		int limit = Math.min(4, shown.size());
		for (int i = 0; i < limit; i++) {
			if (i > 0) {
				line.append(Component.literal(", ").setStyle(DIM));
			}
			JsonObject pet = shown.get(i);
			String type = shortPet(string(pet, "type"));
			Style color = Style.EMPTY.withColor(petColor(string(pet, "tier")));
			if (bool(pet, "active")) {
				color = color.withBold(true);
			}
			line.append(Component.literal(type).setStyle(color));
		}
		if (shown.size() > limit) {
			line.append(Component.literal(" +" + (shown.size() - limit)).setStyle(DIM));
		}
		return line;
	}

	private static MutableComponent armorLine(List<ProfileViewer.SlotItem> armor, boolean apiOff) {
		MutableComponent line = line("");
		line.append(label("Armor", ChatFormatting.GRAY));
		if (armor.isEmpty()) {
			line.append(Component.literal(apiOff ? "API off" : "—").setStyle(apiOff ? Style.EMPTY.withColor(ChatFormatting.RED) : DIM));
			return line;
		}
		List<ProfileViewer.SlotItem> pieces = new ArrayList<>(armor);
		java.util.Collections.reverse(pieces);
		boolean any = false;
		for (ProfileViewer.SlotItem piece : pieces) {
			if (piece == null || piece.empty()) {
				continue;
			}
			if (any) {
				line.append(Component.literal(" · ").setStyle(DOT));
			}
			any = true;
			String name = piece.name() == null || piece.name().isBlank() ? pretty(piece.id()) : piece.name();
			MutableComponent hover = legacy(name, null);
			if (piece.lore() != null) {
				for (String row : piece.lore()) {
					hover.append(Component.literal("\n"));
					hover.append(legacy(row == null ? "" : row, null));
				}
			}
			line.append(legacy(name, new HoverEvent.ShowText(hover)));
		}
		if (!any) {
			line.append(Component.literal("—").setStyle(DIM));
		}
		return line;
	}

	private static MutableComponent floorLine(String title, ChatFormatting color, List<Floor> floors) {
		MutableComponent line = line("");
		line.append(label(title, color));
		Floor best = null;
		for (Floor floor : floors) {
			if (floor.completions > 0) {
				best = floor;
			}
		}
		if (best == null) {
			line.append(Component.literal("—").setStyle(DIM));
			return line;
		}
		MutableComponent hover = Component.empty();
		for (int i = 0; i < floors.size(); i++) {
			Floor floor = floors.get(i);
			if (i > 0) {
				hover.append(Component.literal("\n"));
			}
			String detail = floor.completions <= 0
				? floor.name + "  DNF"
				: floor.name + "  ×" + floor.completions + "   S+ " + clock(floor.sPlus);
			hover.append(Component.literal(detail).setStyle(floor.completions <= 0 ? Style.EMPTY.withColor(ChatFormatting.RED) : TEXT));
		}
		HoverEvent event = new HoverEvent.ShowText(hover);
		line.append(Component.literal(best.name + "  " + clock(best.sPlus)).setStyle(TEXT.withHoverEvent(event)));
		line.append(Component.literal("  ×" + best.completions).setStyle(DIM.withHoverEvent(event)));
		return line;
	}

	private static MutableComponent stats(Object... parts) {
		MutableComponent line = line("");
		for (int i = 0; i < parts.length; i += 2) {
			if (i > 0) {
				line.append(Component.literal("   ").setStyle(DOT));
			}
			line.append((Component) parts[i]);
			line.append((Component) parts[i + 1]);
		}
		return line;
	}

	private static MutableComponent line(String text) {
		return Component.literal("▌ ").setStyle(BAR).append(Component.literal(text).setStyle(DIM));
	}

	private static Component label(String text, ChatFormatting color) {
		return Component.literal(text + " ").setStyle(Style.EMPTY.withColor(color));
	}

	private static Component value(String text) {
		return Component.literal(text).setStyle(TEXT);
	}

	private static String shard(int level) {
		return Integer.toString(Math.max(0, level));
	}

	private static String average(int secrets, int runs) {
		if (runs <= 0) {
			return "";
		}
		return String.format(Locale.ROOT, " (%.2f)", secrets / (double) runs);
	}

	/**
	 * {@code attributes.stacks} stores how many shards were syphoned, not the level.
	 * Murkbat is Rekindle (rare) and Prince is Reborn (epic).
	 */
	private static int shardLevel(JsonObject member, String attribute, int[] cumulative) {
		JsonObject stacks = object(object(member, "attributes"), "stacks");
		if (stacks == null || !stacks.has(attribute)) {
			return 0;
		}
		int syphoned = (int) num(stacks, attribute);
		int level = 0;
		for (int i = 1; i < cumulative.length; i++) {
			if (syphoned >= cumulative[i]) {
				level = i;
			}
		}
		return level;
	}

	private static String norm(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
	}

	private static int magicalPower(JsonObject member) {
		JsonObject bag = object(member, "accessory_bag_storage");
		int power = (int) num(bag, "highest_magical_power");
		if (power == 0) {
			power = (int) num(member, "magical_power");
		}
		if (power == 0) {
			power = (int) num(bag, "magical_power");
		}
		return power;
	}

	private static String power(JsonObject member) {
		String selected = string(object(member, "accessory_bag_storage"), "selected_power");
		if (selected.isBlank()) {
			selected = string(member, "selected_power");
		}
		return selected.isBlank() ? "—" : pretty(selected);
	}

	private static String arrow(JsonObject member) {
		String named = string(member, "favorite_arrow");
		if (!named.isBlank()) {
			return pretty(named);
		}
		List<ProfileViewer.SlotItem> quiver = ProfileViewer.storedItems(member, "quiver", "inv_quiver", "quiver_contents");
		ProfileViewer.SlotItem best = null;
		for (ProfileViewer.SlotItem item : quiver) {
			if (item == null || item.empty()) {
				continue;
			}
			if (best == null || item.count() > best.count()) {
				best = item;
			}
		}
		if (best == null) {
			return "—";
		}
		String name = best.name() == null || best.name().isBlank() ? pretty(best.id()) : plain(best.name());
		return name.isBlank() ? "—" : name;
	}

	private static Integer bloodMobs(JsonObject member) {
		JsonObject stats = object(member, "player_stats");
		if (stats != null && stats.has("blood_mobs_killed")) {
			return (int) num(stats, "blood_mobs_killed");
		}
		JsonObject dungeons = object(member, "dungeons");
		if (dungeons != null && dungeons.has("blood_mobs_killed")) {
			return (int) num(dungeons, "blood_mobs_killed");
		}
		JsonObject kills = object(stats, "kills");
		if (kills == null) {
			return null;
		}
		int total = 0;
		boolean found = false;
		for (String key : kills.keySet()) {
			String norm = norm(key);
			if (norm.contains("lost_adventurer") || norm.contains("frozen_adventurer") || norm.contains("angry_archaeologist")) {
				total += (int) num(kills, key);
				found = true;
			}
		}
		return found ? total : null;
	}

	private static double classAverage(JsonObject classes) {
		String[] keys = {"healer", "mage", "berserk", "archer", "tank"};
		double sum = 0d;
		for (String key : keys) {
			sum += level(num(object(classes, key), "experience"));
		}
		return sum / keys.length;
	}

	private static int level(double xp) {
		int level = 0;
		for (int i = 1; i < CATA_XP.length; i++) {
			if (xp >= CATA_XP[i]) {
				level = i;
			} else {
				break;
			}
		}
		if (level >= CATA_XP.length - 1) {
			level += (int) Math.floor(Math.max(0d, xp - CATA_XP[CATA_XP.length - 1]) / CATA_OVERFLOW);
		}
		return level;
	}

	private static List<Floor> floors(JsonObject type, boolean master) {
		List<Floor> out = new ArrayList<>();
		JsonObject completions = object(type, "tier_completions");
		JsonObject best = object(type, "fastest_time_s_plus");
		int start = master ? 1 : 0;
		for (int i = start; i <= 7; i++) {
			String name = master ? "M" + i : (i == 0 ? "Ent" : "F" + i);
			out.add(new Floor(name, keyed(completions, i), keyed(best, i)));
		}
		return out;
	}

	private static int completions(List<Floor> floors) {
		int total = 0;
		for (Floor floor : floors) {
			total += floor.completions;
		}
		return total;
	}

	private static int keyed(JsonObject object, int key) {
		if (object == null) {
			return 0;
		}
		String raw = Integer.toString(key);
		if (object.has(raw)) {
			return (int) num(object, raw);
		}
		return (int) num(object, raw + ".0");
	}

	private static String clock(int millis) {
		if (millis <= 0) {
			return "—";
		}
		int total = millis / 1000;
		int minutes = total / 60;
		int seconds = total % 60;
		return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
	}

	private static String shortPet(String type) {
		String upper = type.toUpperCase(Locale.ROOT);
		if ("GOLDEN_DRAGON".equals(upper)) {
			return "Gdrag";
		}
		if ("ENDER_DRAGON".equals(upper)) {
			return "Edrag";
		}
		return pretty(type);
	}

	private static ChatFormatting petColor(String tier) {
		return switch (tier == null ? "" : tier.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> ChatFormatting.GREEN;
			case "RARE" -> ChatFormatting.BLUE;
			case "EPIC" -> ChatFormatting.DARK_PURPLE;
			case "LEGENDARY" -> ChatFormatting.GOLD;
			case "MYTHIC" -> ChatFormatting.LIGHT_PURPLE;
			default -> ChatFormatting.WHITE;
		};
	}

	private static String pretty(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1));
			}
		}
		return out.toString();
	}

	private static MutableComponent legacy(String raw, HoverEvent hover) {
		MutableComponent out = Component.empty();
		if (raw == null || raw.isEmpty()) {
			return out;
		}
		Style style = hover == null ? TEXT : TEXT.withHoverEvent(hover);
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char current = raw.charAt(i);
			if (current == '§' && i + 1 < raw.length()) {
				ChatFormatting formatting = ChatFormatting.getByCode(Character.toLowerCase(raw.charAt(i + 1)));
				if (formatting != null) {
					flush(out, buffer, style);
					style = formatting == ChatFormatting.RESET ? TEXT : style.applyFormat(formatting);
					if (hover != null) {
						style = style.withHoverEvent(hover);
					}
					i++;
					continue;
				}
			}
			buffer.append(current);
		}
		flush(out, buffer, style);
		return out;
	}

	private static void flush(MutableComponent out, StringBuilder buffer, Style style) {
		if (buffer.isEmpty()) {
			return;
		}
		out.append(Component.literal(buffer.toString()).setStyle(style));
		buffer.setLength(0);
	}

	private static void say(Component line) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		client.execute(() -> {
			if (client.gui != null && client.gui.getChat() != null) {
				client.gui.getChat().addClientSystemMessage(line);
			}
		});
	}

	private static boolean isSelf(String name) {
		String self = selfName();
		return !self.isBlank() && self.equalsIgnoreCase(name);
	}

	private static String selfName() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return "";
		}
		if (client.player != null && client.player.getGameProfile() != null) {
			return client.player.getGameProfile().name();
		}
		return client.getUser() == null ? "" : client.getUser().getName();
	}

	private static String plain(String text) {
		return text == null ? "" : text.replaceAll("§.", "").trim();
	}

	private static JsonObject object(JsonObject parent, String key) {
		if (parent == null || key == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
			return null;
		}
		return parent.getAsJsonObject(key);
	}

	private static JsonArray array(JsonObject parent, String key) {
		if (parent == null || key == null || !parent.has(key) || !parent.get(key).isJsonArray()) {
			return null;
		}
		return parent.getAsJsonArray(key);
	}

	private static String string(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			return object.get(key).getAsString();
		} catch (Exception ignored) {
			return "";
		}
	}

	private static double num(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return 0d;
		}
		try {
			return object.get(key).getAsDouble();
		} catch (Exception ignored) {
			return 0d;
		}
	}

	private static boolean bool(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return false;
		}
		JsonElement value = object.get(key);
		return value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
	}

	private record Floor(String name, int completions, int sPlus) {
	}
}
