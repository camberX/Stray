package dev.stray.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.StrayClient;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.ui.Anim;
import dev.stray.client.ui.BlockMarkEditScreen;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Middle-click a block to mark it. Marks draw through walls with a tracer and
 * a tag; look at one and press the edit key (Enter) to give it a name and an
 * item icon. Middle-click again to drop it. Cleared on world change.
 */
public final class BlockMarks {
	private static final float TAG_H = 14f;
	private static final float PAD_X = 8f;
	private static final int MAX = 64;
	private static final double HOVER_RANGE = 96.0;
	private static final Map<BlockPos, Mark> MARKS = new LinkedHashMap<>();
	private static boolean editWasDown;
	private static BlockPos hovered;

	private BlockMarks() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emitBoxes());
	}

	/** Returns true when the click was consumed as a mark toggle. */
	public static boolean onPick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.blockMarksEnabled || client == null || client.player == null || client.level == null) {
			return false;
		}
		if (client.screen != null) {
			return false;
		}
		HitResult hit = client.hitResult;
		BlockPos pos = null;
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			pos = block.getBlockPos().immutable();
			if (client.level.getBlockState(pos).isAir()) {
				pos = null;
			}
		}
		if (pos == null) {
			pos = hovered;
		}
		if (pos == null) {
			return false;
		}
		boolean added;
		if (MARKS.remove(pos) != null) {
			added = false;
		} else {
			if (MARKS.size() >= MAX) {
				BlockPos oldest = MARKS.keySet().iterator().next();
				MARKS.remove(oldest);
			}
			MARKS.put(pos, new Mark(pos, "", "", ItemStack.EMPTY));
			added = true;
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(
			SoundEvents.NOTE_BLOCK_HAT.value(),
			added ? 1.4f : 0.8f,
			0.6f
		));
		return true;
	}

	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.blockMarksEnabled || client == null || client.player == null || client.level == null || MARKS.isEmpty()) {
			hovered = null;
			editWasDown = false;
			return;
		}
		hovered = client.screen == null ? findHovered(client) : null;
		boolean down = StrayClient.strayHotkeys(client) && OdinClicks.isPressed(OdinClicks.parseKey(config.blockMarkEditKey));
		if (down && !editWasDown && hovered != null) {
			Mark mark = MARKS.get(hovered);
			if (mark != null) {
				client.setScreen(new BlockMarkEditScreen(mark.pos, mark.name, mark.icon));
			}
		}
		editWasDown = down;
	}

	public static void rename(BlockPos pos, String name, String icon) {
		Mark prior = MARKS.get(pos);
		if (prior == null) {
			return;
		}
		String cleanName = name == null ? "" : name.trim();
		String cleanIcon = icon == null ? "" : icon.trim();
		ItemStack stack = ItemStack.EMPTY;
		if (!cleanIcon.isEmpty()) {
			ItemIds.Preview preview = ItemIds.resolve(cleanIcon);
			if (preview.kind() == ItemIds.Kind.VANILLA || preview.kind() == ItemIds.Kind.SKYBLOCK) {
				stack = preview.stack();
				cleanIcon = preview.canonical();
			} else {
				cleanIcon = "";
			}
		}
		MARKS.put(pos, new Mark(pos, cleanName, cleanIcon, stack));
	}

	public static void remove(BlockPos pos) {
		MARKS.remove(pos);
	}

	public static void clear() {
		MARKS.clear();
	}

	public static int count() {
		return MARKS.size();
	}

	public static boolean active() {
		return StrayConfig.get().blockMarksEnabled && !MARKS.isEmpty();
	}

	public static void onWorldChange() {
		MARKS.clear();
		hovered = null;
	}

	public static List<BlockPos> marks() {
		return List.copyOf(MARKS.keySet());
	}

	private static BlockPos findHovered(Minecraft client) {
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return null;
		}
		Vec3 origin = camera.position();
		Vector3fc f = camera.forwardVector();
		Vec3 dir = new Vec3(f.x(), f.y(), f.z());
		Vec3 end = origin.add(dir.scale(HOVER_RANGE));
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : MARKS.keySet()) {
			AABB box = new AABB(pos).inflate(0.12);
			Optional<Vec3> clip = box.clip(origin, end);
			if (clip.isEmpty()) {
				continue;
			}
			double dist = clip.get().distanceToSqr(origin);
			if (dist < bestDist) {
				bestDist = dist;
				best = pos;
			}
		}
		return best;
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
		int rgb = StrayConfig.get().blockMarksRgb & 0xFFFFFF;
		for (Mark mark : new ArrayList<>(MARKS.values())) {
			BlockPos pos = mark.pos;
			boolean hot = pos.equals(hovered);
			Vec3 head = Vec3.atCenterOf(pos).add(0, 1.1, 0);
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
			boolean icon = !mark.stack.isEmpty();
			String label = mark.name.isEmpty() ? (hot ? "Enter to name" : "") : mark.name;
			Component name = MenuFont.vanilla(label);
			Component meters = MenuFont.vanilla(GuiDraw.meters(dist));
			float iconW = icon ? 12f : 0f;
			float nameW = label.isEmpty() ? 0f : font.width(name) + 5f;
			float distW = font.width(meters);
			float w = PAD_X * 2f + iconW + (icon ? 4f : 0f) + nameW + distW;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			if (scale != 1.0f) {
				graphics.pose().scale(scale, scale);
			}
			float left = -w * 0.5f;
			float top = -2f - TAG_H;
			GuiDraw.panel(graphics, left, top, w, TAG_H, 5, Theme.WINDOW, hot ? Theme.ACCENT : Theme.LINE);
			float cx = left + PAD_X;
			if (icon) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(cx, top + 1f);
				graphics.pose().scale(0.75f, 0.75f);
				graphics.item(mark.stack, 0, 0);
				graphics.pose().popMatrix();
				cx += iconW + 4f;
			}
			if (!label.isEmpty()) {
				int nameColor = mark.name.isEmpty() ? Theme.MUTED : 0xFF000000 | rgb;
				GuiDraw.text(graphics, font, name, cx, GuiDraw.middle(top, TAG_H), nameColor, false);
				cx += nameW;
			}
			GuiDraw.text(graphics, font, meters, cx, GuiDraw.middle(top, TAG_H), Anim.fade(mark.name.isEmpty() ? (0xFF000000 | rgb) : Theme.MUTED, 1f), false);
			graphics.pose().popMatrix();
		}
	}

	private static void emitBoxes() {
		if (!active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		StrayConfig config = StrayConfig.get();
		int rgb = config.blockMarksRgb & 0xFFFFFF;
		int line = 0xEB000000 | rgb;
		int fill = 0x48000000 | rgb;
		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 start = null;
		if (config.blockMarksTracers && camera.isInitialized()) {
			// Start a little in front of and below the camera so the line is
			// never clipped by the near plane or hidden behind the crosshair.
			Vector3fc f = camera.forwardVector();
			Vector3f up = new Vector3f(camera.upVector());
			Vec3 cam = camera.position();
			start = cam.add(f.x() * 0.9, f.y() * 0.9, f.z() * 0.9).subtract(up.x * 0.28, up.y * 0.28, up.z * 0.28);
		}
		for (Mark mark : MARKS.values()) {
			BlockPos pos = mark.pos;
			boolean hot = pos.equals(hovered);
			GizmoProperties cuboid = Gizmos.cuboid(new AABB(pos).inflate(hot ? 0.04 : 0.01), GizmoStyle.strokeAndFill(hot ? 0xFFFFFFFF : line, hot ? 3.0f : 2.2f, fill));
			cuboid.setAlwaysOnTop();
			if (start != null) {
				GizmoProperties tracer = Gizmos.line(start, Vec3.atCenterOf(pos), 0xB0000000 | rgb, 1.8f);
				tracer.setAlwaysOnTop();
			}
		}
	}

	private record Mark(BlockPos pos, String name, String icon, ItemStack stack) {
	}
}
