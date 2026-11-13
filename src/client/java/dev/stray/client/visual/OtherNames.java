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
	/**
	 * Cut-off names ("UltraCharge0" for UltraCharge03, as the scoreboard trims them): every
	 * start of a known name missing at most {@link #MAX_CUT} letters, at least {@link #MIN_CUT}
	 * long (shorter starts are too often ordinary words). A start shared by two names maps to "" (left alone).
	 */
	private static volatile Map<String, String> prefixes = Map.of();
	private static final int MAX_CUT = 4;
	private static final int MIN_CUT = 10;
	/** Where each name was learned, for /stray debug dev othername why. */
	private static final Map<String, String> source = new java.util.concurrent.ConcurrentHashMap<>();
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
				prefixes = Map.of();
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
				if (realAccount(info.getProfile().id())) {
					add(next, info.getProfile().name(), self, "tab");
				}
			}
		}
		if (client.level != null) {
			for (Player player : client.level.players()) {
				if (realAccount(player.getUUID())) {
					add(next, player.getGameProfile().name(), self, "nearby player");
				}
			}
		}
		if (next.size() > 4096) {
			next.clear();
			source.clear();
		}
		publish(next);
	}

	/**
	 * Real accounts have version 4 UUIDs. Hypixel's NPCs, dungeon mobs that are players, and
	 * its tab list columns use other versions, and their "names" are things like room and
	 * dragon names. Off Hypixel (offline test servers) every player counts.
	 */
	private static boolean realAccount(java.util.UUID id) {
		return id != null && (id.version() == 4 || !dev.stray.client.location.SkyblockLocation.onHypixel);
	}

	private static void publish(Map<String, String> next) {
		if (next.equals(names)) {
			return;
		}
		Map<String, String> starts = new HashMap<>();
		for (Map.Entry<String, String> entry : next.entrySet()) {
			String name = entry.getKey();
			for (int len = Math.max(MIN_CUT, name.length() - MAX_CUT); len < name.length(); len++) {
				String start = name.substring(0, len);
				String had = starts.putIfAbsent(start, entry.getValue());
				if (had != null && !had.equals(entry.getValue())) {
					starts.put(start, "");
				}
			}
		}
		// A start that is someone's whole name is that person, not a cut-off.
		starts.keySet().removeAll(next.keySet());
		starts.values().removeIf(String::isEmpty);
		prefixes = Map.copyOf(starts);
		names = Map.copyOf(next);
	}

	/** Set while a scoreboard line is rewritten: only there can a name be cut off. */
	private static final ThreadLocal<Boolean> CUT = ThreadLocal.withInitial(() -> false);

	/** The fake name for a whole word: an exact name, or (scoreboard) a cut-off one. */
	private static String lookup(String word) {
		String fake = names.get(word);
		return fake != null || !CUT.get() ? fake : prefixes.get(word);
	}

	/** A scoreboard line: also swaps names the scoreboard cut short ("UltraCharge0"). */
	public static Component rewriteScoreboard(Component line) {
		if (line == null || !active()) {
			return line;
		}
		CUT.set(true);
		try {
			return wholeLine(line);
		} finally {
			CUT.set(false);
		}
	}

	/**
	 * Like {@link #rewrite(Component)} but over the whole line, so a name split between the
	 * team prefix and suffix (as Hypixel's scoreboard does) still matches. A swapped name takes
	 * the style of its first letter.
	 */
	private static Component wholeLine(Component line) {
		// Visible letters with their style; colour codes ride along before the letter after them.
		StringBuilder visible = new StringBuilder();
		java.util.ArrayList<Style> styles = new java.util.ArrayList<>();
		java.util.ArrayList<String> codes = new java.util.ArrayList<>();
		StringBuilder pending = new StringBuilder();
		line.visit((style, piece) -> {
			for (int k = 0; k < piece.length(); k++) {
				char c = piece.charAt(k);
				if (c == '\u00A7' && k + 1 < piece.length()) {
					pending.append(c).append(piece.charAt(++k));
					continue;
				}
				visible.append(c);
				styles.add(style);
				codes.add(pending.toString());
				pending.setLength(0);
			}
			return Optional.empty();
		}, Style.EMPTY);
		String text = visible.toString();
		if (rewrite(text) == text) {
			return line;
		}
		MutableComponent out = Component.empty();
		StringBuilder run = new StringBuilder();
		Style[] runStyle = {null};
		java.util.function.BiConsumer<String, Style> emit = (piece, style) -> {
			if (runStyle[0] != null && !style.equals(runStyle[0])) {
				out.append(Component.literal(run.toString()).setStyle(runStyle[0]));
				run.setLength(0);
			}
			runStyle[0] = style;
			run.append(piece);
		};
		int n = text.length();
		int i = 0;
		while (i < n) {
			char c = text.charAt(i);
			if (nameChar(c) && (i == 0 || !nameChar(text.charAt(i - 1)))) {
				int j = i;
				while (j < n && nameChar(text.charAt(j)) && j - i <= 16) {
					j++;
				}
				String fake = j - i >= 3 && j - i <= 16 && (j >= n || !nameChar(text.charAt(j))) ? lookup(text.substring(i, j)) : null;
				if (fake != null) {
					// The colour codes inside the name are dropped; the one before it is kept.
					emit.accept(codes.get(i) + fake, styles.get(i));
					i = j;
					continue;
				}
			}
			emit.accept(codes.get(i) + c, styles.get(i));
			i++;
		}
		if (pending.length() > 0) {
			emit.accept(pending.toString(), runStyle[0] == null ? Style.EMPTY : runStyle[0]);
		}
		if (run.length() > 0) {
			out.append(Component.literal(run.toString()).setStyle(runStyle[0]));
		}
		return out;
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
			add(next, name, self, "chat: " + (plain.length() > 60 ? plain.substring(0, 60) + "..." : plain));
		}
		if (next.size() != before) {
			publish(next);
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
		add(next, name, NickHider.realName(), "test command");
		publish(next);
		return next.get(name);
	}

	/** /stray debug dev othername why <word>: whether a word is swapped, and why. */
	public static String why(String word) {
		String exact = names.get(word);
		if (exact != null) {
			return spaced(word) + " is a name, from " + source.getOrDefault(word, "?");
		}
		if (prefixes.containsKey(word)) {
			for (String name : names.keySet()) {
				if (name.startsWith(word)) {
					return spaced(word) + " is cut off from " + spaced(name) + ", from " + source.getOrDefault(name, "?");
				}
			}
		}
		return spaced(word) + " is not swapped (" + names.size() + " names)";
	}

	private static void add(Map<String, String> map, String name, String self, String from) {
		if (name == null || name.equalsIgnoreCase(self) || map.containsKey(name) || !MC_NAME.matcher(name).matches()) {
			return;
		}
		map.put(name, fakeFor(name));
		source.putIfAbsent(name, from);
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
		StringBuilder out = null;
		int last = 0;
		int i = 0;
		int n = text.length();
		while (i < n) {
			char c = text.charAt(i);
			// "§b": skip the colour code, so its letter is not read as the start of a name.
			if (c == '\u00A7' && i + 1 < n) {
				i += 2;
				continue;
			}
			if (!nameChar(c) || i > 0 && nameChar(text.charAt(i - 1)) && !afterFormatCode(text, i)) {
				i++;
				continue;
			}
			int j = i;
			while (j < n && nameChar(text.charAt(j)) && j - i <= 16) {
				j++;
			}
			String fake = j - i >= 3 && j - i <= 16 ? lookup(text.substring(i, j)) : null;
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
				String fake = j - i >= 3 && j - i <= 16 && (j >= n || !nameChar(plain.charAt(j))) ? lookup(plain.substring(i, j)) : null;
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
