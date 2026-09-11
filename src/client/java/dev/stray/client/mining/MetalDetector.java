package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.mixin.GuiAccessor;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.ui.Anim;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mines of Divan metal-detector solver from NotEnoughUpdates: stand still for
 * two identical TREASURE readings, brute-force blocks at y 65–75, then narrow
 * with later readings and the known chest offsets once a Keeper gives the
 * mines center.
 */
public final class MetalDetector {
	private enum SolutionState {
		NOT_STARTED,
		MULTIPLE,
		MULTIPLE_KNOWN,
		FOUND,
		FOUND_KNOWN,
		FAILED,
		INVALID
	}

	private static final Pattern TREASURE = Pattern.compile("TREASURE:\\s*([\\d.,]+)\\s*m", Pattern.CASE_INSENSITIVE);
	private static final Pattern FOUND = Pattern.compile("You found .*with your .*Metal Detector", Pattern.CASE_INSENSITIVE);
	private static final Pattern TOOL = Pattern.compile("Scavenged ([A-Za-z ]+?)(?:\\s+with your|$)");
	private static final String DETECTOR = "DWARVEN_METAL_DETECTOR";
	private static final String[] TOOLS = {
		"DWARVEN_LAPIS_SWORD",
		"DWARVEN_DIAMOND_AXE",
		"DWARVEN_EMERALD_HAMMER",
		"DWARVEN_GOLD_HAMMER"
	};
	private static final String KEEPER_OF = "keeper of ";
	private static final Map<String, BlockPos> KEEPER_OFFSETS = Map.of(
		"diamond", new BlockPos(33, 0, 3),
		"lapis", new BlockPos(-33, 0, -3),
		"emerald", new BlockPos(-3, 0, 33),
		"gold", new BlockPos(3, 0, -33)
	);
	private static final Set<BlockPos> KNOWN_CHESTS = Set.of(
		new BlockPos(-38, -22, 26),
		new BlockPos(38, -22, -26),
		new BlockPos(-40, -22, 18),
		new BlockPos(-41, -20, 22),
		new BlockPos(-5, -21, 16),
		new BlockPos(40, -22, -30),
		new BlockPos(-42, -20, -28),
		new BlockPos(-43, -22, -40),
		new BlockPos(42, -19, -41),
		new BlockPos(43, -21, -16),
		new BlockPos(-1, -22, -20),
		new BlockPos(6, -21, 28),
		new BlockPos(7, -21, 11),
		new BlockPos(7, -21, 22),
		new BlockPos(-12, -21, -44),
		new BlockPos(12, -22, 31),
		new BlockPos(12, -22, -22),
		new BlockPos(12, -21, 7),
		new BlockPos(12, -21, -43),
		new BlockPos(-14, -21, 43),
		new BlockPos(-14, -21, 22),
		new BlockPos(-17, -21, 20),
		new BlockPos(-20, -22, 0),
		new BlockPos(1, -21, 20),
		new BlockPos(19, -22, 29),
		new BlockPos(20, -22, 0),
		new BlockPos(20, -21, -26),
		new BlockPos(-23, -22, 40),
		new BlockPos(22, -21, -14),
		new BlockPos(-24, -22, 12),
		new BlockPos(23, -22, 26),
		new BlockPos(23, -22, -39),
		new BlockPos(24, -22, 27),
		new BlockPos(25, -22, 17),
		new BlockPos(29, -21, -44),
		new BlockPos(-31, -21, -12),
		new BlockPos(-31, -21, -40),
		new BlockPos(30, -21, -25),
		new BlockPos(-32, -21, -40),
		new BlockPos(-36, -20, 42),
		new BlockPos(-37, -21, -14),
		new BlockPos(-37, -21, -22)
	);
	private static final float TAG_H = 14f;
	private static final float PAD_X = 8f;

	private static final List<BlockPos> PREDICTIONS = new ArrayList<>();
	private static final Set<BlockPos> POSSIBLE = new HashSet<>();
	private static final Map<Vec3, Double> EVALUATED = new HashMap<>();
	private static final Set<BlockPos> OPENED = new HashSet<>();

	private static Vec3 prevPlayerPos;
	private static double prevDist;
	private static BlockPos minesCenter;
	private static SolutionState state = SolutionState.NOT_STARTED;
	private static SolutionState previousState = SolutionState.NOT_STARTED;
	private static boolean chestRecentlyFound;
	private static long chestLastFoundMillis;
	private static boolean visitKeeperPrinted;
	private static boolean plinged;
	private static boolean allToolsBeeped;
	private static boolean inDivan;
	private static long lastTreasureMs;

	private MetalDetector() {
	}

	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.metalDetectorSolver && !config.metalDetectorToolTitle) {
			if (inDivan) {
				reset();
			}
			return;
		}
		boolean here = inDivanNow(client);
		if (here != inDivan) {
			if (!here) {
				reset();
			}
			inDivan = here;
		}
		if (!here || client.player == null || client.level == null) {
			return;
		}
		if (config.metalDetectorSolver) {
			locateMinesCenter(client, false);
			pollActionBar(client);
		}
		if (config.metalDetectorToolTitle && holdingDetector(client.player) && hasAllTools(client.player)) {
			if (client.player.tickCount % 20 == 0) {
				showTitle(client, "ALL TOOLS", "");
			}
			if (!allToolsBeeped) {
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f));
				allToolsBeeped = true;
			}
		} else {
			allToolsBeeped = false;
		}
	}

	public static void onActionBar(Component message) {
		onMessage(message, true);
	}

	public static void onMessage(Component message, boolean overlay) {
		if (message == null) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.metalDetectorSolver && !config.metalDetectorToolTitle) {
			return;
		}
		String text = plain(message);
		if (overlay || TREASURE.matcher(text).find()) {
			if (config.metalDetectorSolver && SkyblockLocation.inSkyblock) {
				readTreasure(text);
			}
			if (overlay) {
				return;
			}
		}
		if (overlay || !inDivanNow(Minecraft.getInstance())) {
			return;
		}
		if (!FOUND.matcher(text).find()) {
			return;
		}
		resetSolution(true);
		plinged = false;
		if (config.metalDetectorToolTitle && !config.nucleusAlertTools) {
			Matcher tool = TOOL.matcher(text);
			if (tool.find()) {
				showTitle(Minecraft.getInstance(), "SCAVENGED " + tool.group(1).trim().toUpperCase(Locale.ROOT), "Metal Detector");
				Minecraft client = Minecraft.getInstance();
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f, 1.0f));
			}
		}
	}

	public static void onWorldChange() {
		reset();
	}

	public static void reset() {
		resetSolution(false);
		PREDICTIONS.clear();
		OPENED.clear();
		minesCenter = null;
		prevPlayerPos = null;
		prevDist = 0;
		chestLastFoundMillis = 0;
		visitKeeperPrinted = false;
		plinged = false;
		allToolsBeeped = false;
		inDivan = false;
		lastTreasureMs = 0L;
		state = SolutionState.NOT_STARTED;
		previousState = SolutionState.NOT_STARTED;
	}

	public static boolean active() {
		return StrayConfig.get().metalDetectorSolver && inDivanNow(Minecraft.getInstance()) && !PREDICTIONS.isEmpty();
	}

	public static List<BlockPos> predictions() {
		return PREDICTIONS;
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		Font font = client.font;
		boolean unique = PREDICTIONS.size() == 1;
		for (BlockPos pos : PREDICTIONS) {
			Vec3 head = Vec3.atCenterOf(pos).add(0, 1.2, 0);
			Vec3 rel = head.subtract(camPos);
			double facing = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
			if (facing <= 0.12) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(head);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			double dist = head.distanceTo(camPos);
			float scale = NametagRenderer.distanceScale(dist);
			Component name = MenuFont.vanilla(unique ? "Treasure" : "Possible");
			Component meters = MenuFont.vanilla(GuiDraw.meters(dist));
			float nameW = font.width(name);
			float distW = font.width(meters);
			float inner = nameW + 5f + distW;
			float w = inner + PAD_X * 2f;
			int rgb = unique ? 0x55FF55 : 0xFFAA00;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			if (scale != 1.0f) {
				graphics.pose().scale(scale, scale);
			}
			float left = -w * 0.5f;
			float top = -2f - TAG_H;
			GuiDraw.panel(graphics, left, top, w, TAG_H, 5, Theme.WINDOW, Theme.LINE);
			GuiDraw.text(graphics, font, name, left + PAD_X, GuiDraw.middle(top, TAG_H), 0xFF000000 | rgb, false);
			GuiDraw.text(graphics, font, meters, left + PAD_X + nameW + 5f, GuiDraw.middle(top, TAG_H), Anim.fade(Theme.MUTED, 1f), false);
			graphics.pose().popMatrix();
		}
	}

	public static void emitBoxes() {
		if (!active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Vec3 eyes = client.player == null ? Vec3.ZERO : client.player.getEyePosition();
		int rgb = PREDICTIONS.size() == 1 ? 0x55FF55 : 0xFFAA00;
		int line = 0xEB000000 | rgb;
		int fill = 0x55000000 | rgb;
		for (BlockPos pos : PREDICTIONS) {
			GizmoProperties cuboid = Gizmos.cuboid(new AABB(pos).inflate(0.15), GizmoStyle.strokeAndFill(line, 2.4f, fill));
			cuboid.setAlwaysOnTop();
			Vec3 center = Vec3.atCenterOf(pos);
			GizmoProperties tracer = Gizmos.line(eyes, center, 0xC0FFFFFF, 2.4f);
			tracer.setAlwaysOnTop();
		}
	}

	private static void pollActionBar(Minecraft client) {
		if (client.gui instanceof GuiAccessor accessor) {
			Component overlay = accessor.stray$overlayMessage();
			if (overlay != null) {
				readTreasure(plain(overlay));
			}
		}
	}

	private static void readTreasure(String text) {
		Matcher matcher = TREASURE.matcher(text);
		if (!matcher.find()) {
			return;
		}
		lastTreasureMs = System.currentTimeMillis();
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return;
		}
		double distance;
		try {
			distance = Double.parseDouble(matcher.group(1).replace(',', '.'));
		} catch (NumberFormatException ignored) {
			return;
		}
		if (chestRecentlyFound) {
			long now = System.currentTimeMillis();
			if (chestLastFoundMillis == 0L) {
				chestLastFoundMillis = now;
				return;
			}
			if (now - chestLastFoundMillis < 1000L && distance < 5.0) {
				return;
			}
			chestLastFoundMillis = 0L;
			chestRecentlyFound = false;
		}
		boolean centerNew = locateMinesCenter(client, true);
		SolutionState before = state;
		int beforeCount = POSSIBLE.size();
		findPossibleSolutions(distance, adjustedFeet(player), centerNew);
		if (state != before || POSSIBLE.size() != beforeCount) {
			announce(client);
			syncPredictions();
		}
	}

	private static void findPossibleSolutions(double distToTreasure, Vec3 playerPos, boolean centerNewlyDiscovered) {
		if (prevPlayerPos != null
			&& prevDist == distToTreasure
			&& prevPlayerPos.equals(playerPos)
			&& !EVALUATED.containsKey(playerPos)) {
			EVALUATED.put(playerPos, distToTreasure);
			if (POSSIBLE.isEmpty()) {
				int minZ = (int) Math.floor(-distToTreasure);
				int maxZ = (int) Math.ceil(distToTreasure);
				int maxX = maxZ + 4;
				int originX = (int) Math.floor(playerPos.x);
				int originZ = (int) Math.floor(playerPos.z);
				for (int zOffset = minZ; zOffset <= maxZ; zOffset++) {
					for (int y = 65; y <= 75; y++) {
						scanAxis(playerPos, distToTreasure, originX, y, originZ + zOffset, 1, maxX);
						scanAxis(playerPos, distToTreasure, originX, y, originZ + zOffset, -1, maxX);
					}
				}
				updateSolutionState();
			} else if (POSSIBLE.size() != 1) {
				Set<BlockPos> kept = new HashSet<>();
				for (BlockPos pos : POSSIBLE) {
					if (round1(playerPos.distanceTo(Vec3.atLowerCornerOf(pos).add(0, 1, 0))) == distToTreasure) {
						kept.add(pos);
					}
				}
				POSSIBLE.clear();
				POSSIBLE.addAll(kept);
				updateSolutionState();
			} else {
				BlockPos pos = POSSIBLE.iterator().next();
				if (Math.abs(distToTreasure - playerPos.distanceTo(Vec3.atLowerCornerOf(pos))) > 5) {
					state = SolutionState.INVALID;
				}
			}
		} else if (centerNewlyDiscovered && POSSIBLE.size() > 1) {
			updateSolutionState();
		}
		prevPlayerPos = playerPos;
		prevDist = distToTreasure;
	}

	private static void scanAxis(Vec3 playerPos, double distToTreasure, int originX, int y, int z, int step, int maxOffset) {
		double calculated = 0;
		int offset = 0;
		while (calculated < distToTreasure && offset <= maxOffset) {
			BlockPos pos = new BlockPos(originX + offset * step, y, z);
			calculated = playerPos.distanceTo(Vec3.atLowerCornerOf(pos).add(0, 1, 0));
			if (round1(calculated) == distToTreasure && treasureAllowed(pos)) {
				POSSIBLE.add(pos);
			}
			offset++;
		}
	}

	private static void updateSolutionState() {
		previousState = state;
		if (POSSIBLE.isEmpty()) {
			state = SolutionState.FAILED;
			return;
		}
		if (POSSIBLE.size() == 1) {
			state = SolutionState.FOUND;
			return;
		}
		if (minesCenter == null) {
			state = SolutionState.MULTIPLE;
			return;
		}
		Set<BlockPos> known = new HashSet<>();
		for (BlockPos pos : POSSIBLE) {
			if (KNOWN_CHESTS.contains(pos.subtract(minesCenter))) {
				known.add(pos);
			}
		}
		if (known.isEmpty()) {
			state = SolutionState.MULTIPLE;
			return;
		}
		POSSIBLE.clear();
		POSSIBLE.addAll(known);
		state = known.size() == 1 ? SolutionState.FOUND_KNOWN : SolutionState.MULTIPLE_KNOWN;
	}

	private static void announce(Minecraft client) {
		switch (state) {
			case FOUND, FOUND_KNOWN -> {
				if (!plinged) {
					client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f));
					plinged = true;
				}
				chat("Found treasure.", ChatFormatting.GREEN);
			}
			case MULTIPLE, MULTIPLE_KNOWN -> chat(
				"Need another reading. " + POSSIBLE.size() + " possible.",
				ChatFormatting.YELLOW
			);
			case INVALID -> {
				chat("Previous solution is invalid.", ChatFormatting.RED);
				resetSolution(false);
			}
			case FAILED -> {
				chat("No treasure match. Stand still and take another reading.", ChatFormatting.RED);
				resetSolution(false);
			}
			default -> {
			}
		}
	}

	private static void resetSolution(boolean chestFound) {
		if (chestFound) {
			prevPlayerPos = null;
			prevDist = 0;
			if (POSSIBLE.size() == 1) {
				OPENED.add(POSSIBLE.iterator().next().immutable());
			}
		}
		chestRecentlyFound = chestFound;
		POSSIBLE.clear();
		EVALUATED.clear();
		PREDICTIONS.clear();
		previousState = state;
		state = SolutionState.NOT_STARTED;
		plinged = false;
	}

	private static void syncPredictions() {
		PREDICTIONS.clear();
		PREDICTIONS.addAll(POSSIBLE);
	}

	private static boolean locateMinesCenter(Minecraft client, boolean announceMissing) {
		if (minesCenter != null) {
			return false;
		}
		if (client.level == null || client.player == null) {
			return false;
		}
		ArmorStand keeper = findKeeper(client.level, client.player);
		if (keeper == null) {
			if (announceMissing && !visitKeeperPrinted) {
				chat("Walk up to a Keeper to lock the mines center.", ChatFormatting.YELLOW);
				visitKeeperPrinted = true;
			}
			return false;
		}
		String type = keeperType(keeper);
		BlockPos offset = type == null ? null : KEEPER_OFFSETS.get(type);
		if (offset == null) {
			return false;
		}
		minesCenter = keeper.blockPosition().offset(offset);
		chat("Keeper locked. Using known chest spots.", ChatFormatting.GREEN);
		return true;
	}

	private static ArmorStand findKeeper(ClientLevel level, LocalPlayer player) {
		ArmorStand best = null;
		double bestSq = Double.MAX_VALUE;
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand)) {
				continue;
			}
			if (keeperType(stand) == null) {
				continue;
			}
			double sq = stand.distanceToSqr(player);
			if (sq < bestSq) {
				bestSq = sq;
				best = stand;
			}
		}
		return best;
	}

	private static String keeperType(ArmorStand stand) {
		String name = plain(stand.getCustomName());
		if (name.isEmpty()) {
			name = plain(stand.getDisplayName());
		}
		String lower = name.toLowerCase(Locale.ROOT);
		int index = lower.indexOf(KEEPER_OF);
		if (index < 0) {
			return null;
		}
		String rest = lower.substring(index + KEEPER_OF.length()).trim();
		for (String key : KEEPER_OFFSETS.keySet()) {
			if (rest.contains(key)) {
				return key;
			}
		}
		return null;
	}

	private static boolean treasureAllowed(BlockPos pos) {
		return isKnownOffset(pos) || (isAirAbove(pos) && allowedBlock(pos));
	}

	private static boolean isKnownOffset(BlockPos pos) {
		return minesCenter != null && KNOWN_CHESTS.contains(pos.subtract(minesCenter));
	}

	private static boolean isAirAbove(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		return client.level != null && client.level.getBlockState(pos.above()).isAir();
	}

	private static boolean allowedBlock(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return false;
		}
		BlockState state = client.level.getBlockState(pos);
		if (state.is(Blocks.GOLD_BLOCK)
			|| state.is(Blocks.CHEST)
			|| state.is(Blocks.TRAPPED_CHEST)
			|| state.is(Blocks.PRISMARINE)
			|| state.is(Blocks.PRISMARINE_BRICKS)
			|| state.is(Blocks.DARK_PRISMARINE)) {
			return true;
		}
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.contains("stained_glass")
			|| path.endsWith("_wool")
			|| path.equals("wool")
			|| path.endsWith("_terracotta")
			|| path.equals("terracotta");
	}

	private static Vec3 adjustedFeet(LocalPlayer player) {
		float extra = player.getEyeHeight() - player.getEyeHeight(net.minecraft.world.entity.Pose.STANDING);
		return player.position().add(0, extra, 0);
	}

	private static boolean inDivanNow(Minecraft client) {
		if (SkyblockLocation.inMinesOfDivan()) {
			return true;
		}
		if (System.currentTimeMillis() - lastTreasureMs < 20_000L) {
			return SkyblockLocation.inSkyblock || SkyblockLocation.inCrystalHollows();
		}
		if (!SkyblockLocation.inCrystalHollows()) {
			return false;
		}
		return client != null && client.player != null && holdingDetector(client.player);
	}

	private static String plain(Component message) {
		if (message == null) {
			return "";
		}
		return message.getString().replaceAll("§.", "").replace('\u00a7', ' ').trim();
	}

	private static void showTitle(Minecraft client, String title, String subtitle) {
		Gui gui = client.gui;
		if (gui == null) {
			return;
		}
		gui.setTimes(5, 40, 8);
		gui.setTitle(Component.literal(title));
		gui.setSubtitle(subtitle == null || subtitle.isEmpty() ? Component.empty() : Component.literal(subtitle));
	}

	private static boolean holdingDetector(LocalPlayer player) {
		return DETECTOR.equals(ItemIds.skyblockId(player.getMainHandItem()))
			|| DETECTOR.equals(ItemIds.skyblockId(player.getOffhandItem()));
	}

	private static boolean hasAllTools(LocalPlayer player) {
		boolean[] have = new boolean[TOOLS.length];
		Inventory inventory = player.getInventory();
		int size = inventory.getContainerSize();
		for (int i = 0; i < size; i++) {
			markTool(inventory.getItem(i), have);
		}
		for (boolean found : have) {
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private static void markTool(ItemStack stack, boolean[] have) {
		String id = ItemIds.skyblockId(stack);
		if (id == null) {
			return;
		}
		for (int i = 0; i < TOOLS.length; i++) {
			if (TOOLS[i].equals(id)) {
				have[i] = true;
			}
		}
	}

	private static double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

	private static void chat(String text, ChatFormatting color) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null) {
			return;
		}
		client.gui.getChat().addClientSystemMessage(
			Component.literal("Stray detector ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(text).withStyle(color))
		);
	}
}
