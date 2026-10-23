package dev.stray.client.cs2;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.cs2.anim.NmClip;
import dev.stray.client.cs2.anim.Pose;
import dev.stray.client.cs2.render.KnifeRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * First-person CS2 viewmodel: plays CS2's own knife clips (draw, idle, inspect, swings)
 * on CS2's arm rig, then draws the Minecraft player's arm where CS2's forearm and hand
 * are, and the knife where CS2's weapon bone is.
 */
public final class Cs2Viewmodel {
	private enum Action {
		DRAW, IDLE, INSPECT, SWING
	}

	private static Action action = Action.IDLE;
	private static long startNs = System.nanoTime();
	private static String swingClip = "light_miss1";
	private static boolean swingAlt;
	private static float lastSwing;
	private static Object lastStack;
	private static int inspectVariant;

	private Cs2Viewmodel() {
	}

	public static void inspect() {
		if (action == Action.SWING || action == Action.DRAW) {
			return;
		}
		inspectVariant = (int) (Math.random() * 1000);
		start(Action.INSPECT);
	}

	public static void draw() {
		start(Action.DRAW);
	}

	private static void start(Action next) {
		action = next;
		startNs = System.nanoTime();
	}


	/** CS2's swing clips play this much faster, so clicks and mining keep up with Minecraft. */
	private static final float SWING_RATE = 1.7f;
	private static boolean lastAttackDown;
	private static boolean clickQueued;
	private static volatile boolean externalClick;

	/** A click sent by the mod (autoclicker); swings like a real click. */
	public static void clicked() {
		externalClick = true;
	}
	/** Shortest time between two swings started by clicking (seconds), so spam clicking stays readable. */
	private static final float MIN_SWING_GAP = 0.16f;

	private static void nextSwing() {
		swingAlt = !swingAlt;
		swingClip = swingAlt ? "light_miss1" : "light_miss2";
		start(Action.SWING);
	}

	private static boolean mining() {
		Minecraft mc = Minecraft.getInstance();
		return mc.screen == null && mc.options.keyAttack.isDown() && mc.hitResult != null
			&& mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
	}

	private static float elapsed() {
		return (System.nanoTime() - startNs) / 1_000_000_000f;
	}

	/** Picks the clip for this frame and samples it into the pose. */
	private static void sample(Cs2Knives.Knife knife, float swingProgress, Object stack) {
		Pose pose = knife.pose;
		if (!java.util.Objects.equals(stack, lastStack)) {
			lastStack = stack;
			start(Action.DRAW);
		}
		// A new swing: a fresh left click (so spam clicking restarts the swing each time), or the
		// player's swing starting over for any other reason.
		Minecraft mc = Minecraft.getInstance();
		boolean attackDown = mc.screen == null && mc.options.keyAttack.isDown();
		boolean clicked = attackDown && !lastAttackDown;
		lastAttackDown = attackDown;
		boolean swingRestarted = swingProgress > 0f && swingProgress < 0.5f && (lastSwing == 0f || swingProgress < lastSwing - 0.05f);
		// Each click (yours or the autoclicker's) alternates between the two
		// swings. Clicks inside MIN_SWING_GAP wait for it instead of being dropped, so spam clicking
		// and the autoclicker both swing at the top speed.
		// A restarted Minecraft swing only counts when nothing is swinging (e.g. the triggerbot);
		// otherwise it is the swing of a click already counted.
		if (clicked || externalClick || swingRestarted && action != Action.SWING) {
			clickQueued = true;
		}
		externalClick = false;
		if (clickQueued && (action != Action.SWING || elapsed() >= MIN_SWING_GAP)) {
			clickQueued = false;
			nextSwing();
		}
		lastSwing = swingProgress;
		// Holding left click on a block keeps swinging: start the next swing as the current one winds down.
		// Holding left click on a block, or holding the autoclicker's key, swings back to back at
		// the same top speed as spam clicking.
		if (mining() || dev.stray.client.combat.AutoClicker.leftHeld()) {
			if (action != Action.SWING || elapsed() >= MIN_SWING_GAP) {
				nextSwing();
			}
		}
		if (!StrayConfig.get().cs2Animations) {
			pose.sample(knife.clip("idle"), 0f);
			return;
		}
		NmClip clip = switch (action) {
			case DRAW -> knife.clip("draw");
			case INSPECT -> {
				java.util.List<NmClip> variants = knife.inspects();
				yield variants.isEmpty() ? knife.clip("lookat") : variants.get(inspectVariant % variants.size());
			}
			case SWING -> knife.clip(swingClip) != null ? knife.clip(swingClip) : knife.clip("light_miss");
			default -> null;
		};
		float t = action == Action.SWING ? elapsed() * SWING_RATE : elapsed();
		if (clip != null && t < clip.duration) {
			pose.sample(clip, t);
			return;
		}
		if (action != Action.IDLE) {
			start(Action.IDLE);
			t = 0f;
		}
		NmClip idle = knife.clip("idle1") != null ? knife.clip("idle1") : knife.clip("idle");
		if (idle == null) {
			pose.reference();
			return;
		}
		pose.sample(idle, t % Math.max(0.01f, idle.duration));
	}

	/**
	 * Draws arm and knife in place of the vanilla first-person hand. Returns false when it
	 * cannot (nothing loaded), so vanilla draws as usual.
	 */
	public static boolean render(
		AbstractClientPlayer player,
		HumanoidArm arm,
		float swingProgress,
		Object stack,
		PoseStack pose,
		SubmitNodeCollector collector,
		int light
	) {
		Cs2Knives.Knife knife = Cs2Knives.current();
		if (knife == null || knife.pose == null) {
			return false;
		}
		sample(knife, swingProgress, stack);
		Pose rig = knife.pose;
		float scale = KnifeRender.VIEW_SCALE;
		Matrix4f view = KnifeRender.sourceToView(scale);
		boolean left = arm == HumanoidArm.LEFT;
		if (left) {
			view = new Matrix4f().scale(-1f, 1f, 1f).mul(view);
		}

		pose.pushPose();
		float[] off = StrayConfig.get().cs2ViewOffset;
		if (off != null && off.length >= 3) {
			// Arm and knife move together; positive distance pushes the viewmodel away.
			pose.translate(left ? -off[0] : off[0], off[1], -off[2]);
		}
		Matrix4f knifeView = new Matrix4f(view);
		float[] g = StrayConfig.get().cs2Grip.get(knife.id + "#hand");
		if (g == null || g.length < 7) {
			g = GripDefaults.hand(knife.id);
		}
		{
			// Moves the knife inside the hand, along its own axes (z runs along the blade), so the
			// adjustment follows every animation. Rotation and size pivot at the top of the handle.
			Matrix4f frame = rig.skin[0];
			Matrix4f local = new Matrix4f(frame)
				.translate(g[0], g[1], g[2])
				.rotateXYZ((float) Math.toRadians(g[3]), (float) Math.toRadians(g[4]), (float) Math.toRadians(g[5]))
				.scale(g[6] <= 0f ? 1f : g[6])
				.mul(new Matrix4f(frame).invert());
			knifeView = new Matrix4f(view).mul(local);
		}
		KnifeRender.submit(pose, collector, light, knife, knifeView, rig.skin);
		drawArm(player, rig, view, left, pose, collector, light);
		pose.popPose();
		return true;
	}

	/** Puts the Minecraft arm along CS2's forearm with its hand end at CS2's hand bone. */
	private static void drawArm(AbstractClientPlayer player, Pose rig, Matrix4f view, boolean left, PoseStack pose, SubmitNodeCollector collector, int light) {
		if (rig.handR < 0 || rig.lowerArmR < 0) {
			return;
		}
		// The fist wraps the grip: end the arm between the wrist bone and the weapon bone.
		Vector3f wrist = view.transformPosition(rig.vm[rig.handR].getTranslation(new Vector3f()));
		Vector3f grip = rig.wpn >= 0 ? view.transformPosition(rig.vm[rig.wpn].getTranslation(new Vector3f())) : new Vector3f(wrist);
		Vector3f hand = new Vector3f(wrist).lerp(grip, 0.75f);
		Vector3f elbow = view.transformPosition(rig.vm[rig.lowerArmR].getTranslation(new Vector3f()));
		Vector3f y = new Vector3f(hand).sub(elbow);
		if (y.lengthSquared() < 1e-6f) {
			return;
		}
		y.normalize();
		// Roll: follow the hand bone so the arm turns with the wrist during inspects.
		Vector3f ref = rig.vm[rig.handR].transformDirection(new Vector3f(0f, 0f, 1f));
		view.transformDirection(ref);
		Vector3f z = new Vector3f(ref).sub(new Vector3f(y).mul(ref.dot(y)));
		if (z.lengthSquared() < 1e-6f) {
			z.set(0f, 0f, 1f).sub(new Vector3f(y).mul(y.z));
		}
		z.normalize();
		Vector3f x = new Vector3f(y).cross(z);
		Matrix4f m = new Matrix4f(
			x.x, x.y, x.z, 0f,
			y.x, y.y, y.z, 0f,
			z.x, z.y, z.z, 0f,
			hand.x, hand.y, hand.z, 1f
		);
		// Arm model space: the cube spans y 0..12 px with its hand end at y = 12, centred on x = -6 px
		// (right arm). Slimmer than vanilla to match CS2's forearm, longer so it leaves the screen.
		float sx = left ? 6f / 16f : -6f / 16f;
		// The arm runs back 1.6 x 11 px from the hand, normally ending off screen. In a few swing
		// frames (light attacks) the hand pushes forward far enough that the arm's back end comes
		// into view at the bottom right; lengthen it for those frames until the end is off screen.
		float stretch = 1.6f;
		float perPx = 11f / 16f;
		for (int i = 0; i < 24 && backOnScreen(hand, y, stretch * perPx); i++) {
			stretch += 0.2f;
		}
		m.scale(0.5f, stretch, 0.5f);
		m.translate(-sx, -12f / 16f + 1f / 16f, 0f);
		pose.pushPose();
		pose.last().pose().mul(m);
		pose.last().normal().mul(new org.joml.Matrix3f(m).normal());
		AvatarRenderer<AbstractClientPlayer> renderer = (AvatarRenderer<AbstractClientPlayer>) Minecraft.getInstance()
			.getEntityRenderDispatcher().getPlayerRenderer(player);
		var skin = player.getSkin().body().texturePath();
		if (left) {
			renderer.renderLeftHand(pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
		} else {
			renderer.renderRightHand(pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
		}
		pose.popPose();
	}

	/** Whether the point reach behind the hand along the arm is in front of the camera and on screen (generous bounds). */
	private static boolean backOnScreen(Vector3f hand, Vector3f y, float reach) {
		float bx = hand.x - y.x * reach;
		float by = hand.y - y.y * reach;
		float bz = hand.z - y.z * reach;
		if (bz > -0.05f) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		float tan = (float) Math.tan(Math.toRadians(mc.options.fov().get() * 0.5 + 10));
		float aspect = Math.max(1f, mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight()));
		float margin = 0.12f;
		return Math.abs(bx) < -bz * tan * aspect + margin && Math.abs(by) < -bz * tan + margin;
	}
}
