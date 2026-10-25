package dev.stray.client.slayer;

import dev.stray.client.config.SlayerSettings;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.farming.PestEsp;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.profile.ProfileViewer;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.NametagRenderer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Slayer things drawn in the world: glow on mini-bosses, egg sacs, beacons, Nukekubi skulls,
 * Bloodfiend bosses, ichors and springs, the own boss (hellion shield / invincible colours),
 * lines from the crosshair to them, and labels for beacons, skulls, ichors and valuable drops.
 */
public final class SlayerWorld {
	private record Line(Entity entity, Vec3 fixed, int rgb, double up) {
	}

	private record Label(Vec3 at, String text, int rgb, boolean through) {
	}

	/** A see-through box around a mob with an optional text above it, following the mob. */
	private record Fill(Entity entity, int rgb, String text) {
	}

	private static volatile List<Fill> fills = List.of();
	private static volatile List<Fill> texts = List.of();
	private static final Pattern ZERO_HP = Pattern.compile("(?:^|\\s)0(?:/[\\d.,]+[kKmMbB]?)?\\s*❤");
	private static final Pattern SHIELD_HITS = Pattern.compile("♨\\s*(\\d+)");

	private static final String NUKEKUBI = "eb07594e2df273921a77c101d0bfdfa1115abed5b9b2029eb496ceba9bdbb4b3";
	private static final String ICHOR = "c0340923a6de4825a176813d133503eff186db0896e32b6704928c2a2bf68422";
	private static final String SPRING = "77f7a7bc8ac86f23ca7bf98afeb76960227e1832fe209a3026f6ceb8bde74f54";
	private static final Pattern EGG_TIMER = Pattern.compile("^\\d+s \\d+/\\d+$");
	private static final Pattern SPAWNED_BY = Pattern.compile("Spawned by: (\\w+)");

	private static volatile Int2IntOpenHashMap glow = new Int2IntOpenHashMap();
	private static volatile List<Line> lines = List.of();
	private static volatile List<Label> labels = List.of();
	private static final IntOpenHashSet TAGGED = new IntOpenHashSet();
	private static final Map<BlockPos, Long> SITTING = new HashMap<>();
	private static final Map<Integer, Vec3> FLYING = new HashMap<>();
	private static final IntOpenHashSet WARNED = new IntOpenHashSet();
	private static long steakTitle;
	private static long clawTitle;
	private static int tick;

	private SlayerWorld() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
	}

	private static SlayerSettings settings() {
		return StrayConfig.get().slayer;
	}

	/** Whether any slayer glow can be on (so the glow pipeline runs). */
	public static boolean glowActive() {
		return !glow.isEmpty();
	}

	public static String debug() {
		Int2IntOpenHashMap map = glow;
		Minecraft client = Minecraft.getInstance();
		StringBuilder out = new StringBuilder("glow=" + map.size() + " (render on=" + dev.stray.client.render.MobGlowRenderer.glowEnabled() + ")");
		int n = 0;
		for (var e : map.int2IntEntrySet()) {
			Entity entity = client.level == null ? null : client.level.getEntity(e.getIntKey());
			out.append(n == 0 ? ": " : ", ").append(entity == null ? "gone" : entity.getType().toShortString())
				.append(String.format(java.util.Locale.ROOT, " #%06X", e.getIntValue() & 0xFFFFFF));
			if (++n >= 6) {
				break;
			}
		}
		out.append(" lines=").append(lines.size());
		return out.toString();
	}

	private static volatile Int2IntOpenHashMap marks = new Int2IntOpenHashMap();

	/**
	 * Outline alpha that tells the glow shader to also fill the mob's shape: 231..234 out of
	 * 255, between normal ESP glow (at most 230) and vanilla glowing (235+), for 20/35/50/65% fill.
	 */
	public static int fillAlpha(float opacity) {
		return 231 + fillLevel(opacity);
	}

	public static int fillLevel(float opacity) {
		return Math.max(0, Math.min(3, Math.round((opacity - 0.2f) / 0.15f)));
	}

	/** ARGB outline for an entity, 0 for none. */
	public static int glow(Entity entity) {
		Int2IntOpenHashMap map = glow;
		if (map.isEmpty() || entity == null) {
			return 0;
		}
		int rgb = map.getOrDefault(entity.getId(), 0);
		return rgb == 0 || dying(entity) ? 0 : rgb;
	}

	/** Dead or playing the death animation (or its tag already reads 0 health). */
	static boolean dying(Entity entity) {
		if (entity.isRemoved()) {
			return true;
		}
		if (entity instanceof LivingEntity living && (living.isDeadOrDying() || living.deathTime > 0)) {
			return true;
		}
		for (String line : SlayerTags.of(entity)) {
			if (ZERO_HP.matcher(line).find()) {
				return true;
			}
		}
		return false;
	}

	public static void onAttack(Entity target) {
		if (target instanceof RemotePlayer && target.getName().getString().trim().equals("Bloodfiend")) {
			String owner = owner(target);
			if (owner != null && !owner.equalsIgnoreCase(SlayerApi.playerName()) && !coop(owner)) {
				TAGGED.add(target.getId());
			}
		}
	}

	public static void reset() {
		glow = new Int2IntOpenHashMap();
		lines = List.of();
		labels = List.of();
		fills = List.of();
		texts = List.of();
		TAGGED.clear();
		SITTING.clear();
		FLYING.clear();
		WARNED.clear();
	}

	// ---------------------------------------------------------------- scan

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null || !SkyblockLocation.inSkyblock) {
			if (!glow.isEmpty() || !lines.isEmpty() || !labels.isEmpty()) {
				reset();
			}
			return;
		}
		if (++tick % 4 != 0) {
			return;
		}
		SlayerSettings s = settings();
		Int2IntOpenHashMap nextGlow = new Int2IntOpenHashMap();
		List<Line> nextLines = new ArrayList<>();
		List<Label> nextLabels = new ArrayList<>();
		SlayerType area = SlayerApi.areaType();
		boolean inArea = area != null;
		AABB around = client.player.getBoundingBox().inflate(32);
		List<Entity> nearby = client.level.getEntities(client.player, around, e -> true);

		LivingEntity boss = SlayerApi.boss();
		SlayerType type = SlayerApi.type();
		if (boss != null && type != null) {
			int rgb = 0;
			if (s.esp && s.invincible && type == SlayerType.TARANTULA && SlayerBosses.stuck()) {
				rgb = s.invincibleRgb;
			} else if (s.inferno && s.shieldColors && type == SlayerType.INFERNO) {
				SlayerBosses.Shield shield = SlayerBosses.bossShield();
				rgb = shield == null ? 0 : shield.rgb;
			}
			if (rgb == 0 && s.esp && s.bossGlow) {
				rgb = s.bossRgb;
			}
			if (rgb != 0) {
				nextGlow.put(boss.getId(), 0xFF000000 | rgb);
			}
			if (s.esp && s.bossLine && (type == SlayerType.VOID || type == SlayerType.TARANTULA) && boss.distanceTo(client.player) < 30) {
				nextLines.add(new Line(boss, null, s.bossLineRgb, 1.0));
			}
		}

		// Hellion shields: colour every Inferno boss / demon by the shield on its tags, with a
		// see-through fill and the shield, hits left on it and the boss phase above it.
		List<Fill> nextFills = new ArrayList<>();
		if (s.inferno && (s.shieldColors || s.shieldText)) {
			java.util.Set<Integer> seen = new java.util.HashSet<>();
			for (SlayerTags.Tag tag : SlayerTags.all()) {
				LivingEntity mob = tag.owner();
				if (mob == null || !seen.add(mob.getId())) {
					continue;
				}
				List<String> mobTags = SlayerTags.of(mob);
				SlayerBosses.Shield shield = SlayerBosses.shieldOf(mobTags);
				if (shield == null) {
					continue;
				}
				if (s.shieldColors && !nextGlow.containsKey(mob.getId())) {
					nextGlow.put(mob.getId(), 0xFF000000 | shield.rgb);
				}
				if (s.shieldText) {
					String text = null;
					{
						StringBuilder b = new StringBuilder(shield.name());
						// Hits only on the Demonlord itself, not on its demons (Quazii, Typhoeus).
						for (String line : SlayerType.INFERNO.matches(mob) ? mobTags : List.<String>of()) {
							Matcher m = SHIELD_HITS.matcher(line);
							if (line.contains(shield.name()) && m.find()) {
								int hits = Integer.parseInt(m.group(1));
								b.append("  ").append(hits).append(hits == 1 ? " hit" : " hits");
								break;
							}
						}
						text = b.toString();
					}
					nextFills.add(new Fill(mob, shield.rgb, text));
				}
			}
		}

		List<ArmorStand> stands = new ArrayList<>();
		for (Entity entity : nearby) {
			if (entity instanceof ArmorStand stand) {
				stands.add(stand);
			}
		}

		// Mini-bosses (and their cocoons): any mob whose name tags name a slayer mini-boss.
		if (s.esp && (s.miniHighlight || s.miniLine)) {
			java.util.Set<Integer> done = new java.util.HashSet<>();
			for (SlayerTags.Tag tag : SlayerTags.all()) {
				LivingEntity mob = tag.owner();
				if (mob == null || !done.add(mob.getId()) || !miniBoss(SlayerTags.of(mob))) {
					continue;
				}
				if (s.miniHighlight) {
					nextGlow.put(mob.getId(), 0xFF000000 | s.miniRgb);
				}
				if (s.miniLine && mob.distanceTo(client.player) < 10) {
					nextLines.add(new Line(mob, null, s.miniRgb, 1.0));
				}
			}
			for (Entity entity : nearby) {
				if (entity instanceof LivingEntity mob && !SlayerTags.isTag(mob) && done.add(mob.getId()) && miniBoss(SlayerTags.of(mob))) {
					if (s.miniHighlight) {
						nextGlow.put(mob.getId(), 0xFF000000 | s.miniRgb);
					}
					if (s.miniLine && mob.distanceTo(client.player) < 10) {
						nextLines.add(new Line(mob, null, s.miniRgb, 1.0));
					}
				}
			}
		}

		// Tarantula egg sacs during the boss fight.
		if (s.esp && s.eggSacs && type == SlayerType.TARANTULA && SlayerApi.inBossFight()) {
			List<Entity> shoot = new ArrayList<>();
			List<Entity> timers = new ArrayList<>();
			for (SlayerTags.Tag tag : SlayerTags.all()) {
				for (String line : tag.lines()) {
					if (line.equalsIgnoreCase("SHOOT ME!")) {
						shoot.add(tag.entity());
					} else if (EGG_TIMER.matcher(line).matches()) {
						timers.add(tag.entity());
					}
				}
			}
			for (Entity timer : timers) {
				boolean near = false;
				for (Entity sh : shoot) {
					if (sh.distanceTo(timer) < 2.5) {
						near = true;
					}
				}
				if (!near) {
					continue;
				}
				nextGlow.put(timer.getId(), 0xFF000000 | s.eggSacRgb);
				for (ArmorStand part : stands) {
					if (!part.hasCustomName() && part.distanceTo(timer) < 1.2) {
						nextGlow.put(part.getId(), 0xFF000000 | s.eggSacRgb);
					}
				}
			}
		}

		// Voidgloom: beacons (carried, flying, placed) and Nukekubi skulls.
		boolean end = "The End".equalsIgnoreCase(SkyblockLocation.area);
		if (s.voidgloom && end) {
			boolean beaconAny = s.beaconHighlight || s.beaconWarning || s.beaconLine;
			java.util.Set<Integer> seenFlying = new java.util.HashSet<>();
			for (Entity entity : nearby) {
				if (beaconAny && entity instanceof EnderMan man && s.beaconHighlight) {
					BlockState carried = man.getCarriedBlock();
					if (carried != null && carried.is(Blocks.BEACON) && man.distanceTo(client.player) < 15) {
						nextGlow.put(man.getId(), 0xFF000000 | s.beaconRgb);
					}
				}
				if (!(entity instanceof ArmorStand stand)) {
					continue;
				}
				ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
				if (beaconAny && !helmet.isEmpty() && helmet.getHoverName().getString().equals("Beacon") && stand.distanceTo(client.player) < 15) {
					seenFlying.add(stand.getId());
					FLYING.put(stand.getId(), stand.position());
					if (s.beaconHighlight) {
						nextGlow.put(stand.getId(), 0xFF000000 | s.beaconRgb);
						nextLabels.add(new Label(stand.position().add(0, 2.2, 0), "Beacon", 0xAA0000, true));
					}
					if (s.beaconLine) {
						nextLines.add(new Line(stand, null, s.beaconRgb, 1.5));
					}
					if (s.beaconWarning && WARNED.add(stand.getId())) {
						SlayerAlerts.title("Beacon", 0xAA0000, 40);
					}
					continue;
				}
				if (s.skulls && texture(helmet).equals(NUKEKUBI)) {
					nextGlow.put(stand.getId(), 0xFFFFAA00);
					nextLabels.add(new Label(stand.position().add(0, 2.0, 0), "Nukekubi Skull", 0xFFAA00, false));
					if (s.skullLine && stand.distanceTo(client.player) < 20 && visible(client, stand.position().add(0, 1.5, 0))) {
						nextLines.add(new Line(stand, null, 0xFFAA00, 1.5));
					}
				}
			}
			// A flying beacon that vanished landed: find the placed block.
			FLYING.entrySet().removeIf(entry -> {
				if (seenFlying.contains(entry.getKey())) {
					return false;
				}
				BlockPos center = BlockPos.containing(entry.getValue());
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3))) {
					if (client.level.getBlockState(pos).is(Blocks.BEACON)) {
						SITTING.put(pos.immutable(), System.currentTimeMillis());
						break;
					}
				}
				return true;
			});
			long now = System.currentTimeMillis();
			SITTING.entrySet().removeIf(entry -> now - entry.getValue() > 7000 || !client.level.getBlockState(entry.getKey()).is(Blocks.BEACON));
			for (Map.Entry<BlockPos, Long> entry : SITTING.entrySet()) {
				Vec3 center = Vec3.atCenterOf(entry.getKey());
				if (center.distanceTo(client.player.position()) > 20) {
					continue;
				}
				if (s.beaconLine) {
					nextLines.add(new Line(null, center.add(0, 0.5, 0), s.beaconRgb, 0));
				}
				if (s.beaconHighlight) {
					double left = Math.max(0, 5.0 - (now - entry.getValue()) / 1000.0);
					nextLabels.add(new Label(center.add(0, 1.0, 0), String.format(Locale.ROOT, "Beacon %.1fs", left), 0xAA0000, true));
				}
			}
		} else if (!SITTING.isEmpty() || !FLYING.isEmpty()) {
			SITTING.clear();
			FLYING.clear();
		}

		// Riftstalker Bloodfiend: bosses, ichors, springs.
		if (s.bloodfiend && type == SlayerType.VAMPIRE || s.bloodfiend && area == SlayerType.VAMPIRE) {
			String me = SlayerApi.playerName();
			long now = System.currentTimeMillis();
			for (Entity entity : nearby) {
				if (!(entity instanceof RemotePlayer bf) || !bf.getName().getString().trim().equals("Bloodfiend") || bf.distanceTo(client.player) > 15) {
					continue;
				}
				String owner = owner(bf);
				if (owner == null) {
					continue;
				}
				boolean own = owner.equalsIgnoreCase(me);
				boolean coop = !own && coop(owner);
				boolean other = !own && !coop && TAGGED.contains(bf.getId());
				boolean show = own && s.ownBoss || coop && s.coopBoss || other && s.othersBoss;
				if (!show) {
					continue;
				}
				float max = bf.getMaxHealth();
				boolean steak = max > 0 && bf.getHealth() <= max * 0.2f;
				if (own) {
					long h = SlayerApi.bossHealth();
					long m = SlayerApi.bossMaxHealth();
					if (h >= 0 && m > 0) {
						steak = h <= m * 0.2;
					}
				}
				int rgb = steak && s.steakColor ? s.steakRgb : own ? s.ownRgb : coop ? s.coopRgb : s.othersRgb;
				nextGlow.put(bf.getId(), 0xFF000000 | rgb);
				if (s.bloodLine && visible(client, bf.getEyePosition())) {
					nextLines.add(new Line(bf, null, s.bloodLineRgb, 1.4));
				}
				if (steak && s.ownSteakAlert && (own || coop) && now - steakTitle > 300) {
					steakTitle = now;
					SlayerAlerts.title("STEAK!", 0xFF5555, 6);
				}
				if (s.ownTwinclaws && (own || coop) && now - clawTitle > 5000 && twinclaws(client, bf)) {
					clawTitle = now + Math.max(0, s.twinclawsDelay);
					int ticks = Math.max(5, (1750 - Math.max(0, s.twinclawsDelay)) / 50);
					SlayerAlerts.title("TWINCLAWS", 0xFFAA00, ticks);
				}
			}
			for (ArmorStand stand : stands) {
				if (stand.distanceTo(client.player) > 15) {
					continue;
				}
				String tex = texture(stand.getItemBySlot(EquipmentSlot.HEAD));
				boolean ichor = s.ichor && tex.equals(ICHOR);
				boolean spring = s.spring && tex.equals(SPRING);
				if (!ichor && !spring) {
					continue;
				}
				int rgb = ichor ? s.ichorRgb : s.springRgb;
				nextGlow.put(stand.getId(), 0xFF000000 | rgb);
				nextLabels.add(new Label(stand.position().add(0, 2.5, 0), ichor ? "Ichor" : "Spring", 0xAA0000, false));
				if ((ichor && s.ichorLines || spring && s.springLines) && boss != null && visible(client, stand.position().add(0, 1.5, 0))) {
					nextLines.add(new Line(stand, boss.getEyePosition(), rgb, 1.5));
				}
			}
		} else {
			TAGGED.clear();
		}

		// Valuable drops on the ground.
		if (s.esp && s.itemsOnGround && SlayerApi.hasQuest() && SlayerApi.plausibleArea()) {
			for (Entity entity : nearby) {
				if (!(entity instanceof ItemEntity item) || item.distanceTo(client.player) > 15) {
					continue;
				}
				ItemStack stack = item.getItem();
				if (stack.getItem() instanceof SpawnEggItem) {
					continue;
				}
				String id = NeuIds.of(stack);
				if (id == null) {
					continue;
				}
				double price = ProfileViewer.price(id) * stack.getCount();
				if (price < s.itemsMinPrice) {
					continue;
				}
				String name = (stack.getCount() > 1 ? stack.getCount() + "x " : "") + stack.getHoverName().getString().replaceAll("§.", "")
					+ " §6" + shortCoins(price);
				nextLabels.add(new Label(item.position().add(0, 0.8, 0), name, 0xFFFFFF, false));
			}
		}

		// Style: outline (glow), box, or both; the outline can also fill the mob's shape.
		int style = Math.max(0, Math.min(2, s.espStyle));
		Int2IntOpenHashMap outGlow = new Int2IntOpenHashMap();
		List<Fill> boxes = new ArrayList<>();
		int outlineAlpha = s.outlineFill ? fillAlpha(s.fillOpacity) : 0xD9;
		for (var e : nextGlow.int2IntEntrySet()) {
			int rgb = e.getIntValue() & 0xFFFFFF;
			if (style != 1) {
				outGlow.put(e.getIntKey(), (outlineAlpha << 24) | rgb);
			}
			if (style != 0) {
				Entity entity = client.level.getEntity(e.getIntKey());
				if (entity != null) {
					boxes.add(new Fill(entity, rgb, null));
				}
			}
		}
		marks = nextGlow;
		glow = outGlow;
		lines = List.copyOf(nextLines);
		labels = List.copyOf(nextLabels);
		fills = List.copyOf(boxes);
		texts = List.copyOf(nextFills);
	}

	private static boolean twinclaws(Minecraft client, Entity bf) {
		for (String name : linesNear(bf)) {
			if (name.matches(".*TWINCLAWS [0-9.,]+s.*")) {
				return true;
			}
		}
		return false;
	}

	/** A mob's tags plus unmatched tags floating right above it. */
	private static List<String> linesNear(Entity mob) {
		List<String> out = new ArrayList<>(SlayerTags.of(mob));
		AABB above = mob.getBoundingBox().inflate(1.5, 3.5, 1.5);
		for (SlayerTags.Tag tag : SlayerTags.all()) {
			if (tag.owner() == null && above.contains(tag.entity().position())) {
				out.addAll(tag.lines());
			}
		}
		return out;
	}

	private static boolean coop(String owner) {
		String members = settings().coopMembers;
		if (members == null || members.isBlank()) {
			return false;
		}
		for (String m : members.split(",")) {
			if (m.trim().equalsIgnoreCase(owner)) {
				return true;
			}
		}
		return false;
	}

	private static String owner(Entity mob) {
		for (String line : linesNear(mob)) {
			var m = SPAWNED_BY.matcher(line);
			if (m.find()) {
				return m.group(1);
			}
		}
		return null;
	}

	private static boolean miniBoss(List<String> lines) {
		for (String name : lines) {
			// Hypixel marks them with a "[SLAYER MINIBOSS]" tag.
			if (name.toUpperCase(java.util.Locale.ROOT).contains("SLAYER MINIBOSS")) {
				return true;
			}
			for (SlayerType type : SlayerType.values()) {
				for (String mini : type.miniBosses) {
					if (name.contains(mini)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static String texture(ItemStack stack) {
		String raw = PestEsp.headTexture(stack);
		return raw.isEmpty() ? "" : PestEsp.textureHash(raw);
	}

	private static boolean visible(Minecraft client, Vec3 to) {
		Vec3 from = client.player.getEyePosition();
		HitResult hit = client.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, client.player));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(from) + 0.36 >= to.distanceToSqr(from);
	}

	private static String shortCoins(double v) {
		if (v >= 1e9) {
			return String.format(Locale.ROOT, "%.1fB", v / 1e9);
		}
		if (v >= 1e6) {
			return String.format(Locale.ROOT, "%.1fM", v / 1e6);
		}
		if (v >= 1e3) {
			return String.format(Locale.ROOT, "%.1fk", v / 1e3);
		}
		return String.format(Locale.ROOT, "%.0f", v);
	}

	// ---------------------------------------------------------------- draw

	private static void emit() {
		List<Line> current = lines;
		List<Fill> boxes = fills;
		if (current.isEmpty() && boxes.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		{
			int alpha = Math.round(StrayConfig.clamp(settings().boxOpacity, 0.05f, 0.9f) * 255f);
			for (Fill fill : boxes) {
				if (dying(fill.entity)) {
					continue;
				}
				AABB box = fill.entity.getBoundingBox().move(fill.entity.getPosition(partial).subtract(fill.entity.position())).inflate(0.05);
				int edge = 0xFF000000 | fill.rgb;
				net.minecraft.gizmos.Gizmos.cuboid(box, net.minecraft.gizmos.GizmoStyle.strokeAndFill(edge, 2.0f, (alpha << 24) | fill.rgb)).setAlwaysOnTop();
			}
		}
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		Vec3 look = client.player.getViewVector(partial);
		Vec3 eye = camera.add(look.scale(0.4));
		float width = Math.max(1, Math.min(10, settings().lineWidth));
		for (Line line : current) {
			Vec3 to;
			Vec3 from = eye;
			if (line.entity != null) {
				if (dying(line.entity)) {
					continue;
				}
				to = line.entity.getPosition(partial).add(0, line.up, 0);
				if (line.fixed != null) {
					from = line.fixed;
				}
			} else {
				to = line.fixed;
			}
			Gizmos.line(from, to, 0xFF000000 | line.rgb, width).setAlwaysOnTop();
		}
	}

	public static void extract(GuiGraphicsExtractor graphics) {
		List<Label> current = labels;
		List<Fill> boxes = texts;
		if (!boxes.isEmpty()) {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null && client.font != null && !client.options.hideGui && client.gameRenderer.getMainCamera().isInitialized()) {
				float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
				List<Label> moving = new ArrayList<>(current);
				for (Fill fill : boxes) {
					if (fill.text != null && !dying(fill.entity)) {
						// In the middle of the mob, so it sits on the fill and stays readable.
						Vec3 at = fill.entity.getPosition(partial).add(0, fill.entity.getBbHeight() * 0.5, 0);
						moving.add(new Label(at, fill.text, fill.rgb, true));
					}
				}
				current = moving;
			}
		}
		if (current.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.font == null || client.options.hideGui
			|| !client.gameRenderer.getMainCamera().isInitialized()) {
			return;
		}
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		Vector3fc forward = client.gameRenderer.getMainCamera().forwardVector();
		Font font = client.font;
		for (Label label : current) {
			Vec3 rel = label.at.subtract(camera);
			if (rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z() <= 0.12) {
				continue;
			}
			if (!label.through && !visible(client, label.at)) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(label.at);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			float scale = NametagRenderer.distanceScale(rel.length()) * 1.4f;
			Component text = Component.literal(label.text);
			float w = font.width(text);
			GuiDraw.text(graphics, font, text, x - w * scale * 0.5f, y - font.lineHeight * scale, scale, 0xFF000000 | label.rgb, true);
		}
	}
}
