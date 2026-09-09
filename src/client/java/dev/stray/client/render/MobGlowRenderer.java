package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.ClientLevelAccessor;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Marks matching mobs for the entity-outline buffer. A custom post shader
 * turns that silhouette into a clean outward gradient, not vanilla sobel.
 * <p>
 * {@code /vm esp <text>} glows the living mob as soon as Hypixel sends a
 * matching nametag in entity metadata, even if the hologram plate has not
 * started drawing yet.
 */
public final class MobGlowRenderer {
	private static final double MAX_RANGE = 96.0;
	private static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;
	private static final double HOLOGRAM_XZ = 1.25;
	private static final double HOLOGRAM_XZ_SQ = HOLOGRAM_XZ * HOLOGRAM_XZ;
	private static final double HOLOGRAM_UP = 8.0;
	private static final Pattern STRIP = Pattern.compile("§.");
	private static List<String> cachedIds;
	private static Set<EntityType<?>> cachedTypes = Set.of();
	private static final Object NAME_LOCK = new Object();
	private static volatile int nameTick = Integer.MIN_VALUE;
	private static volatile String nameNeedle = "";
	private static volatile Set<Integer> nameIds = Set.of();
	private static final Map<UUID, String> remembered = new ConcurrentHashMap<>();
	private static final Map<Integer, String> packetLabels = new ConcurrentHashMap<>();
	private static int needleTick = Integer.MIN_VALUE;
	private static List<String> cachedNeedles = List.of();
	private static String cachedNeedleKey = "";
	private static int frame;
	private static int colorFrame = Integer.MIN_VALUE;
	private static final Int2IntOpenHashMap COLOR_CACHE = new Int2IntOpenHashMap();

	private MobGlowRenderer() {
	}

	public static void init() {
	}

	/**
	 * Called once per rendered frame. Outline colors (which may raycast) are
	 * asked for by several hooks per entity per frame; they only need one answer.
	 */
	public static void beginFrame() {
		frame++;
	}

	public static void reset() {
		synchronized (NAME_LOCK) {
			nameTick = Integer.MIN_VALUE;
			needleTick = Integer.MIN_VALUE;
			nameNeedle = "";
			nameIds = Set.of();
			COLOR_CACHE.clear();
			remembered.clear();
			packetLabels.clear();
			EspMobPrint.clear();
		}
	}

	public static void onNamePacket(int entityId, String label) {
		if (label == null || label.isBlank()) {
			return;
		}
		packetLabels.put(entityId, label.toLowerCase(Locale.ROOT));
		nameTick = Integer.MIN_VALUE;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		Entity entity = client.level.getEntity(entityId);
		if (entity == null) {
			return;
		}
		attachName(client, entity, packetLabels.get(entityId));
	}

	public static void onPassengersPacket(int vehicleId, int[] passengerIds) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		Entity vehicle = client.level.getEntity(vehicleId);
		if (vehicle == null || isHologram(vehicle)) {
			return;
		}
		String combined = packetLabels.getOrDefault(vehicleId, "");
		if (passengerIds != null) {
			for (int id : passengerIds) {
				String extra = packetLabels.getOrDefault(id, "");
				Entity passenger = client.level.getEntity(id);
				if (extra.isEmpty() && passenger != null) {
					extra = labelOf(passenger);
				}
				if (!extra.isEmpty()) {
					combined = combined.isEmpty() ? extra : combined + " " + extra;
				}
			}
		}
		if (!combined.isEmpty()) {
			packetLabels.put(vehicleId, combined);
			remembered.put(vehicle.getUUID(), combined);
			nameTick = Integer.MIN_VALUE;
		}
	}

	public static void onRemoveEntities(IntList ids) {
		if (ids == null) {
			return;
		}
		for (int i = 0; i < ids.size(); i++) {
			packetLabels.remove(ids.getInt(i));
		}
	}

	public static int nearbyCount() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return 0;
		}
		int count = 0;
		for (Entity entity : allEntities(client)) {
			if (isEspTarget(entity, client.player)) {
				count++;
			}
		}
		return count;
	}

	public static boolean shouldForceGlow(Entity entity) {
		return outlineColor(entity) != 0;
	}

	public static boolean hasVanillaGlow(Entity entity) {
		return entity != null && entity.isCurrentlyGlowing();
	}

	public static boolean glowEnabled() {
		StrayConfig config = StrayConfig.get();
		return config.mobGlowEnabled || config.starMobEsp;
	}

	public static int outlineColor(Entity entity) {
		StrayConfig config = StrayConfig.get();
		if (!glowEnabled() || entity == null) {
			return 0;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || entity == client.player) {
			return 0;
		}
		if (hasVanillaGlow(entity) || !isEspTarget(entity, client.player)) {
			return 0;
		}
		if (colorFrame != frame) {
			colorFrame = frame;
			COLOR_CACHE.clear();
		}
		int id = entity.getId();
		int cached = COLOR_CACHE.getOrDefault(id, Integer.MIN_VALUE);
		if (cached != Integer.MIN_VALUE) {
			return cached;
		}
		int color = computeOutlineColor(config, client, entity);
		COLOR_CACHE.put(id, color);
		return color;
	}

	public static boolean shouldBypassCulling(Entity entity) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || entity == null || entity == client.player) {
			return false;
		}
		if (!isEspTarget(entity, client.player)) {
			return false;
		}
		StrayConfig config = StrayConfig.get();
		if (StarMobEsp.glowing(entity) && config.starMobThroughWalls) {
			return true;
		}
		return config.mobGlowEnabled
			&& config.mobGlowThroughWalls
			&& (listed(entity.getType()) || nametagHit(entity));
	}

	private static int computeOutlineColor(StrayConfig config, Minecraft client, Entity entity) {
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		if (entity.distanceToSqr(camera) > MAX_RANGE_SQ) {
			return 0;
		}
		boolean star = StarMobEsp.glowing(entity);
		boolean mob = config.mobGlowEnabled && (listed(entity.getType()) || nametagHit(entity));
		boolean through = (star && config.starMobThroughWalls) || (mob && config.mobGlowThroughWalls);
		if (!through && occluded(client, camera, entity.getEyePosition())) {
			return 0;
		}
		if (star) {
			return packColor(config.starMobRgb, config.starMobOpacity);
		}
		return packColor(config.mobGlowRgb, config.mobGlowOpacity);
	}

	public static int packColor(StrayConfig config) {
		return packColor(config.mobGlowRgb, config.mobGlowOpacity);
	}

	public static int packColor(int rgb, float opacity) {
		float clamped = StrayConfig.clamp(opacity, 0.15f, 0.90f);
		int alpha = Math.round(clamped * 255f);
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	public static boolean listed(EntityType<?> type) {
		return type != null && selectedTypes().contains(type);
	}

	public static boolean listed(Entity entity) {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && isEspTarget(entity, client.player);
	}

	private static boolean isEspTarget(Entity entity, Entity player) {
		if (entity == null || entity == player || entity.isRemoved() || hasVanillaGlow(entity)) {
			return false;
		}
		if (isHologram(entity)) {
			return false;
		}
		if (entity instanceof LivingEntity living && !living.isAlive()) {
			return false;
		}
		if (StarMobEsp.glowing(entity)) {
			return true;
		}
		if (!StrayConfig.get().mobGlowEnabled) {
			return false;
		}
		if (listed(entity.getType())) {
			return true;
		}
		return nametagHit(entity);
	}

	private static Set<EntityType<?>> selectedTypes() {
		List<String> ids = StrayConfig.get().mobGlowIds;
		if (ids == cachedIds) {
			return cachedTypes;
		}
		cachedIds = ids;
		Set<EntityType<?>> types = new HashSet<>();
		if (ids != null) {
			for (String id : ids) {
				EntityType<?> type = MobCatalog.type(id);
				if (type != null) {
					types.add(type);
				}
			}
		}
		cachedTypes = types;
		return types;
	}

	private static boolean nametagHit(Entity entity) {
		List<String> needles = needles();
		if (needles.isEmpty()) {
			return false;
		}
		refreshNameIds(needles);
		return nameIds.contains(entity.getId());
	}

	/**
	 * {@link StrayConfig#nametagEspNeedles()} re-normalizes and copies the
	 * label list on every call, so only ask it once per tick.
	 */
	private static List<String> needles() {
		Minecraft client = Minecraft.getInstance();
		int tick = client.player == null ? 0 : client.player.tickCount;
		synchronized (NAME_LOCK) {
			if (tick != needleTick) {
				needleTick = tick;
				cachedNeedles = StrayConfig.get().nametagEspNeedles();
				cachedNeedleKey = String.join("\n", cachedNeedles);
			}
			return cachedNeedles;
		}
	}

	private static void refreshNameIds(List<String> needles) {
		Minecraft client = Minecraft.getInstance();
		int tick = client.player == null ? 0 : client.player.tickCount;
		synchronized (NAME_LOCK) {
			String key = needles == cachedNeedles ? cachedNeedleKey : String.join("\n", needles);
			if (tick == nameTick && key.equals(nameNeedle)) {
				return;
			}
			rebuildNameIds(client, needles, tick, key);
		}
	}

	private static void rebuildNameIds(Minecraft client, List<String> needles, int tick, String key) {
		nameTick = tick;
		nameNeedle = key;
		if (client.level == null || client.player == null) {
			nameIds = Set.of();
			return;
		}
		Set<Integer> ids = new HashSet<>();
		Set<UUID> seen = new HashSet<>();
		Map<UUID, String> plates = new HashMap<>();
		for (Entity entity : allEntities(client)) {
			if (entity == null || entity == client.player) {
				continue;
			}
			seen.add(entity.getUUID());
			if (!isHologram(entity)) {
				continue;
			}
			String live = labelOf(entity);
			if (live.isEmpty()) {
				continue;
			}
			Entity owner = ownerOf(client, entity);
			if (owner == null) {
				continue;
			}
			plates.merge(owner.getUUID(), live, MobGlowRenderer::joinLabels);
			remembered.put(owner.getUUID(), plates.get(owner.getUUID()));
		}
		for (Entity entity : allEntities(client)) {
			if (!isMob(entity) || hasVanillaGlow(entity)) {
				continue;
			}
			String plate = plates.getOrDefault(entity.getUUID(), "");
			boolean hit = false;
			for (String n : needles) {
				boolean named = textHasNeedle(plate, n) || nameMatches(entity, n);
				boolean otherName = hasOtherName(plate, n);
				if (otherName && entity instanceof LivingEntity living) {
					EspMobPrint.forget(n, living);
				}
				if (named && !otherName && entity instanceof LivingEntity living) {
					EspMobPrint.learn(n, living);
				}
				boolean copy = !otherName && entity instanceof LivingEntity living && EspMobPrint.matches(n, living);
				if (named || copy) {
					hit = true;
				}
			}
			if (hit) {
				ids.add(entity.getId());
			}
		}
		remembered.keySet().removeIf(uuid -> !seen.contains(uuid));
		nameIds = ids;
	}

	private static void attachName(Minecraft client, Entity named, String live) {
		String label = live == null || live.isEmpty() ? labelOf(named) : live;
		if (label.isEmpty()) {
			return;
		}
		if (isMob(named)) {
			remembered.put(named.getUUID(), label);
			return;
		}
		if (!isHologram(named)) {
			return;
		}
		Entity owner = ownerOf(client, named);
		if (owner == null) {
			return;
		}
		remembered.merge(owner.getUUID(), label, MobGlowRenderer::joinLabels);
	}

	private static Entity ownerOf(Minecraft client, Entity hologram) {
		Entity vehicle = hologram.getVehicle();
		if (vehicle != null && isMob(vehicle)) {
			return vehicle;
		}
		AABB box = hologram.getBoundingBox()
			.inflate(HOLOGRAM_XZ, 0, HOLOGRAM_XZ)
			.expandTowards(0, -HOLOGRAM_UP, 0)
			.expandTowards(0, 0.75, 0);
		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity other : client.level.getEntities(hologram, box)) {
			if (!isMob(other) || hasVanillaGlow(other) || other.getY() > hologram.getY() + 0.5) {
				continue;
			}
			double xz = xzDistSq(hologram, other);
			if (xz > HOLOGRAM_XZ_SQ) {
				continue;
			}
			double dy = hologram.getY() - other.getY();
			if (dy < -0.25 || dy > HOLOGRAM_UP) {
				continue;
			}
			double score = xz * 8.0 + Math.abs(dy - 2.0) * 0.02;
			if (score < bestScore) {
				bestScore = score;
				best = other;
			}
		}
		return best;
	}

	private static boolean nameMatches(Entity entity, String needle) {
		if (labelContains(entity, needle)) {
			return true;
		}
		for (Entity passenger : entity.getPassengers()) {
			if (labelContains(passenger, needle)) {
				return true;
			}
		}
		Entity vehicle = entity.getVehicle();
		return vehicle != null && labelContains(vehicle, needle);
	}

	private static boolean hasOtherName(String plate, String needle) {
		return plate != null && !plate.isEmpty() && looksLikeNameplate(plate) && !textHasNeedle(plate, needle);
	}

	private static boolean looksLikeNameplate(String text) {
		String trimmed = text == null ? "" : text.trim();
		if (trimmed.length() < 3) {
			return false;
		}
		int letters = 0;
		for (int i = 0; i < trimmed.length(); i++) {
			if (Character.isLetter(trimmed.charAt(i))) {
				letters++;
			}
		}
		return letters >= 3;
	}

	private static boolean labelContains(Entity entity, String needle) {
		return textHasNeedle(labelOf(entity), needle);
	}

	private static boolean textHasNeedle(String text, String needle) {
		if (text == null || needle == null || text.isEmpty() || needle.isEmpty()) {
			return false;
		}
		int from = 0;
		while (from <= text.length() - needle.length()) {
			int at = text.indexOf(needle, from);
			if (at < 0) {
				return false;
			}
			boolean before = at == 0 || !isWordChar(text.charAt(at - 1));
			int end = at + needle.length();
			boolean after = end >= text.length() || !isWordChar(text.charAt(end));
			if (before && after) {
				return true;
			}
			from = at + 1;
		}
		return false;
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	private static String joinLabels(String left, String right) {
		if (left == null || left.isEmpty()) {
			return right == null ? "" : right;
		}
		if (right == null || right.isEmpty() || left.contains(right)) {
			return left;
		}
		if (right.contains(left)) {
			return right;
		}
		return left + " " + right;
	}

	private static double xzDistSq(Entity a, Entity b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	private static String labelOf(Entity entity) {
		if (entity == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		String packet = packetLabels.get(entity.getId());
		if (packet != null && !packet.isEmpty()) {
			out.append(packet);
		}
		append(out, entity.getCustomName());
		if (entity.hasCustomName()) {
			append(out, entity.getDisplayName());
		}
		PlayerTeam team = entity.getTeam();
		if (team != null) {
			append(out, team.getPlayerPrefix());
			append(out, team.getPlayerSuffix());
		}
		if (entity instanceof Display.TextDisplay display) {
			Display.TextDisplay.TextRenderState state = display.textRenderState();
			if (state != null) {
				append(out, state.text());
			}
		}
		if (entity instanceof LivingEntity living) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack stack = living.getItemBySlot(slot);
				if (stack != null && !stack.isEmpty()) {
					append(out, stack.getCustomName());
				}
			}
		}
		return out.toString();
	}

	private static void append(StringBuilder out, Component text) {
		if (text == null) {
			return;
		}
		String raw = STRIP.matcher(text.getString()).replaceAll("").toLowerCase(Locale.ROOT).trim();
		if (raw.isEmpty()) {
			return;
		}
		if (!out.isEmpty()) {
			out.append(' ');
		}
		out.append(raw);
	}

	private static boolean isMob(Entity entity) {
		return entity instanceof LivingEntity
			&& entity != Minecraft.getInstance().player
			&& !isHologram(entity);
	}

	private static boolean isHologram(Entity entity) {
		return entity.getType() == EntityType.ARMOR_STAND
			|| entity.getType() == EntityType.TEXT_DISPLAY
			|| entity instanceof Display.TextDisplay;
	}

	private static Iterable<Entity> allEntities(Minecraft client) {
		if (client.level instanceof ClientLevelAccessor access) {
			return access.stray$entityStorage().getEntityGetter().getAll();
		}
		return client.level.entitiesForRendering();
	}

	private static boolean occluded(Minecraft client, Vec3 from, Vec3 to) {
		HitResult hit = client.level.clip(new ClipContext(
			from,
			to,
			ClipContext.Block.VISUAL,
			ClipContext.Fluid.NONE,
			client.player
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return false;
		}
		return hit.getLocation().distanceToSqr(from) + 0.36 < to.distanceToSqr(from);
	}
}
