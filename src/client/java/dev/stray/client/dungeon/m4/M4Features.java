package dev.stray.client.dungeon.m4;

import dev.stray.client.dungeon.DungeonParty.DungeonClass;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Per-feature logic of BirdAddon's M4 modules (Stun, Highlight's damage flash, Decoy, Tac,
 * Trajectories, Rabbits, Hide Messages, Sounds and Render Optimizer). Drawing is in
 * {@link M4World} and {@link M4Hud}.
 */
public final class M4Features {
	private M4Features() {
	}

	private static M4Config config() {
		return M4.config();
	}

	// ================================================================ server tick

	static void serverTick(Minecraft client) {
		M4Config config = config();
		tacTick();
		if (!M4.inBoss()) {
			return;
		}
		if (config.stun) {
			stunTick();
		}
		if (config.highlight && damaged > -1) {
			damaged--;
		}
		M4Titles.serverTick(client);
		if (config.decoy || config.renderOptimizer) {
			scanNpcs(client);
		}
		if (config.trajectories) {
			trajectories();
		}
		if (config.rabbits) {
			countHutches();
		}
	}

	public static void clientTick() {
		M4Stats.clientTick();
	}

	static void onWorldLoad() {
		stunTimer = 230;
		firstStunTimer = 230;
		secondStunTimer = -1;
		secondStunStarted = false;
		damaged = -1;
		bestSpotIndex = 100;
		bestSpot = null;
		caveSpot = null;
		npcs.clear();
		cowLandings = List.of();
		sheepLandings = List.of();
		rabbitCounts = new int[HUTCHES.length];
		M4Titles.onWorldLoad();
		M4Stats.onWorldLoad();
		M4Waypoints.ensureLoaded();
	}

	// ================================================================ stun

	static int stunTimer = 230;
	static int firstStunTimer = 230;
	static int secondStunTimer = -1;
	private static boolean secondStunStarted;
	static final Vec3 FIRST_STUN = new Vec3(1.5, 86.0, 28.5);
	static final Vec3 SECOND_STUN = new Vec3(25.5, 85.0, 20.5);

	static boolean stunShown() {
		return config().stun && M4.inBoss() && (!config().stunOnlyHealer || M4.myClass() == DungeonClass.HEALER);
	}

	private static void stunTick() {
		if (stunTimer > -1) {
			stunTimer--;
		}
		if (firstStunTimer > -1) {
			firstStunTimer--;
		}
		if (secondStunTimer > -1) {
			secondStunTimer--;
		} else if (M4.thorn != null) {
			// Thorn starts at angle 0 and moves to negative angles before the first stun; the
			// second stun is timed from when it comes back past 0.
			double angle = Math.atan2(M4.thorn.getX() - 5.5, M4.thorn.getZ() - 5.5);
			if (angle > 0 && !secondStunStarted) {
				secondStunTimer = 28 + config().stunSecondOffset;
				secondStunStarted = true;
			}
		}
	}

	// ================================================================ highlight

	/** Ticks left of Thorn's damage flash. */
	static int damaged = -1;

	static void onThornHurt() {
		if (config().stun) {
			stunTimer = 82;
		}
		if (config().highlight) {
			damaged = config().highlightFlashTicks;
		}
	}

	// ================================================================ decoy and NPCs

	private static final AABB NPC_BOX = new AABB(-36.0, 77.0, -36.0, 47.0, 83.0, 47.0);
	private static final double[][] BEST_SPOTS = {
		{26, 29}, {29, 26}, {31, 28}, {27, 32}, // front 4
		{21, 31}, {32, 21}, {19, 33}, {33, 19}, // side 4
		{30, 32}, {33, 30}, {27, 35}, // mid left 3
		{32, 34}, {35, 31}, {29, 36}, // back left 3
		{34, 24}, {35, 27}, {37, 27}, // right 3
		{27, 38}, {24, 37}, {21, 36}, {24, 40} // extra left ones just in case
	};
	private static final Map<Integer, Vec3> CAVE_SPOTS = Map.of(
		0, new Vec3(28.0, 75.5, 28.0),
		1, new Vec3(28.0, 75.5, 28.0),
		2, new Vec3(29.0, 75.5, 29.0),
		3, new Vec3(29.0, 75.5, 29.0),
		4, new Vec3(20.0, 76.5, 30.0),
		5, new Vec3(29.0, 76.5, 22.0)
	);
	private static final Set<String> NPC_NAMES = Set.of("Lost Adventurer", "Crypt Souleater", "Crypt Dreadlord", "Diamond Guy");

	static final Set<Entity> npcs = Collections.newSetFromMap(new java.util.WeakHashMap<>());
	private static int bestSpotIndex = 100;
	static Vec3 bestSpot;
	static Vec3 caveSpot;

	private static void scanNpcs(Minecraft client) {
		if (client.level == null) {
			return;
		}
		for (Entity entity : client.level.getEntities((Entity) null, NPC_BOX, e -> true)) {
			boolean npc = entity instanceof Zombie || entity instanceof Skeleton || entity instanceof WitherSkeleton
				|| entity instanceof Player player && NPC_NAMES.contains(player.getGameProfile().name());
			if (npc) {
				addNpc(entity);
			}
		}
	}

	private static void addNpc(Entity entity) {
		npcs.add(entity);
		if (!config().decoy || !config().decoyBestSpot) {
			return;
		}
		for (int i = 0; i < BEST_SPOTS.length && i < bestSpotIndex; i++) {
			if (BEST_SPOTS[i][0] + 0.5 == entity.getX() && BEST_SPOTS[i][1] + 0.5 == entity.getZ()) {
				bestSpotIndex = i;
				bestSpot = entity.position();
				caveSpot = CAVE_SPOTS.get(i);
				return;
			}
		}
	}

	/** NPCs in the corner Thorn sits over are the ones that matter. */
	static boolean relevantNpc(Entity entity) {
		return entity.getX() > 17 && entity.getZ() > 17;
	}

	// ================================================================ tac

	static int tacTime = -1;

	private static void tacTick() {
		if (tacTime >= 0) {
			tacTime--;
		}
	}

	/** The player used an item (sent to the server). */
	public static void onUseItem() {
		M4Config config = config();
		Minecraft client = Minecraft.getInstance();
		if (!config.tac || client.player == null) {
			return;
		}
		ItemStack held = client.player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!held.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains("tactical insertion")) {
			return;
		}
		if (config.tacTimer && tacTime == -1) {
			tacTime = 60;
		}
		if (config.tacPrint && M4.spawning() && M4.inBoss()
			&& (M4.myClass() == DungeonClass.TANK || !config.tacPrintOnlyTank)) {
			String at = M4Stats.fixed(M4.bearTimer / 20.0, 2);
			if (config.tacPrintParty) {
				client.player.connection.sendCommand("pc Stray > Tacced at " + at + "s");
			} else {
				M4Stats.message("Tacced at " + at + "s");
			}
		}
	}

	// ================================================================ trajectories

	static List<Vec3> cowLandings = List.of();
	static List<Vec3> sheepLandings = List.of();

	private static void trajectories() {
		cowLandings = config().trajectoryCows ? landings(Cow.class) : List.of();
		sheepLandings = config().trajectorySheep ? landings(Sheep.class) : List.of();
	}

	private static List<Vec3> landings(Class<? extends Entity> type) {
		List<Vec3> out = new ArrayList<>();
		for (Entity entity : M4.animalsOf(type)) {
			if (entity.getY() > 71.5) {
				Vec3 landing = predictLanding(entity.position(), entity.getDeltaMovement());
				if (landing != null) {
					out.add(landing);
				}
			}
		}
		return out;
	}

	/** Vanilla physics until y = 69, where the arena floor mostly is. */
	private static Vec3 predictLanding(Vec3 pos, Vec3 velocity) {
		double x = pos.x;
		double y = pos.y;
		double z = pos.z;
		double vx = velocity.x;
		double vy = velocity.y;
		double vz = velocity.z;
		for (int i = 0; i < 100; i++) {
			x += vx;
			y += vy;
			z += vz;
			vx *= 0.98;
			vz *= 0.98;
			vy = (vy - 0.08) * 0.98;
			if (y <= 69.0) {
				return new Vec3(x, 69.0, z);
			}
		}
		return null;
	}

	static double groundY(double x, double z) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return 69.0;
		}
		int bx = (int) x;
		int bz = (int) z;
		for (int y = 100; y >= 69; y--) {
			BlockState state = client.level.getBlockState(new BlockPos(bx, y, bz));
			if (!state.isAir() && state.isSolid()) {
				return y + 1.0;
			}
		}
		return 69.0;
	}

	// ================================================================ rabbits

	static final int RABBIT_SPAWN_TIME = 630;
	static final Vec3[] HUTCHES = {
		new Vec3(16.5, 71.5, -21.5), new Vec3(32.5, 71.5, -5.5), new Vec3(32.5, 71.5, 16.5), new Vec3(16.5, 71.5, 32.5),
		new Vec3(-5.5, 71.5, 32.5), new Vec3(-21.5, 71.5, 16.5), new Vec3(-21.5, 71.5, -5.5), new Vec3(-5.5, 71.5, -21.5)
	};
	static int[] rabbitCounts = new int[HUTCHES.length];

	private static void countHutches() {
		int[] counts = new int[HUTCHES.length];
		for (Entity rabbit : M4.animalsOf(Rabbit.class)) {
			for (int i = 0; i < HUTCHES.length; i++) {
				Vec3 hutch = HUTCHES[i];
				if (rabbit.getX() < hutch.x + 0.5 && rabbit.getX() > hutch.x - 0.5 && rabbit.getZ() < hutch.z + 0.5 && rabbit.getZ() > hutch.z - 0.5) {
					counts[i]++;
				}
			}
		}
		rabbitCounts = counts;
	}

	// ================================================================ hide messages

	private static final Pattern CHICKEN_MINE = Pattern.compile("^A Chicken Mine exploded, hitting you for [\\d,]+(?:\\.\\d+)? damage\\.$");
	private static final Pattern CROWD = Pattern.compile("^\\[CROWD] [^:]+: (.+)$");
	private static final Pattern THORN = Pattern.compile("^\\[BOSS] Thorn: .+$");
	private static final Pattern BEAR_SPAWN = Pattern.compile("^A Spirit Bear has appeared!$");
	private static final Pattern BOW_DROP = Pattern.compile("^The Spirit Bow has dropped!$");
	private static final Pattern BOW_PICKUP = Pattern.compile("^You picked up the Spirit Bow! Use it to attack Thorn!$");
	private static final Pattern CHICKEN_LIGHTNING = Pattern.compile("^The Spirit Chicken's lightning struck you for [\\d,]+(?:\\.\\d+)? damage\\.$");
	private static final Pattern SHEEP_EXPLOSION = Pattern.compile("^A Spirit Sheep exploded, hitting you for [\\d,]+(?:\\.\\d+)? damage\\.$");
	private static final Pattern BOW_SHOT = Pattern.compile("^The Spirit Bow disintegrates as you fire off the shot!$");

	static boolean allowChat(String message) {
		M4Config c = config();
		if (!c.hideMessages || !M4.inBoss()) {
			return true;
		}
		return !(c.hideChickenMines && CHICKEN_MINE.matcher(message).matches()
			|| c.hideCrowd && CROWD.matcher(message).matches()
			|| c.hideThorn && THORN.matcher(message).matches()
			|| c.hideBearSpawn && BEAR_SPAWN.matcher(message).matches()
			|| c.hideBowDrop && BOW_DROP.matcher(message).matches()
			|| c.hideBowPickup && BOW_PICKUP.matcher(message).matches()
			|| c.hideChickenLightning && CHICKEN_LIGHTNING.matcher(message).matches()
			|| c.hideSheepExplosion && SHEEP_EXPLOSION.matcher(message).matches()
			|| c.hideBowShot && BOW_SHOT.matcher(message).matches());
	}

	// ================================================================ hide entities and particles

	public static boolean hideEntity(Entity entity) {
		M4Config c = config();
		if (!(c.highlight || c.renderOptimizer) || !M4.inBoss()) {
			return false;
		}
		if (c.renderOptimizer) {
			// Invisible armor stands, except the ones holding the bow / tribal spear.
			if (c.hideInvisArmorStands && entity instanceof ArmorStand stand && stand.isInvisible()
				&& stand.getMainHandItem().isEmpty() && !M4.ended) {
				return true;
			}
			if (npcs.contains(entity) && (c.npcVisibility == 2 || c.npcVisibility == 1 && !relevantNpc(entity))) {
				return true;
			}
		}
		if (c.highlight) {
			if (c.highlightHideNametags && entity instanceof ArmorStand && entity.getName().getString().contains("❤")) {
				return true;
			}
			if (c.highlightHideMobs) {
				return entity instanceof Bat && c.highlightBats
					|| entity instanceof Chicken && c.highlightChickens
					|| entity instanceof Rabbit && c.highlightRabbits
					|| entity instanceof Sheep && c.highlightSheep
					|| entity instanceof Cow && c.highlightCows
					|| entity instanceof Wolf && c.highlightWolves
					|| entity instanceof Ghast && c.highlightThorn;
			}
		}
		return false;
	}

	public static boolean hideParticle(ParticleOptions options) {
		M4Config c = config();
		if (!c.renderOptimizer || !c.hideParticles || !M4.inBoss()) {
			return false;
		}
		var type = options.getType();
		if (type == ParticleTypes.HAPPY_VILLAGER && c.keepHealerCircle) {
			return false;
		}
		return !(type == ParticleTypes.FIREWORK && c.keepMageBeam);
	}

	// ================================================================ sounds

	private static final Set<String> ANIMAL_SOUNDS = Set.of(
		"minecraft:entity.bat.death", "minecraft:entity.bat.hurt", "minecraft:entity.bat.loop", "minecraft:entity.bat.takeoff", "minecraft:entity.bat.ambient",
		"minecraft:entity.chicken.death", "minecraft:entity.chicken.hurt", "minecraft:entity.chicken.step", "minecraft:entity.chicken.egg", "minecraft:entity.chicken.ambient",
		"minecraft:entity.cow.death", "minecraft:entity.cow.hurt", "minecraft:entity.cow.ambient", "minecraft:entity.cow.step",
		"minecraft:entity.rabbit.attack", "minecraft:entity.rabbit.death", "minecraft:entity.rabbit.hurt", "minecraft:entity.rabbit.jump", "minecraft:entity.rabbit.ambient",
		"minecraft:entity.sheep.death", "minecraft:entity.sheep.hurt", "minecraft:entity.sheep.step", "minecraft:entity.sheep.ambient",
		"minecraft:entity.wolf.death", "minecraft:entity.wolf.growl", "minecraft:entity.wolf.howl", "minecraft:entity.wolf.hurt", "minecraft:entity.wolf.pant",
		"minecraft:entity.wolf.shake", "minecraft:entity.wolf.whine", "minecraft:entity.wolf.ambient", "minecraft:entity.wolf.step"
	);
	private static final Set<String> GHAST_SOUNDS = Set.of(
		"minecraft:entity.ghast.death", "minecraft:entity.ghast.hurt", "minecraft:entity.ghast.scream", "minecraft:entity.ghast.warn", "minecraft:entity.ghast.ambient"
	);
	private static final Set<String> MISC_SOUNDS = Set.of(
		"minecraft:block.fire.ambient", "minecraft:block.grass.step", "minecraft:block.glass.step", "minecraft:block.gravel.step",
		"minecraft:block.stone.step", "minecraft:block.wood.step", "minecraft:block.wool.step", "minecraft:entity.skeleton.hurt",
		"minecraft:entity.zombie.hurt", "minecraft:entity.zombie.infect", "minecraft:entity.lightning_bolt.thunder", "minecraft:entity.generic.explode"
	);

	public static boolean muteSound(String id) {
		M4Config c = config();
		if (!c.sounds || !M4.inBoss()) {
			return false;
		}
		return c.muteAnimals && ANIMAL_SOUNDS.contains(id)
			|| c.muteThorn && GHAST_SOUNDS.contains(id)
			|| c.muteMisc && MISC_SOUNDS.contains(id);
	}
}
