package dev.stray.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.cs2.Cs2Knives;
import dev.stray.client.cs2.Cs2Library;
import dev.stray.client.cs2.GripDefaults;
import dev.stray.client.cs2.render.SoftwareRender;
import dev.stray.client.cs2.s2.S2Model;
import dev.stray.client.cs2.skin.Composer;
import dev.stray.client.cs2.skin.KnifeMaterial;
import dev.stray.client.cs2.skin.PaintKit;
import dev.stray.client.cs2.skin.Tex;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Picks the CS2 knife and finish. Three columns: knives (with search), finishes or grip
 * sliders (tabs), and a live preview with seed, float and options. Selections, tabs, switches
 * and the preview ease into place; finish tiles fade in when the knife changes.
 */
public final class Cs2KnifeScreen extends Screen {
	private static final int MAX_W = 548;
	private static final int MAX_H = 332;
	private static final int PAD = 12;
	private static final int LIST_W = 126;
	private static final int SIDE_W = 196;
	private static final int GAP = 10;
	private static final int ROW_H = 16;
	private static final int TILE_W = 76;
	private static final int TILE_H = 70;
	private static final int TILE_GAP = 6;
	private static final int PREVIEW_H = 128;
	private static final int HEADER_H = 40;
	private static final String[] TABS = {"Finishes", "Grip", "View", "Blade"};
	private static final String[] STYLES = {"smoke", "stars", "portal", "galaxy"};
	private static final String[] STYLE_LABELS = {"Smoke", "Stars", "Portal", "Galaxy"};
	private static final int[] SWATCHES = {0xB46CFF, 0x4FD6EA, 0xFF4F8B, 0xFF9F1C, 0xFFD23F, 0x3DDC84, 0x4F7CFF, 0xF2F4F7};
	private static final String[] APPLY = {"swords", "held", "empty"};
	private static final String[] APPLY_LABELS = {"Swords", "Any item", "Empty hand"};
	private static final String[] GRIP_NAMES = {"Edge / spine", "Sideways", "Along handle", "Pitch", "Yaw", "Roll", "Size"};
	private static final float[][] GRIP_RANGES = {{-6f, 6f}, {-6f, 6f}, {-6f, 6f}, {-180f, 180f}, {-180f, 180f}, {-180f, 180f}, {0.4f, 2.0f}};
	private static final Identifier[] PREVIEW = {Stray.id("cs2/preview_a"), Stray.id("cs2/preview_b")};

	private static final ExecutorService WORKER = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Stray CS2 preview");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private final Screen parent;
	private int W;
	private int H;
	private int left;
	private int top;
	private EditBox knifeSearch;
	private EditBox finishSearch;
	private EditBox seedBox;
	private int tab;

	// scrolling (target + eased)
	private float knifeScroll;
	private float knifeScrollT;
	private float finishScroll;
	private float finishScrollT;

	// animation state
	private long opened;
	private long lastFrame;
	private float dt;
	private float selY = -1f;
	private float tabT;
	private float applyT = -1f;
	private float bladeT;
	private float styleT = -1f;
	private float animT;
	private float perSwordT;
	private long tilesShown;
	private final Map<String, Float> hover = new HashMap<>();

	// dragging
	private int dragging = -1;
	private boolean bindListen; // 0..6 grip slider, 10 = float slider

	// previews
	private final Map<String, Identifier> tiles = new ConcurrentHashMap<>();
	private final Map<String, NativeImage> tileImages = new ConcurrentHashMap<>();
	private final Set<String> tileRequested = ConcurrentHashMap.newKeySet();
	private String previewKey;
	private volatile int[] previewPixels;
	private volatile String previewReadyKey;
	private int previewSlot = -1;
	private boolean previewHasOld;
	private long previewSwap;
	private long previewDue;

	public Cs2KnifeScreen(Screen parent) {
		super(Component.literal("CS2 knife"));
		this.parent = parent;
	}

	// ---------------------------------------------------------------- setup

	@Override
	protected void init() {
		W = Math.min(MAX_W, width - 16);
		H = Math.min(MAX_H, height - 16);
		left = (width - W) / 2;
		top = (height - H) / 2;
		if (opened == 0) {
			opened = System.currentTimeMillis();
			tilesShown = opened;
		}
		StrayConfig c = StrayConfig.get();
		String knifeQuery = knifeSearch == null ? "" : knifeSearch.getValue();
		String finishQuery = finishSearch == null ? "" : finishSearch.getValue();
		knifeSearch = box(listX() + 18, bodyY() + 5, LIST_W - 22, "Search knives", knifeQuery);
		knifeSearch.setResponder(v -> knifeScroll = 0f);
		finishSearch = box(midX() + 18, panelTop() + 5, midW() - 22, "Search finishes", finishQuery);
		finishSearch.setResponder(v -> {
			finishScroll = 0f;
			tilesShown = System.currentTimeMillis();
		});
		finishSearch.visible = tab == 0;
		seedBox = box(sideX() + 34, seedY() + 5, 50, "0-1000", String.valueOf(c.cs2Seed));
		seedBox.setResponder(v -> {
			try {
				c.cs2Seed = v.isEmpty() ? 0 : Math.max(0, Math.min(1000, Integer.parseInt(v.trim())));
			} catch (NumberFormatException ignored) {
			}
			schedulePreview();
		});
		schedulePreview();
	}

	private EditBox box(int x, int y, int w, String hint, String value) {
		EditBox box = new EditBox(font, x, y, w, 10, Component.literal(hint));
		box.setBordered(false);
		box.setMaxLength(64);
		box.setHint(Component.literal(hint).withColor(dim()));
		box.setTextColor(ink());
		box.setValue(value);
		addRenderableWidget(box);
		return box;
	}

	// ---------------------------------------------------------------- style

	private static boolean hypr() {
		return StrayConfig.get().guiStyleHypr();
	}

	private static int ink() {
		return hypr() ? MaterialPalette.onSurface : Theme.TEXT;
	}

	private static int dim() {
		return hypr() ? MaterialPalette.onSurfaceVariant : Theme.MUTED;
	}

	private static int accent() {
		return hypr() ? MaterialPalette.primary : Theme.ACCENT;
	}

	private static int onAccent() {
		return hypr() ? MaterialPalette.onPrimary : 0xFF0B0E14;
	}

	private static int card() {
		return hypr() ? MaterialPalette.withAlpha(MaterialPalette.surfaceHigh, 0xC8) : Theme.CARD;
	}

	private static int cardHover() {
		return hypr() ? MaterialPalette.withAlpha(MaterialPalette.surfaceHighest, 0xD8) : Theme.CARD_HOVER;
	}

	private static int line() {
		return hypr() ? MaterialPalette.outlineVariant : Theme.LINE;
	}

	private static int track() {
		return hypr() ? MaterialPalette.surfaceHighest : Theme.TRACK;
	}

	private void text(GuiGraphicsExtractor g, String s, float x, float y, int color) {
		if (hypr()) {
			GuiDraw.text(g, font, MenuFont.hypr(s), x, y, 1f, color, false);
		} else {
			GuiDraw.menu(g, font, s, x, y, color);
		}
	}

	private void small(GuiGraphicsExtractor g, String s, float x, float y, int color) {
		if (hypr()) {
			GuiDraw.text(g, font, MenuFont.hyprSmall(s), x, y, 1f, color, false);
		} else {
			GuiDraw.small(g, font, s, x, y, color);
		}
	}

	private void title(GuiGraphicsExtractor g, String s, float x, float y, int color) {
		if (hypr()) {
			GuiDraw.text(g, font, MenuFont.hyprTitle(s), x, y, 1f, color, false);
		} else {
			GuiDraw.title(g, font, s, x, y, color);
		}
	}

	/** Text top that centers a line in a box (all menu fonts are tuned to a 9px line box). */
	private static float mid(float boxY, float boxH) {
		return Math.round(GuiDraw.middle(boxY, boxH));
	}

	private int textW(String s) {
		return hypr() ? font.width(MenuFont.hypr(s)) : GuiDraw.menuWidth(font, s);
	}

	private int smallW(String s) {
		return hypr() ? font.width(MenuFont.hyprSmall(s)) : GuiDraw.smallWidth(font, s);
	}

	private String fit(String s, float max, boolean small) {
		if ((small ? smallW(s) : textW(s)) <= max) {
			return s;
		}
		String out = s;
		while (out.length() > 1 && (small ? smallW(out + "…") : textW(out + "…")) > max) {
			out = out.substring(0, out.length() - 1);
		}
		return out + "…";
	}

	// ---------------------------------------------------------------- layout

	private int bodyY() {
		return top + HEADER_H;
	}

	private int bodyH() {
		return top + H - PAD - bodyY();
	}

	private int listX() {
		return left + PAD;
	}

	private int listTop() {
		return bodyY() + 24;
	}

	private int listH() {
		return bodyY() + bodyH() - listTop();
	}

	private int midX() {
		return listX() + LIST_W + GAP;
	}

	private int midW() {
		return sideX() - GAP - midX();
	}

	private int panelTop() {
		return bodyY() + 24;
	}

	private int gridTop() {
		return bodyY() + 48;
	}

	private int gridH() {
		return bodyY() + bodyH() - gridTop();
	}

	private int sideX() {
		return left + W - PAD - SIDE_W;
	}

	private int seedY() {
		return bodyY() + PREVIEW_H + 34;
	}

	private int floatY() {
		return seedY() + 24;
	}

	private int applyY() {
		return floatY() + 30;
	}

	private int switchY() {
		return applyY() + 26;
	}

	private int columns() {
		return Math.max(1, (midW() + TILE_GAP) / (TILE_W + TILE_GAP));
	}

	private int tileW() {
		int cols = columns();
		return (midW() - (cols - 1) * TILE_GAP) / cols;
	}

	// ---------------------------------------------------------------- data

	private List<Cs2Library.Knife> knives(Cs2Library lib) {
		String q = knifeSearch == null ? "" : knifeSearch.getValue().trim().toLowerCase(Locale.ROOT);
		List<Cs2Library.Knife> out = new ArrayList<>();
		for (Cs2Library.Knife k : lib.knives().values()) {
			if (q.isEmpty() || k.name().toLowerCase(Locale.ROOT).contains(q)) {
				out.add(k);
			}
		}
		return out;
	}

	/** Finishes for the selected knife, "Vanilla" first. */
	private List<Cs2Library.Finish> finishes(Cs2Library lib) {
		Cs2Library.Knife k = lib.knives().get(StrayConfig.get().cs2Knife);
		List<Cs2Library.Finish> out = new ArrayList<>();
		if (k == null) {
			return out;
		}
		String q = finishSearch == null ? "" : finishSearch.getValue().trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty() || "vanilla".contains(q)) {
			out.add(new Cs2Library.Finish("", "Vanilla"));
		}
		for (Cs2Library.Finish f : k.finishes()) {
			if (f.kit().isEmpty()) {
				continue;
			}
			if (q.isEmpty() || f.name().toLowerCase(Locale.ROOT).contains(q)) {
				out.add(f);
			}
		}
		return out;
	}

	private String finishName(Cs2Library lib) {
		StrayConfig c = StrayConfig.get();
		Cs2Library.Knife k = lib.knives().get(c.cs2Knife);
		if (k != null) {
			for (Cs2Library.Finish f : k.finishes()) {
				if (f.kit().equals(c.cs2Finish)) {
					return f.name();
				}
			}
		}
		return "Vanilla";
	}

	private static int indexOf(String[] values, String v) {
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(v)) {
				return i;
			}
		}
		return 0;
	}

	private float[] grip() {
		float[] g = StrayConfig.get().cs2Grip.get(StrayConfig.get().cs2Knife + "#hand");
		return g == null || g.length < 7 ? GripDefaults.hand(StrayConfig.get().cs2Knife) : g.clone();
	}

	private void storeGrip(float[] g) {
		StrayConfig.get().cs2Grip.put(StrayConfig.get().cs2Knife + "#hand", g);
	}

	// ---------------------------------------------------------------- animation helpers

	private float ease(float current, float target, float speed) {
		if (Math.abs(target - current) < 0.002f) {
			return target;
		}
		return current + (target - current) * (1f - (float) Math.exp(-speed * dt));
	}

	private float hoverAmount(String key, boolean on) {
		float v = hover.getOrDefault(key, 0f);
		v = ease(v, on ? 1f : 0f, 18f);
		hover.put(key, v);
		return v;
	}

	private static float smooth(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3f - 2f * t);
	}

	private static float outCubic(float t) {
		t = Math.max(0f, Math.min(1f, t));
		float u = 1f - t;
		return 1f - u * u * u;
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Theme.refresh();
		MaterialPalette.refresh();
		long now = System.currentTimeMillis();
		dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;

		float open = outCubic((now - opened) / 220f);
		g.pose().pushMatrix();
		float cx = left + W / 2f;
		float cy = top + H / 2f;
		float s = 0.96f + 0.04f * open;
		g.pose().translate(cx, cy + (1f - open) * 8f);
		g.pose().scale(s, s);
		g.pose().translate(-cx, -cy);

		if (hypr()) {
			HyprMenu.window(g, left, top, W, H, 1f, open);
		} else {
			GuiDraw.panel(g, left, top, W, H, 10, Theme.WINDOW, Theme.LINE);
		}
		knifeScrollT = ease(knifeScrollT, knifeScroll, 16f);
		finishScrollT = ease(finishScrollT, finishScroll, 16f);
		tabT = ease(tabT, tab, 14f);

		Cs2Library lib = Cs2Knives.library();
		drawHeader(g, lib, mouseX, mouseY);
		if (lib == null) {
			drawLoading(g, now);
		} else {
			drawKnives(g, lib, mouseX, mouseY);
			drawMiddle(g, lib, mouseX, mouseY, now);
			drawSide(g, lib, mouseX, mouseY, now);
		}
		drawSearchBack(g, listX(), bodyY(), LIST_W, knifeSearch.isFocused());
		if (tab == 0 && Math.abs(tabT) < 0.05f) {
			drawSearchBack(g, midX(), panelTop(), midW(), finishSearch.isFocused());
		}
		GuiDraw.rounded(g, sideX() + 30, seedY(), 58, 18, 5, seedBox.isFocused() ? cardHover() : card());
		if (seedBox.isFocused()) {
			GuiDraw.roundedOutline(g, sideX() + 30, seedY(), 58, 18, 5, Theme.withAlpha(accent(), 160), 1f);
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.pose().popMatrix();
	}

	private void drawSearchBack(GuiGraphicsExtractor g, int x, int y, int w, boolean focused) {
		GuiDraw.rounded(g, x, y, w, 18, 9, focused ? cardHover() : card());
		if (focused) {
			GuiDraw.roundedOutline(g, x, y, w, 18, 9, Theme.withAlpha(accent(), 160), 1f);
		}
		// magnifier
		GuiDraw.roundedOutline(g, x + 6, y + 5, 6, 6, 3, dim(), 1f);
		GuiDraw.stroke(g, x + 11, y + 10, x + 13.5f, y + 12.5f, 1.2f, dim());
	}

	private void drawHeader(GuiGraphicsExtractor g, Cs2Library lib, int mouseX, int mouseY) {
		int x = left + PAD;
		title(g, "CS2 Knife", x, top + 10, ink());
		StrayConfig c = StrayConfig.get();
		String sub = "";
		if (lib != null) {
			Cs2Library.Knife k = lib.knives().get(c.cs2Knife);
			sub = k == null ? "" : k.name() + "  ·  " + finishName(lib);
			if (c.cs2PerSword) {
				String sword = Cs2Knives.activeSwordName();
				sub = (sword == null ? "Editing the default skin" : "Editing " + sword) + "  ·  " + sub;
			}
		}
		// inspect key chip: click, then press a key (or mouse button) to rebind
		String key = bindListen ? "Press a key…" : keyName(c.cs2InspectKey);
		int kw = Math.max(26, textW(key) + 16);
		int kx = left + W - PAD - 18 - 8 - kw;
		boolean khot = GuiDraw.hovered(mouseX, mouseY, kx, top + 11, kw, 18);
		float kh = hoverAmount("bind", khot || bindListen);
		GuiDraw.rounded(g, kx, top + 11, kw, 18, 9, bindListen ? Theme.withAlpha(accent(), 70) : Anim.mix(card(), cardHover(), kh));
		if (bindListen) {
			GuiDraw.roundedOutline(g, kx, top + 11, kw, 18, 9, accent(), 1f);
		}
		text(g, key, kx + kw / 2f - textW(key) / 2f, mid(top + 11, 18), bindListen ? accent() : Anim.mix(dim(), ink(), kh));
		String label = "Inspect";
		int lx = kx - 6 - textW(label);
		text(g, label, lx, mid(top + 11, 18), dim());

		String status = Cs2Knives.status();
		int right = lx - 10;
		if (!status.isEmpty()) {
			int sw = textW(status) + 16;
			int sx = right - sw;
			GuiDraw.rounded(g, sx, top + 11, sw, 18, 9, Theme.withAlpha(accent(), 40));
			text(g, status, sx + 8, mid(top + 11, 18), accent());
			right = sx - 10;
		}
		small(g, fit(sub, right - x, true), x, top + 25, dim());
		// close button
		int bx = left + W - PAD - 18;
		int by = top + 11;
		boolean hot = GuiDraw.hovered(mouseX, mouseY, bx, by, 18, 18);
		float h = hoverAmount("close", hot);
		GuiDraw.rounded(g, bx, by, 18, 18, 9, Anim.mix(card(), Theme.withAlpha(0xFFE5484D, 220), h));
		int col = Anim.mix(dim(), 0xFFFFFFFF, h);
		GuiDraw.stroke(g, bx + 6, by + 6, bx + 12, by + 12, 1.4f, col);
		GuiDraw.stroke(g, bx + 12, by + 6, bx + 6, by + 12, 1.4f, col);
		GuiDraw.fill(g, left + PAD, top + HEADER_H - 5, W - PAD * 2, 1, Theme.withAlpha(line(), 160));
	}

	private void drawLoading(GuiGraphicsExtractor g, long now) {
		float cx = left + W / 2f;
		float cy = top + H / 2f;
		for (int i = 0; i < 3; i++) {
			float phase = (float) Math.sin(now / 180.0 - i * 0.7);
			float r = 3f + 1.2f * Math.max(0f, phase);
			GuiDraw.circle(g, cx - 12 + i * 12, cy, r, Theme.withAlpha(accent(), 120 + (int) (100 * Math.max(0f, phase))));
		}
		String msg = Cs2Knives.status().isEmpty() ? "Loading knives…" : Cs2Knives.status();
		small(g, msg, cx - smallW(msg) / 2f, cy + 14, dim());
	}

	private void drawKnives(GuiGraphicsExtractor g, Cs2Library lib, int mouseX, int mouseY) {
		int x = listX();
		int y = listTop();
		int h = listH();
		List<Cs2Library.Knife> list = knives(lib);
		String selected = StrayConfig.get().cs2Knife;
		int selIndex = -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id().equals(selected)) {
				selIndex = i;
			}
		}
		GuiDraw.scissor(g, x, y, LIST_W, h);
		if (selIndex >= 0) {
			float target = selIndex * ROW_H;
			selY = selY < 0 ? target : ease(selY, target, 20f);
			float ry = y + selY - knifeScrollT;
			GuiDraw.rounded(g, x, ry, LIST_W, ROW_H - 2, 6, Theme.withAlpha(accent(), 56));
			GuiDraw.rounded(g, x, ry + 3, 3, ROW_H - 8, 1.5f, accent());
		}
		for (int i = 0; i < list.size(); i++) {
			Cs2Library.Knife k = list.get(i);
			float ry = y + i * ROW_H - knifeScrollT;
			if (ry + ROW_H < y || ry > y + h) {
				continue;
			}
			boolean on = i == selIndex;
			boolean hot = mouseY >= y && mouseY < y + h && GuiDraw.hovered(mouseX, mouseY, x, ry, LIST_W, ROW_H - 2);
			float hv = hoverAmount("k:" + k.id(), hot && !on);
			if (hv > 0.01f) {
				GuiDraw.rounded(g, x, ry, LIST_W, ROW_H - 2, 6, Anim.fade(cardHover(), hv));
			}
			float nudge = on ? 2f : hv * 2f;
			text(g, fit(k.name(), LIST_W - 14, false), x + 8 + nudge, mid(ry, ROW_H - 2), on ? ink() : Anim.mix(dim(), ink(), hv));
		}
		GuiDraw.disableScissor(g);
		if (list.isEmpty()) {
			small(g, "No knife matches", x + 8, y + 4, dim());
		}
		scrollbar(g, x + LIST_W + 3, y, h, list.size() * ROW_H, knifeScrollT);
	}

	private void scrollbar(GuiGraphicsExtractor g, float x, float y, float h, float content, float scroll) {
		if (content <= h) {
			return;
		}
		float bar = Math.max(16f, h * h / content);
		float by = y + (h - bar) * (scroll / (content - h));
		GuiDraw.rounded(g, x, by, 2, bar, 1, Theme.withAlpha(dim(), 90));
	}

	private void drawMiddle(GuiGraphicsExtractor g, Cs2Library lib, int mouseX, int mouseY, long now) {
		// tabs: a pill slides under the active one; panels slide and fade between them
		int tx = midX();
		int ty = bodyY();
		float tw = midW();
		float seg = tw / TABS.length;
		GuiDraw.rounded(g, tx, ty, tw, 18, 9, card());
		GuiDraw.rounded(g, tx + 2 + tabT * seg, ty + 2, seg - 4, 14, 7, accent());
		for (int i = 0; i < TABS.length; i++) {
			float on = Math.max(0f, 1f - Math.abs(tabT - i));
			boolean hot = GuiDraw.hovered(mouseX, mouseY, tx + seg * i, ty, seg, 18);
			float hv = hoverAmount("tab" + i, hot);
			int col = Anim.mix(Anim.mix(dim(), ink(), hv), onAccent(), on);
			text(g, TABS[i], tx + seg * i + seg / 2f - textW(TABS[i]) / 2f, mid(ty, 18), col);
		}
		for (int i = 0; i < TABS.length; i++) {
			float d = i - tabT;
			float a = smooth(1f - Math.abs(d));
			if (a <= 0.001f) {
				continue;
			}
			g.pose().pushMatrix();
			g.pose().translate(d * 24f, 0f);
			switch (i) {
				case 0 -> drawFinishes(g, lib, mouseX, mouseY, now, a);
				case 1 -> drawGrip(g, mouseX, mouseY, a);
				case 2 -> drawView(g, mouseX, mouseY, a);
				default -> drawBlade(g, mouseX, mouseY, a);
			}
			g.pose().popMatrix();
		}
	}

	private static final String[] VIEW_NAMES = {"Left / right", "Up / down", "Distance"};

	private float[] viewOffset() {
		float[] o = StrayConfig.get().cs2ViewOffset;
		if (o == null || o.length < 3) {
			o = new float[] {0f, 0f, 0f};
			StrayConfig.get().cs2ViewOffset = o;
		}
		return o;
	}

	private void drawView(GuiGraphicsExtractor g, int mouseX, int mouseY, float alpha) {
		int x = midX();
		int y = panelTop();
		int w = midW();
		small(g, "Moves your arm and knife together in first person.", x, y + 2, Anim.fade(dim(), alpha));
		float[] o = viewOffset();
		for (int i = 0; i < 3; i++) {
			float sy = y + 18 + i * 26;
			sliderRow(g, VIEW_NAMES[i], String.format(Locale.ROOT, "%+.2f", o[i]), x, sy, w, (o[i] + 0.6f) / 1.2f, 30 + i, mouseX, mouseY, alpha);
		}
		button(g, "viewreset", "Reset", x, y + 18 + 3 * 26 + 2, 70, mouseX, mouseY, false, alpha);
	}

	private void drawBlade(GuiGraphicsExtractor g, int mouseX, int mouseY, float alpha) {
		StrayConfig c = StrayConfig.get();
		int x = midX();
		int y = panelTop();
		int w = midW();
		boolean live = alpha > 0.5f;
		// enable
		bladeT = ease(bladeT, c.cs2BladeShader ? 1f : 0f, 16f);
		boolean hot = live && GuiDraw.hovered(mouseX, mouseY, x, y, w, 16);
		text(g, "Blade shader", x, mid(y + 2, 13), Anim.fade(ink(), alpha));
		drawSwitch(g, x + w - 26, y + 2, bladeT, hot, alpha);
		small(g, "Its own look, separate from the held item shader.", x, y + 20, Anim.fade(dim(), alpha));

		// style
		int sy = y + 38;
		text(g, "Style", x, mid(sy, 9), Anim.fade(dim(), alpha));
		int st = indexOf(STYLES, StrayConfig.normalizeHeldItemShaderStyle(c.cs2BladeShaderStyle));
		styleT = styleT < 0 ? st : ease(styleT, st, 16f);
		float seg = w / 4f;
		int by = sy + 12;
		GuiDraw.rounded(g, x, by, w, 18, 9, Anim.fade(card(), alpha));
		GuiDraw.rounded(g, x + 2 + styleT * seg, by + 2, seg - 4, 14, 7, Anim.fade(accent(), alpha));
		for (int i = 0; i < 4; i++) {
			float on = Math.max(0f, 1f - Math.abs(styleT - i));
			text(g, STYLE_LABELS[i], x + seg * i + seg / 2f - textW(STYLE_LABELS[i]) / 2f, mid(by, 18), Anim.fade(Anim.mix(dim(), onAccent(), on), alpha));
		}

		// color: swatches + hue bar
		int cy = by + 28;
		text(g, "Color", x, mid(cy, 9), Anim.fade(dim(), alpha));
		int rgb = c.cs2BladeShaderRgb & 0xFFFFFF;
		GuiDraw.rounded(g, x + w - 26, cy - 1, 26, 11, 5.5f, Anim.fade(0xFF000000 | rgb, alpha));
		int sw = 16;
		float gap = (w - SWATCHES.length * sw) / (float) (SWATCHES.length - 1);
		int swy = cy + 14;
		for (int i = 0; i < SWATCHES.length; i++) {
			float sx = x + i * (sw + gap);
			boolean h = live && GuiDraw.hovered(mouseX, mouseY, sx, swy, sw, sw);
			float hv = hoverAmount("sw" + i, h);
			boolean on = SWATCHES[i] == rgb;
			float grow = hv * 1.5f;
			GuiDraw.rounded(g, sx - grow, swy - grow, sw + grow * 2, sw + grow * 2, 8 + grow, Anim.fade(0xFF000000 | SWATCHES[i], alpha));
			if (on) {
				GuiDraw.roundedOutline(g, sx - 2.5f, swy - 2.5f, sw + 5, sw + 5, 10, Anim.fade(ink(), alpha), 1.2f);
			}
		}
		int hy = swy + sw + 8;
		GuiDraw.scissor(g, x, hy, w, 8);
		GuiDraw.hueBar(g, x, hy, w, 8);
		GuiDraw.disableScissor(g);
		float hue = hue(rgb);
		float hx = x + w * hue;
		GuiDraw.circle(g, hx, hy + 4, 5.5f, Anim.fade(0xFFFFFFFF, alpha));
		GuiDraw.circle(g, hx, hy + 4, 4f, Anim.fade(0xFF000000 | rgb, alpha));

		// strength / detail
		int ly = hy + 20;
		sliderRow(g, "Strength", String.format(Locale.ROOT, "%.0f%%", c.cs2BladeShaderFill * 100f), x, ly, w, (c.cs2BladeShaderFill - 0.08f) / (0.85f - 0.08f), 21, mouseX, mouseY, alpha);
		sliderRow(g, "Detail", String.format(Locale.ROOT, "%.2f", c.cs2BladeShaderSmoke), x, ly + 28, w, (c.cs2BladeShaderSmoke - 0.10f) / (1.50f - 0.10f), 22, mouseX, mouseY, alpha);
	}

	private void sliderRow(GuiGraphicsExtractor g, String label, String value, float x, float y, float w, float k, int id, int mouseX, int mouseY, float alpha) {
		boolean hot = alpha > 0.5f && GuiDraw.hovered(mouseX, mouseY, x, y, w, 22);
		float hv = hoverAmount("sl" + id, hot || dragging == id);
		text(g, label, x, mid(y, 10), Anim.fade(ink(), alpha));
		small(g, value, x + w - smallW(value), mid(y, 10), Anim.fade(Anim.mix(dim(), accent(), hv), alpha));
		slider(g, x, y + 13, w, k, hv, alpha);
	}

	private void drawSwitch(GuiGraphicsExtractor g, float sx, float y, float k, boolean hot, float alpha) {
		float sw = 26;
		if (hypr()) {
			HyprMenu.m3Switch(g, sx, y, sw, 13, k, hot, alpha);
		} else {
			GuiDraw.rounded(g, sx, y, sw, 13, 6.5f, Anim.fade(Anim.mix(track(), accent(), k), alpha));
			GuiDraw.circle(g, sx + 6.5f + k * (sw - 13), y + 6.5f, 4.5f, Anim.fade(Anim.mix(dim(), 0xFFFFFFFF, k), alpha));
		}
	}

	private static float hue(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		if (d < 1e-5f) {
			return 0f;
		}
		float h;
		if (max == r) {
			h = ((g - b) / d) % 6f;
		} else if (max == g) {
			h = (b - r) / d + 2f;
		} else {
			h = (r - g) / d + 4f;
		}
		h /= 6f;
		return h < 0f ? h + 1f : h;
	}

	private static int fromHue(float h) {
		h = (h % 1f + 1f) % 1f * 6f;
		int i = (int) Math.floor(h);
		float f = h - i;
		float q = 1f - f;
		float r;
		float g;
		float b;
		switch (i) {
			case 0 -> { r = 1; g = f; b = 0; }
			case 1 -> { r = q; g = 1; b = 0; }
			case 2 -> { r = 0; g = 1; b = f; }
			case 3 -> { r = 0; g = q; b = 1; }
			case 4 -> { r = f; g = 0; b = 1; }
			default -> { r = 1; g = 0; b = q; }
		}
		return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}

	private void drawFinishes(GuiGraphicsExtractor g, Cs2Library lib, int mouseX, int mouseY, long now, float alpha) {
		int x = midX();
		int y = gridTop();
		int h = gridH();
		int cols = columns();
		int tw = tileW();
		String knife = StrayConfig.get().cs2Knife;
		String chosen = StrayConfig.get().cs2Finish;
		List<Cs2Library.Finish> list = finishes(lib);
		GuiDraw.scissor(g, x - 2, y, midW() + 4, h);
		for (int i = 0; i < list.size(); i++) {
			Cs2Library.Finish f = list.get(i);
			int col = i % cols;
			int row = i / cols;
			float appear = outCubic((now - tilesShown - Math.min(i, 24) * 18L) / 260f);
			float px = x + col * (tw + TILE_GAP);
			float py = y + row * (TILE_H + TILE_GAP) - finishScrollT + (1f - appear) * 10f;
			if (py + TILE_H < y || py > y + h) {
				continue;
			}
			float a = alpha * appear;
			boolean on = f.kit().equals(chosen);
			boolean hot = alpha > 0.5f && mouseY >= y && mouseY < y + h && GuiDraw.hovered(mouseX, mouseY, px, py, tw, TILE_H);
			float hv = hoverAmount("f:" + f.kit(), hot);
			float lift = hv * 2f;
			int base = Anim.mix(card(), cardHover(), hv);
			GuiDraw.rounded(g, px, py - lift, tw, TILE_H, 8, Anim.fade(base, a));
			if (on) {
				GuiDraw.roundedOutline(g, px, py - lift, tw, TILE_H, 8, Anim.fade(accent(), a), 1.5f);
			}
			Identifier tile = f.kit().isEmpty() ? null : tile(lib, knife, f.kit());
			int imgH = TILE_H - 20;
			if (tile != null) {
				int tint = Anim.fade(0xFFFFFFFF, a);
				g.blit(RenderPipelines.GUI_TEXTURED, tile, Math.round(px + 3), Math.round(py - lift + 3), 0f, 0f, tw - 6, imgH, tw - 6, imgH, tint);
			} else if (f.kit().isEmpty()) {
				float mx0 = px + tw / 2f;
				float my0 = py - lift + 3 + imgH / 2f;
				GuiDraw.stroke(g, mx0 - 14, my0 + 8, mx0 + 14, my0 - 8, 3f, Anim.fade(Theme.withAlpha(dim(), 140), a));
				GuiDraw.stroke(g, mx0 - 14, my0 + 8, mx0 - 20, my0 + 12, 4f, Anim.fade(Theme.withAlpha(dim(), 200), a));
			}
			small(g, fit(f.name(), tw - 8, true), px + 5, mid(py - lift + TILE_H - 17, 14), Anim.fade(on ? ink() : Anim.mix(dim(), ink(), hv), a));
		}
		GuiDraw.disableScissor(g);
		if (list.isEmpty()) {
			small(g, "No finish matches", x + 4, y + 4, Anim.fade(dim(), alpha));
		}
		int rows = (list.size() + cols - 1) / cols;
		scrollbar(g, x + midW() + 3, y, h, rows * (TILE_H + TILE_GAP), finishScrollT);
	}

	private void drawGrip(GuiGraphicsExtractor g, int mouseX, int mouseY, float alpha) {
		int x = midX();
		int y = panelTop();
		int w = midW();
		small(g, "Moves the knife in your hand. Changes show live in first person.", x, y + 2, Anim.fade(dim(), alpha));
		float[] grip = grip();
		for (int i = 0; i < 7; i++) {
			float sy = y + 18 + i * 26;
			float lo = GRIP_RANGES[i][0];
			float hi = GRIP_RANGES[i][1];
			float v = grip[i];
			String value = hi > 10f ? String.format(Locale.ROOT, "%.0f°", v)
				: i == 6 ? String.format(Locale.ROOT, "%.2fx", v)
				: String.format(Locale.ROOT, "%+.2f", v);
			boolean hot = alpha > 0.5f && GuiDraw.hovered(mouseX, mouseY, x, sy, w, 22);
			float hv = hoverAmount("g" + i, hot || dragging == i);
			text(g, GRIP_NAMES[i], x, mid(sy, 10), Anim.fade(ink(), alpha));
			small(g, value, x + w - smallW(value), mid(sy, 10), Anim.fade(Anim.mix(dim(), accent(), hv), alpha));
			slider(g, x, sy + 13, w, (v - lo) / (hi - lo), hv, alpha);
		}
		float ry = y + 18 + 7 * 26 + 2;
		button(g, "reset", "Reset to default", x, ry, 110, mouseX, mouseY, false, alpha);
	}

	private void slider(GuiGraphicsExtractor g, float x, float y, float w, float k, float hv, float alpha) {
		k = Math.max(0f, Math.min(1f, k));
		GuiDraw.rounded(g, x, y + 1, w, 4, 2, Anim.fade(track(), alpha));
		GuiDraw.rounded(g, x, y + 1, Math.max(4f, w * k), 4, 2, Anim.fade(accent(), alpha));
		float r = 4f + hv * 1.2f;
		GuiDraw.circle(g, x + w * k, y + 3, r + 1.5f, Anim.fade(Theme.withAlpha(accent(), 60), alpha * hv));
		GuiDraw.circle(g, x + w * k, y + 3, r, Anim.fade(accent(), alpha));
	}

	private boolean button(GuiGraphicsExtractor g, String key, String label, float x, float y, float w, int mouseX, int mouseY, boolean primary, float alpha) {
		boolean hot = GuiDraw.hovered(mouseX, mouseY, x, y, w, 18);
		float hv = hoverAmount("b:" + key, hot);
		int fill = primary ? Anim.mix(accent(), Anim.mix(accent(), 0xFFFFFFFF, 0.15f), hv) : Anim.mix(card(), cardHover(), hv);
		GuiDraw.rounded(g, x, y, w, 18, 9, Anim.fade(fill, alpha));
		int col = primary ? onAccent() : Anim.mix(dim(), ink(), hv);
		text(g, label, x + w / 2f - textW(label) / 2f, mid(y, 18), Anim.fade(col, alpha));
		return hot;
	}

	private void drawSide(GuiGraphicsExtractor g, Cs2Library lib, int mouseX, int mouseY, long now) {
		int x = sideX();
		int y = bodyY();
		StrayConfig c = StrayConfig.get();
		// preview card with a soft spotlight and a crossfade between renders
		GuiDraw.rounded(g, x, y, SIDE_W, PREVIEW_H, 10, card());
		GuiDraw.fillGradient(g, x + 1, y + 1, SIDE_W - 2, PREVIEW_H / 2f, Theme.withAlpha(accent(), 22), 0x00000000);
		drawPreview(g, x, y, now);
		Cs2Library.Knife k = lib.knives().get(c.cs2Knife);
		String name = k == null ? "" : k.name();
		text(g, fit(name, SIDE_W, false), x, y + PREVIEW_H + 7, ink());
		String fin = finishName(lib);
		small(g, fit(fin, SIDE_W, true), x, y + PREVIEW_H + 19, accent());

		// seed
		int sy = seedY();
		small(g, "Seed", x, mid(sy, 18), dim());
		boolean diceHot = GuiDraw.hovered(mouseX, mouseY, x + 92, sy, 18, 18);
		float dh = hoverAmount("dice", diceHot);
		GuiDraw.rounded(g, x + 92, sy, 18, 18, 5, Anim.mix(card(), cardHover(), dh));
		drawDice(g, x + 92, sy, Anim.mix(dim(), ink(), dh));
		String tier = wearName(c.cs2Wear);
		int tierW = textW(tier) + 14;
		GuiDraw.rounded(g, x + SIDE_W - tierW, sy, tierW, 18, 9, Theme.withAlpha(wearColor(c.cs2Wear), 52));
		text(g, tier, x + SIDE_W - tierW + 7, mid(sy, 18), wearColor(c.cs2Wear));

		// float slider
		int fy = floatY();
		small(g, "Float", x, mid(fy, 9), dim());
		String fv = String.format(Locale.ROOT, "%.4f", c.cs2Wear);
		small(g, fv, x + SIDE_W - smallW(fv), mid(fy, 9), ink());
		boolean fhot = GuiDraw.hovered(mouseX, mouseY, x, fy + 8, SIDE_W, 14);
		float fh = hoverAmount("float", fhot || dragging == 10);
		// wear tier bands under the track
		float[] cuts = {0f, 0.07f, 0.15f, 0.38f, 0.45f, 1f};
		for (int i = 0; i < 5; i++) {
			float bx = x + SIDE_W * cuts[i];
			float bw = SIDE_W * (cuts[i + 1] - cuts[i]);
			GuiDraw.fill(g, bx + (i == 0 ? 0 : 1), fy + 20, bw - 1, 2, Theme.withAlpha(wearColor((cuts[i] + cuts[i + 1]) / 2f), 120));
		}
		slider(g, x, fy + 12, SIDE_W, c.cs2Wear, fh, 1f);

		// apply to (segmented)
		int ay = applyY();
		int sel = indexOf(APPLY, c.cs2ApplyTo);
		applyT = applyT < 0 ? sel : ease(applyT, sel, 16f);
		float segW = SIDE_W / 3f;
		GuiDraw.rounded(g, x, ay, SIDE_W, 18, 9, card());
		GuiDraw.rounded(g, x + 2 + applyT * segW, ay + 2, segW - 4, 14, 7, accent());
		for (int i = 0; i < 3; i++) {
			float on = Math.max(0f, 1f - Math.abs(applyT - i));
			String l = APPLY_LABELS[i];
			text(g, l, x + segW * i + segW / 2f - textW(l) / 2f, mid(ay, 18), Anim.mix(dim(), onAccent(), on));
		}

		// switches
		int wy = switchY();
		animT = ease(animT, c.cs2Animations ? 1f : 0f, 16f);
		switchRow(g, "CS2 animations", x, wy, animT, mouseX, mouseY);
		perSwordT = ease(perSwordT, c.cs2PerSword ? 1f : 0f, 16f);
		switchRow(g, "Skin per sword", x, wy + 18, perSwordT, mouseX, mouseY);
	}

	private void switchRow(GuiGraphicsExtractor g, String label, int x, int y, float k, int mouseX, int mouseY) {
		boolean hot = GuiDraw.hovered(mouseX, mouseY, x, y, SIDE_W, 16);
		text(g, label, x, mid(y + 2, 13), Anim.mix(dim(), ink(), Math.max(k, hoverAmount("s:" + label, hot))));
		float sw = 26;
		float sx = x + SIDE_W - sw;
		if (hypr()) {
			HyprMenu.m3Switch(g, sx, y + 2, sw, 13, k, hot, 1f);
		} else {
			GuiDraw.rounded(g, sx, y + 2, sw, 13, 6.5f, Anim.mix(track(), accent(), k));
			GuiDraw.circle(g, sx + 6.5f + k * (sw - 13), y + 8.5f, 4.5f, Anim.mix(dim(), 0xFFFFFFFF, k));
		}
	}

	private void drawDice(GuiGraphicsExtractor g, float x, float y, int color) {
		GuiDraw.roundedOutline(g, x + 4, y + 4, 10, 10, 2.5f, color, 1.2f);
		GuiDraw.circle(g, x + 6.8f, y + 6.8f, 1f, color);
		GuiDraw.circle(g, x + 9f, y + 9f, 1f, color);
		GuiDraw.circle(g, x + 11.2f, y + 11.2f, 1f, color);
	}

	private static int wearColor(float w) {
		if (w < 0.07f) {
			return 0xFF4CC38A;
		}
		if (w < 0.15f) {
			return 0xFF8BC34A;
		}
		if (w < 0.38f) {
			return 0xFFE2B33C;
		}
		if (w < 0.45f) {
			return 0xFFE8873A;
		}
		return 0xFFE5484D;
	}

	private void drawPreview(GuiGraphicsExtractor g, int x, int y, long now) {
		if (previewDue != 0 && now >= previewDue) {
			previewDue = 0;
			startPreview();
		}
		int pw = SIDE_W;
		int ph = PREVIEW_H;
		int[] pixels = previewPixels;
		if (pixels != null) {
			previewPixels = null;
			int slot = previewSlot < 0 ? 0 : 1 - previewSlot;
			NativeImage img = new NativeImage(pw * 2, ph * 2, false);
			for (int py = 0; py < ph * 2; py++) {
				for (int px = 0; px < pw * 2; px++) {
					img.setPixel(px, py, pixels[py * pw * 2 + px]);
				}
			}
			Minecraft.getInstance().getTextureManager().register(PREVIEW[slot], new DynamicTexture(() -> "stray-cs2-preview", img));
			previewHasOld = previewSlot >= 0;
			previewSlot = slot;
			previewSwap = now;
		}
		if (previewSlot >= 0) {
			float t = outCubic((now - previewSwap) / 260f);
			if (previewHasOld && t < 1f) {
				g.blit(RenderPipelines.GUI_TEXTURED, PREVIEW[1 - previewSlot], x, y, 0f, 0f, pw, ph, pw, ph, Anim.fade(0xFFFFFFFF, 1f - t));
			}
			float s = 0.97f + 0.03f * t;
			g.pose().pushMatrix();
			g.pose().translate(x + pw / 2f, y + ph / 2f);
			g.pose().scale(s, s);
			g.pose().translate(-(x + pw / 2f), -(y + ph / 2f));
			g.blit(RenderPipelines.GUI_TEXTURED, PREVIEW[previewSlot], x, y, 0f, 0f, pw, ph, pw, ph, Anim.fade(0xFFFFFFFF, t));
			g.pose().popMatrix();
		}
		if (!java.util.Objects.equals(previewReadyKey, Cs2Knives.key())) {
			// spinner while rendering
			float a = (now % 900) / 900f * (float) (Math.PI * 2);
			for (int i = 0; i < 8; i++) {
				double ang = a + i * Math.PI / 4;
				int alpha = 40 + i * 25;
				GuiDraw.circle(g, x + pw - 14 + (float) Math.cos(ang) * 5f, y + 14 + (float) Math.sin(ang) * 5f, 1.3f, Theme.withAlpha(accent(), Math.min(255, alpha)));
			}
		}
	}

	private static String keyName(String key) {
		return dev.stray.client.combat.OdinClicks.keyLabel(dev.stray.client.combat.OdinClicks.parseKey(key));
	}

	static String wearName(float w) {
		if (w < 0.07f) {
			return "Factory New";
		}
		if (w < 0.15f) {
			return "Minimal Wear";
		}
		if (w < 0.38f) {
			return "Field-Tested";
		}
		if (w < 0.45f) {
			return "Well-Worn";
		}
		return "Battle-Scarred";
	}

	// ------------------------------------------------------------- previews

	private Identifier tile(Cs2Library lib, String knife, String kit) {
		String key = knife + "|" + kit;
		Identifier id = tiles.get(key);
		if (id != null) {
			return id;
		}
		NativeImage img = tileImages.remove(key);
		if (img != null) {
			Identifier tid = Stray.id("cs2/tile/" + Integer.toHexString(key.hashCode()));
			Minecraft.getInstance().getTextureManager().register(tid, new DynamicTexture(() -> "stray-cs2-tile", img));
			tiles.put(key, tid);
			return tid;
		}
		if (tileRequested.add(key)) {
			CompletableFuture.runAsync(() -> {
				Tex t = lib.preview(knife, kit);
				if (t == null) {
					return;
				}
				NativeImage out = new NativeImage(t.w, t.h, false);
				for (int y = 0; y < t.h; y++) {
					for (int x = 0; x < t.w; x++) {
						int i = (y * t.w + x) * 4;
						int a = clamp(t.px[i + 3]);
						out.setPixel(x, y, (a << 24) | (clamp(t.px[i]) << 16) | (clamp(t.px[i + 1]) << 8) | clamp(t.px[i + 2]));
					}
				}
				tileImages.put(key, out);
			}, WORKER);
		}
		return null;
	}

	private static int clamp(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255f)));
	}

	private void schedulePreview() {
		previewDue = System.currentTimeMillis() + 200;
	}

	private void startPreview() {
		Cs2Library lib = Cs2Knives.library();
		if (lib == null) {
			schedulePreview();
			return;
		}
		StrayConfig c = StrayConfig.get();
		String key = Cs2Knives.key();
		if (key.equals(previewKey)) {
			return;
		}
		previewKey = key;
		String knifeId = c.cs2Knife;
		String finish = c.cs2Finish;
		int seed = c.cs2Seed;
		float wear = c.cs2Wear;
		CompletableFuture.runAsync(() -> {
			try {
				Cs2Library.Knife info = lib.knives().get(knifeId);
				if (info == null) {
					return;
				}
				S2Model model = new S2Model(lib.assets().resource(info.model()));
				KnifeMaterial material = new KnifeMaterial(lib.assets(), model.parts.get(0).material);
				PaintKit kit = finish.isEmpty() ? null : lib.kit(finish);
				Composer.Result skin = Composer.compose(lib.assets(), material, kit, seed, wear, 512);
				int[] px = SoftwareRender.render(model, skin, SIDE_W * 2, PREVIEW_H * 2, 180f);
				if (key.equals(previewKey)) {
					previewPixels = px;
					previewReadyKey = key;
				}
			} catch (RuntimeException e) {
				Stray.LOGGER.warn("CS2 knives: preview failed", e);
			}
		}, WORKER);
	}

	// ----------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (bindListen) {
			StrayConfig.get().cs2InspectKey = dev.stray.client.combat.OdinClicks.keyName(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(event.button()));
			bindListen = false;
			return true;
		}
		if (super.mouseClicked(event, doubled)) {
			return true;
		}
		if (event.button() != 0) {
			return false;
		}
		double mx = event.x();
		double my = event.y();
		if (inspectChip(mx, my)) {
			bindListen = true;
			setFocused(null);
			return true;
		}
		if (GuiDraw.hovered(mx, my, left + W - PAD - 18, top + 11, 18, 18)) {
			onClose();
			return true;
		}
		Cs2Library lib = Cs2Knives.library();
		if (lib == null) {
			return false;
		}
		StrayConfig c = StrayConfig.get();
		// knife list
		if (GuiDraw.hovered(mx, my, listX(), listTop(), LIST_W, listH())) {
			int row = (int) ((my - listTop() + knifeScrollT) / ROW_H);
			List<Cs2Library.Knife> list = knives(lib);
			if (row >= 0 && row < list.size()) {
				Cs2Library.Knife k = list.get(row);
				if (!k.id().equals(c.cs2Knife)) {
					c.cs2Knife = k.id();
					if (k.finishes().stream().noneMatch(f -> f.kit().equals(c.cs2Finish))) {
						c.cs2Finish = "";
					}
					finishScroll = 0f;
					finishScrollT = 0f;
					tilesShown = System.currentTimeMillis();
					schedulePreview();
				}
				return true;
			}
		}
		// tabs
		if (GuiDraw.hovered(mx, my, midX(), bodyY(), midW(), 18)) {
			int t = Math.max(0, Math.min(TABS.length - 1, (int) ((mx - midX()) / (midW() / (float) TABS.length))));
			if (t != tab) {
				tab = t;
				finishSearch.visible = tab == 0;
				finishSearch.setFocused(false);
				if (tab == 0) {
					tilesShown = System.currentTimeMillis();
				}
			}
			return true;
		}
		if (tab == 0 && GuiDraw.hovered(mx, my, midX(), gridTop(), midW(), gridH())) {
			int cols = columns();
			int tw = tileW();
			int col = (int) ((mx - midX()) / (tw + TILE_GAP));
			int row = (int) ((my - gridTop() + finishScrollT) / (TILE_H + TILE_GAP));
			int i = row * cols + col;
			List<Cs2Library.Finish> list = finishes(lib);
			if (col < cols && i >= 0 && i < list.size()) {
				c.cs2Finish = list.get(i).kit();
				schedulePreview();
				return true;
			}
		}
		if (tab == 2) {
			int vx = midX();
			int vy = panelTop();
			for (int i = 0; i < 3; i++) {
				float sy = vy + 18 + i * 26;
				if (GuiDraw.hovered(mx, my, vx - 4, sy + 8, midW() + 8, 14)) {
					dragging = 30 + i;
					dragView(mx);
					return true;
				}
			}
			if (GuiDraw.hovered(mx, my, vx, vy + 18 + 3 * 26 + 2, 70, 18)) {
				c.cs2ViewOffset = new float[] {0f, 0f, 0f};
				return true;
			}
		}
		if (tab == 3 && bladeClick(mx, my)) {
			return true;
		}
		if (tab == 1) {
			int x = midX();
			int y = panelTop();
			for (int i = 0; i < 7; i++) {
				float sy = y + 18 + i * 26;
				if (GuiDraw.hovered(mx, my, x - 4, sy + 8, midW() + 8, 14)) {
					dragging = i;
					dragGrip(mx);
					return true;
				}
			}
			if (GuiDraw.hovered(mx, my, x, y + 18 + 7 * 26 + 2, 110, 18)) {
				c.cs2Grip.remove(c.cs2Knife + "#hand");
				return true;
			}
		}
		int x = sideX();
		// dice
		if (GuiDraw.hovered(mx, my, x + 92, seedY(), 18, 18)) {
			seedBox.setValue(String.valueOf((int) (Math.random() * 1001)));
			return true;
		}
		// float
		if (GuiDraw.hovered(mx, my, x - 4, floatY() + 6, SIDE_W + 8, 18)) {
			dragging = 10;
			dragFloat(mx);
			return true;
		}
		// apply to
		if (GuiDraw.hovered(mx, my, x, applyY(), SIDE_W, 18)) {
			int i = Math.max(0, Math.min(2, (int) ((mx - x) / (SIDE_W / 3f))));
			c.cs2ApplyTo = APPLY[i];
			return true;
		}
		if (GuiDraw.hovered(mx, my, x, switchY(), SIDE_W, 16)) {
			c.cs2Animations = !c.cs2Animations;
			return true;
		}
		if (GuiDraw.hovered(mx, my, x, switchY() + 18, SIDE_W, 16)) {
			c.cs2PerSword = !c.cs2PerSword;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == 10) {
			dragFloat(event.x());
			return true;
		}
		if (dragging >= 0 && dragging < 7) {
			dragGrip(event.x());
			return true;
		}
		if (dragging >= 30) {
			dragView(event.x());
			return true;
		}
		if (dragging >= 20) {
			dragBlade(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = -1;
		return super.mouseReleased(event);
	}

	/** Blade tab layout, mirrored from drawBlade. */
	private boolean bladeClick(double mx, double my) {
		StrayConfig c = StrayConfig.get();
		int x = midX();
		int y = panelTop();
		int w = midW();
		if (GuiDraw.hovered(mx, my, x, y, w, 16)) {
			c.cs2BladeShader = !c.cs2BladeShader;
			return true;
		}
		int by = y + 38 + 12;
		if (GuiDraw.hovered(mx, my, x, by, w, 18)) {
			int i = Math.max(0, Math.min(3, (int) ((mx - x) / (w / 4f))));
			c.cs2BladeShaderStyle = STYLES[i];
			return true;
		}
		int swy = by + 28 + 14;
		int sw = 16;
		float gap = (w - SWATCHES.length * sw) / (float) (SWATCHES.length - 1);
		for (int i = 0; i < SWATCHES.length; i++) {
			float sx = x + i * (sw + gap);
			if (GuiDraw.hovered(mx, my, sx - 2, swy - 2, sw + 4, sw + 4)) {
				c.cs2BladeShaderRgb = SWATCHES[i];
				return true;
			}
		}
		int hy = swy + sw + 8;
		if (GuiDraw.hovered(mx, my, x - 4, hy - 3, w + 8, 14)) {
			dragging = 20;
			dragBlade(mx);
			return true;
		}
		int ly = hy + 20;
		if (GuiDraw.hovered(mx, my, x - 4, ly + 8, w + 8, 14)) {
			dragging = 21;
			dragBlade(mx);
			return true;
		}
		if (GuiDraw.hovered(mx, my, x - 4, ly + 28 + 8, w + 8, 14)) {
			dragging = 22;
			dragBlade(mx);
			return true;
		}
		return false;
	}

	private void dragView(double mx) {
		float k = (float) Math.max(0, Math.min(1, (mx - midX()) / midW()));
		viewOffset()[dragging - 30] = Math.round((-0.6f + k * 1.2f) * 100f) / 100f;
	}

	private void dragBlade(double mx) {
		StrayConfig c = StrayConfig.get();
		float k = (float) Math.max(0, Math.min(1, (mx - midX()) / midW()));
		switch (dragging) {
			case 20 -> c.cs2BladeShaderRgb = fromHue(Math.min(0.999f, k));
			case 21 -> c.cs2BladeShaderFill = Math.round((0.08f + k * (0.85f - 0.08f)) * 100f) / 100f;
			case 22 -> c.cs2BladeShaderSmoke = Math.round((0.10f + k * (1.50f - 0.10f)) * 100f) / 100f;
			default -> {
			}
		}
	}

	private void dragFloat(double mx) {
		float k = (float) ((mx - sideX()) / SIDE_W);
		StrayConfig.get().cs2Wear = Math.max(0f, Math.min(1f, Math.round(k * 10000f) / 10000f));
		schedulePreview();
	}

	private void dragGrip(double mx) {
		int i = dragging;
		float k = (float) Math.max(0, Math.min(1, (mx - midX()) / midW()));
		float lo = GRIP_RANGES[i][0];
		float hi = GRIP_RANGES[i][1];
		float v = lo + k * (hi - lo);
		v = hi > 10f ? Math.round(v) : Math.round(v * 100f) / 100f;
		float[] grip = grip();
		grip[i] = v;
		storeGrip(grip);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		Cs2Library lib = Cs2Knives.library();
		if (lib == null) {
			return false;
		}
		if (mx < midX()) {
			float max = Math.max(0, knives(lib).size() * ROW_H - listH());
			knifeScroll = Math.max(0, Math.min(max, knifeScroll - (float) sy * ROW_H * 2));
			return true;
		}
		if (mx < sideX() && tab == 0) {
			int rows = (finishes(lib).size() + columns() - 1) / columns();
			float max = Math.max(0, rows * (TILE_H + TILE_GAP) - gridH());
			finishScroll = Math.max(0, Math.min(max, finishScroll - (float) sy * (TILE_H + TILE_GAP) / 2f));
			return true;
		}
		if (mx >= sideX() && GuiDraw.hovered(mx, my, sideX(), floatY() + 6, SIDE_W, 18)) {
			StrayConfig c = StrayConfig.get();
			c.cs2Wear = Math.max(0f, Math.min(1f, c.cs2Wear + (float) sy * 0.01f));
			schedulePreview();
			return true;
		}
		return false;
	}

	private boolean inspectChip(double mx, double my) {
		String key = keyName(StrayConfig.get().cs2InspectKey);
		int kw = Math.max(26, textW(key) + 16);
		int kx = left + W - PAD - 18 - 8 - kw;
		return GuiDraw.hovered(mx, my, kx, top + 11, kw, 18);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (bindListen) {
			int k = event.key();
			if (k != com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
				StrayConfig.get().cs2InspectKey = dev.stray.client.combat.OdinClicks.keyName(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(k));
			}
			bindListen = false;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		StrayConfig.get().save();
		var textures = Minecraft.getInstance().getTextureManager();
		for (Identifier id : tiles.values()) {
			textures.release(id);
		}
		tiles.clear();
		for (NativeImage img : tileImages.values()) {
			img.close();
		}
		tileImages.clear();
		for (Identifier id : PREVIEW) {
			textures.release(id);
		}
		minecraft.setScreen(parent);
	}
}
