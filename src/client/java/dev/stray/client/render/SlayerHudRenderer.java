package dev.stray.client.render;

import dev.stray.client.config.SlayerSettings;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.slayer.SlayerAlerts;
import dev.stray.client.slayer.SlayerApi;
import dev.stray.client.slayer.SlayerBosses;
import dev.stray.client.slayer.SlayerKills;
import dev.stray.client.slayer.SlayerProfit;
import dev.stray.client.slayer.SlayerRng;
import dev.stray.client.slayer.SlayerType;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The slayer HUD elements (quest + boss, profit tracker, RNG meter, remaining kills, dagger
 * helper, fire pillar) drawn in Stray's panel style; each one is placed, scaled and docked
 * through the HUD editor like the other elements. Also draws the slayer alert banner.
 */
public final class SlayerHudRenderer {
	private static final float WIDTH = 150f;
	private static final float PAD = 5f;
	private static final float LINE = 10f;
	private static final float BAR = 5f;
	private static final int RED = 0xFFFF5A4A;
	private static final int GREEN = 0xFF55D17A;
	private static final int GOLD = 0xFFFFB84A;

	/** One text row: left text, optional right text, colours; or a progress bar when bar >= 0. */
	private record Row(String left, String right, int leftColor, int rightColor, float bar, int barColor) {
		static Row text(String left, String right, int leftColor, int rightColor) {
			return new Row(left, right, leftColor, rightColor, -1f, 0);
		}

		static Row bar(float t, int color) {
			return new Row("", "", 0, 0, Mth.clamp(t, 0f, 1f), color);
		}

		float height() {
			return bar >= 0 ? BAR + 3f : LINE;
		}
	}

	private SlayerHudRenderer() {
	}

	private static SlayerSettings settings() {
		return StrayConfig.get().slayer;
	}

	private static boolean editor() {
		return HudLayout.editorOpen();
	}

	// ---------------------------------------------------------------- visibility

	public static boolean configured(HudLayout.Id id) {
		SlayerSettings s = settings();
		return switch (id) {
			case SLAYER -> s.hud;
			case SLAYER_PROFIT -> s.profit;
			case SLAYER_RNG -> s.rng;
			case SLAYER_KILLS -> s.kills;
			case SLAYER_DAGGER -> s.inferno && s.daggers;
			case SLAYER_PILLAR -> s.inferno && s.pillar;
			default -> false;
		};
	}

	private static boolean live(HudLayout.Id id) {
		SlayerSettings s = settings();
		if (!SlayerApi.active()) {
			return false;
		}
		return switch (id) {
			case SLAYER -> SlayerApi.hasQuest() && (!s.hudAreaOnly || SlayerApi.inAnyArea());
			case SLAYER_PROFIT -> SlayerApi.hasQuest() && SlayerApi.inCorrectArea();
			case SLAYER_RNG -> SlayerApi.hasQuest();
			case SLAYER_KILLS -> SlayerApi.hasQuest() && !SlayerKills.snapshot().lines().isEmpty();
			case SLAYER_DAGGER -> SlayerBosses.daggerLines() != null;
			case SLAYER_PILLAR -> SlayerBosses.pillar() != null;
			default -> false;
		};
	}

	// ---------------------------------------------------------------- sizes

	public static float width(HudLayout.Id id, Font font) {
		if (font == null) {
			font = Minecraft.getInstance().font;
		}
		return switch (id) {
			case SLAYER_DAGGER -> {
				String[] lines = daggerOrSample();
				yield PAD * 2f + 2f + Math.max(GuiDraw.smallWidth(font, lines[0]), GuiDraw.smallWidth(font, lines[1]));
			}
			case SLAYER_PILLAR -> PAD * 2f + 2f + GuiDraw.smallWidth(font, pillarText());
			case SLAYER_PROFIT -> 170f;
			default -> WIDTH;
		};
	}

	public static float height(HudLayout.Id id) {
		return switch (id) {
			case SLAYER_DAGGER -> PAD * 2f + LINE * 2f;
			case SLAYER_PILLAR -> PAD * 2f + LINE;
			default -> heightOf(rows(id));
		};
	}

	private static float heightOf(List<Row> rows) {
		float h = PAD * 2f;
		for (Row row : rows) {
			h += row.height();
		}
		return h;
	}

	// ---------------------------------------------------------------- draw

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		boolean editing = editor();
		if (client.screen != null && !editing) {
			drawAlert(graphics, client.font);
			return;
		}
		Font font = client.font;
		for (HudLayout.Id id : new HudLayout.Id[] {HudLayout.Id.SLAYER, HudLayout.Id.SLAYER_PROFIT, HudLayout.Id.SLAYER_RNG,
			HudLayout.Id.SLAYER_KILLS, HudLayout.Id.SLAYER_DAGGER, HudLayout.Id.SLAYER_PILLAR}) {
			if (!configured(id) || !(editing || live(id))) {
				continue;
			}
			HudLayout.apply(graphics, font, id, () -> draw(graphics, font, id));
			HudChrome.element(null);
		}
		if (!editing) {
			drawAlert(graphics, font);
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, HudLayout.Id id) {
		float w = width(id, font);
		float h = height(id);
		HudChrome.panel(graphics, 0, 0, w, h, 5, Theme.HUD_WINDOW, Theme.HUD_LINE);
		if (id == HudLayout.Id.SLAYER_DAGGER) {
			String[] lines = daggerOrSample();
			GuiDraw.small(graphics, font, lines[0], PAD + 1, PAD, Theme.TEXT);
			GuiDraw.small(graphics, font, lines[1], PAD + 1, PAD + LINE, Theme.MUTED);
			return;
		}
		if (id == HudLayout.Id.SLAYER_PILLAR) {
			GuiDraw.small(graphics, font, pillarText(), PAD + 1, PAD, GOLD);
			return;
		}
		float y = PAD;
		for (Row row : rows(id)) {
			if (row.bar >= 0) {
				float bw = w - PAD * 2f;
				HudChrome.rounded(graphics, PAD, y + 1, bw, BAR, 2, 0x40FFFFFF);
				if (row.bar > 0.01f) {
					HudChrome.rounded(graphics, PAD, y + 1, Math.max(3f, bw * row.bar), BAR, 2, row.barColor);
				}
			} else {
				float rightW = row.right.isEmpty() ? 0 : GuiDraw.smallWidth(font, row.right);
				String left = GuiDraw.ellipsize(font, row.left, w - PAD * 2f - rightW - (rightW > 0 ? 6 : 0), true);
				GuiDraw.small(graphics, font, left, PAD + 1, y, row.leftColor);
				if (rightW > 0) {
					GuiDraw.small(graphics, font, row.right, w - PAD - rightW, y, row.rightColor);
				}
			}
			y += row.height();
		}
	}

	private static List<Row> rows(HudLayout.Id id) {
		boolean sample = editor() && !live(id);
		return switch (id) {
			case SLAYER -> sample ? sampleSlayer() : slayerRows();
			case SLAYER_PROFIT -> sample ? sampleProfit() : profitRows();
			case SLAYER_RNG -> sample ? sampleRng() : rngRows();
			case SLAYER_KILLS -> sample ? sampleKills() : killRows();
			default -> List.of();
		};
	}

	// ---------------------------------------------------------------- slayer quest + boss

	private static List<Row> slayerRows() {
		SlayerSettings s = settings();
		List<Row> rows = new ArrayList<>();
		SlayerType type = SlayerApi.type();
		String category = SlayerApi.category();
		rows.add(Row.text("SLAYER", category.isEmpty() ? "" : shortName(category), Theme.ACCENT, Theme.TEXT));
		SlayerApi.State state = SlayerApi.state();
		if (s.hudProgress) {
			String progress = SlayerApi.progress();
			switch (state) {
				case BOSS_FIGHT -> rows.add(Row.text("Slay the boss!", "", RED, 0));
				case SLAIN -> rows.add(Row.text("Boss slain!", "", GREEN, 0));
				case COCOONED -> rows.add(Row.text("Boss cocooned", "", GOLD, 0));
				case FAILED -> rows.add(Row.text("Quest failed", "", RED, 0));
				default -> {
					float[] frac = fraction(progress);
					String right = frac == null ? "" : String.format(Locale.ROOT, "%.0f%%", frac[0] * 100f);
					rows.add(Row.text(progress.isEmpty() ? "Grinding" : progress, right, Theme.TEXT, Theme.MUTED));
					if (frac != null) {
						rows.add(Row.bar(frac[0], Theme.ACCENT));
					}
				}
			}
		}
		if (s.hudBoss && SlayerApi.boss() != null) {
			long health = SlayerApi.bossHealth();
			long max = SlayerApi.bossMaxHealth();
			String hp = health < 0 ? "?" : shortNumber(health) + " ❤";
			String timer = s.hudTimer ? clock(SlayerApi.bossAliveMs()) : "";
			rows.add(Row.text(type == null ? "Boss" : type.displayName, timer, Theme.TEXT, Theme.MUTED));
			if (max > 0 && health >= 0) {
				float t = (float) health / max;
				rows.add(Row.text(hp, String.format(Locale.ROOT, "%.0f%%", t * 100f), healthColor(t), Theme.MUTED));
				rows.add(Row.bar(t, healthColor(t)));
			}
			if (s.hudPhase) {
				for (SlayerBosses.Phase phase : SlayerBosses.phases()) {
					if (!phase.text().isEmpty()) {
						rows.add(Row.text(phase.text(), "", 0xFF000000 | phase.rgb(), 0));
					}
				}
			}
		}
		return rows;
	}

	private static List<Row> sampleSlayer() {
		List<Row> rows = new ArrayList<>();
		rows.add(Row.text("SLAYER", "Voidgloom IV", Theme.ACCENT, Theme.TEXT));
		rows.add(Row.text("Combat XP 1,640/2,400", "68%", Theme.TEXT, Theme.MUTED));
		rows.add(Row.bar(0.68f, Theme.ACCENT));
		rows.add(Row.text("Voidgloom Seraph", "0:42", Theme.TEXT, Theme.MUTED));
		rows.add(Row.text("31.2M ❤", "62%", healthColor(0.62f), Theme.MUTED));
		rows.add(Row.bar(0.62f, healthColor(0.62f)));
		rows.add(Row.text("Phase 2/6", "", 0xFFFFFF55, 0));
		rows.add(Row.text("43 hits", "", 0xFFFFAA00, 0));
		return rows;
	}

	// ---------------------------------------------------------------- profit

	private static List<Row> profitRows() {
		return profit(SlayerProfit.snapshot());
	}

	private static List<Row> profit(SlayerProfit.Snapshot snap) {
		SlayerSettings s = settings();
		List<Row> rows = new ArrayList<>();
		rows.add(Row.text(shortName(snap.title()), snap.session() ? "Session" : "Total", Theme.ACCENT, Theme.MUTED));
		int limit = Math.max(1, s.profitRows);
		int shown = 0;
		for (SlayerProfit.Row row : snap.rows()) {
			if (shown++ >= limit) {
				break;
			}
			rows.add(Row.text(compact(row.amount()) + "x " + row.name(), coins(row.value()), Theme.TEXT, GOLD));
		}
		if (snap.rows().size() > limit) {
			rows.add(Row.text("+" + (snap.rows().size() - limit) + " more", "", Theme.MUTED, 0));
		}
		if (snap.mobCoins() > 0) {
			rows.add(Row.text("Mob kill coins", coins(snap.mobCoins()), Theme.MUTED, GOLD));
		}
		if (snap.spawnCost() > 0) {
			rows.add(Row.text("Slayer spawn costs", "-" + coins(snap.spawnCost()), Theme.MUTED, RED));
		}
		rows.add(Row.text("Bosses killed", compact(snap.bosses()), Theme.MUTED, Theme.TEXT));
		if (snap.bosses() > 0) {
			rows.add(Row.text("Per boss", coins(snap.perBoss()), Theme.MUTED, signColor(snap.perBoss())));
		}
		if (snap.perHour() != 0) {
			rows.add(Row.text("Per hour", coins(snap.perHour()), Theme.MUTED, signColor(snap.perHour())));
		}
		rows.add(Row.text("Total profit", coins(snap.profit()), Theme.TEXT, signColor(snap.profit())));
		return rows;
	}

	private static List<Row> sampleProfit() {
		List<SlayerProfit.Row> items = List.of(
			new SlayerProfit.Row("SUMMONING_EYE", "Summoning Eye", 2, 2, 1_860_000),
			new SlayerProfit.Row("NULL_ATOM", "Null Atom", 84, 31, 210_000),
			new SlayerProfit.Row("TWILIGHT_ARROW_POISON", "Twilight Arrow Poison", 640, 22, 96_000));
		return profit(new SlayerProfit.Snapshot("Voidgloom Seraph IV", items, 48_200, 400_000, 8, 1_200_000, 2_166_000,
			1_814_200, false));
	}

	// ---------------------------------------------------------------- RNG meter

	private static List<Row> rngRows() {
		return rng(SlayerRng.snapshot());
	}

	private static List<Row> rng(SlayerRng.Snapshot snap) {
		List<Row> rows = new ArrayList<>();
		rows.add(Row.text("RNG METER", shortName(snap.slayer()), Theme.ACCENT, Theme.MUTED));
		if (snap.message() != null) {
			rows.add(Row.text(snap.message(), "", snap.message().contains("dropped") ? GREEN : GOLD, 0));
			if (!snap.item().isEmpty()) {
				rows.add(Row.text(snap.item(), "", Theme.TEXT, 0));
			}
			return rows;
		}
		float t = snap.fraction();
		rows.add(Row.text(snap.item(), String.format(Locale.ROOT, "%.2f%%", t * 100f), 0xFFFF55FF, Theme.TEXT));
		rows.add(Row.bar(t, 0xFFFF55FF));
		String needed = snap.needed() > 0 ? compact(snap.meter()) + "/" + compact(snap.needed()) : compact(snap.meter()) + " XP";
		long left = snap.bossesLeft();
		rows.add(Row.text(needed, left > 0 ? "~" + left + (left == 1 ? " boss" : " bosses") : "", Theme.MUTED, Theme.TEXT));
		return rows;
	}

	private static List<Row> sampleRng() {
		return rng(new SlayerRng.Snapshot("Voidgloom Seraph", "Judgement Core", 412_300, 1_400_000, 3_750, null));
	}

	// ---------------------------------------------------------------- kills

	private static List<Row> killRows() {
		SlayerSettings s = settings();
		SlayerKills.Snapshot snap = SlayerKills.snapshot();
		List<Row> rows = new ArrayList<>();
		rows.add(Row.text("REMAINING KILLS", compact((long) snap.missing()) + " XP", Theme.ACCENT, Theme.MUTED));
		for (SlayerKills.Line line : snap.lines()) {
			StringBuilder name = new StringBuilder();
			if (s.killsLevel && line.mob().level() > 0) {
				name.append("[Lv").append(line.mob().level()).append("] ");
			}
			name.append(line.mob().name());
			if (s.killsHealth && line.mob().maxHealth() > 0) {
				name.append(' ').append(shortNumber(line.mob().maxHealth())).append('❤');
			}
			if (s.killsXp) {
				name.append(String.format(Locale.ROOT, " %.1f XP", line.xp()));
			}
			String kills = line.overkill() && s.killsOverkill ? line.kills() + " (overkill)" : String.valueOf(line.kills());
			rows.add(Row.text(name.toString(), kills, Theme.TEXT, line.overkill() ? GOLD : Theme.ACCENT));
		}
		if (!snap.wisdomKnown()) {
			rows.add(Row.text("Combat wisdom unknown: enable it in /tab", "", Theme.MUTED, 0));
		}
		return rows;
	}

	private static List<Row> sampleKills() {
		List<Row> rows = new ArrayList<>();
		rows.add(Row.text("REMAINING KILLS", "760 XP", Theme.ACCENT, Theme.MUTED));
		rows.add(Row.text("[Lv100] Enderman 9k❤", "38", Theme.TEXT, Theme.ACCENT));
		rows.add(Row.text("[Lv130] Zealot 13k❤", "27", Theme.TEXT, Theme.ACCENT));
		rows.add(Row.text("[Lv100] Voidling Devotee", "2", Theme.TEXT, Theme.ACCENT));
		return rows;
	}

	// ---------------------------------------------------------------- dagger + pillar

	private static String[] daggerOrSample() {
		String[] lines = SlayerBosses.daggerLines();
		return lines != null ? lines : new String[] {"§a[§bCRYSTAL§a]  §8Ashen", "§fSpirit  §eAuric"};
	}

	private static String pillarText() {
		String left = SlayerBosses.pillar();
		return "Fire pillar: " + (left == null ? "4.5" : left) + "s";
	}

	// ---------------------------------------------------------------- alert banner

	private static void drawAlert(GuiGraphicsExtractor graphics, Font font) {
		SlayerAlerts.Title title = SlayerAlerts.currentTitle();
		if (title == null) {
			return;
		}
		float scale = 2f;
		Component text = Component.literal(title.text());
		float tw = font.width(text) * scale;
		float w = tw + 24f;
		float h = font.lineHeight * scale + 14f;
		float x = (graphics.guiWidth() - w) * 0.5f;
		float y = graphics.guiHeight() * 0.28f - h * 0.5f;
		HudChrome.panel(graphics, x, y, w, h, 6, Theme.HUD_WINDOW, Theme.HUD_LINE, title.color());
		GuiDraw.text(graphics, font, text, x + 12f, y + 7f, scale, title.color(), true);
	}

	// ---------------------------------------------------------------- formatting

	private static float[] fraction(String progress) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(?([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)\\)?").matcher(progress == null ? "" : progress);
		if (!m.find()) {
			return null;
		}
		long have = SlayerApi.parseShort(m.group(1));
		long need = SlayerApi.parseShort(m.group(2));
		return need <= 0 ? null : new float[] {Mth.clamp((float) have / need, 0f, 1f)};
	}

	private static String shortName(String category) {
		return category.replace(" Seraph", "").replace(" Demonlord", "").replace(" Broodfather", "").replace(" Horror", "")
			.replace(" Packmaster", "").replace("Riftstalker ", "");
	}

	private static int healthColor(float t) {
		if (t > 0.66f) {
			return GREEN;
		}
		if (t > 0.33f) {
			return 0xFFFFD84A;
		}
		return RED;
	}

	private static int signColor(double v) {
		return v >= 0 ? GOLD : RED;
	}

	private static String clock(long ms) {
		long s = Math.max(0, ms / 1000);
		return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	static String shortNumber(double v) {
		double a = Math.abs(v);
		if (a >= 1e9) {
			return trim(v / 1e9) + "B";
		}
		if (a >= 1e6) {
			return trim(v / 1e6) + "M";
		}
		if (a >= 1e3) {
			return trim(v / 1e3) + "k";
		}
		return String.format(Locale.ROOT, "%.0f", v);
	}

	private static String trim(double v) {
		String s = String.format(Locale.ROOT, "%.1f", v);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private static String coins(double v) {
		return shortNumber(v);
	}

	private static String compact(long v) {
		return String.format(Locale.ROOT, "%,d", v);
	}
}
