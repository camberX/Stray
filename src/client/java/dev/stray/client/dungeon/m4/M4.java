package dev.stray.client.dungeon.m4;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DevMode;
import dev.stray.client.dungeon.DungeonParty;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared M4 (Thorn) state, ported from BirdAddon by BarefootBird (BSD 3-Clause, see
 * THIRD_PARTY_NOTICES.md): boss lifecycle, the boss timer in server ticks, Spirit Bear
 * spawn / kill times, the Spirit Bow, mob kills and overkill, the mobs in the arena, the
 * bear particle trail prediction, and the cgm4 / M4Miku practice islands.
 *
 * <p>A server tick is a {@code ClientboundPingPacket} with a non-zero id, as in Odin, which
 * BirdAddon is built on. Packets are handled on the render thread, before vanilla's own
 * handling of the same packet.
 */
public final class M4 {
	public static final List<Class<? extends Entity>> ANIMALS = List.of(Cow.class, Sheep.class, Chicken.class, Bat.class, Wolf.class, Rabbit.class);

	private static final Pattern ENTERED = Pattern.compile("^\\[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!$");
	private static final Pattern BEAR_SPAWN = Pattern.compile("^A Spirit Bear has appeared!$");
	private static final Pattern BEAR_KILL = Pattern.compile("^The Spirit Bow has dropped!$");
	private static final Pattern BOW_PICKUP_SUBTITLE = Pattern.compile("^.* picked up the Spirit Bow!$");
	private static final Pattern END = Pattern.compile("^\\s*☠ Defeated Thorn in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$");

	private static final AABB ARENA = new AABB(-36.0, 68.0, -36.0, 47.0, 110.0, 47.0);
	private static final BlockPos LAST_RING_BLOCK = new BlockPos(7, 77, 34);

	// ---------------------------------------------------------------- lifecycle and timers

	public static boolean thornDialogueStarted;
	public static boolean ended;
	/** -1 not spawning, 0 alive, 1+ ticks until the bear spawns. */
	public static int bearTimer = -1;
	/** Server ticks since Thorn's first line. */
	public static int timer;

	// ---------------------------------------------------------------- bears

	public static final List<Integer> spawnTimes = new ArrayList<>();
	public static final List<Integer> killTimes = new ArrayList<>();
	public static final List<Integer> spawnStartTimes = new ArrayList<>();
	/** Roughly where the bear spawns, 0.85 above the middle of the arena. */
	public static Vec3 spawnSpot = new Vec3(5.5, 69.85, 5.5);

	// ---------------------------------------------------------------- bow

	public static Integer bowPickup;
	public static Integer bowDraw;
	public static Integer bowRelease;
	private static boolean bowDrawn;

	// ---------------------------------------------------------------- kills

	public static int kills;
	public static final Map<Class<? extends Entity>, Integer> overkill = new LinkedHashMap<>();

	// ---------------------------------------------------------------- mobs

	private static Map<Class<? extends Entity>, List<Entity>> animals = Map.of();
	public static List<ArmorStand> bows = List.of();
	public static Ghast thorn;
	public static Player bear;

	// ---------------------------------------------------------------- islands

	public static boolean onCgm4;
	public static boolean onM4Miku;

	static {
		resetOverkill();
	}

	private M4() {
	}

	// ---------------------------------------------------------------- queries

	public static M4Config config() {
		StrayConfig config = StrayConfig.get();
		if (config.m4 == null) {
			config.m4 = new M4Config();
		}
		return config.m4;
	}

	/** In the F4 / M4 boss room (or Stray's dev mode). */
	public static boolean inBoss() {
		return DevMode.on() || SkyblockLocation.inDungeon && SkyblockLocation.inBoss && SkyblockLocation.dungeonFloor == 4;
	}

	public static boolean master() {
		return SkyblockLocation.dungeonMaster;
	}

	public static int maxKills() {
		return master() ? 30 : 25;
	}

	public static DungeonParty.DungeonClass myClass() {
		DungeonParty.Member self = DungeonParty.self();
		return self == null || self.clazz == null ? DungeonParty.DungeonClass.EMPTY : self.clazz;
	}

	public static boolean spawning() {
		return spawnStartTimes.size() > spawnTimes.size();
	}

	public static boolean spawned() {
		return spawnTimes.size() > killTimes.size();
	}

	public static List<Entity> animalsOf(Class<? extends Entity> type) {
		List<Entity> list = animals.get(type);
		return list == null ? List.of() : list;
	}

	public static List<Entity> allAnimals() {
		List<Entity> out = new ArrayList<>();
		for (Class<? extends Entity> type : ANIMALS) {
			out.addAll(animalsOf(type));
		}
		return out;
	}

	public static Class<? extends Entity> animalType(Entity entity) {
		for (Class<? extends Entity> type : ANIMALS) {
			if (type.isInstance(entity)) {
				return type;
			}
		}
		return null;
	}

	public static String mobName(Class<? extends Entity> type, boolean shortName) {
		if (type == Bat.class) {
			return shortName ? "B" : "Bats";
		}
		if (type == Chicken.class) {
			return shortName ? "Ch" : "Chickens";
		}
		if (type == Cow.class) {
			return shortName ? "C" : "Cows";
		}
		if (type == Sheep.class) {
			return shortName ? "S" : "Sheep";
		}
		if (type == Rabbit.class) {
			return shortName ? "R" : "Rabbits";
		}
		if (type == Wolf.class) {
			return shortName ? "W" : "Wolves";
		}
		return "N/A";
	}

	/** BirdAddon's default highlight colours (Minecraft palette), ARGB. */
	public static int mobColor(Class<? extends Entity> type) {
		if (type == Cow.class) {
			return 0xFF55FFFF;
		}
		if (type == Sheep.class) {
			return 0xFFFFFF55;
		}
		if (type == Chicken.class) {
			return 0xFFFF5555;
		}
		if (type == Bat.class) {
			return 0xFF0000AA;
		}
		if (type == Wolf.class) {
			return 0xFFFFAA00;
		}
		if (type == Rabbit.class) {
			return 0xFF00AA00;
		}
		return 0xFFFFFFFF;
	}

	public static final int THORN_COLOR = 0xFFFFFFFF;
	public static final int THORN_DAMAGED_COLOR = 0xFFFF5555;
	public static final int BEAR_COLOR = 0xFFAA00AA;

	// ---------------------------------------------------------------- events

	private static void bearSpawnStart() {
		if (spawning()) {
			return;
		}
		bearTimer = 70;
		spawnStartTimes.add(timer);
		M4Titles.onBearSpawnStart();
	}

	private static void bearSpawn() {
		spawnTimes.add(timer);
		M4Titles.onBearSpawn();
		M4Stats.onBearSpawn();
	}

	private static void bearKill() {
		bearTimer = -1;
		kills = 0;
		killTimes.add(timer);
		M4Stats.onBearKill();
		M4Titles.onBearKill();
	}

	private static void end() {
		ended = true;
		M4Stats.onEnd();
	}

	// ---------------------------------------------------------------- input

	/** One Hypixel server tick. */
	public static void serverTick() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		// Timers
		if (thornDialogueStarted && !ended) {
			if (spawning()) {
				if (config().bearExperimental && M4Particles.predictionsRemaining > 0) {
					bearTimer = M4Particles.predictionsRemaining - 1;
				} else {
					bearTimer--;
				}
				if (!M4Particles.particleAddedThisTick && M4Particles.predictionsRemaining > -1) {
					M4Particles.predictionsRemaining--;
				}
				M4Particles.particleAddedThisTick = false;
			}
			timer++;
		}
		scanMobs(client.level);
		checkIslands(client.level);
		M4Particles.serverTick();
		if (inBoss()) {
			trackBow(client.level);
		}
		updateSpawnSpot();
		M4Features.serverTick(client);
	}

	/** A chat line, colour codes removed. Returns false to hide it. */
	public static boolean onChat(String message) {
		if (message == null) {
			return true;
		}
		if (ENTERED.matcher(message).matches()) {
			thornDialogueStarted = true;
		}
		if (inBoss()) {
			if (BEAR_SPAWN.matcher(message).matches()) {
				bearSpawn();
			}
			if (BEAR_KILL.matcher(message).matches() && spawned()) {
				bearKill();
			}
			if (!ended && END.matcher(message).matches()) {
				end();
			}
		}
		M4Titles.onChat(message);
		return M4Features.allowChat(message);
	}

	/** A subtitle from the server. Returns false to hide it. */
	public static boolean onSubtitle(String plain) {
		if (plain != null && BOW_PICKUP_SUBTITLE.matcher(plain).matches()) {
			bowPickup = timer;
		}
		return M4Titles.allowServerTitle();
	}

	public static void onEntityDeath(Entity entity) {
		if (!inBoss() || entity == null) {
			return;
		}
		Class<? extends Entity> type = animalType(entity);
		if (type == null) {
			return;
		}
		if (!spawning() && !spawned()) {
			kills++;
			if (kills >= maxKills()) {
				bearSpawnStart();
			}
		} else {
			overkill.merge(type, 1, Integer::sum);
		}
	}

	public static void onHurt(Entity entity) {
		if (!inBoss() || !(entity instanceof Ghast)) {
			return;
		}
		M4Features.onThornHurt();
	}

	/** Coal turning into sea lanterns around the arena counts kills too. */
	public static void onBlockUpdate(BlockPos pos, BlockState old, BlockState updated) {
		if (!inBoss()) {
			return;
		}
		List<BlockPos> ring = master() ? M4_RING : F4_RING;
		int index = ring.indexOf(pos);
		if (index < 0) {
			return;
		}
		if (updated.getBlock() == Blocks.SEA_LANTERN && old.getBlock() == Blocks.COAL_BLOCK) {
			if (kills < maxKills() && index + 1 > kills) {
				kills = index + 1;
			}
			if (pos.equals(LAST_RING_BLOCK)) {
				bearSpawnStart();
			}
		}
	}

	public static void onParticle(ParticleOptions options, double x, double y, double z) {
		if (!inBoss() || !spawning() || !(options instanceof DustParticleOptions dust)) {
			return;
		}
		Vector3f color = dust.getColor();
		float bear = 0.09803922f;
		if (color.x() != bear || color.y() != bear || color.z() != bear) {
			return;
		}
		M4Particles.onBearParticle(new Vec3(x, y, z));
	}

	public static void onWorldLoad() {
		thornDialogueStarted = false;
		ended = false;
		bearTimer = -1;
		timer = 0;
		spawnTimes.clear();
		killTimes.clear();
		spawnStartTimes.clear();
		bowPickup = null;
		bowDraw = null;
		bowRelease = null;
		bowDrawn = false;
		kills = 0;
		resetOverkill();
		animals = Map.of();
		bows = List.of();
		thorn = null;
		bear = null;
		onCgm4 = false;
		onM4Miku = false;
		M4Particles.reset();
		M4Features.onWorldLoad();
	}

	// ---------------------------------------------------------------- trackers

	private static void resetOverkill() {
		overkill.clear();
		overkill.put(Bat.class, 0);
		overkill.put(Chicken.class, 0);
		overkill.put(Cow.class, 0);
		overkill.put(Sheep.class, 0);
		overkill.put(Rabbit.class, 0);
		overkill.put(Wolf.class, 0);
	}

	private static void scanMobs(ClientLevel level) {
		if (!inBoss()) {
			return;
		}
		Map<Class<? extends Entity>, List<Entity>> found = new LinkedHashMap<>();
		for (Class<? extends Entity> type : ANIMALS) {
			found.put(type, new ArrayList<>());
		}
		List<ArmorStand> bowsNew = new ArrayList<>();
		Ghast thornNew = null;
		Player bearNew = null;
		for (Entity entity : level.getEntities((Entity) null, ARENA, e -> true)) {
			if (!entity.isAlive()) {
				continue;
			}
			if (entity instanceof ArmorStand stand && stand.getName().getString().equals("Spirit Bow")) {
				bowsNew.add(stand);
			} else if (entity instanceof Ghast ghast) {
				thornNew = ghast;
			} else if (entity instanceof Player player && "Spirit Bear".equals(player.getGameProfile().name())) {
				bearNew = player;
			}
			Class<? extends Entity> type = animalType(entity);
			if (type == null || entity instanceof Bat && entity.isInvisible()) {
				continue;
			}
			found.get(type).add(entity);
		}
		for (Map.Entry<Class<? extends Entity>, List<Entity>> entry : found.entrySet()) {
			entry.setValue(Collections.unmodifiableList(entry.getValue()));
		}
		animals = found;
		bows = bowsNew;
		thorn = thornNew;
		bear = bearNew;
	}

	private static void trackBow(ClientLevel level) {
		boolean drawing = false;
		for (Player player : level.players()) {
			if (player != null && player.isUsingItem()
				&& player.getItemInHand(InteractionHand.MAIN_HAND).getHoverName().getString().contains("Spirit Bow")) {
				drawing = true;
				break;
			}
		}
		if (drawing) {
			if (!bowDrawn) {
				bowDraw = timer;
			}
			bowDrawn = true;
		} else {
			if (bowDrawn) {
				bowRelease = timer - 1;
			}
			bowDrawn = false;
		}
	}

	private static void updateSpawnSpot() {
		if (!spawning()) {
			return;
		}
		if (M4Particles.spawnPrediction.y != 0.0 && config().bearExperimental) {
			spawnSpot = M4Particles.spawnPrediction;
			return;
		}
		if (thorn != null) {
			// The bear spawns on a circle of radius 0.7 around (5.5, 5.5), at the point
			// closest to where Thorn was when it started spawning.
			double angle = Math.atan2(thorn.getZ() - 5.5, thorn.getX() - 5.5);
			spawnSpot = new Vec3(5.5 + 0.7 * Math.cos(angle), 69.85, 5.5 + 0.7 * Math.sin(angle));
		}
	}

	// ---------------------------------------------------------------- islands

	private static final String[] MIKU = {"##@##", "##@@#", "#@.#@", "@@###", "@@###"};
	private static final String[] CGM4 = {"##@@#", "%#@@#", "#%.#@", "@@@@@", "@@##@"};

	private static void checkIslands(ClientLevel level) {
		if (!SkyblockLocation.area.contains("Private Island") || onCgm4 || onM4Miku) {
			return;
		}
		if (pattern(level, new BlockPos(7, 68, 7), MIKU)) {
			onM4Miku = true;
		}
		if (pattern(level, new BlockPos(9, 109, 9), CGM4)) {
			onCgm4 = true;
		}
	}

	private static boolean pattern(ClientLevel level, BlockPos start, String[] rows) {
		for (int row = 0; row < rows.length; row++) {
			for (int col = 0; col < rows[row].length(); col++) {
				Block expected = switch (rows[row].charAt(col)) {
					case '#' -> Blocks.COARSE_DIRT;
					case '@' -> Blocks.DIRT;
					case '.' -> Blocks.STAINED_GLASS.brown();
					default -> null;
				};
				if (expected == null) {
					continue;
				}
				BlockPos pos = new BlockPos(start.getX() - col, start.getY(), start.getZ() - row);
				if (level.getBlockState(pos).getBlock() != expected) {
					return false;
				}
			}
		}
		return true;
	}

	// One block of the coal / sea lantern ring per kill.
	private static final List<BlockPos> F4_RING = List.of(
		new BlockPos(-3, 77, 33), new BlockPos(-9, 77, 31), new BlockPos(-16, 77, 26), new BlockPos(-20, 77, 20), new BlockPos(-23, 77, 13),
		new BlockPos(-24, 77, 6), new BlockPos(-24, 77, 0), new BlockPos(-22, 77, -7), new BlockPos(-18, 77, -13), new BlockPos(-12, 77, -19),
		new BlockPos(-5, 77, -22), new BlockPos(1, 77, -24), new BlockPos(8, 77, -24), new BlockPos(14, 77, -23), new BlockPos(21, 77, -19),
		new BlockPos(27, 77, -14), new BlockPos(31, 77, -8), new BlockPos(33, 77, -1), new BlockPos(34, 77, 5), new BlockPos(33, 77, 12),
		new BlockPos(31, 77, 19), new BlockPos(27, 77, 25), new BlockPos(20, 77, 30), new BlockPos(14, 77, 33), new BlockPos(7, 77, 34)
	);

	private static final List<BlockPos> M4_RING = List.of(
		new BlockPos(-2, 77, 33), new BlockPos(-7, 77, 32), new BlockPos(-13, 77, 28), new BlockPos(-17, 77, 24), new BlockPos(-21, 77, 18),
		new BlockPos(-23, 77, 13), new BlockPos(-24, 77, 7), new BlockPos(-24, 77, 2), new BlockPos(-23, 77, -4), new BlockPos(-21, 77, -9),
		new BlockPos(-17, 77, -14), new BlockPos(-12, 77, -19), new BlockPos(-6, 77, -22), new BlockPos(-1, 77, -23), new BlockPos(5, 77, -24),
		new BlockPos(10, 77, -24), new BlockPos(16, 77, -22), new BlockPos(21, 77, -19), new BlockPos(27, 77, -15), new BlockPos(30, 77, -10),
		new BlockPos(32, 77, -5), new BlockPos(34, 77, 1), new BlockPos(34, 77, 7), new BlockPos(33, 77, 12), new BlockPos(31, 77, 18),
		new BlockPos(28, 77, 23), new BlockPos(23, 77, 28), new BlockPos(18, 77, 31), new BlockPos(12, 77, 33), new BlockPos(7, 77, 34)
	);
}
