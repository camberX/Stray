package dev.stray.client.mining;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrystalHollows {
	private static final Pattern COORDS = Pattern.compile("\\Dx?(\\d{3})(?=[, ]),? ?y?(\\d{2,3})(?=[, ]),? ?z?(\\d{3})\\D?(?!\\d)");
	private static final int UNKNOWN_CLEAR = 50;
	private static final Map<CrystalStructure, BlockPos> WAYPOINTS = new EnumMap<>(CrystalStructure.class);
	private static final List<StaticMark> NUCLEUS = List.of(
		new StaticMark(new BlockPos(551, 116, 551), "Precursor Remnants", 0x55FFFF),
		new StaticMark(new BlockPos(551, 116, 475), "Mithril Deposits", 0x55FF55),
		new StaticMark(new BlockPos(475, 116, 551), "Goblin Holdout", 0xFFAA00),
		new StaticMark(new BlockPos(475, 116, 475), "Jungle", 0xAA00AA),
		new StaticMark(new BlockPos(513, 106, 524), "Nucleus", 0xFF5555)
	);
	private static boolean locrawPending;
	private static boolean inHollows;
	private static String lastServer = "";

	private CrystalHollows() {
	}

	public static boolean active() {
		return StrayConfig.get().crystalHollowsWaypoints && SkyblockLocation.inCrystalHollows();
	}

	public static void tick(Minecraft client) {
		if (!StrayConfig.get().crystalHollowsWaypoints) {
			if (inHollows) {
				leave();
			}
			return;
		}
		boolean here = SkyblockLocation.inCrystalHollows();
		if (here != inHollows) {
			if (here) {
				enter(client);
			} else {
				leave();
			}
		}
		if (!here || client.player == null) {
			return;
		}
		String server = SkyblockLocation.server;
		if (!server.isBlank() && !server.equals(lastServer)) {
			WAYPOINTS.clear();
			lastServer = server;
		}
		if (!server.isBlank()) {
			CrystalHollowsSocket.tick(server);
		}
		CrystalStructure spot = CrystalStructure.fromLabel(SkyblockLocation.poi);
		if (spot == null) {
			spot = CrystalStructure.fromLabel(SkyblockLocation.area);
		}
		if (spot != null && !WAYPOINTS.containsKey(spot)) {
			add(spot, client.player.blockPosition());
		}
	}

	public static boolean allowChat(Component message, boolean overlay) {
		if (overlay || message == null) {
			return true;
		}
		String text = message.getString().replaceAll("§.", "");
		if (locrawPending && text.startsWith("{\"server\":") && text.endsWith("}")) {
			parseLocraw(text);
			locrawPending = false;
			return false;
		}
		if (text.startsWith("{\"server\":") && text.endsWith("}")) {
			parseLocraw(text);
		}
		if (!active()) {
			return true;
		}
		readNpc(text, Minecraft.getInstance());
		if (StrayConfig.get().crystalHollowsFindChat) {
			readCoords(text);
		}
		return true;
	}

	public static void acceptSocket(List<CrystalHollowsSocket.Incoming> incoming) {
		if (!StrayConfig.get().crystalHollowsWaypoints) {
			return;
		}
		for (CrystalHollowsSocket.Incoming item : incoming) {
			if (item.structure() == null || item.pos() == null || WAYPOINTS.containsKey(item.structure())) {
				continue;
			}
			add(item.structure(), item.pos());
		}
	}

	public static List<Mark> marks() {
		List<Mark> out = new ArrayList<>();
		if (!active()) {
			return out;
		}
		if (StrayConfig.get().crystalHollowsEntrances) {
			for (StaticMark nucleus : NUCLEUS) {
				out.add(new Mark(nucleus.label, nucleus.pos, nucleus.rgb, true));
			}
		}
		for (var entry : WAYPOINTS.entrySet()) {
			out.add(new Mark(entry.getKey().label, entry.getValue(), entry.getKey().rgb, false));
		}
		return out;
	}

	public static void dumpChat() {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null) {
			return;
		}
		List<Mark> lines = new ArrayList<>();
		for (var entry : WAYPOINTS.entrySet()) {
			lines.add(new Mark(entry.getKey().label, entry.getValue(), entry.getKey().rgb, false));
		}
		client.gui.getChat().addClientSystemMessage(
			Component.literal("Stray CH waypoints").withStyle(ChatFormatting.YELLOW)
		);
		if (lines.isEmpty()) {
			client.gui.getChat().addClientSystemMessage(
				Component.literal("None stored.").withStyle(ChatFormatting.GRAY)
			);
			return;
		}
		for (Mark mark : lines) {
			BlockPos pos = mark.pos();
			client.gui.getChat().addClientSystemMessage(
				Component.literal(mark.label() + "  " + pos.getX() + " " + pos.getY() + " " + pos.getZ())
					.withStyle(Style.EMPTY.withColor(mark.rgb() & 0xFFFFFF))
			);
		}
	}

	public static void onWorldChange() {
		leave();
	}

	public static void reset() {
		leave();
	}

	private static void enter(Minecraft client) {
		WAYPOINTS.clear();
		lastServer = "";
		locrawPending = false;
		inHollows = true;
		SkyblockLocation.locrawServer = "";
		SkyblockLocation.server = "";
		requestLocraw(client);
	}

	private static void leave() {
		WAYPOINTS.clear();
		locrawPending = false;
		inHollows = false;
		lastServer = "";
		CrystalHollowsSocket.disconnect();
	}

	private static void requestLocraw(Minecraft client) {
		if (client == null || client.player == null || client.player.connection == null) {
			return;
		}
		locrawPending = true;
		client.player.connection.sendCommand("locraw");
	}

	private static void parseLocraw(String text) {
		try {
			JsonObject json = JsonParser.parseString(text).getAsJsonObject();
			if (json.has("server")) {
				String id = json.get("server").getAsString();
				SkyblockLocation.locrawServer = id;
				SkyblockLocation.server = id;
			}
		} catch (Exception ignored) {
		}
	}

	private static void readNpc(String text, Minecraft client) {
		if (client == null || client.player == null) {
			return;
		}
		for (CrystalStructure structure : CrystalStructure.values()) {
			if (structure.matchesChat(text) && !WAYPOINTS.containsKey(structure)) {
				add(structure, client.player.blockPosition());
			}
		}
	}

	private static void readCoords(String text) {
		if (!text.contains(":")) {
			return;
		}
		String user = text.split(":", 2)[1];
		Matcher matcher = COORDS.matcher(user);
		if (!matcher.find()) {
			return;
		}
		BlockPos pos = new BlockPos(
			Integer.parseInt(matcher.group(1)),
			Integer.parseInt(matcher.group(2)),
			Integer.parseInt(matcher.group(3))
		);
		if (!inside(pos)) {
			return;
		}
		CrystalStructure named = null;
		String lower = user.toLowerCase(Locale.ROOT);
		for (CrystalStructure structure : CrystalStructure.values()) {
			if (structure == CrystalStructure.UNKNOWN) {
				continue;
			}
			for (String word : structure.label.toLowerCase(Locale.ROOT).split(" ")) {
				if (word.length() > 2 && lower.contains(word)) {
					named = structure;
					break;
				}
			}
			if (named != null) {
				break;
			}
		}
		if (named == null) {
			named = CrystalStructure.UNKNOWN;
		}
		if (!WAYPOINTS.containsKey(named)) {
			add(named, pos);
		}
	}

	private static void add(CrystalStructure structure, BlockPos pos) {
		if (structure == null || pos == null) {
			return;
		}
		if (structure != CrystalStructure.UNKNOWN && !inside(pos)) {
			return;
		}
		if (structure != CrystalStructure.UNKNOWN) {
			BlockPos unknown = WAYPOINTS.get(CrystalStructure.UNKNOWN);
			if (unknown != null && Vec3.atCenterOf(unknown).distanceTo(Vec3.atCenterOf(pos)) < UNKNOWN_CLEAR) {
				WAYPOINTS.remove(CrystalStructure.UNKNOWN);
			}
		}
		WAYPOINTS.put(structure, pos);
	}

	static boolean inside(BlockPos pos) {
		return pos.getX() >= 202 && pos.getX() <= 823
			&& pos.getZ() >= 202 && pos.getZ() <= 823
			&& pos.getY() >= 31 && pos.getY() <= 188;
	}

	public record Mark(String label, BlockPos pos, int rgb, boolean nucleus) {
	}

	private record StaticMark(BlockPos pos, String label, int rgb) {
	}
}
