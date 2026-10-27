package dev.stray.client.item.custom;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.stray.Stray;
import dev.stray.client.item.ItemIds;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Per-item cosmetic overrides: name, dye color (hex, gradient, SkyBlock dye,
 * animated dye), armor trim, head skin and enchant glint. Client side only;
 * nothing is sent to the server.
 *
 * Ported from SkyOcean's item customization (https://github.com/meowdding/SkyOcean,
 * MIT, Copyright (c) 2026 meowdding). Dye and animated skull data come from
 * meowdding-repo (MIT). See assets/stray/customization/NOTICE.txt.
 */
public final class CustomItems {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Data> ITEMS = new LinkedHashMap<>();
	private static final Map<ItemStack, Info> INFO = Collections.synchronizedMap(new WeakHashMap<>());
	private static boolean loaded;
	private static long ticks;

	/** Static SkyBlock dyes (incl. vanilla dyes), id -> rgb. */
	private static final Map<String, Integer> STATIC_DYES = new LinkedHashMap<>();
	/** Animated SkyBlock dyes, id -> frames. */
	private static final Map<String, int[]> ANIMATED_DYES = new LinkedHashMap<>();
	/** Animated skulls, SkyBlock id -> frame textures and ticks per frame. */
	private static final Map<String, Skull> SKULLS = new LinkedHashMap<>();
	private static boolean dataLoaded;

	private static final Map<Integer, DyedItemColor> DYE_CACHE = new HashMap<>();
	private static final Map<String, ResolvableProfile> PROFILE_CACHE = new HashMap<>();
	private static final Map<String, ArmorTrim> TRIM_CACHE = new HashMap<>();

	private CustomItems() {
	}

	// ------------------------------------------------------------------ model

	/** One item's overrides. Null fields mean "leave as is". */
	public static final class Data {
		public String name;
		public ColorSpec color;
		public String trimMaterial;
		public String trimPattern;
		public SkinSpec skin;
		public Boolean glint;
		/** Minecraft item id whose model the item is drawn with, e.g. minecraft:diamond_sword. */
		public String model;

		boolean empty() {
			return (name == null || name.isEmpty()) && color == null && trimPattern == null && skin == null && glint == null
				&& (model == null || model.isEmpty());
		}
	}

	/** type: "static" (rgb), "gradient" (colors + time), "dye" or "animated" (id). */
	public static final class ColorSpec {
		public String type;
		public int rgb;
		public List<Integer> colors;
		public int time = 40;
		public String id;
		transient int[] frames;

		public static ColorSpec hex(int rgb) {
			ColorSpec spec = new ColorSpec();
			spec.type = "static";
			spec.rgb = rgb & 0xFFFFFF;
			return spec;
		}

		public static ColorSpec gradient(List<Integer> colors, int time) {
			ColorSpec spec = new ColorSpec();
			spec.type = "gradient";
			spec.colors = new ArrayList<>(colors);
			spec.time = Math.max(colors.size(), time);
			return spec;
		}

		public static ColorSpec dye(String id, boolean animated) {
			ColorSpec spec = new ColorSpec();
			spec.type = animated ? "animated" : "dye";
			spec.id = id.toUpperCase(Locale.ROOT);
			return spec;
		}
	}

	/** type: "animated" (SkyBlock id with frames), "skyblock" (SkyBlock item id) or "texture" (base64). */
	public static final class SkinSpec {
		public String type;
		public String value;

		public static SkinSpec of(String type, String value) {
			SkinSpec spec = new SkinSpec();
			spec.type = type;
			spec.value = value;
			return spec;
		}
	}

	private record Info(String key, String vanillaDye, String vanillaSkin) {
	}

	private record Skull(int ticks, String[] textures) {
	}

	// ---------------------------------------------------------------- keys

	/**
	 * Stable key for an item: its SkyBlock UUID, else id + creation time, else
	 * the SkyBlock id (which then applies to every copy of that item).
	 */
	public static String keyOf(ItemStack stack) {
		Info info = info(stack);
		return info == null ? null : info.key;
	}

	/** True when the key is shared by every item with that id. */
	public static boolean sharedKey(String key) {
		return key != null && key.startsWith("i:");
	}

	private static Info info(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Info cached = INFO.get(stack);
		if (cached != null) {
			return cached.key == null ? null : cached;
		}
		Info made = readInfo(stack);
		INFO.put(stack, made == null ? new Info(null, null, null) : made);
		return made;
	}

	private static Info readInfo(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null || data.isEmpty() ? null : data.copyTag();
		CompoundTag extra = tag == null ? null : tag.getCompoundOrEmpty("ExtraAttributes");
		if (extra != null && extra.isEmpty()) {
			extra = tag;
		}
		String uuid = ItemIds.uuidOf(stack);
		String id = ItemIds.skyblockId(stack);
		String dye = extra == null ? null : extra.getStringOr("dye_item", null);
		String skin = extra == null ? null : extra.getStringOr("skin", null);
		String key;
		if (uuid != null && !uuid.isBlank()) {
			key = "u:" + uuid;
		} else if (id != null && extra != null && extra.contains("timestamp")) {
			key = "t:" + id + ":" + extra.getLongOr("timestamp", 0L);
		} else if (id != null) {
			key = "i:" + id;
		} else {
			return null;
		}
		return new Info(key, dye == null ? null : dye.toUpperCase(Locale.ROOT), skin == null ? null : skin.toUpperCase(Locale.ROOT));
	}

	// ------------------------------------------------------------- storage

	public static Data get(ItemStack stack) {
		load();
		String key = keyOf(stack);
		return key == null ? null : ITEMS.get(key);
	}

	public static Data getOrCreate(ItemStack stack) {
		load();
		String key = keyOf(stack);
		if (key == null) {
			return null;
		}
		return ITEMS.computeIfAbsent(key, k -> new Data());
	}

	public static boolean reset(ItemStack stack) {
		load();
		String key = keyOf(stack);
		boolean removed = key != null && ITEMS.remove(key) != null;
		if (removed) {
			save();
		}
		return removed;
	}

	public static void save() {
		ITEMS.values().removeIf(Data::empty);
		try {
			Path file = file();
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(ITEMS), StandardCharsets.UTF_8);
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not save custom items", exception);
		}
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path file = file();
			if (Files.isRegularFile(file)) {
				Map<String, Data> read = GSON.fromJson(Files.readString(file), new TypeToken<LinkedHashMap<String, Data>>() { }.getType());
				if (read != null) {
					ITEMS.putAll(read);
				}
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read custom items", exception);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("custom_items.json");
	}

	/** Advances animations; call once per client tick. */
	public static void tick() {
		ticks++;
		if (DYE_CACHE.size() > 512) {
			DYE_CACHE.clear();
		}
		if (PROFILE_CACHE.size() > 256) {
			PROFILE_CACHE.clear();
		}
	}

	// ---------------------------------------------------------- replacement

	/**
	 * The value to use instead of {@code type} on this stack, or null to keep
	 * the item's own. Mixins wrap {@code ItemStack.get} with this.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T replacement(ItemStack stack, DataComponentType<T> type) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Data data = get(stack);
		if (type == DataComponents.DYED_COLOR) {
			Integer rgb = color(stack, data);
			return rgb == null ? null : (T) dyed(rgb);
		}
		if (type == DataComponents.PROFILE) {
			return (T) profile(stack, data);
		}
		if (data == null) {
			return null;
		}
		if (type == DataComponents.CUSTOM_NAME) {
			return data.name == null || data.name.isEmpty() ? null : (T) name(data.name);
		}
		if (type == DataComponents.ENCHANTMENT_GLINT_OVERRIDE) {
			return (T) data.glint;
		}
		if (type == DataComponents.TRIM) {
			return (T) trim(data);
		}
		if (type == DataComponents.ITEM_MODEL) {
			return (T) model(data.model);
		}
		return null;
	}

	/** The item model id of a Minecraft item, or null if the id isn't an item. */
	public static Identifier model(String raw) {
		Item item = item(raw);
		return item == null ? null : item.components().get(DataComponents.ITEM_MODEL);
	}

	/** The Minecraft item for an id like "diamond_sword" or "minecraft:diamond_sword". */
	public static Item item(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		Identifier id = Identifier.tryParse(raw.trim().toLowerCase(Locale.ROOT));
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return null;
		}
		Item item = BuiltInRegistries.ITEM.getValue(id);
		return item == Items.AIR ? null : item;
	}

	/** Every Minecraft item id, for the model picker. */
	public static List<String> itemIds() {
		List<String> out = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (!"minecraft:air".equals(id.toString())) {
				out.add(id.toString());
			}
		}
		out.sort(null);
		return out;
	}

	/** Dye or skull id as a name: DYE_PURE_BLACK -> Pure Black. */
	public static String displayName(String id) {
		if (id == null || id.isEmpty()) {
			return "";
		}
		String raw = id.startsWith("DYE_") ? id.substring(4) : id;
		int colon = raw.indexOf(':');
		if (colon >= 0) {
			raw = raw.substring(colon + 1);
		}
		StringBuilder out = new StringBuilder();
		for (String word : raw.toLowerCase(Locale.ROOT).split("[_\\s-]+")) {
			if (word.isEmpty()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	/** A head item showing an animated skull's current frame, for list icons. */
	public static ItemStack skullIcon(String id) {
		loadData();
		Skull skull = id == null ? null : SKULLS.get(id.toUpperCase(Locale.ROOT));
		if (skull == null) {
			return ItemStack.EMPTY;
		}
		ItemStack icon = new ItemStack(Items.PLAYER_HEAD);
		icon.set(DataComponents.PROFILE, texture(skull.textures[0]));
		return icon;
	}

	/** Custom name for tooltips and the held-item name, or null. */
	public static Component customName(ItemStack stack) {
		Data data = get(stack);
		return data == null || data.name == null || data.name.isEmpty() ? null : name(data.name);
	}

	/**
	 * Builds the custom name from & codes: &0-&f colors, &l &o &n &m &k
	 * formats, &r reset and &#RRGGBB hex. Not italic unless &o asks for it,
	 * and white unless a color is given.
	 */
	public static Component name(String raw) {
		Style base = Style.EMPTY.withItalic(false).withColor(ChatFormatting.WHITE);
		MutableComponent out = Component.empty().setStyle(base);
		Style style = base;
		StringBuilder run = new StringBuilder();
		String text = raw.replace('\u00A7', '&');
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 7 < text.length() + 1 && text.charAt(i + 1) == '#') {
				Integer rgb = hex(text.substring(i + 2, i + 8));
				if (rgb != null) {
					flush(out, run, style);
					style = base.withColor(TextColor.fromRgb(rgb));
					i += 7;
					continue;
				}
			}
			if (c == '&' && i + 1 < text.length()) {
				ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1)));
				if (format != null) {
					flush(out, run, style);
					if (format == ChatFormatting.RESET) {
						style = base;
					} else if (format.ordinal() <= ChatFormatting.WHITE.ordinal()) {
						// A color starts fresh, like vanilla: formats before it are cleared.
						style = base.withColor(format);
					} else {
						style = style.applyFormat(format);
					}
					i++;
					continue;
				}
			}
			run.append(c);
		}
		flush(out, run, style);
		return out;
	}

	private static void flush(MutableComponent out, StringBuilder run, Style style) {
		if (run.length() > 0) {
			out.append(Component.literal(run.toString()).setStyle(style));
			run.setLength(0);
		}
	}

	/** The rgb an item shows right now, or null for its own color. */
	public static Integer color(ItemStack stack, Data data) {
		ColorSpec spec = data == null ? null : data.color;
		if (spec == null) {
			// Vanilla integration: animate an applied animated SkyBlock dye.
			Info info = info(stack);
			if (info == null || info.vanillaDye == null) {
				return null;
			}
			loadData();
			int[] frames = ANIMATED_DYES.get(info.vanillaDye);
			return frames == null ? null : frames[(int) ((ticks / 2 + offset(stack)) % frames.length)];
		}
		loadData();
		switch (spec.type == null ? "" : spec.type) {
			case "static" -> {
				return spec.rgb;
			}
			case "dye" -> {
				return STATIC_DYES.get(spec.id);
			}
			case "animated" -> {
				int[] frames = ANIMATED_DYES.get(spec.id);
				return frames == null ? null : frames[(int) ((ticks / 2 + offset(stack)) % frames.length)];
			}
			case "gradient" -> {
				int[] frames = gradientFrames(spec);
				return frames.length == 0 ? null : frames[(int) ((ticks + offset(stack)) % frames.length)];
			}
			default -> {
				return null;
			}
		}
	}

	/** Armor pieces run a few ticks apart so a full set ripples. */
	private static int offset(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null) {
			return 0;
		}
		EquipmentSlot slot = equippable.slot();
		return Math.max(0, Math.min(20, (slot.ordinal() - 2) * 5));
	}

	/** Blends the gradient in HSB, one color per tick, looping. */
	private static int[] gradientFrames(ColorSpec spec) {
		if (spec.frames != null) {
			return spec.frames;
		}
		List<Integer> stops = spec.colors == null ? List.of() : spec.colors;
		if (stops.isEmpty()) {
			return spec.frames = new int[0];
		}
		int total = Math.max(spec.time, stops.size());
		int each = Math.max(1, total / stops.size());
		List<float[]> hsb = new ArrayList<>();
		for (int rgb : stops) {
			hsb.add(Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null));
		}
		hsb.add(hsb.get(0));
		int[] out = new int[each * stops.size()];
		for (int i = 0; i < out.length; i++) {
			int current = i / each;
			float t = (i % each) / (float) each;
			float[] a = hsb.get(current);
			float[] b = hsb.get(Math.min(current + 1, hsb.size() - 1));
			float hue = a[0] + (b[0] - a[0]) * t;
			float sat = a[1] + (b[1] - a[1]) * t;
			float bri = a[2] + (b[2] - a[2]) * t;
			out[i] = Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
		}
		return spec.frames = out;
	}

	private static DyedItemColor dyed(int rgb) {
		return DYE_CACHE.computeIfAbsent(rgb & 0xFFFFFF, DyedItemColor::new);
	}

	private static ResolvableProfile profile(ItemStack stack, Data data) {
		SkinSpec skin = data == null ? null : data.skin;
		if (skin == null) {
			// Vanilla integration: animate an applied animated helmet skin.
			Info info = info(stack);
			if (info == null || info.vanillaSkin == null) {
				return null;
			}
			loadData();
			Skull skull = SKULLS.get(info.vanillaSkin);
			return skull == null ? null : texture(skull.textures[(int) ((ticks / skull.ticks) % skull.textures.length)]);
		}
		if (skin.value == null || skin.value.isBlank()) {
			return null;
		}
		switch (skin.type == null ? "" : skin.type) {
			case "animated" -> {
				loadData();
				Skull skull = SKULLS.get(skin.value.toUpperCase(Locale.ROOT));
				return skull == null ? null : texture(skull.textures[(int) ((ticks / skull.ticks) % skull.textures.length)]);
			}
			case "skyblock" -> {
				ItemStack item = ItemIds.resolve("sb:" + skin.value).stack();
				return item == null || item.isEmpty() ? null : item.get(DataComponents.PROFILE);
			}
			case "texture" -> {
				return texture(skin.value.trim());
			}
			default -> {
				return null;
			}
		}
	}

	private static ResolvableProfile texture(String base64) {
		return PROFILE_CACHE.computeIfAbsent(base64, value -> {
			PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value)));
			UUID uuid = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
			return ResolvableProfile.createResolved(new GameProfile(uuid, "Stray", properties));
		});
	}

	private static ArmorTrim trim(Data data) {
		if (data.trimPattern == null || data.trimMaterial == null) {
			return null;
		}
		String cacheKey = data.trimMaterial + "|" + data.trimPattern;
		ArmorTrim cached = TRIM_CACHE.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return null;
		}
		var access = client.level.registryAccess();
		Optional<? extends Holder<TrimMaterial>> material = access.lookupOrThrow(Registries.TRIM_MATERIAL)
			.get(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.parse(data.trimMaterial)));
		Optional<? extends Holder<TrimPattern>> pattern = access.lookupOrThrow(Registries.TRIM_PATTERN)
			.get(ResourceKey.create(Registries.TRIM_PATTERN, Identifier.parse(data.trimPattern)));
		if (material.isEmpty() || pattern.isEmpty()) {
			return null;
		}
		ArmorTrim trim = new ArmorTrim(material.get(), pattern.get());
		TRIM_CACHE.put(cacheKey, trim);
		return trim;
	}

	/** Trim registries change per world; drop cached trims when it changes. */
	public static void clearWorldCaches() {
		TRIM_CACHE.clear();
	}

	// ----------------------------------------------------------------- data

	public static List<String> staticDyes() {
		loadData();
		return new ArrayList<>(STATIC_DYES.keySet());
	}

	public static List<String> animatedDyes() {
		loadData();
		return new ArrayList<>(ANIMATED_DYES.keySet());
	}

	public static List<String> animatedSkulls() {
		loadData();
		return new ArrayList<>(SKULLS.keySet());
	}

	public static Integer staticDye(String id) {
		loadData();
		return id == null ? null : STATIC_DYES.get(id.toUpperCase(Locale.ROOT));
	}

	/** First frame of an animated dye, for swatches. */
	public static Integer animatedDyePreview(String id) {
		loadData();
		int[] frames = id == null ? null : ANIMATED_DYES.get(id.toUpperCase(Locale.ROOT));
		return frames == null || frames.length == 0 ? null : frames[(int) ((ticks / 2) % frames.length)];
	}

	private static synchronized void loadData() {
		if (dataLoaded) {
			return;
		}
		dataLoaded = true;
		JsonObject dyes = readJson("/assets/stray/customization/dyes.json");
		if (dyes != null) {
			for (String group : new String[]{"static", "vanilla"}) {
				JsonObject map = dyes.has(group) ? dyes.getAsJsonObject(group) : null;
				if (map == null) {
					continue;
				}
				for (Map.Entry<String, JsonElement> entry : map.entrySet()) {
					Integer rgb = hex(entry.getValue().getAsString());
					if (rgb != null) {
						STATIC_DYES.put(entry.getKey().toUpperCase(Locale.ROOT), rgb);
					}
				}
			}
			JsonObject animated = dyes.has("animated") ? dyes.getAsJsonObject("animated") : null;
			if (animated != null) {
				for (Map.Entry<String, JsonElement> entry : animated.entrySet()) {
					JsonArray list = entry.getValue().getAsJsonArray();
					int[] frames = new int[list.size()];
					for (int i = 0; i < frames.length; i++) {
						Integer rgb = hex(list.get(i).getAsString());
						frames[i] = rgb == null ? 0 : rgb;
					}
					if (frames.length > 0) {
						ANIMATED_DYES.put(entry.getKey().toUpperCase(Locale.ROOT), frames);
					}
				}
			}
		}
		JsonObject skulls = readJson("/assets/stray/customization/skulls.json");
		if (skulls != null) {
			for (Map.Entry<String, JsonElement> entry : skulls.entrySet()) {
				JsonObject skull = entry.getValue().getAsJsonObject();
				int frameTicks = Math.max(1, skull.has("ticks") ? skull.get("ticks").getAsInt() : 2);
				JsonArray list = skull.getAsJsonArray("textures");
				if (list == null || list.isEmpty()) {
					continue;
				}
				String[] textures = new String[list.size()];
				for (int i = 0; i < textures.length; i++) {
					String raw = list.get(i).getAsString();
					int colon = raw.indexOf(':');
					textures[i] = colon >= 0 ? raw.substring(colon + 1) : raw;
				}
				SKULLS.put(entry.getKey().toUpperCase(Locale.ROOT), new Skull(frameTicks, textures));
			}
		}
	}

	private static JsonObject readJson(String path) {
		try (InputStream in = CustomItems.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read {}", path, exception);
			return null;
		}
	}

	public static Integer hex(String raw) {
		if (raw == null) {
			return null;
		}
		String value = raw.trim();
		if (value.startsWith("#")) {
			value = value.substring(1);
		}
		if (value.length() != 6) {
			return null;
		}
		try {
			return Integer.parseInt(value, 16);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}
