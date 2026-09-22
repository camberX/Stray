package dev.stray.client.ui;

import dev.stray.client.config.EntityKind;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.config.UnloadState;
import dev.stray.client.render.ArrayListHud;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Column click GUI. Left click toggles a feature. Right click opens its settings.
 * Headers drag. Each column scrolls on its own.
 */
public final class ClickGui {
	static final int COL_W = 106;
	private static final int GAP = 2;
	private static final int HEADER = 16;
	private static final int ROW = 16;
	private static final int ROW_COLOR = 0xFF000000;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFFAAAAAA;
	private static final String[] ORDER = {"World", "Visuals", "Combat", "HUD", "Mining", "Farming", "Menus", "Player"};

	private static final Map<String, Column> columns = new LinkedHashMap<>();
	private static final List<Row> rows = new ArrayList<>();
	private static boolean placed;
	private static boolean lightInk;
	private static String expandedName;
	private static String dragging;
	private static float dragOffX;
	private static float dragOffY;
	private static double pointerX;
	private static double pointerY;
	private static float panelX;
	private static float panelY;
	private static float panelW;
	private static float panelH;
	private static boolean panelLive;

	private ClickGui() {
	}

	public static boolean lightInk() {
		return lightInk;
	}

	static void useLightInk(boolean value) {
		lightInk = value;
	}

	static void pointer(double x, double y) {
		pointerX = x;
		pointerY = y;
	}

	static void extract(StrayScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		syncColumns(screen);
		rows.clear();
		panelLive = false;
		for (String id : ORDER) {
			drawColumn(screen, graphics, font, mouseX, mouseY, columns.get(id));
		}
		if (expandedName != null) {
			drawSettings(screen, graphics, font, mouseX, mouseY);
		}
		screen.clickPicker(graphics, font);
		if (StrayConfig.get().arrayList) {
			ArrayListHud.draw(graphics, font, screen.width);
		}
	}

	static void rightClick(double x, double y) {
		if (panelLive && contains(x, y, panelX, panelY, panelW, panelH)) {
			return;
		}
		for (int i = rows.size() - 1; i >= 0; i--) {
			Row row = rows.get(i);
			if (!contains(x, y, row.x, row.y, row.w, row.h)) {
				continue;
			}
			if (row.mod.feature != null || row.mod.timeout || row.mod.menuStyle) {
				expandedName = row.mod.name.equals(expandedName) ? null : row.mod.name;
			}
			return;
		}
		expandedName = null;
	}

	static void collapse() {
		expandedName = null;
	}

	static boolean drag(double x, double y) {
		if (dragging == null) {
			return false;
		}
		Column column = columns.get(dragging);
		if (column == null) {
			dragging = null;
			return false;
		}
		column.x = (float) x - dragOffX;
		column.y = (float) y - dragOffY;
		return true;
	}

	static void endDrag() {
		if (dragging == null) {
			return;
		}
		dragging = null;
		saveColumns();
	}

	static boolean scroll(double x, double y, double wheel) {
		if (wheel == 0) {
			return false;
		}
		for (Column column : columns.values()) {
			if (contains(x, y, column.x, column.y, COL_W, column.height)) {
				column.scroll = Math.max(0f, column.scroll - (float) wheel * ROW);
				return true;
			}
		}
		return false;
	}

	public static List<String> enabledLabels() {
		List<String> labels = new ArrayList<>();
		for (Mod mod : modules()) {
			if (mod.list && mod.on.getAsBoolean()) {
				labels.add(mod.name);
			}
		}
		return labels;
	}

	public static int colorOf(String name) {
		int hash = name.hashCode();
		float hue = ((hash & 0xFFFF) % 360) / 360f;
		int rgb = hsb(hue, 0.62f, 1f);
		return 0xFF000000 | rgb;
	}

	private static void drawColumn(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, Column column) {
		List<Mod> mods = new ArrayList<>();
		for (Mod mod : modules()) {
			if (column.id.equals(mod.column)) {
				mods.add(mod);
			}
		}
		int x = Math.round(column.x);
		int top = Math.round(column.y);
		float content = mods.size() * ROW;
		float visible = Math.max(ROW, screen.height - top - HEADER);
		column.height = HEADER + Math.min(content, visible);
		float maxScroll = Math.max(0f, content - visible);
		column.scroll = Math.round(Mth.clamp(column.scroll, 0f, maxScroll));

		int accent = Theme.ACCENT;
		GuiDraw.fill(graphics, x, top, COL_W, HEADER, accent);
		String title = column.id;
		GuiDraw.text(graphics, font, title, textX(font, title, x, COL_W), textY(font, top, HEADER), TEXT, true);
		screen.clickHit(x, top, COL_W, HEADER, () -> beginDrag(column.id));

		float clipY = top + HEADER;
		boolean clipped = GuiDraw.scissor(graphics, x, clipY, COL_W, visible);
		float y = clipY - column.scroll;
		for (Mod mod : mods) {
			boolean shown = y + ROW > clipY && y < clipY + visible;
			if (shown) {
				boolean on = mod.on.getAsBoolean();
				int rowY = Math.round(y);
				GuiDraw.fill(graphics, x, rowY, COL_W, ROW, on ? accent : ROW_COLOR);
				String label = fit(font, mod.name, COL_W - 4);
				GuiDraw.text(graphics, font, label, textX(font, label, x, COL_W), textY(font, rowY, ROW), on ? TEXT : DIM, true);
				screen.clickHit(x, rowY, COL_W, ROW, () -> toggle(mod));
				rows.add(new Row(mod, x, rowY, COL_W, ROW));
			}
			y += ROW;
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private static void drawSettings(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Mod mod = null;
		for (Mod candidate : modules()) {
			if (candidate.name.equals(expandedName)) {
				mod = candidate;
				break;
			}
		}
		if (mod == null) {
			return;
		}
		Row anchor = null;
		for (int i = rows.size() - 1; i >= 0; i--) {
			if (rows.get(i).mod.name.equals(expandedName)) {
				anchor = rows.get(i);
				break;
			}
		}
		if (anchor == null) {
			return;
		}
		float rowH = screen.clickSettingRow();
		boolean plain = mod.menuStyle || mod.timeout;
		int settingRows = mod.menuStyle ? 2 : mod.timeout ? 6 : mod.feature.rows();
		panelW = plain ? COL_W : 196;
		panelH = plain ? HEADER + settingRows * ROW : HEADER + settingRows * rowH + 8;
		panelX = anchor.x + COL_W + 2;
		if (panelX + panelW > screen.width - 2) {
			panelX = anchor.x - panelW - 2;
		}
		panelY = anchor.y;
		if (panelY + panelH > screen.height - 2) {
			panelY = Math.max(2, screen.height - 2 - panelH);
		}
		panelLive = true;
		int px = Math.round(panelX);
		int py = Math.round(panelY);
		int pw = Math.round(panelW);
		GuiDraw.fill(graphics, px, py, pw, panelH, ROW_COLOR);
		GuiDraw.fill(graphics, px, py, pw, HEADER, Theme.ACCENT);
		String heading = fit(font, mod.name, pw - 4);
		GuiDraw.text(graphics, font, heading, textX(font, heading, px, pw), textY(font, py, HEADER), TEXT, true);
		screen.clickHit(panelX, panelY, panelW, panelH, () -> {
		});
		if (mod.menuStyle) {
			drawMenuStyle(screen, graphics, font, px, py + HEADER, pw);
			return;
		}
		if (mod.timeout) {
			drawTimeout(screen, graphics, font, px, py + HEADER, pw);
			return;
		}
		float innerX = panelX + 6;
		float innerY = panelY + HEADER + 4;
		float innerW = panelW - 12;
		screen.clickVisuals(mod.kind);
		screen.clickSettings(graphics, font, mouseX, mouseY, innerX, innerY, innerW, mod.feature);
	}

	private static void drawMenuStyle(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		boolean click = StrayConfig.get().clickGui;
		drawChoice(screen, graphics, font, x, y, w, "Click GUI", click, () -> setClickGui(true));
		drawChoice(screen, graphics, font, x, y + ROW, w, "Stray menu", !click, () -> setClickGui(false));
	}

	private static void drawChoice(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		String label,
		boolean on,
		Runnable pick
	) {
		GuiDraw.fill(graphics, x, y, w, ROW, on ? Theme.ACCENT : ROW_COLOR);
		GuiDraw.text(graphics, font, label, textX(font, label, x, w), textY(font, y, ROW), on ? TEXT : DIM, true);
		screen.clickHit(x, y, w, ROW, pick);
	}

	private static void setClickGui(boolean click) {
		StrayConfig.get().clickGui = click;
		UnloadState.markDirty();
	}

	private static void drawTimeout(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		int current = StrayConfig.get().noCursorResetTimeout;
		int[] choices = {50, 100, 150, 300, 500, 1000};
		for (int choice : choices) {
			boolean on = current == choice;
			String label = choice + " ms";
			GuiDraw.fill(graphics, x, y, w, ROW, on ? Theme.ACCENT : ROW_COLOR);
			GuiDraw.text(graphics, font, label, textX(font, label, x, w), textY(font, y, ROW), on ? TEXT : DIM, true);
			float hitY = y;
			screen.clickHit(x, hitY, w, ROW, () -> {
				StrayConfig.get().noCursorResetTimeout = choice;
				UnloadState.markDirty();
			});
			y += ROW;
		}
	}

	private static void beginDrag(String id) {
		Column column = columns.get(id);
		if (column == null) {
			return;
		}
		dragging = id;
		dragOffX = (float) pointerX - column.x;
		dragOffY = (float) pointerY - column.y;
	}

	private static void toggle(Mod mod) {
		mod.set.accept(!mod.on.getAsBoolean());
		UnloadState.markDirty();
	}

	private static void syncColumns(StrayScreen screen) {
		StrayConfig config = StrayConfig.get();
		if (config.clickColumns == null) {
			config.clickColumns = new ArrayList<>();
		}
		for (String id : ORDER) {
			columns.computeIfAbsent(id, Column::new);
		}
		if (!placed) {
			Map<String, StrayConfig.ClickColumnPos> saved = new LinkedHashMap<>();
			for (StrayConfig.ClickColumnPos pos : config.clickColumns) {
				if (pos != null && pos.id != null) {
					saved.put(pos.id, pos);
				}
			}
			int fit = Math.max(1, (screen.width - 2) / (COL_W + GAP));
			int bands = (ORDER.length + fit - 1) / fit;
			float band = Math.max(HEADER + ROW * 4, (screen.height - 8f) / bands);
			for (int i = 0; i < ORDER.length; i++) {
				Column column = columns.get(ORDER[i]);
				StrayConfig.ClickColumnPos pos = saved.get(column.id);
				if (pos != null) {
					column.x = pos.x;
					column.y = pos.y;
				} else {
					column.x = 2 + (i % fit) * (COL_W + GAP);
					column.y = (i / fit) * band;
				}
			}
			placed = true;
		}
		for (Column column : columns.values()) {
			column.x = Mth.clamp(column.x, 0, Math.max(0, screen.width - COL_W));
			column.y = Mth.clamp(column.y, 0, Math.max(0, screen.height - HEADER));
		}
	}

	private static void saveColumns() {
		StrayConfig config = StrayConfig.get();
		config.clickColumns = new ArrayList<>();
		for (String id : ORDER) {
			Column column = columns.get(id);
			if (column == null) {
				continue;
			}
			StrayConfig.ClickColumnPos pos = new StrayConfig.ClickColumnPos();
			pos.id = column.id;
			pos.x = column.x;
			pos.y = column.y;
			config.clickColumns.add(pos);
		}
		UnloadState.markDirty();
	}

	private static float textX(Font font, String label, float x, float w) {
		return x + (w - font.width(label)) / 2f;
	}

	private static float textY(Font font, float y, int h) {
		return y + (h - font.lineHeight) / 2f;
	}

	private static String fit(Font font, String label, int max) {
		if (font.width(label) <= max) {
			return label;
		}
		String trimmed = label;
		while (trimmed.length() > 1 && font.width(trimmed + ".") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + ".";
	}

	private static boolean contains(double x, double y, float rx, float ry, float rw, float rh) {
		return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
	}

	private static int hsb(float hue, float sat, float bri) {
		int sector = (int) (hue * 6f);
		float f = hue * 6f - sector;
		float p = bri * (1f - sat);
		float q = bri * (1f - f * sat);
		float t = bri * (1f - (1f - f) * sat);
		float r;
		float g;
		float b;
		switch (sector % 6) {
			case 0 -> { r = bri; g = t; b = p; }
			case 1 -> { r = q; g = bri; b = p; }
			case 2 -> { r = p; g = bri; b = t; }
			case 3 -> { r = p; g = q; b = bri; }
			case 4 -> { r = t; g = p; b = bri; }
			default -> { r = bri; g = p; b = q; }
		}
		return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
	}

	private static List<Mod> modules() {
		StrayConfig config = StrayConfig.get();
		List<Mod> mods = new ArrayList<>();
		mods.add(mod("World tint", "World", StrayScreen.Feature.WORLD, null, () -> config.worldTintEnabled, v -> config.worldTintEnabled = v, true, false));
		mods.add(mod("Skybox", "World", StrayScreen.Feature.SKY, null, () -> config.skyTintEnabled, v -> config.skyTintEnabled = v, true, false));
		mods.add(mod("Ambience", "World", StrayScreen.Feature.AMBIENCE, null, () -> config.ambienceEnabled, v -> config.ambienceEnabled = v, true, false));
		mods.add(mod("Fog", "World", StrayScreen.Feature.FOG, null, () -> config.fogEnabled, v -> config.fogEnabled = v, true, false));
		mods.add(mod("Aspect", "World", StrayScreen.Feature.VIEW, null, () -> config.aspectEnabled, v -> config.aspectEnabled = v, true, false));
		mods.add(mod("Motion blur", "World", StrayScreen.Feature.MOTION, null, () -> config.motionBlurEnabled, v -> config.motionBlurEnabled = v, true, false));
		mods.add(mod("Skull hold", "World", null, null, () -> config.legacySkullHold, v -> config.legacySkullHold = v, true, false));
		mods.add(mod("Backwards walk", "World", null, null, () -> config.legacyBackwardsWalk, v -> config.legacyBackwardsWalk = v, true, false));

		mods.add(mod("Player glow", "Visuals", StrayScreen.Feature.MOB, EntityKind.PLAYER, () -> config.playerVisuals.glowEnabled, v -> config.playerVisuals.glowEnabled = v, true, false));
		mods.add(mod("Nametags", "Visuals", StrayScreen.Feature.NAMETAGS, EntityKind.PLAYER, () -> config.playerVisuals.nametagsEnabled, v -> config.playerVisuals.nametagsEnabled = v, true, false));
		mods.add(mod("Health bar", "Visuals", StrayScreen.Feature.HEALTH, EntityKind.PLAYER, () -> config.playerVisuals.healthEnabled, v -> config.playerVisuals.healthEnabled = v, true, false));
		mods.add(mod("2D box", "Visuals", StrayScreen.Feature.BOX, EntityKind.PLAYER, () -> config.playerVisuals.boxEnabled, v -> config.playerVisuals.boxEnabled = v, true, false));
		mods.add(mod("Shader", "Visuals", StrayScreen.Feature.FILL, EntityKind.PLAYER, () -> config.playerVisuals.shaderEnabled, v -> config.playerVisuals.shaderEnabled = v, true, false));
		mods.add(mod("Mob glow", "Visuals", StrayScreen.Feature.MOB, EntityKind.MOB, () -> config.mobVisuals.glowEnabled, v -> config.mobVisuals.glowEnabled = v, true, false));
		mods.add(mod("Mob tags", "Visuals", StrayScreen.Feature.NAMETAGS, EntityKind.MOB, () -> config.mobVisuals.nametagsEnabled, v -> config.mobVisuals.nametagsEnabled = v, true, false));
		mods.add(mod("Mob health", "Visuals", StrayScreen.Feature.HEALTH, EntityKind.MOB, () -> config.mobVisuals.healthEnabled, v -> config.mobVisuals.healthEnabled = v, true, false));
		mods.add(mod("Mob box", "Visuals", StrayScreen.Feature.BOX, EntityKind.MOB, () -> config.mobVisuals.boxEnabled, v -> config.mobVisuals.boxEnabled = v, true, false));
		mods.add(mod("Mob shader", "Visuals", StrayScreen.Feature.FILL, EntityKind.MOB, () -> config.mobVisuals.shaderEnabled, v -> config.mobVisuals.shaderEnabled = v, true, false));
		mods.add(mod("Star mobs", "Visuals", StrayScreen.Feature.STAR, EntityKind.STAR, () -> config.starVisuals.glowEnabled, v -> {
			config.starVisuals.glowEnabled = v;
			config.starMobEsp = v;
		}, true, false));
		mods.add(mod("Held item", "Visuals", StrayScreen.Feature.HELD_ITEM, null, () -> config.heldItemShaderEnabled, v -> config.heldItemShaderEnabled = v, true, false));
		mods.add(mod("Block outline", "Visuals", StrayScreen.Feature.BLOCK, null, () -> config.blockOutlineGlow, v -> config.blockOutlineGlow = v, true, false));
		mods.add(mod("Chest ESP", "Visuals", StrayScreen.Feature.CHEST, null, () -> config.chestEspEnabled, v -> config.chestEspEnabled = v, true, false));
		mods.add(mod("Fairy souls", "Visuals", StrayScreen.Feature.FAIRY, null, () -> config.fairySoulEsp, v -> config.fairySoulEsp = v, true, false));

		mods.add(mod("Hitsound", "Combat", StrayScreen.Feature.HITSOUND, null, () -> config.hitsoundEnabled, v -> config.hitsoundEnabled = v, true, false));
		mods.add(mod("Auto clicker", "Combat", StrayScreen.Feature.AUTO_CLICKER, null, () -> config.autoClickerEnabled, v -> config.autoClickerEnabled = v, true, false));
		mods.add(mod("Auto rogue", "Combat", null, null, () -> config.autoRogueEnabled, v -> config.autoRogueEnabled = v, true, false));
		mods.add(mod("Triggerbot", "Combat", null, null, () -> config.triggerbotEnabled, v -> config.triggerbotEnabled = v, true, false));
		mods.add(mod("Experiments", "Combat", StrayScreen.Feature.AUTO_EXPERIMENTS, null, () -> config.autoExperimentsEnabled, v -> config.autoExperimentsEnabled = v, true, false));

		mods.add(mod("Array list", "HUD", null, null, () -> config.arrayList, v -> config.arrayList = v, false, false));
		mods.add(mod("Watermark", "HUD", StrayScreen.Feature.WATERMARK, null, () -> config.watermarkEnabled, v -> config.watermarkEnabled = v, true, false));
		mods.add(mod("Music", "HUD", StrayScreen.Feature.MUSIC, null, () -> config.musicHudEnabled, v -> config.musicHudEnabled = v, true, false));
		mods.add(mod("Picture in picture", "HUD", StrayScreen.Feature.PIP, null, () -> config.pipEnabled, v -> config.pipEnabled = v, true, false));
		mods.add(mod("Raw mats", "HUD", StrayScreen.Feature.RAWMATS, null, () -> config.rawmatsHudEnabled, v -> config.rawmatsHudEnabled = v, true, false));
		mods.add(mod("Pickup log", "HUD", null, null, () -> config.pickupLogEnabled, v -> config.pickupLogEnabled = v, true, false));
		mods.add(mod("Skill progress", "HUD", StrayScreen.Feature.SKILL, null, () -> config.skillProgressHudEnabled, v -> config.skillProgressHudEnabled = v, true, false));
		mods.add(mod("Inventory", "HUD", StrayScreen.Feature.INVENTORY, null, () -> config.inventoryHudEnabled, v -> config.inventoryHudEnabled = v, true, false));
		mods.add(mod("Scoreboard", "HUD", null, null, () -> config.hudScoreboard, v -> config.hudScoreboard = v, true, false));
		mods.add(mod("Boss bar", "HUD", null, null, () -> config.hudBossBar, v -> config.hudBossBar = v, true, false));
		mods.add(mod("Effects", "HUD", null, null, () -> config.hudEffects, v -> config.hudEffects = v, true, false));
		mods.add(mod("Item name", "HUD", null, null, () -> config.hudHeldItem, v -> config.hudHeldItem = v, true, false));

		mods.add(mod("Mining HUD", "Mining", StrayScreen.Feature.MINING, null, () -> config.miningHudEnabled, v -> config.miningHudEnabled = v, true, false));
		mods.add(mod("Titanium ESP", "Mining", StrayScreen.Feature.TITANIUM, null, () -> config.titaniumEsp, v -> config.titaniumEsp = v, true, false));
		mods.add(mod("CH waypoints", "Mining", StrayScreen.Feature.CRYSTAL, null, () -> config.crystalHollowsWaypoints, v -> config.crystalHollowsWaypoints = v, true, false));
		mods.add(mod("CH map", "Mining", StrayScreen.Feature.CH_MAP, null, () -> config.crystalHollowsMap, v -> config.crystalHollowsMap = v, true, false));
		mods.add(mod("Metal detector", "Mining", StrayScreen.Feature.METAL, null, () -> config.metalDetectorSolver, v -> config.metalDetectorSolver = v, true, false));
		mods.add(mod("Robot parts", "Mining", null, null, () -> config.nucleusAlertParts, v -> config.nucleusAlertParts = v, true, false));
		mods.add(mod("Divan tools", "Mining", null, null, () -> config.nucleusAlertTools, v -> config.nucleusAlertTools = v, true, false));
		mods.add(mod("Nodes", "Mining", StrayScreen.Feature.NODES, null, () -> config.markersEnabled, v -> config.markersEnabled = v, true, false));
		mods.add(mod("Node ESP", "Mining", StrayScreen.Feature.NODE_ESP, null, () -> config.boxFill, v -> config.boxFill = v, true, false));

		mods.add(mod("Yaw / Pitch", "Farming", StrayScreen.Feature.FARMING, null, () -> config.farmingYawPitch, v -> config.farmingYawPitch = v, true, false));
		mods.add(mod("Jacob contest", "Farming", null, null, () -> config.jacobContestHudEnabled, v -> config.jacobContestHudEnabled = v, true, false));
		mods.add(mod("Composter", "Farming", null, null, () -> config.composterHudEnabled, v -> config.composterHudEnabled = v, true, false));
		mods.add(mod("Garden plots", "Farming", StrayScreen.Feature.PLOTS, null, () -> config.gardenPlotsWidget, v -> config.gardenPlotsWidget = v, true, false));
		mods.add(mod("Shopping list", "Farming", StrayScreen.Feature.SHOPPING, null, () -> config.gardenShoppingHudEnabled, v -> config.gardenShoppingHudEnabled = v, true, false));
		mods.add(mod("Pest ESP", "Farming", StrayScreen.Feature.PEST, null, () -> config.pestEspEnabled, v -> config.pestEspEnabled = v, true, false));
		mods.add(mod("Pest cooldown", "Farming", StrayScreen.Feature.PEST_COOLDOWN, null, () -> config.pestCooldownHudEnabled, v -> config.pestCooldownHudEnabled = v, true, false));
		mods.add(mod("Auto DNA", "Farming", StrayScreen.Feature.AUTO_DNA, null, () -> config.autoDnaEnabled, v -> config.autoDnaEnabled = v, true, false));

		mods.add(mod("Click GUI", "Menus", null, null, () -> config.clickGui, v -> config.clickGui = v, false, false, true));
		mods.add(mod("Loadouts", "Menus", StrayScreen.Feature.LOADOUTS, null, () -> config.loadoutsMenuEnabled, v -> config.loadoutsMenuEnabled = v, true, false));
		mods.add(mod("Wardrobe", "Menus", StrayScreen.Feature.WARDROBE, null, () -> config.wardrobeMenuEnabled, v -> config.wardrobeMenuEnabled = v, true, false));
		mods.add(mod("Profile viewer", "Menus", null, null, () -> config.profileViewerEnabled, v -> config.profileViewerEnabled = v, true, false));
		mods.add(mod("Storage preview", "Menus", null, null, () -> config.storagePreviewEnabled, v -> config.storagePreviewEnabled = v, true, false));
		mods.add(mod("Themed GUIs", "Menus", null, null, () -> config.themedGuisEnabled, v -> config.themedGuisEnabled = v, true, false));
		mods.add(mod("Themed chat", "Menus", null, null, () -> config.themedChatEnabled, v -> config.themedChatEnabled = v, true, false));
		mods.add(mod("Compact stash", "Menus", null, null, () -> config.stashChatCompact, v -> config.stashChatCompact = v, true, false));
		mods.add(mod("Cleaner NPC", "Menus", null, null, () -> config.npcChatClean, v -> config.npcChatClean = v, true, false));
		mods.add(mod("No cursor reset", "Menus", null, null, () -> config.noCursorReset, v -> config.noCursorReset = v, true, true));
		mods.add(mod("Disabled potions", "Menus", null, null, () -> config.disabledPotionsHighlight, v -> config.disabledPotionsHighlight = v, true, false));
		mods.add(mod("Global IRC", "Menus", null, null, () -> config.strayIrcEnabled, v -> config.strayIrcEnabled = v, true, false));
		mods.add(mod("Lobby pings", "Menus", null, null, () -> config.strayPingEnabled, v -> config.strayPingEnabled = v, true, false));
		mods.add(mod("Block marks", "Menus", StrayScreen.Feature.MARKS, null, () -> config.blockMarksEnabled, v -> config.blockMarksEnabled = v, true, false));
		mods.add(mod("Command rings", "Menus", StrayScreen.Feature.RINGS, null, () -> config.commandRingsEnabled, v -> config.commandRingsEnabled = v, true, false));
		mods.add(mod("Paths", "Menus", StrayScreen.Feature.PATHS, null, () -> config.pathsEnabled, v -> config.pathsEnabled = v, true, false));

		mods.add(mod("Nick hider", "Player", null, null, () -> config.nickEnabled, v -> config.nickEnabled = v, true, false));
		return mods;
	}

	private static Mod mod(
		String name,
		String column,
		StrayScreen.Feature feature,
		EntityKind kind,
		BooleanSupplier on,
		Consumer<Boolean> set,
		boolean list,
		boolean timeout
	) {
		return mod(name, column, feature, kind, on, set, list, timeout, false);
	}

	private static Mod mod(
		String name,
		String column,
		StrayScreen.Feature feature,
		EntityKind kind,
		BooleanSupplier on,
		Consumer<Boolean> set,
		boolean list,
		boolean timeout,
		boolean menuStyle
	) {
		return new Mod(name, column, feature, kind, on, set, list, timeout, menuStyle);
	}

	private record Mod(
		String name,
		String column,
		StrayScreen.Feature feature,
		EntityKind kind,
		BooleanSupplier on,
		Consumer<Boolean> set,
		boolean list,
		boolean timeout,
		boolean menuStyle
	) {
	}

	private static final class Column {
		final String id;
		float x;
		float y;
		float scroll;
		float height = HEADER;

		Column(String id) {
			this.id = id;
		}
	}

	private record Row(Mod mod, float x, float y, float w, float h) {
	}
}
