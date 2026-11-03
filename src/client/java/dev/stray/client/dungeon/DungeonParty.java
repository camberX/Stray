package dev.stray.client.dungeon;

import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon teammates read from the tab list: name, class, class level and whether
 * they are dead, plus their player entity when it is loaded. Ported from
 * NoammAddons' DungeonListener (CC0), read-only: nothing here sends anything.
 */
public final class DungeonParty {
	public enum DungeonClass {
		ARCHER("Archer", 0xFFFFAA00),
		BERSERK("Berserk", 0xFFAA0000),
		HEALER("Healer", 0xFFFF55FF),
		MAGE("Mage", 0xFF55FFFF),
		TANK("Tank", 0xFF00AA00),
		EMPTY("Empty", 0xFFAAAAAA);

		public final String label;
		public final int color;

		DungeonClass(String label, int color) {
			this.label = label;
			this.color = color;
		}

		public static DungeonClass fromName(String name) {
			for (DungeonClass value : values()) {
				if (value.label.equalsIgnoreCase(name)) {
					return value;
				}
			}
			return EMPTY;
		}
	}

	public static final class Member {
		public final String name;
		public DungeonClass clazz;
		public int clazzLevel;
		public boolean dead;
		public PlayerSkin skin;
		public AbstractClientPlayer entity;

		Member(String name, DungeonClass clazz, int clazzLevel, PlayerSkin skin, boolean dead) {
			this.name = name;
			this.clazz = clazz;
			this.clazzLevel = clazzLevel;
			this.skin = skin;
			this.dead = dead;
		}
	}

	private static final Pattern TAB = Pattern.compile("^\\[\\d+] (?:\\[[^]]+] )*([A-Za-z0-9_]{1,16}) .*\\((\\w+)(?: (\\w+))?\\)$");
	private static final Pattern DOOR = Pattern.compile("^(?:\\[.+?] )?(\\w+) opened a WITHER door!$");

	private static final List<Member> TEAM = new CopyOnWriteArrayList<>();
	private static volatile Member lastDoorOpener;
	private static boolean wasInDungeon;
	private static int ticks;

	private DungeonParty() {
	}

	/** Everyone in the run, yourself included. */
	public static List<Member> all() {
		return TEAM;
	}

	/** Teammates without yourself, in tab-list order. */
	public static List<Member> others() {
		Minecraft client = Minecraft.getInstance();
		String self = client.getUser() == null ? "" : client.getUser().getName();
		List<Member> out = new ArrayList<>();
		for (Member member : TEAM) {
			if (!member.name.equals(self)) {
				out.add(member);
			}
		}
		return out;
	}

	public static Member self() {
		Minecraft client = Minecraft.getInstance();
		String self = client.getUser() == null ? "" : client.getUser().getName();
		for (Member member : TEAM) {
			if (member.name.equals(self)) {
				return member;
			}
		}
		return null;
	}

	public static Member lastDoorOpener() {
		return lastDoorOpener;
	}

	public static void tick(Minecraft client) {
		if (!SkyblockLocation.inDungeon) {
			if (wasInDungeon) {
				TEAM.clear();
				lastDoorOpener = null;
			}
			wasInDungeon = false;
			return;
		}
		wasInDungeon = true;
		if (client.getConnection() == null) {
			return;
		}
		if (++ticks % 5 == 0) {
			for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
				update(client, info);
			}
		}
		if (client.level != null) {
			for (Member member : TEAM) {
				if (member.entity != null && (member.entity.isRemoved() || member.entity.level() != client.level)) {
					member.entity = null;
				}
				if (member.entity == null) {
					for (var player : client.level.players()) {
						if (player.getName().getString().equals(member.name)) {
							member.entity = player;
							break;
						}
					}
				}
			}
		}
	}

	private static void update(Minecraft client, PlayerInfo info) {
		Component display = info.getTabListDisplayName();
		if (display == null) {
			return;
		}
		Matcher m = TAB.matcher(display.getString().trim());
		if (!m.matches()) {
			return;
		}
		String name = m.group(1);
		String clazz = m.group(2);
		int level = roman(m.group(3));
		PlayerInfo own = name.equals(info.getProfile().name()) ? info : client.getConnection().getPlayerInfo(name);
		PlayerSkin skin = (own == null ? info : own).getSkin();
		boolean dead = "DEAD".equals(clazz);
		for (Member member : TEAM) {
			if (member.name.equals(name)) {
				if (!dead) {
					member.clazz = DungeonClass.fromName(clazz);
				}
				member.clazzLevel = level;
				member.skin = skin;
				member.dead = dead;
				return;
			}
		}
		TEAM.add(new Member(name, DungeonClass.fromName(clazz), level, skin, dead));
	}

	/** Chat lines: who opened the last wither door. */
	public static void onChat(String plain) {
		if (!SkyblockLocation.inDungeon || plain == null) {
			return;
		}
		Matcher m = DOOR.matcher(plain);
		if (m.matches()) {
			for (Member member : TEAM) {
				if (member.name.equals(m.group(1))) {
					lastDoorOpener = member;
				}
			}
		}
	}

	private static int roman(String raw) {
		if (raw == null || raw.isBlank()) {
			return 0;
		}
		String s = raw.toUpperCase(Locale.ROOT);
		int total = 0;
		int prev = 0;
		for (int i = s.length() - 1; i >= 0; i--) {
			int v = switch (s.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				default -> 0;
			};
			if (v == 0) {
				try {
					return Integer.parseInt(raw);
				} catch (NumberFormatException e) {
					return 0;
				}
			}
			total += v < prev ? -v : v;
			prev = Math.max(prev, v);
		}
		return total;
	}
}
