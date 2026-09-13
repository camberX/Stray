package dev.stray.client.visual;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.profile.ProfileViewer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wear another player's identity: their skin and cape, their username, their
 * Hypixel rank tag, and their Skyblock level. Name swaps ride on
 * {@link NickHider}; rank and level tags are rewritten in legacy text so the
 * colors match the stolen rank in chat, tab, and nametags.
 */
public final class NickSteal {
	public enum Status {
		OFF, LOADING, READY, ERROR
	}

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(10))
		.build();
	private static final String MOJANG = "https://api.mojang.com/users/profiles/minecraft/";
	private static final String ASHCON = "https://api.ashcon.app/mojang/v2/user/";
	private static final String SESSION = "https://sessionserver.mojang.com/session/minecraft/profile/";
	private static final String SOOPY_PLAYER = "https://soopy.dev/api/v2/player/";
	private static final String PROFILE = "https://hypixel.odtheking.com/get/";
	private static final Pattern COLOR = Pattern.compile("§([0-9a-f])");

	private static volatile Status status = Status.OFF;
	private static volatile String error = "";
	private static volatile Stolen stolen;
	private static volatile Pattern tagPattern;
	private static volatile String tagPatternFor = "";
	private static volatile String lastSeen = "";
	private static int unmatchedLogged;
	private static Supplier<PlayerSkin> skinLookup;
	private static Stolen skinLookupFor;
	private static int generation;

	private NickSteal() {
	}

	public static void init() {
		String target = StrayConfig.get().nickStealTarget;
		if (target != null && !target.isBlank()) {
			steal(target, false);
		}
	}

	public static Status status() {
		return status;
	}

	public static String error() {
		return error;
	}

	public static boolean active() {
		return status == Status.READY && stolen != null;
	}

	public static String target() {
		String target = StrayConfig.get().nickStealTarget;
		return target == null ? "" : target;
	}

	public static String name() {
		Stolen current = stolen;
		return current == null ? "" : current.name;
	}

	/** Last legacy line that contained the real name, § shown as &, for /st steal debug. */
	public static String lastSeen() {
		return lastSeen.replace('§', '&');
	}

	public static String statusLabel() {
		Stolen current = stolen;
		return switch (status) {
			case OFF -> "Not stealing";
			case LOADING -> "Looking up " + target() + "…";
			case READY -> current == null ? "Ready" : summary(current);
			case ERROR -> error.isBlank() ? "Failed" : error;
		};
	}

	private static String summary(Stolen current) {
		StringBuilder out = new StringBuilder(current.name);
		if (!current.rankPlain.isEmpty()) {
			out.append("  ").append(current.rankPlain);
		}
		if (current.level > 0) {
			out.append("  Lv ").append(current.level);
		}
		return out.toString();
	}

	public static void steal(String name) {
		steal(name, true);
	}

	public static void stop() {
		generation++;
		StrayConfig config = StrayConfig.get();
		config.nickStealTarget = "";
		config.save();
		stolen = null;
		skinLookup = null;
		skinLookupFor = null;
		status = Status.OFF;
		error = "";
	}

	private static void steal(String name, boolean save) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty()) {
			stop();
			return;
		}
		StrayConfig config = StrayConfig.get();
		config.nickStealTarget = trimmed;
		if (save) {
			config.save();
		}
		int gen = ++generation;
		status = Status.LOADING;
		error = "";
		Util.nonCriticalIoPool().execute(() -> lookup(gen, trimmed));
	}

	private static void lookup(int gen, String query) {
		try {
			JsonObject mojang = getJson(MOJANG + encode(query));
			UUID uuid = uuidOf(string(mojang, "id"));
			String named = string(mojang, "name");
			if (uuid == null) {
				JsonObject ashcon = getJson(ASHCON + encode(query));
				uuid = uuidOf(string(ashcon, "uuid"));
				named = string(ashcon, "username");
			}
			if (uuid == null) {
				fail(gen, "No player called " + query);
				return;
			}
			if (named.isBlank()) {
				named = query;
			}
			String compact = uuid.toString().replace("-", "");
			String value = "";
			String signature = "";
			JsonObject session = getJson(SESSION + compact + "?unsigned=false");
			JsonArray properties = session == null || !session.has("properties") || !session.get("properties").isJsonArray()
				? null
				: session.getAsJsonArray("properties");
			if (properties != null) {
				for (JsonElement element : properties) {
					if (element == null || !element.isJsonObject()) {
						continue;
					}
					JsonObject property = element.getAsJsonObject();
					if ("textures".equalsIgnoreCase(string(property, "name"))) {
						value = string(property, "value");
						signature = string(property, "signature");
						break;
					}
				}
			}
			if (gen != generation) {
				return;
			}
			JsonObject soopy = getJson(SOOPY_PLAYER + compact);
			JsonObject stats = object(object(soopy, "data"), "stats");
			String prefix = string(stats, "prefixCalculated").trim();
			if (prefix.contains("undefined")) {
				prefix = "";
			}
			if (prefix.isEmpty()) {
				prefix = string(stats, "prefix").trim();
			}
			if (prefix.contains("undefined")) {
				prefix = "";
			}
			int level = skyblockLevel(compact);
			if (gen != generation) {
				return;
			}
			String rankPlain = prefix.isEmpty() ? "" : prefix.replaceAll("§.", "").trim();
			char rankColor = '7';
			Matcher color = COLOR.matcher(prefix);
			if (color.find()) {
				rankColor = color.group(1).charAt(0);
			}
			Stolen next = new Stolen(named, uuid, value, signature, prefix, rankPlain, rankColor, level);
			Minecraft.getInstance().execute(() -> {
				if (gen != generation) {
					return;
				}
				stolen = next;
				status = Status.READY;
				error = "";
			});
		} catch (Exception exception) {
			Stray.LOGGER.warn("Nick steal lookup failed", exception);
			fail(gen, "Lookup failed");
		}
	}

	private static int skyblockLevel(String compact) {
		JsonObject root = getJson(PROFILE + compact);
		if (root == null || !root.has("profiles") || !root.get("profiles").isJsonArray()) {
			return 0;
		}
		int best = 0;
		int selected = -1;
		for (JsonElement element : root.getAsJsonArray("profiles")) {
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			JsonObject profile = element.getAsJsonObject();
			JsonObject members = object(profile, "members");
			JsonObject member = null;
			if (members != null) {
				for (String key : members.keySet()) {
					if (key.replace("-", "").equalsIgnoreCase(compact)) {
						member = object(members, key);
						break;
					}
				}
			}
			if (member == null) {
				continue;
			}
			double xp = num(object(member, "leveling"), "experience");
			int level = (int) Math.floor(xp / 100d);
			boolean chosen = profile.has("selected") && profile.get("selected").isJsonPrimitive() && profile.get("selected").getAsBoolean();
			if (chosen) {
				selected = level;
			}
			best = Math.max(best, level);
		}
		return selected >= 0 ? selected : best;
	}

	/** Stolen skin and cape for the local player, or the original when off. */
	public static PlayerSkin skin(PlayerSkin original) {
		Stolen current = stolen;
		if (!active() || current == null) {
			return original;
		}
		if (skinLookup == null || skinLookupFor != current) {
			skinLookup = lookupFor(current);
			skinLookupFor = current;
		}
		if (skinLookup == null) {
			return original;
		}
		PlayerSkin skin = skinLookup.get();
		return skin == null ? original : skin;
	}

	private static Supplier<PlayerSkin> lookupFor(Stolen current) {
		Minecraft client = Minecraft.getInstance();
		try {
			if (current.skinValue.isBlank()) {
				return client.getSkinManager().createLookup(new GameProfile(current.uuid, current.name), false);
			}
			boolean signed = !current.skinSignature.isBlank();
			Property textures = signed
				? new Property("textures", current.skinValue, current.skinSignature)
				: new Property("textures", current.skinValue);
			PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", textures));
			return client.getSkinManager().createLookup(new GameProfile(current.uuid, current.name, properties), signed);
		} catch (Exception exception) {
			Stray.LOGGER.warn("Nick steal skin lookup failed", exception);
			return null;
		}
	}

	/**
	 * Rewrite legacy text so a Skyblock level tag, Hypixel rank tag, or rank
	 * color in front of the real name becomes the stolen player's. Returns
	 * null when nothing in front of the name needed changing.
	 */
	public static String rewriteTags(String legacy, String realName) {
		Stolen current = stolen;
		if (!active() || current == null || legacy == null || legacy.isEmpty() || realName == null || realName.isEmpty()) {
			return null;
		}
		Pattern pattern = patternFor(realName);
		lastSeen = legacy;
		Matcher matcher = pattern.matcher(legacy);
		StringBuilder out = null;
		int cursor = 0;
		while (matcher.find()) {
			String levelTag = matcher.group(1);
			String rankTag = matcher.group(2);
			String colorOnly = matcher.group(3);
			if (levelTag == null && rankTag == null && colorOnly == null) {
				continue;
			}
			if (out == null) {
				out = new StringBuilder(legacy.length() + 16);
			}
			out.append(legacy, cursor, matcher.start());
			if (levelTag != null) {
				if (current.level > 0) {
					out.append("§8[").append(ProfileViewer.levelColor(current.level)).append(current.level).append("§8] ");
				} else {
					out.append(levelTag);
				}
			}
			if (rankTag != null) {
				out.append(current.prefix.isEmpty() ? "§7" : current.prefix + " ");
			} else if (colorOnly != null || levelTag != null) {
				out.append('§').append(current.rankColor);
			}
			out.append(current.name);
			cursor = matcher.end();
		}
		if (out == null) {
			if (legacy.indexOf('[') >= 0 && ++unmatchedLogged <= 20) {
				Stray.LOGGER.info("Nick steal: no rank/level tag matched before name in: {}", legacy.replace('§', '&'));
			}
			return null;
		}
		out.append(legacy, cursor, legacy.length());
		return out.toString();
	}

	private static Pattern patternFor(String realName) {
		Pattern cached = tagPattern;
		if (cached != null && realName.equals(tagPatternFor)) {
			return cached;
		}
		String quoted = Pattern.quote(realName);
		// Hypixel sprinkles resets and repeated colors between segments, so
		// allow any run of codes at every seam. A code right before the name
		// counts as a boundary; otherwise the previous char must not be part
		// of a username.
		String codes = "(?:§[0-9a-fk-or])*";
		String gap = codes + "\\s+" + codes;
		// Level tag: [digits]. Rank tag: [LETTERS+]. Either may carry any
		// colors; only the bracket structure is trusted.
		Pattern pattern = Pattern.compile(
			"(" + codes + "\\[" + codes + "\\d+" + codes + "\\]" + gap + ")?"
				+ "(" + codes + "\\[" + codes + "[A-Za-z][A-Za-z+§0-9a-fk-or]*\\]" + gap + ")?"
				+ "(?:(" + codes + "§[0-9a-f]" + codes + ")|(?<![A-Za-z0-9_])|(?<=§[0-9a-fk-or]))"
				+ quoted + "(?![A-Za-z0-9_])"
		);
		tagPattern = pattern;
		tagPatternFor = realName;
		return pattern;
	}

	/** Flatten a component into § legacy text, keeping named colors and styles. */
	public static String toLegacy(Component component) {
		StringBuilder out = new StringBuilder();
		component.visit((style, text) -> {
			if (text.isEmpty()) {
				return java.util.Optional.empty();
			}
			appendStyle(out, style);
			out.append(text);
			return java.util.Optional.empty();
		}, Style.EMPTY);
		// A color code already resets formatting in legacy text, so the
		// leading §r is noise that would break the tag patterns.
		return out.toString().replaceAll("§r(§[0-9a-f])", "$1");
	}

	private static void appendStyle(StringBuilder out, Style style) {
		out.append("§r");
		TextColor color = style.getColor();
		if (color != null) {
			ChatFormatting named = ChatFormatting.getByName(color.serialize());
			if (named != null && named.isColor()) {
				out.append('§').append(named.getChar());
			}
		}
		if (style.isBold()) {
			out.append("§l");
		}
		if (style.isItalic()) {
			out.append("§o");
		}
		if (style.isUnderlined()) {
			out.append("§n");
		}
		if (style.isStrikethrough()) {
			out.append("§m");
		}
		if (style.isObfuscated()) {
			out.append("§k");
		}
	}

	private static void fail(int gen, String message) {
		Minecraft.getInstance().execute(() -> {
			if (gen != generation) {
				return;
			}
			stolen = null;
			status = Status.ERROR;
			error = message;
		});
	}

	private static JsonObject getJson(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", "Stray/" + Stray.MOD_ID)
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return null;
			}
			JsonElement element = JsonParser.parseString(response.body());
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static JsonObject object(JsonObject parent, String key) {
		if (parent == null || key == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
			return null;
		}
		return parent.getAsJsonObject(key);
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

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static UUID uuidOf(String raw) {
		if (raw == null) {
			return null;
		}
		String compact = raw.replace("-", "").trim().toLowerCase(Locale.ROOT);
		if (compact.length() != 32) {
			return null;
		}
		try {
			return UUID.fromString(compact.replaceFirst(
				"(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
				"$1-$2-$3-$4-$5"
			));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private record Stolen(
		String name,
		UUID uuid,
		String skinValue,
		String skinSignature,
		String prefix,
		String rankPlain,
		char rankColor,
		int level
	) {
	}
}
