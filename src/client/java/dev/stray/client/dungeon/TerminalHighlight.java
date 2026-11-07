package dev.stray.client.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.render.TopDownCapture;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.ShadowLook;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F7 phase 3: a filled glow on the command block of every terminal in the current section
 * that is not done yet, with an optional Shadow tag showing its number in that section.
 *
 * <p>Terminal spots and section order follow NoammAddons' Terminal ESP (CC0); the section
 * tracking follows Odin's Terminals Status (BSD-3): Goldor's first line starts section 1, a
 * section ends once all its tasks are done and its gate is down (section 4 has no gate), and
 * the Core opening ends phase 3. A terminal counts as done once its armor stand no longer
 * reads "Inactive Terminal". Visual only: nothing is clicked or sent. The full build glows
 * and fills through walls; the legit build shows nothing through walls.
 */
public final class TerminalHighlight {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png");
	private static final Pattern TASK = Pattern.compile("^(.{1,16}) (?:activated|completed) a (?:terminal|lever|device)! \\((\\d)/(\\d)\\)$");
	private static final String GOLDOR = "[BOSS] Goldor: Who dares trespass into my domain?";
	private static final String GATE = "The gate has been destroyed!";
	private static final String CORE = "The Core entrance is opening!";
	private static final int UNKNOWN = 0;
	private static final int DONE = 5;
	private static final float TAG = 15f;

	/** Terminal stand spots per section, in the usual numbering (1 first). */
	private static final int[][][] SPOTS = {
		{{110, 113, 73}, {110, 119, 79}, {90, 112, 92}, {90, 122, 101}},
		{{68, 109, 122}, {59, 119, 123}, {47, 109, 122}, {39, 108, 142}, {40, 124, 123}},
		{{-2, 109, 112}, {-2, 119, 93}, {18, 123, 93}, {-2, 109, 77}},
		{{41, 109, 30}, {44, 121, 30}, {67, 109, 30}, {72, 114, 47}}
	};

	/** Command block found next to each spot, filled in once that chunk is there. */
	private static final BlockPos[][] BLOCKS = new BlockPos[SPOTS.length][];

	static {
		for (int s = 0; s < SPOTS.length; s++) {
			BLOCKS[s] = new BlockPos[SPOTS[s].length];
		}
	}

	private static volatile boolean glowThisFrame;
	private static int section = UNKNOWN;
	private static boolean tasksDone;
	private static boolean gate;
	private static ClientLevel lastLevel;
	private static List<Target> targets = List.of();

	private record Target(BlockPos block, int number) {
	}

	/** One Hypixel terminal/device label (two stacked stands), drawn as one Shadow tag. */
	private record StandTag(List<ArmorStand> stands, Component[] lines, int color) {
	}

	private static final java.util.Set<String> PRIMARY = java.util.Set.of("Inactive Terminal", "Terminal Active", "Inactive", "Active");
	private static final java.util.Set<String> DONE_NAMES = java.util.Set.of("Terminal Active", "Active");
	private static List<StandTag> standTags = List.of();
	/** Second-line names seen next to a terminal or device ("CLICK HERE", ...), hidden by name too. */
	private static final java.util.Set<String> PARTNERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** P3 sections as boxes (min x, y, z, max x, y, z), from NoammAddons' LocationUtils. */
	private static final double[][] SECTION_BOXES = {
		{90, 105, 32, 111, 158, 123},
		{16, 105, 122, 111, 158, 143},
		{-3, 106, 48, 19, 158, 142},
		{-3, 106, 30, 91, 158, 50}
	};
	private static it.unimi.dsi.fastutil.ints.IntSet hiddenStands = it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET;

	/** /stray debug dev highlight: four stand-in terminals in front of the player. */
	private static BlockPos[] preview;
	private static boolean[] previewDone;

	private TerminalHighlight() {
	}

	public static void init() {
		LevelRenderEvents.END_EXTRACTION.register(context -> {
			// The glow runs the full-screen outline post pass, by far the most expensive part of
			// this feature, so it only runs while a highlighted terminal is in front of the camera.
			glowThisFrame = !dev.stray.Edition.LEGIT && !targets.isEmpty() && !TopDownCapture.offscreen() && anyInView(context.camera());
			if (glowThisFrame) {
				context.levelState().shouldShowEntityOutlines = true;
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			// The glow pass draws through walls, so the legit build leaves it out.
			// Anything drawn into the outline buffer brings the post pass with it.
			if (!glowThisFrame || targets.isEmpty() || TopDownCapture.offscreen() || context.poseStack() == null) {
				return;
			}
			Vec3 camera = context.levelState().cameraRenderState.pos;
			int color = glowColor();
			List<Target> list = targets;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.outline(WHITE), (pose, consumer) -> {
				for (Target target : list) {
					BlockPos pos = target.block;
					float x = (float) (pos.getX() - camera.x);
					float y = (float) (pos.getY() - camera.y);
					float z = (float) (pos.getZ() - camera.z);
					box(consumer, pose, color, x, y, z, x + 1f, y + 1f, z + 1f);
				}
			});
		});
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			if (targets.isEmpty()) {
				return;
			}
			int fill = fillColor();
			if (dev.stray.Edition.LEGIT) {
				// Legit: fill and a soft edge, both hidden behind walls.
				GizmoStyle style = GizmoStyle.strokeAndFill(glowColor() | 0xFF000000, 2.0f, fill);
				for (Target target : targets) {
					Gizmos.cuboid(new AABB(target.block).inflate(0.002), style);
				}
				return;
			}
			for (Target target : targets) {
				Gizmos.cuboid(new AABB(target.block).inflate(0.002), GizmoStyle.fill(fill)).setAlwaysOnTop();
			}
		});
	}

	/** Whether any highlighted block is roughly on screen (a wide cone around the view direction). */
	private static boolean anyInView(Camera camera) {
		if (camera == null || !camera.isInitialized()) {
			return true;
		}
		Vec3 eye = camera.position();
		Vector3fc forward = camera.forwardVector();
		for (Target target : targets) {
			double dx = target.block.getX() + 0.5 - eye.x;
			double dy = target.block.getY() + 0.5 - eye.y;
			double dz = target.block.getZ() + 0.5 - eye.z;
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 3.0 || (dx * forward.x() + dy * forward.y() + dz * forward.z()) / len > 0.2) {
				return true;
			}
		}
		return false;
	}

	private static boolean active() {
		return StrayConfig.get().terminalHighlight
			&& (DevMode.on() || SkyblockLocation.inDungeon && SkyblockLocation.inBoss && SkyblockLocation.dungeonFloor == 7);
	}

	// ------------------------------------------------------------------ state

	/** Toggles the preview terminals; returns whether it is on now. */
	public static boolean togglePreview(Minecraft client) {
		if (preview != null || client.player == null) {
			preview = null;
			return false;
		}
		net.minecraft.core.Direction facing = client.player.getDirection();
		net.minecraft.core.Direction side = facing.getClockWise();
		BlockPos base = client.player.blockPosition().relative(facing, 5).relative(side, -5);
		preview = new BlockPos[4];
		previewDone = new boolean[4];
		for (int i = 0; i < 4; i++) {
			preview[i] = base.relative(side, i * 3 + 1).above(i % 2);
		}
		return true;
	}

	public static boolean previewActive() {
		return preview != null;
	}

	/** Marks preview terminal n (1-4) done, or not done again. Returns false without a preview. */
	public static boolean togglePreviewDone(int n) {
		if (preview == null || n < 1 || n > preview.length) {
			return false;
		}
		previewDone[n - 1] = !previewDone[n - 1];
		return true;
	}

	/** Sets the current section (1-4, 0 unknown, 5 done) by hand, for testing on your own server. */
	public static void setSection(int value) {
		section = Math.max(UNKNOWN, Math.min(DONE, value));
		tasksDone = false;
		gate = false;
	}

	public static int section() {
		return section;
	}

	private static void reset() {
		section = UNKNOWN;
		tasksDone = false;
		gate = false;
		targets = List.of();
		for (BlockPos[] row : BLOCKS) {
			java.util.Arrays.fill(row, null);
		}
	}

	private static void nextSection() {
		section = section >= 4 ? DONE : section + 1;
		tasksDone = false;
		gate = false;
	}

	public static void onChat(String plain) {
		if (!SkyblockLocation.inDungeon && !DevMode.on()) {
			return;
		}
		switch (plain) {
			case GOLDOR -> {
				section = 1;
				tasksDone = false;
				gate = false;
				return;
			}
			case GATE -> {
				gate = true;
				if (tasksDone && section >= 1 && section <= 4) {
					nextSection();
				}
				return;
			}
			case CORE -> {
				section = DONE;
				return;
			}
			default -> {
			}
		}
		Matcher m = TASK.matcher(plain);
		if (m.matches() && section >= 1 && section <= 4 && m.group(2).equals(m.group(3))) {
			tasksDone = true;
			if (gate || section == 4) {
				nextSection();
			}
		}
	}

	public static void tick(Minecraft client) {
		// Only a new world resets: the scoreboard's boss flag can flicker mid-fight.
		if (client.level != lastLevel) {
			reset();
			preview = null;
			lastLevel = client.level;
		}
		List<ArmorStand> stands = namedStands(client);
		int current = currentSection(client);
		tickTags(stands, current);
		if (preview != null) {
			List<Target> shown = new ArrayList<>();
			if (StrayConfig.get().terminalHighlight) {
				for (int i = 0; i < preview.length; i++) {
					if (!previewDone[i]) {
						shown.add(new Target(preview[i], i + 1));
					}
				}
			}
			targets = shown;
			return;
		}
		if (!active() || client.level == null || client.player == null || current == DONE) {
			targets = List.of();
			return;
		}
		List<Target> found = new ArrayList<>();
		for (int s = 0; s < SPOTS.length; s++) {
			if (current != UNKNOWN && current != s + 1) {
				continue;
			}
			for (int i = 0; i < SPOTS[s].length; i++) {
				int[] spot = SPOTS[s][i];
				Vec3 at = new Vec3(spot[0] + 0.5, spot[1], spot[2] + 0.5);
				String name = standName(stands, at);
				boolean inactive = name != null && name.contains("Inactive Terminal");
				// With the section known a terminal whose stand is not loaded yet still shows;
				// without it only terminals the server marks inactive do.
				boolean show = inactive || current != UNKNOWN && name == null;
				if (!show) {
					continue;
				}
				BlockPos block = commandBlock(client.level, s, i, spot);
				if (block != null) {
					found.add(new Target(block, i + 1));
				}
			}
		}
		targets = found;
	}

	/** Named armor stands, gathered once per tick for both the glow and the labels. */
	private static List<ArmorStand> namedStands(Minecraft client) {
		if (client.level == null || !StrayConfig.get().terminalHighlight || !active()) {
			return List.of();
		}
		List<ArmorStand> stands = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof ArmorStand stand && entity.hasCustomName()) {
				stands.add(stand);
			}
		}
		return stands;
	}

	/**
	 * The section to show: the one tracked from chat, else the one you stand in (as NoammAddons
	 * does), else unknown.
	 */
	private static int currentSection(Minecraft client) {
		if (section != UNKNOWN || client.player == null) {
			return section;
		}
		return sectionAt(client.player.getX(), client.player.getY(), client.player.getZ());
	}

	/** Per-stand answer for {@link #hidesStand}, kept until the stand's name object changes. */
	private record NameCheck(Component name, boolean hide) {
	}

	private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<NameCheck> NAME_CHECKS = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();

	/** Whether this armor stand's own name is hidden because a Shadow tag replaces it. Called every frame per entity. */
	public static boolean hidesStand(Entity stand) {
		if (hiddenStands.contains(stand.getId())) {
			return true;
		}
		// Also by name, so a stand Hypixel just (re)spawned is hidden before the next tick groups it.
		if (!labelsOn || !stand.hasCustomName()) {
			return false;
		}
		Component name = stand.getCustomName();
		NameCheck check = NAME_CHECKS.get(stand.getId());
		if (check == null || check.name != name) {
			String text = net.minecraft.util.StringUtil.stripColor(name.getString()).trim();
			check = new NameCheck(name, PRIMARY.contains(text) || PARTNERS.contains(text));
			if (NAME_CHECKS.size() > 2048) {
				NAME_CHECKS.clear();
			}
			NAME_CHECKS.put(stand.getId(), check);
		}
		return check.hide;
	}

	private static volatile boolean labelsOn;

	/** The P3 section (1-4) a spot is in, or 0 outside all of them. */
	private static int sectionAt(double x, double y, double z) {
		for (int i = 0; i < SECTION_BOXES.length; i++) {
			double[] b = SECTION_BOXES[i];
			if (x >= b[0] && x <= b[3] + 1 && y >= b[1] && y <= b[4] + 1 && z >= b[2] && z <= b[5] + 1) {
				return i + 1;
			}
		}
		return 0;
	}

	private static void tickTags(List<ArmorStand> named, int current) {
		StrayConfig config = StrayConfig.get();
		labelsOn = config.terminalHighlight && config.terminalHighlightTags && active();
		if (!labelsOn || named.isEmpty()) {
			standTags = List.of();
			hiddenStands = it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET;
			return;
		}
		// Names once per stand, not once per pair.
		int n = named.size();
		String[] names = new String[n];
		for (int i = 0; i < n; i++) {
			names[i] = plain(named.get(i));
		}
		it.unimi.dsi.fastutil.ints.IntSet hidden = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
		List<StandTag> tags = new ArrayList<>();
		int rgb = 0xFF000000 | (config.terminalHighlightRgb & 0xFFFFFF);
		for (int a = 0; a < n; a++) {
			ArmorStand stand = named.get(a);
			String name = names[a];
			if (!PRIMARY.contains(name) || hidden.contains(stand.getId())) {
				continue;
			}
			// Hypixel's second line ("CLICK HERE" or the device line) is a stand right above or below.
			List<ArmorStand> group = new ArrayList<>(2);
			List<String> groupNames = new ArrayList<>(2);
			group.add(stand);
			groupNames.add(name);
			for (int b = 0; b < n; b++) {
				ArmorStand other = named.get(b);
				if (b == a) {
					continue;
				}
				double dx = other.getX() - stand.getX();
				double dz = other.getZ() - stand.getZ();
				if (dx * dx + dz * dz >= 0.09 || Math.abs(other.getY() - stand.getY()) > 1.2
					|| PRIMARY.contains(names[b]) || hidden.contains(other.getId())) {
					continue;
				}
				group.add(other);
				groupNames.add(names[b]);
			}
			for (int i = 0; i < group.size(); i++) {
				hidden.add(group.get(i).getId());
				if (i > 0) {
					PARTNERS.add(groupNames.get(i));
				}
			}
			if (DONE_NAMES.contains(name) || current == DONE) {
				continue;
			}
			// Only the current section: labels elsewhere stay hidden.
			int at = sectionAt(stand.getX(), stand.getY(), stand.getZ());
			if (current >= 1 && current <= 4 && at != 0 && at != current) {
				continue;
			}
			// Top line first.
			Integer[] order = new Integer[group.size()];
			for (int i = 0; i < order.length; i++) {
				order[i] = i;
			}
			java.util.Arrays.sort(order, (x, y) -> Double.compare(group.get(y).getY(), group.get(x).getY()));
			Component[] lines = new Component[order.length];
			ArmorStand[] sorted = new ArmorStand[order.length];
			for (int i = 0; i < order.length; i++) {
				String text = groupNames.get(order[i]);
				Component line = MenuFont.shadow(text);
				// "CLICK HERE" in bold, like Hypixel's.
				lines[i] = text.equalsIgnoreCase("CLICK HERE") ? line.copy().withStyle(net.minecraft.ChatFormatting.BOLD) : line;
				sorted[i] = group.get(order[i]);
			}
			tags.add(new StandTag(List.of(sorted), lines, rgb));
		}
		standTags = tags;
		hiddenStands = hidden;
	}

	private static float labelScale() {
		return Math.max(50, Math.min(200, StrayConfig.get().terminalLabelScale)) / 100f;
	}

	private static String plain(ArmorStand stand) {
		Component name = stand.getCustomName();
		return name == null ? "" : net.minecraft.util.StringUtil.stripColor(name.getString()).trim();
	}

	/** The Shadow tags that replace Hypixel's terminal and device labels, with more range. */
	private static void extractStandTags(GuiGraphicsExtractor graphics, Minecraft client, Vec3 camPos, Vector3fc forward, float partial) {
		Font font = client.font;
		for (StandTag tag : standTags) {
			ArmorStand top = tag.stands.get(0);
			if (top.isRemoved()) {
				continue;
			}
			// Centred where Hypixel's lines sat: the middle of the stands' name positions.
			double nameY = 0;
			for (ArmorStand stand : tag.stands) {
				Vec3 attach = stand.getAttachments().getNullable(net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, stand.getYRot(partial));
				nameY += stand.getPosition(partial).y + (attach == null ? stand.getBbHeight() : attach.y) + 0.5;
			}
			nameY /= tag.stands.size();
			Vec3 pos = top.getPosition(partial);
			Vec3 head = new Vec3(pos.x, nameY, pos.z);
			Vec3 rel = head.subtract(camPos);
			if (rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z() <= 0.12) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(head);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			// Never smaller than 0.8 so far labels stay readable (vanilla's shrink with distance).
			float scale = Math.max(0.8f, NametagRenderer.distanceScale(head.distanceTo(camPos))) * labelScale();
			float lineH = 10f;
			float w = 0f;
			for (Component line : tag.lines) {
				w = Math.max(w, font.width(line));
			}
			w += 14f;
			float h = tag.lines.length * lineH + 6f;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			if (scale != 1f) {
				graphics.pose().scale(scale, scale);
			}
			float left = -w * 0.5f;
			float topY = -h * 0.5f;
			ShadowLook.panel(graphics, left, topY, w, h, 5f, dev.stray.client.ui.Theme.HUD_WINDOW);
			for (int i = 0; i < tag.lines.length; i++) {
				Component line = tag.lines[i];
				GuiDraw.text(graphics, font, line, -font.width(line) * 0.5f, GuiDraw.middle(topY + 3f + i * lineH, lineH), tag.color, false);
			}
			graphics.pose().popMatrix();
		}
	}

	private static String standName(List<ArmorStand> stands, Vec3 at) {
		String best = null;
		double bestDist = 2.25;
		for (ArmorStand stand : stands) {
			double dist = stand.position().distanceToSqr(at);
			if (dist > bestDist) {
				continue;
			}
			String name = stand.getCustomName() == null ? "" : stand.getCustomName().getString();
			if (name.contains("Terminal")) {
				best = name;
				bestDist = dist;
			}
		}
		return best;
	}

	private static BlockPos commandBlock(ClientLevel level, int s, int i, int[] spot) {
		BlockPos cached = BLOCKS[s][i];
		if (cached != null) {
			return cached;
		}
		BlockPos center = new BlockPos(spot[0], spot[1], spot[2]);
		if (!level.hasChunkAt(center)) {
			return null;
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
			if (!level.getBlockState(pos).is(Blocks.COMMAND_BLOCK)) {
				continue;
			}
			double dist = pos.distSqr(center);
			if (dist < bestDist) {
				bestDist = dist;
				best = pos.immutable();
			}
		}
		BLOCKS[s][i] = best;
		return best;
	}

	// ------------------------------------------------------------------ draw

	private static int glowColor() {
		StrayConfig config = StrayConfig.get();
		float opacity = StrayConfig.clamp(config.terminalHighlightOpacity, 0.15f, 0.90f);
		return (Math.round(opacity * 255f) << 24) | (config.terminalHighlightRgb & 0xFFFFFF);
	}

	private static int fillColor() {
		StrayConfig config = StrayConfig.get();
		float opacity = StrayConfig.clamp(config.terminalHighlightOpacity, 0.15f, 0.90f) * 0.55f;
		return (Math.round(opacity * 255f) << 24) | (config.terminalHighlightRgb & 0xFFFFFF);
	}

	/** Number tags over the highlighted terminals, drawn with the HUD. */
	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		boolean numbers = !targets.isEmpty() && StrayConfig.get().terminalHighlightNumbers;
		if (!numbers && standTags.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden()) {
			return;
		}
		Camera camera = client.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		if (!standTags.isEmpty()) {
			extractStandTags(graphics, client, camPos, forward, delta.getGameTimeDeltaPartialTick(true));
		}
		if (!numbers) {
			return;
		}
		Font font = client.font;
		int accent = 0xFF000000 | (StrayConfig.get().terminalHighlightRgb & 0xFFFFFF);
		for (Target target : targets) {
			Vec3 head = Vec3.atCenterOf(target.block).add(0, 1.0, 0);
			Vec3 rel = head.subtract(camPos);
			if (rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z() <= 0.12) {
				continue;
			}
			if (dev.stray.Edition.LEGIT && hidden(client, camPos, target.block)) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(head);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			float scale = NametagRenderer.distanceScale(head.distanceTo(camPos)) * labelScale();
			Component label = MenuFont.shadow(String.valueOf(target.number));
			float w = Math.max(TAG, font.width(label) + 10f);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			if (scale != 1f) {
				graphics.pose().scale(scale, scale);
			}
			float left = -w * 0.5f;
			float top = -TAG * 0.5f;
			ShadowLook.panel(graphics, left, top, w, TAG, 5f, dev.stray.client.ui.Theme.HUD_WINDOW);
			GuiDraw.text(graphics, font, label, -font.width(label) * 0.5f, GuiDraw.middle(top, TAG), accent, false);
			graphics.pose().popMatrix();
		}
	}

	/** Legit: the tag hides when a block sits between the camera and the terminal. */
	private static boolean hidden(Minecraft client, Vec3 camPos, BlockPos block) {
		Vec3 target = Vec3.atCenterOf(block);
		net.minecraft.world.phys.BlockHitResult hit = client.level.clip(new net.minecraft.world.level.ClipContext(
			camPos, target, net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, client.player));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && !hit.getBlockPos().equals(block);
	}

	private static void box(VertexConsumer consumer, PoseStack.Pose pose, int color, float x0, float y0, float z0, float x1, float y1, float z1) {
		quad(consumer, pose, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(consumer, pose, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(consumer, pose, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(consumer, pose, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(consumer, pose, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(consumer, pose, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int color,
		float x0, float y0, float z0, float x1, float y1, float z1,
		float x2, float y2, float z2, float x3, float y3, float z3) {
		consumer.addVertex(pose, x0, y0, z0).setUv(0f, 0f).setColor(color);
		consumer.addVertex(pose, x1, y1, z1).setUv(1f, 0f).setColor(color);
		consumer.addVertex(pose, x2, y2, z2).setUv(1f, 1f).setColor(color);
		consumer.addVertex(pose, x3, y3, z3).setUv(0f, 1f).setColor(color);
	}
}
