package dev.stray.client.movement;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.editor.WorldEditor;
import dev.stray.client.mixin.ClientInputAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Freecam: the camera leaves the player and flies on its own. Entirely client side: the
 * player's movement input is zeroed while it is on (so the player stands still and nothing
 * new is sent), mouse look turns the camera instead of the player, and attack/use/pick are
 * swallowed so clicks don't act on whatever the (unmoved) player happens to face.
 *
 * <p>WASD flies along the view direction's horizontal heading, Space/Shift go up/down,
 * Sprint flies faster, and the scroll wheel changes the speed.
 */
public final class Freecam {
	private static final double MIN_SPEED = 0.05;
	private static final double MAX_SPEED = 8.0;

	private static boolean active;
	private static ClientLevel level;
	private static double x;
	private static double y;
	private static double z;
	private static double prevX;
	private static double prevY;
	private static double prevZ;
	private static float yaw;
	private static float pitch;
	private static Vec3 velocity = Vec3.ZERO;
	private static boolean savedSmartCull = true;

	private Freecam() {
	}

	public static boolean active() {
		return active;
	}

	public static void toggle() {
		if (active) {
			disable();
		} else {
			enable();
		}
	}

	public static void set(boolean on) {
		if (on != active) {
			toggle();
		}
	}

	public static void enable() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || active) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		x = prevX = eye.x;
		y = prevY = eye.y;
		z = prevZ = eye.z;
		yaw = player.getYRot();
		pitch = player.getXRot();
		velocity = Vec3.ZERO;
		level = client.level;
		active = true;
		// Smart culling hides everything when the camera sits inside a block; the camera can fly
		// through walls here, so turn it off while flying.
		savedSmartCull = client.smartCull;
		client.smartCull = false;
		releaseMovementKeys(client);
	}

	public static void disable() {
		if (!active) {
			return;
		}
		active = false;
		level = null;
		velocity = Vec3.ZERO;
		Minecraft client = Minecraft.getInstance();
		client.smartCull = savedSmartCull;
		if (WorldEditor.active()) {
			WorldEditor.disable();
		}
	}

	/** Runs at the end of every client tick: moves the camera from the movement keys. */
	public static void tick(Minecraft client) {
		if (!active) {
			return;
		}
		if (client.player == null || client.level == null || client.level != level || !client.player.isAlive()) {
			disable();
			return;
		}
		prevX = x;
		prevY = y;
		prevZ = z;
		Vec3 target = Vec3.ZERO;
		workspace = dev.stray.client.editor.EditorScreen.flyable(client.gui.screen());
		if (client.gui.screen() == null || workspace) {
			var options = client.options;
			double forward = (down(options.keyUp) ? 1 : 0) - (down(options.keyDown) ? 1 : 0);
			double strafe = (down(options.keyLeft) ? 1 : 0) - (down(options.keyRight) ? 1 : 0);
			double vertical = (down(options.keyJump) ? 1 : 0) - (down(options.keyShift) ? 1 : 0);
			double rad = Math.toRadians(yaw);
			double sin = Math.sin(rad);
			double cos = Math.cos(rad);
			// Minecraft yaw: 0 faces +Z, 90 faces -X.
			double dx = -sin * forward + cos * strafe;
			double dz = cos * forward + sin * strafe;
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			if (horizontal > 1) {
				dx /= horizontal;
				dz /= horizontal;
			}
			double speed = speed() * (down(options.keySprint) ? 2.5 : 1.0);
			target = new Vec3(dx * speed, vertical * speed, dz * speed);
		}
		// Ease toward the wanted velocity so starts and stops aren't instant jerks.
		velocity = velocity.lerp(target, 0.55);
		if (velocity.lengthSqr() < 1.0E-6) {
			velocity = Vec3.ZERO;
		}
		x += velocity.x;
		y += velocity.y;
		z += velocity.z;
	}

	/** The world editor's workspace is open (cursor free): movement keys are read directly. */
	private static boolean workspace;

	private static boolean down(KeyMapping key) {
		if (workspace) {
			return dev.stray.client.combat.OdinClicks.isPressed(dev.stray.client.combat.OdinClicks.parseKey(key.saveString()));
		}
		return key.isDown();
	}

	/** Moves the camera smoothly by {@code delta} (mouse wheel in the editor). */
	public static void glide(Vec3 delta) {
		if (active) {
			velocity = velocity.add(delta.scale(0.45));
		}
	}

	/** While flying the player stays put: no movement keys reach it. */
	public static void applyInput(ClientInput input) {
		if (!active || input == null) {
			return;
		}
		input.keyPresses = Input.EMPTY;
		((ClientInputAccessor) input).stray$setMoveVector(Vec2.ZERO);
	}

	/** Mouse look turns the camera. Same scale as {@code Entity.turn}. */
	public static boolean turn(double xo, double yo) {
		if (!active) {
			return false;
		}
		yaw += (float) xo * 0.15f;
		pitch = Mth.clamp(pitch + (float) yo * 0.15f, -90f, 90f);
		return true;
	}

	/**
	 * Swallows attack / use / pick-block clicks so the unmoved player doesn't act on what it
	 * faces. The world editor gets them instead when it is on. Runs before vanilla's keybinds.
	 */
	public static void preKeybinds(Minecraft client) {
		if (!active) {
			return;
		}
		var options = client.options;
		int attacks = drain(options.keyAttack);
		int uses = drain(options.keyUse);
		int picks = drain(options.keyPickItem);
		boolean attackHeld = options.keyAttack.isDown();
		boolean useHeld = options.keyUse.isDown();
		options.keyAttack.setDown(false);
		options.keyUse.setDown(false);
		options.keyPickItem.setDown(false);
		if (WorldEditor.active()) {
			int[] slots = new int[options.keyHotbarSlots.length];
			for (int i = 0; i < slots.length; i++) {
				slots[i] = drain(options.keyHotbarSlots[i]);
			}
			WorldEditor.onInput(client, attacks, uses, picks, attackHeld, useHeld, slots);
		}
	}

	private static int drain(KeyMapping key) {
		int count = 0;
		while (key.consumeClick()) {
			count++;
		}
		return count;
	}

	/** Scroll: the world editor uses it for the palette, plain freecam for the flight speed. */
	public static boolean scroll(double amount) {
		if (!active || amount == 0 || Minecraft.getInstance().gui.screen() != null) {
			return false;
		}
		if (WorldEditor.active()) {
			return WorldEditor.scroll(amount);
		}
		StrayConfig config = StrayConfig.get();
		double next = speed() * (amount > 0 ? 1.15 : 1 / 1.15);
		config.freecamSpeed = (float) Mth.clamp(next, MIN_SPEED, MAX_SPEED);
		return true;
	}

	public static double speed() {
		return Mth.clamp(StrayConfig.get().freecamSpeed, MIN_SPEED, MAX_SPEED);
	}

	public static double x(float partial) {
		return Mth.lerp(partial, prevX, x);
	}

	public static double y(float partial) {
		return Mth.lerp(partial, prevY, y);
	}

	public static double z(float partial) {
		return Mth.lerp(partial, prevZ, z);
	}

	public static Vec3 position(float partial) {
		return new Vec3(x(partial), y(partial), z(partial));
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	/** Unit vector the camera looks along. */
	public static Vec3 look() {
		return Vec3.directionFromRotation(pitch, yaw);
	}

	public static void teleport(Vec3 pos) {
		x = prevX = pos.x;
		y = prevY = pos.y;
		z = prevZ = pos.z;
		velocity = Vec3.ZERO;
	}

	private static void releaseMovementKeys(Minecraft client) {
		LocalPlayer player = client.player;
		if (player != null) {
			player.setSprinting(false);
		}
	}
}
