package dev.stray.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.cs2.Cs2Knives;
import dev.stray.client.cs2.Cs2Library;
import dev.stray.client.cs2.render.SoftwareRender;
import dev.stray.client.cs2.s2.S2Model;
import dev.stray.client.cs2.skin.Composer;
import dev.stray.client.cs2.skin.KnifeMaterial;
import dev.stray.client.cs2.skin.PaintKit;
import dev.stray.client.cs2.skin.Tex;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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
 * Picks the CS2 knife and finish. Finish tiles use CS2's own preview images; the large
 * preview is rendered from the real mesh with the chosen seed and wear.
 */
public final class Cs2KnifeScreen extends Screen {
	private static final int W = 470;
	private static final int H = 300;
	private static final int LIST_W = 112;
	private static final int ROW_H = 13;
	private static final int TILE_W = 74;
	private static final int TILE_H = 64;
	private static final int PREVIEW_W = 168;
	private static final int PREVIEW_H = 126;
	private static final String[] APPLY = {"swords", "held", "empty"};
	private static final String[] APPLY_LABELS = {"Swords & axes", "Any held item", "Empty hand"};
	private static final Identifier PREVIEW = Stray.id("cs2/preview");

	private static final ExecutorService WORKER = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Stray CS2 preview");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private final Screen parent;
	private int left;
	private int top;
	private float knifeScroll;
	private float finishScroll;
	private EditBox seedBox;
	private EditBox wearBox;
	private boolean gripMode;

	private final Map<String, Identifier> tiles = new ConcurrentHashMap<>();
	private final Map<String, NativeImage> tileImages = new ConcurrentHashMap<>();
	private final Set<String> tileRequested = ConcurrentHashMap.newKeySet();
	private String previewKey;
	private volatile int[] previewPixels;
	private volatile String previewReadyKey;
	private boolean previewUploaded;
	private long previewDue;

	public Cs2KnifeScreen(Screen parent) {
		super(Component.literal("CS2 knife"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = (height - H) / 2;
		StrayConfig c = StrayConfig.get();
		int bottom = top + H - 26;
		int x = left + 10;
		if (!gripMode) {
			seedBox = box(x + 30, bottom - 24, 46, "Seed", "0-1000", String.valueOf(c.cs2Seed));
			seedBox.setResponder(v -> {
				try {
					c.cs2Seed = v.isEmpty() ? 0 : Math.min(1000, Integer.parseInt(v));
				} catch (NumberFormatException ignored) {
				}
				schedulePreview();
			});
			addRenderableWidget(Button.builder(Component.literal("Random"), b -> {
				int seed = (int) (Math.random() * 1001);
				seedBox.setValue(String.valueOf(seed));
			}).bounds(x + 80, bottom - 24, 50, 18).build());
			wearBox = box(x + 30 + 140, bottom - 24, 70, "Wear", "0.00-1.00", String.format(Locale.ROOT, "%.4f", c.cs2Wear));
			wearBox.setResponder(v -> {
				try {
					c.cs2Wear = Math.max(0f, Math.min(1f, Float.parseFloat(v)));
				} catch (NumberFormatException ignored) {
				}
				schedulePreview();
			});
			addRenderableWidget(Button.builder(Component.literal(applyLabel()), b -> {
				int i = (indexOf(APPLY, c.cs2ApplyTo) + 1) % APPLY.length;
				c.cs2ApplyTo = APPLY[i];
				b.setMessage(Component.literal(applyLabel()));
			}).bounds(x + 250, bottom - 24, 96, 18).build());
			addRenderableWidget(Button.builder(Component.literal(toggle("Blade shader", c.cs2BladeShader)), b -> {
				c.cs2BladeShader = !c.cs2BladeShader;
				b.setMessage(Component.literal(toggle("Blade shader", c.cs2BladeShader)));
			}).bounds(x + 350, bottom - 24, 100, 18).build());
			addRenderableWidget(Button.builder(Component.literal(toggle("Animations", c.cs2Animations)), b -> {
				c.cs2Animations = !c.cs2Animations;
				b.setMessage(Component.literal(toggle("Animations", c.cs2Animations)));
			}).bounds(x, bottom, 96, 18).build());
		} else {
			int gx = gridX();
			int gy = gridY();
			int gw = gridW();
			addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
				StrayConfig.get().cs2Grip.remove(gripKey());
				rebuildWidgets();
			}).bounds(gx, gy, gw, 18).build());
			String[] names = {"Edge / spine", "Sideways", "Along handle", "Pitch", "Yaw", "Roll", "Size"};
			float m = 6f;
			float[][] ranges = {{-m, m}, {-m, m}, {-m, m}, {-180f, 180f}, {-180f, 180f}, {-180f, 180f}, {0.4f, 2.0f}};
			float[] g = grip();
			for (int i = 0; i < 7; i++) {
				final int k = i;
				float lo = ranges[i][0];
				float hi = ranges[i][1];
				addRenderableWidget(new GripSlider(gx, gy + 24 + i * 22, gw, names[i], lo, hi, g[i], v -> {
					float[] grip = grip();
					grip[k] = v;
					storeGrip(grip);
				}));
			}
		}
		addRenderableWidget(Button.builder(Component.literal(gripMode ? "Skins" : "Adjust grip"), b -> {
			gripMode = !gripMode;
			rebuildWidgets();
		}).bounds(left + W - 10 - 160 - 4, bottom, 80, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(left + W - 10 - 76, bottom, 76, 18).build());
		schedulePreview();
	}

	private String gripKey() {
		String id = StrayConfig.get().cs2Knife;
		return id + "#hand";
	}

	private float[] grip() {
		float[] g = StrayConfig.get().cs2Grip.get(gripKey());
		if (g == null || g.length < 7) {
			return dev.stray.client.cs2.GripDefaults.hand(StrayConfig.get().cs2Knife);
		}
		return g.clone();
	}

	private void storeGrip(float[] g) {
		StrayConfig.get().cs2Grip.put(gripKey(), g);
	}

	private EditBox box(int x, int y, int w, String name, String hint, String value) {
		EditBox box = new EditBox(font, x, y, w, 18, Component.literal(name));
		box.setMaxLength(256);
		box.setHint(Component.literal(hint));
		box.setValue(value);
		addRenderableWidget(box);
		return box;
	}

	private static int indexOf(String[] values, String v) {
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(v)) {
				return i;
			}
		}
		return 0;
	}

	private static String applyLabel() {
		return APPLY_LABELS[indexOf(APPLY, StrayConfig.get().cs2ApplyTo)];
	}

	private static String toggle(String name, boolean on) {
		return name + ": " + (on ? "On" : "Off");
	}

	// ---------------------------------------------------------------- layout

	private int listX() {
		return left + 10;
	}

	private int listY() {
		return top + 24;
	}

	private int listH() {
		return H - 24 - 64;
	}

	private int gridX() {
		return left + 10 + LIST_W + 8;
	}

	private int gridY() {
		return top + 24;
	}

	private int gridW() {
		return W - 20 - LIST_W - 8 - PREVIEW_W - 8;
	}

	private int gridH() {
		return listH();
	}

	private int previewX() {
		return left + W - 10 - PREVIEW_W;
	}

	private int columns() {
		return Math.max(1, gridW() / (TILE_W + 4));
	}

	// ------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		GuiDraw.panel(graphics, left, top, W, H, 8, Theme.WINDOW, Theme.LINE);
		GuiDraw.small(graphics, font, "CS2 KNIFE", left + 10, top + 8, Theme.ACCENT);
		String status = Cs2Knives.status();
		Cs2Library lib = Cs2Knives.library();
		if (lib == null) {
			GuiDraw.menu(graphics, font, status.isEmpty() ? "Loading knives..." : status, left + 70, top + 8, Theme.MUTED);
		} else if (!status.isEmpty()) {
			GuiDraw.small(graphics, font, status, left + 70, top + 8, Theme.MUTED);
		}
		if (lib != null) {
			drawKnives(graphics, lib, mouseX, mouseY);
			if (!gripMode) {
				drawFinishes(graphics, lib, mouseX, mouseY);
			} else {
				GuiDraw.small(graphics, font, "Moves the knife in your hand (live)", gridX(), top + H - 26 - 22, Theme.MUTED);
			}
			drawPreview(graphics);
		}
		if (!gripMode) {
			GuiDraw.small(graphics, font, "Seed", left + 10, top + H - 26 - 24 + 5, Theme.MUTED);
			GuiDraw.small(graphics, font, "Wear", left + 10 + 146, top + H - 26 - 24 + 5, Theme.MUTED);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
	}

	private void drawKnives(GuiGraphicsExtractor graphics, Cs2Library lib, int mouseX, int mouseY) {
		int x = listX();
		int y = listY();
		graphics.enableScissor(x, y, x + LIST_W, y + listH());
		int row = 0;
		String selected = StrayConfig.get().cs2Knife;
		for (Cs2Library.Knife k : lib.knives().values()) {
			int ry = y + Math.round(row * ROW_H - knifeScroll);
			row++;
			if (ry + ROW_H < y || ry > y + listH()) {
				continue;
			}
			boolean on = k.id().equals(selected);
			boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= ry && mouseY < ry + ROW_H && mouseY >= y && mouseY < y + listH();
			if (on || hover) {
				GuiDraw.rounded(graphics, x, ry, LIST_W, ROW_H - 1, 3, on ? Theme.withAlpha(Theme.ACCENT, 90) : Theme.CARD_HOVER);
			}
			GuiDraw.menu(graphics, font, k.name(), x + 4, ry + 2, on ? Theme.TEXT : Theme.MUTED);
		}
		graphics.disableScissor();
	}

	private List<Cs2Library.Finish> finishes(Cs2Library lib) {
		Cs2Library.Knife k = lib.knives().get(StrayConfig.get().cs2Knife);
		return k == null ? List.of() : k.finishes();
	}

	private void drawFinishes(GuiGraphicsExtractor graphics, Cs2Library lib, int mouseX, int mouseY) {
		int x = gridX();
		int y = gridY();
		int cols = columns();
		String knife = StrayConfig.get().cs2Knife;
		String chosen = StrayConfig.get().cs2Finish;
		graphics.enableScissor(x, y, x + gridW(), y + gridH());
		List<Cs2Library.Finish> list = finishes(lib);
		for (int i = 0; i < list.size(); i++) {
			Cs2Library.Finish f = list.get(i);
			int tx = x + (i % cols) * (TILE_W + 4);
			int ty = y + Math.round((i / cols) * (TILE_H + 4) - finishScroll);
			if (ty + TILE_H < y || ty > y + gridH()) {
				continue;
			}
			boolean on = f.kit().equals(chosen);
			boolean hover = mouseX >= tx && mouseX < tx + TILE_W && mouseY >= ty && mouseY < ty + TILE_H && mouseY >= y && mouseY < y + gridH();
			GuiDraw.rounded(graphics, tx, ty, TILE_W, TILE_H, 4, on ? Theme.withAlpha(Theme.ACCENT, 80) : hover ? Theme.CARD_HOVER : Theme.CARD);
			Identifier tile = tile(lib, knife, f.kit());
			if (tile != null) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, tile, tx + 2, ty + 2, 0f, 0f, TILE_W - 4, TILE_H - 16, TILE_W - 4, TILE_H - 16);
			}
			String name = f.name();
			while (font.width(name) > TILE_W - 6 && name.length() > 3) {
				name = name.substring(0, name.length() - 2);
			}
			GuiDraw.small(graphics, font, name, tx + 3, ty + TILE_H - 12, on ? Theme.TEXT : Theme.MUTED);
		}
		graphics.disableScissor();
	}

	private void drawPreview(GuiGraphicsExtractor graphics) {
		int x = previewX();
		int y = gridY();
		GuiDraw.rounded(graphics, x, y, PREVIEW_W, PREVIEW_H, 6, Theme.CARD);
		if (previewDue != 0 && System.currentTimeMillis() >= previewDue) {
			previewDue = 0;
			startPreview();
		}
		int[] pixels = previewPixels;
		if (pixels != null && !previewUploaded) {
			NativeImage img = new NativeImage(PREVIEW_W * 2, PREVIEW_H * 2, false);
			for (int py = 0; py < PREVIEW_H * 2; py++) {
				for (int px = 0; px < PREVIEW_W * 2; px++) {
					img.setPixel(px, py, pixels[py * PREVIEW_W * 2 + px]);
				}
			}
			Minecraft.getInstance().getTextureManager().register(PREVIEW, new DynamicTexture(() -> "stray-cs2-preview", img));
			previewUploaded = true;
		}
		if (previewReadyKey != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, PREVIEW, x, y, 0f, 0f, PREVIEW_W, PREVIEW_H, PREVIEW_W, PREVIEW_H);
		}
		if (!java.util.Objects.equals(previewReadyKey, Cs2Knives.key())) {
			GuiDraw.small(graphics, font, "Rendering...", x + 6, y + PREVIEW_H - 12, Theme.MUTED);
		}
		Cs2Library lib = Cs2Knives.library();
		StrayConfig c = StrayConfig.get();
		if (lib != null) {
			Cs2Library.Knife k = lib.knives().get(c.cs2Knife);
			String finish = "Vanilla";
			if (k != null) {
				for (Cs2Library.Finish f : k.finishes()) {
					if (f.kit().equals(c.cs2Finish)) {
						finish = f.name();
					}
				}
			}
			GuiDraw.menu(graphics, font, (k == null ? "" : k.name()) + " | " + finish, x, y + PREVIEW_H + 6, Theme.TEXT);
			GuiDraw.small(graphics, font, "Seed " + c.cs2Seed + "  ·  Float " + String.format(Locale.ROOT, "%.4f", c.cs2Wear) + "  ·  " + wearName(c.cs2Wear), x, y + PREVIEW_H + 19, Theme.MUTED);
			GuiDraw.small(graphics, font, "Inspect: " + keyName(c.cs2InspectKey), x, y + PREVIEW_H + 31, Theme.MUTED);
		}
	}

	private static String keyName(String key) {
		String k = key == null ? "" : key.replace("key.keyboard.", "").replace("key.mouse.", "mouse ");
		return k.toUpperCase(Locale.ROOT);
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
		previewDue = System.currentTimeMillis() + 250;
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
				int[] px = SoftwareRender.render(model, skin, PREVIEW_W * 2, PREVIEW_H * 2, 180f);
				if (key.equals(previewKey)) {
					previewPixels = px;
					previewUploaded = false;
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
		if (super.mouseClicked(event, doubled)) {
			return true;
		}
		Cs2Library lib = Cs2Knives.library();
		if (lib == null || event.button() != 0) {
			return false;
		}
		double mx = event.x();
		double my = event.y();
		StrayConfig c = StrayConfig.get();
		if (mx >= listX() && mx < listX() + LIST_W && my >= listY() && my < listY() + listH()) {
			int row = (int) ((my - listY() + knifeScroll) / ROW_H);
			List<Cs2Library.Knife> knives = List.copyOf(lib.knives().values());
			if (row >= 0 && row < knives.size()) {
				String id = knives.get(row).id();
				if (!id.equals(c.cs2Knife)) {
					c.cs2Knife = id;
					boolean has = knives.get(row).finishes().stream().anyMatch(f -> f.kit().equals(c.cs2Finish));
					if (!has) {
						c.cs2Finish = "";
					}
					finishScroll = 0f;
					schedulePreview();
					if (gripMode) {
						rebuildWidgets();
					}
				}
				return true;
			}
		}
		if (!gripMode && mx >= gridX() && mx < gridX() + gridW() && my >= gridY() && my < gridY() + gridH()) {
			int cols = columns();
			int col = (int) ((mx - gridX()) / (TILE_W + 4));
			int row = (int) ((my - gridY() + finishScroll) / (TILE_H + 4));
			int i = row * cols + col;
			List<Cs2Library.Finish> list = finishes(lib);
			if (col < cols && i >= 0 && i < list.size()) {
				c.cs2Finish = list.get(i).kit();
				schedulePreview();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		Cs2Library lib = Cs2Knives.library();
		if (lib == null) {
			return false;
		}
		if (mx < gridX()) {
			float max = Math.max(0, lib.knives().size() * ROW_H - listH());
			knifeScroll = Math.max(0, Math.min(max, knifeScroll - (float) sy * ROW_H * 2));
			return true;
		}
		if (mx < previewX()) {
			int rows = (finishes(lib).size() + columns() - 1) / columns();
			float max = Math.max(0, rows * (TILE_H + 4) - gridH());
			finishScroll = Math.max(0, Math.min(max, finishScroll - (float) sy * (TILE_H + 4) / 2f));
			return true;
		}
		return false;
	}

	/** A labelled slider over a float range. */
	private static final class GripSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
		private final String name;
		private final float lo;
		private final float hi;
		private final java.util.function.Consumer<Float> apply;

		GripSlider(int x, int y, int w, String name, float lo, float hi, float value, java.util.function.Consumer<Float> apply) {
			super(x, y, w, 18, Component.empty(), (Math.max(lo, Math.min(hi, value)) - lo) / (hi - lo));
			this.name = name;
			this.lo = lo;
			this.hi = hi;
			this.apply = apply;
			updateMessage();
		}

		private float current() {
			return lo + (float) value * (hi - lo);
		}

		@Override
		protected void updateMessage() {
			float v = current();
			String text = hi > 10f ? String.format(Locale.ROOT, "%s: %.0f°", name, v)
				: name.equals("Size") ? String.format(Locale.ROOT, "%s: %.2fx", name, v)
				: String.format(Locale.ROOT, "%s: %+.2f", name, v);
			setMessage(Component.literal(text));
		}

		@Override
		protected void applyValue() {
			apply.accept(current());
		}
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
		textures.release(PREVIEW);
		minecraft.setScreen(parent);
	}
}
