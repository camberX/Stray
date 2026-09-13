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
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mines of Divan metal-detector solver from SoopyV2: lock the quartz-stair /
 * barrier origin, then match the TREASURE action-bar distance against the
 * known chest offsets while standing still.
 */
public final class MetalDetector {
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
	/** Relative offsets from SoopyV2 {@code features/mining/coords.json}. */
	private static final int[][] CHESTS = {
		{-7, 26, -2},
		{-15, 26, 31},
		{-17, 26, 19},
		{47, 25, 33},
		{36, 26, 45},
		{48, 27, 45},
		{45, 27, -13},
		{-38, 26, 21},
		{42, 26, 27},
		{29, 27, -7},
		{22, 26, -15},
		{-7, 27, -26},
		{-2, 26, -6},
		{43, 27, -21},
		{10, 26, -11},
		{17, 26, 49},
		{19, 26, -17},
		{-35, 27, 35},
		{25, 27, 5},
		{-37, 24, 46},
		{-24, 26, 49},
		{-7, 26, 48},
		{-14, 27, -24},
		{-18, 27, 44},
		{-1, 26, -23},
		{41, 25, -37},
		{19, 26, -38},
		{-7, 27, 27},
		{42, 26, 19},
		{-33, 27, 31},
		{6, 27, 25},
		{-2, 26, -17},
		{-15, 27, 5},
		{-20, 27, -12},
		{-25, 26, 30},
		{28, 27, -35},
		{-19, 27, -22},
		{4, 26, -15},
		{36, 26, 17}
	};
	private static final float TAG_H = 14f;
	private static final float PAD_X = 8f;

	private static final List<BlockPos> PREDICTIONS = new ArrayList<>();

	private static BlockPos base;
	private static BlockPos ignore;
	private static double lastX;
	private static double lastY;
	private static double lastZ;
	private static boolean hadLastPos;
	private static long lastBaseScanMs;
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
		if (!PREDICTIONS.isEmpty()) {
			ignore = PREDICTIONS.getFirst();
		}
		PREDICTIONS.clear();
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
		PREDICTIONS.clear();
		base = null;
		ignore = null;
		hadLastPos = false;
		lastBaseScanMs = 0L;
		allToolsBeeped = false;
		inDivan = false;
		lastTreasureMs = 0L;
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
		if (base == null) {
			base = findBase(client.level, player);
		}
		if (base == null) {
			return;
		}
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		if (!hadLastPos || lastX != x || lastY != y || lastZ != z) {
			lastX = x;
			lastY = y;
			lastZ = z;
			hadLastPos = true;
			return;
		}
		boolean wasEmpty = PREDICTIONS.isEmpty();
		PREDICTIONS.clear();
		for (int[] offset : CHESTS) {
			BlockPos chest = new BlockPos(
				base.getX() - offset[0],
				base.getY() - offset[1],
				base.getZ() - offset[2]
			);
			if (ignore != null && ignore.equals(chest)) {
				ignore = null;
				continue;
			}
			double dx = x - chest.getX();
			double dy = y - (chest.getY() + 1);
			double dz = z - chest.getZ();
			if (round1(Math.sqrt(dx * dx + dy * dy + dz * dz)) == distance) {
				PREDICTIONS.add(chest);
			}
		}
		if (wasEmpty && !PREDICTIONS.isEmpty()) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 1.0f));
		}
	}

	private static BlockPos findBase(ClientLevel level, LocalPlayer player) {
		if (System.currentTimeMillis() - lastBaseScanMs < 15_000L) {
			return null;
		}
		int x = (int) player.getX();
		int y = (int) player.getY();
		int z = (int) player.getZ();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = x - 50; i < x + 50; i++) {
			for (int j = y + 30; j >= y - 30; j--) {
				for (int k = z - 50; k < z + 50; k++) {
					if (level.getBlockState(cursor.set(i, j, k)).is(Blocks.QUARTZ_STAIRS)
						&& level.getBlockState(cursor.set(i, j + 13, k)).is(Blocks.BARRIER)) {
						return walkBarrier(level, i, j + 13, k);
					}
				}
			}
		}
		lastBaseScanMs = System.currentTimeMillis();
		return null;
	}

	private static BlockPos walkBarrier(ClientLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
		if (!level.getBlockState(cursor).is(Blocks.BARRIER)) {
			return cursor.immutable();
		}
		boolean moving = true;
		while (moving) {
			moving = false;
			if (level.getBlockState(cursor.set(x + 1, y, z)).is(Blocks.BARRIER)) {
				x++;
				moving = true;
			}
			if (level.getBlockState(cursor.set(x, y - 1, z)).is(Blocks.BARRIER)) {
				y--;
				moving = true;
			}
			if (level.getBlockState(cursor.set(x, y, z + 1)).is(Blocks.BARRIER)) {
				z++;
				moving = true;
			}
		}
		return new BlockPos(x, y, z);
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
}
