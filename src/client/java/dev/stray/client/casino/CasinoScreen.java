package dev.stray.client.casino;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.GuiFrostBlur;
import dev.stray.client.render.RoundRect;
import dev.stray.client.ui.MaterialPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /st casino: slots, blackjack and roulette for play chips. Every result comes from the
 * server; this screen sends the bet or move and animates the answer.
 */
public final class CasinoScreen extends Screen {
	// ---- palette: the click GUI's Material roles (MaterialPalette), refreshed every frame
	// so the casino follows the Stray accent like the Hyprland menu does.
	private int primary;
	private int primary2;
	private int onPrimary;
	private int surface;
	private int surfaceLow;
	private int container;
	private int containerHigh;
	private int containerHighest;
	private int line;
	private int TEXT;
	private int MUTED;
	private int FAINT;
	private static final int GOLD = 0xFFF5C451;
	private static final int RED = 0xFFF0565B;
	private static final int GREEN = 0xFF46DC95;
	private static final int CARD_RED = 0xFFD62F3A;
	private static final int FELT_TOP = 0xFF17694A;
	private static final int FELT_BOTTOM = 0xFF0A3524;
	/** Window corner radius; ii rounds windows at 18px, 9 at GUI scale 2, a bit more for a big panel. */
	private static final float R = 12f;

	private static final int W = 460;
	private static final int H = 326;
	/** Content area inside the window. */
	private static final int CW = W - 28;
	private static final int CH = H - 46 - 36;
	private static final int TABLE_H = 190;

	private enum T { SMALL, BODY, TITLE }
	private static final String[] TABS = {"Slots", "Blackjack", "Roulette", "Send"};

	// ---- slots
	private static final String SLOT_STRIP = "CLGBCRLGCBLSGCRLBGCDLRCGBLCSGRLB";
	private static final Map<Character, ItemStack> SLOT_ITEMS = Map.of(
		'C', new ItemStack(Items.SWEET_BERRIES),
		'L', new ItemStack(Items.GLOW_BERRIES),
		'G', new ItemStack(Items.CHORUS_FRUIT),
		'B', new ItemStack(Items.BELL),
		'R', new ItemStack(Items.GOLD_INGOT),
		'D', new ItemStack(Items.DIAMOND)
	);
	private static final String[][] PAYTABLE = {
		{"DDD", "500x"}, {"SSS", "150x"}, {"RRR", "50x"}, {"BBB", "25x"}, {"GGG", "15x"},
		{"LLL", "10x"}, {"CCC", "8x"}, {"any 3 of 7 / BAR / gem", "10x"}, {"CC_", "4x"}, {"any 3 fruits", "1x"}
	};

	// ---- roulette (European wheel order)
	private static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
	private static final int[] RED_NUMBERS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
	private static final long[] CHIPS = {1, 10, 100, 1_000, 10_000, 100_000};
	private static final int[] CHIP_COLORS = {0xFFE8E8E8, 0xFF3B82F6, 0xFF111827, 0xFF9333EA, 0xFFEAB308, 0xFFE11D48};

	private int left;
	private float ui = 1f;
	/** Screen pixels per casino unit, for snapping text to whole pixels. */
	private float pixel = 2f;
	private int top;
	private int tab;
	private float tabT;
	private long lastNanos = System.nanoTime();
	private float time;

	// account
	private long balance = -1;
	private double shownBalance;
	private long pendingBalance = Long.MIN_VALUE;
	private float pendingBalanceAt;
	private String status = "Connecting...";
	private boolean statusBad;
	private float statusAt;
	private boolean busy;
	private String name = "";
	private final List<String> inbox = new ArrayList<>();
	private final List<String[]> top10 = new ArrayList<>();

	// text fields: 0 slots bet, 1 blackjack bet, 2 send name, 3 send amount
	private final String[] fields = {"10", "10", "", ""};
	private int focus = -1;
	private final List<Hit> hits = new ArrayList<>();

	// slots state
	private final double[] reel = {0, 11, 23};
	private final double[] reelFrom = new double[3];
	private final double[] reelTarget = new double[3];
	private final float[] reelStopAt = new float[3];
	private boolean spinning;
	private boolean slotsResult;
	private float slotsSpinStart;
	private long slotWin;
	private int slotMultiplier;
	private float slotWinAt = -10f;
	private final List<float[]> coins = new ArrayList<>();

	// blackjack state
	private JsonObject bj;
	private final List<DCard> cards = new ArrayList<>();
	private float bjRevealAt;

	// roulette state
	private int chip = 1;
	private final LinkedHashMap<String, Long> bets = new LinkedHashMap<>();
	private LinkedHashMap<String, Long> lastBets = new LinkedHashMap<>();
	private float wheelAngle;
	private float wheelSpeed = 0.25f;
	private float ballAngle;
	private float ballRadius = 1f;
	private boolean rolling;
	private float rollStart;
	private float rollDur = 5.5f;
	private float ballFrom;
	private float ballTo;
	private float wheelFrom;
	private float wheelOmega;
	private int rouletteNumber = -1;
	private float rouletteDoneAt = -10f;
	private long rouletteWin;
	private JsonObject rouletteResult;
	private final List<String> rouletteHistory = new ArrayList<>();

	public CasinoScreen() {
		super(Component.literal("Stray Casino"));
	}

	@Override
	protected void init() {
		// Shrink the whole casino on small windows / big GUI scales so it always fits.
		ui = Math.min(1f, Math.min((height - 12f) / H, (width - 12f) / W));
		left = Math.round((width / ui - W) / 2f);
		top = Math.round((height / ui - H) / 2f);
		if (balance < 0 && !busy) {
			busy = true;
			CasinoApi.login().thenAccept(r -> Minecraft.getInstance().execute(() -> {
				busy = false;
				if (!r.ok()) {
					setStatus(r.error(), true);
					return;
				}
				name = str(r.body(), "name");
				applyAccount(r.body(), true);
				setStatus("Welcome, " + name + ". Play money only.", false);
			}));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ================================================================== frame

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		time += dt;
		hits.clear();
		tabT += (tab - tabT) * Math.min(1f, dt * 14f);
		if (pendingBalance != Long.MIN_VALUE && time >= pendingBalanceAt) {
			balance = pendingBalance;
			pendingBalance = Long.MIN_VALUE;
		}
		if (balance >= 0) {
			shownBalance += (balance - shownBalance) * Math.min(1f, dt * 6f);
			if (Math.abs(balance - shownBalance) < 0.5) {
				shownBalance = balance;
			}
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.pose().pushMatrix();
		g.pose().scale(ui, ui);
		mouseX = Math.round(mouseX / ui);
		mouseY = Math.round(mouseY / ui);

		palette();
		pixel = (float) Minecraft.getInstance().getWindow().getGuiScale() * ui;

		// window: frosted like the click GUI, a top-to-bottom surface gradient, an accent
		// glow fading down from the top-left and a gradient border.
		GuiFrostBlur.blitWindow(g, left, top, W, H, R, 1f);
		RoundRect.vertical(g, left, top, W, H, R, withA(surfaceLow, 0xEC), withA(surface, 0xF6));
		RoundRect.draw(g, left, top, W, H, R, withA(primary, 0x30), withA(primary2, 0x18), withA(primary2, 0x00), withA(primary, 0x00), false);
		RoundRect.outline(g, left, top, W, H, R, withA(primary, 0xE0), withA(primary2, 0x50));
		drawHeader(g, mouseX, mouseY);

		switch (tab) {
			case 0 -> drawSlots(g, mouseX, mouseY, dt, cy0(), CH);
			case 1 -> drawBlackjack(g, mouseX, mouseY, dt, cy0(), CH);
			case 2 -> drawRoulette(g, mouseX, mouseY, dt, cy0(), CH);
			default -> drawSend(g, mouseX, mouseY, cy0(), CH);
		}
		drawToast(g);
		updateCoins(g, dt);
		g.pose().popMatrix();
	}

	private void palette() {
		MaterialPalette.refresh();
		primary = MaterialPalette.primary;
		primary2 = hueShift(primary, 0.11f);
		onPrimary = MaterialPalette.onPrimary;
		surface = MaterialPalette.surface;
		surfaceLow = MaterialPalette.surfaceLow;
		container = MaterialPalette.surfaceContainer;
		containerHigh = MaterialPalette.surfaceHigh;
		containerHighest = MaterialPalette.surfaceHighest;
		line = MaterialPalette.outlineVariant;
		TEXT = MaterialPalette.onSurface;
		MUTED = MaterialPalette.onSurfaceVariant;
		FAINT = MaterialPalette.outline;
	}

	private static final String[] TAB_ICONS = {"", "", "", ""};
	private static final float HEAD_Y = 10;
	private static final float HEAD_H = 26;

	/** The bar: title group, workspace-style tabs, balance; the same pill groups as the click GUI's bar. */
	private void drawHeader(GuiGraphicsExtractor g, int mx, int my) {
		float y = top + HEAD_Y;
		float h = HEAD_H;
		// title group
		float gx = left + 10;
		float titleW = 12 + 14 + tw("Casino", T.TITLE) + 12;
		pill(g, gx, y, titleW, h, container, containerHigh);
		icon(g, "", gx + 10, y, h, primary);
		label(g, "Casino", gx + 26, y, h, TEXT, T.TITLE);

		// balance (laid out first: the tabs take the room between it and the title)
		String value = balance < 0 ? "..." : fmt(Math.round(shownBalance));
		float bw = 10 + 12 + 5 + tw(value, T.TITLE) + 4 + tw("chips", T.SMALL) + 11;
		float bx = left + W - 10 - bw;
		pill(g, bx, y, bw, h, container, containerHigh);
		RoundRect.diagonal(g, bx + 10, y + h / 2f - 6, 12, 12, 6, primary, primary2);
		RoundRect.fill(g, bx + 13, y + h / 2f - 3, 6, 6, 3, withA(onPrimary, 0xB0));
		label(g, value, bx + 27, y, h, TEXT, T.TITLE);
		label(g, "chips", bx + 27 + tw(value, T.TITLE) + 4, y, h, MUTED, T.SMALL);

		// tabs, like the bar's workspaces
		float tx0 = gx + titleW + 8;
		float tabW = Math.min(76, (bx - 8 - tx0 - 6) / TABS.length);
		float groupW = tabW * TABS.length + 6;
		float tx = Math.max(tx0, Math.min(left + (W - groupW) / 2f, bx - 8 - groupW));
		pill(g, tx, y, groupW, h, container, containerHigh);
		float px = tx + 3 + tabT * tabW;
		RoundRect.horizontal(g, px, y + 3, tabW, h - 6, (h - 6) / 2f, primary, primary2);
		for (int i = 0; i < TABS.length; i++) {
			float ix = tx + 3 + i * tabW;
			boolean hot = hovered(mx, my, ix, y, tabW, h);
			float on = Math.max(0f, 1f - Math.abs(tabT - i));
			if (hot && on < 0.5f) {
				RoundRect.fill(g, ix, y + 3, tabW, h - 6, (h - 6) / 2f, withA(TEXT, 0x14));
			}
			int c = mixColor(hot ? TEXT : MUTED, onPrimary, on);
			float cw = 9 + 4 + tw(TABS[i], T.BODY);
			float cx = ix + (tabW - cw) / 2f;
			icon(g, TAB_ICONS[i], cx, y, h, c);
			label(g, TABS[i], cx + 13, y, h, c, T.BODY);
			final int idx = i;
			hit(ix, y, tabW, h, () -> {
				tab = idx;
				focus = -1;
				if (idx == 3) {
					refreshTop();
				}
			});
		}
	}

	/** A bar group: vertical gradient pill with a faint rim. */
	private void pill(GuiGraphicsExtractor g, float x, float y, float w, float h, int from, int to) {
		RoundRect.vertical(g, x, y, w, h, h / 2f, to, from);
		RoundRect.outline(g, x, y, w, h, h / 2f, withA(TEXT, 0x18), withA(TEXT, 0x06));
	}

	/** A content card: Material surface container with a soft top-lit gradient. */
	private void card(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		RoundRect.vertical(g, x, y, w, h, 10, withA(containerHigh, 0xF0), withA(container, 0xE8));
		RoundRect.outline(g, x, y, w, h, 10, withA(TEXT, 0x1C), withA(TEXT, 0x08));
	}

	private void drawToast(GuiGraphicsExtractor g) {
		if (status.isEmpty()) {
			return;
		}
		float age = time - statusAt;
		float a = age < 0.2f ? age / 0.2f : age > 5f ? 1f - (age - 5f) / 0.6f : 1f;
		if (a <= 0.01f) {
			return;
		}
		float h = 22;
		float w = Math.min(W - 40, tw(status, T.BODY) + 34);
		float x = left + (W - w) / 2f;
		float y = top + H - 30 + (1f - Math.min(1f, age / 0.2f)) * 6f;
		int accent = statusBad ? RED : GREEN;
		RoundRect.fill(g, x, y + 2, w, h, h / 2f, alpha(0x70000000, a));
		RoundRect.vertical(g, x, y, w, h, h / 2f, alpha(containerHighest, a), alpha(containerHigh, a));
		RoundRect.horizontal(g, x, y, w * 0.5f, h, h / 2f, alpha(withA(accent, 0x38), a), alpha(withA(accent, 0x00), a));
		RoundRect.outline(g, x, y, w, h, h / 2f, alpha(withA(accent, 0x90), a), alpha(withA(accent, 0x30), a));
		RoundRect.fill(g, x + 9, y + h / 2f - 3.5f, 7, 7, 3.5f, alpha(accent, a));
		label(g, fitText(status, w - 34, T.BODY), x + 22, y, h, alpha(TEXT, a), T.BODY);
	}

	// ================================================================== slots

	private void drawSlots(GuiGraphicsExtractor g, int mx, int my, float dt, int y, int h) {
		int x0 = cx0();
		int mw = 244;
		// machine card
		card(g, x0, y, mw, h);
		int cell = 44;
		int reelW = 62;
		int gap = 7;
		int framesW = reelW * 3 + gap * 2;
		int rx0 = x0 + (mw - framesW) / 2;
		int ry = y + 16;
		// accent frame around the reels
		RoundRect.diagonal(g, rx0 - 7, ry - 7, framesW + 14, cell * 3 + 14, 11, primary, primary2);
		RoundRect.fill(g, rx0 - 5, ry - 5, framesW + 10, cell * 3 + 10, 9, 0xFF0D0E12);
		advanceReels(dt);
		for (int i = 0; i < 3; i++) {
			int rx = rx0 + i * (reelW + gap);
			RoundRect.draw(g, rx, ry, reelW, cell * 3, 5, 0xFFD5CEC2, 0xFFD5CEC2, 0xFFD5CEC2, 0xFFD5CEC2, false);
			GuiDraw.scissor(g, rx, ry, reelW, cell * 3);
			GuiDraw.fillGradient(g, rx, ry, reelW, cell * 1.5f, 0xFFD5CEC2, 0xFFFFFFFF);
			GuiDraw.fillGradient(g, rx, ry + cell * 1.5f, reelW, cell * 1.5f, 0xFFFFFFFF, 0xFFD5CEC2);
			double p = reel[i];
			int base = (int) Math.floor(p);
			for (int j = base - 2; j <= base + 3; j++) {
				float sy = ry + cell + (j - (float) p) * cell;
				char sym = SLOT_STRIP.charAt(Math.floorMod(j, SLOT_STRIP.length()));
				drawSymbol(g, sym, rx + reelW / 2f, sy + cell / 2f);
			}
			GuiDraw.fillGradient(g, rx, ry, reelW, 18, 0xB0000000, 0x00000000);
			GuiDraw.fillGradient(g, rx, ry + cell * 3 - 18, reelW, 18, 0x00000000, 0xB0000000);
			GuiDraw.disableScissor(g);
		}
		boolean winning = !spinning && slotWin > 0 && time - slotWinAt < 5f;
		float pulse = winning ? 0.5f + 0.5f * (float) Math.sin(time * 10f) : 0f;
		int lineC = winning ? mixColor(primary, 0xFFFFFFFF, pulse) : withA(primary, 0xB0);
		float lineY = ry + cell * 1.5f;
		RoundRect.horizontal(g, rx0 - 3, lineY - 1, framesW + 6, 2, 1, withA(lineC, 0x40), lineC);
		drawChevron(g, rx0 - 9, lineY, lineC, true);
		drawChevron(g, rx0 + framesW + 9, lineY, lineC, false);

		// result
		float mid = x0 + mw / 2f;
		float resY = ry + cell * 3 + 12;
		float resH = y + h - resY - 10;
		if (winning) {
			float t = Math.min(1f, (time - slotWinAt) / 0.35f);
			float s = 1.5f * (1f + 0.35f * (1f - easeOutBack(t)));
			String txt = slotMultiplier >= 50 ? "BIG WIN" : "WIN";
			float tx = tw(txt, T.TITLE) * s;
			text(g, txt, mid - tx / 2f, resY + 4 - CAP_MID[2] * (s - 1f), alpha(primary, t), T.TITLE, s);
			centered(g, "+" + fmt(slotWin) + " chips  ·  " + slotMultiplier + "x", mid, resY + 22, 14, alpha(GREEN, t), T.BODY);
		} else if (!spinning && slotsResult) {
			centered(g, "No win this time", mid, resY, resH, MUTED, T.BODY);
		} else if (!spinning) {
			centered(g, "Set your bet and spin", mid, resY, resH / 2f + 4, MUTED, T.BODY);
			centered(g, "Space also spins", mid, resY + resH / 2f, resH / 2f - 4, FAINT, T.SMALL);
		}

		// controls
		int px = x0 + mw + 10;
		int pw = CW - mw - 10;
		card(g, px, y, pw, h);
		px += 12;
		pw -= 24;
		label(g, "Bet", px, y + 8, 12, MUTED, T.SMALL);
		betField(g, mx, my, 0, px, y + 22, pw);
		boolean canSpin = !spinning && !busy && balance > 0;
		button(g, mx, my, px, y + 80, pw, 30, spinning ? "Spinning..." : "Spin", canSpin, true, spinning ? null : "Space", this::spin);
		RoundRect.fill(g, px, y + 122, pw, 1, 0.5f, withA(TEXT, 0x16));
		label(g, "Paytable", px, y + 128, 12, MUTED, T.SMALL);
		float py = y + 142;
		float rowH = 9.4f;
		for (int i = 0; i < PAYTABLE.length; i++) {
			String[] row = PAYTABLE[i];
			if ((i & 1) == 0) {
				RoundRect.fill(g, px - 4, py, pw + 8, rowH, 3, withA(TEXT, 0x07));
			}
			if (row[0].length() == 3 && !row[0].contains(" ")) {
				float ix = px + 5;
				for (int k = 0; k < 3; k++) {
					char c = row[0].charAt(k);
					if (c != '_') {
						drawSymbolSmall(g, c, ix, py + rowH / 2f);
					} else {
						label(g, "any", ix - 5, py, rowH, FAINT, T.SMALL);
					}
					ix += 13;
				}
			} else {
				label(g, row[0], px, py, rowH, TEXT, T.SMALL);
			}
			label(g, row[1], px + pw - tw(row[1], T.BODY), py, rowH, primary, T.BODY);
			py += rowH;
		}
	}

	private void drawSymbol(GuiGraphicsExtractor g, char sym, float cx, float cy) {
		if (sym == 'S') {
			float s = 2.8f;
			glyph(g, "7", cx - glyphWidth("7") * s / 2f + 1, cy - 4.2f * s, 0xFFD3122A, s);
			return;
		}
		ItemStack stack = SLOT_ITEMS.get(sym);
		if (stack == null) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(cx - 15, cy - 15);
		g.pose().scale(1.875f, 1.875f);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
		if (sym == 'R') {
			centered(g, "BAR", cx, cy + 8, 9, 0xFF3A2A05, T.SMALL);
		}
	}

	private void drawSymbolSmall(GuiGraphicsExtractor g, char sym, float cx, float cy) {
		if (sym == 'S') {
			glyph(g, "7", cx - 2.5f, cy - 4.5f, 0xFFE11D2A, 1f);
			return;
		}
		ItemStack stack = SLOT_ITEMS.get(sym);
		g.pose().pushMatrix();
		g.pose().translate(cx - 5, cy - 5);
		g.pose().scale(0.625f, 0.625f);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	private void spin() {
		long bet = parse(fields[0]);
		if (bet < 1) {
			setStatus("Enter a bet.", true);
			return;
		}
		if (bet > balance) {
			setStatus("Not enough chips.", true);
			return;
		}
		spinning = true;
		slotsResult = false;
		slotWin = 0;
		busy = true;
		slotsSpinStart = time;
		for (int i = 0; i < 3; i++) {
			reelStopAt[i] = Float.MAX_VALUE;
		}
		balance -= bet;
		JsonObject body = new JsonObject();
		body.addProperty("bet", bet);
		CasinoApi.call("/slots", body).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			busy = false;
			if (!r.ok()) {
				spinning = false;
				applyAccount(r.body(), false);
				if (r.body() == null) {
					balance += bet;
				}
				setStatus(r.error(), true);
				return;
			}
			JsonObject d = r.body();
			JsonArray stops = d.getAsJsonArray("stops");
			float start = Math.max(time, slotsSpinStart + 0.6f);
			for (int i = 0; i < 3; i++) {
				reelStop[i] = stops.get(i).getAsInt();
				reelDecel[i] = false;
				reelStopAt[i] = start + 0.9f + i * 0.45f;
			}
			slotWin = d.get("win").getAsLong();
			slotMultiplier = d.get("multiplier").getAsInt();
			pendingBalance = d.get("balance").getAsLong();
			pendingBalanceAt = reelStopAt[2];
		}));
	}

	private static final double REEL_SPEED = 20.0;
	private final int[] reelStop = new int[3];
	private final boolean[] reelDecel = new boolean[3];

	private void advanceReels(float dt) {
		if (!spinning) {
			return;
		}
		boolean allDone = true;
		for (int i = 0; i < 3; i++) {
			if (reelStopAt[i] == Float.MAX_VALUE) {
				reel[i] -= dt * REEL_SPEED;
				allDone = false;
				continue;
			}
			float decelStart = reelStopAt[i] - 0.9f;
			if (time < decelStart) {
				reel[i] -= dt * REEL_SPEED;
				allDone = false;
				continue;
			}
			if (!reelDecel[i]) {
				// Land on the server's stop a few symbols further down, so slowing down
				// starts at the spinning speed (the ease starts at 4.2x its average speed).
				reelDecel[i] = true;
				reelFrom[i] = reel[i];
				double target = Math.floor(reel[i]) - 4;
				target -= Math.floorMod((int) target - reelStop[i], SLOT_STRIP.length());
				reelTarget[i] = target;
			}
			float t = Math.min(1f, (time - decelStart) / 0.9f);
			// ease out with a small bounce at the end
			double k = easeOutBack(t);
			reel[i] = reelFrom[i] + (reelTarget[i] - reelFrom[i]) * k;
			if (t < 1f) {
				allDone = false;
			} else {
				reel[i] = Math.floorMod((int) Math.round(reelTarget[i]), SLOT_STRIP.length());
			}
		}
		if (allDone) {
			spinning = false;
			slotsResult = true;
			if (slotWin > 0) {
				slotWinAt = time;
				burstCoins(cx0() + 120, cy0() + 80, slotMultiplier >= 50 ? 60 : slotMultiplier >= 10 ? 30 : 12);
				setStatus("You won " + fmt(slotWin) + " chips.", false);
			}
		}
	}

	// ================================================================== blackjack

	private static final class DCard {
		String code;
		boolean faceUp;
		float x;
		float y;
		float tx;
		float ty;
		float appearAt;
		float flipAt;
		boolean placed;
		int owner;
		int index;
	}

	// Table layout (offsets from the table's top): dealer label, dealer cards, the message
	// band in the middle, player cards, then each hand's label under its own cards.
	private static final int CARD_W = 30;
	private static final int CARD_H = 42;
	private static final int DEALER_LABEL_Y = 10;
	private static final int DEALER_CARDS_Y = 32;
	private static final int MESSAGE_Y = 84;
	private static final int PLAYER_CARDS_Y = 118;
	private static final int PLAYER_LABEL_Y = 166;

	private void drawBlackjack(GuiGraphicsExtractor g, int mx, int my, float dt, int y, int h) {
		int tx = cx0();
		int tw = CW;
		int th = TABLE_H;
		// felt with a soft light in the middle, an accent rim
		RoundRect.vertical(g, tx, y, tw, th, 12, FELT_TOP, FELT_BOTTOM);
		RoundRect.vertical(g, tx + tw * 0.15f, y, tw * 0.7f, th * 0.6f, 40, 0x14FFFFFF, 0x00FFFFFF);
		RoundRect.outline(g, tx, y, tw, th, 12, withA(primary, 0xC0), withA(primary2, 0x50));
		// shoe
		RoundRect.vertical(g, tx + tw - 50, y + 10, 40, 30, 6, 0xFF2B2E36, 0xFF15171C);
		drawCardBack(g, tx + tw - 44, y + 6, 0.62f);

		for (DCard c : cards) {
			if (time < c.appearAt) {
				continue;
			}
			c.placed = true;
			c.x += (c.tx - c.x) * Math.min(1f, dt * 12f);
			c.y += (c.ty - c.y) * Math.min(1f, dt * 12f);
			float flip = c.faceUp ? Math.min(1f, Math.max(0f, (time - c.flipAt + 0.15f) / 0.3f)) : 0f;
			float sx = c.faceUp ? Math.abs(1f - 2f * flip) : 1f;
			boolean showFace = c.faceUp && flip >= 0.5f;
			drawCard(g, c.x, c.y, sx, showFace ? c.code : null);
		}
		float last = lastCardTime();
		String phase = bj == null ? "none" : str(bj, "phase");
		boolean settled = bj != null && "done".equals(phase) && time >= last + 0.2f;
		float center = tx + tw / 2f;

		// dealer label: centred over the dealer's cards
		if (bj != null) {
			String dealerTotal = (!"done".equals(phase) || time >= last) ? String.valueOf(bj.get("dealerTotal").getAsInt()) : "";
			handLabel(g, center, y + DEALER_LABEL_Y, "Dealer", dealerTotal, false);
		} else {
			handLabel(g, center, y + DEALER_LABEL_Y, "Dealer", "", false);
		}

		if (bj != null) {
			JsonArray hands = bj.getAsJsonArray("hands");
			int n = hands.size();
			int active = bj.get("active").getAsInt();
			for (int i = 0; i < n; i++) {
				JsonObject hnd = hands.get(i).getAsJsonObject();
				float hcx = handCenter(i, n);
				int count = hnd.getAsJsonArray("cards").size();
				float handW = CARD_W + (count - 1) * fanStep(n);
				if ("player".equals(phase) && i == active && n > 1) {
					float boxW = Math.max(handW, 70) + 12;
					float pulse = 0.55f + 0.45f * (float) Math.sin(time * 6f);
					RoundRect.outline(g, hcx - boxW / 2f, y + PLAYER_CARDS_Y - 6, boxW, PLAYER_LABEL_Y - PLAYER_CARDS_Y + 24, 9, alpha(primary, pulse), alpha(primary2, pulse * 0.5f));
				}
				int total = hnd.get("total").getAsInt();
				String totalTxt = (hnd.get("soft").getAsBoolean() && total < 21 ? "Soft " : "") + total;
				String who = n == 1 ? "You" : n == 2 ? "Hand " + (i + 1) : "";
				String side;
				int sideFrom;
				int sideTo;
				int sideInk;
				if (settled && !hnd.get("result").isJsonNull()) {
					String res = str(hnd, "result");
					int col = switch (res) {
						case "win", "blackjack" -> GREEN;
						case "push" -> MUTED;
						default -> RED;
					};
					side = switch (res) {
						case "blackjack" -> "Blackjack +" + fmt(hnd.get("payout").getAsLong() - hnd.get("bet").getAsLong());
						case "win" -> "Win +" + fmt(hnd.get("bet").getAsLong());
						case "push" -> "Push";
						case "bust" -> "Bust";
						default -> "Lose";
					};
					float pop = Math.min(1f, (time - last - 0.2f) / 0.3f);
					sideFrom = alpha(withA(col, 0xF0), pop);
					sideTo = alpha(withA(mixColor(col, 0xFF000000, 0.25f), 0xF0), pop);
					sideInk = alpha(0xFF0B0D10, pop);
				} else {
					side = shortAmount(hnd.get("bet").getAsLong()) + (hnd.get("doubled").getAsBoolean() ? " x2" : "");
					sideFrom = primary;
					sideTo = primary2;
					sideInk = onPrimary;
				}
				// one row under the hand: [You 15] [bet or result], centred on the cards
				float h1 = 18;
				float w1 = handLabelWidth(who, totalTxt);
				float w2 = badgeWidth(side, 16);
				float rowX = hcx - (w1 + 4 + w2) / 2f;
				handLabel(g, rowX + w1 / 2f, y + PLAYER_LABEL_Y, who, totalTxt, i == active && "player".equals(phase));
				badge(g, rowX + w1 + 4, y + PLAYER_LABEL_Y + 1, 16, side, sideFrom, sideTo, sideInk);
			}
		}

		// message band between dealer and player
		float band = y + MESSAGE_Y;
		if (settled) {
			centered(g, str(bj, "message"), center, band, 16, TEXT, T.TITLE);
			long insPay = bj.has("insurancePayout") ? bj.get("insurancePayout").getAsLong() : 0;
			if (insPay > 0) {
				centered(g, "Insurance pays " + fmt(insPay), center, band + 16, 12, GREEN, T.BODY);
			}
		} else {
			centered(g, "BLACKJACK PAYS 3 TO 2", center, band, 14, 0xB0FFFFFF, T.BODY);
			centered(g, "Dealer stands on all 17s  ·  Insurance pays 2 to 1", center, band + 14, 12, 0x80FFFFFF, T.SMALL);
		}

		// controls under the table
		boolean animating = time < last + 0.1f;
		float cy = y + th + 10;
		float ch = Math.min(32, h - th - 10);
		if (bj == null || "done".equals(phase)) {
			if (!animating) {
				float fw = 120;
				field(g, mx, my, 1, tx, cy + (ch - 24) / 2f, fw, "Bet");
				float qx = tx + fw + 6;
				float qw = 36;
				for (int i = 0; i < QUICK.length; i++) {
					quickButton(g, mx, my, 1, i, qx + i * (qw + 4), cy + (ch - 24) / 2f, qw, 24);
				}
				float dx = qx + QUICK.length * (qw + 4) + 6;
				button(g, mx, my, dx, cy, tx + tw - dx, ch, "Deal", !busy && balance > 0, true, "Space", this::deal);
			}
		} else if ("insurance".equals(phase)) {
			if (!animating) {
				label(g, "Dealer shows an Ace. Insure for " + fmt(insuranceCost()) + "?", tx, cy, ch, TEXT, T.BODY);
				float bw = 112;
				button(g, mx, my, tx + tw - bw * 2 - 8, cy, bw, ch, "No thanks", !busy, false, "N", () -> act("noinsurance"));
				button(g, mx, my, tx + tw - bw, cy, bw, ch, "Insure", !busy, true, "Y", () -> act("insurance"));
			}
		} else if (!animating) {
			float bw = (tw - 3 * 8) / 4f;
			button(g, mx, my, tx, cy, bw, ch, "Hit", !busy, true, "H", () -> act("hit"));
			button(g, mx, my, tx + (bw + 8), cy, bw, ch, "Stand", !busy, false, "S", () -> act("stand"));
			button(g, mx, my, tx + 2 * (bw + 8), cy, bw, ch, "Double", !busy && bj.get("canDouble").getAsBoolean(), false, "D", () -> act("double"));
			button(g, mx, my, tx + 3 * (bw + 8), cy, bw, ch, "Split", !busy && bj.get("canSplit").getAsBoolean(), false, "P", () -> act("split"));
		}
	}

	/** "Dealer 17" / "You Soft 15": a dark pill, name muted, total bold; centred on cx. */
	private void handLabel(GuiGraphicsExtractor g, float cx, float y, String who, String total, boolean active) {
		float h = 18;
		float w = handLabelWidth(who, total);
		float x = cx - w / 2f;
		RoundRect.vertical(g, x, y, w, h, h / 2f, 0xC0101215, 0xC0080A0C);
		RoundRect.outline(g, x, y, w, h, h / 2f, active ? primary : 0x30FFFFFF, active ? primary2 : 0x10FFFFFF);
		float ix = x + 9;
		if (!who.isEmpty()) {
			label(g, who, ix, y, h, 0xC0FFFFFF, T.BODY);
			ix += tw(who, T.BODY) + (total.isEmpty() ? 0 : 5);
		}
		if (!total.isEmpty()) {
			label(g, total, ix, y, h, 0xFFFFFFFF, T.TITLE);
		}
	}

	private float handLabelWidth(String who, String total) {
		float w = 18;
		if (!who.isEmpty()) {
			w += tw(who, T.BODY);
		}
		if (!total.isEmpty()) {
			w += tw(total, T.TITLE) + (who.isEmpty() ? 0 : 5);
		}
		return w;
	}

	private long insuranceCost() {
		if (bj == null) {
			return 0;
		}
		return bj.getAsJsonArray("hands").get(0).getAsJsonObject().get("bet").getAsLong() / 2;
	}

	private float lastCardTime() {
		float last = 0;
		for (DCard c : cards) {
			float flip = c.flipAt == Float.MAX_VALUE ? c.appearAt : c.flipAt;
			last = Math.max(last, Math.max(c.appearAt, flip) + 0.35f);
		}
		return last;
	}

	/** Horizontal centre of player hand i of n. */
	private float handCenter(int i, int n) {
		float spacing = n <= 2 ? 150 : CW / (float) n;
		return cx0() + CW / 2f + (i - (n - 1) / 2f) * spacing;
	}

	private static float fanStep(int hands) {
		return hands > 2 ? 11 : 17;
	}

	/** Left edge of card i in a fan of count cards centred on cx. */
	private static float fanX(float cx, int i, int count, float step) {
		float w = CARD_W + (count - 1) * step;
		return cx - w / 2f + i * step;
	}

	private void deal() {
		long bet = parse(fields[1]);
		if (bet < 1) {
			setStatus("Enter a bet.", true);
			return;
		}
		if (bet > balance) {
			setStatus("Not enough chips.", true);
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("action", "start");
		body.addProperty("bet", bet);
		cards.clear();
		bj = null;
		balance -= bet;
		sendBlackjack(body, true);
	}

	private void act(String action) {
		JsonObject body = new JsonObject();
		body.addProperty("action", action);
		sendBlackjack(body, false);
	}

	private void sendBlackjack(JsonObject body, boolean fresh) {
		busy = true;
		CasinoApi.call("/blackjack", body).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			busy = false;
			if (r.body() != null && r.body().has("blackjack") && r.body().get("blackjack").isJsonObject()) {
				showBlackjack(r.body().getAsJsonObject("blackjack"), fresh);
			}
			if (!r.ok()) {
				applyAccount(r.body(), false);
				if (r.body() == null && fresh) {
					balance += parse(fields[1]);
				}
				setStatus(r.error(), true);
				return;
			}
			long bal = r.body().get("balance").getAsLong();
			JsonObject view = r.body().getAsJsonObject("blackjack");
			if (view != null && "done".equals(str(view, "phase"))) {
				pendingBalance = bal;
				pendingBalanceAt = lastCardTime() + 0.2f;
			} else {
				balance = bal;
			}
		}));
	}

	/** Lays the server's cards out on the table, animating only the new ones. */
	private void showBlackjack(JsonObject view, boolean fresh) {
		bj = view;
		int tx = cx0();
		int tw = CW;
		int y = cy0();
		List<DCard> unused = new ArrayList<>(cards);
		List<DCard> next = new ArrayList<>();
		float stagger = Math.max(time, lastCardTime() - 0.35f);
		float shoeX = tx + tw - 44;
		float shoeY = y + 6;
		// dealer
		JsonArray dealer = view.getAsJsonArray("dealer");
		boolean done = "done".equals(str(view, "phase"));
		for (int i = 0; i < dealer.size(); i++) {
			JsonElement e = dealer.get(i);
			String code = e.isJsonNull() ? "?" : e.getAsString();
			float cx = fanX(tx + tw / 2f, i, dealer.size(), 18);
			float cy = y + DEALER_CARDS_Y;
			DCard c = take(unused, i == 1 ? null : code, true, i);
			if (c == null) {
				c = new DCard();
				c.x = shoeX;
				c.y = shoeY;
				c.appearAt = stagger;
				stagger += 0.28f;
				c.flipAt = c.appearAt + 0.15f;
			}
			c.tx = cx;
			c.ty = cy;
			boolean wasUp = c.faceUp;
			c.code = code;
			c.faceUp = !"?".equals(code);
			if (c.faceUp && !wasUp && c.placed) {
				c.flipAt = stagger;
				stagger += 0.35f;
			}
			c.owner = -1;
			c.index = i;
			next.add(c);
		}
		// player hands
		JsonArray hands = view.getAsJsonArray("hands");
		for (int hIdx = 0; hIdx < hands.size(); hIdx++) {
			JsonArray hc = hands.get(hIdx).getAsJsonObject().getAsJsonArray("cards");
			for (int i = 0; i < hc.size(); i++) {
				String code = hc.get(i).getAsString();
				DCard c = take(unused, code, false, -1);
				if (c == null) {
					c = new DCard();
					c.x = shoeX;
					c.y = shoeY;
					c.appearAt = stagger;
					c.flipAt = stagger + 0.15f;
					stagger += 0.28f;
				}
				c.code = code;
				c.faceUp = true;
				c.tx = fanX(handCenter(hIdx, hands.size()), i, hc.size(), fanStep(hands.size()));
				c.ty = y + PLAYER_CARDS_Y;
				c.owner = hIdx;
				c.index = i;
				next.add(c);
			}
		}
		// deal order for a fresh round: player, dealer, player, dealer
		if (fresh && next.size() == 4) {
			DCard d1 = next.get(0);
			DCard d2 = next.get(1);
			DCard p1 = next.get(2);
			DCard p2 = next.get(3);
			float t = time;
			for (DCard c : new DCard[] {p1, d1, p2, d2}) {
				c.appearAt = t;
				c.flipAt = c == d2 && !d2.faceUp ? Float.MAX_VALUE : t + 0.15f;
				t += 0.3f;
			}
			if (d2.faceUp) {
				// revealed at once (dealer blackjack or the round is over)
				d2.flipAt = t + 0.2f;
			}
		}
		cards.clear();
		cards.addAll(next);
		if (done) {
			bjRevealAt = lastCardTime();
		}
	}

	private DCard take(List<DCard> pool, String code, boolean dealer, int index) {
		for (int i = 0; i < pool.size(); i++) {
			DCard c = pool.get(i);
			if (dealer) {
				if (c.owner == -1 && c.index == index) {
					pool.remove(i);
					return c;
				}
			} else if (c.owner >= 0 && code != null && code.equals(c.code)) {
				pool.remove(i);
				return c;
			}
		}
		return null;
	}

	private void drawCard(GuiGraphicsExtractor g, float x, float y, float scaleX, String code) {
		float w = CARD_W;
		float h = CARD_H;
		g.pose().pushMatrix();
		g.pose().translate(x + w / 2f, y);
		g.pose().scale(Math.max(0.02f, scaleX), 1f);
		g.pose().translate(-w / 2f, 0);
		RoundRect.fill(g, 0.5f, 1.5f, w, h, 3.5f, 0x60000000);
		if (code == null) {
			drawCardBackLocal(g, w, h);
		} else {
			RoundRect.vertical(g, 0, 0, w, h, 3.5f, 0xFFFFFFFF, 0xFFEDEAE3);
			RoundRect.outline(g, 0, 0, w, h, 3.5f, 0x30000000, 0x50000000);
			String rank = code.substring(0, code.length() - 1);
			char suit = code.charAt(code.length() - 1);
			String suitGlyph = switch (suit) {
				case 'H' -> "\u2665";
				case 'D' -> "\u2666";
				case 'C' -> "\u2663";
				default -> "\u2660";
			};
			int col = suit == 'H' || suit == 'D' ? CARD_RED : 0xFF16181D;
			float rw = tw(rank, T.TITLE);
			// rank and small suit stacked in the top-left corner, big suit in the middle
			text(g, rank, 3.5f, 2.5f, col, T.TITLE);
			glyph(g, suitGlyph, 3.5f + (rw - glyphWidth(suitGlyph) * 0.75f) / 2f, 12.5f, col, 0.75f);
			float big = 1.6f;
			glyph(g, suitGlyph, w / 2f - glyphWidth(suitGlyph) * big / 2f + 0.5f, h / 2f - 3.5f * big + 3f, col, big);
		}
		g.pose().popMatrix();
	}

	private void drawCardBack(GuiGraphicsExtractor g, float x, float y, float s) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s, s);
		drawCardBackLocal(g, CARD_W, CARD_H);
		g.pose().popMatrix();
	}

	/** Card backs in the accent gradient, so the deck matches the menu. */
	private void drawCardBackLocal(GuiGraphicsExtractor g, float w, float h) {
		RoundRect.fill(g, 0, 0, w, h, 3.5f, 0xFFF7F6F2);
		RoundRect.diagonal(g, 2, 2, w - 4, h - 4, 2.5f, primary, mixColor(primary2, 0xFF000000, 0.35f));
		for (int i = 0; i < 6; i++) {
			RoundRect.fill(g, 4, 6 + i * 5.5f, w - 8, 1, 0.5f, 0x26FFFFFF);
		}
		RoundRect.outline(g, 4, 4, w - 8, h - 8, 2, 0x90FFFFFF, 0x40FFFFFF);
	}

	// ================================================================== roulette

	private void drawRoulette(GuiGraphicsExtractor g, int mx, int my, float dt, int y, int h) {
		int x0 = cx0();
		int ww = 154;
		card(g, x0, y, ww, h);
		float cx = x0 + ww / 2f;
		float cy = y + 82;
		float r = 64;
		advanceWheel(dt);
		drawWheel(g, cx, cy, r);
		boolean showResult = !rolling && rouletteNumber >= 0 && time - rouletteDoneAt < 8f;
		if (showResult) {
			int col = rouletteNumber == 0 ? 0xFF16A34A : isRed(rouletteNumber) ? 0xFFC81E28 : 0xFF16181D;
			float pop = Math.min(1f, (time - rouletteDoneAt) / 0.3f);
			float rad = 16 * easeOutBack(pop);
			GuiDraw.circle(g, cx, cy, rad + 1.5f, 0xFFFFFFFF);
			GuiDraw.circle(g, cx, cy, rad, col);
			centered(g, String.valueOf(rouletteNumber), cx, cy - 8, 16, 0xFFFFFFFF, T.TITLE);
		}
		// result line + history under the wheel
		float resY = y + 160;
		if (showResult) {
			String res = rouletteWin > 0 ? "Won " + fmt(rouletteWin) : "No win";
			centered(g, res, cx, resY, 18, rouletteWin > 0 ? GREEN : MUTED, T.TITLE);
		} else if (rolling) {
			centered(g, "No more bets", cx, resY, 18, MUTED, T.BODY);
		} else {
			centered(g, "Place your chips", cx, resY, 18, FAINT, T.BODY);
		}
		RoundRect.fill(g, x0 + 10, y + 186, ww - 20, 1, 0.5f, withA(TEXT, 0x16));
		label(g, "Last numbers", x0 + 10, y + 192, 12, MUTED, T.SMALL);
		float hx = x0 + 10;
		float hy = y + 208;
		for (int i = 0; i < rouletteHistory.size(); i++) {
			String s = rouletteHistory.get(i);
			int n = Integer.parseInt(s);
			int col = n == 0 ? 0xFF16A34A : isRed(n) ? 0xFFC81E28 : 0xFF2A2F38;
			float pw = 14.5f;
			if (hx + pw > x0 + ww - 10) {
				break;
			}
			RoundRect.vertical(g, hx, hy, pw, 16, 5, mixColor(col, 0xFFFFFFFF, 0.12f), col);
			if (i == 0) {
				RoundRect.outline(g, hx, hy, pw, 16, 5, 0xC0FFFFFF, 0x60FFFFFF);
			}
			centered(g, s, hx + pw / 2f, hy, 16, 0xFFFFFFFF, T.SMALL);
			hx += pw + 1.5f;
		}

		// board
		int bxCard = x0 + ww + 10;
		int bwCard = CW - ww - 10;
		card(g, bxCard, y, bwCard, h);
		float cw = 18;
		float ch = 22;
		float boardW = cw * 13 + 24;
		float bx = bxCard + (bwCard - boardW) / 2f;
		float by = y + 10;
		boolean locked = rolling || busy;
		boardCell(g, mx, my, bx, by, cw, ch * 3, "0", 0xFF15803D, "straight:0", locked);
		for (int c = 0; c < 12; c++) {
			for (int row = 0; row < 3; row++) {
				int num = c * 3 + (3 - row);
				int col = isRed(num) ? 0xFFB91C1C : 0xFF16181D;
				boardCell(g, mx, my, bx + cw + c * cw, by + row * ch, cw, ch, String.valueOf(num), col, "straight:" + num, locked);
			}
		}
		for (int row = 0; row < 3; row++) {
			boardCell(g, mx, my, bx + cw * 13, by + row * ch, 24, ch, "2:1", 0xFF14532D, "column:" + (3 - row), locked);
		}
		for (int d = 0; d < 3; d++) {
			boardCell(g, mx, my, bx + cw + d * cw * 4, by + ch * 3, cw * 4, ch, (d == 0 ? "1st" : d == 1 ? "2nd" : "3rd") + " 12", 0xFF14532D, "dozen:" + (d + 1), locked);
		}
		String[][] outs = {{"1-18", "low"}, {"Even", "even"}, {"Red", "red"}, {"Black", "black"}, {"Odd", "odd"}, {"19-36", "high"}};
		for (int o = 0; o < 6; o++) {
			int col = o == 2 ? 0xFFB91C1C : o == 3 ? 0xFF16181D : 0xFF14532D;
			boardCell(g, mx, my, bx + cw + o * cw * 2, by + ch * 4, cw * 2, ch, outs[o][0], col, outs[o][1] + ":0", locked);
		}
		RoundRect.outline(g, bx, by, boardW, ch * 5, 3, 0x50FFFFFF, 0x30FFFFFF);
		// chips
		float ry = by + ch * 5 + 12;
		label(g, "Chip", bx, ry, 22, MUTED, T.SMALL);
		for (int i = 0; i < CHIPS.length; i++) {
			float x = bx + 30 + i * 30;
			boolean sel = i == chip;
			float lift = sel ? -2 : 0;
			float ccy = ry + 11 + lift;
			if (sel) {
				RoundRect.diagonal(g, x + 10 - 13.5f, ccy - 13.5f, 27, 27, 13.5f, primary, primary2);
			}
			drawChip(g, x + 10, ccy, 11, CHIP_COLORS[i]);
			centered(g, shortAmount(CHIPS[i]), x + 10, ccy - 6, 12, i == 0 || i == 4 ? 0xFF111111 : 0xFFFFFFFF, T.SMALL);
			final int idx = i;
			hit(x - 2, ry - 4, 24, 30, () -> chip = idx);
		}
		long total = 0;
		for (long v : bets.values()) {
			total += v;
		}
		float ty = ry + 30;
		label(g, "Total bet", bx, ty, 16, MUTED, T.BODY);
		label(g, fmt(total), bx + tw("Total bet", T.BODY) + 6, ty, 16, total > 0 ? primary : FAINT, T.TITLE);
		float ay = ty + 22;
		float ax = bx;
		ax = chipButton(g, mx, my, ax, ay, "Clear", !locked && !bets.isEmpty(), bets::clear);
		ax = chipButton(g, mx, my, ax, ay, "Rebet", !locked && !lastBets.isEmpty(), () -> {
			bets.clear();
			bets.putAll(lastBets);
		});
		ax = chipButton(g, mx, my, ax, ay, "2x", !locked && !bets.isEmpty(), () -> bets.replaceAll((k, v) -> v * 2));
		button(g, mx, my, ax, ay - 3, bx + boardW - ax, 30, rolling ? "Rolling..." : "Spin", !locked && !bets.isEmpty(), true, rolling ? null : "Space", this::spinRoulette);
		label(g, "Left click adds a chip, right click removes one. 1-6 picks a chip.", bx, ay + 30, 12, FAINT, T.SMALL);
	}

	private void boardCell(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h, String lbl, int color, String key, boolean locked) {
		boolean hot = !locked && hovered(mx, my, x, y, w, h);
		boolean winner = !rolling && rouletteNumber >= 0 && time - rouletteDoneAt < 8f && wins(key, rouletteNumber);
		int fill = hot ? mixColor(color, 0xFFFFFFFF, 0.2f) : color;
		if (winner) {
			fill = mixColor(color, primary, 0.45f + 0.25f * (float) Math.sin(time * 8f));
		}
		GuiDraw.fillGradient(g, x, y, w, h, mixColor(fill, 0xFFFFFFFF, 0.08f), mixColor(fill, 0xFF000000, 0.12f));
		GuiDraw.border(g, x, y, w, h, 0x40FFFFFF, 0.5f);
		Long amount = bets.get(key);
		if (amount != null && amount > 0) {
			int ci = 0;
			for (int i = 0; i < CHIPS.length; i++) {
				if (amount >= CHIPS[i]) {
					ci = i;
				}
			}
			drawChip(g, x + w / 2f, y + h / 2f, 7.5f, CHIP_COLORS[ci]);
			centered(g, shortAmount(amount), x + w / 2f, y + h / 2f - 5, 10, ci == 0 || ci == 4 ? 0xFF111111 : 0xFFFFFFFF, T.SMALL);
		} else {
			centered(g, lbl, x + w / 2f, y, h, 0xFFFFFFFF, lbl.length() > 2 ? T.SMALL : T.BODY);
		}
		hits.add(new Hit(x, y, w, h, () -> {
			if (!locked) {
				bets.merge(key, CHIPS[chip], Long::sum);
			}
		}, () -> {
			if (locked) {
				return;
			}
			Long now = bets.get(key);
			if (now != null) {
				long rest = now - CHIPS[chip];
				if (rest <= 0) {
					bets.remove(key);
				} else {
					bets.put(key, rest);
				}
			}
		}));
	}

	private boolean wins(String key, int n) {
		String[] p = key.split(":");
		int v = Integer.parseInt(p[1]);
		return switch (p[0]) {
			case "straight" -> v == n;
			case "red" -> n != 0 && isRed(n);
			case "black" -> n != 0 && !isRed(n);
			case "odd" -> n != 0 && n % 2 == 1;
			case "even" -> n != 0 && n % 2 == 0;
			case "low" -> n >= 1 && n <= 18;
			case "high" -> n >= 19;
			case "dozen" -> n != 0 && (n - 1) / 12 + 1 == v;
			case "column" -> n != 0 && (n - 1) % 3 + 1 == v;
			default -> false;
		};
	}

	private void spinRoulette() {
		long total = 0;
		JsonArray arr = new JsonArray();
		for (Map.Entry<String, Long> e : bets.entrySet()) {
			String[] p = e.getKey().split(":");
			JsonObject b = new JsonObject();
			b.addProperty("type", p[0]);
			b.addProperty("value", Integer.parseInt(p[1]));
			b.addProperty("amount", e.getValue());
			arr.add(b);
			total += e.getValue();
		}
		if (total > balance) {
			setStatus("Not enough chips.", true);
			return;
		}
		JsonObject body = new JsonObject();
		body.add("bets", arr);
		busy = true;
		rolling = true;
		rouletteNumber = -1;
		rouletteWin = 0;
		rollStart = time;
		wheelFrom = wheelAngle;
		wheelOmega = 2.6f;
		ballFrom = ballAngle;
		ballTo = Float.NaN;
		ballRadius = 1f;
		lastBets = new LinkedHashMap<>(bets);
		balance -= total;
		final long staked = total;
		CasinoApi.call("/roulette", body).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			busy = false;
			if (!r.ok()) {
				rolling = false;
				applyAccount(r.body(), false);
				if (r.body() == null) {
					balance += staked;
				}
				setStatus(r.error(), true);
				return;
			}
			rouletteResult = r.body();
			int number = r.body().get("number").getAsInt();
			rollStart = time;
			wheelFrom = wheelAngle;
			ballFrom = ballAngle;
			// where the pocket will be when the wheel stops, plus a few laps for the ball
			float wheelEnd = wheelFrom + wheelOmega * rollDur / 2f;
			float pocket = pocketAngle(number);
			float target = wheelEnd + pocket;
			float laps = 3f * (float) (Math.PI * 2);
			float k = ballFrom - laps;
			ballTo = k - floorModF(k - target, (float) (Math.PI * 2));
			rouletteNumber = -1;
			pendingNumber = number;
			pendingBalance = r.body().get("balance").getAsLong();
			pendingBalanceAt = time + rollDur;
			rouletteWinPending = r.body().get("win").getAsLong();
		}));
	}

	private int pendingNumber = -1;
	private long rouletteWinPending;

	private void advanceWheel(float dt) {
		if (!rolling) {
			wheelAngle += dt * wheelSpeed;
			if (rouletteNumber >= 0) {
				ballAngle = wheelAngle + pocketAngle(rouletteNumber);
			}
			return;
		}
		if (Float.isNaN(ballTo)) {
			// waiting for the server: spin freely
			wheelAngle += dt * wheelOmega;
			ballAngle -= dt * 9f;
			wheelFrom = wheelAngle;
			ballFrom = ballAngle;
			rollStart = time;
			return;
		}
		float t = Math.min(1f, (time - rollStart) / rollDur);
		float s = t * rollDur;
		wheelAngle = wheelFrom + wheelOmega * (s - s * s / (2f * rollDur));
		float e = 1f - (float) Math.pow(1f - t, 3);
		ballAngle = ballFrom + (ballTo - ballFrom) * e;
		// the ball drops off the rim into the pockets near the end, with a few hops
		float drop = Math.max(0f, (t - 0.55f) / 0.45f);
		float hop = drop > 0 && drop < 0.8f ? 0.05f * Math.abs((float) Math.sin(drop * 20f)) * (1f - drop) : 0f;
		ballRadius = 1f - 0.22f * Math.min(1f, drop * 1.6f) + hop;
		if (t >= 1f) {
			rolling = false;
			rouletteNumber = pendingNumber;
			rouletteDoneAt = time;
			rouletteWin = rouletteWinPending;
			rouletteHistory.add(0, String.valueOf(rouletteNumber));
			while (rouletteHistory.size() > 9) {
				rouletteHistory.remove(rouletteHistory.size() - 1);
			}
			if (rouletteWin > 0) {
				burstCoins(cx0() + 77, cy0() + 82, rouletteWin >= 10L * Math.max(1, sum(lastBets)) ? 50 : 18);
				setStatus(rouletteNumber + " " + colorName(rouletteNumber) + ". You won " + fmt(rouletteWin) + ".", false);
			} else {
				setStatus(rouletteNumber + " " + colorName(rouletteNumber) + ". No win.", false);
			}
			bets.clear();
		}
	}

	private float pocketAngle(int number) {
		for (int i = 0; i < WHEEL.length; i++) {
			if (WHEEL[i] == number) {
				return (float) (i * Math.PI * 2 / WHEEL.length);
			}
		}
		return 0f;
	}

	private void drawWheel(GuiGraphicsExtractor g, float cx, float cy, float r) {
		// rim
		GuiDraw.circle(g, cx, cy, r + 6, 0xFF4A2C14);
		GuiDraw.circle(g, cx, cy, r + 3, 0xFF7A4A22);
		float seg = (float) (Math.PI * 2 / WHEEL.length);
		float inner = r * 0.55f;
		int bands = 6;
		for (int i = 0; i < WHEEL.length; i++) {
			int n = WHEEL[i];
			int col = n == 0 ? 0xFF15803D : isRed(n) ? 0xFFB91C1C : 0xFF16181D;
			float a = wheelAngle + i * seg - (float) Math.PI / 2f;
			for (int b = 0; b < bands; b++) {
				float r0 = inner + (r - inner) * b / bands;
				float r1 = inner + (r - inner) * (b + 1) / bands;
				float width = r1 * seg * 1.08f;
				GuiDraw.stroke(g, cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0, cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, width, col);
			}
		}
		// pocket dividers and numbers
		for (int i = 0; i < WHEEL.length; i++) {
			float a = wheelAngle + (i + 0.5f) * seg - (float) Math.PI / 2f;
			GuiDraw.stroke(g, cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 0.6f, 0x90D4AF37);
			float na = wheelAngle + i * seg - (float) Math.PI / 2f;
			String s = String.valueOf(WHEEL[i]);
			g.pose().pushMatrix();
			g.pose().translate(cx + (float) Math.cos(na) * (r - 7), cy + (float) Math.sin(na) * (r - 7));
			g.pose().rotate(na + (float) Math.PI / 2f);
			text(g, s, -tw(s, T.SMALL) / 2f, -4f, 0xFFFFFFFF, T.SMALL);
			g.pose().popMatrix();
		}
		// cone
		GuiDraw.circle(g, cx, cy, inner, 0xFF5A3418);
		GuiDraw.circle(g, cx, cy, inner * 0.72f, 0xFF7A4A22);
		for (int k = 0; k < 4; k++) {
			float a = wheelAngle * 1f + k * (float) Math.PI / 2f;
			GuiDraw.stroke(g, cx, cy, cx + (float) Math.cos(a) * inner * 0.7f, cy + (float) Math.sin(a) * inner * 0.7f, 2.4f, 0xFFD4AF37);
		}
		GuiDraw.circle(g, cx, cy, 4, 0xFFD4AF37);
		// ball
		if (rolling || rouletteNumber >= 0) {
			float ba = ballAngle - (float) Math.PI / 2f;
			float rad = rolling ? r * (0.86f + 0.12f * (ballRadius - 0.78f) / 0.22f) : r * 0.86f;
			GuiDraw.circle(g, cx + (float) Math.cos(ba) * rad, cy + (float) Math.sin(ba) * rad, 3.2f, 0xFFFFFFFF);
			GuiDraw.circle(g, cx + (float) Math.cos(ba) * rad - 0.8f, cy + (float) Math.sin(ba) * rad - 0.8f, 1.2f, 0xFFFFFFFF);
		}
	}

	// ================================================================== send

	private void drawSend(GuiGraphicsExtractor g, int mx, int my, int y, int h) {
		int x0 = cx0();
		int cw = (CW - 10) / 2;
		card(g, x0, y, cw, h);
		float x = x0 + 14;
		float w = cw - 28;
		label(g, "Send chips", x, y + 10, 16, TEXT, T.TITLE);
		label(g, "Player", x, y + 32, 12, MUTED, T.SMALL);
		field(g, mx, my, 2, x, y + 45, w, "Minecraft name");
		label(g, "Amount", x, y + 76, 12, MUTED, T.SMALL);
		field(g, mx, my, 3, x, y + 89, w, "0");
		button(g, mx, my, x, y + 122, w, 28, "Send", !busy && !fields[2].isBlank() && parse(fields[3]) > 0, true, null, this::sendChips);
		label(g, "Arrives right away. New players start with 1,000.", x, y + 154, 12, FAINT, T.SMALL);
		RoundRect.fill(g, x, y + 172, w, 1, 0.5f, withA(TEXT, 0x16));
		label(g, "Inbox", x, y + 178, 12, MUTED, T.SMALL);
		if (inbox.isEmpty()) {
			label(g, "Nothing yet", x, y + 192, 12, FAINT, T.BODY);
		}
		for (int i = 0; i < Math.min(4, inbox.size()); i++) {
			label(g, fitText(inbox.get(i), w, T.BODY), x, y + 192 + i * 12, 12, TEXT, T.BODY);
		}

		int lx = x0 + cw + 10;
		card(g, lx, y, cw, h);
		label(g, "Leaderboard", lx + 14, y + 10, 16, TEXT, T.TITLE);
		float rw = tw("Refresh", T.BODY) + 20;
		chipButton(g, mx, my, lx + cw - 12 - rw, y + 6, "Refresh", !busy, this::refreshTop);
		float rowH = 19;
		for (int i = 0; i < top10.size(); i++) {
			String[] row = top10.get(i);
			float ry = y + 36 + i * (rowH + 1);
			boolean me = row[0].equalsIgnoreCase(name);
			if (me) {
				RoundRect.horizontal(g, lx + 8, ry, cw - 16, rowH, rowH / 2f, withA(primary, 0x40), withA(primary2, 0x14));
			} else if ((i & 1) == 0) {
				RoundRect.fill(g, lx + 8, ry, cw - 16, rowH, rowH / 2f, withA(TEXT, 0x06));
			}
			int medal = i == 0 ? GOLD : i == 1 ? 0xFFC9D1DB : i == 2 ? 0xFFD08A4A : 0;
			float mcx = lx + 20;
			if (medal != 0) {
				RoundRect.vertical(g, mcx - 7, ry + rowH / 2f - 7, 14, 14, 7, mixColor(medal, 0xFFFFFFFF, 0.25f), mixColor(medal, 0xFF000000, 0.15f));
			}
			centered(g, String.valueOf(i + 1), mcx, ry, rowH, medal != 0 ? 0xFF15110A : MUTED, T.SMALL);
			label(g, fitText(row[0], cw - 100, T.BODY), lx + 34, ry, rowH, me ? primary : TEXT, T.BODY);
			label(g, row[1], lx + cw - 16 - tw(row[1], T.BODY), ry, rowH, me ? primary : MUTED, T.BODY);
		}
		if (top10.isEmpty()) {
			label(g, "Loading...", lx + 14, y + 36, 16, FAINT, T.BODY);
		}
	}

	private void sendChips() {
		long amount = parse(fields[3]);
		String to = fields[2].trim();
		JsonObject body = new JsonObject();
		body.addProperty("to", to);
		body.addProperty("amount", amount);
		busy = true;
		CasinoApi.call("/send", body).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			busy = false;
			applyAccount(r.body(), false);
			if (!r.ok()) {
				setStatus(r.error(), true);
				return;
			}
			setStatus("Sent " + fmt(amount) + " chips to " + str(r.body(), "to") + ".", false);
			fields[3] = "";
			refreshTop();
		}));
	}

	private void refreshTop() {
		CasinoApi.call("/top", new JsonObject()).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			if (!r.ok()) {
				return;
			}
			top10.clear();
			for (JsonElement e : r.body().getAsJsonArray("top")) {
				JsonObject o = e.getAsJsonObject();
				top10.add(new String[] {str(o, "name"), fmt(o.get("balance").getAsLong())});
			}
			applyAccount(r.body(), false);
		}));
	}

	// ================================================================== shared widgets

	private static final String[] QUICK = {"1/2", "2x", "+100", "Max"};

	/** Bet field with the quick buttons in a row under it. */
	private void betField(GuiGraphicsExtractor g, int mx, int my, int idx, float x, float y, float w) {
		field(g, mx, my, idx, x, y, w, "Bet");
		float by = y + 30;
		float bw = (w - 3 * 5) / 4f;
		for (int i = 0; i < QUICK.length; i++) {
			quickButton(g, mx, my, idx, i, x + i * (bw + 5), by, bw, 20);
		}
	}

	private void quickButton(GuiGraphicsExtractor g, int mx, int my, int idx, int k, float x, float y, float w, float h) {
		boolean hot = hovered(mx, my, x, y, w, h);
		RoundRect.vertical(g, x, y, w, h, h / 2f, hot ? containerHighest : containerHigh, hot ? containerHigh : container);
		RoundRect.outline(g, x, y, w, h, h / 2f, withA(TEXT, hot ? 0x30 : 0x18), withA(TEXT, 0x08));
		centered(g, QUICK[k], x + w / 2f, y, h, hot ? TEXT : MUTED, T.BODY);
		hit(x, y, w, h, () -> {
			long v = parse(fields[idx]);
			long max = Math.max(1, balance);
			v = switch (k) {
				case 0 -> Math.max(1, v / 2);
				case 1 -> Math.min(max, Math.max(1, v * 2));
				case 2 -> Math.min(max, v + 100);
				default -> max;
			};
			fields[idx] = String.valueOf(v);
		});
	}

	private void field(GuiGraphicsExtractor g, int mx, int my, int idx, float x, float y, float w, String placeholder) {
		float h = 24;
		boolean focused = focus == idx;
		boolean hot = hovered(mx, my, x, y, w, h);
		RoundRect.vertical(g, x, y, w, h, 8, withA(surface, 0xF0), withA(surfaceLow, 0xF0));
		if (focused) {
			RoundRect.outline(g, x, y, w, h, 8, primary, primary2);
		} else {
			RoundRect.outline(g, x, y, w, h, 8, withA(TEXT, hot ? 0x40 : 0x22), withA(TEXT, hot ? 0x28 : 0x12));
		}
		String value = fields[idx];
		boolean empty = value.isEmpty();
		String shown = empty ? placeholder : (idx == 2 ? value : grouped(value));
		label(g, shown, x + 10, y, h, empty ? FAINT : TEXT, T.BODY);
		if (focused && (int) (time * 2) % 2 == 0) {
			float cxp = x + 10 + (empty ? 0 : tw(shown, T.BODY)) + 1;
			RoundRect.fill(g, cxp, y + 6, 1, 12, 0.5f, primary);
		}
		hit(x, y, w, h, () -> focus = idx);
	}

	/**
	 * Material button: primary is the accent gradient with on-primary text, secondary a tonal
	 * surface. A key hint sits in its own small chip after the label.
	 */
	private void button(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h, String lbl, boolean on, boolean primaryStyle, String hint, Runnable action) {
		boolean hot = on && hovered(mx, my, x, y, w, h);
		float r = h / 2f;
		int tc;
		if (!on) {
			RoundRect.fill(g, x, y, w, h, r, withA(container, 0xD0));
			RoundRect.outline(g, x, y, w, h, r, withA(TEXT, 0x10), withA(TEXT, 0x06));
			tc = FAINT;
		} else if (primaryStyle) {
			int a = hot ? mixColor(primary, 0xFFFFFFFF, 0.14f) : primary;
			int b = hot ? mixColor(primary2, 0xFFFFFFFF, 0.14f) : primary2;
			RoundRect.fill(g, x, y + 2, w, h, r, withA(primary, 0x30));
			// accent left to right, a touch lighter along the top edge
			RoundRect.draw(g, x, y, w, h, r, mixColor(a, 0xFFFFFFFF, 0.12f), mixColor(b, 0xFFFFFFFF, 0.12f), b, a, false);
			tc = onPrimary;
		} else {
			RoundRect.vertical(g, x, y, w, h, r, hot ? containerHighest : containerHigh, hot ? containerHigh : container);
			RoundRect.outline(g, x, y, w, h, r, withA(TEXT, hot ? 0x34 : 0x1E), withA(TEXT, 0x0A));
			tc = TEXT;
		}
		T fontSize = h >= 28 ? T.TITLE : T.BODY;
		float labelW = tw(lbl, fontSize);
		if (hint != null && on) {
			float hh = 13;
			float hw = Math.max(hh, tw(hint, T.SMALL) + 8);
			float total = labelW + 6 + hw;
			float sx = x + (w - total) / 2f;
			label(g, lbl, sx, y, h, tc, fontSize);
			float hx = sx + labelW + 6;
			float hy = y + (h - hh) / 2f;
			RoundRect.fill(g, hx, hy, hw, hh, 4, primaryStyle ? withA(onPrimary, 0x30) : withA(TEXT, 0x16));
			centered(g, hint, hx + hw / 2f, hy, hh, primaryStyle ? withA(onPrimary, 0xD0) : MUTED, T.SMALL);
		} else {
			centered(g, lbl, x + w / 2f, y, h, tc, fontSize);
		}
		if (on) {
			hit(x, y, w, h, action);
		}
	}

	/** A small tonal pill button sized to its label. Returns the x after it (with a gap). */
	private float chipButton(GuiGraphicsExtractor g, int mx, int my, float x, float y, String lbl, boolean on, Runnable action) {
		float h = 24;
		float w = tw(lbl, T.BODY) + 20;
		button(g, mx, my, x, y, w, h, lbl, on, false, null, action);
		return x + w + 6;
	}

	/** A label pill; returns its right edge. */
	private float badge(GuiGraphicsExtractor g, float x, float y, String lbl, int bg, int fg) {
		return badge(g, x, y, 16, lbl, bg, bg, fg);
	}

	private float badge(GuiGraphicsExtractor g, float x, float y, float h, String lbl, int from, int to, int fg) {
		float w = badgeWidth(lbl, h);
		RoundRect.horizontal(g, x, y, w, h, h / 2f, from, to);
		centered(g, lbl, x + w / 2f, y, h, fg, T.BODY);
		return x + w;
	}

	private float badgeWidth(String lbl, float h) {
		return tw(lbl, T.BODY) + h;
	}

	private void drawChip(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
		GuiDraw.circle(g, cx, cy + 1, r, 0x70000000);
		GuiDraw.circle(g, cx, cy, r, color);
		GuiDraw.circle(g, cx, cy, r * 0.72f, mixColor(color, 0xFFFFFFFF, 0.25f));
		GuiDraw.circle(g, cx, cy, r * 0.58f, color);
		for (int k = 0; k < 4; k++) {
			float a = k * (float) Math.PI / 2f + 0.4f;
			GuiDraw.circle(g, cx + (float) Math.cos(a) * r * 0.86f, cy + (float) Math.sin(a) * r * 0.86f, r * 0.13f, 0xE0FFFFFF);
		}
	}

	private void drawChevron(GuiGraphicsExtractor g, float x, float y, int color, boolean right) {
		for (int i = 0; i < 4; i++) {
			float off = right ? i : -i;
			GuiDraw.fill(g, x + off - (right ? 0 : 1), y - 4 + i, 1, 8 - i * 2, color);
		}
	}

	// ---- coins burst
	private void burstCoins(float x, float y, int count) {
		for (int i = 0; i < count; i++) {
			double a = Math.random() * Math.PI * 2;
			double s = 40 + Math.random() * 120;
			coins.add(new float[] {x, y, (float) (Math.cos(a) * s), (float) (Math.sin(a) * s - 90), 1.6f + (float) Math.random() * 0.8f, (float) Math.random() * 6f});
		}
	}

	private void updateCoins(GuiGraphicsExtractor g, float dt) {
		for (int i = coins.size() - 1; i >= 0; i--) {
			float[] c = coins.get(i);
			c[0] += c[2] * dt;
			c[1] += c[3] * dt;
			c[3] += 260 * dt;
			c[4] -= dt;
			c[5] += dt * 10f;
			if (c[4] <= 0) {
				coins.remove(i);
				continue;
			}
			float a = Math.min(1f, c[4]);
			float wobble = Math.abs((float) Math.cos(c[5]));
			GuiDraw.circle(g, c[0], c[1], 3.2f * Math.max(0.3f, wobble), alpha(GOLD, a));
			GuiDraw.circle(g, c[0], c[1], 2f * Math.max(0.3f, wobble), alpha(0xFFFFE9A8, a));
		}
	}

	// ================================================================== input

	private record Hit(float x, float y, float w, float h, Runnable left, Runnable right) {
		Hit(float x, float y, float w, float h, Runnable left) {
			this(x, y, w, h, left, null);
		}
	}

	private void hit(float x, float y, float w, float h, Runnable action) {
		hits.add(new Hit(x, y, w, h, action));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double mx = event.x() / ui;
		double my = event.y() / ui;
		focus = -1;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
				Runnable r = event.button() == 1 ? h.right : h.left;
				if (r != null) {
					r.run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (focus >= 0) {
			if (key == InputConstants.KEY_BACKSPACE) {
				String v = fields[focus];
				if (!v.isEmpty()) {
					fields[focus] = v.substring(0, v.length() - 1);
				}
				return true;
			}
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				int f = focus;
				focus = -1;
				if (f == 0 && !spinning && !busy) {
					spin();
				} else if (f == 1 && !busy) {
					deal();
				} else if (f >= 2 && !busy && !fields[2].isBlank() && parse(fields[3]) > 0) {
					sendChips();
				}
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				focus = -1;
				return true;
			}
		} else if (shortcut(key)) {
			return true;
		}
		return super.keyPressed(event);
	}

	/** Keyboard play: Space spins or deals, H/S/D/P in blackjack, Y/N for insurance, 1-6 picks a chip. */
	private boolean shortcut(int key) {
		if (busy) {
			return false;
		}
		String phase = bj == null ? "none" : str(bj, "phase");
		boolean animating = time < lastCardTime() + 0.1f;
		if (key == InputConstants.KEY_SPACE) {
			if (tab == 0 && !spinning && balance > 0) {
				spin();
				return true;
			}
			if (tab == 1 && !animating && (bj == null || "done".equals(phase)) && balance > 0) {
				deal();
				return true;
			}
			if (tab == 2 && !rolling && !bets.isEmpty()) {
				spinRoulette();
				return true;
			}
			return false;
		}
		if (tab == 1 && !animating) {
			if ("player".equals(phase)) {
				switch (key) {
					case InputConstants.KEY_H -> act("hit");
					case InputConstants.KEY_S -> act("stand");
					case InputConstants.KEY_D -> {
						if (bj.get("canDouble").getAsBoolean()) {
							act("double");
						}
					}
					case InputConstants.KEY_P -> {
						if (bj.get("canSplit").getAsBoolean()) {
							act("split");
						}
					}
					default -> {
						return false;
					}
				}
				return true;
			}
			if ("insurance".equals(phase) && (key == InputConstants.KEY_Y || key == InputConstants.KEY_N)) {
				act(key == InputConstants.KEY_Y ? "insurance" : "noinsurance");
				return true;
			}
		}
		if (tab == 2 && !rolling && key >= InputConstants.KEY_1 && key <= InputConstants.KEY_6) {
			chip = key - InputConstants.KEY_1;
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (focus < 0) {
			return super.charTyped(event);
		}
		String s = event.codepointAsString();
		String v = fields[focus];
		if (focus == 2) {
			if (s.matches("[A-Za-z0-9_]") && v.length() < 16) {
				fields[focus] = v + s;
			}
		} else if (s.matches("[0-9]") && v.length() < 12) {
			fields[focus] = (v.equals("0") ? "" : v) + s;
		} else if ((s.equals("k") || s.equals("K")) && !v.isEmpty() && v.length() < 9) {
			fields[focus] = v + "000";
		}
		return true;
	}

	// ================================================================== account

	private void applyAccount(JsonObject body, boolean first) {
		if (body == null) {
			return;
		}
		if (body.has("balance") && body.get("balance").isJsonPrimitive()) {
			balance = body.get("balance").getAsLong();
			pendingBalance = Long.MIN_VALUE;
			if (first) {
				shownBalance = balance;
			}
		}
		if (body.has("inbox") && body.get("inbox").isJsonArray()) {
			for (JsonElement e : body.getAsJsonArray("inbox")) {
				String t = str(e.getAsJsonObject(), "text");
				inbox.add(0, t);
				Minecraft client = Minecraft.getInstance();
				if (client.player != null) {
					client.player.sendSystemMessage(Component.literal("§6Casino §8» §e" + t));
				}
			}
		}
		if (body.has("blackjack") && body.get("blackjack").isJsonObject() && bj == null) {
			// a hand left open last time
			showBlackjack(body.getAsJsonObject("blackjack"), false);
		}
	}

	private void setStatus(String text, boolean bad) {
		status = text == null ? "" : text;
		statusBad = bad;
		statusAt = time;
	}

	// ================================================================== helpers

	private int cx0() {
		return left + 14;
	}

	private int cy0() {
		return top + 46;
	}

	private static Component comp(String s, T t) {
		return switch (t) {
			case SMALL -> dev.stray.client.ui.MenuFont.hyprSmall(s);
			case BODY -> dev.stray.client.ui.MenuFont.hypr(s);
			case TITLE -> dev.stray.client.ui.MenuFont.hyprTitle(s);
		};
	}

	/**
	 * How far below the draw position the middle of a capital letter sits, per font: the
	 * baseline (7 + the font's shift) minus half the cap height (Google Sans Flex caps are
	 * 0.716 em). Centring on this instead of the vanilla 9px line box keeps every size
	 * optically centred in its pill.
	 */
	private static final float[] CAP_MID = {7.25f - 0.358f * 5.5f, 6.75f - 0.358f * 6.5f, 6.0f - 0.358f * 8.0f};

	/** Text in the Hyprland click GUI's fonts (small, body, title), drawn at their native size. */
	private void text(GuiGraphicsExtractor g, String s, float x, float y, int color, T t) {
		text(g, s, x, y, color, t, 1f);
	}

	private void text(GuiGraphicsExtractor g, String s, float x, float y, int color, T t, float scale) {
		if (((color >>> 24) & 0xFF) < 6) {
			return;
		}
		// Snap to whole screen pixels (not whole GUI units), so half-unit centring isn't lost.
		g.pose().pushMatrix();
		g.pose().translate(snap(x), snap(y));
		GuiDraw.text(g, font, comp(s, t), 0, 0, scale, color, false);
		g.pose().popMatrix();
	}

	private float snap(float v) {
		return Math.round(v * pixel) / pixel;
	}

	private float tw(String s, T t) {
		return font.width(comp(s, t));
	}

	/** Left-aligned, optically centred on the box's middle. */
	private void label(GuiGraphicsExtractor g, String s, float x, float boxY, float boxH, int color, T t) {
		text(g, s, x, boxY + boxH / 2f - CAP_MID[t.ordinal()], color, t);
	}

	/** Centred in a box. */
	private void centered(GuiGraphicsExtractor g, String s, float cx, float boxY, float boxH, int color, T t) {
		label(g, s, cx - tw(s, t) / 2f, boxY, boxH, color, t);
	}

	/** A Material icon (the click GUI's icon font), centred on the box's middle. */
	private void icon(GuiGraphicsExtractor g, String code, float x, float boxY, float boxH, int color) {
		g.pose().pushMatrix();
		g.pose().translate(snap(x), snap(boxY + (boxH - 9f) / 2f));
		GuiDraw.text(g, font, Component.literal(code).withStyle(dev.stray.client.ui.MenuFont.ICON), 0, 0, color, false);
		g.pose().popMatrix();
	}

	private String fitText(String s, float max, T t) {
		if (tw(s, t) <= max) {
			return s;
		}
		String out = s;
		while (out.length() > 1 && tw(out + "...", t) > max) {
			out = out.substring(0, out.length() - 1);
		}
		return out + "...";
	}

	/** Minecraft's font, for card suits and the slot 7 (the menu font has no such glyphs). */
	private void glyph(GuiGraphicsExtractor g, String s, float x, float y, int color, float scale) {
		GuiDraw.text(g, font, s, x, y, scale, color, false);
	}

	private float glyphWidth(String s) {
		return font.width(s);
	}

	private static boolean hovered(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static boolean isRed(int n) {
		for (int r : RED_NUMBERS) {
			if (r == n) {
				return true;
			}
		}
		return false;
	}

	private static String colorName(int n) {
		return n == 0 ? "green" : isRed(n) ? "red" : "black";
	}

	private static long parse(String s) {
		try {
			return s == null || s.isBlank() ? 0 : Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static long sum(Map<String, Long> m) {
		long t = 0;
		for (long v : m.values()) {
			t += v;
		}
		return t;
	}

	private static String fmt(long v) {
		return String.format(Locale.US, "%,d", v);
	}

	private static String grouped(String digits) {
		return fmt(parse(digits));
	}

	private static String shortAmount(long v) {
		if (v >= 1_000_000) {
			return (v % 1_000_000 == 0 ? String.valueOf(v / 1_000_000) : String.format(Locale.US, "%.1f", v / 1e6)) + "M";
		}
		if (v >= 1_000) {
			return (v % 1_000 == 0 ? String.valueOf(v / 1_000) : String.format(Locale.US, "%.1f", v / 1e3)) + "K";
		}
		return String.valueOf(v);
	}

	private static String str(JsonObject o, String key) {
		return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	private static int withA(int color, int a) {
		return (a << 24) | (color & 0xFFFFFF);
	}

	/** The accent with its hue turned a little (turn is a fraction of the colour wheel), for two-tone gradients. */
	private static int hueShift(int color, float turn) {
		float r = ((color >> 16) & 0xFF) / 255f;
		float g = ((color >> 8) & 0xFF) / 255f;
		float b = (color & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float hue = 0f;
		if (d > 1e-5f) {
			if (max == r) {
				hue = ((g - b) / d) / 6f;
			} else if (max == g) {
				hue = ((b - r) / d + 2f) / 6f;
			} else {
				hue = ((r - g) / d + 4f) / 6f;
			}
		}
		float sat = max <= 0f ? 0f : d / max;
		hue = ((hue + turn) % 1f + 1f) % 1f;
		float h6 = hue * 6f;
		int sector = (int) Math.floor(h6) % 6;
		float f = h6 - (float) Math.floor(h6);
		float p = max * (1f - sat);
		float q = max * (1f - f * sat);
		float t = max * (1f - (1f - f) * sat);
		float[] rgb = switch (sector) {
			case 0 -> new float[] {max, t, p};
			case 1 -> new float[] {q, max, p};
			case 2 -> new float[] {p, max, t};
			case 3 -> new float[] {p, q, max};
			case 4 -> new float[] {t, p, max};
			default -> new float[] {max, p, q};
		};
		return (color & 0xFF000000) | (Math.round(rgb[0] * 255f) << 16) | (Math.round(rgb[1] * 255f) << 8) | Math.round(rgb[2] * 255f);
	}

	private static int alpha(int color, float a) {
		int base = (color >>> 24) & 0xFF;
		return (Math.round(base * Math.max(0f, Math.min(1f, a))) << 24) | (color & 0xFFFFFF);
	}

	private static int mixColor(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ar = (a >> 16) & 0xFF;
		int ag = (a >> 8) & 0xFF;
		int ab = a & 0xFF;
		int br = (b >> 16) & 0xFF;
		int bg = (b >> 8) & 0xFF;
		int bb = b & 0xFF;
		return (a & 0xFF000000) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}

	private static float easeOutBack(float t) {
		float c1 = 1.20158f;
		float c3 = c1 + 1f;
		return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
	}

	private static float floorModF(float a, float m) {
		float r = a % m;
		return r < 0 ? r + m : r;
	}
}
