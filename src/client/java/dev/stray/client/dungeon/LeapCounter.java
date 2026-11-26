package dev.stray.client.dungeon;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.mixin.GuiGraphicsExtractorInvoker;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudLayout;
import dev.stray.client.render.RenderCompat;
import dev.stray.client.render.StrayShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who has leaped to you in F7, after NoammAddons' LeapCounter (ported with noamm's
 * permission): standing on one of the P3 leap spots (or at core, or in the relic phase),
 * each teammate counts once they show up in that section. The HUD shows every teammate's
 * head and name: grey until they're there, in colour after. When everyone is there a title
 * says so. Visual only: it reads where players are, nothing is sent.
 */
public final class LeapCounter {
	public static final float SLOT_W = 52f;
	public static final float WIDTH = SLOT_W * 4f + 24f;
	public static final float HEIGHT = 50f;
	private static final float HEAD = 16f;
	private static final long DONE_HOLD_MS = 1000L;

	/** NoammAddons' P3 sections (in order: 1 to 4). */
	private static final AABB[] P3_SECTIONS = {
		box(90, 158, 123, 111, 105, 32),
		box(16, 158, 122, 111, 105, 143),
		box(19, 158, 48, -3, 106, 142),
		box(91, 158, 50, -3, 106, 30)
	};
	private static final AABB CORE_AREA = box(68, 106, 54, 42, 155, 119);

	private enum Spot {
		SS(box(106, 119, 92, 109, 121, 96), 3, 1),
		EE2(box(57, 108, 130, 59, 110, 132), 4, 2),
		HIGH_EE2(box(57, 132, 138, 62, 133, 140), 4, 2),
		EE3(box(1, 108, 101, 3, 110, 107), 3, 3),
		CORE(box(51, 114, 54, 58, 117, 49), 4, 4),
		IN_CORE(box(51, 114, 60, 58, 117, 55), 4, 0),
		RELIC(box(16.5, 3, 38.5, 102.5, 10, 134.5), 4, -1);

		final AABB box;
		final int max;
		/** The P3 section a teammate must be in; 0 the core area, -1 the relic phase. */
		final int section;
		final Set<String> leaped = new HashSet<>();
		boolean completed;
		long doneAt;

		Spot(AABB box, int max, int section) {
			this.box = box;
			this.max = max;
			this.section = section;
		}

		boolean counts(double x, double y, double z) {
			return switch (section) {
				case 0 -> CORE_AREA.contains(x, y, z);
				case -1 -> y <= 45;
				default -> p3Section(x, y, z) == section;
			};
		}
	}

	private static Spot current;
	/** Debug: a made-up P3 spot with three teammates arriving one by one. */
	private static final Set<String> SIM_LEAPED = new HashSet<>();
	private static final List<String> SIM_NAMES = new ArrayList<>();
	private static long simStart;
	private static boolean simDone;
	private static final Map<String, Float> COLOUR = new HashMap<>();
	private static long lastFrame;
	private static RenderPipeline greyPipeline;

	private LeapCounter() {
	}

	private static boolean enabled() {
		StrayConfig config = StrayConfig.get();
		return config.f7TitlesEnabled && config.f7LeapCounter;
	}

	public static void reset() {
		for (Spot spot : Spot.values()) {
			spot.leaped.clear();
			spot.completed = false;
			spot.doneAt = 0L;
		}
		current = null;
	}

	/**
	 * Debug (/stray debug dev leap): three teammates leap to you one after another, then the
	 * "everyone's here" title, on any server. Real players nearby lend their heads.
	 */
	public static void simulate() {
		Minecraft client = Minecraft.getInstance();
		SIM_LEAPED.clear();
		SIM_NAMES.clear();
		if (client.level != null && client.player != null) {
			for (AbstractClientPlayer player : client.level.players()) {
				if (player != client.player && SIM_NAMES.size() < 3) {
					SIM_NAMES.add(player.getGameProfile().name());
				}
			}
		}
		String[] fill = {"Fotca", "OrangeMango", "Noamm"};
		for (int i = 0; SIM_NAMES.size() < 3; i++) {
			SIM_NAMES.add(fill[i]);
		}
		simStart = System.currentTimeMillis();
		simDone = false;
	}

	private static boolean simulating() {
		return !SIM_NAMES.isEmpty();
	}

	private static void tickSimulation() {
		long elapsed = System.currentTimeMillis() - simStart;
		// One arrives every 1.2 s after a short pause; the HUD stays 3 s after the last.
		int arrived = (int) Math.max(0, Math.min(SIM_NAMES.size(), (elapsed - 800L) / 1200L + 1));
		for (int i = 0; i < arrived; i++) {
			SIM_LEAPED.add(SIM_NAMES.get(i));
		}
		if (arrived == SIM_NAMES.size() && !simDone) {
			simDone = true;
			F7Titles.show("LEAPED", "Everyone's here", 0x55FF55, true);
		}
		if (elapsed > 800L + 1200L * SIM_NAMES.size() + 3000L) {
			SIM_NAMES.clear();
			SIM_LEAPED.clear();
		}
	}

	public static void tick(Minecraft client) {
		if (simulating()) {
			tickSimulation();
			return;
		}
		if (!enabled() || client.player == null || client.level == null
			|| !SkyblockLocation.inDungeon || !SkyblockLocation.inBoss || SkyblockLocation.dungeonFloor != 7) {
			current = null;
			return;
		}
		long now = System.currentTimeMillis();
		for (Spot spot : Spot.values()) {
			if (spot.doneAt != 0L && now - spot.doneAt >= DONE_HOLD_MS) {
				spot.completed = true;
			}
		}
		Player self = client.player;
		current = null;
		for (Spot spot : Spot.values()) {
			if (!spot.completed && spot.box.contains(self.position())) {
				current = spot;
				break;
			}
		}
		if (current == null) {
			return;
		}
		List<DungeonParty.Member> team = teammates(client);
		int max = Math.min(team.size(), current.max);
		if (max <= 0) {
			return;
		}
		for (DungeonParty.Member member : team) {
			AbstractClientPlayer entity = entity(client, member);
			if (entity == null || current.leaped.contains(member.name)) {
				continue;
			}
			if (current.counts(entity.getX(), entity.getY(), entity.getZ())) {
				current.leaped.add(member.name);
				if (current.leaped.size() == max) {
					current.doneAt = now;
					F7Titles.show("LEAPED", "Everyone's here", 0x55FF55, true);
				}
			}
		}
	}

	// ------------------------------------------------------------------ drawing

	public static void render(GuiGraphicsExtractor graphics, Font font, boolean editor) {
		if (!enabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Spot spot = current;
		List<String> names = new ArrayList<>();
		List<AbstractClientPlayer> players = new ArrayList<>();
		Set<String> leaped;
		int max;
		if (simulating()) {
			for (String name : SIM_NAMES) {
				names.add(name);
				AbstractClientPlayer real = null;
				if (client.level != null) {
					for (AbstractClientPlayer player : client.level.players()) {
						if (player.getGameProfile().name().equals(name)) {
							real = player;
						}
					}
				}
				// Made-up teammates wear your skin, so the colour change still shows.
				players.add(real != null ? real : client.player);
			}
			max = names.size();
			leaped = SIM_LEAPED;
		} else if (spot != null) {
			for (DungeonParty.Member member : teammates(client)) {
				names.add(member.name);
				players.add(entity(client, member));
			}
			max = Math.min(names.size(), spot.max);
			leaped = spot.leaped;
		} else if (editor) {
			String self = client.getUser().getName();
			names = List.of(self, "Player 2", "Player 3", "Player 4");
			players = new ArrayList<>(java.util.Collections.nCopies(4, null));
			players.set(0, client.player);
			max = 4;
			leaped = Set.of(self, "Player 3");
		} else {
			return;
		}
		if (names.isEmpty()) {
			return;
		}
		int shownCount = Math.min(names.size(), 4);
		List<String> finalNames = names;
		List<AbstractClientPlayer> finalPlayers = players;
		int finalMax = max;
		Set<String> finalLeaped = leaped;
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		HudLayout.apply(graphics, font, HudLayout.Id.F7_LEAPED, () -> {
			float w = shownCount * SLOT_W + 24f;
			float x = (WIDTH - w) / 2f;
			F7Titles.card(graphics, x, 0f, w, HEIGHT, 1f);
			int count = 0;
			for (int i = 0; i < shownCount; i++) {
				if (finalLeaped.contains(finalNames.get(i))) {
					count++;
				}
			}
			F7Titles.text(graphics, font, "LEAPED", x + 12f, 6f, 0.75f, 0x8D8A84, 1f);
			String total = count + "/" + finalMax;
			F7Titles.text(graphics, font, total, x + w - 12f - F7Titles.textWidth(font, total, 0.75f), 6f, 0.75f,
				count >= finalMax ? 0x55FF55 : 0x8D8A84, 1f);
			for (int i = 0; i < shownCount; i++) {
				String name = finalNames.get(i);
				boolean in = finalLeaped.contains(name);
				// The head's colour fades in when they arrive rather than switching.
				float colour = COLOUR.getOrDefault(name, in ? 1f : 0f);
				colour += ((in ? 1f : 0f) - colour) * (1f - (float) Math.exp(-12f * dt));
				COLOUR.put(name, colour);
				float sx = x + 12f + i * SLOT_W;
				head(graphics, finalPlayers.get(i), sx + (SLOT_W - HEAD) / 2f, 15f, colour);
				String shown = GuiDraw.ellipsize(font, name, SLOT_W - 4f, false);
				float nw = F7Titles.textWidth(font, shown, 0.75f);
				int ink = mix(0x5B5954, 0xFFFFFF, colour);
				F7Titles.text(graphics, font, shown, sx + (SLOT_W - nw) / 2f, 36f, 0.75f, ink, 1f);
			}
		});
	}

	/** A player's face (with hat), {@code colour} 0 grey and dimmed, 1 as it is. */
	private static void head(GuiGraphicsExtractor graphics, AbstractClientPlayer player, float x, float y, float colour) {
		if (player == null) {
			GuiDraw.roundedFine(graphics, x, y, HEAD, HEAD, 2f, colour > 0.5f ? 0xFF555555 : 0xFF2C2C2C);
			return;
		}
		Identifier id = player.getSkin().body().texturePath();
		var texture = Minecraft.getInstance().getTextureManager().getTexture(id);
		GpuTextureView view = texture == null ? null : texture.getTextureView();
		if (view == null || !StrayShaders.ready() || !(graphics instanceof GuiGraphicsExtractorInvoker invoker)) {
			int tint = mix(0x6E6E6E, 0xFFFFFF, colour);
			GuiDraw.blit(graphics, id, x, y, HEAD, HEAD, 8, 8, 8, 8, 64, 64, 0xFF000000 | tint);
			GuiDraw.blit(graphics, id, x, y, HEAD, HEAD, 40, 8, 8, 8, 64, 64, 0xFF000000 | tint);
			return;
		}
		ensurePipeline();
		int keep = Math.max(0, Math.min(255, Math.round(colour * 255f)));
		int color = 0xFF000000 | (keep << 16);
		var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(HEAD, HEAD);
		invoker.stray$innerBlit(greyPipeline, view, sampler, 0, 0, 1, 1, 8f / 64f, 16f / 64f, 8f / 64f, 16f / 64f, color);
		invoker.stray$innerBlit(greyPipeline, view, sampler, 0, 0, 1, 1, 40f / 64f, 48f / 64f, 8f / 64f, 16f / 64f, color);
		graphics.pose().popMatrix();
	}

	private static synchronized void ensurePipeline() {
		if (greyPipeline == null) {
			greyPipeline = RenderCompat.guiBlit(Stray.id("pipeline/gui_grey_blit"), Stray.id("core/gui_grey_blit"), BlendFunction.TRANSLUCENT).build();
		}
	}

	// ------------------------------------------------------------------ helpers

	private static List<DungeonParty.Member> teammates(Minecraft client) {
		String self = client.getUser().getName();
		List<DungeonParty.Member> out = new ArrayList<>();
		for (DungeonParty.Member member : DungeonParty.all()) {
			if (!member.name.equalsIgnoreCase(self)) {
				out.add(member);
			}
		}
		return out;
	}

	private static AbstractClientPlayer entity(Minecraft client, DungeonParty.Member member) {
		if (member.entity != null && !member.entity.isRemoved()) {
			return member.entity;
		}
		if (client.level == null) {
			return null;
		}
		for (AbstractClientPlayer player : client.level.players()) {
			if (player.getGameProfile().name().equals(member.name)) {
				return player;
			}
		}
		return null;
	}

	/** F7 boss phase by height: 1 Maxor, 2 Storm, 3 Goldor (terminals), 4 Necron, 5 the relics. */
	private static int phase(double y) {
		if (y > 210) {
			return 1;
		}
		if (y > 155) {
			return 2;
		}
		if (y > 100) {
			return 3;
		}
		return y > 45 ? 4 : 5;
	}

	private static int p3Section(double x, double y, double z) {
		if (phase(Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.getY()) != 3) {
			return 0;
		}
		for (int i = 0; i < P3_SECTIONS.length; i++) {
			if (P3_SECTIONS[i].contains(x, y, z)) {
				return i + 1;
			}
		}
		return 0;
	}

	private static int mix(int from, int to, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}

	private static AABB box(double x1, double y1, double z1, double x2, double y2, double z2) {
		return new AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
	}
}
