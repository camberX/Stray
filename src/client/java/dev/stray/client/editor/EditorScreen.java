package dev.stray.client.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.movement.Freecam;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.ui.TextKeys;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The world editor's workspace, laid out like Blender: a header with the main actions, a tool
 * shelf on the left, a properties panel on the right (N toggles it), a block browser along the
 * bottom with the palette, and a status bar. The middle is the viewport: the world itself,
 * where the cursor aims the active tool. The same layout is drawn (without input) while you
 * hold right mouse to look around, so the panels don't blink away.
 */
public class EditorScreen extends Screen {
	// Blender's default dark theme.
	private static final int REGION = 0xF0303030;
	private static final int HEADER = 0xF0262626;
	private static final int STATUS = 0xF01D1D1D;
	private static final int BORDER = 0xFF161616;
	private static final int WIDGET = 0xFF545454;
	private static final int WIDGET_HOVER = 0xFF656565;
	private static final int WIDGET_DARK = 0xFF3D3D3D;
	private static final int FIELD = 0xFF1D1D1D;
	private static final int ACTIVE = 0xFF4772B3;
	private static final int ACTIVE_HOVER = 0xFF5680C2;
	private static final int TEXT = 0xFFE6E6E6;
	private static final int DIM = 0xFF9A9A9A;
	private static final int FAINT = 0xFF6A6A6A;
	private static final int RED = 0xFFE05555;
	private static final int ORANGE = 0xFFF0A030;

	private static final float TOP_H = 15;
	private static final float STATUS_H = 11;
	private static final float SHELF_W = 24;
	private static final float PANEL_W = 122;
	private static final float BROWSER_H = 76;
	private static final float CELL = 18;
	private static final String SEARCH_KEY = "editor-search";
	private static final String[] CATEGORIES = {"All", "Building", "Nature", "Color", "Light", "Recent"};

	private static EditorScreen instance;
	private static List<BlockState> allBlocks;
	private static final List<BlockState> RECENT = new ArrayList<>();

	private final List<Hit> hits = new ArrayList<>();
	private boolean interactive;
	private String search = "";
	private boolean searchFocused;
	private int category;
	private List<BlockState> shown = new ArrayList<>();
	private String shownFor;
	private float scroll;
	private float rulesScroll;
	private boolean panelOpen = true;
	private boolean confirmClear;
	private final boolean[] collapsed = new boolean[4];
	// Last layout, for input.
	private float viewX;
	private float viewY;
	private float viewW;
	private float viewH;
	private float gridX;
	private float gridY;
	private float gridW;
	private float gridH;
	private float rulesX;
	private float rulesY;
	private float rulesW;
	private float rulesH;
	private float panelX;
	private float panelY;
	private float panelH;
	private float panelScroll;
	private float panelContent;

	private EditorScreen() {
		super(Component.literal("World editor"));
	}

	public static EditorScreen get() {
		if (instance == null) {
			instance = new EditorScreen();
		}
		return instance;
	}

	/** The workspace is open and nothing is being typed: WASD flies the camera. */
	public static boolean flyable(Screen screen) {
		return screen instanceof EditorScreen editor && !editor.searchFocused;
	}

	public static boolean ctrlDown() {
		return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void onClose() {
		// Esc leaves the editor (and freecam) altogether.
		TextKeys.clear();
		searchFocused = false;
		WorldEditor.disable();
		if (minecraft != null && minecraft.gui.screen() == this) {
			minecraft.gui.setScreen(null);
		}
	}

	@Override
	public void removed() {
		WorldEditor.cursor(Double.NaN, Double.NaN);
		WorldEditor.viewportRelease();
	}

	/** Draws the workspace without taking input (while looking around or with chat open). */
	public void drawStatic(GuiGraphicsExtractor graphics) {
		width = graphics.guiWidth();
		height = graphics.guiHeight();
		interactive = false;
		hits.clear();
		draw(graphics, -1000, -1000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		interactive = true;
		hits.clear();
		draw(graphics, mouseX, mouseY);
		boolean inView = GuiDraw.hovered(mouseX, mouseY, viewX, viewY, viewW, viewH);
		WorldEditor.cursor(inView ? mouseX / (double) Math.max(1, width) : Double.NaN, inView ? mouseY / (double) Math.max(1, height) : Double.NaN);
	}

	private void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		float bodyTop = TOP_H;
		float browserTop = height - STATUS_H - BROWSER_H;
		viewX = SHELF_W;
		viewY = bodyTop;
		viewW = width - SHELF_W - (panelOpen ? PANEL_W : 0);
		viewH = browserTop - bodyTop;

		drawHeader(graphics, font, mouseX, mouseY);
		drawShelf(graphics, font, mouseX, mouseY, 0, bodyTop, SHELF_W, browserTop - bodyTop);
		drawViewportOverlay(graphics, font);
		if (panelOpen) {
			drawPanel(graphics, font, mouseX, mouseY, width - PANEL_W, bodyTop, PANEL_W, browserTop - bodyTop);
		}
		drawBrowser(graphics, font, mouseX, mouseY, 0, browserTop, width, BROWSER_H);
		drawStatus(graphics, font, 0, height - STATUS_H, width);
	}

	// ------------------------------------------------------------------ header

	private void drawHeader(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		GuiDraw.fill(graphics, 0, 0, width, TOP_H, HEADER);
		GuiDraw.fill(graphics, 0, TOP_H - 1, width, 1, BORDER);
		float x = 4;
		GuiDraw.rounded(graphics, x + 1, 4, 7, 7, 1.5f, ORANGE);
		x += 12;
		GuiDraw.small(graphics, font, "Stray Editor", x, 3f, TEXT);
		x += GuiDraw.smallWidth(font, "Stray Editor") + 10;
		x = menuButton(graphics, font, mouseX, mouseY, x, "Undo", WorldEditor.canUndo(), WorldEditor::undo);
		x = menuButton(graphics, font, mouseX, mouseY, x, "Redo", WorldEditor.canRedo(), WorldEditor::redo);
		x = menuButton(graphics, font, mouseX, mouseY, x, StrayConfig.get().editorShowEdits ? "Hide edits" : "Show edits", true,
			() -> GhostBlocks.setShown(!StrayConfig.get().editorShowEdits));

		String area = EditArea.label(GhostBlocks.areaKey());
		float aw = GuiDraw.smallWidth(font, area) + 14;
		float ax = Math.max(x + 6, (width - aw) / 2f);
		GuiDraw.rounded(graphics, ax, 2, aw, TOP_H - 4, 3, FIELD);
		GuiDraw.small(graphics, font, area, ax + 7, 3f, GhostBlocks.ready() ? TEXT : DIM);

		float rx = width - 4;
		String exit = "Exit";
		float ew = GuiDraw.smallWidth(font, exit) + 10;
		rx -= ew;
		widget(graphics, font, mouseX, mouseY, rx, 2, ew, TOP_H - 4, exit, false, RED, WorldEditor::disable);
		String speed = String.format(Locale.ROOT, "Fly %.2f", Freecam.speed());
		float sw = GuiDraw.smallWidth(font, speed) + 30;
		rx -= sw + 6;
		if (rx > ax + aw + 4) {
			GuiDraw.rounded(graphics, rx, 2, sw, TOP_H - 4, 3, WIDGET_DARK);
			arrowButton(graphics, font, mouseX, mouseY, rx, 2, 11, TOP_H - 4, "<", () -> speed(-1));
			GuiDraw.small(graphics, font, speed, rx + 15, 3f, TEXT);
			arrowButton(graphics, font, mouseX, mouseY, rx + sw - 11, 2, 11, TOP_H - 4, ">", () -> speed(1));
		}
	}

	private static void speed(int dir) {
		StrayConfig config = StrayConfig.get();
		config.freecamSpeed = (float) Mth.clamp(Freecam.speed() * (dir > 0 ? 1.25 : 0.8), 0.05, 8.0);
		config.save();
	}

	private float menuButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, String label, boolean enabled, Runnable click) {
		float w = GuiDraw.smallWidth(font, label) + 10;
		boolean hover = enabled && GuiDraw.hovered(mouseX, mouseY, x, 1, w, TOP_H - 2);
		if (hover) {
			GuiDraw.rounded(graphics, x, 2, w, TOP_H - 4, 3, WIDGET);
		}
		GuiDraw.small(graphics, font, label, x + 5, 3f, enabled ? TEXT : FAINT);
		if (enabled) {
			hits.add(new Hit(x, 1, w, TOP_H - 2, click));
		}
		return x + w + 2;
	}

	private void arrowButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h, String label, Runnable click) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, h);
		if (hover) {
			GuiDraw.rounded(graphics, x, y, w, h, 3, WIDGET_HOVER);
		}
		GuiDraw.small(graphics, font, label, x + (w - GuiDraw.smallWidth(font, label)) / 2f, GuiDraw.middle(y, h), TEXT);
		hits.add(new Hit(x, y, w, h, click));
	}

	// ------------------------------------------------------------------ tool shelf

	private void drawShelf(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h) {
		GuiDraw.fill(graphics, x, y, w, h, REGION);
		GuiDraw.fill(graphics, x + w - 1, y, 1, h, BORDER);
		float size = w - 6;
		float by = y + 4;
		String tip = null;
		float tipY = 0;
		for (WorldEditor.Tool tool : WorldEditor.Tool.values()) {
			boolean on = WorldEditor.tool() == tool;
			boolean hover = GuiDraw.hovered(mouseX, mouseY, x + 3, by, size, size);
			GuiDraw.rounded(graphics, x + 3, by, size, size, 3, on ? (hover ? ACTIVE_HOVER : ACTIVE) : hover ? WIDGET : WIDGET_DARK);
			icon(graphics, tool, x + 3 + size / 2f, by + size / 2f, on);
			hits.add(new Hit(x + 3, by, size, size, () -> WorldEditor.setTool(tool)));
			if (hover) {
				tip = tool.label + "  " + tool.keyLabel;
				tipY = by + 3;
			}
			by += size + 2;
			if (tool == WorldEditor.Tool.WAND) {
				GuiDraw.fill(graphics, x + 5, by + 1, size - 4, 1, BORDER);
				by += 4;
			}
		}
		if (tip != null) {
			tooltip(graphics, font, tip, x + w + 3, tipY);
		}
	}

	/** Small drawn icons, so the shelf reads like Blender's toolbar. */
	private static void icon(GuiGraphicsExtractor graphics, WorldEditor.Tool tool, float cx, float cy, boolean on) {
		int ink = on ? 0xFFFFFFFF : 0xFFD0D0D0;
		switch (tool) {
			case BUILD -> {
				GuiDraw.border(graphics, cx - 5, cy - 3, 8, 8, ink, 1f);
				GuiDraw.fill(graphics, cx + 2, cy - 7, 1, 5, ink);
				GuiDraw.fill(graphics, cx, cy - 5, 5, 1, ink);
			}
			case SELECT -> {
				for (int i = 0; i < 4; i++) {
					GuiDraw.fill(graphics, cx - 6 + i * 3, cy - 6, 2, 1, ink);
					GuiDraw.fill(graphics, cx - 6 + i * 3, cy + 5, 2, 1, ink);
					GuiDraw.fill(graphics, cx - 6, cy - 6 + i * 3, 1, 2, ink);
					GuiDraw.fill(graphics, cx + 5, cy - 6 + i * 3, 1, 2, ink);
				}
			}
			case PAINT -> {
				GuiDraw.circle(graphics, cx, cy, 5.5f, ink);
				GuiDraw.circle(graphics, cx, cy, 4f, on ? ACTIVE : WIDGET_DARK);
				GuiDraw.circle(graphics, cx, cy, 1.5f, ink);
			}
			case SWAP -> {
				GuiDraw.border(graphics, cx - 6, cy - 6, 8, 8, ink, 1f);
				GuiDraw.fill(graphics, cx - 2, cy - 2, 8, 8, ink);
			}
			case WAND -> {
				GuiDraw.stroke(graphics, cx - 5, cy + 5, cx + 2, cy - 2, 1.6f, ink);
				GuiDraw.fill(graphics, cx + 2, cy - 7, 1, 5, ink);
				GuiDraw.fill(graphics, cx, cy - 5, 5, 1, ink);
				GuiDraw.fill(graphics, cx + 4, cy - 1, 1, 1, ink);
				GuiDraw.fill(graphics, cx - 2, cy - 6, 1, 1, ink);
			}
			case REPLACE -> {
				GuiDraw.fill(graphics, cx - 6, cy - 5, 5, 5, ink);
				GuiDraw.border(graphics, cx + 1, cy, 5, 5, ink, 1f);
				GuiDraw.stroke(graphics, cx - 2, cy + 4, cx + 1, cy + 4, 1f, ink);
				GuiDraw.stroke(graphics, cx + 3, cy - 4, cx + 3, cy - 1, 1f, ink);
			}
		}
	}

	// ------------------------------------------------------------------ viewport overlay

	private void drawViewportOverlay(GuiGraphicsExtractor graphics, Font font) {
		WorldEditor.Tool tool = WorldEditor.tool();
		GuiDraw.small(graphics, font, "Freecam Perspective", viewX + 6, viewY + 5, 0xE0FFFFFF);
		String sub = "(" + tool.label + ")";
		if (!StrayConfig.get().editorShowEdits) {
			sub += "  edits hidden";
		} else if (!GhostBlocks.ready()) {
			sub += "  finding out where you are";
		}
		GuiDraw.small(graphics, font, sub, viewX + 6, viewY + 14, 0xB0FFFFFF);
		var hover = WorldEditor.hover();
		if (hover != null && !WorldEditor.looking()) {
			var pos = hover.getBlockPos();
			String info = WorldEditor.name(GhostBlocks.shown(pos)) + "  " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
			GuiDraw.small(graphics, font, info, viewX + 6, viewY + viewH - 10, 0xC0FFFFFF);
		}
		String message = WorldEditor.status();
		if (!message.isEmpty()) {
			float mw = GuiDraw.smallWidth(font, message) + 12;
			float mx = viewX + (viewW - mw) / 2f;
			float my = viewY + viewH - 24;
			GuiDraw.rounded(graphics, mx, my, mw, 12, 3, 0xE0202020);
			GuiDraw.small(graphics, font, message, mx + 6, my + 1.5f, TEXT);
		}
		drawAxes(graphics, font, viewX + viewW - 20, viewY + 20, 12);
	}

	/** The navigation gizmo in the corner: world axes as seen from the camera. */
	private static void drawAxes(GuiGraphicsExtractor graphics, Font font, float cx, float cy, float r) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		Vector3fc forward = camera.forwardVector();
		GuiDraw.circle(graphics, cx, cy, r + 5, 0x30FFFFFF);
		float[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {-1, 0, 0}, {0, -1, 0}, {0, 0, -1}};
		int[] colors = {0xFFFF3352, 0xFF8BDC00, 0xFF2890FF};
		String[] names = {"X", "Y", "Z"};
		Integer[] order = {0, 1, 2, 3, 4, 5};
		// Far ends first so the near ones draw on top.
		Arrays.sort(order, (a, b) -> Float.compare(dot(axes[b], forward), dot(axes[a], forward)));
		for (int i : order) {
			float[] axis = axes[i];
			float sx = cx - dot(axis, left) * r;
			float sy = cy - dot(axis, up) * r;
			int color = colors[i % 3];
			if (i < 3) {
				GuiDraw.stroke(graphics, cx, cy, sx, sy, 1.5f, color);
				GuiDraw.circle(graphics, sx, sy, 3.5f, color);
				GuiDraw.small(graphics, font, names[i], sx - GuiDraw.smallWidth(font, names[i]) / 2f + 0.5f, sy - 4.5f, 0xFF000000);
			} else {
				GuiDraw.circle(graphics, sx, sy, 3f, (color & 0xFFFFFF) | 0x70000000);
			}
		}
	}

	private static float dot(float[] axis, Vector3fc v) {
		return axis[0] * v.x() + axis[1] * v.y() + axis[2] * v.z();
	}

	// ------------------------------------------------------------------ properties panel

	private void drawPanel(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h) {
		GuiDraw.fill(graphics, x, y, w, h, REGION);
		GuiDraw.fill(graphics, x, y, 1, h, BORDER);
		boolean clipped = GuiDraw.scissor(graphics, x, y, w, h);
		panelX = x;
		panelY = y;
		panelH = h;
		panelScroll = Mth.clamp(panelScroll, 0, Math.max(0, panelContent - h));
		float ix = x + 6;
		float iw = w - 12;
		float cy = y + 4 - panelScroll;
		WorldEditor.Tool tool = WorldEditor.tool();
		cy = section(graphics, font, mouseX, mouseY, 0, "Tool: " + tool.label, x, cy, w);
		if (!collapsed[0]) {
			cy = toolOptions(graphics, font, mouseX, mouseY, tool, ix, cy, iw);
		}
		if (tool == WorldEditor.Tool.SELECT || tool == WorldEditor.Tool.WAND || WorldEditor.hasSelection()) {
			cy = section(graphics, font, mouseX, mouseY, 1, "Selection", x, cy, w);
			if (!collapsed[1]) {
				cy = selectionOptions(graphics, font, mouseX, mouseY, ix, cy, iw);
			}
		}
		cy = section(graphics, font, mouseX, mouseY, 2, "Replace rules (" + GhostBlocks.rules().size() + ")", x, cy, w);
		if (!collapsed[2]) {
			float rh = Math.max(20, Math.min(GhostBlocks.rules().size() * 18 + 2, 92));
			cy = rules(graphics, font, mouseX, mouseY, ix, cy, iw, rh);
		}
		cy = section(graphics, font, mouseX, mouseY, 3, "This place", x, cy, w);
		if (!collapsed[3]) {
			label(graphics, font, "Blocks placed", String.valueOf(GhostBlocks.placedCount()), ix, cy, iw);
			cy += 11;
			checkbox(graphics, font, mouseX, mouseY, ix, cy, iw, "Show edits", StrayConfig.get().editorShowEdits, GhostBlocks::setShown);
			cy += 13;
			widget(graphics, font, mouseX, mouseY, ix, cy, iw, 13, confirmClear ? "Click again to clear all" : "Clear all edits here", false, RED, () -> {
				if (confirmClear) {
					GhostBlocks.clearArea();
					WorldEditor.forgetHistory();
					WorldEditor.say("Cleared every edit here");
					confirmClear = false;
				} else {
					confirmClear = true;
				}
			});
			cy += 17;
		}
		panelContent = cy + panelScroll - y;
		if (panelContent > h) {
			// Scroll bar, Blender-thin.
			float barH = Math.max(12, h * h / panelContent);
			float barY = y + (h - barH) * (panelScroll / Math.max(1, panelContent - h));
			GuiDraw.rounded(graphics, x + w - 4, barY + 1, 2, barH - 2, 1, 0x80FFFFFF);
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
	}

	/** Blender-style collapsible panel header. */
	private float section(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int index, String title, float x, float y, float w) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x + 3, y, w - 6, 12);
		GuiDraw.rounded(graphics, x + 3, y, w - 6, 12, 3, hover ? 0xFF404040 : 0xFF383838);
		// Disclosure triangle.
		float tx = x + 9;
		float ty = y + 6;
		if (collapsed[index]) {
			for (int i = 0; i < 3; i++) {
				GuiDraw.fill(graphics, tx - 1 + i, ty - 3 + i, 1, 6 - i * 2, DIM);
			}
		} else {
			for (int i = 0; i < 3; i++) {
				GuiDraw.fill(graphics, tx - 3 + i, ty - 1 + i, 6 - i * 2, 1, DIM);
			}
		}
		GuiDraw.small(graphics, font, title, x + 15, y + 1.5f, TEXT);
		hits.add(new Hit(x + 3, y, w - 6, 12, () -> collapsed[index] = !collapsed[index]));
		return y + 15;
	}

	private float toolOptions(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, WorldEditor.Tool tool, float x, float y, float w) {
		switch (tool) {
			case BUILD -> {
				y = keyLine(graphics, font, "Place", "LMB", x, y, w);
				y = keyLine(graphics, font, "Remove", "Ctrl LMB", x, y, w);
				y = keyLine(graphics, font, "Delete hovered", "X", x, y, w);
				y = keyLine(graphics, font, "Pick block", "MMB", x, y, w);
			}
			case SWAP -> {
				y = keyLine(graphics, font, "Replace block", "LMB", x, y, w);
				y = keyLine(graphics, font, "Show real block", "Ctrl LMB", x, y, w);
				y = keyLine(graphics, font, "Pick block", "MMB", x, y, w);
				y = wrapText(graphics, font, "The block under the cursor becomes the palette block. Hold to drag across blocks.", x, y + 1, w, DIM);
			}
			case SELECT -> {
				y = keyLine(graphics, font, "Drag a box", "LMB", x, y, w);
				y = keyLine(graphics, font, "Extend", "Shift LMB", x, y, w);
				y = keyLine(graphics, font, "Deselect", "C", x, y, w);
			}
			case WAND -> {
				y = keyLine(graphics, font, "Connected", "LMB", x, y, w);
				y = keyLine(graphics, font, "Every one", "Ctrl LMB", x, y, w);
				y = keyLine(graphics, font, "Add to it", "Shift LMB", x, y, w);
				y = wrapText(graphics, font, "Selects blocks of the clicked type. Then Fill, Replace or Delete them.", x, y + 1, w, DIM);
			}
			case PAINT -> {
				GuiDraw.small(graphics, font, "Brush", x, y + 1.5f, DIM);
				float fw = w - 34;
				float fx = x + 32;
				GuiDraw.rounded(graphics, fx, y, fw, 12, 3, FIELD);
				float t = WorldEditor.brushSize() / (float) WorldEditor.MAX_BRUSH;
				GuiDraw.rounded(graphics, fx, y, Math.max(6, fw * (0.08f + 0.92f * t)), 12, 3, ACTIVE);
				String size = String.valueOf(WorldEditor.brushSize() * 2 + 1);
				GuiDraw.small(graphics, font, size, fx + (fw - GuiDraw.smallWidth(font, size)) / 2f, y + 1.5f, TEXT);
				arrowButton(graphics, font, mouseX, mouseY, fx, y, 10, 12, "<", () -> WorldEditor.brush(-1));
				arrowButton(graphics, font, mouseX, mouseY, fx + fw - 10, y, 10, 12, ">", () -> WorldEditor.brush(1));
				y += 16;
				y = keyLine(graphics, font, "Paint", "LMB", x, y, w);
				y = keyLine(graphics, font, "Erase", "Ctrl LMB", x, y, w);
				y = keyLine(graphics, font, "Size", "[ ]", x, y, w);
			}
			case REPLACE -> {
				y = keyLine(graphics, font, "Replace type", "LMB", x, y, w);
				y = keyLine(graphics, font, "Undo a rule", "Ctrl LMB", x, y, w);
				y = wrapText(graphics, font, "Every block of the clicked type becomes the palette block, also in chunks that load later.", x, y + 1, w, DIM);
			}
		}
		return y + 4;
	}

	private float selectionOptions(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w) {
		label(graphics, font, "Size", WorldEditor.hasSelection() ? WorldEditor.selectionText() : "none", x, y, w);
		y += 12;
		float half = (w - 3) / 2f;
		widget(graphics, font, mouseX, mouseY, x, y, half, 13, "Fill  F", false, TEXT, () -> WorldEditor.fillSelection(WorldEditor.palette()));
		widget(graphics, font, mouseX, mouseY, x + half + 3, y, half, 13, "Replace  R", false, TEXT, WorldEditor::replaceInSelection);
		y += 15;
		widget(graphics, font, mouseX, mouseY, x, y, half, 13, "Delete  X", false, TEXT, () -> WorldEditor.fillSelection(Blocks.AIR.defaultBlockState()));
		widget(graphics, font, mouseX, mouseY, x + half + 3, y, half, 13, "Restore", false, TEXT, WorldEditor::restoreSelection);
		return y + 18;
	}

	private float rules(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h) {
		List<GhostBlocks.Rule> rules = GhostBlocks.rules();
		rulesX = x;
		rulesY = y;
		rulesW = w;
		rulesH = h;
		GuiDraw.rounded(graphics, x, y, w, h, 3, FIELD);
		if (rules.isEmpty()) {
			GuiDraw.small(graphics, font, "None yet: use Replace all", x + 4, GuiDraw.middle(y, h), FAINT);
			return y + h + 4;
		}
		float rowH = 18;
		rulesScroll = Mth.clamp(rulesScroll, 0, Math.max(0, rules.size() * rowH + 2 - h));
		boolean clipped = GuiDraw.scissor(graphics, x, y, w, h);
		for (int i = 0; i < rules.size(); i++) {
			GhostBlocks.Rule rule = rules.get(i);
			float ry = y + 1 + i * rowH - rulesScroll;
			if (ry + rowH < y || ry > y + h) {
				continue;
			}
			boolean hover = GuiDraw.hovered(mouseX, mouseY, x, ry, w, rowH);
			if (hover) {
				GuiDraw.fill(graphics, x, ry, w, rowH, 0x20FFFFFF);
			}
			graphics.item(EditorHud.icon(rule.from.defaultBlockState()), (int) x + 1, (int) ry + 1);
			GuiDraw.small(graphics, font, "→", x + 19, ry + 4.5f, DIM);
			graphics.item(EditorHud.icon(rule.to), (int) x + 26, (int) ry + 1);
			String name = clip(font, EditorHud.label(rule.to), (int) (w - 56));
			GuiDraw.small(graphics, font, name, x + 44, ry + 4.5f, hover ? TEXT : DIM);
			boolean del = GuiDraw.hovered(mouseX, mouseY, x + w - 12, ry, 12, rowH);
			GuiDraw.small(graphics, font, "×", x + w - 9, ry + 4.5f, del ? RED : FAINT);
			Block from = rule.from;
			hits.add(new Hit(x + w - 12, ry, 12, rowH, () -> WorldEditor.removeRule(from)));
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
		return y + h + 4;
	}

	private float keyLine(GuiGraphicsExtractor graphics, Font font, String what, String keys, float x, float y, float w) {
		GuiDraw.small(graphics, font, what, x, y, DIM);
		float kw = GuiDraw.smallWidth(font, keys) + 6;
		GuiDraw.rounded(graphics, x + w - kw, y - 0.5f, kw, 10, 2, WIDGET_DARK);
		GuiDraw.small(graphics, font, keys, x + w - kw + 3, y, TEXT);
		return y + 12;
	}

	private void label(GuiGraphicsExtractor graphics, Font font, String key, String value, float x, float y, float w) {
		GuiDraw.small(graphics, font, key, x, y, DIM);
		GuiDraw.small(graphics, font, value, x + w - GuiDraw.smallWidth(font, value), y, TEXT);
	}

	private void checkbox(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, String label, boolean value, java.util.function.Consumer<Boolean> setter) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y - 1, w, 11);
		GuiDraw.rounded(graphics, x, y - 1, 10, 10, 2, value ? (hover ? ACTIVE_HOVER : ACTIVE) : hover ? WIDGET_HOVER : WIDGET);
		if (value) {
			GuiDraw.stroke(graphics, x + 2.5f, y + 4, x + 4.5f, y + 6, 1.4f, 0xFFFFFFFF);
			GuiDraw.stroke(graphics, x + 4.5f, y + 6, x + 8f, y + 1.5f, 1.4f, 0xFFFFFFFF);
		}
		GuiDraw.small(graphics, font, label, x + 14, y - 0.5f, TEXT);
		hits.add(new Hit(x, y - 1, w, 11, () -> setter.accept(!value)));
	}

	private void widget(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h, String label, boolean on, int ink, Runnable click) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, h);
		GuiDraw.rounded(graphics, x, y, w, h, 3, on ? (hover ? ACTIVE_HOVER : ACTIVE) : hover ? WIDGET_HOVER : WIDGET);
		String text = clip(font, label, (int) w - 4);
		GuiDraw.small(graphics, font, text, x + (w - GuiDraw.smallWidth(font, text)) / 2f, GuiDraw.middle(y, h), ink);
		hits.add(new Hit(x, y, w, h, click));
	}

	private float wrapText(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float w, int color) {
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (GuiDraw.smallWidth(font, next) > w && !line.isEmpty()) {
				GuiDraw.small(graphics, font, line.toString(), x, y, color);
				y += 9;
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (!line.isEmpty()) {
			GuiDraw.small(graphics, font, line.toString(), x, y, color);
			y += 9;
		}
		return y;
	}

	private void tooltip(GuiGraphicsExtractor graphics, Font font, String text, float x, float y) {
		if (!interactive) {
			return;
		}
		float w = GuiDraw.smallWidth(font, text) + 8;
		float tx = Math.min(x, width - w - 2);
		GuiDraw.rounded(graphics, tx, y, w, 12, 3, 0xF0181818);
		GuiDraw.small(graphics, font, text, tx + 4, y + 1.5f, TEXT);
	}

	// ------------------------------------------------------------------ block browser

	private void drawBrowser(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h) {
		GuiDraw.fill(graphics, x, y, w, h, REGION);
		GuiDraw.fill(graphics, x, y, w, 1, BORDER);
		float headH = 22;
		GuiDraw.fill(graphics, x, y + 1, w, headH, HEADER);
		float px = x + 4;
		GuiDraw.small(graphics, font, "Palette", px, y + 7.5f, DIM);
		px += GuiDraw.smallWidth(font, "Palette") + 6;
		String tip = null;
		float tipX = 0;
		for (int i = 0; i < WorldEditor.SLOTS; i++) {
			float sx = px + i * (CELL + 1);
			float sy = y + 3;
			boolean on = i == WorldEditor.slot();
			boolean hover = GuiDraw.hovered(mouseX, mouseY, sx, sy, CELL, CELL);
			GuiDraw.rounded(graphics, sx, sy, CELL, CELL, 3, on ? ACTIVE : hover ? WIDGET_HOVER : WIDGET_DARK);
			graphics.item(EditorHud.icon(WorldEditor.paletteAt(i)), (int) sx + 1, (int) sy + 1);
			int index = i;
			hits.add(new Hit(sx, sy, CELL, CELL, () -> WorldEditor.setSlot(index)));
			if (hover) {
				tip = (i + 1) + "  " + EditorHud.label(WorldEditor.paletteAt(i));
				tipX = sx;
			}
		}
		float paletteEnd = px + WorldEditor.SLOTS * (CELL + 1) + 6;
		String current = EditorHud.label(WorldEditor.palette());
		float searchW = Math.min(120, w - paletteEnd - 8);
		float searchX = x + w - searchW - 4;
		if (searchX - paletteEnd > 30) {
			GuiDraw.small(graphics, font, clip(font, current, (int) (searchX - paletteEnd - 6)), paletteEnd, y + 7.5f, TEXT);
		}
		drawSearch(graphics, font, mouseX, mouseY, searchX, y + 5, searchW, 13);

		// Catalog list on the left, like Blender's asset browser.
		float catW = 54;
		float bodyY = y + headH + 2;
		float bodyH = h - headH - 3;
		float cy = bodyY + 1;
		for (int i = 0; i < CATEGORIES.length; i++) {
			boolean on = i == category;
			boolean hover = GuiDraw.hovered(mouseX, mouseY, x + 3, cy, catW - 6, 8.5f);
			if (on || hover) {
				GuiDraw.rounded(graphics, x + 3, cy, catW - 6, 8.5f, 2, on ? ACTIVE : WIDGET_DARK);
			}
			GuiDraw.small(graphics, font, CATEGORIES[i], x + 6, GuiDraw.middle(cy, 8.5f), on ? TEXT : DIM);
			int index = i;
			hits.add(new Hit(x + 3, cy, catW - 6, 8.5f, () -> {
				category = index;
				scroll = 0;
			}));
			cy += 8.5f;
		}
		GuiDraw.fill(graphics, x + catW, bodyY, 1, bodyH, BORDER);
		gridX = x + catW + 3;
		gridY = bodyY;
		gridW = w - catW - 6;
		gridH = bodyH;
		drawGrid(graphics, font, mouseX, mouseY);
		if (tip != null) {
			tooltip(graphics, font, tip, tipX, y - 13);
		}
	}

	private void drawSearch(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, h);
		GuiDraw.rounded(graphics, x, y, w, h, h / 2f, searchFocused ? 0xFF282828 : hover ? 0xFF262626 : FIELD);
		if (searchFocused) {
			GuiDraw.border(graphics, x, y, w, h, ACTIVE, 1f);
			TextKeys.mark(graphics, SEARCH_KEY, x, y, w, h);
		}
		GuiDraw.circle(graphics, x + 7, y + h / 2f - 0.5f, 2.5f, DIM);
		GuiDraw.circle(graphics, x + 7, y + h / 2f - 0.5f, 1.5f, searchFocused ? 0xFF282828 : FIELD);
		GuiDraw.stroke(graphics, x + 8.5f, y + h / 2f + 1, x + 10.5f, y + h / 2f + 3, 1f, DIM);
		boolean caret = searchFocused && !TextKeys.isSelected(SEARCH_KEY) && (System.currentTimeMillis() / 530) % 2 == 0;
		String text = search.isEmpty() && !searchFocused ? "Search blocks  Ctrl F" : search + (caret ? "|" : "");
		GuiDraw.small(graphics, font, clip(font, text, (int) w - 18), x + 14, GuiDraw.middle(y, h), search.isEmpty() && !searchFocused ? FAINT : TEXT);
		hits.add(new Hit(x, y, w, h, () -> searchFocused = true));
	}

	private void drawGrid(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		List<BlockState> blocks = filtered();
		int cols = Math.max(1, (int) ((gridW - 2) / (CELL + 1)));
		int rows = (blocks.size() + cols - 1) / cols;
		float maxScroll = Math.max(0, rows * (CELL + 1) + 2 - gridH);
		scroll = Mth.clamp(scroll, 0, maxScroll);
		if (blocks.isEmpty()) {
			GuiDraw.small(graphics, font, category == 5 ? "Blocks you choose show up here" : "Nothing matches \"" + search + "\"", gridX + 4, gridY + 6, FAINT);
			return;
		}
		boolean clipped = GuiDraw.scissor(graphics, gridX, gridY, gridW, gridH);
		int first = (int) (scroll / (CELL + 1));
		int last = Math.min(rows - 1, (int) ((scroll + gridH) / (CELL + 1)) + 1);
		Block current = WorldEditor.palette().getBlock();
		BlockState hovered = null;
		float hoverX = 0;
		float hoverY = 0;
		boolean inGrid = GuiDraw.hovered(mouseX, mouseY, gridX, gridY, gridW, gridH);
		for (int row = first; row <= last; row++) {
			for (int col = 0; col < cols; col++) {
				int index = row * cols + col;
				if (index >= blocks.size()) {
					break;
				}
				BlockState state = blocks.get(index);
				float bx = gridX + 1 + col * (CELL + 1);
				float by = gridY + 2 + row * (CELL + 1) - scroll;
				boolean hover = inGrid && GuiDraw.hovered(mouseX, mouseY, bx, by, CELL, CELL);
				if (state.getBlock() == current) {
					GuiDraw.rounded(graphics, bx, by, CELL, CELL, 3, ACTIVE);
				} else if (hover) {
					GuiDraw.rounded(graphics, bx, by, CELL, CELL, 3, WIDGET);
				}
				if (hover) {
					hovered = state;
					hoverX = bx;
					hoverY = by;
				}
				graphics.item(EditorHud.icon(state), (int) bx + 1, (int) by + 1);
				if (inGrid) {
					hits.add(new Hit(bx, by, CELL, CELL, () -> choose(state)));
				}
			}
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
		if (hovered != null) {
			tooltip(graphics, font, EditorHud.label(hovered), hoverX, hoverY - 13);
		}
	}

	private void choose(BlockState state) {
		WorldEditor.setPalette(WorldEditor.slot(), state);
		RECENT.remove(state);
		RECENT.add(0, state);
		while (RECENT.size() > 40) {
			RECENT.remove(RECENT.size() - 1);
		}
		if (category == 5) {
			shownFor = null;
		}
	}

	private List<BlockState> filtered() {
		String query = search.toLowerCase(Locale.ROOT).trim();
		String key = query + "\u0000" + category + "\u0000" + (category == 5 ? RECENT.hashCode() : 0);
		if (key.equals(shownFor)) {
			return shown;
		}
		shownFor = key;
		List<BlockState> out = new ArrayList<>();
		for (BlockState state : category == 5 ? RECENT : blocks()) {
			String id = WorldEditor.id(state.getBlock());
			if (!query.isEmpty() && !id.replace('_', ' ').contains(query) && !id.contains(query)
				&& !WorldEditor.name(state).toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			if (category != 0 && category != 5 && category(id) != category) {
				continue;
			}
			out.add(state);
		}
		shown = out;
		return out;
	}

	private static List<BlockState> blocks() {
		if (allBlocks == null) {
			List<BlockState> out = new ArrayList<>();
			out.add(Blocks.AIR.defaultBlockState());
			out.add(Blocks.WATER.defaultBlockState());
			out.add(Blocks.LAVA.defaultBlockState());
			for (Block block : BuiltInRegistries.BLOCK) {
				if (block == Blocks.AIR || block == Blocks.WATER || block == Blocks.LAVA || block.asItem() == Items.AIR) {
					continue;
				}
				out.add(block.defaultBlockState());
			}
			allBlocks = out;
		}
		return allBlocks;
	}

	private static final String[] NATURE = {"log", "leaves", "dirt", "grass", "sand", "gravel", "_ore", "flower", "mushroom", "coral", "ice",
		"snow", "moss", "clay", "netherrack", "end_stone", "sapling", "vine", "tulip", "fern", "bush", "root", "mud", "podzol", "mycelium",
		"basalt", "stem", "wart", "kelp", "lily", "cactus", "bamboo", "dripstone", "amethyst", "calcite", "tuff", "deepslate", "obsidian",
		"bedrock", "water", "lava", "pumpkin", "melon", "hay", "allium", "orchid", "daisy", "poppy", "dandelion", "rose", "lilac", "peony"};
	private static final String[] COLOR = {"wool", "concrete", "terracotta", "stained", "carpet", "_bed", "banner", "shulker", "candle"};
	private static final String[] LIGHT = {"lantern", "glowstone", "torch", "lamp", "froglight", "shroomlight", "campfire", "beacon", "end_rod",
		"sea_pickle", "glow_lichen", "light"};

	private static int category(String id) {
		if (has(id, LIGHT)) {
			return 4;
		}
		if (has(id, COLOR)) {
			return 3;
		}
		if (has(id, NATURE) && !id.contains("brick") && !id.contains("polished") && !id.contains("stairs") && !id.contains("slab")
			&& !id.contains("wall") && !id.contains("planks") && !id.contains("tiles")) {
			return 2;
		}
		return 1;
	}

	private static boolean has(String id, String[] parts) {
		for (String part : parts) {
			if (id.contains(part)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ status bar

	private void drawStatus(GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		GuiDraw.fill(graphics, x, y, w, STATUS_H, STATUS);
		String hint = switch (WorldEditor.tool()) {
			case BUILD -> "LMB place · Ctrl LMB remove · MMB pick · RMB look · WASD fly · Ctrl Z undo";
			case SELECT -> "LMB drag select · F fill · R replace · X delete · Alt X restore · C deselect";
			case SWAP -> "LMB replace hovered block · Ctrl LMB show the real block · hold to drag · MMB pick";
			case WAND -> "LMB connected blocks · Ctrl LMB every block of that type · Shift adds · F fill · R replace · X delete";
			case PAINT -> "LMB paint · Ctrl LMB erase · [ ] brush · RMB look · WASD fly";
			case REPLACE -> "LMB replace every block of that type · Ctrl LMB undo it · RMB look";
		};
		if (WorldEditor.looking()) {
			hint = "Looking around · WASD fly · Space/Shift up/down · let go of RMB to return";
		}
		String right = GhostBlocks.placedCount() + " blocks · " + GhostBlocks.rules().size() + " rules · Esc exits";
		float rw = GuiDraw.smallWidth(font, right);
		GuiDraw.small(graphics, font, clip(font, hint, (int) (w - rw - 16)), x + 4, GuiDraw.middle(y, STATUS_H), DIM);
		GuiDraw.small(graphics, font, right, x + w - rw - 4, GuiDraw.middle(y, STATUS_H), DIM);
	}

	private static String clip(Font font, String value, int maxWidth) {
		if (maxWidth <= 8 || GuiDraw.smallWidth(font, value) <= maxWidth) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && GuiDraw.smallWidth(font, trimmed + "..") > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	// ------------------------------------------------------------------ input

	private final TextKeys.Field searchField = new TextKeys.Field(SEARCH_KEY, () -> search, value -> {
		search = value;
		scroll = 0;
	}, 40);

	private boolean inViewport(double x, double y) {
		return GuiDraw.hovered(x, y, viewX, viewY, viewW, viewH);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double mx = event.x();
		double my = event.y();
		if (inViewport(mx, my)) {
			searchFocused = false;
			confirmClear = false;
			int which = MouseButtons.left(event) ? 0 : MouseButtons.right(event) ? 1 : MouseButtons.middle(event) ? 2 : -1;
			if (which >= 0) {
				WorldEditor.cursor(mx / Math.max(1, width), my / Math.max(1, height));
				WorldEditor.viewportPress(which, event.hasControlDown(), event.hasShiftDown());
			}
			return true;
		}
		if (!MouseButtons.left(event)) {
			return true;
		}
		boolean wasConfirm = confirmClear;
		boolean wasSearch = searchFocused;
		searchFocused = false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(mx, my)) {
				hit.click.run();
				if (wasConfirm && confirmClear) {
					confirmClear = false;
				}
				if (!searchFocused && wasSearch) {
					TextKeys.clear();
				}
				return true;
			}
		}
		confirmClear = false;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (MouseButtons.left(event)) {
			WorldEditor.viewportRelease();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0) {
			return true;
		}
		if (GuiDraw.hovered(mouseX, mouseY, gridX, gridY, gridW, gridH)) {
			scroll -= (float) scrollY * (CELL + 1) * 2;
			return true;
		}
		if (GuiDraw.hovered(mouseX, mouseY, rulesX, rulesY, rulesW, rulesH) && GhostBlocks.rules().size() * 18 + 2 > rulesH) {
			rulesScroll -= (float) scrollY * 18;
			return true;
		}
		if (panelOpen && GuiDraw.hovered(mouseX, mouseY, panelX, panelY, PANEL_W, panelH)) {
			panelScroll -= (float) scrollY * 16;
			return true;
		}
		if (inViewport(mouseX, mouseY)) {
			WorldEditor.viewportScroll(scrollY, ctrlDown());
			return true;
		}
		WorldEditor.setSlot(WorldEditor.slot() + (scrollY > 0 ? -1 : 1));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchFocused) {
			if (event.isEscape() || event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				searchFocused = false;
				TextKeys.clear();
				return true;
			}
			TextKeys.key(event, searchField);
			return true;
		}
		if (event.isEscape()) {
			onClose();
			return true;
		}
		if (event.key() == InputConstants.KEY_N && !event.hasControlDown()) {
			panelOpen = !panelOpen;
			return true;
		}
		if (event.hasControlDown() && event.key() == InputConstants.KEY_F) {
			searchFocused = true;
			return true;
		}
		WorldEditor.key(event.key(), event.hasControlDown(), event.hasShiftDown(), event.hasAltDown());
		// Movement keys and the rest need nothing here: Freecam reads them directly.
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!searchFocused || !event.isAllowedChatCharacter()) {
			return true;
		}
		String typed = event.codepointAsString();
		if (typed.isEmpty() || typed.charAt(0) < 32) {
			return true;
		}
		TextKeys.type(typed, searchField);
		return true;
	}

	private record Hit(float x, float y, float w, float h, Runnable click) {
		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
