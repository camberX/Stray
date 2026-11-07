package dev.stray.client.dungeon;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.mixin.BossHealthOverlayAccessor;
import dev.stray.client.mixin.HudTitleInvoker;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

	private static Component title = Component.empty();
	private static Component subtitle = Component.empty();
	private static long titleUntil;
	private static Component terminalLine = Component.empty();
	private static long terminalUntil;
	private static long lightningEnd;
	private static boolean maxorDead;
	private static boolean goldorDead;
	private static boolean necronDead;
	private static boolean goldorStart;
	private static boolean necronStart;
	private static boolean wasBoss;
	private static long bossSince;

	private F7Titles() {
	}

	private static boolean active() {
		return StrayConfig.get().f7TitlesEnabled
			&& (DevMode.on() || SkyblockLocation.inDungeon && SkyblockLocation.inBoss && SkyblockLocation.dungeonFloor == 7);
	}

	// ------------------------------------------------------------------ input

	public static void tick(Minecraft client) {
		boolean boss = SkyblockLocation.inBoss;
		if (boss && !wasBoss) {
			bossSince = System.currentTimeMillis();
		}
		if (!boss && wasBoss || client.level == null) {
			clear();
		}
		wasBoss = boss;
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
				show("§dMaxor Dead!");
			} else if (name.contains("Goldor") && !goldorDead && goldorStart) {
				goldorDead = true;
				show("§7Goldor Dead!");
			} else if (name.contains("Necron") && !necronDead && necronStart) {
				necronDead = true;
				show("§cNecron Dead!!");
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
	}

	public static void onChat(String plain) {
		if (!active() || !StrayConfig.get().f7WitherTitles) {
			return;
		}
		switch (plain) {
			case "[BOSS] Maxor: YOU TRICKED ME!", "[BOSS] Maxor: THAT BEAM! IT HURTS! IT HURTS!!" -> show("§dMaxor Stunned!");
			case "[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!" -> show("§bStorm Crushed!");
			case "[BOSS] Storm: I should have known that I stood no chance." -> show("§bStorm Dead!");
			case "[BOSS] Necron: ARGH!" -> necronStart = true;
			case "The Core entrance is opening!" -> goldorStart = true;
			default -> {
			}
		}
	}

	/** A subtitle from the server. Returns false to leave it out (we show our own version). */
	public static boolean onSubtitle(Component text) {
		if (!active()) {
			return true;
		}
		StrayConfig config = StrayConfig.get();
		String plain = text.getString();
		if (plain.isBlank()) {
			return true;
		}
		if (config.f7CrystalTitles) {
			if (plain.equals("The Energy Laser is charging up!")) {
				return false;
			}
			Matcher m = CRYSTAL.matcher(plain);
			if (m.find()) {
				int min = Integer.parseInt(m.group(1));
				int max = Integer.parseInt(m.group(2));
				String color = min == max ? "§b" : "§c";
				subtitleOnly("§3Crystal §r(" + color + min + "§r/§b" + max + "§r)");
				return false;
			}
		}
		if (config.f7WitherTitles) {
			Matcher m = ENRAGED.matcher(plain);
			if (m.find()) {
				String boss = m.group(1);
				show((boss.equals("Storm") ? "§b" : boss.equals("Maxor") ? "§5" : "") + plain);
				return false;
			}
		}
		if (config.f7TerminalTitles) {
			if (plain.equals("The gate has been destroyed!")) {
				terminal(Component.literal("§cGate Destroyed!"));
				return false;
			}
			if (plain.equals("The gate will open in 5 seconds!")) {
				terminal(Component.literal("§c§lGATE!"));
				return false;
			}
			Matcher m = TERMINAL.matcher(plain);
			if (m.find()) {
				terminal(terminalText(m.group(1), m.group(2), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))));
				return false;
			}
		}
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
			lightningEnd = now + Math.round(number * 1.35 * 1000.0);
		}
		return false;
	}

	private static Component terminalText(String name, String type, int min, int max) {
		if (min == max) {
			return Component.literal("§a§lPhase Done!");
		}
		DungeonParty.Member member = null;
		for (DungeonParty.Member m : DungeonParty.all()) {
			if (m.name.equals(name)) {
				member = m;
			}
		}
		int classColor = member != null && member.clazz != null ? member.clazz.color : 0xAAAAAA;
		String typeText = switch (type) {
			case "terminal" -> "§5Terminal";
			case "device" -> "§bDevice";
			case "lever" -> "§cLever";
			default -> "";
		};
		float pct = max <= 0 ? 0f : min / (float) max;
		String progress = (pct >= 0.75f ? "§a" : pct >= 0.5f ? "§e" : pct >= 0.25f ? "§6" : "§c") + min + "§8/§a" + max;
		MutableComponent line = Component.literal(name).withColor(classColor & 0xFFFFFF);
		return line.append(Component.literal(" " + typeText + " §f(" + progress + "§f)"));
	}

	private static void show(String text) {
		title = Component.empty();
		subtitle = Component.literal(text);
		titleUntil = System.currentTimeMillis() + TITLE_MS;
		ping();
	}

	private static void subtitleOnly(String text) {
		title = Component.empty();
		subtitle = Component.literal(text);
		titleUntil = System.currentTimeMillis() + TITLE_MS;
	}

	private static void terminal(Component line) {
		terminalLine = line;
		terminalUntil = System.currentTimeMillis() + TERMINAL_MS;
	}

	private static void ping() {
		Minecraft client = Minecraft.getInstance();
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.25f));
	}

	/** Debug: shows each kind once so the look can be checked anywhere. */
	public static void preview() {
		show("§bStorm Crushed!");
		terminal(terminalText(Minecraft.getInstance().getUser().getName(), "terminal", 5, 7));
		lightningEnd = System.currentTimeMillis() + 5400L;
	}

	// ------------------------------------------------------------------ drawing

	private static final float TITLE_W = 220f;
	private static final float TITLE_H = 9f * 2.5f + 4f + 9f * 1.5f;
	private static final float LIGHTNING_W = 72f;
	private static final float LIGHTNING_H = 9f * 3f;
	private static final float TERMINAL_W = 170f;
	private static final float TERMINAL_H = 10f;

	public static float boxWidth(HudLayout.Id id) {
		return switch (id) {
			case F7_LIGHTNING -> LIGHTNING_W;
			case F7_TERMINAL -> TERMINAL_W;
			default -> TITLE_W;
		};
	}

	public static float boxHeight(HudLayout.Id id) {
		return switch (id) {
			case F7_LIGHTNING -> LIGHTNING_H;
			case F7_TERMINAL -> TERMINAL_H;
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
			show("§aStorm's Lightning Ended!");
		}
		if (config.f7LightningTimer && (lightningEnd != 0L || editor)) {
			double left = editor && lightningEnd == 0L ? 3.2 : (lightningEnd - now) / 1000.0;
			Component text = Component.literal(String.format(Locale.ROOT, "§c§l%.1f", left));
			HudLayout.apply(graphics, font, HudLayout.Id.F7_LIGHTNING, () -> centered(graphics, font, text, LIGHTNING_W * 0.5f, 0f, 3f, 1f));
		}
		if (titleUntil > now || editor && (config.f7CrystalTitles || config.f7WitherTitles || config.f7LightningTimer)) {
			boolean live = titleUntil > now;
			float alpha = live ? fade(titleUntil - now, TITLE_MS) : 1f;
			Component top = live ? title : Component.empty();
			Component bottom = live ? subtitle : Component.literal("§bStorm Crushed!");
			HudLayout.apply(graphics, font, HudLayout.Id.F7_TITLE, () -> {
				centered(graphics, font, top, TITLE_W * 0.5f, 0f, 2.5f, alpha);
				centered(graphics, font, bottom, TITLE_W * 0.5f, 9f * 2.5f + 4f, 1.5f, alpha);
			});
		}
		if (config.f7TerminalTitles && (terminalUntil > now || editor)) {
			boolean live = terminalUntil > now;
			float alpha = live ? fade(terminalUntil - now, TERMINAL_MS) : 1f;
			Component line = live ? terminalLine : terminalText(client.getUser().getName(), "terminal", 5, 7);
			HudLayout.apply(graphics, font, HudLayout.Id.F7_TERMINAL, () -> centered(graphics, font, line, TERMINAL_W * 0.5f, 0.5f, 1f, alpha));
		}
	}

	/** On top of the leap menu: ours, plus the game's own title/subtitle the menu would cover. */
	public static void renderOverMenu(GuiGraphicsExtractor graphics, int width, int height) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null && client.gui.hud != null) {
			((HudTitleInvoker) client.gui.hud).stray$extractTitle(graphics, client.getDeltaTracker());
		}
		render(graphics, width, height);
	}

	private static float fade(long left, long total) {
		float in = Math.min(1f, (total - left) / 150f);
		float out = Math.min(1f, left / 300f);
		return Math.max(0f, Math.min(in, out));
	}

	private static void centered(GuiGraphicsExtractor graphics, Font font, Component text, float cx, float y, float scale, float alpha) {
		if (text == null || text.getString().isEmpty() || alpha <= 0.02f) {
			return;
		}
		int a = Math.max(4, Math.round(alpha * 255f));
		float w = font.width(text) * scale;
		GuiDraw.text(graphics, font, text, cx - w * 0.5f, y, scale, (a << 24) | 0xFFFFFF, true);
	}
}
