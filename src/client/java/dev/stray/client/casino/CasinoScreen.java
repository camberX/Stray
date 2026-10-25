package dev.stray.client.casino;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.render.GuiDraw;
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
	// ---- palette
	private static final int BG = 0xF50F1115;
	private static final int SURFACE = 0xFF171A20;
	private static final int SURFACE_HI = 0xFF20242C;
	private static final int SURFACE_HOVER = 0xFF2A2F39;
	private static final int LINE = 0xFF2C313B;
	private static final int INPUT = 0xFF0C0E12;
	private static final int FELT = 0xFF0F4A33;
	private static final int GOLD = 0xFFF5C451;
	private static final int GOLD_DARK = 0xFFB8862B;
	private static final int INK = 0xFF1A1205;
	private static final int TEXT = 0xFFF4F6F9;
	private static final int MUTED = 0xFFB3BCC8;
	private static final int FAINT = 0xFF838D9B;
	private static final int RED = 0xFFF0565B;
	private static final int GREEN = 0xFF46DC95;
	private static final int CARD_RED = 0xFFD62F3A;

	private static final int W = 460;
	private static final int H = 326;
	/** Content area inside the window. */
	private static final int CW = W - 28;
	private static final int CH = H - 56 - 36;
	private static final int TABLE_H = 172;

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

		// window
		GuiDraw.rounded(g, left - 1, top - 1, W + 2, H + 2, 13, LINE);
		GuiDraw.rounded(g, left, top, W, H, 12, BG);
		// gold glow fading down from the top (stacked rounded layers keep the corners)
		for (int i = 0; i < 6; i++) {
			GuiDraw.rounded(g, left, top, W, 46 - i * 6, 12, 0x07F5C451);
		}
		text(g, "Casino", left + 16, top + 12, TEXT, T.TITLE);
		text(g, "Play money", left + 16, top + 27, FAINT, T.SMALL);
		drawTabs(g, mouseX, mouseY);
		drawBalance(g);
		GuiDraw.fill(g, left + 12, top + 46, W - 24, 1, LINE);

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

	private void drawTabs(GuiGraphicsExtractor g, int mx, int my) {
		float tw = 62;
		float tx = left + 104;
		float ty = top + 11;
		GuiDraw.rounded(g, tx, ty, tw * TABS.length, 24, 12, SURFACE);
		GuiDraw.rounded(g, tx + 2 + tabT * tw, ty + 2, tw - 4, 20, 10, GOLD);
		for (int i = 0; i < TABS.length; i++) {
			boolean hot = hovered(mx, my, tx + i * tw, ty, tw, 24);
			float on = Math.max(0f, 1f - Math.abs(tabT - i));
			int c = mixColor(hot ? TEXT : MUTED, INK, on);
			centered(g, TABS[i], tx + i * tw + tw / 2f, ty, 24, c, T.BODY);
			final int idx = i;
			hit(tx + i * tw, ty, tw, 24, () -> {
				tab = idx;
				focus = -1;
				if (idx == 3) {
					refreshTop();
				}
			});
		}
	}

	private void drawBalance(GuiGraphicsExtractor g) {
		String value = balance < 0 ? "..." : fmt(Math.round(shownBalance));
		float w = tw(value, T.TITLE) + 36;
		float x = left + W - 14 - w;
		float y = top + 11;
		GuiDraw.rounded(g, x, y, w, 24, 12, SURFACE);
		drawChip(g, x + 13, y + 12, 7, GOLD);
		label(g, value, x + 26, y, 24, GOLD, T.TITLE);
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
		float w = Math.min(W - 40, tw(status, T.BODY) + 32);
		float x = left + (W - w) / 2f;
		float y = top + H - 29 + (1f - Math.min(1f, age / 0.2f)) * 6f;
		GuiDraw.rounded(g, x, y + 1, w, 22, 11, alpha(0x80000000, a));
		GuiDraw.rounded(g, x, y, w, 22, 11, alpha(SURFACE_HI, a));
		GuiDraw.circle(g, x + 12, y + 11, 3.5f, alpha(statusBad ? RED : GREEN, a));
		label(g, fitText(status, w - 30, T.BODY), x + 21, y, 22, alpha(TEXT, a), T.BODY);
	}

	// ================================================================== slots

	private void drawSlots(GuiGraphicsExtractor g, int mx, int my, float dt, int y, int h) {
		int x0 = cx0();
		// machine card
		GuiDraw.rounded(g, x0, y, 240, h, 10, SURFACE);
		int cell = 42;
		int reelW = 60;
		int gap = 8;
		int rx0 = x0 + (240 - (reelW * 3 + gap * 2)) / 2;
		int ry = y + 16;
		GuiDraw.rounded(g, rx0 - 7, ry - 7, reelW * 3 + gap * 2 + 14, cell * 3 + 14, 9, GOLD_DARK);
		GuiDraw.rounded(g, rx0 - 5, ry - 5, reelW * 3 + gap * 2 + 10, cell * 3 + 10, 8, 0xFF140C05);
		advanceReels(dt);
		for (int i = 0; i < 3; i++) {
			int rx = rx0 + i * (reelW + gap);
			GuiDraw.fillGradient(g, rx, ry, reelW, cell * 1.5f, 0xFFD9D2C5, 0xFFFFFFFF);
			GuiDraw.fillGradient(g, rx, ry + cell * 1.5f, reelW, cell * 1.5f, 0xFFFFFFFF, 0xFFD9D2C5);
			GuiDraw.scissor(g, rx, ry, reelW, cell * 3);
			double p = reel[i];
			int base = (int) Math.floor(p);
			for (int j = base - 2; j <= base + 3; j++) {
				float sy = ry + cell + (j - (float) p) * cell;
				char sym = SLOT_STRIP.charAt(Math.floorMod(j, SLOT_STRIP.length()));
				drawSymbol(g, sym, rx + reelW / 2f, sy + cell / 2f);
			}
			GuiDraw.disableScissor(g);
			GuiDraw.fillGradient(g, rx, ry, reelW, 16, 0xA0000000, 0x00000000);
			GuiDraw.fillGradient(g, rx, ry + cell * 3 - 16, reelW, 16, 0x00000000, 0xA0000000);
		}
		boolean winning = !spinning && slotWin > 0 && time - slotWinAt < 5f;
		float pulse = winning ? 0.5f + 0.5f * (float) Math.sin(time * 10f) : 0f;
		int lineC = winning ? mixColor(GOLD, 0xFFFFFFFF, pulse) : 0xB0E5484D;
		float lineY = ry + cell * 1.5f;
		GuiDraw.fill(g, rx0 - 4, lineY - 1, reelW * 3 + gap * 2 + 8, 2, lineC);
		drawChevron(g, rx0 - 9, lineY, lineC, true);
		drawChevron(g, rx0 + reelW * 3 + gap * 2 + 9, lineY, lineC, false);

		// result
		float mid = x0 + 120;
		int resY = ry + cell * 3 + 14;
		if (winning) {
			float t = Math.min(1f, (time - slotWinAt) / 0.35f);
			float s = 1.6f * (1f + 0.35f * (1f - easeOutBack(t)));
			String txt = slotMultiplier >= 50 ? "BIG WIN" : "WIN";
			text(g, txt, mid - tw(txt, T.TITLE) * s / 2f, resY, alpha(GOLD, t), T.TITLE, s);
			String amt = "+" + fmt(slotWin) + " chips  (" + slotMultiplier + "x)";
			centered(g, amt, mid, resY + 22, 14, alpha(GREEN, t), T.BODY);
		} else if (!spinning && slotsResult) {
			centered(g, "No win this time", mid, resY + 6, 14, MUTED, T.BODY);
		} else if (!spinning) {
			centered(g, "Set your bet and spin", mid, resY + 2, 14, MUTED, T.BODY);
			centered(g, "Space also spins", mid, resY + 16, 12, FAINT, T.SMALL);
		}

		// controls
		int px = x0 + 250;
		int pw = CW - 250;
		GuiDraw.rounded(g, px, y, pw, h, 10, SURFACE);
		px += 12;
		pw -= 24;
		text(g, "Bet", px, y + 12, MUTED, T.SMALL);
		betField(g, mx, my, 0, px, y + 22, pw);
		boolean canSpin = !spinning && !busy && balance > 0;
		button(g, mx, my, px, y + 72, pw, 30, spinning ? "Spinning..." : "Spin", canSpin, true, null, this::spin);
		GuiDraw.fill(g, px, y + 112, pw, 1, LINE);
		text(g, "Paytable", px, y + 118, MUTED, T.SMALL);
		float py = y + 130;
		for (String[] row : PAYTABLE) {
			if (row[0].length() == 3 && !row[0].contains(" ")) {
				float ix = px + 5;
				for (int k = 0; k < 3; k++) {
					char c = row[0].charAt(k);
					if (c != '_') {
						drawSymbolSmall(g, c, ix, py + 5);
					} else {
						text(g, "any", ix - 4, py + 1, FAINT, T.SMALL);
					}
					ix += 13;
				}
			} else {
				label(g, row[0], px, py, 10, TEXT, T.BODY);
			}
			label(g, row[1], px + pw - tw(row[1], T.BODY), py, 10, GOLD, T.BODY);
			py += 10;
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

	private void drawBlackjack(GuiGraphicsExtractor g, int mx, int my, float dt, int y, int h) {
		int tx = cx0();
		int tw = CW;
		int th = TABLE_H;
		GuiDraw.rounded(g, tx - 3, y - 3, tw + 6, th + 6, 13, 0xFF4A2F17);
		GuiDraw.rounded(g, tx, y, tw, th, 11, FELT);
		for (int i = 0; i < 5; i++) {
			GuiDraw.rounded(g, tx, y, tw, th - i * 30, 11, 0x06FFFFFF);
		}
		// shoe
		GuiDraw.rounded(g, tx + tw - 48, y + 8, 38, 26, 5, 0xFF2A1C10);
		drawCardBack(g, tx + tw - 43, y + 5, 0.62f);
		text(g, "Dealer", tx + 12, y + 10, 0xC0F5C451, T.SMALL);

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
		if (bj != null) {
			int dealerTotal = bj.get("dealerTotal").getAsInt();
			if (!"done".equals(phase) || time >= last) {
				badge(g, tx + 12, y + 22, String.valueOf(dealerTotal), 0xD0101317, TEXT);
			}
			JsonArray hands = bj.getAsJsonArray("hands");
			int n = hands.size();
			int active = bj.get("active").getAsInt();
			for (int i = 0; i < n; i++) {
				JsonObject hnd = hands.get(i).getAsJsonObject();
				float hx = handX(i, n);
				float hy = handY();
				if ("player".equals(phase) && i == active && n > 1) {
					GuiDraw.roundedOutline(g, hx - 6, hy - 22, 86, 70, 7, alpha(GOLD, 0.55f + 0.45f * (float) Math.sin(time * 6f)), 1.2f);
				}
				int total = hnd.get("total").getAsInt();
				String totalTxt = (hnd.get("soft").getAsBoolean() && total < 21 ? "Soft " : "") + total;
				float bx = badge(g, hx, hy - 17, totalTxt, 0xD0101317, TEXT);
				String betTxt = fmt(hnd.get("bet").getAsLong()) + (hnd.get("doubled").getAsBoolean() ? " (doubled)" : "");
				if (settled && !hnd.get("result").isJsonNull()) {
					String res = str(hnd, "result");
					int col = switch (res) {
						case "win", "blackjack" -> GREEN;
						case "push" -> MUTED;
						default -> RED;
					};
					String lbl = switch (res) {
						case "blackjack" -> "Blackjack +" + fmt(hnd.get("payout").getAsLong() - hnd.get("bet").getAsLong());
						case "win" -> "Win +" + fmt(hnd.get("bet").getAsLong());
						case "push" -> "Push";
						case "bust" -> "Bust";
						default -> "Lose";
					};
					float pop = Math.min(1f, (time - last - 0.2f) / 0.3f);
					badge(g, bx + 4, hy - 17, lbl, alpha(0xE0101317, pop), alpha(col, pop));
				} else {
					drawChip(g, bx + 10, hy - 10, 5.5f, GOLD);
					label(g, betTxt, bx + 18, hy - 17, 14, GOLD, T.SMALL);
				}
			}
			if (settled) {
				String msg = str(bj, "message");
				centered(g, msg, tx + tw / 2f, y + 62, 14, TEXT, T.TITLE);
				long insPay = bj.has("insurancePayout") ? bj.get("insurancePayout").getAsLong() : 0;
				if (insPay > 0) {
					centered(g, "Insurance pays " + fmt(insPay), tx + tw / 2f, y + 78, 12, GREEN, T.BODY);
				}
			}
		}
		if (!settled) {
			centered(g, "BLACKJACK PAYS 3 TO 2", tx + tw / 2f, y + 64, 12, 0x90F5C451, T.SMALL);
			centered(g, "Dealer stands on all 17s  ·  Insurance pays 2 to 1", tx + tw / 2f, y + 76, 12, 0x70FFFFFF, T.SMALL);
		}

		// controls under the table
		boolean animating = time < last + 0.1f;
		int cy = y + th + 8;
		if (bj == null || "done".equals(phase)) {
			if (!animating) {
				text(g, "Bet", tx, cy, MUTED, T.SMALL);
				betField(g, mx, my, 1, tx, cy + 9, 170);
				button(g, mx, my, tx + 182, cy + 9, 140, 42, "Deal", !busy && balance > 0, true, "Space", this::deal);
			}
		} else if ("insurance".equals(phase)) {
			if (!animating) {
				label(g, "The dealer shows an Ace. Take insurance for " + fmt(insuranceCost()) + "?", tx, cy, 14, TEXT, T.BODY);
				button(g, mx, my, tx, cy + 18, 140, 28, "Insure", !busy, true, "Y", () -> act("insurance"));
				button(g, mx, my, tx + 148, cy + 18, 140, 28, "No thanks", !busy, false, "N", () -> act("noinsurance"));
			}
		} else if (!animating) {
			float bw = (tw - 3 * 8) / 4f;
			button(g, mx, my, tx, cy + 6, bw, 32, "Hit", !busy, true, "H", () -> act("hit"));
			button(g, mx, my, tx + (bw + 8), cy + 6, bw, 32, "Stand", !busy, false, "S", () -> act("stand"));
			button(g, mx, my, tx + 2 * (bw + 8), cy + 6, bw, 32, "Double", !busy && bj.get("canDouble").getAsBoolean(), false, "D", () -> act("double"));
			button(g, mx, my, tx + 3 * (bw + 8), cy + 6, bw, 32, "Split", !busy && bj.get("canSplit").getAsBoolean(), false, "P", () -> act("split"));
		}
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

	private float handX(int i, int n) {
		float spacing = 104;
		return cx0() + CW / 2f - (n - 1) * spacing / 2f + i * spacing - 22;
	}

	private float handY() {
		return cy0() + TABLE_H - 50;
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
		float shoeX = tx + tw - 43;
		float shoeY = y + 5;
		// dealer
		JsonArray dealer = view.getAsJsonArray("dealer");
		boolean done = "done".equals(str(view, "phase"));
		for (int i = 0; i < dealer.size(); i++) {
			JsonElement e = dealer.get(i);
			String code = e.isJsonNull() ? "?" : e.getAsString();
			float cx = tx + tw / 2f - 30 + i * 20;
			float cy = y + 14;
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
				c.tx = handX(hIdx, hands.size()) + i * (hands.size() > 2 ? 10 : 14);
				c.ty = handY() - i * 2;
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
		float w = 30;
		float h = 42;
		g.pose().pushMatrix();
		g.pose().translate(x + w / 2f, y);
		g.pose().scale(Math.max(0.02f, scaleX), 1f);
		g.pose().translate(-w / 2f, 0);
		GuiDraw.rounded(g, 0.5f, 1.5f, w, h, 3, 0x70000000);
		if (code == null) {
			drawCardBackLocal(g, w, h);
		} else {
			GuiDraw.rounded(g, 0, 0, w, h, 3, 0xFFFBFAF6);
			String rank = code.substring(0, code.length() - 1);
			char suit = code.charAt(code.length() - 1);
			String suitGlyph = switch (suit) {
				case 'H' -> "♥";
				case 'D' -> "♦";
				case 'C' -> "♣";
				default -> "♠";
			};
			int col = suit == 'H' || suit == 'D' ? CARD_RED : 0xFF16181D;
			text(g, rank, 3, 3, col, T.TITLE);
			glyph(g, suitGlyph, 3.5f, 14, col, 0.8f);
			glyph(g, suitGlyph, w / 2f - glyphWidth(suitGlyph) * 1.7f / 2f + 2, h / 2f - 2, col, 1.7f);
		}
		g.pose().popMatrix();
	}

	private void drawCardBack(GuiGraphicsExtractor g, float x, float y, float s) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s, s);
		drawCardBackLocal(g, 30, 42);
		g.pose().popMatrix();
	}

	private void drawCardBackLocal(GuiGraphicsExtractor g, float w, float h) {
		GuiDraw.rounded(g, 0, 0, w, h, 3, 0xFFFAFAF7);
		GuiDraw.rounded(g, 2, 2, w - 4, h - 4, 2, 0xFF8E1B24);
		for (int i = 0; i < 7; i++) {
			GuiDraw.fill(g, 4, 5 + i * 5, w - 8, 1, 0x40FFFFFF);
		}
		GuiDraw.roundedOutline(g, 4, 4, w - 8, h - 8, 2, 0x80F5C451, 0.8f);
	}

	// ================================================================== roulette

	private void drawRoulette(GuiGraphicsExtractor g, int mx, int my, float dt, int y, int h) {
		int x0 = cx0();
		float cx = x0 + 80;
		float cy = y + 82;
		float r = 70;
		advanceWheel(dt);
		drawWheel(g, cx, cy, r);
		boolean showResult = !rolling && rouletteNumber >= 0 && time - rouletteDoneAt < 8f;
		if (showResult) {
			int col = rouletteNumber == 0 ? 0xFF16A34A : isRed(rouletteNumber) ? 0xFFC81E28 : 0xFF16181D;
			float pop = Math.min(1f, (time - rouletteDoneAt) / 0.3f);
			GuiDraw.circle(g, cx, cy, 16 * easeOutBack(pop), col);
			centered(g, String.valueOf(rouletteNumber), cx, cy - 7, 14, 0xFFFFFFFF, T.TITLE);
		}
		// result line + history under the wheel
		if (showResult) {
			String res = rouletteWin > 0 ? "Won " + fmt(rouletteWin) : "No win";
			centered(g, res, cx, y + 160, 14, rouletteWin > 0 ? GREEN : MUTED, T.TITLE);
		} else if (rolling) {
			centered(g, "No more bets", cx, y + 160, 14, MUTED, T.BODY);
		}
		float hx = x0 + 2;
		float hy = y + 184;
		text(g, "Last", hx, hy + 2, FAINT, T.SMALL);
		hx += 22;
		for (String s : rouletteHistory) {
			int n = Integer.parseInt(s);
			int col = n == 0 ? 0xFF16A34A : isRed(n) ? 0xFFC81E28 : 0xFF2A2F38;
			GuiDraw.rounded(g, hx, hy - 1, 15, 13, 4, col);
			centered(g, s, hx + 7.5f, hy - 1, 13, 0xFFFFFFFF, T.SMALL);
			hx += 17;
		}

		// board
		float bx = x0 + 168;
		float by = y;
		float cw = 18;
		float ch = 22;
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
		// chips
		float ry = by + ch * 5 + 12;
		text(g, "Chip", bx, ry + 5, MUTED, T.SMALL);
		for (int i = 0; i < CHIPS.length; i++) {
			float x = bx + 30 + i * 30;
			boolean sel = i == chip;
			float lift = sel ? -3 : 0;
			if (sel) {
				GuiDraw.circle(g, x + 10, ry + 9 + lift, 12.5f, GOLD);
			}
			drawChip(g, x + 10, ry + 9 + lift, 11, CHIP_COLORS[i]);
			String lbl = shortAmount(CHIPS[i]);
			centered(g, lbl, x + 10, ry + 2 + lift, 14, i == 0 || i == 4 ? 0xFF111111 : 0xFFFFFFFF, T.SMALL);
			final int idx = i;
			hit(x - 2, ry - 4, 24, 26, () -> chip = idx);
		}
		long total = 0;
		for (long v : bets.values()) {
			total += v;
		}
		label(g, "Total bet", bx, ry + 26, 14, MUTED, T.BODY);
		label(g, fmt(total), bx + tw("Total bet", T.BODY) + 6, ry + 26, 14, GOLD, T.BODY);
		float ay = ry + 44;
		float ax = bx;
		ax = chipButton(g, mx, my, ax, ay, "Clear", !locked && !bets.isEmpty(), bets::clear);
		ax = chipButton(g, mx, my, ax, ay, "Rebet", !locked && !lastBets.isEmpty(), () -> {
			bets.clear();
			bets.putAll(lastBets);
		});
		ax = chipButton(g, mx, my, ax, ay, "2x", !locked && !bets.isEmpty(), () -> bets.replaceAll((k, v) -> v * 2));
		button(g, mx, my, ax, ay, x0 + CW - ax, 26, rolling ? "Rolling..." : "Spin", !locked && !bets.isEmpty(), true, "Space", this::spinRoulette);
		text(g, "Left click adds a chip, right click takes one off. 1-6 picks a chip.", bx, ay + 34, FAINT, T.SMALL);
	}

	private void boardCell(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h, String lbl, int color, String key, boolean locked) {
		boolean hot = !locked && hovered(mx, my, x, y, w, h);
		boolean winner = !rolling && rouletteNumber >= 0 && time - rouletteDoneAt < 8f && wins(key, rouletteNumber);
		int fill = hot ? mixColor(color, 0xFFFFFFFF, 0.2f) : color;
		if (winner) {
			fill = mixColor(color, GOLD, 0.35f + 0.25f * (float) Math.sin(time * 8f));
		}
		GuiDraw.fill(g, x, y, w, h, fill);
		GuiDraw.border(g, x, y, w, h, 0x50FFFFFF, 0.5f);
		Long amount = bets.get(key);
		if (amount != null && amount > 0) {
			int ci = 0;
			for (int i = 0; i < CHIPS.length; i++) {
				if (amount >= CHIPS[i]) {
					ci = i;
				}
			}
			drawChip(g, x + w / 2f, y + h / 2f, 7.5f, CHIP_COLORS[ci]);
			centered(g, shortAmount(amount), x + w / 2f, y + h / 2f - 6, 12, ci == 0 || ci == 4 ? 0xFF111111 : 0xFFFFFFFF, T.SMALL);
		} else {
			centered(g, lbl, x + w / 2f, y, h, 0xFFFFFFFF, T.BODY);
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
				burstCoins(cx0() + 80, cy0() + 82, rouletteWin >= 10L * Math.max(1, sum(lastBets)) ? 50 : 18);
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
		GuiDraw.rounded(g, x0, y, cw, h, 10, SURFACE);
		float x = x0 + 14;
		float w = cw - 28;
		text(g, "Send chips", x, y + 12, TEXT, T.TITLE);
		text(g, "Player", x, y + 34, MUTED, T.SMALL);
		field(g, mx, my, 2, x, y + 44, w, "Minecraft name");
		text(g, "Amount", x, y + 76, MUTED, T.SMALL);
		field(g, mx, my, 3, x, y + 86, w, "0");
		button(g, mx, my, x, y + 118, w, 28, "Send", !busy && !fields[2].isBlank() && parse(fields[3]) > 0, true, null, this::sendChips);
		text(g, "Arrives right away. New players start with 1,000.", x, y + 152, FAINT, T.SMALL);
		text(g, "Inbox", x, y + 172, MUTED, T.SMALL);
		if (inbox.isEmpty()) {
			text(g, "Nothing yet", x, y + 184, FAINT, T.BODY);
		}
		for (int i = 0; i < Math.min(4, inbox.size()); i++) {
			text(g, fitText(inbox.get(i), w, T.BODY), x, y + 184 + i * 11, TEXT, T.BODY);
		}

		int lx = x0 + cw + 10;
		GuiDraw.rounded(g, lx, y, cw, h, 10, SURFACE);
		text(g, "Leaderboard", lx + 14, y + 12, TEXT, T.TITLE);
		chipButton(g, mx, my, lx + cw - 14 - (tw("Refresh", T.BODY) + 16), y + 9, "Refresh", !busy, this::refreshTop);
		for (int i = 0; i < top10.size(); i++) {
			String[] row = top10.get(i);
			float ry = y + 34 + i * 19;
			boolean me = row[0].equalsIgnoreCase(name);
			if (me) {
				GuiDraw.rounded(g, lx + 8, ry - 2, cw - 16, 18, 6, SURFACE_HI);
			}
			int medal = i == 0 ? GOLD : i == 1 ? 0xFFC9D1DB : i == 2 ? 0xFFD08A4A : 0xFF3A414C;
			GuiDraw.circle(g, lx + 22, ry + 7, 6.5f, medal);
			centered(g, String.valueOf(i + 1), lx + 22, ry, 14, i < 3 ? 0xFF15110A : TEXT, T.SMALL);
			label(g, row[0], lx + 34, ry, 14, me ? GOLD : TEXT, T.BODY);
			label(g, row[1], lx + cw - 14 - tw(row[1], T.BODY), ry, 14, GOLD, T.BODY);
		}
		if (top10.isEmpty()) {
			text(g, "Loading...", lx + 14, y + 36, FAINT, T.BODY);
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

	private void betField(GuiGraphicsExtractor g, int mx, int my, int idx, float x, float y, float w) {
		field(g, mx, my, idx, x, y, w, "Bet");
		float by = y + 28;
		String[] labels = {"1/2", "2x", "+100", "Max"};
		float bw = (w - 3 * 5) / 4f;
		for (int i = 0; i < labels.length; i++) {
			final int k = i;
			float bx = x + i * (bw + 5);
			boolean hot = hovered(mx, my, bx, by, bw, 18);
			GuiDraw.rounded(g, bx, by, bw, 18, 6, hot ? SURFACE_HOVER : SURFACE_HI);
			centered(g, labels[i], bx + bw / 2f, by, 18, TEXT, T.BODY);
			hit(bx, by, bw, 18, () -> {
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
	}

	private void field(GuiGraphicsExtractor g, int mx, int my, int idx, float x, float y, float w, String placeholder) {
		boolean focused = focus == idx;
		boolean hot = hovered(mx, my, x, y, w, 24);
		GuiDraw.rounded(g, x, y, w, 24, 7, focused ? GOLD : hot ? 0xFF4A5260 : LINE);
		GuiDraw.rounded(g, x + 1, y + 1, w - 2, 22, 6, INPUT);
		String value = fields[idx];
		boolean empty = value.isEmpty();
		String shown = empty ? placeholder : (idx == 2 ? value : grouped(value));
		label(g, shown, x + 9, y, 24, empty ? FAINT : TEXT, T.BODY);
		if (focused && (int) (time * 2) % 2 == 0) {
			float cxp = x + 9 + (empty ? 0 : tw(shown, T.BODY)) + 1;
			GuiDraw.fill(g, cxp, y + 6, 1, 12, GOLD);
		}
		hit(x, y, w, 24, () -> focus = idx);
	}

	private void button(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h, String lbl, boolean on, boolean primary, String hint, Runnable action) {
		boolean hot = on && hovered(mx, my, x, y, w, h);
		int fill;
		if (primary) {
			fill = on ? (hot ? 0xFFFFD36B : GOLD) : 0xFF3A3F48;
		} else {
			fill = on ? (hot ? SURFACE_HOVER : SURFACE_HI) : SURFACE;
		}
		GuiDraw.rounded(g, x, y + 1.5f, w, h, 8, 0x60000000);
		GuiDraw.rounded(g, x, y, w, h, 8, fill);
		if (!primary) {
			GuiDraw.roundedOutline(g, x, y, w, h, 8, on ? LINE : 0xFF22262D, 1f);
		}
		int tc = primary ? (on ? INK : 0xFF7A808A) : (on ? TEXT : FAINT);
		T font = h >= 28 ? T.TITLE : T.BODY;
		float labelW = tw(lbl, font);
		if (hint != null && on) {
			float hw = tw(hint, T.SMALL) + 8;
			float total = labelW + 6 + hw;
			float sx = x + w / 2f - total / 2f;
			label(g, lbl, sx, y, h, tc, font);
			float hx = sx + labelW + 6;
			GuiDraw.rounded(g, hx, y + h / 2f - 6, hw, 12, 4, primary ? 0x30000000 : 0x40FFFFFF);
			centered(g, hint, hx + hw / 2f, y + h / 2f - 6, 12, primary ? 0xC0201406 : MUTED, T.SMALL);
		} else {
			centered(g, lbl, x + w / 2f, y, h, tc, font);
		}
		if (on) {
			hit(x, y, w, h, action);
		}
	}

	private float chipButton(GuiGraphicsExtractor g, int mx, int my, float x, float y, String lbl, boolean on, Runnable action) {
		float w = tw(lbl, T.BODY) + 16;
		boolean hot = on && hovered(mx, my, x, y, w, 24);
		GuiDraw.rounded(g, x, y, w, 24, 8, on ? (hot ? SURFACE_HOVER : SURFACE_HI) : SURFACE);
		GuiDraw.roundedOutline(g, x, y, w, 24, 8, on ? LINE : 0xFF22262D, 1f);
		centered(g, lbl, x + w / 2f, y, 24, on ? TEXT : FAINT, T.BODY);
		if (on) {
			hit(x, y, w, 24, action);
		}
		return x + w + 6;
	}

	private float badge(GuiGraphicsExtractor g, float x, float y, String lbl, int bg, int fg) {
		float w = tw(lbl, T.BODY) + 12;
		GuiDraw.rounded(g, x, y, w, 14, 7, bg);
		centered(g, lbl, x + w / 2f, y, 14, fg, T.BODY);
		return x + w;
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
		return top + 56;
	}

	private static Component comp(String s, T t) {
		return switch (t) {
			case SMALL -> dev.stray.client.ui.MenuFont.hyprSmall(s);
			case BODY -> dev.stray.client.ui.MenuFont.hypr(s);
			case TITLE -> dev.stray.client.ui.MenuFont.hyprTitle(s);
		};
	}

	/** Text in the Hyprland click GUI's fonts (small, body, title), drawn at their native size. */
	private void text(GuiGraphicsExtractor g, String s, float x, float y, int color, T t) {
		text(g, s, x, y, color, t, 1f);
	}

	private void text(GuiGraphicsExtractor g, String s, float x, float y, int color, T t, float scale) {
		GuiDraw.text(g, font, comp(s, t), Math.round(x), Math.round(y), scale, color, false);
	}

	private float tw(String s, T t) {
		return font.width(comp(s, t));
	}

	/** Left-aligned, vertically centred in a box. */
	private void label(GuiGraphicsExtractor g, String s, float x, float boxY, float boxH, int color, T t) {
		text(g, s, x, GuiDraw.middle(boxY, boxH), color, t);
	}

	/** Centred in a box. */
	private void centered(GuiGraphicsExtractor g, String s, float cx, float boxY, float boxH, int color, T t) {
		text(g, s, cx - tw(s, t) / 2f, GuiDraw.middle(boxY, boxH), color, t);
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
