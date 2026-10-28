package dev.stray.client.farming.greenhouse;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.stray.Stray;
import dev.stray.client.farming.PestEsp;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pastes a SkyMutations or SkyShards greenhouse layout and draws each missing
 * crop on the 10×10 grid.
 */
public final class GreenhousePaste {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PRESETS = FabricLoader.getInstance().getConfigDir().resolve("stray-greenhouse.json");
	private static final Set<String> IGNORE = Set.of("PlantboyRoots", "godseedPillar");
	private static final int FULL_BRIGHT = 15728880;
	private static final Map<String, String> SAVED = new LinkedHashMap<>();
	private static final Map<Integer, ItemStack> STACKS = new HashMap<>();
	private static final Map<Integer, BlockState> BLOCKS = new HashMap<>();

	private static final int[][] CURRENT = new int[10][10];
	private static int[][] target = GreenhouseLayout.empty();
	private static BlockPos corner;
	private static ClientLevel world;
	private static int scanDelay;

	private GreenhousePaste() {
	}

	public static void init() {
		load();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
		ClientTickEvents.END_CLIENT_TICK.register(GreenhousePaste::tick);
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> boxes());
		LevelRenderEvents.COLLECT_SUBMITS.register(GreenhousePaste::pictures);
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("greenhouse")
			.then(ClientCommands.literal("paste")
				.executes(context -> paste(null))
				.then(ClientCommands.argument("preset", StringArgumentType.greedyString())
					.suggests((context, builder) -> SharedSuggestionProvider.suggest(SAVED.keySet(), builder))
					.executes(context -> paste(StringArgumentType.getString(context, "preset")))))
			.then(ClientCommands.literal("endPaste").executes(context -> clear()))
			.then(ClientCommands.literal("clear").executes(context -> clear()))
			.then(ClientCommands.literal("rotate")
				.then(ClientCommands.literal("left").executes(context -> rotate(true)))
				.then(ClientCommands.literal("right").executes(context -> rotate(false))))
			.then(ClientCommands.literal("mirror").executes(context -> mirror()))
			.then(ClientCommands.literal("save")
				.then(ClientCommands.argument("name", StringArgumentType.greedyString())
					.executes(context -> savePreset(StringArgumentType.getString(context, "name")))))
			.then(ClientCommands.literal("presets").executes(context -> listPresets()));
	}

	private static void tick(Minecraft client) {
		if (client.level != world) {
			world = client.level;
			corner = null;
		}
		if (!GreenhouseLayout.occupied(target) || client.player == null || client.level == null) {
			return;
		}
		if (!SkyblockLocation.inGarden()) {
			return;
		}
		if (--scanDelay > 0) {
			return;
		}
		scanDelay = 10;
		if (corner == null) {
			if (!inGreenhouse(false)) {
				return;
			}
			locate();
			return;
		}
		if (near(client)) {
			scan();
		}
	}

	private static int paste(String preset) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return Command.SINGLE_SUCCESS;
		}
		if (!SkyblockLocation.inGarden()) {
			tell("Stand in your garden first.", true);
			return Command.SINGLE_SUCCESS;
		}
		if (!inGreenhouse(true)) {
			return Command.SINGLE_SUCCESS;
		}
		String link = linkFor(preset, client);
		if (link == null || link.isBlank()) {
			tell("Copy a SkyMutations or SkyShards Greenhouse link first.", true);
			return Command.SINGLE_SUCCESS;
		}
		int[][] laid = GreenhouseLayout.decode(link);
		if (laid == null) {
			tell("Couldn't read that greenhouse layout.", true);
			return Command.SINGLE_SUCCESS;
		}
		target = laid;
		locate();
		tell("Pasted the greenhouse layout.", false);
		return Command.SINGLE_SUCCESS;
	}

	private static String linkFor(String preset, Minecraft client) {
		if (preset == null || preset.isBlank()) {
			return client.keyboardHandler.getClipboard();
		}
		String saved = saved(preset);
		if (saved != null) {
			return saved;
		}
		return preset;
	}

	private static String saved(String name) {
		String direct = SAVED.get(name);
		if (direct != null) {
			return direct;
		}
		for (Map.Entry<String, String> entry : SAVED.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name)) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static int clear() {
		target = GreenhouseLayout.empty();
		corner = null;
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 10; z++) {
				CURRENT[x][z] = 0;
			}
		}
		tell("Cleared the greenhouse preview.", false);
		return Command.SINGLE_SUCCESS;
	}

	private static int rotate(boolean left) {
		if (!GreenhouseLayout.occupied(target)) {
			tell("Paste a layout first.", true);
			return Command.SINGLE_SUCCESS;
		}
		int[][] next = new int[10][10];
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 10; z++) {
				if (left) {
					next[z][9 - x] = target[x][z];
				} else {
					next[9 - z][x] = target[x][z];
				}
			}
		}
		target = next;
		tell(left ? "Rotated the layout left." : "Rotated the layout right.", false);
		return Command.SINGLE_SUCCESS;
	}

	private static int mirror() {
		if (!GreenhouseLayout.occupied(target)) {
			tell("Paste a layout first.", true);
			return Command.SINGLE_SUCCESS;
		}
		int[][] next = new int[10][10];
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 10; z++) {
				next[9 - x][z] = target[x][z];
			}
		}
		target = next;
		tell("Mirrored the layout.", false);
		return Command.SINGLE_SUCCESS;
	}

	private static int savePreset(String name) {
		String trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			tell("Name the preset.", true);
			return Command.SINGLE_SUCCESS;
		}
		Minecraft client = Minecraft.getInstance();
		String link = client.keyboardHandler == null ? "" : client.keyboardHandler.getClipboard();
		if (link == null || link.isBlank()) {
			tell("Copy a layout link first.", true);
			return Command.SINGLE_SUCCESS;
		}
		SAVED.put(trimmed, link.strip());
		save();
		tell("Saved greenhouse preset " + trimmed + ".", false);
		return Command.SINGLE_SUCCESS;
	}

	private static int listPresets() {
		if (SAVED.isEmpty()) {
			tell("No greenhouse presets saved.", false);
			return Command.SINGLE_SUCCESS;
		}
		tell("Greenhouse presets: " + String.join(", ", SAVED.keySet()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static boolean inGreenhouse(boolean complain) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return false;
		}
		BlockPos plot = plotCorner(client.player.blockPosition());
		AABB box = new AABB(plot.getX(), 60, plot.getZ(), plot.getX() + 96, 90, plot.getZ() + 96);
		for (Entity entity : client.level.getEntities(client.player, box, entity -> true)) {
			Component custom = entity.getCustomName();
			if (custom != null && custom.getString().contains("Carpenter")) {
				return true;
			}
		}
		if (complain) {
			tell("Stand in the greenhouse first.", true);
		}
		return false;
	}

	private static void locate() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		BlockPos plot = plotCorner(client.player.blockPosition());
		corner = new BlockPos(plot.getX() + 43, 73, plot.getZ() + 43);
		scan();
	}

	private static BlockPos plotCorner(BlockPos player) {
		int x = ((int) ((player.getX() + 240) / 96.0)) * 96 - 240;
		int z = ((int) ((player.getZ() + 240) / 96.0)) * 96 - 240;
		return new BlockPos(x, 73, z);
	}

	private static void scan() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null || corner == null) {
			return;
		}
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 10; z++) {
				BlockPos pos = new BlockPos(corner.getX() + x, corner.getY(), corner.getZ() + z);
				CURRENT[x][z] = cropAt(client, pos);
			}
		}
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				spreadPlantboy(client, x, z);
				spreadSnoozling(x, z);
				spreadGodseed(x, z);
			}
		}
	}

	private static int cropAt(Minecraft client, BlockPos pos) {
		AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 6, pos.getZ() + 1);
		GreenhouseCrops.Crop found = null;
		int score = -1;
		boolean prior = ItemAppearance.suppress();
		try {
			for (Entity entity : client.level.getEntities(client.player, box, entity -> entity instanceof ArmorStand)) {
				ItemStack head = ((ArmorStand) entity).getItemBySlot(EquipmentSlot.HEAD);
				if (head.isEmpty()) {
					continue;
				}
				String name = plain(head.getHoverName().getString());
				if (IGNORE.contains(name)) {
					continue;
				}
				for (GreenhouseCrops.Crop crop : GreenhouseCrops.all()) {
					String stand = crop.standName();
					if (stand.isEmpty()) {
						continue;
					}
					boolean direct = name.contains(stand);
					boolean loose = name.toLowerCase(Locale.ROOT).contains(stand.toLowerCase(Locale.ROOT));
					if (!direct && !loose) {
						continue;
					}
					int next = stand.length() + (direct ? 100 : 0);
					if (next > score) {
						found = crop;
						score = next;
					}
				}
				if (found == null) {
					GreenhouseCrops.Crop byTexture = GreenhouseCrops.byTextureHash(textureHash(PestEsp.headTexture(head)));
					if (byTexture != null) {
						found = byTexture;
					}
				}
			}
		} finally {
			ItemAppearance.resume(prior);
		}
		if (found != null) {
			return found.id();
		}
		BlockState state = client.level.getBlockState(pos.above());
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		for (GreenhouseCrops.Crop crop : GreenhouseCrops.all()) {
			if (id.equals(crop.block())) {
				return crop.id();
			}
		}
		return 0;
	}

	private static void spreadPlantboy(Minecraft client, int x, int z) {
		if (CURRENT[x][z] != 26 || corner == null) {
			return;
		}
		BlockPos pos = corner.offset(x, 0, z);
		AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 6, pos.getZ() + 1);
		ArmorStand stand = null;
		for (Entity entity : client.level.getEntities(client.player, box, entity -> entity instanceof ArmorStand)) {
			ItemStack head = ((ArmorStand) entity).getItemBySlot(EquipmentSlot.HEAD);
			if (head.isEmpty()) {
				continue;
			}
			String name = plain(head.getHoverName().getString()).toLowerCase(Locale.ROOT);
			if (name.contains("plantboy") && !name.contains("advance")) {
				stand = (ArmorStand) entity;
				break;
			}
		}
		if (stand == null) {
			return;
		}
		int lx = (int) Math.floor(stand.getX() - 0.2) - corner.getX();
		int lz = (int) Math.floor(stand.getZ() - 0.2) - corner.getZ();
		fill(lx, lz, 2, 26);
	}

	private static void spreadSnoozling(int x, int z) {
		if (CURRENT[x][z] != 23) {
			return;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -2; dz <= 0; dz++) {
				set(x + dx, z + dz, 23);
			}
		}
	}

	private static void spreadGodseed(int x, int z) {
		if (CURRENT[x][z] != 37) {
			return;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				set(x + dx, z + dz, 37);
			}
		}
	}

	private static void fill(int x, int z, int size, int id) {
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				set(x + dx, z + dz, id);
			}
		}
	}

	private static void set(int x, int z, int id) {
		if (x >= 0 && z >= 0 && x < 10 && z < 10) {
			CURRENT[x][z] = id;
		}
	}

	private static void boxes() {
		Minecraft client = Minecraft.getInstance();
		if (!showing(client)) {
			return;
		}
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 10; z++) {
				int want = target[x][z];
				if (want == -1 || matches(CURRENT[x][z], want)) {
					continue;
				}
				BlockPos pos = new BlockPos(corner.getX() + x, corner.getY() + 1, corner.getZ() + z);
				if (want == 0) {
					if (CURRENT[x][z] == 0) {
						AABB pad = new AABB(pos.getX(), pos.getY() - 0.05, pos.getZ(), pos.getX() + 1, pos.getY() + 0.08, pos.getZ() + 1);
						mark(pad, 0x22C55E, 0.35f);
					} else {
						mark(new AABB(pos), 0xEF4444, 0.45f);
					}
				} else if (CURRENT[x][z] != 0) {
					mark(new AABB(pos), 0xEF4444, 0.45f);
				}
			}
		}
	}

	private static void mark(AABB box, int rgb, float fillOpacity) {
		int fill = (Math.round(fillOpacity * 255f) << 24) | rgb;
		int line = (Math.round(0.95f * 255f) << 24) | rgb;
		GizmoProperties cuboid = Gizmos.cuboid(box, GizmoStyle.strokeAndFill(line, 2.0f, fill));
		cuboid.setAlwaysOnTop();
	}

	private static void pictures(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!showing(client) || context.poseStack() == null) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 10; z++) {
				int want = target[x][z];
				if (want <= 0 || matches(CURRENT[x][z], want) || CURRENT[x][z] != 0) {
					continue;
				}
				GreenhouseCrops.Crop crop = GreenhouseCrops.byId(want);
				if (crop == null) {
					continue;
				}
				BlockPos pos = new BlockPos(corner.getX() + x, corner.getY() + 1, corner.getZ() + z);
				pose.pushPose();
				pose.translate(pos.getX() - camera.x + 0.5, pos.getY() - camera.y + 0.5, pos.getZ() - camera.z + 0.5);
				submitItem(client, collector, pose, stack(crop), want);
				pose.popPose();
			}
		}
	}

	private static void submitItem(Minecraft client, SubmitNodeCollector collector, PoseStack pose, ItemStack stack, int seed) {
		if (stack.isEmpty()) {
			return;
		}
		var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
		client.getItemModelResolver().updateForTopItem(
			state,
			stack,
			ItemDisplayContext.FIXED,
			client.level,
			null,
			seed
		);
		state.submit(pose, collector, FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
	}

	private static ItemStack stack(GreenhouseCrops.Crop crop) {
		ItemStack cached = STACKS.get(crop.id());
		if (cached != null) {
			return cached;
		}
		ItemStack stack;
		if (crop.head()) {
			stack = new ItemStack(Items.PLAYER_HEAD);
			stack.set(DataComponents.PROFILE, profile(crop));
		} else {
			BlockState state = blockState(crop);
			Item item = state.getBlock().asItem();
			if (item == Items.AIR && "fire".equals(crop.block())) {
				item = Items.FIRE_CHARGE;
			}
			stack = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
		}
		STACKS.put(crop.id(), stack);
		return stack;
	}

	private static BlockState blockState(GreenhouseCrops.Crop crop) {
		BlockState cached = BLOCKS.get(crop.id());
		if (cached != null) {
			return cached;
		}
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + crop.block()));
		BlockState state = block.defaultBlockState();
		for (Property<?> property : state.getProperties()) {
			if ("age".equals(property.getName()) && property instanceof IntegerProperty age) {
				int max = 0;
				for (int value : age.getPossibleValues()) {
					if (value > max) {
						max = value;
					}
				}
				state = state.setValue(age, max);
			}
		}
		BLOCKS.put(crop.id(), state);
		return state;
	}

	private static ResolvableProfile profile(GreenhouseCrops.Crop crop) {
		PropertyMap properties = new PropertyMap(ImmutableMultimap.of(
			"textures",
			new com.mojang.authlib.properties.Property("textures", crop.texture())
		));
		UUID uuid = UUID.nameUUIDFromBytes(crop.texture().getBytes(StandardCharsets.UTF_8));
		return ResolvableProfile.createResolved(new GameProfile(uuid, "Stray", properties));
	}

	private static boolean showing(Minecraft client) {
		return GreenhouseLayout.occupied(target)
			&& corner != null
			&& client.player != null
			&& client.level != null
			&& SkyblockLocation.inGarden()
			&& near(client);
	}

	private static boolean near(Minecraft client) {
		double x = client.player.getX();
		double z = client.player.getZ();
		return x >= corner.getX() - 43 && x <= corner.getX() + 53
			&& z >= corner.getZ() - 43 && z <= corner.getZ() + 53;
	}

	private static boolean matches(int current, int want) {
		if (current == want) {
			return true;
		}
		return (current == 48 || current == 49) && (want == 48 || want == 49);
	}

	private static String textureHash(String texture) {
		String hash = PestEsp.textureHash(texture);
		if (!hash.isEmpty() || texture == null || texture.isEmpty()) {
			return hash;
		}
		int pad = (4 - texture.length() % 4) % 4;
		return PestEsp.textureHash(texture + "=".repeat(pad));
	}

	private static String plain(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}

	private static void load() {
		if (!Files.isRegularFile(PRESETS)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(PRESETS)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject presets = root.has("presets") ? root.getAsJsonObject("presets") : root;
			for (String name : presets.keySet()) {
				if (presets.get(name).isJsonPrimitive()) {
					SAVED.put(name, presets.get(name).getAsString());
				}
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read stray-greenhouse.json", exception);
		}
	}

	private static void save() {
		JsonObject presets = new JsonObject();
		for (Map.Entry<String, String> entry : SAVED.entrySet()) {
			presets.addProperty(entry.getKey(), entry.getValue());
		}
		JsonObject root = new JsonObject();
		root.add("presets", presets);
		try {
			Files.createDirectories(PRESETS.getParent());
			try (Writer writer = Files.newBufferedWriter(PRESETS)) {
				GSON.toJson(root, writer);
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not write stray-greenhouse.json", exception);
		}
	}

	private static void tell(String text, boolean problem) {
		MutableComponent line = Component.literal("STRAY").withStyle(style(Theme.ACCENT).withBold(true))
			.append(Component.literal(" | ").withStyle(style(Theme.MUTED)))
			.append(Component.literal("GREENHOUSE").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal(" " + text).withStyle(style(problem ? 0xFB7185 : Theme.TEXT)));
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(line);
		}
	}

	private static Style style(int color) {
		return Style.EMPTY.withColor(color & 0xFFFFFF);
	}
}
