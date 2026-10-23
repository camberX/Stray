package dev.stray.client.cs2;

import dev.stray.client.cs2.s2.Kv1;
import dev.stray.client.cs2.s2.Vpk;
import dev.stray.client.cs2.skin.Assets;
import dev.stray.client.cs2.skin.PaintKit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything read from the player's own CS2 install: knives, their finishes and names.
 * Nothing from CS2 ships with Stray; without an install this stays empty.
 */
public final class Cs2Library {
	/** Knives not offered: the plain CT and T knives and the Shadow Daggers. */
	private static final java.util.Set<String> HIDDEN = java.util.Set.of("weapon_knife", "weapon_knife_t", "weapon_knife_push");

	public record Finish(String kit, String name) {
	}

	public record Knife(String id, String name, String model, List<Finish> finishes) {
	}

	private static final String CSGO = "steamapps/common/Counter-Strike Global Offensive/game/csgo";

	private final Path csgo;
	/** Every file path the source holds (game paths, compiled names). */
	private final java.util.Collection<String> paths;
	private final Assets assets;
	private final Map<String, String> strings;
	private final Map<String, Knife> knives = new LinkedHashMap<>();
	private final Map<String, PaintKit> kits = new java.util.concurrent.ConcurrentHashMap<>();

	/** For tests: an index-only VPK plus a separate file source. */
	public Cs2Library(Path csgo, Vpk index, Assets assets) {
		this(csgo, pathsOf(index), assets);
	}

	/** Any source of CS2 files: the install's VPK or Stray's bundled pack. */
	public Cs2Library(Path csgo, java.util.Collection<String> paths, Assets assets) {
		this.csgo = csgo;
		this.paths = paths;
		this.assets = assets;
		this.strings = loadStrings();
		loadKnives();
	}

	private static java.util.Collection<String> pathsOf(Vpk vpk) {
		List<String> out = new ArrayList<>(vpk.entries().size());
		for (Vpk.Entry e : vpk.entries().values()) {
			out.add(e.path());
		}
		return out;
	}

	private Cs2Library(Path csgo) throws IOException {
		this(csgo, new Vpk(csgo.resolve("pak01_dir.vpk")));
	}

	private Cs2Library(Path csgo, Vpk vpk) {
		this.csgo = csgo;
		this.paths = pathsOf(vpk);
		this.assets = new Assets() {
			@Override
			protected byte[] readRaw(String path) throws IOException {
				return vpk.read(path);
			}

			@Override
			public boolean exists(String path) {
				return vpk.find(compiled(path)) != null;
			}
		};
		this.strings = loadStrings();
		loadKnives();
	}

	public static Cs2Library open(String overridePath) {
		Path csgo = findCsgo(overridePath);
		if (csgo == null) {
			return null;
		}
		try {
			return new Cs2Library(csgo);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	public Path csgo() {
		return csgo;
	}

	public Assets assets() {
		return assets;
	}

	/** Compiled file paths under a folder prefix (game paths, with _c). */
	public List<String> list(String prefix) {
		List<String> out = new ArrayList<>();
		String p = prefix.toLowerCase(Locale.ROOT);
		for (String path : paths) {
			if (path.toLowerCase(Locale.ROOT).startsWith(p)) {
				out.add(path);
			}
		}
		out.sort(null);
		return out;
	}

	/** CS2's own preview image for a knife and finish (finish "" = vanilla), as RGBA, or null. */
	public dev.stray.client.cs2.skin.Tex preview(String knifeId, String kit) {
		return assets.texture(previewPath(knifeId, kit), 512, false);
	}

	public String previewPath(String knifeId, String kit) {
		return kit == null || kit.isEmpty()
			? "panorama/images/econ/weapons/base_weapons/" + knifeId + "_png.vtex"
			: "panorama/images/econ/default_generated/" + knifeId + "_" + kit + "_light_png.vtex";
	}

	public Map<String, Knife> knives() {
		return knives;
	}

	public PaintKit kit(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		PaintKit kit = kits.computeIfAbsent(name, n -> {
			PaintKit k = PaintKit.load(assets, n);
			return k == null ? EMPTY : k;
		});
		return kit == EMPTY ? null : kit;
	}

	private static final PaintKit EMPTY = new PaintKit("", Map.of(), null);

	// ---- install discovery

	public static Path findCsgo(String overridePath) {
		List<Path> candidates = new ArrayList<>();
		if (overridePath != null && !overridePath.isBlank()) {
			Path p = Path.of(overridePath.trim());
			candidates.add(p);
			candidates.add(p.resolve("game/csgo"));
			candidates.add(p.resolve("csgo"));
		}
		String home = System.getProperty("user.home");
		List<Path> steamRoots = new ArrayList<>(List.of(
			Path.of(home, ".local/share/Steam"),
			Path.of(home, ".steam/steam"),
			Path.of(home, ".steam/root"),
			Path.of(home, ".var/app/com.valvesoftware.Steam/.local/share/Steam"),
			Path.of(home, "snap/steam/common/.local/share/Steam"),
			Path.of(home, "Library/Application Support/Steam"),
			Path.of("C:/Program Files (x86)/Steam"),
			Path.of("C:/Program Files/Steam")
		));
		for (Path root : steamRoots) {
			candidates.add(root.resolve(CSGO));
			for (Path lib : libraries(root)) {
				candidates.add(lib.resolve(CSGO));
			}
		}
		for (Path c : candidates) {
			if (Files.isRegularFile(c.resolve("pak01_dir.vpk"))) {
				return c;
			}
		}
		return null;
	}

	private static List<Path> libraries(Path steamRoot) {
		List<Path> out = new ArrayList<>();
		Path vdf = steamRoot.resolve("steamapps/libraryfolders.vdf");
		if (!Files.isRegularFile(vdf)) {
			return out;
		}
		try {
			Matcher m = Pattern.compile("\"path\"\\s+\"([^\"]+)\"").matcher(Files.readString(vdf));
			while (m.find()) {
				out.add(Path.of(m.group(1).replace("\\\\", "\\")));
			}
		} catch (IOException ignored) {
		}
		return out;
	}

	// ---- names

	private Map<String, String> loadStrings() {
		Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Path file = csgo == null ? null : csgo.resolve("resource/csgo_english.txt");
		byte[] bytes = null;
		try {
			if (file != null && Files.isRegularFile(file)) {
				bytes = Files.readAllBytes(file);
			}
		} catch (IOException ignored) {
		}
		if (bytes == null) {
			bytes = assets.read("resource/csgo_english.txt");
		}
		if (bytes == null) {
			return out;
		}
		// Lines look like: "Token"   "Text" (with \" escapes). A plain scan; regexes overflow on long values.
		String text = new String(bytes, StandardCharsets.UTF_8);
		for (String line : text.split("\n")) {
			String key = null;
			StringBuilder cur = null;
			boolean esc = false;
			for (int i = 0; i < line.length(); i++) {
				char ch = line.charAt(i);
				if (cur == null) {
					if (ch == '"') {
						cur = new StringBuilder();
					}
					continue;
				}
				if (esc) {
					cur.append(ch == 'n' ? '\n' : ch);
					esc = false;
				} else if (ch == '\\') {
					esc = true;
				} else if (ch == '"') {
					if (key == null) {
						key = cur.toString();
						cur = null;
					} else {
						out.putIfAbsent(key, cur.toString());
						break;
					}
				} else {
					cur.append(ch);
				}
			}
		}
		return out;
	}

	private String string(String token, String fallback) {
		if (token == null) {
			return fallback;
		}
		String v = strings.get(token.startsWith("#") ? token.substring(1) : token);
		return v == null || v.isBlank() ? fallback : v;
	}

	// ---- knives and finishes

	@SuppressWarnings("unchecked")
	private void loadKnives() {
		byte[] raw = assets.read("scripts/items/items_game.txt");
		if (raw == null) {
			return;
		}
		Map<String, Object> root = Kv1.parse(new String(raw, StandardCharsets.UTF_8));
		Map<String, Object> game = (Map<String, Object>) root.getOrDefault("items_game", root);
		Map<String, Object> items = (Map<String, Object>) game.get("items");
		Map<String, Object> paintKits = (Map<String, Object>) game.get("paint_kits");
		Map<String, String> kitTags = new LinkedHashMap<>();
		if (paintKits != null) {
			for (Object o : paintKits.values()) {
				if (o instanceof Map<?, ?> pk && pk.get("name") instanceof String n) {
					Object tag = pk.get("description_tag");
					kitTags.put(n, tag == null ? null : tag.toString());
				}
			}
		}
		// Valid knife/finish pairs: the game ships a preview image for each.
		Map<String, List<String>> combos = new TreeMap<>();
		String prefix = "panorama/images/econ/default_generated/";
		for (String p : paths) {
			if (!p.startsWith(prefix) || !p.endsWith("_light_png.vtex_c")) {
				continue;
			}
			String name = p.substring(prefix.length(), p.length() - "_light_png.vtex_c".length());
			if (name.startsWith("weapon_knife") || name.startsWith("weapon_bayonet")) {
				combos.computeIfAbsent(name, k -> new ArrayList<>());
			}
		}
		List<Map<String, Object>> knifeItems = new ArrayList<>();
		if (items != null) {
			for (Object o : items.values()) {
				if (!(o instanceof Map<?, ?> item)) {
					continue;
				}
				Object n = item.get("name");
				Object model = item.get("model_player");
				if (n == null || model == null) {
					continue;
				}
				String id = n.toString();
				if ((id.startsWith("weapon_knife") || id.equals("weapon_bayonet")) && !id.equals("weapon_knifegg")
					&& model.toString().contains("/knife/")) {
					knifeItems.add((Map<String, Object>) item);
				}
			}
		}
		// Longest id first so weapon_knife_m9_bayonet wins over weapon_knife.
		knifeItems.sort(Comparator.comparingInt((Map<String, Object> i) -> -i.get("name").toString().length()));
		Map<String, List<Finish>> finishes = new LinkedHashMap<>();
		for (String combo : combos.keySet()) {
			for (Map<String, Object> item : knifeItems) {
				String id = item.get("name").toString();
				if (combo.startsWith(id + "_")) {
					String kit = combo.substring(id.length() + 1);
					finishes.computeIfAbsent(id, k -> new ArrayList<>()).add(new Finish(kit, finishName(kit, kitTags.get(kit))));
					break;
				}
			}
		}
		knifeItems.sort(Comparator.comparing(i -> string((String) i.get("item_name"), i.get("name").toString())));
		for (Map<String, Object> item : knifeItems) {
			String id = item.get("name").toString();
			String model = item.get("model_player").toString();
			if (!assets.exists(model)) {
				continue;
			}
			List<Finish> list = new ArrayList<>();
			list.add(new Finish("", "Vanilla"));
			List<Finish> found = new ArrayList<>(finishes.getOrDefault(id, List.of()));
			found.sort(Comparator.comparing(Finish::name));
			list.addAll(found);
			String name = string((String) item.get("item_name"), pretty(id.replace("weapon_", "")));
			// The plain default knives and the Shadow Daggers are left out of the picker.
			if (!HIDDEN.contains(id)) {
				knives.put(id, new Knife(id, name, model, List.copyOf(list)));
			}
		}
	}

	private String finishName(String kit, String tag) {
		String base = string(tag, pretty(kit.replaceFirst("^[a-z]{2}_", "")));
		for (String[] gem : new String[][]{{"ruby", "Ruby"}, {"sapphire", "Sapphire"}, {"blackpearl", "Black Pearl"}, {"emerald", "Emerald"}}) {
			if (kit.contains(gem[0])) {
				return base + " (" + gem[1] + ")";
			}
		}
		Matcher phase = Pattern.compile("phase(\\d)").matcher(kit);
		if (phase.find() && !base.toLowerCase(Locale.ROOT).contains("phase")) {
			base += " (Phase " + phase.group(1) + ")";
		}
		return base;
	}

	private static String pretty(String s) {
		StringBuilder b = new StringBuilder();
		for (String part : s.split("_")) {
			if (part.isEmpty()) {
				continue;
			}
			if (b.length() > 0) {
				b.append(' ');
			}
			b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return b.toString();
	}
}
