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
	private static final int FELT = 0xFF0E3B2A;
	private static final int FELT_DARK = 0xFF082419;
	private static final int PANEL = 0xF0121418;
	private static final int PANEL_LINE = 0xFF2A2F38;
	private static final int GOLD = 0xFFF5C451;
	private static final int GOLD_DARK = 0xFFB8862B;
	private static final int TEXT = 0xFFF2F4F7;
	private static final int MUTED = 0xFF9AA4B2;
	private static final int RED = 0xFFE5484D;
	private static final int GREEN = 0xFF3DD68C;
	private static final int CARD_RED = 0xFFD62F3A;
	private static final int BTN = 0xFF1F2530;
	private static final int BTN_HOT = 0xFF2B3442;

	private static final int W = 440;
	private static final int H = 290;
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
		left = (width - W) / 2;
		top = (height - H) / 2;
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

		// window
		GuiDraw.rounded(g, left - 1, top - 1, W + 2, H + 2, 12, PANEL_LINE);
		GuiDraw.rounded(g, left, top, W, H, 11, PANEL);
		// Gold glow fading down from the top: stacked rounded layers keep the window's corners.
		for (int i = 0; i < 8; i++) {
			GuiDraw.rounded(g, left, top, W, 36 - i * 4, 11, 0x08F5C451);
		}
		text(g, "STRAY", left + 14, top + 11, GOLD, 1.4f);
		text(g, "CASINO", left + 14 + width("STRAY") * 1.4f + 4, top + 11, TEXT, 1.4f);
		drawTabs(g, mouseX, mouseY);
		drawBalance(g);

		int bodyY = top + 40;
		int bodyH = H - 40 - 20;
		switch (tab) {
			case 0 -> drawSlots(g, mouseX, mouseY, dt, bodyY, bodyH);
			case 1 -> drawBlackjack(g, mouseX, mouseY, dt, bodyY, bodyH);
			case 2 -> drawRoulette(g, mouseX, mouseY, dt, bodyY, bodyH);
			default -> drawSend(g, mouseX, mouseY, bodyY, bodyH);
		}
		// status line
		float fade = Math.min(1f, Math.max(0f, 1f - (time - statusAt - 6f) / 1.5f));
		if (!status.isEmpty() && fade > 0.02f) {
			int c = statusBad ? RED : MUTED;
			text(g, status, left + 14, top + H - 15, alpha(c, fade), 1f);
		}
		text(g, "Play money only", left + W - 14 - width("Play money only") * 0.75f, top + H - 14, 0xFF5B6472, 0.75f);
		updateCoins(g, dt);
	}

	private void drawTabs(GuiGraphicsExtractor g, int mx, int my) {
		float tx = left + 118;
		float tw = 52;
		GuiDraw.rounded(g, tx, top + 8, tw * TABS.length, 20, 10, 0xFF1A1E25);
		GuiDraw.rounded(g, tx + 2 + tabT * tw, top + 10, tw - 4, 16, 8, GOLD_DARK);
		for (int i = 0; i < TABS.length; i++) {
			float cx = tx + i * tw + tw / 2f;
			boolean hot = hovered(mx, my, tx + i * tw, top + 8, tw, 20);
			int c = i == tab ? 0xFF1A1205 : hot ? TEXT : MUTED;
			text(g, TABS[i], cx - width(TABS[i]) / 2f, top + 14, c, 1f);
			final int idx = i;
			hit(tx + i * tw, top + 8, tw, 20, () -> {
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
		float w = width(value) + 30;
		float x = left + W - 12 - w;
		GuiDraw.rounded(g, x, top + 8, w, 20, 10, 0xFF1A1E25);
		drawChip(g, x + 10, top + 18, 6, GOLD);
		text(g, value, x + 20, top + 14, GOLD, 1f);
	}

	// ================================================================== slots

	private void drawSlots(GuiGraphicsExtractor g, int mx, int my, float dt, int y, int h) {
		int cell = 40;
		int reelW = 52;
		int gap = 8;
		int boxW = reelW * 3 + gap * 2 + 20;
		int bx = left + 18;
		int by = y + 8;
		int boxH = cell * 3 + 20;
		// cabinet
		GuiDraw.rounded(g, bx - 3, by - 3, boxW + 6, boxH + 6, 12, GOLD_DARK);
		GuiDraw.rounded(g, bx, by, boxW, boxH, 10, 0xFF1B0F06);
		advanceReels(dt);
		for (int i = 0; i < 3; i++) {
			int rx = bx + 10 + i * (reelW + gap);
			int ry = by + 10;
			GuiDraw.fillGradient(g, rx, ry, reelW, cell * 3, 0xFFDCD6CC, 0xFFFFFFFF);
			GuiDraw.fillGradient(g, rx, ry + cell * 1.5f, reelW, cell * 1.5f, 0xFFFFFFFF, 0xFFDCD6CC);
			GuiDraw.scissor(g, rx, ry, reelW, cell * 3);
			double p = reel[i];
			int base = (int) Math.floor(p);
			for (int j = base - 2; j <= base + 3; j++) {
				float sy = ry + cell + (j - (float) p) * cell;
				char sym = SLOT_STRIP.charAt(Math.floorMod(j, SLOT_STRIP.length()));
				drawSymbol(g, sym, rx + reelW / 2f, sy + cell / 2f, spinning && reelStopAt[i] > time ? 0.85f : 1f);
			}
			GuiDraw.disableScissor(g);
			// glass shading
			GuiDraw.fillGradient(g, rx, ry, reelW, 14, 0x90000000, 0x00000000);
			GuiDraw.fillGradient(g, rx, ry + cell * 3 - 14, reelW, 14, 0x00000000, 0x90000000);
		}
		// payline
		boolean winning = !spinning && slotWin > 0 && time - slotWinAt < 4f;
		float pulse = winning ? 0.5f + 0.5f * (float) Math.sin(time * 10f) : 0f;
		int lineC = winning ? mixColor(GOLD, 0xFFFFFFFF, pulse) : 0x90E5484D;
		GuiDraw.fill(g, bx + 4, by + 10 + cell * 1.5f - 1, boxW - 8, 2, lineC);
		drawChevron(g, bx + 2, by + 10 + cell * 1.5f, lineC, true);
		drawChevron(g, bx + boxW - 2, by + 10 + cell * 1.5f, lineC, false);

		// win banner
		if (winning) {
			float t = Math.min(1f, (time - slotWinAt) / 0.35f);
			float s = 1f + 0.4f * (1f - easeOutBack(t));
			String txt = slotMultiplier >= 50 ? "BIG WIN!" : "WIN";
			float tw = width(txt) * 2f * s;
			text(g, txt, bx + boxW / 2f - tw / 2f, by + boxH + 10, alpha(GOLD, t), 2f * s);
			String amt = "+" + fmt(slotWin);
			text(g, amt, bx + boxW / 2f - width(amt) / 2f, by + boxH + 30, alpha(GREEN, t), 1f);
		} else if (!spinning && slotsResult) {
			String txt = "No win";
			text(g, txt, bx + boxW / 2f - width(txt) / 2f, by + boxH + 16, MUTED, 1f);
		}

		// side panel: bet + spin + paytable
		int px = bx + boxW + 18;
		int pw = left + W - 16 - px;
		text(g, "BET", px, by, MUTED, 0.75f);
		betField(g, mx, my, 0, px, by + 9, pw);
		boolean canSpin = !spinning && !busy && balance > 0;
		bigButton(g, mx, my, px, by + 58, pw, 26, spinning ? "SPINNING..." : "SPIN", canSpin, this::spin);
		text(g, "PAYS (middle line)", px, by + 92, MUTED, 0.75f);
		float py = by + 102;
		for (String[] row : PAYTABLE) {
			float ix = px;
			if (row[0].length() == 3 && !row[0].contains(" ")) {
				for (int k = 0; k < 3; k++) {
					char c = row[0].charAt(k);
					if (c != '_') {
						drawSymbolSmall(g, c, ix + 5, py + 5);
					}
					ix += 12;
				}
			} else {
				text(g, row[0], ix, py + 1, TEXT, 0.75f);
			}
			text(g, row[1], px + pw - width(row[1]) * 0.75f, py + 1, GOLD, 0.75f);
			py += 11;
		}
	}

	private void drawSymbol(GuiGraphicsExtractor g, char sym, float cx, float cy, float alphaK) {
		if (sym == 'S') {
			float s = 2.6f;
			glyph(g, "7", cx - glyphWidth("7") * s / 2f + 1, cy - 4.5f * s, alpha(0xFFE11D2A, alphaK), s);
			return;
		}
		ItemStack stack = SLOT_ITEMS.get(sym);
		if (stack == null) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(cx - 14, cy - 14);
		g.pose().scale(1.75f, 1.75f);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
		if (sym == 'R') {
			text(g, "BAR", cx - width("BAR") * 0.6f / 2f, cy + 9, 0xFF3A2A05, 0.6f);
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
				burstCoins(left + 18 + 92, top + 48 + 70, slotMultiplier >= 50 ? 60 : slotMultiplier >= 10 ? 30 : 12);
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
		int tx = left + 12;
		int tw = W - 24;
		int th = h - 54;
		// table
		GuiDraw.rounded(g, tx - 2, y - 2, tw + 4, th + 4, 14, 0xFF5A3A1A);
		GuiDraw.rounded(g, tx, y, tw, th, 12, FELT);
		GuiDraw.fillGradient(g, tx + 2, y + 2, tw - 4, th / 2f, 0x30FFFFFF, 0x00000000);
		text(g, "BLACKJACK PAYS 3 TO 2", tx + tw / 2f - width("BLACKJACK PAYS 3 TO 2") * 0.75f / 2f, y + th / 2f - 2, 0x50F5C451, 0.75f);
		text(g, "Dealer stands on all 17s  ·  Insurance pays 2 to 1", tx + tw / 2f - width("Dealer stands on all 17s  ·  Insurance pays 2 to 1") * 0.6f / 2f, y + th / 2f + 8, 0x40FFFFFF, 0.6f);
		// shoe
		GuiDraw.rounded(g, tx + tw - 44, y + 8, 34, 22, 4, 0xFF2A1C10);
		drawCardBack(g, tx + tw - 40, y + 4, 0.6f);

		for (DCard c : cards) {
			if (time < c.appearAt) {
				continue;
			}
			if (!c.placed) {
				c.placed = true;
			}
			c.x += (c.tx - c.x) * Math.min(1f, dt * 12f);
			c.y += (c.ty - c.y) * Math.min(1f, dt * 12f);
			float flip = c.faceUp ? Math.min(1f, Math.max(0f, (time - c.flipAt + 0.15f) / 0.3f)) : 0f;
			float sx = c.faceUp ? Math.abs(1f - 2f * flip) : 1f;
			boolean showFace = c.faceUp && flip >= 0.5f;
			drawCard(g, c.x, c.y, sx, showFace ? c.code : null);
		}
		float last = lastCardTime();
		boolean settled = bj != null && "done".equals(str(bj, "phase")) && time >= last + 0.2f;
		// totals
		if (bj != null) {
			int dealerTotal = bj.get("dealerTotal").getAsInt();
			String dt2 = "done".equals(str(bj, "phase")) ? (time >= last ? String.valueOf(dealerTotal) : "") : String.valueOf(dealerTotal);
			if (!dt2.isEmpty()) {
				badge(g, tx + tw / 2f - 70, y + 14, dt2, 0xFF1A1E25, TEXT);
			}
			JsonArray hands = bj.getAsJsonArray("hands");
			int n = hands.size();
			int active = bj.get("active").getAsInt();
			for (int i = 0; i < n; i++) {
				JsonObject hnd = hands.get(i).getAsJsonObject();
				float hx = handX(i, n);
				float hy = y + th - 50;
				boolean isActive = "player".equals(str(bj, "phase")) && i == active;
				if (isActive && n > 1) {
					GuiDraw.roundedOutline(g, hx - 6, hy - 6, 70, 56, 6, alpha(GOLD, 0.6f + 0.4f * (float) Math.sin(time * 6f)), 1.2f);
				}
				String total = (hnd.get("soft").getAsBoolean() && hnd.get("total").getAsInt() < 21 ? "soft " : "") + hnd.get("total").getAsInt();
				badge(g, hx, hy - 17, total, 0xFF1A1E25, TEXT);
				float resultX = hx + width(total) * 0.85f + 14;
				String betTxt = fmt(hnd.get("bet").getAsLong()) + (hnd.get("doubled").getAsBoolean() ? " x2" : "");
				drawChip(g, hx + 4, hy + 50, 5, GOLD);
				text(g, betTxt, hx + 12, hy + 47, GOLD, 0.75f);
				if (settled && !hnd.get("result").isJsonNull()) {
					String res = str(hnd, "result");
					int col = switch (res) {
						case "win", "blackjack" -> GREEN;
						case "push" -> MUTED;
						default -> RED;
					};
					String label = switch (res) {
						case "blackjack" -> "BLACKJACK +" + fmt(hnd.get("payout").getAsLong() - hnd.get("bet").getAsLong());
						case "win" -> "WIN +" + fmt(hnd.get("bet").getAsLong());
						case "push" -> "PUSH";
						case "bust" -> "BUST";
						default -> "LOSE";
					};
					float pop = Math.min(1f, (time - last - 0.2f) / 0.3f);
					badge(g, resultX, hy - 17, label, alpha(0xFF0B0D10, pop), alpha(col, pop));
				}
			}
			if (settled && !str(bj, "message").isEmpty()) {
				String msg = str(bj, "message");
				text(g, msg, tx + tw / 2f - width(msg) / 2f, y + 64, alpha(TEXT, 0.9f), 1f);
			}
			long insPay = bj.has("insurancePayout") ? bj.get("insurancePayout").getAsLong() : 0;
			if (settled && insPay > 0) {
				String ins = "Insurance pays " + fmt(insPay);
				text(g, ins, tx + tw / 2f - width(ins) * 0.75f / 2f, y + 76, GREEN, 0.75f);
			}
		}

		// controls
		String phase = bj == null ? "none" : str(bj, "phase");
		boolean animating = time < last + 0.1f;
		int cy = y + th + 8;
		if (bj == null || "done".equals(phase)) {
			if (!animating) {
				text(g, "BET", tx + 2, cy, MUTED, 0.75f);
				betField(g, mx, my, 1, tx + 2, cy + 9, 170);
				bigButton(g, mx, my, tx + 184, cy + 9, 110, 24, "DEAL", !busy && balance > 0, this::deal);
			}
		} else if ("insurance".equals(phase)) {
			if (!animating) {
				text(g, "Dealer shows an Ace. Insurance?", tx + 2, cy, TEXT, 1f);
				bigButton(g, mx, my, tx + 2, cy + 14, 130, 24, "INSURE (" + fmt(insuranceCost()) + ")", !busy, () -> act("insurance"));
				bigButton(g, mx, my, tx + 138, cy + 14, 90, 24, "NO", !busy, () -> act("noinsurance"));
			}
		} else if (!animating) {
			float bx = tx + 2;
			bx = smallAction(g, mx, my, bx, cy + 10, "HIT", true, () -> act("hit"));
			bx = smallAction(g, mx, my, bx, cy + 10, "STAND", true, () -> act("stand"));
			bx = smallAction(g, mx, my, bx, cy + 10, "DOUBLE", bj.get("canDouble").getAsBoolean(), () -> act("double"));
			smallAction(g, mx, my, bx, cy + 10, "SPLIT", bj.get("canSplit").getAsBoolean(), () -> act("split"));
		}
	}

	private float smallAction(GuiGraphicsExtractor g, int mx, int my, float x, float y, String label, boolean on, Runnable action) {
		float w = Math.max(56, width(label) + 20);
		bigButton(g, mx, my, x, y, w, 22, label, on && !busy, action);
		return x + w + 6;
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
		int tx = left + 12;
		int tw = W - 24;
		float spacing = 96;
		float total = (n - 1) * spacing;
		return tx + tw / 2f - total / 2f + i * spacing - 30;
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
		int tx = left + 12;
		int tw = W - 24;
		int y = top + 40;
		int th = H - 40 - 20 - 54;
		List<DCard> unused = new ArrayList<>(cards);
		List<DCard> next = new ArrayList<>();
		float stagger = Math.max(time, lastCardTime() - 0.35f);
		float shoeX = tx + tw - 40;
		float shoeY = y + 4;
		// dealer
		JsonArray dealer = view.getAsJsonArray("dealer");
		boolean done = "done".equals(str(view, "phase"));
		for (int i = 0; i < dealer.size(); i++) {
			JsonElement e = dealer.get(i);
			String code = e.isJsonNull() ? "?" : e.getAsString();
			float cx = tx + tw / 2f - 30 + i * 18;
			float cy = y + 12;
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
				c.ty = y + th - 50 - i * 2;
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
		GuiDraw.rounded(g, 0.5f, 1.5f, w, h, 3, 0x60000000);
		if (code == null) {
			drawCardBackLocal(g, w, h);
		} else {
			GuiDraw.rounded(g, 0, 0, w, h, 3, 0xFFFAFAF7);
			String rank = code.substring(0, code.length() - 1);
			char suit = code.charAt(code.length() - 1);
			String suitGlyph = switch (suit) {
				case 'H' -> "♥";
				case 'D' -> "♦";
				case 'C' -> "♣";
				default -> "♠";
			};
			int col = suit == 'H' || suit == 'D' ? CARD_RED : 0xFF16181D;
			text(g, rank, 3, 3, col, 0.85f);
			glyph(g, suitGlyph, 3, 11, col, 0.85f);
			glyph(g, suitGlyph, w / 2f - glyphWidth(suitGlyph) * 1.6f / 2f, h / 2f - 6, col, 1.6f);
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
		float cx = left + 82;
		float cy = y + 82;
		float r = 66;
		advanceWheel(dt);
		drawWheel(g, cx, cy, r);
		// result
		if (!rolling && rouletteNumber >= 0 && time - rouletteDoneAt < 6f) {
			int col = rouletteNumber == 0 ? 0xFF16A34A : isRed(rouletteNumber) ? RED : 0xFF16181D;
			float pop = Math.min(1f, (time - rouletteDoneAt) / 0.3f);
			GuiDraw.circle(g, cx, cy, 14 * easeOutBack(pop), col);
			String n = String.valueOf(rouletteNumber);
			text(g, n, cx - width(n) * 1.3f / 2f, cy - 6, 0xFFFFFFFF, 1.3f);
			if (rouletteWin > 0) {
				String w = "+" + fmt(rouletteWin);
				text(g, w, cx - width(w) / 2f, y + 160, alpha(GREEN, pop), 1f);
			}
		}
		// history
		float hx = left + 16;
		float hy = y + 172;
		text(g, "LAST", hx, hy - 1, MUTED, 0.6f);
		hx += 20;
		for (String s : rouletteHistory) {
			int n = Integer.parseInt(s);
			int col = n == 0 ? 0xFF16A34A : isRed(n) ? RED : 0xFF2A2F38;
			GuiDraw.rounded(g, hx, hy - 3, 12, 10, 3, col);
			text(g, s, hx + 6 - width(s) * 0.6f / 2f, hy, 0xFFFFFFFF, 0.6f);
			hx += 14;
		}

		// board
		float bx = left + 170;
		float by = y + 6;
		float cw = 18;
		float ch = 20;
		boolean locked = rolling || busy;
		// zero
		boardCell(g, mx, my, bx, by, cw, ch * 3, "0", 0xFF15803D, "straight:0", locked);
		for (int c = 0; c < 12; c++) {
			for (int row = 0; row < 3; row++) {
				int num = c * 3 + (3 - row);
				int col = isRed(num) ? 0xFFB91C1C : 0xFF16181D;
				boardCell(g, mx, my, bx + cw + c * cw, by + row * ch, cw, ch, String.valueOf(num), col, "straight:" + num, locked);
			}
		}
		for (int row = 0; row < 3; row++) {
			boardCell(g, mx, my, bx + cw * 13, by + row * ch, cw + 4, ch, "2:1", 0xFF14532D, "column:" + (3 - row), locked);
		}
		for (int d = 0; d < 3; d++) {
			boardCell(g, mx, my, bx + cw + d * cw * 4, by + ch * 3, cw * 4, ch, (d == 0 ? "1st" : d == 1 ? "2nd" : "3rd") + " 12", 0xFF14532D, "dozen:" + (d + 1), locked);
		}
		String[][] outs = {{"1-18", "low"}, {"EVEN", "even"}, {"RED", "red"}, {"BLACK", "black"}, {"ODD", "odd"}, {"19-36", "high"}};
		for (int o = 0; o < 6; o++) {
			int col = o == 2 ? 0xFFB91C1C : o == 3 ? 0xFF16181D : 0xFF14532D;
			boardCell(g, mx, my, bx + cw + o * cw * 2, by + ch * 4, cw * 2, ch, outs[o][0], col, outs[o][1] + ":0", locked);
		}
		// chip rack
		float ry = by + ch * 5 + 10;
		text(g, "CHIP", bx, ry + 5, MUTED, 0.75f);
		for (int i = 0; i < CHIPS.length; i++) {
			float x = bx + 26 + i * 26;
			boolean sel = i == chip;
			float lift = sel ? -3 : 0;
			drawChip(g, x + 10, ry + 8 + lift, sel ? 10 : 9, CHIP_COLORS[i]);
			String label = shortAmount(CHIPS[i]);
			text(g, label, x + 10 - width(label) * 0.6f / 2f, ry + 5.5f + lift, i == 0 || i == 4 ? 0xFF111111 : 0xFFFFFFFF, 0.6f);
			final int idx = i;
			hit(x, ry - 2, 22, 20, () -> chip = idx);
		}
		long total = 0;
		for (long v : bets.values()) {
			total += v;
		}
		text(g, "Total bet " + fmt(total), bx, ry + 28, TEXT, 0.85f);
		float ay = ry + 42;
		float ax = bx;
		ax = smallButton(g, mx, my, ax, ay, "CLEAR", !locked && !bets.isEmpty(), bets::clear);
		ax = smallButton(g, mx, my, ax, ay, "REBET", !locked && !lastBets.isEmpty(), () -> {
			bets.clear();
			bets.putAll(lastBets);
		});
		ax = smallButton(g, mx, my, ax, ay, "2x", !locked && !bets.isEmpty(), () -> bets.replaceAll((k, v) -> v * 2));
		bigButton(g, mx, my, ax, ay, left + W - 16 - ax, 22, rolling ? "NO MORE BETS" : "SPIN", !locked && !bets.isEmpty(), this::spinRoulette);
		text(g, "Left click: add chip   Right click: remove", bx, ay + 28, 0xFF5B6472, 0.6f);
	}

	private void boardCell(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h, String label, int color, String key, boolean locked) {
		boolean hot = !locked && hovered(mx, my, x, y, w, h);
		boolean winner = !rolling && rouletteNumber >= 0 && time - rouletteDoneAt < 6f && wins(key, rouletteNumber);
		int fill = hot ? mixColor(color, 0xFFFFFFFF, 0.18f) : color;
		if (winner) {
			fill = mixColor(color, GOLD, 0.35f + 0.25f * (float) Math.sin(time * 8f));
		}
		GuiDraw.fill(g, x, y, w, h, fill);
		GuiDraw.border(g, x, y, w, h, 0x60FFFFFF, 0.5f);
		text(g, label, x + w / 2f - width(label) * 0.7f / 2f, y + h / 2f - 3, 0xFFFFFFFF, 0.7f);
		Long amount = bets.get(key);
		if (amount != null && amount > 0) {
			int ci = 0;
			for (int i = 0; i < CHIPS.length; i++) {
				if (amount >= CHIPS[i]) {
					ci = i;
				}
			}
			drawChip(g, x + w / 2f, y + h / 2f, 6.5f, CHIP_COLORS[ci]);
			String s = shortAmount(amount);
			text(g, s, x + w / 2f - width(s) * 0.5f / 2f, y + h / 2f - 2, ci == 0 || ci == 4 ? 0xFF111111 : 0xFFFFFFFF, 0.5f);
		}
		hits.add(new Hit(x, y, w, h, () -> {
			if (locked) {
				return;
			}
			bets.merge(key, CHIPS[chip], Long::sum);
		}, () -> {
			if (locked) {
				return;
			}
			Long now = bets.get(key);
			if (now != null) {
				long left = now - CHIPS[chip];
				if (left <= 0) {
					bets.remove(key);
				} else {
					bets.put(key, left);
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
				burstCoins(left + 82, top + 122, rouletteWin >= 10L * Math.max(1, sum(lastBets)) ? 50 : 18);
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
			text(g, s, -width(s) * 0.45f / 2f, -2f, 0xFFFFFFFF, 0.45f);
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
		float x = left + 18;
		float w = 190;
		title(g, "Send chips", x, y + 6);
		text(g, "PLAYER", x, y + 26, MUTED, 0.75f);
		field(g, mx, my, 2, x, y + 35, w, "Minecraft name");
		text(g, "AMOUNT", x, y + 62, MUTED, 0.75f);
		field(g, mx, my, 3, x, y + 71, w, "0");
		bigButton(g, mx, my, x, y + 98, w, 24, "SEND", !busy && !fields[2].isBlank() && parse(fields[3]) > 0, this::sendChips);
		text(g, "They get them right away, even if they", x, y + 130, 0xFF5B6472, 0.65f);
		text(g, "never opened the casino (new players start at 1000).", x, y + 139, 0xFF5B6472, 0.65f);
		float iy = y + 158;
		text(g, "INBOX", x, iy, MUTED, 0.75f);
		iy += 11;
		if (inbox.isEmpty()) {
			text(g, "Nothing yet.", x, iy, 0xFF5B6472, 0.75f);
		}
		for (int i = 0; i < Math.min(5, inbox.size()); i++) {
			text(g, inbox.get(i), x, iy + i * 10, TEXT, 0.75f);
		}

		float lx = left + 236;
		float lw = left + W - 18 - lx;
		title(g, "Richest players", lx, y + 6);
		GuiDraw.rounded(g, lx, y + 24, lw, 170, 8, 0xFF161A20);
		for (int i = 0; i < top10.size(); i++) {
			String[] row = top10.get(i);
			float ry = y + 32 + i * 16;
			int medal = i == 0 ? GOLD : i == 1 ? 0xFFC0C7D1 : i == 2 ? 0xFFCD7F32 : MUTED;
			text(g, (i + 1) + ".", lx + 8, ry, medal, 0.85f);
			text(g, row[0], lx + 24, ry, row[0].equalsIgnoreCase(name) ? GOLD : TEXT, 0.85f);
			text(g, row[1], lx + lw - 8 - width(row[1]) * 0.85f, ry, GOLD, 0.85f);
		}
		smallButton(g, mx, my, lx, y + 200, "REFRESH", !busy, this::refreshTop);
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
		float by = y + 24;
		String[] labels = {"1/2", "2x", "+100", "MAX"};
		float bw = (w - 3 * 4) / 4f;
		for (int i = 0; i < labels.length; i++) {
			final int k = i;
			float bx = x + i * (bw + 4);
			boolean hot = hovered(mx, my, bx, by, bw, 16);
			GuiDraw.rounded(g, bx, by, bw, 16, 5, hot ? BTN_HOT : BTN);
			text(g, labels[i], bx + bw / 2f - width(labels[i]) * 0.75f / 2f, by + 5, TEXT, 0.75f);
			hit(bx, by, bw, 16, () -> {
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
		boolean hot = hovered(mx, my, x, y, w, 20);
		GuiDraw.rounded(g, x, y, w, 20, 6, focused ? GOLD_DARK : hot ? 0xFF3A4250 : PANEL_LINE);
		GuiDraw.rounded(g, x + 1, y + 1, w - 2, 18, 5, 0xFF0D0F13);
		String value = fields[idx];
		boolean empty = value.isEmpty();
		String shown = empty ? placeholder : (idx == 2 ? value : grouped(value));
		boolean caret = focused && (int) (time * 2) % 2 == 0;
		text(g, shown + (caret ? "_" : ""), x + 7, y + 6, empty ? 0xFF5B6472 : TEXT, 1f);
		hit(x, y, w, 20, () -> focus = idx);
	}

	private void bigButton(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h, String label, boolean on, Runnable action) {
		boolean hot = on && hovered(mx, my, x, y, w, h);
		int top = on ? (hot ? 0xFFFFD873 : GOLD) : 0xFF3A3F48;
		int bottom = on ? (hot ? 0xFFE0A93A : GOLD_DARK) : 0xFF2A2E35;
		GuiDraw.rounded(g, x, y + 1.5f, w, h, 7, 0x80000000);
		GuiDraw.rounded(g, x, y, w, h, 7, bottom);
		GuiDraw.fillGradient(g, x + 2, y + 1, w - 4, h / 2f, top, bottom);
		int tc = on ? 0xFF1A1205 : 0xFF7A808A;
		text(g, label, x + w / 2f - width(label) / 2f, y + h / 2f - 3.5f, tc, 1f);
		if (on) {
			hit(x, y, w, h, action);
		}
	}

	private float smallButton(GuiGraphicsExtractor g, int mx, int my, float x, float y, String label, boolean on, Runnable action) {
		float w = width(label) * 0.85f + 14;
		boolean hot = on && hovered(mx, my, x, y, w, 22);
		GuiDraw.rounded(g, x, y, w, 22, 6, on ? (hot ? BTN_HOT : BTN) : 0xFF16191E);
		text(g, label, x + 7, y + 8, on ? TEXT : 0xFF5B6472, 0.85f);
		if (on) {
			hit(x, y, w, 22, action);
		}
		return x + w + 5;
	}

	private void badge(GuiGraphicsExtractor g, float x, float y, String label, int bg, int fg) {
		float w = width(label) * 0.85f + 10;
		GuiDraw.rounded(g, x, y, w, 13, 6, bg);
		text(g, label, x + 5, y + 3.5f, fg, 0.85f);
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
		double mx = event.x();
		double my = event.y();
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
		} else if (key == InputConstants.KEY_SPACE) {
			if (tab == 0 && !spinning && !busy && balance > 0) {
				spin();
				return true;
			}
		}
		return super.keyPressed(event);
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

	/** The Hyprland click GUI's font. */
	private void text(GuiGraphicsExtractor g, String s, float x, float y, int color, float scale) {
		GuiDraw.text(g, font, dev.stray.client.ui.MenuFont.hypr(s), x, y, scale, color, false);
	}

	/** Minecraft's font, for card suits and the slot 7 (the menu font has no such glyphs). */
	private void glyph(GuiGraphicsExtractor g, String s, float x, float y, int color, float scale) {
		GuiDraw.text(g, font, s, x, y, scale, color, false);
	}

	private void title(GuiGraphicsExtractor g, String s, float x, float y) {
		text(g, s, x, y, TEXT, 1.2f);
	}

	private float width(String s) {
		return font.width(dev.stray.client.ui.MenuFont.hypr(s));
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
