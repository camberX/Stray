package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
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
 * Mines of Divan metal-detector solver from SkyHanni: quartz-stair + barrier
 * origin, chest offsets from SkyHanni-REPO MetalDetectorChests.json, match the
 * action-bar TREASURE distance while standing still.
 */
public final class MetalDetector {
	private static final Pattern TREASURE = Pattern.compile("TREASURE:\\s*([\\d.,]+)m");
	private static final Pattern FOUND = Pattern.compile("You found .*with your .*Metal Detector");
	private static final Pattern TOOL = Pattern.compile("Scavenged ([A-Za-z ]+?)(?:\\s+with your|$)");
	private static final String DETECTOR = "DWARVEN_METAL_DETECTOR";
	private static final String[] TOOLS = {
		"DWARVEN_LAPIS_SWORD",
		"DWARVEN_DIAMOND_AXE",
		"DWARVEN_EMERALD_HAMMER",
		"DWARVEN_GOLD_HAMMER"
	};
	private static final BlockPos[] OFFSETS = offsets(
		"-7:38:-2",
		"-15:38:31",
		"-17:38:19",
		"47:37:33",
		"36:38:45",
		"48:39:45",
		"45:39:-13",
		"-38:38:21",
		"42:38:27",
		"29:39:-7",
		"22:38:-15",
		"-7:39:-26",
		"-2:38:-6",
		"43:39:-21",
		"10:38:-11",
		"17:38:49",
		"19:38:-17",
		"-35:39:35",
		"25:39:5",
		"-37:36:46",
		"-24:38:49",
		"-7:38:48",
		"-14:39:-24",
		"-18:39:44",
		"-1:38:-23",
		"41:37:-37",
		"19:38:-38",
		"-7:39:27",
		"42:38:19",
		"-33:39:31",
		"6:39:25",
		"-2:38:-17",
		"-15:39:5",
		"-20:39:-12",
		"-25:38:30",
		"28:39:-35",
		"-19:39:-22",
		"4:38:-15",
		"36:38:17"
	);
	private static final float TAG_H = 14f;
	private static final float PAD_X = 8f;
	private static final List<BlockPos> PREDICTIONS = new ArrayList<>();
	private static BlockPos origin;
	private static BlockPos ignore;
	private static Vec3 lastFeet;
	private static long nextOriginScan;
	private static boolean plinged;
	private static boolean allToolsBeeped;
	private static boolean inDivan;

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
		boolean here = SkyblockLocation.inMinesOfDivan();
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
			trimPredictions(client.player.position());
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

	public static void onMessage(Component message, boolean overlay) {
		if (message == null) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.metalDetectorSolver && !config.metalDetectorToolTitle) {
			return;
		}
		if (!SkyblockLocation.inMinesOfDivan()) {
			return;
		}
		String text = message.getString().replace('\u00a7', ' ');
		if (overlay) {
			if (config.metalDetectorSolver) {
				readTreasure(text);
			}
			return;
		}
		if (!FOUND.matcher(text).find()) {
			return;
		}
		PREDICTIONS.clear();
		plinged = false;
		if (config.metalDetectorToolTitle) {
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
		origin = null;
		ignore = null;
		lastFeet = null;
		nextOriginScan = 0L;
		plinged = false;
		allToolsBeeped = false;
		inDivan = false;
	}

	public static boolean active() {
		return StrayConfig.get().metalDetectorSolver && SkyblockLocation.inMinesOfDivan() && !PREDICTIONS.isEmpty();
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
			Component name = MenuFont.vanilla("Treasure");
			Component meters = MenuFont.vanilla(GuiDraw.meters(dist));
			float nameW = font.width(name);
			float distW = font.width(meters);
			float inner = nameW + 5f + distW;
			float w = inner + PAD_X * 2f;
			int rgb = PREDICTIONS.size() == 1 ? 0x55FF55 : 0xFFAA00;
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

	private static void readTreasure(String text) {
		Matcher matcher = TREASURE.matcher(text);
		if (!matcher.find()) {
			return;
		}
		if (PREDICTIONS.size() == 1) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			return;
		}
		double distance;
		try {
			distance = Double.parseDouble(matcher.group(1).replace(',', '.'));
		} catch (NumberFormatException ignored) {
			return;
		}
		Vec3 feet = player.position();
		if (lastFeet == null || lastFeet.distanceToSqr(feet) > 1.0E-4) {
			lastFeet = feet;
			plinged = false;
		}
		if (origin == null) {
			findOrigin(level, player.blockPosition());
		}
		if (origin == null) {
			return;
		}
		PREDICTIONS.clear();
		for (BlockPos offset : OFFSETS) {
			BlockPos loc = origin.offset(-offset.getX(), -offset.getY(), -offset.getZ());
			if (ignore != null && ignore.equals(loc)) {
				ignore = null;
				return;
			}
			double measured = Vec3.atLowerCornerOf(loc).add(0, 1, 0).distanceTo(feet);
			if (round1(measured) == distance) {
				PREDICTIONS.add(loc);
			}
		}
		if (!PREDICTIONS.isEmpty() && !plinged) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f));
			plinged = true;
		}
	}

	private static void trimPredictions(Vec3 feet) {
		if (PREDICTIONS.size() == 1) {
			BlockPos only = PREDICTIONS.getFirst();
			if (only.distToCenterSqr(feet) <= 25.0) {
				ignore = only;
				PREDICTIONS.clear();
			}
			return;
		}
		if (PREDICTIONS.isEmpty() && ignore != null && ignore.distToCenterSqr(feet) > 100.0) {
			ignore = null;
		}
	}

	private static void findOrigin(ClientLevel level, BlockPos around) {
		long now = System.currentTimeMillis();
		if (now < nextOriginScan) {
			return;
		}
		nextOriginScan = now + 15_000L;
		for (int x = -50; x < 50; x++) {
			for (int y = 30; y >= -30; y--) {
				for (int z = -50; z < 50; z++) {
					BlockPos stairs = around.offset(x, y, z);
					if (!level.getBlockState(stairs).is(Blocks.QUARTZ_STAIRS)) {
						continue;
					}
					BlockPos barrier = stairs.above(13);
					if (!level.getBlockState(barrier).is(Blocks.BARRIER)) {
						continue;
					}
					origin = walkBarrier(level, barrier);
					return;
				}
			}
		}
	}

	private static BlockPos walkBarrier(ClientLevel level, BlockPos start) {
		BlockPos current = start;
		boolean changed = true;
		while (changed) {
			changed = false;
			BlockPos east = current.offset(1, 0, 0);
			if (level.getBlockState(east).is(Blocks.BARRIER)) {
				current = east;
				changed = true;
			}
			BlockPos up = current.offset(0, 1, 0);
			if (level.getBlockState(up).is(Blocks.BARRIER)) {
				current = up;
				changed = true;
			}
			BlockPos south = current.offset(0, 0, 1);
			if (level.getBlockState(south).is(Blocks.BARRIER)) {
				current = south;
				changed = true;
			}
		}
		return current;
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

	private static BlockPos[] offsets(String... raw) {
		BlockPos[] out = new BlockPos[raw.length];
		for (int i = 0; i < raw.length; i++) {
			String[] parts = raw[i].split(":");
			out[i] = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		}
		return out;
	}
}
