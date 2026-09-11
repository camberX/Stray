package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.Anim;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Middle-click a block to mark it. Marks draw through walls with a tracer and
 * a distance tag; middle-click the same block again to drop it. Cleared on
 * world change.
 */
public final class BlockMarks {
	private static final float TAG_H = 14f;
	private static final float PAD_X = 8f;
	private static final int MAX = 64;
	private static final Set<BlockPos> MARKS = new LinkedHashSet<>();

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
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = block.getBlockPos().immutable();
		if (client.level.getBlockState(pos).isAir()) {
			return false;
		}
		boolean added;
		if (MARKS.remove(pos)) {
			added = false;
		} else {
			if (MARKS.size() >= MAX) {
				BlockPos oldest = MARKS.iterator().next();
				MARKS.remove(oldest);
			}
			MARKS.add(pos);
			added = true;
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(
			SoundEvents.NOTE_BLOCK_HAT.value(),
			added ? 1.4f : 0.8f,
			0.6f
		));
		return true;
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
		for (BlockPos pos : new ArrayList<>(MARKS)) {
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
			Component meters = MenuFont.vanilla(GuiDraw.meters(dist));
			float distW = font.width(meters);
			float w = distW + PAD_X * 2f;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			if (scale != 1.0f) {
				graphics.pose().scale(scale, scale);
			}
			float left = -w * 0.5f;
			float top = -2f - TAG_H;
			GuiDraw.panel(graphics, left, top, w, TAG_H, 5, Theme.WINDOW, Theme.LINE);
			GuiDraw.text(graphics, font, meters, left + PAD_X, GuiDraw.middle(top, TAG_H), Anim.fade(0xFF000000 | rgb, 1f), false);
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
		Vec3 eyes = client.player == null ? null : client.player.getEyePosition();
		for (BlockPos pos : MARKS) {
			GizmoProperties cuboid = Gizmos.cuboid(new AABB(pos).inflate(0.01), GizmoStyle.strokeAndFill(line, 2.2f, fill));
			cuboid.setAlwaysOnTop();
			if (config.blockMarksTracers && eyes != null) {
				GizmoProperties tracer = Gizmos.line(eyes, Vec3.atCenterOf(pos), 0xB0000000 | rgb, 1.8f);
				tracer.setAlwaysOnTop();
			}
		}
	}

	public static List<BlockPos> marks() {
		return List.copyOf(MARKS);
	}
}
