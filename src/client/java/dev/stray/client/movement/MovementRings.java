package dev.stray.client.movement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.stray.client.StrayClient;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.IslandSaves;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.ClientInputAccessor;
import dev.stray.client.mixin.KeyMappingAccessor;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.ui.Anim;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.mining.SmoothRotate;
import dev.stray.client.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Place a ring with {@code /stray move 2}. Stand in it and press the record
 * key to capture look, WASD, jump/sneak/sprint, clicks, and hotbar. Press
 * again to save. Walking in plays that replay.
 */
public final class MovementRings {
	private static final Path FILE = IslandSaves.DIR.resolve("movement-rings.json");
	private static final int MAX = 32;
	private static final int MAX_FRAMES = 12_000;
	private static final int SEGMENTS = 48;
	private static final int SEGMENTS_FAR = 24;
	private static final double DRAW_RANGE = 96.0;
	private static final double DRAW_RANGE_SQ = DRAW_RANGE * DRAW_RANGE;
	private static final double FAR_SQ = 24.0 * 24.0;
	private static final double[] COS = new double[SEGMENTS + 1];
	private static final double[] SIN = new double[SEGMENTS + 1];
	private static final int F_FORWARD = 1;
	private static final int F_BACK = 1 << 1;
	private static final int F_LEFT = 1 << 2;
	private static final int F_RIGHT = 1 << 3;
	private static final int F_JUMP = 1 << 4;
	private static final int F_SHIFT = 1 << 5;
	private static final int F_SPRINT = 1 << 6;
	private static final int F_ATTACK = 1 << 7;
	private static final int F_USE = 1 << 8;

	static {
		for (int i = 0; i <= SEGMENTS; i++) {
			double angle = (i % SEGMENTS) * (Math.PI * 2.0 / SEGMENTS);
			COS[i] = Math.cos(angle);
			SIN[i] = Math.sin(angle);
		}
	}

	private static final float TAG_H = 14f;
	private static final float PAD_X = 8f;
	private static final Map<String, List<Ring>> SAVED = new LinkedHashMap<>();
	private static boolean loaded;
	private static boolean dirty;
	private static String lastIsland = "";
	private static boolean recordWasDown;
	private static Ring recordingRing;
	private static Ring playingRing;
	private static int playIndex;
	private static boolean playAttack;
	private static boolean playUse;
	private static boolean aiming;
	private static long aimStart;
	private static long aimMs;
	private static float aimFromYaw;
	private static float aimFromPitch;
	private static float aimToYaw;
	private static float aimToPitch;
	private static float lookYaw;
	private static float lookPitch;
	private static long lookNanos;
	private static long playTickNanos;
	private static long lastPlaySyncNanos;
	private static final Component PLAYING_LABEL = MenuFont.body("Playing Recording");
	private static long lastServerTime = Long.MIN_VALUE;
	private static long lastServerNano;
	private static long lastServerDeltaTicks = 1L;
	private static float serverTps = 20f;
	private static float tickBudget;

	private MovementRings() {
	}

	public static void init() {
		load();
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			stopRecording(false);
			stopPlayback();
			save();
		});
	}

	public static boolean enabled() {
		return StrayConfig.get().movementRingsEnabled;
	}

	public static boolean playing() {
		return playingRing != null;
	}

	public static boolean recording() {
		return recordingRing != null;
	}

	public static float serverTps() {
		return serverTps;
	}

	public static int liveFrames() {
		return recordingRing == null ? 0 : recordingRing.frames.size();
	}

	public static int count() {
		return here().size();
	}

	public static List<Ring> rings() {
		return here();
	}

	public static void clear() {
		stopRecording(false);
		stopPlayback();
		String island = IslandSaves.key();
		List<Ring> rings = SAVED.get(island);
		if (rings == null || rings.isEmpty()) {
			return;
		}
		rings.clear();
		SAVED.remove(island);
		touch();
	}

	public static void onWorldChange() {
		stopRecording(false);
		stopPlayback();
		lastIsland = "";
		recordWasDown = false;
		resetPace();
	}

	public static void onServerTime(long gameTime) {
		long now = System.nanoTime();
		if (lastServerNano != 0L && gameTime > lastServerTime) {
			long ticks = gameTime - lastServerTime;
			double seconds = (now - lastServerNano) / 1_000_000_000.0;
			lastServerDeltaTicks = Math.max(1L, ticks);
			if (seconds > 0.015 && seconds < 8.0) {
				float sample = (float) (ticks / seconds);
				if (sample > 20.5f) {
					sample = 20f;
				}
				if (sample >= 19.5f) {
					sample = 20f;
				}
				sample = StrayConfig.clamp(sample, 1f, 20f);
				serverTps = serverTps * 0.65f + sample * 0.35f;
			}
		}
		lastServerTime = gameTime;
		lastServerNano = now;
	}

	public static String place(float radius) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return "Join a world first.";
		}
		StrayConfig config = StrayConfig.get();
		if (!config.movementRingsEnabled) {
			config.movementRingsEnabled = true;
			config.save();
		}
		float size = StrayConfig.clamp(radius, 0.5f, 16f);
		List<Ring> rings = mutableHere();
		if (rings.size() >= MAX) {
			rings.removeFirst();
		}
		Vec3 pos = player.position();
		rings.add(new Ring(pos.x, pos.y, pos.z, size, new ArrayList<>(), true));
		touch();
		return "Movement ring " + rings.size() + " on " + IslandSaves.label() + "  " + format(size) + "m";
	}

	public static boolean remove(int index) {
		List<Ring> rings = mutableHere();
		if (index < 1 || index > rings.size()) {
			return false;
		}
		Ring ring = rings.get(index - 1);
		if (recordingRing == ring) {
			stopRecording(false);
		}
		if (playingRing == ring) {
			stopPlayback();
		}
		rings.remove(index - 1);
		if (rings.isEmpty()) {
			SAVED.remove(IslandSaves.key());
		}
		touch();
		return true;
	}

	public static void tick(Minecraft client) {
		syncIsland();
		if (dirty) {
			save();
		}
		if (client == null || client.player == null || client.level == null) {
			recordWasDown = false;
			stopRecording(false);
			stopPlayback();
			return;
		}
		if (!enabled()) {
			recordWasDown = false;
			stopRecording(false);
			stopPlayback();
			return;
		}
		StrayConfig config = StrayConfig.get();
		boolean down = StrayClient.strayHotkeys(client) && OdinClicks.isPressed(OdinClicks.parseKey(config.movementRecordKey));
		if (down && !recordWasDown) {
			toggleRecord(client);
		}
		recordWasDown = down;
		if (recordingRing != null) {
			if (serverStep(client) && client.screen == null) {
				capture(client);
				if (recordingRing.frames.size() >= MAX_FRAMES) {
					stopRecording(true);
				}
			}
			if (recordingRing != null) {
				recordingRing.invalidateLabels();
			}
		} else if (playingRing != null) {
			if (client.screen != null) {
				stopPlayback();
			} else if (!aiming) {
				syncTape(client);
			}
			if (playingRing != null) {
				playingRing.invalidateLabels();
			}
		}
		updateInside(client);
	}

	public static void preKeybinds(Minecraft client) {
		if (playingRing == null || client.player == null) {
			return;
		}
		if (client.screen != null || client.level == null || !enabled()) {
			stopPlayback();
			return;
		}
		applyCamera(client);
		if (aiming) {
			Frame first = playingRing.frames.getFirst();
			int slot = first.slot & 0xFF;
			if (slot >= 0 && slot < 9 && client.player.getInventory().getSelectedSlot() != slot) {
				client.player.getInventory().setSelectedSlot(slot);
			}
			setClick(client.options.keyAttack, false, playAttack);
			setClick(client.options.keyUse, false, playUse);
			playAttack = false;
			playUse = false;
			return;
		}
		Frame frame = currentFrame();
		if (frame == null) {
			stopPlayback();
			return;
		}
		int slot = frame.slot & 0xFF;
		if (slot >= 0 && slot < 9 && client.player.getInventory().getSelectedSlot() != slot) {
			client.player.getInventory().setSelectedSlot(slot);
		}
		boolean attack = (frame.flags & F_ATTACK) != 0;
		boolean use = (frame.flags & F_USE) != 0;
		setClick(client.options.keyAttack, attack, playAttack);
		setClick(client.options.keyUse, use, playUse);
		playAttack = attack;
		playUse = use;
	}

	public static float cameraYaw() {
		return lookYaw;
	}

	public static float cameraPitch() {
		return lookPitch;
	}

	/**
	 * Sample look for this render frame and write it onto the local player so
	 * {@code Camera.alignWithEntity} and the view matrix both move at display rate.
	 */
	public static boolean sampleCameraLook() {
		if (playingRing == null) {
			return false;
		}
		computeLook();
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			applyLook(client.player);
		}
		return true;
	}

	public static void applyCamera(Minecraft client) {
		if (playingRing == null || client == null || client.player == null) {
			return;
		}
		computeLook();
		applyLook(client.player);
	}

	private static void applyLook(LocalPlayer player) {
		player.setYRot(lookYaw);
		player.setXRot(lookPitch);
		player.forceSetRotation(lookYaw, false, lookPitch, false);
		player.setYHeadRot(lookYaw);
	}

	private static void computeLook() {
		long now = System.nanoTime();
		lookNanos = now;
		float yaw;
		float pitch;
		if (aiming) {
			double progress = aimMs <= 0L ? 1.0 : Math.min((now - aimStart) / (aimMs * 1_000_000.0), 1.0);
			float ease = SmoothRotate.easeInOutCubic(progress);
			yaw = SmoothRotate.interpolateYaw(aimFromYaw, aimToYaw, ease);
			pitch = SmoothRotate.lerp(aimFromPitch, aimToPitch, ease);
			if (progress >= 1.0) {
				aiming = false;
				tickBudget = 0f;
				playTickNanos = now;
				yaw = aimToYaw;
				pitch = aimToPitch;
			}
		} else {
			int last = playingRing.frames.size() - 1;
			if (last < 1) {
				yaw = tapeYaw(0f);
				pitch = tapePitch(0f);
			} else {
				float index = visualTapeIndex(last);
				yaw = tapeYaw(index);
				pitch = tapePitch(index);
			}
		}
		lookYaw = yaw;
		lookPitch = SmoothRotate.normalizePitch(pitch);
	}

	public static void applyInput(ClientInput input) {
		if (input == null) {
			return;
		}
		if (aiming) {
			input.keyPresses = Input.EMPTY;
			((ClientInputAccessor) input).stray$setMoveVector(Vec2.ZERO);
			return;
		}
		Frame frame = currentFrame();
		if (frame == null) {
			return;
		}
		Input keys = new Input(
			(frame.flags & F_FORWARD) != 0,
			(frame.flags & F_BACK) != 0,
			(frame.flags & F_LEFT) != 0,
			(frame.flags & F_RIGHT) != 0,
			(frame.flags & F_JUMP) != 0,
			(frame.flags & F_SHIFT) != 0,
			(frame.flags & F_SPRINT) != 0
		);
		input.keyPresses = keys;
		float forward = impulse(keys.forward(), keys.backward());
		float strafe = impulse(keys.left(), keys.right());
		((ClientInputAccessor) input).stray$setMoveVector(new Vec2(strafe, forward).normalized());
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!enabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		if (playing()) {
			drawPlaying(graphics, client.font);
		}
		if (here().isEmpty()) {
			return;
		}
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		Font font = client.font;
		int rgb = StrayConfig.get().movementRingsRgb & 0xFFFFFF;
		for (Ring ring : here()) {
			double relX = ring.x - camPos.x;
			double relY = ring.y + 1.15 - camPos.y;
			double relZ = ring.z - camPos.z;
			double distSq = relX * relX + relY * relY + relZ * relZ;
			if (distSq > DRAW_RANGE_SQ) {
				continue;
			}
			double facing = relX * forward.x() + relY * forward.y() + relZ * forward.z();
			if (facing <= 0.12) {
				continue;
			}
			Vec3 head = new Vec3(ring.x, ring.y + 1.15, ring.z);
			Vec3 ndc = client.gameRenderer.projectPointToScreen(head);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float sx = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float sy = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			float scale = NametagRenderer.distanceScale(Math.sqrt(distSq));
			Component name = ring.nameLabel();
			Component meters = ring.metersLabel();
			float nameW = font.width(name);
			float distW = font.width(meters);
			float w = nameW + 5f + distW + PAD_X * 2f;
			graphics.pose().pushMatrix();
			graphics.pose().translate(sx, sy);
			if (scale != 1.0f) {
				graphics.pose().scale(scale, scale);
			}
			float left = -w * 0.5f;
			float top = -2f - TAG_H;
			int rim = ring == recordingRing || ring == playingRing || ring.inside ? Theme.ACCENT : Theme.LINE;
			GuiDraw.panel(graphics, left, top, w, TAG_H, 5, Theme.WINDOW, rim);
			GuiDraw.text(graphics, font, name, left + PAD_X, GuiDraw.middle(top, TAG_H), 0xFF000000 | rgb, false);
			GuiDraw.text(graphics, font, meters, left + PAD_X + nameW + 5f, GuiDraw.middle(top, TAG_H), Anim.fade(Theme.MUTED, 1f), false);
			graphics.pose().popMatrix();
		}
	}

	private static void drawPlaying(GuiGraphicsExtractor graphics, Font font) {
		float w = GuiDraw.menuWidth(font, "Playing Recording");
		float x = graphics.guiWidth() * 0.5f - w * 0.5f;
		float y = graphics.guiHeight() * 0.5f - 28f;
		GuiDraw.hud(graphics, font, PLAYING_LABEL, x, y, Theme.ACCENT);
	}

	private static void toggleRecord(Minecraft client) {
		if (recordingRing != null) {
			stopRecording(true);
			return;
		}
		if (playingRing != null) {
			stopPlayback();
		}
		Ring inside = insideRing(client.player);
		if (inside == null) {
			chat("Stand in a movement ring first. /stray move 2", ChatFormatting.YELLOW);
			return;
		}
		inside.frames.clear();
		inside.invalidateLabels();
		recordingRing = inside;
		inside.armed = false;
		tickBudget = 0f;
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.3f, 0.7f));
		chat("Recording movement into this ring.", ChatFormatting.GREEN);
	}

	private static void stopRecording(boolean announce) {
		Ring done = recordingRing;
		recordingRing = null;
		if (done == null) {
			return;
		}
		done.invalidateLabels();
		if (done.frames.size() < 2) {
			done.frames.clear();
			if (announce) {
				chat("Nothing recorded.", ChatFormatting.GRAY);
			}
			return;
		}
		touch();
		if (announce) {
			Minecraft client = Minecraft.getInstance();
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 0.7f));
			chat("Saved " + done.frames.size() + " ticks (" + String.format(Locale.ROOT, "%.1f", done.frames.size() / 20f) + "s).", ChatFormatting.YELLOW);
		}
	}

	private static void stopPlayback() {
		if (playingRing == null) {
			return;
		}
		playingRing = null;
		playIndex = 0;
		playAttack = false;
		playUse = false;
		aiming = false;
		lookNanos = 0L;
		playTickNanos = 0L;
		lastPlaySyncNanos = 0L;
		Minecraft client = Minecraft.getInstance();
		if (client.options == null) {
			return;
		}
		syncPhysical(client.options.keyAttack);
		syncPhysical(client.options.keyUse);
	}

	private static void startPlayback(Ring ring) {
		if (ring.frames.size() < 2) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		playingRing = ring;
		playIndex = 0;
		playAttack = false;
		playUse = false;
		tickBudget = 0f;
		lookNanos = 0L;
		playTickNanos = 0L;
		lastPlaySyncNanos = 0L;
		Frame first = ring.frames.getFirst();
		aimFromYaw = player == null ? first.yaw : player.getYRot();
		aimFromPitch = player == null ? first.pitch : player.getXRot();
		aimToYaw = first.yaw;
		aimToPitch = first.pitch;
		lookYaw = aimFromYaw;
		lookPitch = aimFromPitch;
		float span = Math.max(
			Math.abs(SmoothRotate.normalizeYaw(aimToYaw - aimFromYaw)),
			Math.abs(aimToPitch - aimFromPitch)
		);
		if (span < 1.25f || player == null) {
			aiming = false;
			lookYaw = aimToYaw;
			lookPitch = aimToPitch;
			playTickNanos = System.nanoTime();
			if (player != null) {
				applyLook(player);
			}
		} else {
			aiming = true;
			aimStart = System.nanoTime();
			float speed = StrayConfig.clamp(StrayConfig.get().movementAimSpeed, 0.25f, 2.00f);
			aimMs = Math.max(80L, Math.min(800L, Math.round((140.0 + span * 2.8) / speed)));
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2f, 0.45f));
	}

	private static void capture(Minecraft client) {
		LocalPlayer player = client.player;
		Input keys = player.input == null ? Input.EMPTY : player.input.keyPresses;
		int flags = 0;
		if (keys.forward()) {
			flags |= F_FORWARD;
		}
		if (keys.backward()) {
			flags |= F_BACK;
		}
		if (keys.left()) {
			flags |= F_LEFT;
		}
		if (keys.right()) {
			flags |= F_RIGHT;
		}
		if (keys.jump()) {
			flags |= F_JUMP;
		}
		if (keys.shift()) {
			flags |= F_SHIFT;
		}
		if (keys.sprint()) {
			flags |= F_SPRINT;
		}
		if (client.options.keyAttack.isDown()) {
			flags |= F_ATTACK;
		}
		if (client.options.keyUse.isDown()) {
			flags |= F_USE;
		}
		recordingRing.frames.add(new Frame(player.getYRot(), player.getXRot(), player.getInventory().getSelectedSlot(), flags));
		recordingRing.invalidateLabels();
	}

	private static void updateInside(Minecraft client) {
		List<Ring> rings = here();
		if (rings.isEmpty()) {
			return;
		}
		LocalPlayer player = client.player;
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		boolean allow = client.screen == null && recordingRing == null && playingRing == null;
		for (Ring ring : rings) {
			double dx = x - ring.x;
			double dz = z - ring.z;
			boolean inside = dx * dx + dz * dz <= ring.radius * ring.radius && Math.abs(y - ring.y) <= 2.5;
			if (!inside) {
				ring.armed = true;
				ring.inside = false;
				continue;
			}
			ring.inside = true;
			if (allow && ring.armed && ring.frames.size() >= 2) {
				ring.armed = false;
				startPlayback(ring);
				allow = false;
			} else {
				ring.armed = false;
			}
		}
	}

	private static Ring insideRing(LocalPlayer player) {
		Ring best = null;
		double bestDist = Double.MAX_VALUE;
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		for (Ring ring : here()) {
			double dx = x - ring.x;
			double dz = z - ring.z;
			double dist = dx * dx + dz * dz;
			if (dist <= ring.radius * ring.radius && Math.abs(y - ring.y) <= 2.5 && dist < bestDist) {
				best = ring;
				bestDist = dist;
			}
		}
		return best;
	}

	private static Frame currentFrame() {
		if (playingRing == null) {
			return null;
		}
		int index = currentTapeTick();
		if (index < 0 || index >= playingRing.frames.size()) {
			return null;
		}
		return playingRing.frames.get(index);
	}

	private static int currentTapeTick() {
		if (playingRing == null) {
			return -1;
		}
		if (playTickNanos == 0L) {
			return playIndex;
		}
		int last = playingRing.frames.size();
		int index = Mth.floor(rawTapeTime());
		if (index < 0) {
			return 0;
		}
		if (index >= last) {
			return last - 1;
		}
		return index;
	}

	private static float rawTapeTime() {
		if (playTickNanos == 0L) {
			return 0f;
		}
		return (float) ((System.nanoTime() - playTickNanos) / 1_000_000_000.0 * playbackRate());
	}

	private static float visualTapeIndex(int last) {
		return Mth.clamp(rawTapeTime(), 0f, last - 0.0001f);
	}

	private static float playbackRate() {
		return serverTps >= 19.5f ? 20f : serverTps;
	}

	private static void syncTape(Minecraft client) {
		pollServerTps();
		long now = System.nanoTime();
		boolean frozen = client.level != null && !client.level.tickRateManager().runsNormally();
		if (frozen) {
			if (playTickNanos != 0L && lastPlaySyncNanos != 0L) {
				playTickNanos += now - lastPlaySyncNanos;
			}
			lastPlaySyncNanos = now;
			return;
		}
		lastPlaySyncNanos = now;
		if (playTickNanos == 0L) {
			return;
		}
		int size = playingRing.frames.size();
		if (rawTapeTime() >= size) {
			stopPlayback();
			return;
		}
		playIndex = Mth.clamp(Mth.floor(rawTapeTime()), 0, size - 1);
	}

	private static void pollServerTps() {
		long now = System.nanoTime();
		if (lastServerNano != 0L && lastServerDeltaTicks <= 2L) {
			double silence = (now - lastServerNano) / 1_000_000_000.0;
			if (silence > 0.08) {
				float starved = (float) Math.min(20.0, 1.0 / silence);
				if (starved < serverTps) {
					serverTps = serverTps * 0.45f + starved * 0.55f;
				}
			}
		}
	}

	private static float tapeYaw(float index) {
		return tapeAngle(index, true);
	}

	private static float tapePitch(float index) {
		return SmoothRotate.normalizePitch(tapeAngle(index, false));
	}

	private static float tapeAngle(float index, boolean yaw) {
		int last = playingRing.frames.size() - 1;
		if (last <= 0) {
			return yaw ? sampleYaw(0) : samplePitch(0);
		}
		float clamped = Mth.clamp(index, 0f, last);
		int i = Math.min(Mth.floor(clamped), last - 1);
		float t = clamped - i;
		float y1 = yaw ? sampleYaw(i) : samplePitch(i);
		float y2 = yaw ? unwrapYaw(sampleYaw(i + 1), y1) : samplePitch(i + 1);
		return y1 + (y2 - y1) * t;
	}

	private static float unwrapYaw(float yaw, float ref) {
		return ref + SmoothRotate.normalizeYaw(yaw - ref);
	}

	private static float sampleYaw(int index) {
		List<Frame> frames = playingRing.frames;
		return frames.get(Mth.clamp(index, 0, frames.size() - 1)).yaw;
	}

	private static float samplePitch(int index) {
		List<Frame> frames = playingRing.frames;
		return frames.get(Mth.clamp(index, 0, frames.size() - 1)).pitch;
	}

	private static void setClick(KeyMapping mapping, boolean down, boolean wasDown) {
		if (down && !wasDown) {
			KeyMapping.click(((KeyMappingAccessor) mapping).stray$boundKey());
		}
		mapping.setDown(down);
	}

	private static void syncPhysical(KeyMapping mapping) {
		mapping.setDown(OdinClicks.isPressed(((KeyMappingAccessor) mapping).stray$boundKey()));
	}

	private static boolean serverStep(Minecraft client) {
		pollServerTps();
		float rate = 20f;
		if (client.level != null) {
			var ticks = client.level.tickRateManager();
			if (!ticks.runsNormally()) {
				return false;
			}
			rate = ticks.tickrate();
		}
		float tps = playbackRate();
		float effective = Math.min(tps, 20f) * (rate / 20f);
		effective = StrayConfig.clamp(effective, 0.5f, 20f);
		tickBudget += effective / 20f;
		if (tickBudget < 1f) {
			return false;
		}
		tickBudget -= 1f;
		if (tickBudget > 1f) {
			tickBudget = 1f;
		}
		return true;
	}

	private static void resetPace() {
		lastServerTime = Long.MIN_VALUE;
		lastServerNano = 0L;
		lastServerDeltaTicks = 1L;
		serverTps = 20f;
		tickBudget = 0f;
	}

	private static float impulse(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0f;
		}
		return positive ? 1f : -1f;
	}

	private static void emit() {
		if (!enabled() || here().isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 camPos = camera.isInitialized() ? camera.position() : null;
		int rgb = StrayConfig.get().movementRingsRgb & 0xFFFFFF;
		int line = 0xEB000000 | rgb;
		for (Ring ring : here()) {
			int segments = SEGMENTS;
			if (camPos != null) {
				double dx = ring.x - camPos.x;
				double dy = ring.y - camPos.y;
				double dz = ring.z - camPos.z;
				double distSq = dx * dx + dy * dy + dz * dz;
				if (distSq > DRAW_RANGE_SQ) {
					continue;
				}
				if (distSq > FAR_SQ) {
					segments = SEGMENTS_FAR;
				}
			}
			double y = ring.y + 0.04;
			boolean hot = ring == recordingRing || ring == playingRing || ring.inside;
			int fill = (Math.round((hot ? 0.40f : 0.22f) * 255f) << 24) | rgb;
			drawDisk(ring, y, fill, segments);
			drawCircle(ring, y, ring.radius, line, 2.6f, segments);
			drawCircle(ring, y, Math.max(0.2, ring.radius * 0.92), 0x66000000 | rgb, 1.4f, segments);
		}
	}

	private static void drawDisk(Ring ring, double y, int fill, int segments) {
		GizmoStyle style = GizmoStyle.fill(fill);
		Vec3 center = new Vec3(ring.x, y, ring.z);
		Vec3 prev = null;
		int step = SEGMENTS / segments;
		for (int i = 0; i <= SEGMENTS; i += step) {
			Vec3 point = new Vec3(ring.x + COS[i] * ring.radius, y, ring.z + SIN[i] * ring.radius);
			if (prev != null) {
				GizmoProperties gizmo = Gizmos.rect(center, prev, point, center, style);
				gizmo.setAlwaysOnTop();
			}
			prev = point;
		}
	}

	private static void drawCircle(Ring ring, double y, double radius, int color, float width, int segments) {
		Vec3 prev = null;
		int step = SEGMENTS / segments;
		for (int i = 0; i <= SEGMENTS; i += step) {
			Vec3 point = new Vec3(ring.x + COS[i] * radius, y, ring.z + SIN[i] * radius);
			if (prev != null) {
				GizmoProperties gizmo = Gizmos.line(prev, point, color, width);
				gizmo.setAlwaysOnTop();
			}
			prev = point;
		}
	}

	private static String format(float value) {
		if (Math.abs(value - Math.round(value)) < 0.05f) {
			return Integer.toString(Math.round(value));
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static void chat(String text, ChatFormatting color) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null) {
			return;
		}
		client.gui.getChat().addClientSystemMessage(
			Component.literal("Stray move ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(text).withStyle(color))
		);
	}

	public static final class Ring {
		public final double x;
		public final double y;
		public final double z;
		public final float radius;
		final List<Frame> frames;
		boolean inside;
		boolean armed;
		private Component nameLabel;
		private Component metersLabel;

		Ring(double x, double y, double z, float radius, List<Frame> frames, boolean placedNow) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.radius = radius;
			this.frames = frames;
			this.inside = placedNow;
			this.armed = false;
		}

		Component nameLabel() {
			if (nameLabel == null) {
				if (this == recordingRing) {
					nameLabel = MenuFont.vanilla("REC " + frames.size());
				} else if (this == playingRing) {
					nameLabel = MenuFont.vanilla((aiming ? "AIM " : "PLAY ") + Math.min(playIndex + 1, frames.size()) + "/" + frames.size());
				} else if (frames.isEmpty()) {
					nameLabel = MenuFont.vanilla("empty");
				} else {
					nameLabel = MenuFont.vanilla(String.format(Locale.ROOT, "%.1fs", frames.size() / 20f));
				}
			}
			return nameLabel;
		}

		Component metersLabel() {
			if (metersLabel == null) {
				metersLabel = MenuFont.vanilla(format(radius) + "m");
			}
			return metersLabel;
		}

		void invalidateLabels() {
			nameLabel = null;
		}

		public int ticks() {
			return frames.size();
		}
	}

	static final class Frame {
		final float yaw;
		final float pitch;
		final int slot;
		final int flags;

		Frame(float yaw, float pitch, int slot, int flags) {
			this.yaw = yaw;
			this.pitch = pitch;
			this.slot = Math.max(0, Math.min(8, slot));
			this.flags = flags;
		}
	}

	private static List<Ring> here() {
		List<Ring> rings = SAVED.get(IslandSaves.key());
		return rings == null ? List.of() : rings;
	}

	private static List<Ring> mutableHere() {
		return SAVED.computeIfAbsent(IslandSaves.key(), ignored -> new ArrayList<>());
	}

	private static void syncIsland() {
		String island = IslandSaves.key();
		if (island.equals(lastIsland)) {
			return;
		}
		stopRecording(false);
		stopPlayback();
		lastIsland = island;
		for (Ring ring : here()) {
			ring.inside = false;
			ring.armed = false;
		}
	}

	private static void touch() {
		dirty = true;
		save();
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		SAVED.clear();
		JsonObject islands = IslandSaves.readIslands(FILE);
		for (String island : islands.keySet()) {
			JsonArray list = islands.getAsJsonArray(island);
			if (list == null) {
				continue;
			}
			List<Ring> rings = new ArrayList<>();
			for (JsonElement element : list) {
				Ring ring = read(element);
				if (ring != null) {
					rings.add(ring);
				}
			}
			if (!rings.isEmpty()) {
				SAVED.put(island, rings);
			}
		}
	}

	private static Ring read(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return null;
		}
		JsonObject object = element.getAsJsonObject();
		if (!object.has("x") || !object.has("y") || !object.has("z")) {
			return null;
		}
		float radius = object.has("radius") ? object.get("radius").getAsFloat() : 2f;
		radius = StrayConfig.clamp(radius, 0.5f, 16f);
		List<Frame> frames = new ArrayList<>();
		JsonArray raw = object.getAsJsonArray("frames");
		if (raw != null) {
			for (JsonElement item : raw) {
				if (!item.isJsonArray()) {
					continue;
				}
				JsonArray row = item.getAsJsonArray();
				if (row.size() < 4) {
					continue;
				}
				frames.add(new Frame(row.get(0).getAsFloat(), row.get(1).getAsFloat(), row.get(2).getAsInt(), row.get(3).getAsInt()));
				if (frames.size() >= MAX_FRAMES) {
					break;
				}
			}
		}
		return new Ring(
			object.get("x").getAsDouble(),
			object.get("y").getAsDouble(),
			object.get("z").getAsDouble(),
			radius,
			frames,
			false
		);
	}

	private static void save() {
		JsonObject islands = new JsonObject();
		for (Map.Entry<String, List<Ring>> entry : SAVED.entrySet()) {
			if (entry.getValue().isEmpty()) {
				continue;
			}
			JsonArray list = new JsonArray();
			for (Ring ring : entry.getValue()) {
				JsonObject object = new JsonObject();
				object.addProperty("x", IslandSaves.coord(ring.x));
				object.addProperty("y", IslandSaves.coord(ring.y));
				object.addProperty("z", IslandSaves.coord(ring.z));
				object.addProperty("radius", IslandSaves.coord(ring.radius));
				JsonArray frames = new JsonArray();
				for (Frame frame : ring.frames) {
					JsonArray row = new JsonArray();
					row.add(IslandSaves.coord(frame.yaw));
					row.add(IslandSaves.coord(frame.pitch));
					row.add(frame.slot);
					row.add(frame.flags);
					frames.add(row);
				}
				object.add("frames", frames);
				list.add(object);
			}
			islands.add(entry.getKey(), list);
		}
		if (IslandSaves.writeIslands(FILE, islands)) {
			dirty = false;
		}
	}
}
