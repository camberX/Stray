package dev.stray.client.visual;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * "Hide other names": every other player's name is shown as a made-up one, everywhere text
 * is drawn (chat, tab, nametags, scoreboard, titles, the leap menu, tooltips). It rides on the
 * same hooks as the nick hider. Names come from the tab list and the players around you; each
 * gets a stable fake name (the same person always gets the same one), and names are matched as
 * whole words, case-sensitively. Client-side only.
 */
public final class OtherNames {
	private static final Pattern MC_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final String[] FIRST = {
		"Silent", "Hollow", "Velvet", "Crimson", "Ashen", "Lunar", "Frost", "Ember", "Shadow", "Quiet",
		"Pale", "Rusty", "Misty", "Stormy", "Dusky", "Golden", "Iron", "Copper", "Feral", "Gentle",
		"Swift", "Sleepy", "Brave", "Lucky", "Clever", "Wild", "Cosmic", "Neon", "Cobalt", "Amber",
		"Jade", "Onyx"
	};
	private static final String[] SECOND = {
		"Fox", "Owl", "Wolf", "Raven", "Lynx", "Otter", "Moth", "Hare", "Crow", "Viper",
		"Heron", "Badger", "Falcon", "Panda", "Koala", "Gecko", "Bison", "Shark", "Tiger", "Finch",
		"Mole", "Newt", "Squid", "Wren", "Yak", "Stag", "Seal", "Toad", "Bat", "Puma",
		"Cat", "Elk"
	};

	/** Real name -> shown name. Swapped as a whole each refresh, read on the render thread. */
	private static volatile Map<String, String> names = Map.of();
	private static final Map<String, String> assigned = new HashMap<>();
	private static final Set<String> used = new HashSet<>();
	private static long nextRefresh;

	private OtherNames() {
	}

	public static boolean active() {
		return StrayConfig.get().hideOtherNames && !names.isEmpty();
	}

	/** Refresh the names, at most twice a second. Called every client tick. */
	public static void tick(Minecraft client) {
		if (!StrayConfig.get().hideOtherNames) {
			if (!names.isEmpty()) {
				names = Map.of();
			}
			return;
		}
		long now = System.nanoTime();
		if (now < nextRefresh) {
			return;
		}
		nextRefresh = now + 500_000_000L;
		String self = NickHider.realName();
		// Keeps names seen earlier this session (people who left still show their fake name in chat).
		Map<String, String> next = new HashMap<>(names);
		if (client.getConnection() != null) {
			for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
				add(next, info.getProfile().name(), self);
			}
		}
		if (client.level != null) {
			for (Player player : client.level.players()) {
				add(next, player.getGameProfile().name(), self);
			}
		}
		if (next.size() > 4096) {
			next.clear();
		}
		names = Map.copyOf(next);
	}

	private static final String RANKS = "(?:\\[[^\\]]{1,24}\\] )*";
	private static final String NAME = "([A-Za-z0-9_]{3,16})";
	/** Chat formats that name people who are not in your lobby (party, guild, DMs, party finder). */
	private static final Pattern[] CHAT_NAMES = {
		Pattern.compile("^(?:Party|Guild|Officer|Co-op) > " + RANKS + NAME + "(?: \\[[^\\]]{1,12}\\])?: "),
		Pattern.compile("^(?:From|To) " + RANKS + NAME + ": "),
		Pattern.compile("^Party Finder > " + NAME + " joined the (?:dungeon )?group!"),
		Pattern.compile("^" + RANKS + NAME + " (?:joined|left) the party\\."),
		Pattern.compile("^" + RANKS + NAME + " has (?:been removed from|disbanded) the party"),
		Pattern.compile("^You have joined " + RANKS + NAME + "'s? party!"),
		Pattern.compile("^" + RANKS + NAME + " has invited you to join "),
		Pattern.compile("^(?:Friend|Guild) > " + NAME + " (?:joined|left)\\."),
		Pattern.compile("^The party was transferred to " + RANKS + NAME),
	};
	/** "Party Members: [MVP+] A ● B ●" and "Party Leader: [VIP] C ●". */
	private static final Pattern PARTY_LIST = Pattern.compile("^Party (?:Leader|Moderators|Members): ");
	private static final Pattern PARTY_ENTRY = Pattern.compile(RANKS + NAME + " ●");
	/** Public chat "[392] ♲ [MVP+] Name: hi": the word right before the first ": ". */
	private static final Pattern PUBLIC = Pattern.compile("^(?:\\[\\d{1,3}\\] )?(?:\\S{1,2} )?" + RANKS + NAME + "(?: \\S{1,2})?: ");

	/** Learns names from incoming chat (real names, before anything is drawn). */
	public static void onChat(Component message) {
		if (!StrayConfig.get().hideOtherNames || message == null) {
			return;
		}
		String plain = net.minecraft.util.StringUtil.stripColor(message.getString()).trim();
		if (plain.isEmpty() || plain.startsWith("[NPC]") || plain.startsWith("[BOSS]") || plain.startsWith("[SKULL]")
			|| plain.startsWith("[STATUE]") || plain.startsWith("[CROWD]")) {
			return;
		}
		java.util.List<String> found = new java.util.ArrayList<>(4);
		for (Pattern pattern : CHAT_NAMES) {
			java.util.regex.Matcher m = pattern.matcher(plain);
			if (m.find()) {
				found.add(m.group(1));
			}
		}
		if (PARTY_LIST.matcher(plain).find()) {
			java.util.regex.Matcher m = PARTY_ENTRY.matcher(plain);
			while (m.find()) {
				found.add(m.group(1));
			}
		}
		if (found.isEmpty()) {
			java.util.regex.Matcher m = PUBLIC.matcher(plain);
			if (m.find()) {
				found.add(m.group(1));
			}
		}
		if (found.isEmpty()) {
			return;
		}
		String self = NickHider.realName();
		Map<String, String> next = new HashMap<>(names);
		int before = next.size();
		for (String name : found) {
			add(next, name, self);
		}
		if (next.size() != before) {
			names = Map.copyOf(next);
		}
	}

	/**
	 * /stray debug dev othername check: what the nearest other player's nametag holds, with
	 * zero-width spaces between the letters so the names themselves are not swapped on screen.
	 */
	public static String describeNearest(Minecraft client) {
		if (client.level == null || client.player == null) {
			return "No world.";
		}
		Player nearest = null;
		double best = Double.MAX_VALUE;
		for (Player player : client.level.players()) {
			if (player == client.player) {
				continue;
			}
			double d = player.distanceToSqr(client.player);
			if (d < best) {
				best = d;
				nearest = player;
			}
		}
		if (nearest == null) {
			return "No other player loaded.";
		}
		String profile = nearest.getGameProfile().name();
		String shown = nearest.getDisplayName() == null ? "" : nearest.getDisplayName().getString();
		return "profile=" + spaced(profile) + " known=" + names.containsKey(profile)
			+ " display=" + spaced(shown.replace('\u00A7', '&')) + " names=" + names.size();
	}

	private static String spaced(String text) {
		StringBuilder out = new StringBuilder(text.length() * 2);
		for (int i = 0; i < text.length(); i++) {
			out.append(text.charAt(i)).append('\u200B');
		}
		return out.toString();
	}

	/** /stray debug dev othername <name>: treat a name as another player's, for testing. */
	public static String addForTest(String name) {
		Map<String, String> next = new HashMap<>(names);
		add(next, name, NickHider.realName());
		names = Map.copyOf(next);
		return next.get(name);
	}

	private static void add(Map<String, String> map, String name, String self) {
		if (name == null || name.equalsIgnoreCase(self) || map.containsKey(name) || !MC_NAME.matcher(name).matches()) {
			return;
		}
		map.put(name, fakeFor(name));
	}

	/** A stable made-up name: picked from the real name's hash, bumped on a clash. */
	private static synchronized String fakeFor(String name) {
		String known = assigned.get(name);
		if (known != null) {
			return known;
		}
		int h = name.toLowerCase(java.util.Locale.ROOT).hashCode() & 0x7fffffff;
		String fake = null;
		for (int bump = 0; bump < 64; bump++) {
			int v = h + bump * 7919;
			String candidate = FIRST[v % FIRST.length] + SECOND[(v / FIRST.length) % SECOND.length] + (v / 1024 % 100);
			if (!used.contains(candidate)) {
				fake = candidate;
				break;
			}
		}
		if (fake == null) {
			fake = "Player" + (used.size() + 1);
		}
		used.add(fake);
		assigned.put(name, fake);
		return fake;
	}

	// ------------------------------------------------------------------ rewriting

	private static boolean nameChar(int c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	/** Every whole-word name in a string replaced; the same string back when there are none. */
	public static String rewrite(String text) {
		if (text == null || text.length() < 3 || !active()) {
			return text;
		}
		Map<String, String> map = names;
		StringBuilder out = null;
		int last = 0;
		int i = 0;
		int n = text.length();
		while (i < n) {
			if (!nameChar(text.charAt(i)) || i > 0 && nameChar(text.charAt(i - 1)) && !afterFormatCode(text, i)) {
				i++;
				continue;
			}
			int j = i;
			while (j < n && nameChar(text.charAt(j)) && j - i <= 16) {
				j++;
			}
			String fake = j - i >= 3 && j - i <= 16 ? map.get(text.substring(i, j)) : null;
			if (fake != null && (j >= n || !nameChar(text.charAt(j)))) {
				if (out == null) {
					out = new StringBuilder(n + 16);
				}
				out.append(text, last, i).append(fake);
				last = j;
			}
			i = j;
		}
		if (out == null) {
			return text;
		}
		return out.append(text, last, n).toString();
	}

	/** "§aName": the letter after § is a format code, so the name starts right after it. */
	private static boolean afterFormatCode(String text, int i) {
		return i >= 2 && text.charAt(i - 2) == '§';
	}

	public static Component rewrite(Component component) {
		if (component == null || !active()) {
			return component;
		}
		String plain = component.getString();
		if (rewrite(plain) == plain) {
			return component;
		}
		MutableComponent out = Component.empty();
		component.visit((style, text) -> {
			out.append(Component.literal(rewrite(text)).setStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static final ThreadLocal<StringBuilder> SEQ_TEXT = ThreadLocal.withInitial(StringBuilder::new);
	private static final ThreadLocal<java.util.ArrayList<Style>> SEQ_STYLES = ThreadLocal.withInitial(java.util.ArrayList::new);
	private static final ThreadLocal<it.unimi.dsi.fastutil.ints.IntArrayList> SEQ_CPS = ThreadLocal.withInitial(it.unimi.dsi.fastutil.ints.IntArrayList::new);

	/** The char sequence path, over the whole line (names split across components still match). */
	public static FormattedCharSequence rewrite(FormattedCharSequence sequence) {
		if (sequence == null || !active()) {
			return sequence;
		}
		StringBuilder text = SEQ_TEXT.get();
		java.util.ArrayList<Style> styles = SEQ_STYLES.get();
		it.unimi.dsi.fastutil.ints.IntArrayList orig = SEQ_CPS.get();
		text.setLength(0);
		styles.clear();
		orig.clear();
		sequence.accept((index, style, cp) -> {
			orig.add(cp);
			// One char per entry keeps text and styles aligned; supplementary chars are rare here.
			if (Character.isBmpCodePoint(cp)) {
				text.append((char) cp);
			} else {
				text.append('�');
			}
			styles.add(style);
			return true;
		});
		String plain = text.toString();
		String replaced = rewrite(plain);
		if (replaced == plain) {
			return sequence;
		}
		// Rebuild with the same styles: walk the original and replaced strings together.
		Map<String, String> map = names;
		java.util.ArrayList<int[]> outCps = new java.util.ArrayList<>();
		java.util.ArrayList<Style> outStyles = new java.util.ArrayList<>();
		int n = plain.length();
		int i = 0;
		while (i < n) {
			boolean start = nameChar(plain.charAt(i)) && (i == 0 || !nameChar(plain.charAt(i - 1)));
			if (start) {
				int j = i;
				while (j < n && nameChar(plain.charAt(j)) && j - i <= 16) {
					j++;
				}
				String fake = j - i >= 3 && j - i <= 16 && (j >= n || !nameChar(plain.charAt(j))) ? map.get(plain.substring(i, j)) : null;
				if (fake != null) {
					Style style = styles.get(i);
					for (int k = 0; k < fake.length(); k++) {
						outCps.add(new int[]{fake.charAt(k)});
						outStyles.add(style);
					}
					i = j;
					continue;
				}
			}
			outCps.add(new int[]{orig.getInt(i)});
			outStyles.add(styles.get(i));
			i++;
		}
		int count = outCps.size();
		int[] cps = new int[count];
		Style[] st = new Style[count];
		for (int k = 0; k < count; k++) {
			cps[k] = outCps.get(k)[0];
			st[k] = outStyles.get(k);
		}
		return sink -> {
			for (int k = 0; k < count; k++) {
				if (!sink.accept(k, st[k], cps[k])) {
					return false;
				}
			}
			return true;
		};
	}
}
