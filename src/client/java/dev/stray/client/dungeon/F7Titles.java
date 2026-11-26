package dev.stray.client.dungeon;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.mixin.BossHealthOverlayAccessor;
import dev.stray.client.mixin.HudTitleInvoker;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudLayout;
import dev.stray.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F7 boss titles, after NoammAddons' F7Titles (CC0): short titles for crystals, the
 * wither phases and Storm's lightning, and a cleaner "who did which terminal (3/7)"
 * line in phase 3. Hypixel's own subtitles that get replaced are just not shown; the
 * packets themselves are left alone.
 *
 * <p>Everything here is drawn by {@link #render} on the HUD, and again on top of the
 * leap menu (with the game's own title), so none of it is hidden while leaping.
 */
public final class F7Titles {
	private static final Pattern CRYSTAL = Pattern.compile("^(\\d)/(\\d) Energy Crystals are now active!$");
	private static final Pattern ENRAGED = Pattern.compile("^⚠ (\\w+) is enraged! ⚠$");
	private static final Pattern TERMINAL = Pattern.compile("(.+) (?:activated|completed) a (terminal|device|lever)! \\((\\d)/(\\d)\\)");
	private static final long TITLE_MS = 2000L;
	private static final long TERMINAL_MS = 2500L;

	/** The boss card: a small label (who), the big text (what happened), the bar's colour. */
	private static String titleLabel = "";
	private static String titleText = "";
	private static int titleColor = 0xFFFFFF;
	private static long titleUntil;
	/** The terminal row: who, what kind, progress; or a plain line (gate, phase done). */
	private static String termName = "";
	private static int termClass = 0xAAAAAA;
	private static String termType = "";
	private static int termMin;
	private static int termMax;
	private static String termPlain = "";
	private static int termPlainColor = 0xFFFFFF;
	private static long terminalUntil;
	private static long lightningEnd;
	private static long lightningStart;
	private static boolean maxorDead;
	private static boolean goldorDead;
	private static boolean necronDead;
	private static boolean goldorStart;
	private static boolean necronStart;
	private static boolean wasBoss;
	private static long bossSince;
	/** Hypixel can send the same line as chat and as a subtitle: the second within a second is skipped. */
	private static String lastLine = "";
	private static long lastLineAt;

	private F7Titles() {
	}

	private static boolean active() {
		return StrayConfig.get().f7TitlesEnabled
			&& (DevMode.on() || SkyblockLocation.inDungeon && SkyblockLocation.inBoss && SkyblockLocation.dungeonFloor == 7);
	}

	// ------------------------------------------------------------------ input

	public static void tick(Minecraft client) {
		runSimulation();
		boolean boss = SkyblockLocation.inBoss;
		if (boss && !wasBoss) {
			bossSince = System.currentTimeMillis();
		}
		if (!boss && wasBoss || client.level == null) {
			SIM_AT.clear();
			SIM_DO.clear();
			clear();
		}
		wasBoss = boss;
		LeapCounter.tick(client);
		if (!active() || !StrayConfig.get().f7WitherTitles || client.gui == null) {
			return;
		}
		for (LerpingBossEvent event : ((BossHealthOverlayAccessor) client.gui.hud.getBossOverlay()).stray$events().values()) {
			if (event.getProgress() > 0f) {
				continue;
			}
			String name = event.getName().getString();
			if (name.contains("Maxor") && !maxorDead && System.currentTimeMillis() - bossSince > 6000L) {
				maxorDead = true;
				show("MAXOR", "Dead", 0xFF55FF, true);
			} else if (name.contains("Goldor") && !goldorDead && goldorStart) {
				goldorDead = true;
				show("GOLDOR", "Dead", 0xAAAAAA, true);
			} else if (name.contains("Necron") && !necronDead && necronStart) {
				necronDead = true;
				show("NECRON", "Dead", 0xFF5555, true);
			}
		}
	}

	private static void clear() {
		maxorDead = false;
		goldorDead = false;
		necronDead = false;
		goldorStart = false;
		necronStart = false;
		lightningEnd = 0L;
		titleUntil = 0L;
		terminalUntil = 0L;
		LeapCounter.reset();
	}

	/**
	 * A chat line. Hypixel sends terminal progress and the crystal count as chat (the
	 * subtitle may or may not come too), so both are read here as well as in
	 * {@link #onSubtitle}. Chat is never hidden.
	 */
	public static void onChat(String plain) {
		if (!active() || plain == null) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (config.f7WitherTitles) {
			switch (plain) {
				case "[BOSS] Maxor: YOU TRICKED ME!", "[BOSS] Maxor: THAT BEAM! IT HURTS! IT HURTS!!" -> show("MAXOR", "Stunned", 0xFF55FF, true);
				case "[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!" -> show("STORM", "Crushed", 0x55FFFF, true);
				case "[BOSS] Storm: I should have known that I stood no chance." -> show("STORM", "Dead", 0x55FFFF, true);
				default -> {
				}
			}
		}
		switch (plain) {
			case "[BOSS] Necron: ARGH!" -> necronStart = true;
			case "The Core entrance is opening!" -> goldorStart = true;
			default -> {
			}
		}
		line(plain);
	}

	/** A subtitle from the server. Returns false to leave it out (we show our own version). */
	public static boolean onSubtitle(Component text) {
		if (!active()) {
			return true;
		}
		String plain = text.getString();
		if (plain.isBlank()) {
			return true;
		}
		if (StrayConfig.get().f7CrystalTitles && plain.equals("The Energy Laser is charging up!")) {
			return false;
		}
		return !line(plain);
	}

	/** Crystal, enraged, terminal and gate lines, from chat or subtitle. True when it became one of ours. */
	private static boolean line(String plain) {
		StrayConfig config = StrayConfig.get();
		long now = System.currentTimeMillis();
		boolean repeat = plain.equals(lastLine) && now - lastLineAt < 1000L;
		if (config.f7CrystalTitles) {
			Matcher m = CRYSTAL.matcher(plain);
			if (m.find()) {
				if (!repeat) {
					int min = Integer.parseInt(m.group(1));
					int max = Integer.parseInt(m.group(2));
					show("CRYSTALS", min + " / " + max + " active", min == max ? 0x55FFFF : 0xFF5555, false);
				}
				return seen(plain, now);
			}
		}
		if (config.f7WitherTitles) {
			Matcher m = ENRAGED.matcher(plain);
			if (m.find()) {
				if (!repeat) {
					String boss = m.group(1);
					int color = boss.equals("Storm") ? 0x55FFFF : boss.equals("Maxor") ? 0xAA00AA : 0xFF5555;
					show(boss.toUpperCase(Locale.ROOT), "Enraged", color, true);
				}
				return seen(plain, now);
			}
		}
		if (config.f7TerminalTitles) {
			if (plain.equals("The gate has been destroyed!")) {
				if (!repeat) {
					plainTerminal("Gate destroyed", 0xFF5555);
				}
				return seen(plain, now);
			}
			if (plain.equals("The gate will open in 5 seconds!")) {
				if (!repeat) {
					plainTerminal("Gate opens in 5 s", 0xFF5555);
				}
				return seen(plain, now);
			}
			Matcher m = TERMINAL.matcher(plain);
			if (m.find()) {
				if (!repeat) {
					terminal(m.group(1), m.group(2), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
				}
				return seen(plain, now);
			}
		}
		return false;
	}

	private static boolean seen(String plain, long now) {
		lastLine = plain;
		lastLineAt = now;
		return true;
	}

	/** A title from the server. Returns false to leave it out (Storm's lightning countdown becomes a timer). */
	public static boolean onTitle(Component text) {
		if (!active() || !StrayConfig.get().f7LightningTimer) {
			return true;
		}
		String plain = text.getString().trim();
		if (plain.isEmpty()) {
			return true;
		}
		int number;
		try {
			number = Integer.parseInt(plain);
		} catch (NumberFormatException e) {
			return true;
		}
		long now = System.currentTimeMillis();
		if (lightningEnd <= now && (number == 4 || number == 6)) {
			lightningStart = now;
			lightningEnd = now + Math.round(number * 1.35 * 1000.0);
		}
		return false;
	}

	private static void terminal(String name, String type, int min, int max) {
		if (min == max) {
			plainTerminal("Phase done", 0x55FF55);
			return;
		}
		// Chat may put a rank in front of the name.
		String clean = name.replaceAll("^(?:\\[[^]]+] )+", "").trim();
		int classColor = 0xAAAAAA;
		for (DungeonParty.Member m : DungeonParty.all()) {
			if (m.name.equals(clean) && m.clazz != null) {
				classColor = m.clazz.color & 0xFFFFFF;
			}
		}
		termName = clean;
		termClass = classColor;
		termType = type;
		termMin = min;
		termMax = max;
		termPlain = "";
		terminalUntil = System.currentTimeMillis() + TERMINAL_MS;
	}

	private static void plainTerminal(String text, int color) {
		termPlain = text;
		termPlainColor = color;
		terminalUntil = System.currentTimeMillis() + TERMINAL_MS;
	}

	/** The boss card. {@code ping}: with the pling, for the moments that matter. */
	static void show(String label, String text, int color, boolean ping) {
		titleLabel = label;
		titleText = text;
		titleColor = color & 0xFFFFFF;
		titleUntil = System.currentTimeMillis() + TITLE_MS;
		if (ping) {
			ping();
		}
	}

	private static void ping() {
		Minecraft client = Minecraft.getInstance();
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.25f));
	}

	// ------------------------------------------------------------------ debug

	/** A simulated run: what still has to be fed in, and when (ms after the start). */
	private static final java.util.List<long[]> SIM_AT = new java.util.ArrayList<>();
	private static final java.util.List<Runnable> SIM_DO = new java.util.ArrayList<>();
	private static long simStart;

	/**
	 * Debug (/stray debug dev titles): plays an F7 boss fight's lines through the same code the
	 * real ones go through (chat, subtitles, the lightning title), about 30 s, so every title
	 * can be checked on any server. Dev mode stays on while it runs.
	 */
	public static void simulate() {
		DevMode.set(true);
		SIM_AT.clear();
		SIM_DO.clear();
		clear();
		simStart = System.currentTimeMillis();
		String me = Minecraft.getInstance().getUser().getName();
		String[] team = {me, "Fotca", "OrangeMango", "Noamm"};
		long t = 0L;
		t = sim(t + 500L, () -> onChat("1/2 Energy Crystals are now active!"));
		t = sim(t + 2200L, () -> onChat("2/2 Energy Crystals are now active!"));
		t = sim(t + 2200L, () -> onChat("[BOSS] Maxor: YOU TRICKED ME!"));
		t = sim(t + 2200L, () -> onSubtitle(Component.literal("⚠ Maxor is enraged! ⚠")));
		t = sim(t + 2200L, () -> show("MAXOR", "Dead", 0xFF55FF, true));
		t = sim(t + 2200L, () -> onChat("[BOSS] Storm: Oof"));
		t = sim(t + 2200L, () -> onTitle(Component.literal("6")));
		t += 8500L;
		for (int i = 1; i <= 7; i++) {
			int step = i;
			String who = team[i % team.length];
			String kind = i % 3 == 0 ? "device" : i == 5 ? "lever" : "terminal";
			String verb = kind.equals("terminal") ? "activated" : "completed";
			t = sim(t + 1400L, () -> onChat(who + " " + verb + " a " + kind + "! (" + step + "/7)"));
		}
		t = sim(t + 1800L, () -> onChat("The gate will open in 5 seconds!"));
		t = sim(t + 2200L, () -> onSubtitle(Component.literal("The gate has been destroyed!")));
		t = sim(t + 2200L, () -> show("GOLDOR", "Dead", 0xAAAAAA, true));
		sim(t + 2200L, () -> show("NECRON", "Dead", 0xFF5555, true));
	}

	private static long sim(long at, Runnable action) {
		SIM_AT.add(new long[] {at});
		SIM_DO.add(action);
		return at;
	}

	/** True while a simulated run is playing. */
	public static boolean simulating() {
		return !SIM_DO.isEmpty();
	}

	private static void runSimulation() {
		long elapsed = System.currentTimeMillis() - simStart;
		while (!SIM_DO.isEmpty() && SIM_AT.get(0)[0] <= elapsed) {
			SIM_AT.remove(0);
			SIM_DO.remove(0).run();
		}
	}

	// ------------------------------------------------------------------ drawing

	private static final float TITLE_W = 200f;
	private static final float TITLE_H = 40f;
	private static final float LIGHTNING_W = 84f;
	private static final float LIGHTNING_H = 44f;
	private static final float TERMINAL_W = 240f;
	private static final float TERMINAL_H = 18f;
	private static final int MUTED = 0x8D8A84;

	public static float boxWidth(HudLayout.Id id) {
		return switch (id) {
			case F7_LIGHTNING -> LIGHTNING_W;
			case F7_TERMINAL -> TERMINAL_W;
			case F7_LEAPED -> LeapCounter.WIDTH;
			default -> TITLE_W;
		};
	}

	public static float boxHeight(HudLayout.Id id) {
		return switch (id) {
			case F7_LIGHTNING -> LIGHTNING_H;
			case F7_TERMINAL -> TERMINAL_H;
			case F7_LEAPED -> LeapCounter.HEIGHT;
			default -> TITLE_H;
		};
	}

	/**
	 * Our F7 titles, each in its own HUD element (movable in the HUD editor); drawn on the
	 * HUD and again on top of the leap menu. In the HUD editor each shows an example.
	 */
	public static void render(GuiGraphicsExtractor graphics, int width, int height) {
		StrayConfig config = StrayConfig.get();
		if (!config.f7TitlesEnabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		long now = System.currentTimeMillis();
		boolean editor = HudLayout.editorOpen();
		if (lightningEnd != 0L && lightningEnd <= now) {
			lightningEnd = 0L;
			show("STORM", "Lightning ended", 0x55FF55, true);
		}
		if (config.f7LightningTimer && (lightningEnd != 0L || editor)) {
			boolean live = lightningEnd != 0L;
			double left = live ? (lightningEnd - now) / 1000.0 : 3.2;
			float part = live ? (float) ((lightningEnd - now) / (double) Math.max(1L, lightningEnd - lightningStart)) : 0.6f;
			HudLayout.apply(graphics, font, HudLayout.Id.F7_LIGHTNING, () -> lightning(graphics, font, left, part));
		}
		if (titleUntil > now || editor && (config.f7CrystalTitles || config.f7WitherTitles || config.f7LightningTimer)) {
			boolean live = titleUntil > now;
			float alpha = live ? fade(titleUntil - now, TITLE_MS) : 1f;
			float part = live ? (titleUntil - now) / (float) TITLE_MS : 0.7f;
			String label = live ? titleLabel : "STORM";
			String text = live ? titleText : "Crushed";
			int color = live ? titleColor : 0x55FFFF;
			HudLayout.apply(graphics, font, HudLayout.Id.F7_TITLE, () -> titleCard(graphics, font, label, text, color, part, alpha));
		}
		if (config.f7TerminalTitles && (terminalUntil > now || editor)) {
			boolean live = terminalUntil > now;
			float alpha = live ? fade(terminalUntil - now, TERMINAL_MS) : 1f;
			HudLayout.apply(graphics, font, HudLayout.Id.F7_TERMINAL, () -> {
				if (live) {
					terminalRow(graphics, font, termName, termClass, termType, termMin, termMax, termPlain, termPlainColor, alpha);
				} else {
					terminalRow(graphics, font, client.getUser().getName(), 0x55FFFF, "terminal", 5, 7, "", 0, 1f);
				}
			});
		}
		LeapCounter.render(graphics, font, editor);
	}

	/** On top of the leap menu: ours, plus the game's own title/subtitle the menu would cover. */
	public static void renderOverMenu(GuiGraphicsExtractor graphics, int width, int height) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null && client.gui.hud != null) {
			((HudTitleInvoker) client.gui.hud).stray$extractTitle(graphics, client.getDeltaTracker());
		}
		render(graphics, width, height);
	}

	private static void titleCard(GuiGraphicsExtractor graphics, Font font, String label, String text, int color, float part, float alpha) {
		float labelW = textWidth(font, label, 0.75f);
		float textW = textWidth(font, text, 1.6f);
		float w = Math.min(TITLE_W, Math.max(110f, Math.max(labelW, textW) + 28f));
		float x = (TITLE_W - w) / 2f;
		card(graphics, x, 0f, w, TITLE_H, alpha);
		text(graphics, font, label, TITLE_W / 2f - labelW / 2f, 6f, 0.75f, MUTED, alpha);
		text(graphics, font, text, TITLE_W / 2f - textW / 2f, 15f, 1.6f, 0xFFFFFF, alpha);
		bar(graphics, x + 10f, TITLE_H - 6f, w - 20f, part, color, alpha);
	}

	private static void lightning(GuiGraphicsExtractor graphics, Font font, double left, float part) {
		card(graphics, 0f, 0f, LIGHTNING_W, LIGHTNING_H, 1f);
		String label = "LIGHTNING";
		text(graphics, font, label, LIGHTNING_W / 2f - textWidth(font, label, 0.75f) / 2f, 6f, 0.75f, MUTED, 1f);
		String number = String.format(Locale.ROOT, "%.1f", Math.max(0.0, left));
		text(graphics, font, number, LIGHTNING_W / 2f - textWidth(font, number, 2f) / 2f, 15f, 2f, 0xFF5555, 1f);
		bar(graphics, 10f, LIGHTNING_H - 6f, LIGHTNING_W - 20f, part, 0xFF5555, 1f);
	}

	/** Name in the class colour, a chip for the kind, one segment per step, the count. */
	private static void terminalRow(GuiGraphicsExtractor graphics, Font font, String name, int classColor, String type, int min, int max,
		String plain, int plainColor, float alpha) {
		float h = TERMINAL_H;
		if (!plain.isEmpty()) {
			float w = textWidth(font, plain, 1f) + 24f;
			float x = (TERMINAL_W - w) / 2f;
			card(graphics, x, 0f, w, h, alpha);
			text(graphics, font, plain, x + 12f, 5f, 1f, plainColor, alpha);
			return;
		}
		String kind = switch (type) {
			case "device" -> "Device";
			case "lever" -> "Lever";
			default -> "Terminal";
		};
		int kindColor = switch (type) {
			case "device" -> 0x55FFFF;
			case "lever" -> 0xFF5555;
			default -> 0xD58AFF;
		};
		String count = min + "/" + max;
		float nameW = textWidth(font, name, 1f);
		float kindW = textWidth(font, kind, 0.75f) + 10f;
		int segments = Math.max(1, Math.min(max, 12));
		float segW = segments * 10f - 2f;
		float countW = textWidth(font, count, 1f);
		float w = 12f + nameW + 8f + kindW + 8f + segW + 8f + countW + 12f;
		float x = (TERMINAL_W - w) / 2f;
		card(graphics, x, 0f, w, h, alpha);
		float cx = x + 12f;
		text(graphics, font, name, cx, 5f, 1f, classColor, alpha);
		cx += nameW + 8f;
		GuiDraw.hairline(graphics, cx, 3.5f, kindW, 11f, 2f, fade(0xFF000000 | kindColor, alpha * 0.55f));
		text(graphics, font, kind, cx + 5f, 6.5f, 0.75f, kindColor, alpha);
		cx += kindW + 8f;
		float pct = max <= 0 ? 0f : min / (float) max;
		for (int i = 0; i < segments; i++) {
			boolean on = i < Math.round(pct * segments);
			GuiDraw.roundedFine(graphics, cx + i * 10f, 7f, 8f, 4f, 1f, fade(on ? 0xFF55FF55 : 0xFF2C2C2C, alpha));
		}
		cx += segW + 8f;
		text(graphics, font, count, cx, 5f, 1f, 0xFFFFFF, alpha);
	}

	/** A card behind an element, unless backgrounds are off in the settings. */
	static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float alpha) {
		if (StrayConfig.get().f7NoBackgrounds || alpha <= 0.02f) {
			return;
		}
		float was = GuiDraw.alphaScale;
		GuiDraw.alphaScale = was * alpha;
		try {
			dev.stray.client.render.HudChrome.box(graphics, x, y, w, h, Theme.HUD_WINDOW);
		} finally {
			GuiDraw.alphaScale = was;
		}
	}

	/** A thin bar showing how much time is left. */
	private static void bar(GuiGraphicsExtractor graphics, float x, float y, float w, float part, int color, float alpha) {
		part = Math.max(0f, Math.min(1f, part));
		GuiDraw.roundedFine(graphics, x, y, w, 2f, 1f, fade(0xFF2C2C2C, alpha * 0.8f));
		if (part > 0.001f) {
			GuiDraw.roundedFine(graphics, x, y, w * part, 2f, 1f, fade(0xFF000000 | color, alpha));
		}
	}

	static float textWidth(Font font, String text, float scale) {
		return font.width(dev.stray.client.ui.MenuFont.look(Component.literal(text))) * scale;
	}

	/** Text in the theme's font; with backgrounds off it gets a shadow so it reads on the world. */
	static void text(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float scale, int rgb, float alpha) {
		if (text == null || text.isEmpty() || alpha <= 0.02f) {
			return;
		}
		int a = Math.max(4, Math.round(alpha * 255f));
		Component shown = dev.stray.client.ui.MenuFont.look(Component.literal(text));
		GuiDraw.text(graphics, font, shown, x, y, scale, (a << 24) | (rgb & 0xFFFFFF), StrayConfig.get().f7NoBackgrounds);
	}

	static int fade(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static float fade(long left, long total) {
		float in = Math.min(1f, (total - left) / 150f);
		float out = Math.min(1f, left / 300f);
		return Math.max(0f, Math.min(in, out));
	}
}
