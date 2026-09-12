package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Side-of-entity health bars. Height matches the mob on screen. Fill eases
 * toward real health and shifts green to red as it drops. Skyblock mobs use the
 * hologram armor-stand name; no HP in that name means no bar.
 */
public final class EntityHealthBars {
	private static final float WIDTH_RATIO = 0.09f;
	private static final float GAP_RATIO = 0.11f;
	private static final int TRACK = 0xCC0B0E14;
	private static final int LINE = 0x661C2430;
	private static final String HEART = "❤";
	private static final String HEART_ALT = "♥";
	private static final Pattern SLASH_HP = Pattern.compile(
		"(?i)(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*([kmb])?\\s*/\\s*(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*([kmb])?"
	);
	private static final Pattern HEART_HP = Pattern.compile(
		"(?i)(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*([kmb])?\\s*[❤♥]"
	);
	private static final Map<UUID, Bar> BARS = new HashMap<>();
	private static long lastNs = System.nanoTime();

	private EntityHealthBars() {
	}

	public static void init() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.options.hideGui) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.healthBarEnabled) {
			BARS.clear();
			return;
		}
		long now = System.nanoTime();
		float dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		double range = StrayConfig.clamp(config.healthBarRange, 16, 96);
		double maxSq = range * range;
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		float partial = delta.getGameTimeDeltaPartialTick(true);
		boolean through = config.healthBarThroughWalls;
		boolean players = config.healthBarPlayers;
		boolean right = config.healthBarRight();
		boolean skyblock = SkyblockLocation.inSkyblock;
		Map<Integer, Float> holograms = skyblock ? hologramHealth(client, maxSq, camPos) : Map.of();
		float guiW = graphics.guiWidth();
		float guiH = graphics.guiHeight();
		Set<UUID> seen = new HashSet<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			Float sky = skyblock ? holograms.get(living.getId()) : null;
			if (!include(client, living, players, maxSq, camPos, skyblock, sky != null)) {
				continue;
			}
			Vec3 feet = living.getPosition(partial);
			Vec3 mid = feet.add(0.0, living.getBbHeight() * 0.5, 0.0);
			if (!EntityScreenBoxes.facing(mid, camPos, forward)) {
				continue;
			}
			if (!through && EntityScreenBoxes.occluded(client, camPos, mid)) {
				continue;
			}
			EntityScreenBoxes.Box screen = EntityScreenBoxes.project(
				client,
				EntityScreenBoxes.bounds(living, feet),
				guiW,
				guiH
			);
			if (screen == null || screen.h() < 4f) {
				continue;
			}
			float target;
			if (sky != null) {
				target = sky;
			} else {
				float max = Math.max(0.001f, living.getMaxHealth());
				target = Mth.clamp(living.getHealth() / max, 0f, 1f);
			}
			UUID id = living.getUUID();
			Bar bar = BARS.computeIfAbsent(id, ignored -> new Bar(target));
			bar.tick(target, dt);
			seen.add(id);
			draw(graphics, screen, bar.shown, right, config);
		}
		Iterator<Map.Entry<UUID, Bar>> it = BARS.entrySet().iterator();
		while (it.hasNext()) {
			if (!seen.contains(it.next().getKey())) {
				it.remove();
			}
		}
	}

	private static Map<Integer, Float> hologramHealth(Minecraft client, double maxSq, Vec3 camPos) {
		Map<Integer, Float> out = new HashMap<>();
		double standSq = maxSq + 36.0;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand) || stand.distanceToSqr(camPos) > standSq) {
				continue;
			}
			String name = plate(stand);
			float ratio = parseHealth(name);
			if (Float.isNaN(ratio)) {
				continue;
			}
			LivingEntity mob = bindStand(stand, name, client.player);
			if (mob == null) {
				continue;
			}
			out.put(mob.getId(), ratio);
		}
		return out;
	}

	private static String plate(ArmorStand stand) {
		Component custom = stand.getCustomName();
		if (custom != null) {
			String raw = custom.getString();
			if (!raw.isEmpty()) {
				return raw;
			}
		}
		return stand.getDisplayName().getString();
	}

	private static float parseHealth(String raw) {
		if (raw == null || raw.isEmpty()) {
			return Float.NaN;
		}
		String plain = raw.replaceAll("§.", "");
		if (!hasHeart(plain) && plain.indexOf('/') < 0) {
			return Float.NaN;
		}
		Matcher slash = SLASH_HP.matcher(plain);
		if (slash.find()) {
			float current = readAmount(slash.group(1), slash.group(2));
			float max = readAmount(slash.group(3), slash.group(4));
			if (max > 0f) {
				return Mth.clamp(current / max, 0f, 1f);
			}
		}
		if (!hasHeart(plain)) {
			return Float.NaN;
		}
		Matcher heart = HEART_HP.matcher(plain);
		if (heart.find()) {
			float current = readAmount(heart.group(1), heart.group(2));
			if (current >= 0f) {
				return 1f;
			}
		}
		return Float.NaN;
	}

	private static boolean hasHeart(String plain) {
		return plain.contains(HEART) || plain.contains(HEART_ALT);
	}

	private static float readAmount(String raw, String suffix) {
		if (raw == null || raw.isEmpty()) {
			return Float.NaN;
		}
		float value = Float.parseFloat(raw.replace(",", ""));
		if (suffix != null && !suffix.isEmpty()) {
			char unit = Character.toLowerCase(suffix.charAt(0));
			if (unit == 'k') {
				value *= 1000f;
			} else if (unit == 'm') {
				value *= 1_000_000f;
			} else if (unit == 'b') {
				value *= 1_000_000_000f;
			}
		}
		return value;
	}

	private static LivingEntity bindStand(ArmorStand stand, String name, Player self) {
		String plain = name.replaceAll("§.", "").toUpperCase();
		int offset = plain.contains("WITHERMANCER") ? 3 : 1;
		Entity byId = stand.level().getEntity(stand.getId() - offset);
		if (byId instanceof LivingEntity living && skyMob(living, self)) {
			return living;
		}
		AABB box = stand.getBoundingBox().inflate(0.8, 1.8, 0.8).move(0.0, -1.2, 0.0);
		List<Entity> nearby = stand.level().getEntities(stand, box, other -> other instanceof LivingEntity living && skyMob(living, self));
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		Vec3 at = stand.position().add(0.0, -1.0, 0.0);
		for (Entity other : nearby) {
			double dist = other.distanceToSqr(at);
			if (dist < bestDist) {
				bestDist = dist;
				best = (LivingEntity) other;
			}
		}
		return best;
	}

	private static boolean skyMob(LivingEntity living, Player self) {
		if (living.isRemoved() || living.isDeadOrDying() || living instanceof ArmorStand) {
			return false;
		}
		if (living == self) {
			return false;
		}
		if (living instanceof Player player) {
			return player.getUUID().version() == 2;
		}
		return true;
	}

	private static boolean include(
		Minecraft client,
		LivingEntity living,
		boolean players,
		double maxSq,
		Vec3 camPos,
		boolean skyblock,
		boolean hologram
	) {
		if (living.isRemoved() || living.isDeadOrDying()) {
			return false;
		}
		if (living instanceof ArmorStand) {
			return false;
		}
		if (living.isInvisibleTo(client.player)) {
			return false;
		}
		if (living == client.player) {
			return false;
		}
		if (living instanceof Player) {
			if (!players || !NametagRenderer.realAccount(living)) {
				return hologram;
			}
			return living.distanceToSqr(camPos) <= maxSq;
		}
		if (skyblock) {
			return hologram && living.distanceToSqr(camPos) <= maxSq;
		}
		if (living.getMaxHealth() <= 0f) {
			return false;
		}
		return living.distanceToSqr(camPos) <= maxSq;
	}

	private static void draw(GuiGraphicsExtractor graphics, EntityScreenBoxes.Box box, float shown, boolean right, StrayConfig config) {
		boolean csgo = config.healthBarCsgo();
		float h = box.h();
		float want = Mth.clamp(config.healthBarWidth, 1f, 6f);
		float w = Mth.clamp(h * WIDTH_RATIO * (want / 3f), 1f, want);
		float gap = Mth.clamp(h * GAP_RATIO, 1.5f, 5f);
		float pad = Mth.clamp(h * 0.03f, 0.4f, 1f);
		float x = right ? box.x() + box.w() + gap : box.x() - gap - w;
		float y = box.y();
		float fillH = h * Mth.clamp(shown, 0f, 1f);
		int color = 0xFF000000 | healthColor(shown, config);
		if (csgo) {
			GuiDraw.fillSmooth(graphics, x - pad, y - pad, w + pad * 2f, h + pad * 2f, 0xFF000000);
			if (fillH >= 0.5f) {
				GuiDraw.fillSmooth(graphics, x, y + h - fillH, w, fillH, color);
			}
			return;
		}
		float radius = Math.min(w * 0.45f, h * 0.12f);
		GuiDraw.rounded(graphics, x - pad, y - pad, w + pad * 2f, h + pad * 2f, radius + pad * 0.4f, LINE);
		GuiDraw.rounded(graphics, x, y, w, h, radius, TRACK);
		if (fillH >= 0.6f) {
			GuiDraw.rounded(graphics, x, y + h - fillH, w, fillH, radius, color);
		}
	}

	private static int healthColor(float t, StrayConfig config) {
		t = Mth.clamp(t, 0f, 1f);
		int start = config.healthBarFullRgb & 0xFFFFFF;
		int end = config.healthBarEmptyRgb & 0xFFFFFF;
		int r = Math.round(Mth.lerp(t, (end >> 16) & 0xFF, (start >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(t, (end >> 8) & 0xFF, (start >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, end & 0xFF, start & 0xFF));
		return (r << 16) | (g << 8) | b;
	}

	private static final class Bar {
		float shown;

		Bar(float start) {
			this.shown = start;
		}

		void tick(float target, float dt) {
			float k = 1f - (float) Math.exp(-14f * dt);
			shown += (target - shown) * k;
			if (Math.abs(target - shown) < 0.002f) {
				shown = target;
			}
		}
	}
}
