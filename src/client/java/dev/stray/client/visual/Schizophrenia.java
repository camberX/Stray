package dev.stray.client.visual;

import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Schizophrenia: the people around you stare at you, and now and then you hear and see things
 * that aren't there. All of it is on your screen only: drawn heads, local sounds, a HUD line
 * and a drawn shape. Nothing is sent, no entity is added to the world.
 *
 * <p>Stares: how the drawn head turns (Behaviour). Episodes (how often: Intensity): whispers
 * (footsteps, a chest, breathing), a nametag that says your name, a fake chat ping, a shadow
 * person at the edge of your screen that is gone when you look.
 */
public final class Schizophrenia {
	public static final String[] BEHAVIOURS = {"Look away when watched", "Always stare", "Late notice"};
	public static final String[] INTENSITIES = {"Mild", "Bad day", "Full episode"};
	/** Seconds between episodes, shortest and longest, per intensity. */
	private static final int[][] GAPS = {{50, 110}, {20, 50}, {6, 16}};
	/** Shadow people come on their own, much more often: seconds between them per intensity. */
	private static final int[][] SHADOW_GAPS = {{12, 25}, {6, 12}, {2, 5}};
	/** How far the head turns from the body before the body follows (Turn body too). */
	private static final float NECK = 50f;
	/** Late notice: how long they ignore you, then how fast they turn. */
	private static final float NOTICE_DELAY = 1.4f;
	private static final String[] PINGS = {"%s", "%s?", "turn around %s", "%s behind you", "is %s watching", "we know %s", "%s look up", "dont tell %s"};

	private static final Random RANDOM = new Random();
	private static final Map<Integer, Stare> STARES = new HashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<Integer, Long> TAGS = new HashMap<>();
	private static int nextEpisode = -1;
	private static int nextShadow = -1;
	/** Have an episode now: which kind is next, so each press shows another one. */
	private static int forced = -1;
	private static int ticks;
	private static Shadow shadow;

	private Schizophrenia() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> drawShadow());
	}

	// ------------------------------------------------------------------ stares

	private static final class Stare {
		float w;
		long at;
		long inRange;
		boolean near;
	}

	/** A player: turns the drawn head toward you, and maybe their nametag says your name. */
	public static void applyPlayer(AbstractClientPlayer entity, LivingEntityRenderState state, float partial) {
		StrayConfig config = StrayConfig.get();
		if (!config.schizophrenia || entity == Minecraft.getInstance().player) {
			return;
		}
		stare(entity, state, partial, config);
		Long until = TAGS.get(entity.getId());
		if (until != null && System.currentTimeMillis() < until && state.nameTag != null) {
			Player me = Minecraft.getInstance().player;
			state.nameTag = (entity.getId() & 1) == 0 || me == null
				? Component.literal("...").withStyle(net.minecraft.ChatFormatting.GRAY)
				: Component.literal(me.getGameProfile().name()).withStyle(net.minecraft.ChatFormatting.RED);
		}
	}

	/** A mob (Mobs too): the same stare. */
	public static void applyMob(LivingEntity entity, LivingEntityRenderState state, float partial) {
		StrayConfig config = StrayConfig.get();
		if (!config.schizophrenia || !config.schizophreniaMobs || !(entity instanceof net.minecraft.world.entity.Mob)) {
			return;
		}
		stare(entity, state, partial, config);
	}

	/** How much this player's head tilts sideways (radians), for the player model. */
	public static float tilt(int id) {
		StrayConfig config = StrayConfig.get();
		if (!config.schizophrenia || !config.schizophreniaTilt) {
			return 0f;
		}
		Stare s = STARES.get(id);
		return s == null ? 0f : s.w * ((id & 1) == 0 ? 0.32f : -0.32f);
	}

	private static void stare(LivingEntity entity, LivingEntityRenderState state, float partial, StrayConfig config) {
		Minecraft client = Minecraft.getInstance();
		Player me = client.player;
		if (me == null) {
			return;
		}
		Vec3 from = entity.getEyePosition(partial);
		Vec3 to = me.getEyePosition(partial);
		Vec3 d = to.subtract(from);
		float range = StrayConfig.clamp(config.schizophreniaRange, 4f, 128f);
		boolean near = d.lengthSqr() <= range * range && d.lengthSqr() > 1.0e-4;

		long now = System.nanoTime();
		Stare s = STARES.get(entity.getId());
		if (s == null) {
			if (!near) {
				return;
			}
			s = new Stare();
			s.at = now;
			STARES.put(entity.getId(), s);
		}
		float dt = Math.min(0.1f, (now - s.at) / 1.0e9f);
		s.at = now;
		if (near && !s.near) {
			s.inRange = now;
		}
		s.near = near;

		int behaviour = Math.floorMod(config.schizophreniaBehaviour, BEHAVIOURS.length);
		float target;
		float speed;
		if (!near) {
			target = 0f;
			speed = 6f;
		} else if (behaviour == 1) {
			target = 1f;
			speed = 1000f;
		} else if (behaviour == 2) {
			target = (now - s.inRange) / 1.0e9f > NOTICE_DELAY ? 1f : 0f;
			speed = 2.2f;
		} else {
			// Caught: they snap back to normal the moment you look at them, and back to you after.
			target = watched(me, from, partial) ? 0f : 1f;
			speed = 30f;
		}
		s.w += (target - s.w) * (1f - (float) Math.exp(-speed * dt));
		if (Math.abs(target - s.w) < 0.002f) {
			s.w = target;
		}
		if (s.w <= 0f) {
			return;
		}

		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		float headYaw = state.bodyRot + state.yRot;
		float lookYaw = headYaw + Mth.wrapDegrees(yaw - headYaw) * s.w;
		float lookPitch = state.xRot + (pitch - state.xRot) * s.w;
		float turn = Mth.wrapDegrees(lookYaw - state.bodyRot);
		if (config.schizophreniaBody && Math.abs(turn) > NECK) {
			// The body turns with it, so the head never twists further than a real neck.
			state.bodyRot = lookYaw - Math.copySign(NECK, turn);
			turn = Math.copySign(NECK, turn);
		}
		state.yRot = turn;
		state.xRot = lookPitch;
	}

	/** True when your crosshair is on (or very close to) this head. */
	private static boolean watched(Player me, Vec3 head, float partial) {
		Vec3 eye = me.getEyePosition(partial);
		Vec3 to = head.subtract(eye);
		double dist = to.length();
		if (dist < 1.0e-3) {
			return true;
		}
		double cos = me.getViewVector(partial).dot(to.scale(1.0 / dist));
		double limit = Math.max(Math.toRadians(7.0), Math.atan(0.9 / dist));
		return cos > Math.cos(limit);
	}

	// ------------------------------------------------------------------ episodes

	private record Pending(int at, Runnable run) {
	}

	private record Shadow(double x, double y, double z, boolean alongX, int until) {
	}

	/** Every client tick: plays what is due and starts the next episode. */
	public static void tick(Minecraft client) {
		ticks++;
		StrayConfig config = StrayConfig.get();
		if (!config.schizophrenia || client.player == null || client.level == null) {
			removePing(client);
			PENDING.clear();
			TAGS.clear();
			shadow = null;
			nextEpisode = -1;
			nextShadow = -1;
			return;
		}
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (ticks >= p.at) {
				it.remove();
				p.run.run();
			}
		}
		if (shadow != null && (ticks > shadow.until || shadowSeen(client.player))) {
			shadow = null;
		}
		TAGS.values().removeIf(until -> System.currentTimeMillis() > until);
		// Forget who has not been drawn for two seconds (gone, or out of sight).
		long stale = System.nanoTime() - 2_000_000_000L;
		STARES.values().removeIf(st -> st.at < stale);
		int intensity = Math.floorMod(config.schizophreniaIntensity, INTENSITIES.length);
		if (config.schizophreniaShadows) {
			if (nextShadow < 0) {
				nextShadow = ticks + gap(SHADOW_GAPS, intensity);
			}
			// A new one only once the last is gone; the wait starts again either way.
			if (ticks >= nextShadow) {
				nextShadow = ticks + gap(SHADOW_GAPS, intensity);
				if (shadow == null) {
					spawnShadow(client);
				}
			}
		} else {
			nextShadow = -1;
		}
		if (nextEpisode < 0) {
			nextEpisode = ticks + gap(GAPS, intensity);
		}
		if (ticks < nextEpisode) {
			return;
		}
		nextEpisode = ticks + gap(GAPS, intensity);
		if (choices(config).isEmpty() && forced >= 0) {
			net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(Component.literal("§bStray §7Schizophrenia: turn on Whispers, Nametags, Fake pings or Shadow people first."));
			forced = -1;
			return;
		}
		List<Runnable> choices = new ArrayList<>();
		if (config.schizophreniaWhispers) {
			choices.add(() -> whisper(client));
		}
		if (config.schizophreniaNametags) {
			choices.add(() -> nametag(client));
		}
		if (config.schizophreniaPings) {
			choices.add(() -> ping(client));
		}
		if (config.schizophreniaShadows) {
			choices.add(() -> spawnShadow(client));
		}
		if (choices.isEmpty()) {
			return;
		}
		if (forced >= 0) {
			choices.get(forced % choices.size()).run();
			forced = -1;
			return;
		}
		choices.get(RANDOM.nextInt(choices.size())).run();
	}

	/** Starts one episode now, for testing: /stray debug dev schizo. */
	public static void episodeNow() {
		nextEpisode = ticks;
		forced = ++pressed;
	}

	private static int pressed = -1;

	private static List<Boolean> choices(StrayConfig config) {
		List<Boolean> on = new ArrayList<>();
		for (boolean b : new boolean[]{config.schizophreniaWhispers, config.schizophreniaNametags, config.schizophreniaPings, config.schizophreniaShadows}) {
			if (b) {
				on.add(true);
			}
		}
		return on;
	}

	private static int gap(int[][] gaps, int intensity) {
		int[] g = gaps[intensity];
		return (g[0] + RANDOM.nextInt(g[1] - g[0] + 1)) * 20;
	}

	private static void later(int delay, Runnable run) {
		PENDING.add(new Pending(ticks + delay, run));
	}

	// Whispers: footsteps walking up behind you, a chest or door nearby, breathing in your ear.

	private static void whisper(Minecraft client) {
		switch (RANDOM.nextInt(4)) {
			case 0 -> {
				float side = (RANDOM.nextFloat() - 0.5f) * 40f;
				for (int i = 0; i < 6; i++) {
					double back = 7.0 - i * 1.0;
					later(i * 7, () -> sound(client, behind(client, back, side), null, 0.45f, 0.9f + RANDOM.nextFloat() * 0.2f));
				}
			}
			case 1 -> sound(client, behind(client, 6.0 + RANDOM.nextDouble() * 4.0, (RANDOM.nextFloat() - 0.5f) * 120f),
				RANDOM.nextBoolean() ? SoundEvents.CHEST_OPEN : SoundEvents.WOODEN_DOOR_OPEN, 0.5f, 0.95f);
			case 2 -> sound(client, behind(client, 1.2, (RANDOM.nextFloat() - 0.5f) * 60f), SoundEvents.PLAYER_BREATH, 0.6f, 0.8f);
			default -> sound(client, behind(client, 3.0, 180f * (RANDOM.nextBoolean() ? 0.5f : -0.5f)), SoundEvents.AMBIENT_CAVE.value(), 0.5f, 1f);
		}
	}

	/** A point behind you ({@code side} degrees off straight back), at your feet's height. */
	private static Vec3 behind(Minecraft client, double back, float side) {
		Player me = client.player;
		float yaw = (me.getYRot() + 180f + side) * Mth.DEG_TO_RAD;
		return new Vec3(me.getX() - Mth.sin(yaw) * back, me.getY(), me.getZ() + Mth.cos(yaw) * back);
	}

	/** A local sound; {@code event} null means the footstep of the block there. */
	private static void sound(Minecraft client, Vec3 at, SoundEvent event, float volume, float pitch) {
		if (client.level == null || client.player == null || !StrayConfig.get().schizophrenia) {
			return;
		}
		if (event == null) {
			BlockState below = client.level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
			event = below.isAir() ? SoundEvents.STONE_STEP : below.getSoundType().getStepSound();
		}
		client.level.playLocalSound(at.x, at.y, at.z, event, SoundSource.PLAYERS, volume, pitch, false);
	}

	// Nametags: someone near you has your name over their head for a moment.

	private static void nametag(Minecraft client) {
		List<AbstractClientPlayer> near = nearPlayers(client);
		if (near.isEmpty()) {
			return;
		}
		TAGS.put(near.get(RANDOM.nextInt(near.size())).getId(), System.currentTimeMillis() + 2500L);
	}

	private static List<AbstractClientPlayer> nearPlayers(Minecraft client) {
		float range = StrayConfig.clamp(StrayConfig.get().schizophreniaRange, 4f, 128f);
		List<AbstractClientPlayer> near = new ArrayList<>();
		for (AbstractClientPlayer p : client.level.players()) {
			if (p != client.player && p.distanceToSqr(client.player) <= range * range) {
				near.add(p);
			}
		}
		return near;
	}

	// Pings: a chat line with your name in it, gone a second later.

	private static void ping(Minecraft client) {
		removePing(client);
		List<AbstractClientPlayer> near = nearPlayers(client);
		String me = client.player.getGameProfile().name();
		String[] parts = PINGS[RANDOM.nextInt(PINGS.length)].split("%s", -1);
		net.minecraft.network.chat.MutableComponent line = Component.empty();
		if (!near.isEmpty()) {
			line.append(Component.literal(near.get(RANDOM.nextInt(near.size())).getGameProfile().name()).withStyle(net.minecraft.ChatFormatting.GRAY))
				.append(Component.literal(": ").withStyle(net.minecraft.ChatFormatting.WHITE));
		}
		for (int i = 0; i < parts.length; i++) {
			line.append(Component.literal(parts[i]).withStyle(net.minecraft.ChatFormatting.WHITE));
			if (i < parts.length - 1) {
				line.append(Component.literal(me).withStyle(net.minecraft.ChatFormatting.YELLOW));
			}
		}
		// A real line in your own chat (never sent, no chat event), taken out again a second later.
		fakeLine = line;
		client.gui.hud.getChat().addClientSystemMessage(line);
		sound(client, client.player.position(), SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
		later(24, () -> removePing(client));
	}

	private static Component fakeLine;

	/** Takes the fake line out of chat, as if it was never there. */
	private static void removePing(Minecraft client) {
		Component line = fakeLine;
		fakeLine = null;
		if (line == null || client.gui == null) {
			return;
		}
		dev.stray.client.mixin.ChatComponentAccessor chat = (dev.stray.client.mixin.ChatComponentAccessor) client.gui.hud.getChat();
		String text = line.getString();
		boolean removed = chat.stray$allMessages().removeIf(m -> m.content() == line);
		if (!removed) {
			// Another mod may have rebuilt the line on the way in; the newest matching text is ours.
			List<net.minecraft.client.multiplayer.chat.GuiMessage> all = chat.stray$allMessages();
			for (int i = 0; i < Math.min(all.size(), 20); i++) {
				if (all.get(i).content().getString().equals(text)) {
					all.remove(i);
					removed = true;
					break;
				}
			}
		}
		if (removed) {
			chat.stray$refreshTrimmedMessages();
		}
	}

	// Shadows: a dark figure at the edge of your screen, gone when you look at it.

	private static void spawnShadow(Minecraft client) {
		Player me = client.player;
		// Your view: forward, right and up, and how far the screen reaches (FOV and window shape).
		Vec3 eye = me.getEyePosition();
		Vec3 f = me.getViewVector(1f);
		Vec3 right = f.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0e-4) {
			float yaw = me.getYRot() * Mth.DEG_TO_RAD;
			right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		}
		right = right.normalize();
		Vec3 up = right.cross(f).normalize();
		double tanV = Math.tan(Math.toRadians(client.options.fov().get() / 2.0));
		double tanH = tanV * client.getWindow().getWidth() / (double) Math.max(1, client.getWindow().getHeight());
		for (int attempt = 0; attempt < 32; attempt++) {
			// A corner: far to one side, and low (it stands there) or high (only its top shows).
			boolean top = RANDOM.nextInt(3) == 0;
			double nx = (0.78 + RANDOM.nextDouble() * 0.1) * (RANDOM.nextBoolean() ? 1 : -1);
			double ny = (0.55 + RANDOM.nextDouble() * 0.15) * (top ? 1 : -1);
			Vec3 dir = f.add(right.scale(nx * tanH)).add(up.scale(ny * tanV)).normalize();
			double dist = 5.0 + RANDOM.nextDouble() * 9.0;
			Vec3 at = eye.add(dir.scale(dist));
			// Low corners put its feet there, high corners its head.
			double feetY = top ? at.y - 1.9 : at.y;
			BlockPos column = BlockPos.containing(at.x, feetY + 1.5, at.z);
			for (int i = 0; i < 4; i++) {
				BlockPos feet = column.below(i);
				if (clear(client, feet) && clear(client, feet.above())
					&& !client.level.getBlockState(feet.below()).getCollisionShape(client.level, feet.below()).isEmpty()) {
					// It faces you: its shoulders run across the line between you.
					boolean alongX = Math.abs(me.getZ() - at.z) > Math.abs(me.getX() - at.x);
					shadow = new Shadow(at.x, feet.getY(), at.z, alongX, ticks + 160);
					edge = (float) Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, f.dot(dir)))));
					return;
				}
			}
		}
	}

	private static boolean clear(Minecraft client, BlockPos pos) {
		return client.level.getBlockState(pos).getCollisionShape(client.level, pos).isEmpty();
	}

	/** How far from your crosshair the shadow stood, in degrees; it is gone once you look within half of that. */
	private static float edge = 40f;

	private static boolean shadowSeen(Player me) {
		Vec3 eye = me.getEyePosition();
		Vec3 to = new Vec3(shadow.x, shadow.y + 1.0, shadow.z).subtract(eye);
		double dist = to.length();
		return dist < 3.0 || me.getViewVector(1f).dot(to.scale(1.0 / dist)) > Math.cos(Math.toRadians(edge * 0.5));
	}

	private static void drawShadow() {
		Shadow s = shadow;
		if (s == null || !StrayConfig.get().schizophrenia) {
			return;
		}
		int ink = 0xF2060606;
		box(s, -0.25, 0.25, 0.0, 0.75, 0.125, ink);
		box(s, -0.25, 0.25, 0.75, 1.5, 0.125, ink);
		box(s, -0.5, -0.25, 0.78, 1.5, 0.125, ink);
		box(s, 0.25, 0.5, 0.78, 1.5, 0.125, ink);
		box(s, -0.25, 0.25, 1.5, 2.0, 0.25, ink);
	}

	/** One part of the figure: {@code u} across its shoulders, {@code depth} half its thickness. */
	private static void box(Shadow s, double u0, double u1, double y0, double y1, double depth, int ink) {
		AABB box = s.alongX
			? new AABB(s.x + u0, s.y + y0, s.z - depth, s.x + u1, s.y + y1, s.z + depth)
			: new AABB(s.x - depth, s.y + y0, s.z + u0, s.x + depth, s.y + y1, s.z + u1);
		Gizmos.cuboid(box, GizmoStyle.fill(ink));
	}
}
