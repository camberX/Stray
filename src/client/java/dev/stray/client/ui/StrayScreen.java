package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.StrayClient;
import dev.stray.client.combat.AutoClicker;
import dev.stray.client.combat.Hitsound;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.UnloadState;
import dev.stray.client.farming.FarmingHud;
import dev.stray.client.farming.JacobContestTracker;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.mining.CrystalHollows;
import dev.stray.client.mining.MiningAreas;
import dev.stray.client.mining.MiningTracker;
import dev.stray.client.mining.TitaniumTracker;
import dev.stray.client.render.GlowBlurRadius;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudStats;
import dev.stray.client.render.EspMobPrint;
import dev.stray.client.render.MobCatalog;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.render.PlayerPreview;
import dev.stray.client.render.Starfield;
import dev.stray.client.visual.CustomCape;
import dev.stray.client.visual.NickHider;
import dev.stray.client.visual.ShopCape;
import dev.stray.client.visual.WorldTint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class StrayScreen extends Screen {
	private static final float MENU_W = 400;
	private static final float MENU_H = 268;
	private static final float SIDEBAR_W = 88;
	private static final float TOOLBAR_H = 22;
	private static final float ROW = 16;
	private static final float COL_GAP = 10;
	private static final float PAD = 8;
	private static final float CARD_PAD = 8;
	private static final float CARD_HEAD = 20;
	private static final float ACTION_W = 54;
	private static final float ICON_SLOT = 14;
	private static final float CATEGORY_ICON = 1.55f;
	private static final float CATEGORY_ICON_LINE = 9.0f;
	private static final float PICKER_W = 132;
	private static final float PICKER_H = 140;
	private static final float PANEL_W = 168;
	private static final float FEATURE_W = 176;
	private static final float SETTINGS_H = 444;
	private static final float FONT_SEARCH_H = 14;
	private static final float FONT_ROW = 16;
	private static final int FONT_VISIBLE = 6;
	private static final float COG_W = 14;

	private enum Group {
		WORLD("WORLD", "World", MenuFont.GLOBE),
		ESP("VISUALS", "Visuals", MenuFont.EYE),
		COMBAT("COMBAT", "Combat", MenuFont.SWORD),
		HUD("HUD", "HUD", MenuFont.DISPLAY),
		MINING("MINING", "Mining", MenuFont.DIAMOND),
		FARMING("FARMING", "Farming", MenuFont.GRAIN),
		MISC("MISC", "Misc", MenuFont.CATEGORY),
		PLAYER("PLAYER", "Player", MenuFont.PERSON),
		THEME("THEME", "Theme", MenuFont.PALETTE);

		final String label;
		final String caption;
		final String glyph;

		Group(String label, String caption, String glyph) {
			this.label = label;
			this.caption = caption;
			this.glyph = glyph;
		}
	}

	private enum Tab {
		WORLD("Atmosphere", Group.WORLD),
		CAMERA("Camera", Group.WORLD),
		ESP("ESP", Group.ESP),
		PLAYERS("Players", Group.ESP),
		CATALOG("Mobs", Group.ESP),
		COMBAT("Hitsound", Group.COMBAT),
		ASSIST("Assist", Group.COMBAT),
		OVERLAY("Widgets", Group.HUD),
		MEDIA("Media", Group.HUD),
		BARS("Vanilla", Group.HUD),
		MINING("Dwarven", Group.MINING),
		HOLLOWS("Hollows", Group.MINING),
		NODES("Nodes", Group.MINING),
		FARMING("HUD", Group.FARMING),
		GARDEN("Garden", Group.FARMING),
		GREENHOUSE("DNA", Group.FARMING),
		MENUS("Menus", Group.MISC),
		KEYS("Keys", Group.MISC),
		STATUS("Status", Group.MISC),
		PLAYER("Player", Group.PLAYER),
		SETTINGS("Theme", Group.THEME);

		final String label;
		final Group group;

		Tab(String label, Group group) {
			this.label = label;
			this.group = group;
		}
	}

	private enum Feature {
		WORLD("World tint", 4),
		SKY("Skybox", 4),
		FOG("Fog", 5),
		VIEW("Aspect", 3),
		MOTION("Motion blur", 3),
		HITSOUND("Hitsound", 3),
		HELD_ITEM("Held item", 7),
		FILL("Player shader", 10),
		AUTO_CLICKER("Auto clicker", 8),
		AUTO_EXPERIMENTS("Auto experiments", 5),
		MOB("Mob glow", 3),
		STAR("Star mobs", 6),
		BLOCK("Block outline", 1),
		CHEST("Chest ESP", 5),
		FAIRY("Fairy souls", 1),
		NODE_ESP("Node ESP", 4),
		WATERMARK("Watermark", 4),
		MUSIC("Music", 3),
		RAWMATS("Raw mats", 1),
		MINING("Mining HUD", 1),
		TITANIUM("Titanium ESP", 3),
		CRYSTAL("CH waypoints", 4),
		CH_MAP("CH map", 1),
		METAL("Metal detector", 1),
		FARMING("Yaw / Pitch", 1),
		INVENTORY("Inventory", 5),
		PLOTS("Garden plots", 1),
		PEST("Pest ESP", 2),
		AUTO_DNA("Auto DNA", 5),
		NAMETAGS("Nametags", 6),
		HEALTH("Health bar", 4),
		NODES("Nodes", 5);

		final String title;
		final int rows;

		Feature(String title, int rows) {
			this.title = title;
			this.rows = rows;
		}

		float height() {
			return 22 + rows * ROW + 10;
		}
	}

	private enum PickerTarget {
		WORLD, SKY, FOG, NODE, THEME, PANE, CONTROL, PILL, MOB, STAR, BLOCK, TITANIUM, CHEST, PEST, HELD_ITEM, HELD_ITEM_OUTLINE, FILL, FILL_OUTLINE
	}

	private record SearchEntry(String label, Tab tab, String hint) {
	}

	private static final SearchEntry[] SEARCH = {
		new SearchEntry("World tint", Tab.WORLD, "World"),
		new SearchEntry("Lightmap", Tab.WORLD, "World"),
		new SearchEntry("Shader", Tab.WORLD, "World"),
		new SearchEntry("Skybox tint", Tab.WORLD, "World"),
		new SearchEntry("End sky", Tab.WORLD, "World"),
		new SearchEntry("End skybox", Tab.WORLD, "World"),
		new SearchEntry("Aspect ratio", Tab.CAMERA, "Camera"),
		new SearchEntry("Custom fog", Tab.CAMERA, "Camera"),
		new SearchEntry("Motion blur", Tab.CAMERA, "Camera"),
		new SearchEntry("Velocity blur", Tab.CAMERA, "Camera"),
		new SearchEntry("Frame blending", Tab.CAMERA, "Camera"),
		new SearchEntry("Hybrid blur", Tab.CAMERA, "Camera"),
		new SearchEntry("Accumulation blur", Tab.CAMERA, "Camera"),
		new SearchEntry("Combat", Tab.COMBAT, "Hitsound"),
		new SearchEntry("Hitsound", Tab.COMBAT, "Hitsound"),
		new SearchEntry("Melee hitsound", Tab.COMBAT, "Hitsound"),
		new SearchEntry("Arrow hitsound", Tab.COMBAT, "Hitsound"),
		new SearchEntry("Hitmarker", Tab.COMBAT, "Hitsound"),
		new SearchEntry("Hitmarker scale", Tab.COMBAT, "Hitsound"),
		new SearchEntry("Hit volume", Tab.COMBAT, "Hitsound"),
		new SearchEntry("Hit pitch", Tab.COMBAT, "Hitsound"),
		new SearchEntry("Triggerbot", Tab.ASSIST, "Assist"),
		new SearchEntry("Triggerbot players", Tab.ASSIST, "Assist"),
		new SearchEntry("Triggerbot humanize", Tab.ASSIST, "Assist"),
		new SearchEntry("Auto clicker", Tab.ASSIST, "Assist"),
		new SearchEntry("Autoclicker", Tab.ASSIST, "Assist"),
		new SearchEntry("Terminator", Tab.ASSIST, "Assist"),
		new SearchEntry("Auto experiments", Tab.MENUS, "Misc"),
		new SearchEntry("Chronomatron", Tab.MENUS, "Misc"),
		new SearchEntry("Ultrasequencer", Tab.MENUS, "Misc"),
		new SearchEntry("Click delay", Tab.MENUS, "Misc"),
		new SearchEntry("Serum count", Tab.MENUS, "Misc"),
		new SearchEntry("Disabled potions", Tab.MENUS, "Menus"),
		new SearchEntry("Toggle Potion Effects", Tab.MENUS, "Menus"),
		new SearchEntry("Potion effects", Tab.MENUS, "Menus"),
		new SearchEntry("Held item shader", Tab.ESP, "Held item"),
		new SearchEntry("Item shader", Tab.ESP, "Held item"),
		new SearchEntry("Held item outline", Tab.ESP, "Held item"),
		new SearchEntry("Held item outline color", Tab.ESP, "Held item"),
		new SearchEntry("Item smoke", Tab.ESP, "Held item"),
		new SearchEntry("End portal shader", Tab.ESP, "Held item"),
		new SearchEntry("Portal shader", Tab.ESP, "Held item"),
		new SearchEntry("Galaxy shader", Tab.ESP, "Held item"),
		new SearchEntry("Black hole shader", Tab.ESP, "Held item"),
		new SearchEntry("Ghost", Tab.ESP, "Held item"),
		new SearchEntry("Ghost item", Tab.ESP, "Held item"),
		new SearchEntry("Player fill", Tab.PLAYERS, "Shader"),
		new SearchEntry("Player shader", Tab.PLAYERS, "Shader"),
		new SearchEntry("Player fill ESP", Tab.PLAYERS, "Shader"),
		new SearchEntry("Player fill color", Tab.PLAYERS, "Shader"),
		new SearchEntry("Player fill outline", Tab.PLAYERS, "Shader"),
		new SearchEntry("Player fill portal", Tab.PLAYERS, "Shader"),
		new SearchEntry("Player fill outline color", Tab.PLAYERS, "Shader"),
		new SearchEntry("Fill star mobs", Tab.PLAYERS, "Shader"),
		new SearchEntry("Shader star mobs", Tab.PLAYERS, "Shader"),
		new SearchEntry("Glow ESP", Tab.ESP, "Star mobs"),
		new SearchEntry("Fill ESP mobs", Tab.PLAYERS, "Shader"),
		new SearchEntry("Shader mobs", Tab.PLAYERS, "Shader"),
		new SearchEntry("Fill through walls", Tab.PLAYERS, "Shader"),
		new SearchEntry("Silhouette", Tab.PLAYERS, "Shader"),
		new SearchEntry("Held item silhouette", Tab.ESP, "Held item"),
		new SearchEntry("Mob fill", Tab.PLAYERS, "Players"),
		new SearchEntry("Held fill", Tab.ESP, "Held item"),
		new SearchEntry("Hand fill", Tab.ESP, "Held item"),
		new SearchEntry("Nametag ESP", Tab.PLAYERS, "Players"),
		new SearchEntry("Star mobs", Tab.ESP, "Glow"),
		new SearchEntry("Star mob ESP", Tab.ESP, "Glow"),
		new SearchEntry("Starred mobs", Tab.ESP, "Glow"),
		new SearchEntry("Highlight bats", Tab.ESP, "Glow"),
		new SearchEntry("Highlight fels", Tab.ESP, "Glow"),
		new SearchEntry("Block outline", Tab.ESP, "World"),
		new SearchEntry("Block outline color", Tab.ESP, "World"),
		new SearchEntry("Chest ESP", Tab.ESP, "World"),
		new SearchEntry("Fairy souls", Tab.ESP, "World"),
		new SearchEntry("Fairy soul ESP", Tab.ESP, "World"),
		new SearchEntry("Fairy soul tracker", Tab.ESP, "World"),
		new SearchEntry("Chest tracers", Tab.ESP, "World"),
		new SearchEntry("Chest aim speed", Tab.ESP, "World"),
		new SearchEntry("Mobs", Tab.CATALOG, "Mobs"),
		new SearchEntry("Node ESP", Tab.NODES, "Nodes"),
		new SearchEntry("Nametags", Tab.PLAYERS, "Players"),
		new SearchEntry("Nametag style", Tab.PLAYERS, "Players"),
		new SearchEntry("Own nametag", Tab.PLAYERS, "Players"),
		new SearchEntry("Nametag size", Tab.PLAYERS, "Players"),
		new SearchEntry("Nametag opacity", Tab.PLAYERS, "Players"),
		new SearchEntry("Health bar", Tab.ESP, "Health"),
		new SearchEntry("Mob health", Tab.ESP, "Health"),
		new SearchEntry("Player health", Tab.ESP, "Health"),
		new SearchEntry("Health side", Tab.ESP, "Health"),
		new SearchEntry("Menu scale", Tab.SETTINGS, "Theme"),
		new SearchEntry("HUD opacity", Tab.SETTINGS, "Theme"),
		new SearchEntry("Menu stars", Tab.SETTINGS, "Theme"),
		new SearchEntry("HUD stars", Tab.SETTINGS, "Theme"),
		new SearchEntry("Auto update", Tab.SETTINGS, "Theme"),
		new SearchEntry("Auto-update", Tab.SETTINGS, "Theme"),
		new SearchEntry("Updater", Tab.SETTINGS, "Theme"),
		new SearchEntry("Update notify", Tab.SETTINGS, "Theme"),
		new SearchEntry("Update notification", Tab.SETTINGS, "Theme"),
		new SearchEntry("New version", Tab.SETTINGS, "Theme"),
		new SearchEntry("Markers", Tab.NODES, "Nodes"),
		new SearchEntry("Keybinds", Tab.KEYS, "Keys"),
		new SearchEntry("Open menu", Tab.KEYS, "Keys"),
		new SearchEntry("Menus", Tab.MENUS, "Menus"),
		new SearchEntry("Loadouts", Tab.MENUS, "Menus"),
		new SearchEntry("Loadouts menu", Tab.MENUS, "Menus"),
		new SearchEntry("Loadouts animation", Tab.MENUS, "Menus"),
		new SearchEntry("Open animation", Tab.MENUS, "Menus"),
		new SearchEntry("Wardrobe", Tab.MENUS, "Menus"),
		new SearchEntry("Wardrobe menu", Tab.MENUS, "Menus"),
		new SearchEntry("Armor Sets", Tab.MENUS, "Menus"),
		new SearchEntry("Profile viewer", Tab.MENUS, "Menus"),
		new SearchEntry("Profile", Tab.MENUS, "Menus"),
		new SearchEntry("/pv", Tab.MENUS, "Menus"),
		new SearchEntry("Node HUD", Tab.NODES, "Nodes"),
		new SearchEntry("Mining HUD", Tab.MINING, "Mining"),
		new SearchEntry("Titanium ESP", Tab.MINING, "Mining"),
		new SearchEntry("Commissions", Tab.MINING, "Mining"),
		new SearchEntry("Pickaxe ability", Tab.MINING, "Mining"),
		new SearchEntry("Ability alert", Tab.MINING, "Mining"),
		new SearchEntry("Farming", Tab.FARMING, "Farming"),
		new SearchEntry("Yaw", Tab.FARMING, "Farming"),
		new SearchEntry("Pitch", Tab.FARMING, "Farming"),
		new SearchEntry("Yaw / Pitch", Tab.FARMING, "Farming"),
		new SearchEntry("Farming tool", Tab.FARMING, "Farming"),
		new SearchEntry("Jacob contest HUD", Tab.FARMING, "Farming"),
		new SearchEntry("Contest prediction", Tab.FARMING, "Farming"),
		new SearchEntry("Crops per second", Tab.FARMING, "Farming"),
		new SearchEntry("Composter overlay", Tab.FARMING, "Farming"),
		new SearchEntry("Garden plots", Tab.GARDEN, "Garden"),
		new SearchEntry("Pest ESP", Tab.GARDEN, "Garden"),
		new SearchEntry("Garden pests", Tab.GARDEN, "Garden"),
		new SearchEntry("Vacuum", Tab.GARDEN, "Garden"),
		new SearchEntry("Plot widget", Tab.GARDEN, "Garden"),
		new SearchEntry("Configure Plots", Tab.GARDEN, "Garden"),
		new SearchEntry("Auto DNA", Tab.GREENHOUSE, "DNA"),
		new SearchEntry("DNA analyzer", Tab.GREENHOUSE, "DNA"),
		new SearchEntry("Greenhouse DNA", Tab.GREENHOUSE, "DNA"),
		new SearchEntry("Organic Matter", Tab.FARMING, "Farming"),
		new SearchEntry("Composter Fuel", Tab.FARMING, "Farming"),
		new SearchEntry("Filled box", Tab.NODES, "Nodes"),
		new SearchEntry("Watermark", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Music HUD", Tab.MEDIA, "Media"),
		new SearchEntry("Song Notification", Tab.MEDIA, "Media"),
		new SearchEntry("Song chat", Tab.MEDIA, "Media"),
		new SearchEntry("Now playing chat", Tab.MEDIA, "Media"),
		new SearchEntry("Raw mats", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Pickup log", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Picked up items", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Enchanted materials", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Spotify", Tab.MEDIA, "Media"),
		new SearchEntry("YouTube Music", Tab.MEDIA, "Media"),
		new SearchEntry("Scoreboard", Tab.BARS, "Bars"),
		new SearchEntry("Boss bar", Tab.BARS, "Bars"),
		new SearchEntry("Effects", Tab.BARS, "Bars"),
		new SearchEntry("Held item", Tab.BARS, "Bars"),
		new SearchEntry("CH map", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Crystal Hollows map", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Hollows map", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Metal detector", Tab.HOLLOWS, "Divan"),
		new SearchEntry("TREASURE", Tab.HOLLOWS, "Divan"),
		new SearchEntry("Scavenged", Tab.HOLLOWS, "Divan"),
		new SearchEntry("Crystal Hollows waypoints", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("CH waypoints", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Entrance zones", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Nucleus waypoints", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Zone doors", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Dump coords", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Jungle Temple", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Mines of Divan", Tab.HOLLOWS, "Divan"),
		new SearchEntry("Goblin Queen", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Lost Precursor City", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Khazad-dum", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Fairy Grotto", Tab.HOLLOWS, "Hollows"),
		new SearchEntry("Inventory HUD", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Minimal", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Blur", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Item count", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Pane opacity", Tab.SETTINGS, "Theme"),
		new SearchEntry("Control glass", Tab.SETTINGS, "Theme"),
		new SearchEntry("Pills", Tab.SETTINGS, "Theme"),
		new SearchEntry("Pills opacity", Tab.SETTINGS, "Theme"),
		new SearchEntry("Category pills", Tab.SETTINGS, "Theme"),
		new SearchEntry("Feature pills", Tab.SETTINGS, "Theme"),
		new SearchEntry("Menu glass", Tab.SETTINGS, "Theme"),
		new SearchEntry("Frost", Tab.SETTINGS, "Theme"),
		new SearchEntry("Status", Tab.STATUS, "Status"),
		new SearchEntry("FPS", Tab.STATUS, "Status"),
		new SearchEntry("Ping", Tab.STATUS, "Status"),
		new SearchEntry("Hypixel", Tab.STATUS, "Status"),
		new SearchEntry("Nick hider", Tab.PLAYER, "Player"),
		new SearchEntry("Cape", Tab.PLAYER, "Player"),
		new SearchEntry("Cape creator", Tab.PLAYER, "Player"),
		new SearchEntry("Cape shop", Tab.PLAYER, "Player"),
		new SearchEntry("Refresh capes", Tab.PLAYER, "Player")
	};

	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Float> anims = new HashMap<>();
	private Tab tab = Tab.WORLD;
	private PickerTarget pickerTarget;
	private float pickerHue = 200f;
	private float pickerSat = 0.82f;
	private float pickerVal = 1f;
	private float pickerAlpha = 1f;
	private float pickerX;
	private float pickerY;
	private double lastClickY;
	private boolean settingsOpen;
	private boolean notesOpen;
	private boolean searchOpen;
	private boolean featureOpen;
	private Feature featureId;
	private String fieldScope = "";
	private int bindListen;
	private boolean capeFocused;
	private boolean nickFocused;
	private String searchQuery = "";
	private String capeUrlDraft = "";
	private boolean dragging;
	private boolean moved;
	private double dragOffX;
	private double dragOffY;
	private boolean placed;
	private long lastNs = System.nanoTime();
	private float dt = 0.016f;
	private float appear;
	private boolean closing;
	private boolean finishedClose;
	private float pageT = 1f;
	private float pageDir = 1f;
	private float navY = -1f;
	private float railNavY = -1f;
	private float tabLineX = -1f;
	private float tabLineW;
	private float settingsT;
	private float notesT;
	private float searchT;
	private float pickerT;
	private float featureT;
	private float settingsX;
	private float settingsY;
	private float notesX;
	private float notesY;
	private float notesH;
	private float notesScroll;
	private float featureX;
	private float featureY;
	private float searchFieldX;
	private float searchFieldW;
	private float capeFieldX;
	private float capeFieldY;
	private float capeFieldW;
	private float nickFieldX;
	private float nickFieldY;
	private float nickFieldW;
	private boolean mobSearchFocused;
	private String mobQuery = "";
	private float mobScroll;
	private float mobListX;
	private float mobListY;
	private float mobListW;
	private float mobListH;
	private float mobFieldX;
	private float mobFieldY;
	private float mobFieldW;
	private boolean ensureMobVisible;
	private float nametagEspScroll;
	private float nametagEspListX;
	private float nametagEspListY;
	private float nametagEspListW;
	private float nametagEspListH;
	private float tabScroll;
	private float tabScrollMax;
	private float pageExtent;
	private float pageClipX;
	private float pageClipY;
	private float pageClipW;
	private float pageClipH;
	private boolean contentHitMode;
	private float previewX;
	private float previewY;
	private float previewW;
	private float previewH;
	private boolean fontPickerOpen;
	private boolean fontSearchFocused;
	private String fontQuery = "";
	private float fontScroll;
	private float fontListX;
	private float fontListY;
	private float fontListW;
	private float fontListH;

	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;
	private float viewScale = 1f;
	private float viewCx;
	private float viewCy;
	private float viewLift;

	public StrayScreen() {
		super(Component.literal("Stray"));
		tab = parseTab(StrayConfig.get().menuTab);
		String url = StrayConfig.get().capeUrl;
		if (url != null && !url.isBlank()) {
			capeUrlDraft = url;
		} else {
			String path = StrayConfig.get().capePath;
			capeUrlDraft = path == null ? "" : path;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (controlCenter()) {
			return;
		}
		if (minecraft.level != null) {
			extractBlurredBackground(graphics);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		tickAnim();
		hits.clear();
		if (closing && appear <= 0.02f) {
			finishClose();
			return;
		}
		Font font = minecraft.font;
		layout();

		int dim = Anim.fade(0x14000000, appear);
		GuiDraw.fill(graphics, 0, 0, width, height, dim);

		float scale = (0.92f + 0.08f * appear) * StrayConfig.normalizeMenuScale(StrayConfig.get().menuScale);
		float lift = (1f - appear) * 12f;
		float cx = windowX + windowW * 0.5f;
		float cy = windowY + windowH * 0.5f;
		viewScale = Math.max(0.35f, scale);
		viewCx = cx;
		viewCy = cy;
		viewLift = lift;
		int localMx = Math.round(localX(mouseX));
		int localMy = Math.round(localY(mouseY));
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy + lift);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-cx, -cy);

		if (controlCenter()) {
			ControlChrome.window(graphics, windowX, windowY, windowW, windowH);
		} else {
			boolean chromeClip = GuiDraw.scissor(graphics, windowX, windowY, windowW, windowH);
			GuiDraw.rounded(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW);
			GuiDraw.roundLeft(graphics, windowX, windowY, sidebarW(), windowH, Theme.WINDOW_RADIUS, Theme.SIDEBAR);
			Starfield.draw(graphics, windowX + sidebarW(), windowY, windowW - sidebarW(), windowH, Theme.WINDOW_RADIUS, appear);
			GuiDraw.fill(graphics, windowX + sidebarW(), windowY, 1, windowH, Theme.withAlpha(Theme.ACCENT, 90));
			if (chromeClip) {
				GuiDraw.disableScissor(graphics);
			}
		}

		drawSidebar(graphics, font, localMx, localMy);
		drawToolbar(graphics, font, localMx, localMy);
		pageClipX = windowX + sidebarW();
		pageClipY = windowY + toolbarH();
		pageClipW = windowW - sidebarW();
		pageClipH = windowH - toolbarH();
		tabScroll = Mth.clamp(tabScroll, 0f, tabScrollMax);
		pageExtent = pageClipY;
		boolean columnsClip = GuiDraw.scissor(graphics, pageClipX, pageClipY, pageClipW, pageClipH);
		int columnHits = hits.size();
		contentHitMode = true;
		graphics.pose().pushMatrix();
		applyPageTransform(graphics);
		graphics.pose().translate(0f, -tabScroll);
		drawColumns(graphics, font, localMx, Math.round(localMy + tabScroll));
		graphics.pose().popMatrix();
		contentHitMode = false;
		tabScrollMax = Math.max(0f, pageExtent - contentBottom());
		tabScroll = Mth.clamp(tabScroll, 0f, tabScrollMax);
		if (columnsClip) {
			GuiDraw.disableScissor(graphics);
		}
		drawPageScrollbar(graphics);
		if (pageT < 0.86f && hits.size() > columnHits) {
			hits.subList(columnHits, hits.size()).clear();
		}
		if (searchT > 0.02f && !searchQuery.isBlank()) {
			drawSearchResults(graphics, font, localMx, localMy);
		}
		if (featureT > 0.02f && featureId != null) {
			drawFeaturePanel(graphics, font, localMx, localMy);
		}
		if (settingsT > 0.02f && !controlCenter()) {
			drawSettings(graphics, font, localMx, localMy);
		}
		if (notesT > 0.02f) {
			drawNotes(graphics, font, localMx, localMy);
		}
		if (pickerT > 0.02f && pickerTarget != null) {
			drawPicker(graphics, font);
		}
		graphics.pose().popMatrix();
		if (closing || appear < 0.88f) {
			hits.clear();
		}
	}

	private void tickAnim() {
		long now = System.nanoTime();
		dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		appear = Anim.exp(appear, closing ? 0f : 1f, closing ? 16f : 13f, dt);
		settingsT = Anim.exp(settingsT, settingsOpen ? 1f : 0f, 18f, dt);
		notesT = Anim.exp(notesT, notesOpen ? 1f : 0f, 18f, dt);
		searchT = Anim.exp(searchT, searchOpen ? 1f : 0f, 18f, dt);
		pickerT = Anim.exp(pickerT, pickerTarget != null ? 1f : 0f, 18f, dt);
		featureT = Anim.exp(featureT, featureOpen && featureId != null ? 1f : 0f, 18f, dt);
		pageT = Anim.exp(pageT, 1f, 14f, dt);
	}

	private float anim(String key, float target) {
		float current = anims.getOrDefault(key, target);
		float next = Anim.exp(current, target, 16f, dt);
		anims.put(key, next);
		return next;
	}

	private String rowAnimKey(String label, float x, float y) {
		String name = fieldScope.isEmpty() ? label : fieldScope + "/" + label;
		return name + "@" + Math.round(x) + ":" + Math.round(y);
	}

	private boolean controlCenter() {
		return StrayConfig.get().guiDesignControl();
	}

	private int ink() {
		return controlCenter() ? ControlChrome.cardText() : Theme.TEXT;
	}

	private int fade() {
		return controlCenter() ? ControlChrome.cardMuted() : Theme.MUTED;
	}

	private float menuW() {
		return controlCenter() ? 528f : MENU_W;
	}

	private float menuH() {
		return controlCenter() ? 392f : MENU_H;
	}

	private float sidebarW() {
		return controlCenter() ? ControlChrome.RAIL_INSET * 2f + ControlChrome.RAIL_W : SIDEBAR_W;
	}

	private float toolbarH() {
		return controlCenter() ? 36f : TOOLBAR_H;
	}

	private float pad() {
		return controlCenter() ? 14f : PAD;
	}

	private float cardPad() {
		return controlCenter() ? 14f : CARD_PAD;
	}

	private float cardTop() {
		return controlCenter() ? 9f : 0f;
	}

	private float cardHead() {
		return controlCenter() ? rowH() : CARD_HEAD;
	}

	private float rowH() {
		return controlCenter() ? 20f : ROW;
	}

	private void layout() {
		windowW = Math.min(menuW(), Math.max(1, width - 16));
		windowH = Math.min(menuH(), Math.max(1, height - 16));
		if (!placed) {
			StrayConfig config = StrayConfig.get();
			if (config.menuPlaced) {
				windowX = config.menuX;
				windowY = config.menuY;
			} else {
				windowX = (width - windowW) / 2f;
				windowY = (height - windowH) / 2f;
			}
			placed = true;
		}
		windowX = Mth.clamp(windowX, 4, Math.max(4, width - windowW - 4));
		windowY = Mth.clamp(windowY, 4, Math.max(4, height - windowH - 4));
	}

	private float localX(double mx) {
		return (float) ((mx - viewCx) / viewScale + viewCx);
	}

	private float localY(double my) {
		return (float) ((my - viewCy - viewLift) / viewScale + viewCy);
	}

	private float contentX() {
		return windowX + sidebarW() + pad();
	}

	private float contentW() {
		return windowW - sidebarW() - pad() * 2;
	}

	private float colW() {
		return (contentW() - COL_GAP) / 2f;
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (controlCenter()) {
			drawControlRail(graphics, font, mouseX, mouseY);
			return;
		}
		GuiDraw.title(graphics, font, "STRAY", windowX + 10, windowY + 8, Theme.TEXT);
		GuiDraw.small(graphics, font, "v" + modVersion(), windowX + 10 + GuiDraw.titleWidth(font, "STRAY") + 3, windowY + 10, Theme.ACCENT);
		GuiDraw.rounded(graphics, windowX + 10, windowY + 20, 16, 2, 1, Theme.ACCENT);
		hits.add(new Hit(windowX, windowY, SIDEBAR_W, 26, mx -> startDrag(mx, lastClickY), true));

		float y = windowY + 28;
		Group last = null;
		float activeY = y;
		float footY = windowY + windowH - 22;
		float[] rowY = new float[Tab.values().length];
		for (Tab value : Tab.values()) {
			if (value == Tab.PLAYER || value == Tab.SETTINGS) {
				continue;
			}
			if (value.group != last) {
				if (groupSize(value.group) > 1) {
					y += 4;
					GuiDraw.small(graphics, font, value.group.label, windowX + 10, y, Theme.HEADER);
					y += 9;
				} else if (last != null) {
					y += 2;
				}
				last = value.group;
			}
			rowY[value.ordinal()] = y;
			if (tab == value) {
				activeY = y;
			}
			hits.add(new Hit(windowX + 6, y, SIDEBAR_W - 12, 16, () -> selectTab(value)));
			y += 16;
		}
		if (tab != Tab.PLAYER) {
			if (navY < 0f) {
				navY = activeY;
			}
			navY = Anim.exp(navY, activeY, 18f, dt);
			GuiDraw.rounded(graphics, windowX + 6, navY, SIDEBAR_W - 12, 16, 8, Theme.NAV_PILL);
		} else {
			navY = -1f;
		}
		for (Tab value : Tab.values()) {
			if (value == Tab.PLAYER || value == Tab.SETTINGS) {
				continue;
			}
			float row = rowY[value.ordinal()];
			boolean on = tab == value;
			boolean hovered = GuiDraw.hovered(mouseX, mouseY, windowX + 6, row, SIDEBAR_W - 12, 16);
			float hover = anim("navh-" + value.name(), hovered && !on ? 1f : 0f);
			if (hover > 0.02f) {
				GuiDraw.rounded(graphics, windowX + 6, row, SIDEBAR_W - 12, 16, 8, Anim.fade(0x18FFFFFF, hover));
			}
			float onT = anim("navon-" + value.name(), on ? 1f : 0f);
			float labelY = GuiDraw.middle(row, 16);
			float iconH = CATEGORY_ICON_LINE * CATEGORY_ICON;
			GuiDraw.icon(
				graphics,
				font,
				tabGlyph(value),
				windowX + 9,
				row + (16 - iconH) * 0.5f,
				CATEGORY_ICON,
				Anim.mix(Theme.ACCENT, Theme.TEXT, onT)
			);
			GuiDraw.menu(graphics, font, value.label, windowX + 27, labelY, Anim.mix(Theme.MUTED, Theme.TEXT, onT));
		}

		GuiDraw.fill(graphics, windowX + 8, footY - 5, SIDEBAR_W - 16, 1, Theme.ACCENT);
		int face = 14;
		int faceX = Math.round(windowX + 10);
		int faceY = Math.round(footY);
		boolean footHover = GuiDraw.hovered(mouseX, mouseY, windowX + 6, footY - 2, SIDEBAR_W - 12, face + 4);
		boolean footOn = tab == Tab.PLAYER;
		if (footOn || footHover) {
			GuiDraw.rounded(graphics, windowX + 6, footY - 2, SIDEBAR_W - 12, face + 4, 6, footOn ? Theme.NAV_PILL : Anim.fade(0x18FFFFFF, 1f));
		}
		PlayerSkin skin = playerSkin();
		if (skin != null && skin.body() != null) {
			PlayerFaceExtractor.extractRenderState(graphics, skin, faceX, faceY, face);
		} else {
			GuiDraw.rounded(graphics, faceX, faceY, face, face, 3, Theme.ACCENT);
		}
		float nameX = windowX + 10 + face + 4;
		GuiDraw.menu(graphics, font, fitName(font, playerName(), (int) (SIDEBAR_W - 18 - face)), nameX, GuiDraw.middle(footY, face), Theme.TEXT);
		hits.add(new Hit(windowX + 6, footY - 2, SIDEBAR_W - 12, face + 4, () -> selectTab(Tab.PLAYER)));
	}

	private void applyPageTransform(GuiGraphicsExtractor graphics) {
		float t = pageT;
		if (t >= 0.995f) {
			return;
		}
		float cx = contentX() + contentW() * 0.5f;
		float cy = windowY + toolbarH() + Math.max(24f, (windowH - toolbarH()) * 0.5f);
		float slide = (1f - t) * 14f * pageDir;
		float lift = (1f - t) * 8f;
		float scale = 0.975f + 0.025f * t;
		graphics.pose().translate(cx + slide, cy + lift);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-cx, -cy);
	}

	private void selectTab(Tab value) {
		if (tab != value) {
			pageDir = value.ordinal() > tab.ordinal() ? 1f : -1f;
			pageT = 0f;
			if (tab.group != value.group) {
				tabLineX = -1f;
			}
		}
		tab = value;
		tabScroll = 0f;
		tabScrollMax = 0f;
		pickerTarget = null;
		searchOpen = false;
		featureOpen = false;
		capeFocused = tab == Tab.PLAYER;
		nickFocused = tab == Tab.PLAYER;
		mobSearchFocused = false;
		if (value == Tab.CATALOG) {
			ensureMobVisible = true;
		} else {
			mobFieldX = 0f;
			mobFieldY = 0f;
			mobFieldW = 0f;
			mobListX = 0f;
			mobListY = 0f;
			mobListW = 0f;
			mobListH = 0f;
		}
		if (value != Tab.PLAYERS) {
			nametagEspListX = 0f;
			nametagEspListY = 0f;
			nametagEspListW = 0f;
			nametagEspListH = 0f;
		}
		commitCapeUrl();
		StrayConfig config = StrayConfig.get();
		config.menuTab = value.name();
		config.save();
	}

	private static Tab parseTab(String name) {
		try {
			return Tab.valueOf(StrayConfig.normalizeMenuTab(name));
		} catch (IllegalArgumentException ignored) {
			return Tab.WORLD;
		}
	}

	private void drawMobsTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = contentX();
		float top = windowY + toolbarH() + 6;
		float col = colW();
		float right = left + col + COL_GAP;
		float ix = innerX(left);
		float rx = innerX(right);
		float iw = innerW(col);
		StrayConfig config = StrayConfig.get();

		float y;
		float namesTop;
		if (controlCenter()) {
			y = sectionLabel(graphics, font, left, top, "Glow");
			y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Mob glow", config.mobGlowEnabled, v -> config.mobGlowEnabled = v, Feature.MOB);
			float starH = cardHeight(Feature.STAR.rows);
			float starInner = featureCard(graphics, font, left, y, col, starH, "Star mobs");
			drawFeatureFields(graphics, font, mouseX, mouseY, ix, starInner, iw, Feature.STAR);
			y = y + starH + 8;
			y = sectionLabel(graphics, font, left, y, "World");
			y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Block outline", config.blockOutlineGlow, v -> config.blockOutlineGlow = v, Feature.BLOCK);
			y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Chest ESP", config.chestEspEnabled, v -> config.chestEspEnabled = v, Feature.CHEST);
			y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Fairy souls", config.fairySoulEsp, v -> config.fairySoulEsp = v, Feature.FAIRY);
			y = sectionLabel(graphics, font, left, y, "Items");
			y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Held item", config.heldItemShaderEnabled, v -> config.heldItemShaderEnabled = v, Feature.HELD_ITEM);
			namesTop = y;
		} else {
			y = featureCard(graphics, font, left, top, col, cardHeight(7), "Glow");
			y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Mob glow", config.mobGlowEnabled, v -> config.mobGlowEnabled = v, Feature.MOB);
			y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Star mobs", config.starMobEsp, v -> config.starMobEsp = v, Feature.STAR);
			y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Block outline", config.blockOutlineGlow, v -> config.blockOutlineGlow = v, Feature.BLOCK);
			y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Chest ESP", config.chestEspEnabled, v -> config.chestEspEnabled = v, Feature.CHEST);
			y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Player shader", config.playerFillEsp, v -> config.playerFillEsp = v, Feature.FILL);
			y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Nametags", config.nametagsEnabled, v -> config.nametagsEnabled = v, Feature.NAMETAGS);
			toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Own nametag", config.nametagSelf, v -> config.nametagSelf = v);
			float heldTop = top + cardHeight(7) + 8;
			y = featureCard(graphics, font, left, heldTop, col, cardHeight(1), "Held item");
			toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Shader", config.heldItemShaderEnabled, v -> config.heldItemShaderEnabled = v, Feature.HELD_ITEM);
			namesTop = heldTop + cardHeight(1) + 8;
		}

		List<String> nametags = config.nametagEspLabels();
		float namesH = Math.max(cardHeight(2), contentBottom() - namesTop);
		if (namesH >= cardHeight(0) + rowH()) {
			String namesTitle = nametags.isEmpty() ? "Nametag ESP" : "Nametag ESP  " + nametags.size();
			float namesY = featureCard(graphics, font, left, namesTop, col, namesH, namesTitle);
			drawNametagEspList(graphics, font, ix, namesY, iw, namesH - cardTop() - cardHead() - 4, mouseX, mouseY, true);
		}

		List<MobCatalog.Entry> entries = MobCatalog.filtered(mobQuery);
		float mobTop = top;
		if (controlCenter()) {
			y = sectionLabel(graphics, font, right, top, "Players");
			y = controlCard(graphics, font, right, y, col, mouseX, mouseY, "Player shader", config.playerFillEsp, v -> config.playerFillEsp = v, Feature.FILL);
			float tagTop = y;
			float tagH = cardHeight(Feature.NAMETAGS.rows + 1);
			float tagY = featureCard(graphics, font, right, tagTop, col, tagH, "Nametags", config.nametagsEnabled, v -> config.nametagsEnabled = v, mouseX, mouseY);
			fieldScope = Feature.NAMETAGS.name();
			tagY = toggle(graphics, font, rx, tagY, iw, mouseX, mouseY, "Own nametag", config.nametagSelf, v -> config.nametagSelf = v);
			drawFeatureFields(graphics, font, mouseX, mouseY, rx, tagY, iw, Feature.NAMETAGS);
			fieldScope = "";
			mobTop = sectionLabel(graphics, font, right, tagTop + tagH + 8, "Catalog");
		}
		// Player fill + Nametags sit above this list, so leftover window height is often
		// negative. Floor the card at ~10 rows so the catalog is actually visible.
		float listH = Math.max(cardHeight(10), contentBottom() - mobTop);
		featureCard(graphics, font, right, mobTop, col, listH, entries.isEmpty() ? "Mobs" : "Mobs  " + entries.size());
		float searchY = mobTop + cardTop() + cardHead();
		mobFieldX = rx;
		mobFieldY = searchY;
		mobFieldW = iw;
		boolean hoverSearch = GuiDraw.hovered(mouseX, mouseY, mobFieldX, mobFieldY, mobFieldW, 14);
		GuiDraw.panel(graphics, mobFieldX, mobFieldY, mobFieldW, 14, 5, mobSearchFocused || hoverSearch ? Theme.CARD_HOVER : Theme.PANEL, mobSearchFocused ? Theme.ACCENT : Theme.LINE);
		String shown = mobQuery.isEmpty() && !mobSearchFocused ? "Search mobs..." : mobQuery + (mobSearchFocused ? "|" : "");
		GuiDraw.menu(graphics, font, clip(font, shown, (int) mobFieldW - 10), mobFieldX + 5, GuiDraw.middle(mobFieldY, 14), mobQuery.isEmpty() && !mobSearchFocused ? fade() : ink());
		hits.add(new Hit(mobFieldX, mobFieldY, mobFieldW, 14, () -> {
			mobSearchFocused = true;
			capeFocused = false;
			nickFocused = false;
			searchOpen = false;
		}));

		float row = rowH();
		mobListX = rx;
		mobListY = searchY + 18;
		mobListW = iw;
		mobListH = Math.max(row, listH - cardTop() - cardHead() - 22);
		float contentH = entries.size() * row;
		float maxScroll = Math.max(0f, contentH - mobListH);
		if (ensureMobVisible) {
			for (int i = 0; i < entries.size(); i++) {
				if (config.isMobGlowSelected(entries.get(i).id().toString())) {
					mobScroll = Mth.clamp(i * row - mobListH * 0.4f, 0f, maxScroll);
					break;
				}
			}
			ensureMobVisible = false;
		}
		mobScroll = Mth.clamp(mobScroll, 0f, maxScroll);

		boolean clipped = GuiDraw.scissor(graphics, mobListX, mobListY, mobListW, mobListH);
		if (entries.isEmpty()) {
			GuiDraw.menu(graphics, font, "No matching mobs", mobListX + 2, GuiDraw.middle(mobListY, mobListH), fade());
		} else {
			int first = (int) (mobScroll / row);
			int last = Math.min(entries.size() - 1, first + (int) (mobListH / row) + 1);
			for (int i = first; i <= last; i++) {
				MobCatalog.Entry entry = entries.get(i);
				float iy = mobListY + i * row - mobScroll;
				boolean on = config.isMobGlowSelected(entry.id().toString());
				boolean hover = GuiDraw.hovered(mouseX, mouseY, mobListX, iy, mobListW, row)
					&& GuiDraw.hovered(mouseX, mouseY, mobListX, mobListY, mobListW, mobListH);
				if (on) {
					GuiDraw.rounded(graphics, mobListX - 2, iy, mobListW + 4, row, 5, Theme.withAlpha(Theme.ACCENT, 38));
					GuiDraw.rounded(graphics, mobListX - 2, iy + 3, 2, row - 6, 1, Theme.ACCENT);
				} else if (hover) {
					GuiDraw.rounded(graphics, mobListX - 2, iy, mobListW + 4, row, 5, 0x10FFFFFF);
				}
				GuiDraw.menu(graphics, font, clip(font, entry.name(), (int) mobListW - 8), mobListX + 6, GuiDraw.middle(iy, row), on ? ink() : fade());
				float hitY = Math.max(iy, mobListY);
				float hitB = Math.min(iy + row, mobListY + mobListH);
				if (hitB - hitY >= 3f) {
					hits.add(new Hit(mobListX, hitY, mobListW, hitB - hitY, () -> {
						config.toggleMobGlow(entry.id().toString());
						UnloadState.markDirty();
					}));
				}
			}
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}

		if (maxScroll > 1f) {
			float trackX = right + col - 5;
			float trackY = mobListY;
			float trackH = mobListH;
			GuiDraw.rounded(graphics, trackX, trackY, 2.4f, trackH, 1.2f, Theme.TRACK);
			float thumbH = Math.max(14f, trackH * trackH / (trackH + maxScroll));
			float thumbY = trackY + (mobScroll / maxScroll) * (trackH - thumbH);
			GuiDraw.rounded(graphics, trackX - 0.4f, thumbY, 3.2f, thumbH, 1.6f, Theme.ACCENT);
		}
	}

	private void drawCatalogTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = contentX();
		float top = windowY + toolbarH() + 6;
		float col = colW();
		float ix = innerX(left);
		StrayConfig config = StrayConfig.get();
		List<MobCatalog.Entry> entries = MobCatalog.filtered(mobQuery);

		float y = sectionLabel(graphics, font, left, top, "Catalog");
		float listH = Math.max(cardHeight(12), contentBottom() - y);
		featureCard(graphics, font, left, y, col + COL_GAP + col, listH, entries.isEmpty() ? "Mobs" : "Mobs  " + entries.size());
		float searchY = y + cardTop() + cardHead();
		mobFieldX = ix;
		mobFieldY = searchY;
		mobFieldW = innerW(col + COL_GAP + col);
		boolean hoverSearch = GuiDraw.hovered(mouseX, mouseY, mobFieldX, mobFieldY, mobFieldW, 14);
		GuiDraw.panel(graphics, mobFieldX, mobFieldY, mobFieldW, 14, 5, mobSearchFocused || hoverSearch ? Theme.CARD_HOVER : Theme.PANEL, mobSearchFocused ? Theme.ACCENT : Theme.LINE);
		String shown = mobQuery.isEmpty() && !mobSearchFocused ? "Search mobs..." : mobQuery + (mobSearchFocused ? "|" : "");
		GuiDraw.menu(graphics, font, clip(font, shown, (int) mobFieldW - 10), mobFieldX + 5, GuiDraw.middle(mobFieldY, 14), mobQuery.isEmpty() && !mobSearchFocused ? fade() : ink());
		hits.add(new Hit(mobFieldX, mobFieldY, mobFieldW, 14, () -> {
			mobSearchFocused = true;
			capeFocused = false;
			nickFocused = false;
			searchOpen = false;
		}));

		float row = rowH();
		mobListX = ix;
		mobListY = searchY + 18;
		mobListW = mobFieldW;
		mobListH = Math.max(row, listH - cardTop() - cardHead() - 22);
		float contentH = entries.size() * row;
		float maxScroll = Math.max(0f, contentH - mobListH);
		if (ensureMobVisible) {
			for (int i = 0; i < entries.size(); i++) {
				if (config.isMobGlowSelected(entries.get(i).id().toString())) {
					mobScroll = Mth.clamp(i * row - mobListH * 0.4f, 0f, maxScroll);
					break;
				}
			}
			ensureMobVisible = false;
		}
		mobScroll = Mth.clamp(mobScroll, 0f, maxScroll);

		boolean clipped = GuiDraw.scissor(graphics, mobListX, mobListY, mobListW, mobListH);
		if (entries.isEmpty()) {
			GuiDraw.menu(graphics, font, "No matching mobs", mobListX + 2, GuiDraw.middle(mobListY, mobListH), fade());
		} else {
			int first = (int) (mobScroll / row);
			int last = Math.min(entries.size() - 1, first + (int) (mobListH / row) + 1);
			for (int i = first; i <= last; i++) {
				MobCatalog.Entry entry = entries.get(i);
				float iy = mobListY + i * row - mobScroll;
				boolean on = config.isMobGlowSelected(entry.id().toString());
				boolean hover = GuiDraw.hovered(mouseX, mouseY, mobListX, iy, mobListW, row)
					&& GuiDraw.hovered(mouseX, mouseY, mobListX, mobListY, mobListW, mobListH);
				if (on) {
					GuiDraw.rounded(graphics, mobListX - 2, iy, mobListW + 4, row, 5, Theme.withAlpha(Theme.ACCENT, 38));
					GuiDraw.rounded(graphics, mobListX - 2, iy + 3, 2, row - 6, 1, Theme.ACCENT);
				} else if (hover) {
					GuiDraw.rounded(graphics, mobListX - 2, iy, mobListW + 4, row, 5, 0x10FFFFFF);
				}
				GuiDraw.menu(graphics, font, clip(font, entry.name(), (int) mobListW - 8), mobListX + 6, GuiDraw.middle(iy, row), on ? ink() : fade());
				float hitY = Math.max(iy, mobListY);
				float hitB = Math.min(iy + row, mobListY + mobListH);
				if (hitB - hitY >= 3f) {
					hits.add(new Hit(mobListX, hitY, mobListW, hitB - hitY, () -> {
						config.toggleMobGlow(entry.id().toString());
						UnloadState.markDirty();
					}));
				}
			}
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
		if (maxScroll > 1f) {
			float trackX = left + col + COL_GAP + col - 5;
			float trackY = mobListY;
			float trackH = mobListH;
			GuiDraw.rounded(graphics, trackX, trackY, 2.4f, trackH, 1.2f, Theme.TRACK);
			float thumbH = Math.max(14f, trackH * trackH / (trackH + maxScroll));
			float thumbY = trackY + (mobScroll / maxScroll) * (trackH - thumbH);
			GuiDraw.rounded(graphics, trackX - 0.4f, thumbY, 3.2f, thumbH, 1.6f, Theme.ACCENT);
		}
	}

	private void drawMiningLive(GuiGraphicsExtractor graphics, Font font, float x, float top, float col, float ix, float iw) {
		float y = sectionLabel(graphics, font, x, top, "Live");
		y = featureCard(graphics, font, x, y, col, cardTop() + cardHead() + 54 + cardPad(), "Session");
		var snap = MiningTracker.snapshot();
		GuiDraw.menu(graphics, font, snap.ability(), ix, y + 2, ink());
		GuiDraw.menu(graphics, font, snap.abilityReady() ? "Ready" : snap.abilityLabel(), ix, y + 14, snap.abilityReady() ? Theme.ACCENT : fade());
		String jobs = snap.commissions().isEmpty() ? "No commissions" : snap.commissions().size() + " commission" + (snap.commissions().size() == 1 ? "" : "s");
		GuiDraw.menu(graphics, font, jobs, ix, y + 26, fade());
		String titanium;
		int titaniumColor = fade();
		if (!MiningTracker.hasTitaniumCommission()) {
			titanium = "No titanium job";
		} else {
			MiningAreas.TitaniumFilter filter = MiningTracker.titaniumFilter();
			int count = TitaniumTracker.get().count();
			titanium = filter.unrestricted()
				? count + " titanium"
				: count + " in " + filter.label();
			titaniumColor = Theme.ACCENT;
		}
		GuiDraw.menu(graphics, font, clip(font, titanium, (int) iw - 4), ix, y + 38, titaniumColor);
	}

	private void drawFarmingContest(GuiGraphicsExtractor graphics, Font font, float x, float top, float col, float ix, float iw) {
		float y = sectionLabel(graphics, font, x, top, "Contest");
		y = featureCard(graphics, font, x, y, col, cardHeight(4), "Jacob");
		var contest = JacobContestTracker.snapshot();
		if (contest.present()) {
			GuiDraw.menu(graphics, font, clip(font, contest.crop() + "  " + contest.remaining(), (int) iw - 4), ix, y + 2, ink());
			GuiDraw.menu(graphics, font, String.format(Locale.ROOT, "%,d collected", contest.score()), ix, y + 16, fade());
			String projected = contest.projectedRank().name() + "  " + String.format(Locale.ROOT, "%,d", contest.projectedScore());
			GuiDraw.menu(graphics, font, clip(font, projected, (int) iw - 4), ix, y + 30, Theme.ACCENT);
			String rate = String.format(Locale.ROOT, "%,.0f/s · %,.0f/update", contest.perSecond(), contest.perUpdate());
			GuiDraw.small(graphics, font, clip(font, rate, (int) iw - 4), ix, y + 44, fade());
			return;
		}
		GuiDraw.menu(graphics, font, "No active Jacob contest", ix, y + 2, fade());
		GuiDraw.small(graphics, font, "Reads the live player-list widget", ix, y + 16, fade());
		if (minecraft.player != null && FarmingHud.holdingTool(minecraft.player)) {
			GuiDraw.menu(graphics, font, "Yaw  " + FarmingHud.yawLabel(minecraft.player), ix, y + 32, ink());
			GuiDraw.menu(graphics, font, "Pitch  " + FarmingHud.pitchLabel(minecraft.player), ix, y + 46, ink());
		}
	}

	private void drawPlayerTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = contentX();
		float top = windowY + toolbarH() + 6;
		float col = colW();
		float right = left + col + COL_GAP;
		float ix = innerX(left);
		float iw = innerW(col);
		StrayConfig config = StrayConfig.get();
		float modelH = windowY + windowH - PAD - top;
		featureCard(graphics, font, left, top, col, modelH, "You");
		previewX = ix;
		previewY = top + CARD_HEAD;
		previewW = iw;
		previewH = Math.max(48, modelH - cardTop() - cardHead() - cardPad());
		PlayerPreview.Drawn drawn = PlayerPreview.draw(
			graphics,
			previewX,
			previewY,
			previewW,
			previewH,
			0f,
			0f,
			new PlayerPreview.View(viewScale, viewCx, viewCy, viewLift)
		);
		NickHider.suppress();
		Component tag = config.nickEnabled ? NickHider.formattedNick() : Component.literal(playerName());
		NickHider.resume();
		if (drawn == null) {
			PlayerSkin skin = playerSkin();
			if (skin != null && skin.body() != null) {
				int face = 28;
				int fx = Math.round(previewX + (previewW - face) * 0.5f);
				int fy = Math.round(previewY + previewH * 0.55f - face * 0.5f);
				PlayerFaceExtractor.extractRenderState(graphics, skin, fx, fy, face);
				NametagRenderer.drawVanilla(graphics, font, previewX + previewW * 0.5f, fy - 12, tag);
			} else {
				GuiDraw.menu(graphics, font, "Join a world to preview", previewX + 4, previewY + previewH - 16, Theme.MUTED);
			}
		} else {
			NametagRenderer.drawVanilla(graphics, font, drawn.nameX(), drawn.nameY(), tag);
		}

		float nickH = cardHeight(1) + 56;
		float y = featureCard(graphics, font, right, top, col, nickH, "Nick");
		float rx = innerX(right);
		y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Replace my name", config.nickEnabled, v -> config.nickEnabled = v);
		GuiDraw.small(graphics, font, "Chat, tab, scoreboard. Use &6 &l.", rx, y + 1, Theme.MUTED);
		y += 12;
		nickFieldX = rx;
		nickFieldY = y;
		nickFieldW = iw;
		boolean hoverNick = GuiDraw.hovered(mouseX, mouseY, rx, y, iw, 16);
		GuiDraw.panel(graphics, rx, y, iw, 16, 5, nickFocused || hoverNick ? Theme.CARD_HOVER : Theme.CARD, nickFocused ? Theme.ACCENT : Theme.LINE);
		NickHider.suppress();
		String raw = config.nick == null ? "" : config.nick;
		String shown = raw.isEmpty() && !nickFocused ? "Nick  (&6Name)" : raw + (nickFocused ? "|" : "");
		GuiDraw.menu(graphics, font, clip(font, shown, (int) iw - 12), rx + 5, GuiDraw.middle(y, 16), raw.isEmpty() && !nickFocused ? Theme.MUTED : Theme.TEXT);
		NickHider.resume();
		hits.add(new Hit(rx, y, iw, 16, () -> {
			nickFocused = true;
			capeFocused = false;
			searchOpen = false;
		}));
		y += 22;
		GuiDraw.rounded(graphics, rx, y, iw, 22, 5, Theme.PANEL);
		NickHider.suppress();
		Component preview = config.nickEnabled ? NickHider.formattedNick() : Component.literal(playerName());
		if (preview.getString().isEmpty()) {
			GuiDraw.menu(graphics, font, "Name hidden", rx + 6, GuiDraw.middle(y, 22), Theme.MUTED);
		} else {
			graphics.pose().pushMatrix();
			graphics.pose().translate(rx + 6, GuiDraw.middle(y, 22));
			graphics.text(font, preview, 0, 0, 0xFFFFFFFF, false);
			graphics.pose().popMatrix();
		}
		NickHider.resume();

		drawCapeColumn(graphics, font, mouseX, mouseY, right, top + nickH + 8, col);
	}

	private void drawCapeColumn(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float right, float top, float col) {
		float rx = innerX(right);
		float iw = innerW(col);
		if (!capeFocused) {
			StrayConfig config = StrayConfig.get();
			if (config.capeUrl != null && !config.capeUrl.isBlank()) {
				capeUrlDraft = config.capeUrl;
			} else if (config.capePath != null && !config.capePath.isBlank()) {
				capeUrlDraft = config.capePath;
			} else if (CustomCape.status() == CustomCape.Status.EMPTY) {
				capeUrlDraft = "";
			}
		}

		float want = ShopCape.allowed() ? cardHeight(6) : cardHeight(3);
		float maxH = windowY + windowH - PAD - top;
		float h = Math.min(want, Math.max(cardHeight(3), maxH));
		float y = featureCard(graphics, font, right, top, col, h, "Cape");
		if (!ShopCape.allowed()) {
			String shop = ShopCape.publishStatus();
			GuiDraw.small(graphics, font, shop.isBlank() ? ShopCape.lockLabel() : shop, rx, y + 2, Theme.WARN);
			y += ROW;
			GuiDraw.panel(graphics, rx, y + 1, iw, ROW - 2, 5, Theme.CARD, Theme.LINE);
			GuiDraw.menu(graphics, font, "Cape locked", rx + 5, GuiDraw.middle(y, ROW), Theme.MUTED);
			y += ROW;
			capeRefreshRow(graphics, font, mouseX, mouseY, rx, y, iw);
			return;
		}
		String shop = ShopCape.publishStatus();
		String label = !shop.isBlank() ? shop : CustomCape.statusLabel();
		GuiDraw.small(graphics, font, label, rx, y + 2, CustomCape.status() == CustomCape.Status.ERROR || shop.contains("failed") || shop.contains("not whitelisted") || shop.contains("Need") || shop.contains("Wrong") ? Theme.WARN : Theme.MUTED);
		y += ROW;

		capeFieldX = rx;
		capeFieldY = y;
		capeFieldW = iw;
		boolean hoverField = GuiDraw.hovered(mouseX, mouseY, rx, y, iw, ROW);
		GuiDraw.panel(graphics, rx, y, iw, ROW, 4, capeFocused || hoverField ? Theme.CARD_HOVER : Theme.CARD, capeFocused ? Theme.ACCENT : Theme.LINE);
		NickHider.suppress();
		String shown = capeUrlDraft.isEmpty() && !capeFocused ? "https://...png" : capeUrlDraft + (capeFocused ? "|" : "");
		GuiDraw.menu(graphics, font, clip(font, shown, (int) iw - 10), rx + 5, GuiDraw.middle(y, ROW), capeUrlDraft.isEmpty() && !capeFocused ? Theme.MUTED : Theme.TEXT);
		NickHider.resume();
		hits.add(new Hit(rx, y, iw, ROW, () -> {
			capeFocused = true;
			nickFocused = false;
			searchOpen = false;
		}));
		y += ROW;

		boolean picking = CustomCape.picking();
		boolean hoverFile = !picking && GuiDraw.hovered(mouseX, mouseY, rx, y, iw, ROW);
		GuiDraw.panel(graphics, rx, y + 1, iw, ROW - 2, 5, hoverFile ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.menu(graphics, font, picking ? "Selecting…" : "Local file...", rx + 5, GuiDraw.middle(y, ROW), picking ? Theme.MUTED : Theme.TEXT);
		hits.add(new Hit(rx, y, iw, ROW, CustomCape::pickLocal));
		y += ROW;

		boolean hoverCreate = !picking && GuiDraw.hovered(mouseX, mouseY, rx, y, iw, ROW);
		GuiDraw.panel(graphics, rx, y + 1, iw, ROW - 2, 5, hoverCreate ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.menu(graphics, font, picking ? "Selecting…" : "Create cape...", rx + 5, GuiDraw.middle(y, ROW), picking ? Theme.MUTED : Theme.TEXT);
		hits.add(new Hit(rx, y, iw, ROW, CustomCape::pickCreate));
		y += ROW;

		boolean hoverClear = GuiDraw.hovered(mouseX, mouseY, rx, y, iw, ROW);
		GuiDraw.panel(graphics, rx, y + 1, iw, ROW - 2, 5, hoverClear ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.menu(graphics, font, "Remove cape", rx + 5, GuiDraw.middle(y, ROW), Theme.TEXT);
		hits.add(new Hit(rx, y, iw, ROW, () -> {
			capeUrlDraft = "";
			CustomCape.clear();
		}));
		y += ROW;
		capeRefreshRow(graphics, font, mouseX, mouseY, rx, y, iw);
	}

	private void capeRefreshRow(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float rx, float y, float iw) {
		boolean ready = ShopCape.refreshReady();
		boolean hover = ready && GuiDraw.hovered(mouseX, mouseY, rx, y, iw, ROW);
		GuiDraw.panel(graphics, rx, y + 1, iw, ROW - 2, 5, hover ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.menu(graphics, font, ShopCape.refreshLabel(), rx + 5, GuiDraw.middle(y, ROW), ready ? Theme.TEXT : Theme.MUTED);
		hits.add(new Hit(rx, y, iw, ROW, ShopCape::refreshAll));
	}

	private void commitCapeUrl() {
		if (!ShopCape.allowed()) {
			return;
		}
		String draft = capeUrlDraft.trim();
		StrayConfig config = StrayConfig.get();
		if (draft.isEmpty()) {
			if ((config.capeUrl == null || config.capeUrl.isBlank()) && (config.capePath == null || config.capePath.isBlank())) {
				return;
			}
			CustomCape.clear();
			return;
		}
		if (draft.equals(config.capeUrl) || draft.equals(config.capePath)) {
			return;
		}
		if (draft.startsWith("http://") || draft.startsWith("https://")) {
			CustomCape.applyUrl(draft);
		}
	}

	private String playerName() {
		if (StrayConfig.get().nickEnabled) {
			String nick = NickHider.plainNick();
			if (!nick.isBlank()) {
				return nick;
			}
		}
		String name = minecraft.getGameProfile().name();
		if (name == null || name.isBlank()) {
			return "Player";
		}
		return name;
	}

	private PlayerSkin playerSkin() {
		if (minecraft.player != null) {
			return minecraft.player.getSkin();
		}
		return minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), true).get();
	}

	private static String fitName(Font font, String name, int maxWidth) {
		if (GuiDraw.menuWidth(font, name) <= maxWidth) {
			return name;
		}
		String trimmed = name;
		while (trimmed.length() > 1 && GuiDraw.menuWidth(font, trimmed + "..") > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	private void drawControlRail(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float railX = windowX + ControlChrome.RAIL_INSET;
		float railY = windowY + ControlChrome.RAIL_INSET;
		float railW = ControlChrome.RAIL_W;
		float railH = windowH - ControlChrome.RAIL_INSET * 2f;
		ControlChrome.rail(graphics, railX, railY, railW, railH);
		hits.add(new Hit(windowX, windowY, sidebarW(), windowH, mx -> startDrag(mx, lastClickY), true));

		Group[] groups = {
			Group.WORLD, Group.ESP, Group.COMBAT, Group.HUD,
			Group.MINING, Group.FARMING, Group.MISC, Group.THEME
		};
		float top = 8f;
		float markH = 36f;
		float slot = Math.min(36f, (railH - top - markH) / groups.length);
		float iy = railY + top;
		float selectedY = iy;
		for (Group group : groups) {
			if (tab.group == group) {
				selectedY = iy;
			}
			iy += slot;
		}
		if (railNavY < 0f) {
			railNavY = selectedY;
		}
		railNavY = Anim.exp(railNavY, selectedY, 18f, dt);
		float px = railX + 6;
		float py = railNavY + 1;
		float pw = railW - 12;
		float ph = slot - 2;
		float pr = Math.min(11f, ph * 0.5f);
		GuiDraw.rounded(graphics, px, py, pw, ph, pr, ControlChrome.selectedFill());
		ControlChrome.rim(graphics, px, py, pw, ph, pr);

		iy = railY + top;
		for (Group group : groups) {
			boolean on = tab.group == group;
			boolean hover = GuiDraw.hovered(mouseX, mouseY, railX + 4, iy, railW - 8, slot);
			float t = anim("cc-nav-" + group.name(), hover && !on ? 1f : 0f);
			if (t > 0.02f) {
				GuiDraw.rounded(graphics, railX + 6, iy + 1, railW - 12, slot - 2, pr, Anim.fade(ControlChrome.selectedFill(), t * 0.45f));
			}
			String glyph = groupGlyph(group);
			float onT = anim("cc-on-" + group.name(), on ? 1f : 0f);
			int icon = Anim.mix(ControlChrome.muted(), ControlChrome.text(), onT);
			float iconH = CATEGORY_ICON_LINE * CATEGORY_ICON;
			float iconW = GuiDraw.iconWidth(font, glyph, CATEGORY_ICON);
			float capH = 10f;
			float stack = iconH + 1.5f + capH;
			float sy = iy + (slot - stack) * 0.5f;
			GuiDraw.icon(
				graphics,
				font,
				glyph,
				railX + (railW - iconW) * 0.5f,
				sy,
				CATEGORY_ICON,
				icon
			);
			drawRailCaption(graphics, font, group.caption, railX + railW * 0.5f, sy + iconH + 1.5f, railW - 8f, icon);
			hits.add(new Hit(railX + 4, iy, railW - 8, slot, () -> openControlGroup(group)));
			iy += slot;
		}
		drawControlMark(graphics, font, railX, railY + railH - markH, railW);
	}

	private void drawControlMark(GuiGraphicsExtractor graphics, Font font, float railX, float y, float railW) {
		float scale = 1.50f;
		float gw = GuiDraw.iconWidth(font, MenuFont.CAT, scale);
		float gy = y + 4f;
		GuiDraw.icon(
			graphics,
			font,
			MenuFont.CAT,
			railX + (railW - gw) * 0.5f,
			gy,
			scale,
			ControlChrome.accent()
		);
		drawRailCaption(graphics, font, modVersion(), railX + railW * 0.5f, gy + 9f * scale + 1f, railW - 8f, ControlChrome.accent());
	}

	private static int groupSize(Group group) {
		int count = 0;
		for (Tab value : Tab.values()) {
			if (value.group == group) {
				count++;
			}
		}
		return count;
	}

	private static String groupGlyph(Group group) {
		return group.glyph;
	}

	private static void drawRailCaption(
		GuiGraphicsExtractor graphics,
		Font font,
		String label,
		float centerX,
		float y,
		float maxW,
		int color
	) {
		float scale = MenuFont.smallScale() * 0.94f;
		float width = font.width(MenuFont.small(label)) * scale;
		if (width > maxW && width > 0.5f) {
			scale *= maxW / width;
			width = maxW;
		}
		GuiDraw.text(graphics, font, MenuFont.small(label), centerX - width * 0.5f, MenuFont.menuY(y, scale), scale, color, false);
	}

	private void openControlGroup(Group group) {
		settingsOpen = false;
		notesOpen = false;
		searchOpen = false;
		featureOpen = false;
		fontPickerOpen = false;
		fontSearchFocused = false;
		selectGroup(group);
	}

	private void selectGroup(Group group) {
		if (group == Group.PLAYER) {
			selectTab(Tab.PLAYER);
			return;
		}
		if (tab.group == group) {
			return;
		}
		for (Tab value : Tab.values()) {
			if (value.group == group) {
				selectTab(value);
				return;
			}
		}
	}

	private void drawControlHeader(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = contentX();
		float y = windowY + 10;
		float w = contentW();
		hits.add(new Hit(windowX + sidebarW(), windowY, windowW - sidebarW(), toolbarH(), mx -> startDrag(mx, lastClickY), true));

		float tabX = x;
		float activeX = x;
		float activeW = 0f;
		for (Tab value : Tab.values()) {
			if (value.group != tab.group) {
				continue;
			}
			boolean on = tab == value;
			float textW = GuiDraw.menuWidth(font, value.label);
			float tw = textW + 16;
			float onT = anim("hdr-" + value.name(), on ? 1f : 0f);
			int color = Anim.mix(ControlChrome.muted(), ControlChrome.text(), onT);
			GuiDraw.menu(graphics, font, value.label, tabX, GuiDraw.middle(y, 22), color);
			if (on) {
				activeX = tabX;
				activeW = textW;
			}
			hits.add(new Hit(tabX, y, tw, 22, () -> selectTab(value)));
			tabX += tw + 6;
		}
		if (activeW > 0f) {
			if (tabLineX < 0f) {
				tabLineX = activeX;
				tabLineW = activeW;
			}
			tabLineX = Anim.exp(tabLineX, activeX, 18f, dt);
			tabLineW = Anim.exp(tabLineW, activeW, 18f, dt);
			GuiDraw.rounded(graphics, tabLineX, y + 19, tabLineW, 1.4f, 0.7f, ControlChrome.text());
		}

		float face = 22;
		float faceX = x + w - face;
		float hudX = faceX - 26;
		float bellX = hudX - 24;
		float searchW = 110;
		float searchX = bellX - 8 - searchW;
		searchFieldX = searchX;
		searchFieldW = searchW;
		ControlChrome.search(graphics, searchX, y, searchW, 22);
		GuiDraw.icon(graphics, font, MenuFont.SEARCH, searchX + 8, GuiDraw.middle(y, 22), ControlChrome.muted());
		String shown = searchQuery.isEmpty() ? "Search" : searchQuery + (searchOpen ? "|" : "");
		GuiDraw.menu(graphics, font, clip(font, shown, (int) searchW - 28), searchX + 22, GuiDraw.middle(y, 22), searchQuery.isEmpty() ? ControlChrome.muted() : ControlChrome.text());
		hits.add(new Hit(searchX, y, searchW, 22, () -> {
			searchOpen = true;
			settingsOpen = false;
			notesOpen = false;
			featureOpen = false;
		}));

		boolean bellHover = GuiDraw.hovered(mouseX, mouseY, bellX, y, 22, 22);
		if (bellHover || notesOpen) {
			GuiDraw.circle(graphics, bellX + 11, y + 11, 10, 0x33FFFFFF);
		}
		drawCenteredIcon(graphics, font, MenuFont.BELL, bellX, y, 22, notesOpen ? ControlChrome.text() : ControlChrome.muted());
		if (ReleaseNotes.unread() && !notesOpen) {
			GuiDraw.circle(graphics, bellX + 16, y + 5, 2.1f, Theme.ACCENT);
		}
		hits.add(new Hit(bellX, y, 22, 22, () -> {
			notesOpen = !notesOpen;
			settingsOpen = false;
			searchOpen = false;
			featureOpen = false;
			if (notesOpen) {
				ReleaseNotes.markSeen();
			}
		}));

		boolean hudHover = GuiDraw.hovered(mouseX, mouseY, hudX, y, 22, 22);
		if (hudHover) {
			GuiDraw.circle(graphics, hudX + 11, y + 11, 10, 0x33FFFFFF);
		}
		drawCenteredIcon(graphics, font, MenuFont.HUD, hudX, y, 22, ControlChrome.text());
		hits.add(new Hit(hudX, y, 22, 22, () -> minecraft.setScreen(new HudEditorScreen())));

		ControlChrome.face(graphics, faceX, y, face, playerSkin());
		hits.add(new Hit(faceX, y, face, 22, () -> {
			settingsOpen = false;
			selectTab(Tab.PLAYER);
		}));
	}

	private static String tabGlyph(Tab value) {
		return switch (value) {
			case WORLD, CAMERA -> MenuFont.GLOBE;
			case COMBAT, ASSIST -> MenuFont.SWORD;
			case ESP, PLAYERS, CATALOG -> MenuFont.EYE;
			case OVERLAY, MEDIA -> MenuFont.DISPLAY;
			case BARS -> MenuFont.BARS;
			case NODES -> MenuFont.PIN;
			case MINING, HOLLOWS -> MenuFont.DIAMOND;
			case FARMING, GARDEN, GREENHOUSE -> MenuFont.GRAIN;
			case MENUS, KEYS -> MenuFont.HANGER;
			case STATUS -> MenuFont.SPEED;
			case PLAYER -> MenuFont.PERSON;
			case SETTINGS -> MenuFont.PALETTE;
		};
	}

	private void drawToolbar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (controlCenter()) {
			drawControlHeader(graphics, font, mouseX, mouseY);
			return;
		}
		float x = contentX();
		float y = windowY + 5;
		float w = contentW();
		GuiDraw.hline(graphics, windowX + SIDEBAR_W + 1, windowY + TOOLBAR_H - 1, windowW - SIDEBAR_W - 1, Theme.LINE);
		hits.add(new Hit(windowX + SIDEBAR_W, windowY, windowW - SIDEBAR_W, TOOLBAR_H, mx -> startDrag(mx, lastClickY), true));

		float labelY = GuiDraw.middle(y, 14);
		boolean hudHover = GuiDraw.hovered(mouseX, mouseY, x, y, ACTION_W, 14);
		GuiDraw.panel(graphics, x, y, ACTION_W, 14, 5, hudHover ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.menu(graphics, font, "HUD", x + (ACTION_W - GuiDraw.menuWidth(font, "HUD")) / 2f, labelY, Theme.TEXT);
		hits.add(new Hit(x, y, ACTION_W, 14, () -> minecraft.setScreen(new HudEditorScreen())));

		float titleX = x + ACTION_W + 8;
		float searchMax = w - ACTION_W - 8 - ICON_SLOT * 3 - 8;
		searchFieldX = titleX;
		searchFieldW = Mth.lerp(searchT, 72, Math.max(72, searchMax));

		if (searchT > 0.08f) {
			GuiDraw.panel(graphics, searchFieldX, y, searchFieldW, 14, 5, Theme.CARD_HOVER, Theme.ACCENT);
			String shown = searchQuery.isEmpty() ? "Search settings..." : searchQuery + (searchOpen ? "|" : "");
			int color = searchQuery.isEmpty() ? Theme.MUTED : Theme.TEXT;
			GuiDraw.menu(graphics, font, clip(font, shown, (int) searchFieldW - 10), searchFieldX + 6, labelY, color);
			hits.add(new Hit(searchFieldX, y, searchFieldW, 14, () -> searchOpen = true));
		} else {
			GuiDraw.menu(graphics, font, tab.label, titleX, labelY, Theme.HEADER);
		}

		float iconX = x + w - ICON_SLOT * 3;
		drawIconButton(graphics, font, mouseX, mouseY, iconX, y, MenuFont.SETTINGS, settingsOpen, () -> {
			settingsOpen = !settingsOpen;
			notesOpen = false;
			searchOpen = false;
			featureOpen = false;
			if (!settingsOpen) {
				fontPickerOpen = false;
				fontSearchFocused = false;
			}
		});
		drawIconButton(graphics, font, mouseX, mouseY, iconX + ICON_SLOT, y, MenuFont.BELL, notesOpen, () -> {
			notesOpen = !notesOpen;
			settingsOpen = false;
			searchOpen = false;
			featureOpen = false;
			fontPickerOpen = false;
			fontSearchFocused = false;
			if (notesOpen) {
				ReleaseNotes.markSeen();
			}
		});
		if (ReleaseNotes.unread() && !notesOpen) {
			GuiDraw.circle(graphics, iconX + ICON_SLOT + ICON_SLOT - 2.5f, y + 3.2f, 2.1f, Theme.ACCENT);
		}
		drawIconButton(graphics, font, mouseX, mouseY, iconX + ICON_SLOT * 2, y, MenuFont.SEARCH, searchOpen, () -> {
			searchOpen = !searchOpen;
			settingsOpen = false;
			notesOpen = false;
			featureOpen = false;
			fontPickerOpen = false;
			fontSearchFocused = false;
			if (!searchOpen) {
				searchQuery = "";
			}
		});
	}

	private void drawIconButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, String glyph, boolean active, Runnable click) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, ICON_SLOT, 14);
		float t = anim("icon-" + glyph, hover || active ? 1f : 0f);
		if (t > 0.02f) {
			GuiDraw.rounded(graphics, x, y, ICON_SLOT, 14, 4, Anim.fade(Theme.withAlpha(Theme.ACCENT, 40), t));
		}
		float iw = GuiDraw.iconWidth(font, glyph);
		GuiDraw.icon(graphics, font, glyph, x + (ICON_SLOT - iw) * 0.5f, GuiDraw.middle(y, 14), active ? Theme.ACCENT : Theme.MUTED);
		hits.add(new Hit(x, y, ICON_SLOT, 14, click));
	}

	private static void drawCenteredIcon(
		GuiGraphicsExtractor graphics,
		Font font,
		String glyph,
		float x,
		float y,
		float size,
		int color
	) {
		float iw = GuiDraw.iconWidth(font, glyph);
		float ih = 9.0f;
		GuiDraw.icon(graphics, font, glyph, x + (size - iw) * 0.5f, y + (size - ih) * 0.5f, color);
	}

	private static String clip(Font font, String value, int maxWidth) {
		if (GuiDraw.menuWidth(font, value) <= maxWidth) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && GuiDraw.menuWidth(font, trimmed + "..") > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	private void drawSearchResults(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		List<SearchEntry> matches = matches();
		float top = windowY + toolbarH() + 2;
		if (matches.isEmpty()) {
			if (controlCenter()) {
				ControlChrome.sheet(graphics, searchFieldX, top, searchFieldW, 20);
			} else {
				GuiDraw.panel(graphics, searchFieldX, top, searchFieldW, 20, 6, Anim.fade(Theme.PANEL, searchT), Theme.LINE);
			}
			GuiDraw.menu(graphics, font, "No matches", searchFieldX + 8, GuiDraw.middle(top, 20), controlCenter() ? ControlChrome.muted() : Theme.MUTED);
			return;
		}
		float h = matches.size() * 16 + 6;
		if (controlCenter()) {
			ControlChrome.sheet(graphics, searchFieldX, top, searchFieldW, h);
		} else {
			GuiDraw.panel(graphics, searchFieldX, top, searchFieldW, h, 6, Anim.fade(Theme.PANEL, searchT), Theme.LINE);
		}
		float iy = top + 3;
		for (SearchEntry entry : matches) {
			boolean hover = GuiDraw.hovered(mouseX, mouseY, searchFieldX, iy, searchFieldW, 16);
			if (hover) {
				GuiDraw.rounded(graphics, searchFieldX + 2, iy, searchFieldW - 4, 16, 4, Theme.withAlpha(Theme.ACCENT, 28));
			}
			GuiDraw.menu(graphics, font, entry.label, searchFieldX + 8, GuiDraw.middle(iy, 16), hover ? Theme.ACCENT : Theme.TEXT);
			GuiDraw.small(graphics, font, entry.hint, searchFieldX + searchFieldW - GuiDraw.smallWidth(font, entry.hint) - 8, GuiDraw.middle(iy, 16) + 1, Theme.MUTED);
			hits.add(new Hit(searchFieldX, iy, searchFieldW, 16, () -> {
				openSearch(entry);
			}));
			iy += 16;
		}
	}

	private List<SearchEntry> matches() {
		String q = searchQuery.trim().toLowerCase(Locale.ROOT);
		List<SearchEntry> out = new ArrayList<>();
		if (q.isEmpty()) {
			return out;
		}
		for (SearchEntry entry : SEARCH) {
			if (entry.label.toLowerCase(Locale.ROOT).contains(q) || entry.hint.toLowerCase(Locale.ROOT).contains(q) || entry.tab.label.toLowerCase(Locale.ROOT).contains(q)) {
				out.add(entry);
			}
		}
		return out;
	}

	private void openSearch(SearchEntry entry) {
		searchQuery = "";
		searchOpen = false;
		if (entry.tab == Tab.SETTINGS && !controlCenter()) {
			settingsOpen = true;
			notesOpen = false;
			featureOpen = false;
			return;
		}
		selectTab(entry.tab);
	}

	private void drawSettings(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		settingsX = contentX() + contentW() - PANEL_W;
		settingsY = windowY + toolbarH() + 2;
		float sheetH = settingsHeight() * Math.max(0.2f, settingsT);
		if (controlCenter()) {
			ControlChrome.sheet(graphics, settingsX, settingsY, PANEL_W, sheetH);
		} else {
			GuiDraw.sheet(graphics, settingsX, settingsY, PANEL_W, sheetH, 8, Anim.fade(Theme.SHEET, settingsT), Anim.fade(Theme.ACCENT, settingsT));
		}
		if (settingsT < 0.85f) {
			return;
		}
		GuiDraw.menu(graphics, font, "Theme", settingsX + 8, settingsY + 6, Theme.HEADER);
		float y = colorRow(graphics, font, settingsX + 8, settingsY + 20, PANEL_W - 16, mouseX, mouseY, "Glass", StrayConfig.get().controlPaneRgb, PickerTarget.CONTROL);
		y = colorRow(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Pills", StrayConfig.get().controlPillRgb, PickerTarget.PILL);
		y = slider(graphics, font, settingsX + 8, y, PANEL_W - 16, "Frost", Math.round(StrayConfig.get().controlFrost * 100) + "%", StrayConfig.get().controlFrost, v -> StrayConfig.get().controlFrost = StrayConfig.clamp(v, 0f, 1f));
		GuiDraw.small(graphics, font, "Accent", settingsX + 8, y + 2, controlCenter() ? ControlChrome.muted() : Theme.MUTED);
		y = swatchRow(graphics, mouseX, mouseY, settingsX + 10, y + 14, PANEL_W - 26, Theme.PRESETS, true);
		y = colorRow(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Custom", StrayConfig.get().themeAccentRgb, PickerTarget.THEME);
		GuiDraw.small(graphics, font, "Pane", settingsX + 8, y + 1, Theme.MUTED);
		y = swatchRow(graphics, mouseX, mouseY, settingsX + 10, y + 12, PANEL_W - 26, Theme.PANE_PRESETS, false);
		y = colorRow(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Custom", StrayConfig.get().themePaneRgb, PickerTarget.PANE);
		GuiDraw.small(graphics, font, "Scale", settingsX + 8, y + 1, Theme.MUTED);
		y += 12;
		y = chipRow(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, new String[]{"100%", "90%", "75%", "50%"}, menuScaleChip(), index -> {
			float[] values = {1.00f, 0.90f, 0.75f, 0.50f};
			StrayConfig.get().menuScale = values[index];
		});
		y = slider(graphics, font, settingsX + 8, y, PANEL_W - 16, "HUD", Math.round(StrayConfig.get().hudOpacity * 100) + "%", (StrayConfig.get().hudOpacity - 0.20f) / 0.80f, v -> {
			StrayConfig.get().hudOpacity = StrayConfig.clamp(0.20f + v * 0.80f, 0.20f, 1f);
			Theme.refresh();
		});
		y = toggle(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Menu stars", StrayConfig.get().menuStarfield, v -> StrayConfig.get().menuStarfield = v);
		y = toggle(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "HUD stars", StrayConfig.get().hudStarfield, v -> StrayConfig.get().hudStarfield = v);
		y = toggle(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Animations", StrayConfig.get().uiAnimations, v -> StrayConfig.get().uiAnimations = v);
		y = toggle(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Auto update", StrayConfig.get().autoUpdate, v -> StrayConfig.get().autoUpdate = v);
		toggle(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Update notify", StrayConfig.get().updateNotify, v -> StrayConfig.get().updateNotify = v);
	}

	private float settingsHeight() {
		return SETTINGS_H;
	}

	private static String currentFontLabel() {
		return fontLabel(StrayConfig.get().uiFont);
	}

	private static String fontLabel(String family) {
		if (family == null || family.isBlank()) {
			return "Nunito";
		}
		if (MenuFont.minecraftFamily(family)) {
			return MenuFont.MINECRAFT_FAMILY;
		}
		return family;
	}

	private List<String> fontMatches() {
		String q = fontQuery.trim().toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		if (q.isEmpty() || "nunito".contains(q)) {
			out.add("");
		}
		if (q.isEmpty() || "minecraft".contains(q) || "vanilla".contains(q)) {
			out.add(MenuFont.MINECRAFT_FAMILY);
		}
		for (String family : SystemFonts.families()) {
			if (MenuFont.minecraftFamily(family)) {
				continue;
			}
			if (q.isEmpty() || family.toLowerCase(Locale.ROOT).contains(q)) {
				out.add(family);
			}
		}
		return out;
	}

	private void drawFontPicker(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY) {
		GuiDraw.small(graphics, font, "Font", x, y + 1, Theme.MUTED);
		y += 12;
		float rowY = y;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, rowY, w, ROW);
		GuiDraw.panel(graphics, x, rowY + 1, w, ROW - 2, 5, hover || fontPickerOpen ? Theme.CARD_HOVER : Theme.CARD, fontPickerOpen ? Theme.ACCENT : Theme.LINE);
		GuiDraw.menu(graphics, font, clip(font, currentFontLabel(), (int) w - 18), x + 5, GuiDraw.middle(rowY, ROW), Theme.TEXT);
		GuiDraw.small(graphics, font, fontPickerOpen ? "^" : "v", x + w - 8 - GuiDraw.smallWidth(font, fontPickerOpen ? "^" : "v"), GuiDraw.middle(rowY, ROW) + 1, Theme.MUTED);
		hits.add(new Hit(x, rowY, w, ROW, () -> {
			fontPickerOpen = !fontPickerOpen;
			fontSearchFocused = fontPickerOpen;
			if (fontPickerOpen) {
				fontQuery = "";
				fontScroll = 0f;
			}
		}));
		if (!fontPickerOpen) {
			return;
		}
		float searchY = rowY + ROW + 2;
		fontListX = x;
		fontListY = searchY + FONT_SEARCH_H + 2;
		fontListW = w;
		fontListH = FONT_VISIBLE * FONT_ROW;
		GuiDraw.panel(graphics, x, searchY, w, FONT_SEARCH_H + 2 + fontListH + 2, 5, Theme.CARD, Theme.LINE);
		boolean searchHover = GuiDraw.hovered(mouseX, mouseY, x, searchY, w, FONT_SEARCH_H);
		if (fontSearchFocused || searchHover) {
			GuiDraw.rounded(graphics, x + 1, searchY + 1, w - 2, FONT_SEARCH_H - 1, 4, Theme.CARD_HOVER);
		}
		String shown = fontQuery.isEmpty() ? (fontSearchFocused ? "|" : "Search fonts") : fontQuery + (fontSearchFocused ? "|" : "");
		GuiDraw.menu(graphics, font, clip(font, shown, (int) w - 10), x + 5, GuiDraw.middle(searchY, FONT_SEARCH_H), fontQuery.isEmpty() && !fontSearchFocused ? Theme.MUTED : Theme.TEXT);
		hits.add(new Hit(x, searchY, w, FONT_SEARCH_H, () -> fontSearchFocused = true));
		List<String> matches = fontMatches();
		float maxScroll = Math.max(0f, matches.size() * FONT_ROW - fontListH);
		fontScroll = Mth.clamp(fontScroll, 0f, maxScroll);
		boolean clip = GuiDraw.scissor(graphics, fontListX, fontListY, fontListW, fontListH);
		if (matches.isEmpty()) {
			GuiDraw.menu(graphics, font, "No fonts found", fontListX + 4, GuiDraw.middle(fontListY, fontListH), Theme.MUTED);
		} else {
			float iy = fontListY - fontScroll;
			String selected = StrayConfig.get().uiFont == null ? "" : StrayConfig.get().uiFont;
			for (String family : matches) {
				if (iy + FONT_ROW > fontListY && iy < fontListY + fontListH) {
					boolean on = family.equals(selected) || (family.isEmpty() && selected.isBlank()) || (MenuFont.minecraftFamily(family) && MenuFont.minecraftFamily(selected));
					boolean rowHover = GuiDraw.hovered(mouseX, mouseY, fontListX, iy, fontListW, FONT_ROW)
						&& GuiDraw.hovered(mouseX, mouseY, fontListX, fontListY, fontListW, fontListH);
					if (on || rowHover) {
						GuiDraw.rounded(graphics, fontListX, iy, fontListW, FONT_ROW, 3, Theme.withAlpha(Theme.ACCENT, on ? 40 : 22));
					}
					String label = family.isEmpty() ? "Nunito (default)" : fontLabel(family);
					GuiDraw.menu(graphics, font, clip(font, label, (int) fontListW - 10), fontListX + 5, GuiDraw.middle(iy, FONT_ROW), on ? Theme.ACCENT : Theme.TEXT);
					String pick = family;
					float hitY = Math.max(iy, fontListY);
					float hitB = Math.min(iy + FONT_ROW, fontListY + fontListH);
					if (hitB - hitY >= 3f) {
						hits.add(new Hit(fontListX, hitY, fontListW, hitB - hitY, () -> pickFont(pick)));
					}
				}
				iy += FONT_ROW;
			}
		}
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
		if (maxScroll > 1f) {
			float trackX = x + w - 4;
			float trackH = fontListH - 4;
			float trackY = fontListY + 2;
			GuiDraw.rounded(graphics, trackX, trackY, 2.4f, trackH, 1.2f, Theme.TRACK);
			float thumbH = Math.max(12f, trackH * trackH / (trackH + maxScroll));
			float thumbY = trackY + (fontScroll / maxScroll) * (trackH - thumbH);
			GuiDraw.rounded(graphics, trackX - 0.4f, thumbY, 3.2f, thumbH, 1.6f, Theme.ACCENT);
		}
	}

	private void pickFont(String family) {
		String current = StrayConfig.get().uiFont == null ? "" : StrayConfig.get().uiFont;
		String next = family == null ? "" : family;
		if (next.equals(current) || (next.isEmpty() && current.isBlank()) || (MenuFont.minecraftFamily(next) && MenuFont.minecraftFamily(current))) {
			return;
		}
		UiFontPack.apply(next);
	}

	private static int menuScaleChip() {
		float scale = StrayConfig.normalizeMenuScale(StrayConfig.get().menuScale);
		if (Math.abs(scale - 1.00f) < 0.01f) {
			return 0;
		}
		if (Math.abs(scale - 0.90f) < 0.01f) {
			return 1;
		}
		if (Math.abs(scale - 0.75f) < 0.01f) {
			return 2;
		}
		return 3;
	}

	private float swatchRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float startX, float startY, float maxW, Theme.Swatch[] swatches, boolean accent) {
		float dx = startX;
		float dy = startY;
		float rowEnd = startX + Math.max(16, maxW);
		for (int i = 0; i < swatches.length; i++) {
			Theme.Swatch swatch = swatches[i];
			int current = accent ? StrayConfig.get().themeAccentRgb : StrayConfig.get().themePaneRgb;
			boolean active = (current & 0xFFFFFF) == swatch.rgb();
			boolean hover = GuiDraw.hovered(mouseX, mouseY, dx, dy, 14, 14);
			GuiDraw.rounded(graphics, dx - 1, dy - 1, 16, 16, 4, active || hover ? ink() : fade());
			GuiDraw.rounded(graphics, dx, dy, 14, 14, 3, 0xFF000000 | swatch.rgb());
			hits.add(new Hit(dx, dy, 14, 14, accent ? () -> Theme.applyPreset(swatch) : () -> Theme.applyPanePreset(swatch)));
			dx += 18;
			if (i + 1 < swatches.length && dx + 14 > rowEnd) {
				dx = startX;
				dy += 18;
			}
		}
		return dy + 18;
	}

	private static float swatchBlockH(int count, float maxW) {
		int per = Math.max(1, (int) (maxW / 18f));
		int rows = (count + per - 1) / per;
		return rows * 18f;
	}

	private void drawNotes(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		notesX = contentX() + contentW() - PANEL_W;
		notesY = windowY + toolbarH() + 2;
		notesH = Math.min(windowH - toolbarH() - 10, 210);
		if (controlCenter()) {
			ControlChrome.sheet(graphics, notesX, notesY, PANEL_W, notesH * Math.max(0.2f, notesT));
		} else {
			GuiDraw.sheet(graphics, notesX, notesY, PANEL_W, notesH * Math.max(0.2f, notesT), 8, Anim.fade(Theme.SHEET, notesT), Anim.fade(Theme.ACCENT, notesT));
		}
		if (notesT < 0.85f) {
			return;
		}
		GuiDraw.menu(graphics, font, "What's new", notesX + 8, notesY + 6, Theme.HEADER);
		float listX = notesX + 8;
		float listY = notesY + 20;
		float listW = PANEL_W - 16;
		float listH = notesH - 28;
		float row = 11f;
		float contentH = ReleaseNotes.contentHeight(font, listW, row);
		float maxScroll = Math.max(0f, contentH - listH);
		notesScroll = Mth.clamp(notesScroll, 0f, maxScroll);
		boolean clipped = GuiDraw.scissor(graphics, listX, listY, listW, listH);
		float y = listY - notesScroll;
		float bulletW = GuiDraw.menuWidth(font, "• ");
		float wrapW = Math.max(8f, listW - bulletW);
		for (ReleaseNotes.Entry entry : ReleaseNotes.visible()) {
			GuiDraw.small(graphics, font, entry.version(), listX, y, Theme.ACCENT);
			y += 12;
			for (String line : entry.lines()) {
				List<String> rows = ReleaseNotes.wrapLine(font, line, wrapW);
				for (int i = 0; i < rows.size(); i++) {
					if (i == 0) {
						GuiDraw.menu(graphics, font, "• " + rows.get(i), listX, y, Theme.TEXT);
					} else {
						GuiDraw.menu(graphics, font, rows.get(i), listX + bulletW, y, Theme.TEXT);
					}
					y += row;
				}
			}
			y += 6;
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
		hits.add(new Hit(notesX, notesY, PANEL_W, notesH, () -> {
		}));
	}

	private void drawColumns(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = contentX();
		float top = windowY + toolbarH() + 6;
		float col = colW();
		float right = left + col + COL_GAP;
		float ix = innerX(left);
		float rx = innerX(right);
		float iw = innerW(col);
		StrayConfig config = StrayConfig.get();
		if (!controlCenter() && tab == Tab.SETTINGS) {
			selectTab(Tab.WORLD);
		}
		if (controlCenter()) {
			drawControlColumns(graphics, font, mouseX, mouseY, left, right, top, col, ix, rx, iw, config);
			return;
		}

		switch (tab) {
			case WORLD -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(2), "World");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "World tint", config.worldTintEnabled, v -> config.worldTintEnabled = v, Feature.WORLD);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Skybox", config.skyTintEnabled, v -> config.skyTintEnabled = v, Feature.SKY);

				y = featureCard(graphics, font, right, top, col, cardHeight(3), "Camera");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Fog", config.fogEnabled, v -> config.fogEnabled = v, Feature.FOG);
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Aspect ratio", config.aspectEnabled, v -> config.aspectEnabled = v, Feature.VIEW);
				toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Motion blur", config.motionBlurEnabled, v -> config.motionBlurEnabled = v, Feature.MOTION);
			}
			case COMBAT -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(4), "Hitsound");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.hitsoundEnabled, v -> {
					config.hitsoundEnabled = v;
					if (v) {
						Hitsound.playPreview();
					}
				}, Feature.HITSOUND);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Melee", config.hitsoundMelee, v -> config.hitsoundMelee = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Arrows", config.hitsoundArrows, v -> config.hitsoundArrows = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Hitmarker", config.hitmarkerEnabled, v -> config.hitmarkerEnabled = v);

				y = featureCard(graphics, font, left, top + cardHeight(4) + 8, col, cardHeight(3), "Triggerbot");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.triggerbotEnabled, v -> config.triggerbotEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Players", config.triggerbotPlayers, v -> config.triggerbotPlayers = v);
				slider(graphics, font, ix, y, iw, "Humanize", Math.round(config.triggerbotHumanize * 100) + "%", config.triggerbotHumanize, v -> config.triggerbotHumanize = StrayConfig.clamp(v, 0f, 1f));

				float clickerH = fitH(top, cardHeight(1 + autoClickerFieldRows()));
				y = featureCard(graphics, font, right, top, col, clickerH, "Auto clicker");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Enable", config.autoClickerEnabled, v -> config.autoClickerEnabled = v);
				drawFeatureFields(graphics, font, mouseX, mouseY, rx, y, iw, Feature.AUTO_CLICKER);
			}
			case ESP -> drawMobsTab(graphics, font, mouseX, mouseY);
			case OVERLAY -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(5), "HUD");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Watermark", config.watermarkEnabled, v -> config.watermarkEnabled = v, Feature.WATERMARK);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Music", config.musicHudEnabled, v -> config.musicHudEnabled = v, Feature.MUSIC);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Raw mats", config.rawmatsHudEnabled, v -> config.rawmatsHudEnabled = v, Feature.RAWMATS);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Pickup log", config.pickupLogEnabled, v -> config.pickupLogEnabled = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Inventory HUD", config.inventoryHudEnabled, v -> config.inventoryHudEnabled = v, Feature.INVENTORY);
			}
			case BARS -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(4) + 28, "Info");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Scoreboard", config.hudScoreboard, v -> config.hudScoreboard = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Boss bar", config.hudBossBar, v -> config.hudBossBar = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Effects", config.hudEffects, v -> config.hudEffects = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Held item", config.hudHeldItem, v -> config.hudHeldItem = v);
				GuiDraw.menu(graphics, font, "Move and scale each piece", ix, y + 4, fade());
				GuiDraw.menu(graphics, font, "from the toolbar HUD editor.", ix, y + 16, fade());
			}
			case NODES -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(3), "Markers");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.markersEnabled, v -> config.markersEnabled = v, Feature.NODES);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Node HUD", config.hudEnabled, v -> config.hudEnabled = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Node ESP", config.boxFill, v -> config.boxFill = v, Feature.NODE_ESP);
			}
			case MENUS -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(5), "Menus");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Loadouts menu", config.loadoutsMenuEnabled, v -> config.loadoutsMenuEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Wardrobe menu", config.wardrobeMenuEnabled, v -> config.wardrobeMenuEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Profile viewer", config.profileViewerEnabled, v -> config.profileViewerEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Open animation", config.loadoutsOpenAnim, v -> config.loadoutsOpenAnim = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Disabled potions", config.disabledPotionsHighlight, v -> config.disabledPotionsHighlight = v);

				float experimentsH = cardHeight(1 + Feature.AUTO_EXPERIMENTS.rows);
				y = featureCard(graphics, font, left, top + cardHeight(5) + 8, col, experimentsH, "Auto experiments");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.autoExperimentsEnabled, v -> config.autoExperimentsEnabled = v);
				drawFeatureFields(graphics, font, mouseX, mouseY, ix, y, iw, Feature.AUTO_EXPERIMENTS);

				float bindsH = cardHeight(4);
				y = featureCard(graphics, font, right, top, col, bindsH, "Keybinds");
				y = drawMenuKeybinds(graphics, font, rx, y, iw, mouseX, mouseY);
				y = featureCard(graphics, font, right, top + bindsH + 8, col, cardHeight(5), "Commands");
				GuiDraw.menu(graphics, font, "/loadouts  /ld", rx, y + 2, ink());
				GuiDraw.menu(graphics, font, "/wardrobe  /wd", rx, y + 16, ink());
				GuiDraw.menu(graphics, font, "/pv  /profile", rx, y + 30, ink());
				GuiDraw.menu(graphics, font, "/autoclicker add left", rx, y + 44, ink());
				GuiDraw.menu(graphics, font, "1-9 equips and closes", rx, y + 58, fade());
			}
			case STATUS -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(4), "Location");
				y = readout(graphics, font, ix, y, iw, "Hypixel", SkyblockLocation.onHypixel);
				y = readout(graphics, font, ix, y, iw, "Skyblock", SkyblockLocation.inSkyblock);
				y = readout(graphics, font, ix, y, iw, "The End", SkyblockLocation.inTheEnd);
				String area = SkyblockLocation.area.isEmpty() ? "Unknown" : SkyblockLocation.area;
				GuiDraw.menu(graphics, font, clip(font, area, (int) iw - 4), ix, GuiDraw.middle(y, ROW), fade());

				y = featureCard(graphics, font, right, top, col, cardHeight(2), "Client");
				y = statRow(graphics, font, rx, y, iw, "FPS", HudStats.fps() + "");
				statRow(graphics, font, rx, y, iw, "Ping", HudStats.pingLabel());
			}
			case MINING -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(5), "Mining");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Mining HUD", config.miningHudEnabled, v -> config.miningHudEnabled = v, Feature.MINING);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Titanium ESP", config.titaniumEsp, v -> config.titaniumEsp = v, Feature.TITANIUM);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "CH waypoints", config.crystalHollowsWaypoints, v -> config.crystalHollowsWaypoints = v, Feature.CRYSTAL);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "CH map", config.crystalHollowsMap, v -> config.crystalHollowsMap = v, Feature.CH_MAP);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Metal detector", config.metalDetectorSolver, v -> config.metalDetectorSolver = v, Feature.METAL);

				y = featureCard(graphics, font, right, top, col, cardTop() + cardHead() + 54 + cardPad(), "Live");
				var snap = MiningTracker.snapshot();
				GuiDraw.menu(graphics, font, snap.ability(), rx, y + 2, ink());
				GuiDraw.menu(graphics, font, snap.abilityReady() ? "Ready" : snap.abilityLabel(), rx, y + 14, snap.abilityReady() ? Theme.ACCENT : fade());
				String jobs = snap.commissions().isEmpty() ? "No commissions" : snap.commissions().size() + " commission" + (snap.commissions().size() == 1 ? "" : "s");
				GuiDraw.menu(graphics, font, jobs, rx, y + 26, fade());
				String titanium;
				int titaniumColor = Theme.MUTED;
				if (!MiningTracker.hasTitaniumCommission()) {
					titanium = "No titanium job";
				} else {
					MiningAreas.TitaniumFilter filter = MiningTracker.titaniumFilter();
					int count = TitaniumTracker.get().count();
					titanium = filter.unrestricted()
						? count + " titanium"
						: count + " in " + filter.label();
					titaniumColor = Theme.ACCENT;
				}
				GuiDraw.menu(graphics, font, clip(font, titanium, (int) iw - 4), rx, y + 38, titaniumColor);
			}
			case FARMING -> {
				float farmingH = cardHeight(5);
				float dnaH = cardHeight(1 + Feature.AUTO_DNA.rows);
				float y = featureCard(graphics, font, left, top, col, farmingH, "Farming");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Yaw / Pitch", config.farmingYawPitch, v -> config.farmingYawPitch = v, Feature.FARMING);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Jacob contest HUD", config.jacobContestHudEnabled, v -> config.jacobContestHudEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Composter overlay", config.composterHudEnabled, v -> config.composterHudEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Garden plots", config.gardenPlotsWidget, v -> config.gardenPlotsWidget = v, Feature.PLOTS);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Pest ESP", config.pestEspEnabled, v -> config.pestEspEnabled = v, Feature.PEST);

				y = featureCard(graphics, font, left, top + farmingH + 8, col, dnaH, "Auto DNA");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.autoDnaEnabled, v -> config.autoDnaEnabled = v);
				drawFeatureFields(graphics, font, mouseX, mouseY, ix, y, iw, Feature.AUTO_DNA);

				y = featureCard(graphics, font, right, top, col, cardHeight(4), "Contest");
				var contest = JacobContestTracker.snapshot();
				if (contest.present()) {
					GuiDraw.menu(graphics, font, clip(font, contest.crop() + "  " + contest.remaining(), (int) iw - 4), rx, y + 2, ink());
					GuiDraw.menu(graphics, font, String.format(Locale.ROOT, "%,d collected", contest.score()), rx, y + 16, fade());
					String projected = contest.projectedRank().name() + "  " + String.format(Locale.ROOT, "%,d", contest.projectedScore());
					GuiDraw.menu(graphics, font, clip(font, projected, (int) iw - 4), rx, y + 30, Theme.ACCENT);
					String rate = String.format(Locale.ROOT, "%,.0f/s · %,.0f/update", contest.perSecond(), contest.perUpdate());
					GuiDraw.small(graphics, font, clip(font, rate, (int) iw - 4), rx, y + 44, fade());
				} else {
					GuiDraw.menu(graphics, font, "No active Jacob contest", rx, y + 2, fade());
					GuiDraw.small(graphics, font, "Reads the live player-list widget", rx, y + 16, fade());
					if (minecraft.player != null && FarmingHud.holdingTool(minecraft.player)) {
						GuiDraw.menu(graphics, font, "Yaw  " + FarmingHud.yawLabel(minecraft.player), rx, y + 32, ink());
						GuiDraw.menu(graphics, font, "Pitch  " + FarmingHud.pitchLabel(minecraft.player), rx, y + 46, ink());
					}
				}
			}
			case PLAYER -> drawPlayerTab(graphics, font, mouseX, mouseY);
			case SETTINGS -> drawControlSettings(graphics, font, mouseX, mouseY, left, right, top, col, ix, rx, iw);
			default -> drawControlColumns(graphics, font, mouseX, mouseY, left, right, top, col, ix, rx, iw, config);
		}
	}

	private void drawControlColumns(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float left,
		float right,
		float top,
		float col,
		float ix,
		float rx,
		float iw,
		StrayConfig config
	) {
		switch (tab) {
			case WORLD -> {
				float y = sectionLabel(graphics, font, left, top, "Tint");
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "World tint", config.worldTintEnabled, v -> config.worldTintEnabled = v, Feature.WORLD);
				controlCard(graphics, font, left, y, col, mouseX, mouseY, "Skybox", config.skyTintEnabled, v -> config.skyTintEnabled = v, Feature.SKY);
			}
			case CAMERA -> {
				float y = sectionLabel(graphics, font, left, top, "Lens");
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Fog", config.fogEnabled, v -> config.fogEnabled = v, Feature.FOG);
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Aspect ratio", config.aspectEnabled, v -> config.aspectEnabled = v, Feature.VIEW);
				y = sectionLabel(graphics, font, right, top, "Motion");
				controlCard(graphics, font, right, y, col, mouseX, mouseY, "Motion blur", config.motionBlurEnabled, v -> config.motionBlurEnabled = v, Feature.MOTION);
			}
			case COMBAT -> {
				float y = sectionLabel(graphics, font, left, top, "Feedback");
				y = featureCard(graphics, font, left, y, col, cardHeight(6), "Hitsound", config.hitsoundEnabled, v -> {
					config.hitsoundEnabled = v;
					if (v) {
						Hitsound.playPreview();
					}
				}, mouseX, mouseY);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Melee", config.hitsoundMelee, v -> config.hitsoundMelee = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Arrows", config.hitsoundArrows, v -> config.hitsoundArrows = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Hitmarker", config.hitmarkerEnabled, v -> config.hitmarkerEnabled = v);
				drawFeatureFields(graphics, font, mouseX, mouseY, ix, y, iw, Feature.HITSOUND);
			}
			case ASSIST -> {
				float y = sectionLabel(graphics, font, left, top, "Aim");
				y = featureCard(graphics, font, left, y, col, cardHeight(3), "Triggerbot");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.triggerbotEnabled, v -> config.triggerbotEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Players", config.triggerbotPlayers, v -> config.triggerbotPlayers = v);
				slider(graphics, font, ix, y, iw, "Humanize", Math.round(config.triggerbotHumanize * 100) + "%", config.triggerbotHumanize, v -> config.triggerbotHumanize = StrayConfig.clamp(v, 0f, 1f));

				y = sectionLabel(graphics, font, right, top, "Clicks");
				float clickerH = fitH(y, cardHeight(autoClickerFieldRows()));
				y = featureCard(graphics, font, right, y, col, clickerH, "Auto clicker", config.autoClickerEnabled, v -> config.autoClickerEnabled = v, mouseX, mouseY);
				drawFeatureFields(graphics, font, mouseX, mouseY, rx, y, iw, Feature.AUTO_CLICKER);
			}
			case ESP -> {
				float y = sectionLabel(graphics, font, left, top, "Glow");
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Mob glow", config.mobGlowEnabled, v -> config.mobGlowEnabled = v, Feature.MOB);
				float starH = cardHeight(Feature.STAR.rows);
				float starInner = featureCard(graphics, font, left, y, col, starH, "Star mobs");
				drawFeatureFields(graphics, font, mouseX, mouseY, ix, starInner, iw, Feature.STAR);
				y = y + starH + 8;
				y = sectionLabel(graphics, font, left, y, "World");
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Block outline", config.blockOutlineGlow, v -> config.blockOutlineGlow = v, Feature.BLOCK);
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Chest ESP", config.chestEspEnabled, v -> config.chestEspEnabled = v, Feature.CHEST);
				controlCard(graphics, font, left, y, col, mouseX, mouseY, "Fairy souls", config.fairySoulEsp, v -> config.fairySoulEsp = v, Feature.FAIRY);
				y = sectionLabel(graphics, font, right, top, "Held item");
				y = controlCard(graphics, font, right, y, col, mouseX, mouseY, "Held item", config.heldItemShaderEnabled, v -> config.heldItemShaderEnabled = v, Feature.HELD_ITEM);
				y = sectionLabel(graphics, font, right, y, "Health");
				controlCard(graphics, font, right, y, col, mouseX, mouseY, "Health bar", config.healthBarEnabled, v -> config.healthBarEnabled = v, Feature.HEALTH);
			}
			case PLAYERS -> {
				float y = sectionLabel(graphics, font, left, top, "Shader");
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Player shader", config.playerFillEsp, v -> config.playerFillEsp = v, Feature.FILL);
				y = sectionLabel(graphics, font, right, top, "Nametags");
				float tagH = cardHeight(Feature.NAMETAGS.rows + 1);
				float tagY = featureCard(graphics, font, right, y, col, tagH, "Nametags", config.nametagsEnabled, v -> config.nametagsEnabled = v, mouseX, mouseY);
				fieldScope = Feature.NAMETAGS.name();
				tagY = toggle(graphics, font, rx, tagY, iw, mouseX, mouseY, "Own nametag", config.nametagSelf, v -> config.nametagSelf = v);
				drawFeatureFields(graphics, font, mouseX, mouseY, rx, tagY, iw, Feature.NAMETAGS);
				fieldScope = "";
				float namesTop = y + tagH + 8;
				List<String> nametags = config.nametagEspLabels();
				float namesH = Math.max(cardHeight(4), contentBottom() - namesTop);
				String namesTitle = nametags.isEmpty() ? "Nametag ESP" : "Nametag ESP  " + nametags.size();
				float namesY = featureCard(graphics, font, right, namesTop, col, namesH, namesTitle);
				drawNametagEspList(graphics, font, rx, namesY, iw, namesH - cardTop() - cardHead() - 4, mouseX, mouseY, true);
			}
			case CATALOG -> drawCatalogTab(graphics, font, mouseX, mouseY);
			case OVERLAY -> {
				float y = sectionLabel(graphics, font, left, top, "Info");
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Watermark", config.watermarkEnabled, v -> config.watermarkEnabled = v, Feature.WATERMARK);
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Raw mats", config.rawmatsHudEnabled, v -> config.rawmatsHudEnabled = v, Feature.RAWMATS);
				toggleCard(graphics, font, left, y, col, mouseX, mouseY, "Pickup log", config.pickupLogEnabled, v -> config.pickupLogEnabled = v);
				y = sectionLabel(graphics, font, right, top, "Inventory");
				controlCard(graphics, font, right, y, col, mouseX, mouseY, "Inventory HUD", config.inventoryHudEnabled, v -> config.inventoryHudEnabled = v, Feature.INVENTORY);
			}
			case MEDIA -> {
				float y = sectionLabel(graphics, font, left, top, "Now playing");
				controlCard(graphics, font, left, y, col, mouseX, mouseY, "Music", config.musicHudEnabled, v -> config.musicHudEnabled = v, Feature.MUSIC);
			}
			case BARS -> {
				float y = sectionLabel(graphics, font, left, top, "Vanilla HUD");
				y = featureCard(graphics, font, left, y, col, cardHeight(4) + 28, "Show");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Scoreboard", config.hudScoreboard, v -> config.hudScoreboard = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Boss bar", config.hudBossBar, v -> config.hudBossBar = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Effects", config.hudEffects, v -> config.hudEffects = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Held item", config.hudHeldItem, v -> config.hudHeldItem = v);
				GuiDraw.menu(graphics, font, "Move and scale each piece", ix, y + 4, fade());
				GuiDraw.menu(graphics, font, "from the toolbar HUD editor.", ix, y + 16, fade());
			}
			case NODES -> {
				float y = sectionLabel(graphics, font, left, top, "Scan");
				y = featureCard(graphics, font, left, y, col, cardHeight(6), "Markers", config.markersEnabled, v -> config.markersEnabled = v, mouseX, mouseY);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Node HUD", config.hudEnabled, v -> config.hudEnabled = v);
				drawFeatureFields(graphics, font, mouseX, mouseY, ix, y, iw, Feature.NODES);
				y = sectionLabel(graphics, font, right, top, "ESP");
				controlCard(graphics, font, right, y, col, mouseX, mouseY, "Node ESP", config.boxFill, v -> config.boxFill = v, Feature.NODE_ESP);
			}
			case MENUS -> {
				float y = sectionLabel(graphics, font, left, top, "Skyblock");
				y = featureCard(graphics, font, left, y, col, cardHeight(5), "Menus");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Loadouts menu", config.loadoutsMenuEnabled, v -> config.loadoutsMenuEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Wardrobe menu", config.wardrobeMenuEnabled, v -> config.wardrobeMenuEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Profile viewer", config.profileViewerEnabled, v -> config.profileViewerEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Open animation", config.loadoutsOpenAnim, v -> config.loadoutsOpenAnim = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Disabled potions", config.disabledPotionsHighlight, v -> config.disabledPotionsHighlight = v);
				y = sectionLabel(graphics, font, right, top, "Experiments");
				y = featureCard(graphics, font, right, y, col, cardHeight(Feature.AUTO_EXPERIMENTS.rows), "Auto experiments", config.autoExperimentsEnabled, v -> config.autoExperimentsEnabled = v, mouseX, mouseY);
				drawFeatureFields(graphics, font, mouseX, mouseY, rx, y, iw, Feature.AUTO_EXPERIMENTS);
			}
			case KEYS -> {
				float y = sectionLabel(graphics, font, left, top, "Binds");
				y = featureCard(graphics, font, left, y, col, cardHeight(4), "Keybinds");
				drawMenuKeybinds(graphics, font, ix, y, iw, mouseX, mouseY);
				y = sectionLabel(graphics, font, right, top, "Chat");
				y = featureCard(graphics, font, right, y, col, cardHeight(5), "Commands");
				GuiDraw.menu(graphics, font, "/loadouts  /ld", rx, y + 2, ink());
				GuiDraw.menu(graphics, font, "/wardrobe  /wd", rx, y + 16, ink());
				GuiDraw.menu(graphics, font, "/pv  /profile", rx, y + 30, ink());
				GuiDraw.menu(graphics, font, "/autoclicker add left", rx, y + 44, ink());
				GuiDraw.menu(graphics, font, "1-9 equips and closes", rx, y + 58, fade());
			}
			case STATUS -> {
				float y = sectionLabel(graphics, font, left, top, "Server");
				y = featureCard(graphics, font, left, y, col, cardHeight(4), "Location");
				y = readout(graphics, font, ix, y, iw, "Hypixel", SkyblockLocation.onHypixel);
				y = readout(graphics, font, ix, y, iw, "Skyblock", SkyblockLocation.inSkyblock);
				y = readout(graphics, font, ix, y, iw, "The End", SkyblockLocation.inTheEnd);
				String area = SkyblockLocation.area.isEmpty() ? "Unknown" : SkyblockLocation.area;
				GuiDraw.menu(graphics, font, clip(font, area, (int) iw - 4), ix, GuiDraw.middle(y, ROW), fade());
				y = sectionLabel(graphics, font, right, top, "Client");
				y = featureCard(graphics, font, right, y, col, cardHeight(2), "Performance");
				y = statRow(graphics, font, rx, y, iw, "FPS", HudStats.fps() + "");
				statRow(graphics, font, rx, y, iw, "Ping", HudStats.pingLabel());
			}
			case MINING -> {
				float y = sectionLabel(graphics, font, left, top, "Dwarven mines");
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Mining HUD", config.miningHudEnabled, v -> config.miningHudEnabled = v, Feature.MINING);
				controlCard(graphics, font, left, y, col, mouseX, mouseY, "Titanium ESP", config.titaniumEsp, v -> config.titaniumEsp = v, Feature.TITANIUM);
				drawMiningLive(graphics, font, right, top, col, rx, iw);
			}
			case HOLLOWS -> {
				float y = sectionLabel(graphics, font, left, top, "Crystal Hollows");
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "CH waypoints", config.crystalHollowsWaypoints, v -> config.crystalHollowsWaypoints = v, Feature.CRYSTAL, "Dump", CrystalHollows::dumpChat);
				controlCard(graphics, font, left, y, col, mouseX, mouseY, "CH map", config.crystalHollowsMap, v -> config.crystalHollowsMap = v, Feature.CH_MAP);
				y = sectionLabel(graphics, font, right, top, "Mines of Divan");
				controlCard(graphics, font, right, y, col, mouseX, mouseY, "Metal detector", config.metalDetectorSolver, v -> config.metalDetectorSolver = v, Feature.METAL);
			}
			case FARMING -> {
				float y = sectionLabel(graphics, font, left, top, "Overlays");
				y = controlCard(graphics, font, left, y, col, mouseX, mouseY, "Yaw / Pitch", config.farmingYawPitch, v -> config.farmingYawPitch = v, Feature.FARMING);
				y = toggleCard(graphics, font, left, y, col, mouseX, mouseY, "Jacob contest HUD", config.jacobContestHudEnabled, v -> config.jacobContestHudEnabled = v);
				toggleCard(graphics, font, left, y, col, mouseX, mouseY, "Composter overlay", config.composterHudEnabled, v -> config.composterHudEnabled = v);
				drawFarmingContest(graphics, font, right, top, col, rx, iw);
			}
			case GARDEN -> {
				float y = sectionLabel(graphics, font, left, top, "Plots");
				controlCard(graphics, font, left, y, col, mouseX, mouseY, "Garden plots", config.gardenPlotsWidget, v -> config.gardenPlotsWidget = v, Feature.PLOTS);
				y = sectionLabel(graphics, font, right, top, "Pests");
				controlCard(graphics, font, right, y, col, mouseX, mouseY, "Pest ESP", config.pestEspEnabled, v -> config.pestEspEnabled = v, Feature.PEST);
			}
			case GREENHOUSE -> {
				float y = sectionLabel(graphics, font, left, top, "Analyzer");
				y = featureCard(graphics, font, left, y, col, cardHeight(Feature.AUTO_DNA.rows), "Auto DNA", config.autoDnaEnabled, v -> config.autoDnaEnabled = v, mouseX, mouseY);
				drawFeatureFields(graphics, font, mouseX, mouseY, ix, y, iw, Feature.AUTO_DNA);
			}
			case PLAYER -> drawPlayerTab(graphics, font, mouseX, mouseY);
			case SETTINGS -> drawControlSettings(graphics, font, mouseX, mouseY, left, right, top, col, ix, rx, iw);
			default -> {
			}
		}
	}

	private float controlCard(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		int mouseX,
		int mouseY,
		String title,
		boolean enabled,
		Consumer<Boolean> setter,
		Feature feature
	) {
		return controlCard(graphics, font, x, y, w, mouseX, mouseY, title, enabled, setter, feature, null, null);
	}

	private float controlCard(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		int mouseX,
		int mouseY,
		String title,
		boolean enabled,
		Consumer<Boolean> setter,
		Feature feature,
		String action,
		Runnable onAction
	) {
		float h = cardHeight(feature.rows);
		float iy = featureCard(graphics, font, x, y, w, h, title, enabled, setter, mouseX, mouseY, action, onAction);
		drawFeatureFields(graphics, font, mouseX, mouseY, innerX(x), iy, innerW(w), feature);
		return y + h + 8;
	}

	private float toggleCard(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		int mouseX,
		int mouseY,
		String title,
		boolean enabled,
		Consumer<Boolean> setter
	) {
		float h = cardHeight(0);
		featureCard(graphics, font, x, y, w, h, title, enabled, setter, mouseX, mouseY);
		return y + h + 8;
	}

	private float sectionLabel(GuiGraphicsExtractor graphics, Font font, float x, float y, String title) {
		GuiDraw.small(graphics, font, title.toUpperCase(Locale.ROOT), x + 2, y + 1, ControlChrome.muted());
		GuiDraw.rounded(graphics, x + 2, y + 12, 14, 1.2f, 0.6f, Theme.withAlpha(Theme.ACCENT, 90));
		pageExtent = Math.max(pageExtent, y + 18);
		return y + 18;
	}

	private void drawControlSettings(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float left,
		float right,
		float top,
		float col,
		float ix,
		float rx,
		float iw
	) {
		StrayConfig config = StrayConfig.get();
		float y = sectionLabel(graphics, font, left, top, "Window");
		y = featureCard(graphics, font, left, y, col, cardHeight(7), "Control");
		y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Glass", config.controlPaneRgb, PickerTarget.CONTROL);
		y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Pills", config.controlPillRgb, PickerTarget.PILL);
		y = slider(graphics, font, ix, y, iw, "Frost", Math.round(config.controlFrost * 100) + "%", config.controlFrost, v -> config.controlFrost = StrayConfig.clamp(v, 0f, 1f));
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Menu stars", config.menuStarfield, v -> config.menuStarfield = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Animations", config.uiAnimations, v -> config.uiAnimations = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Auto update", config.autoUpdate, v -> config.autoUpdate = v);
		toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Update notify", config.updateNotify, v -> config.updateNotify = v);

		float accentH = cardTop() + cardHead() + 14 + swatchBlockH(Theme.PRESETS.length, iw) + rowH() * 3 + cardPad();
		float lookY = sectionLabel(graphics, font, right, top, "Look");
		y = featureCard(graphics, font, right, lookY, col, accentH, "Accent");
		GuiDraw.small(graphics, font, "Preset", rx, y + 1, ControlChrome.muted());
		y = swatchRow(graphics, mouseX, mouseY, rx + 2, y + 12, iw - 2, Theme.PRESETS, true);
		y = colorRow(graphics, font, rx, y, iw, mouseX, mouseY, "Custom", config.themeAccentRgb, PickerTarget.THEME);
		y = slider(graphics, font, rx, y, iw, "HUD", Math.round(config.hudOpacity * 100) + "%", (config.hudOpacity - 0.20f) / 0.80f, v -> {
			config.hudOpacity = StrayConfig.clamp(0.20f + v * 0.80f, 0.20f, 1f);
			Theme.refresh();
		});
		toggle(graphics, font, rx, y, iw, mouseX, mouseY, "HUD stars", config.hudStarfield, v -> config.hudStarfield = v);

		float scaleTop = lookY + accentH + 10;
		y = featureCard(graphics, font, right, scaleTop, col, cardHeight(1) + 18, "Scale");
		GuiDraw.small(graphics, font, "Menu", rx, y + 1, ControlChrome.muted());
		y += 12;
		y = chipRow(graphics, font, rx, y, iw, mouseX, mouseY, new String[]{"100%", "90%", "75%", "50%"}, menuScaleChip(), index -> {
			float[] values = {1.00f, 0.90f, 0.75f, 0.50f};
			config.menuScale = values[index];
		});
	}

	private void drawControlFeaturePage(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float left,
		float right,
		float top,
		float col,
		float ix,
		float rx,
		float iw
	) {
		Feature feature = featureId;
		if (feature == null) {
			return;
		}
		float y = featureCard(graphics, font, left, top, col, cardHeight(Math.max(3, feature.rows)), feature.title);
		drawFeatureFields(graphics, font, mouseX, mouseY, ix, y, iw, feature);
		y = featureCard(graphics, font, right, top, col, cardHeight(1), "Done");
		cycle(graphics, font, rx, y, iw, mouseX, mouseY, "Back", tab.label, () -> featureOpen = false);
	}

	private void drawPageScrollbar(GuiGraphicsExtractor graphics) {
		if (tabScrollMax <= 1f || pageClipH < 24f) {
			return;
		}
		float radius = controlCenter() ? ControlChrome.WINDOW_R : Theme.WINDOW_RADIUS;
		float trackW = 3.2f;
		float rightInset = Math.max(8f, radius * 0.36f);
		float trackX = pageClipX + pageClipW - trackW - rightInset;
		float edgeInset = (pageClipX + pageClipW) - (trackX + trackW);
		float corner = cornerClearance(radius, edgeInset);
		float topPad = 8f;
		float botPad = Math.max(8f, corner + 3f);
		float trackY = pageClipY + topPad;
		float trackH = pageClipH - topPad - botPad;
		if (trackH < 16f) {
			return;
		}
		int track = controlCenter() ? ControlChrome.TRACK : Theme.TRACK;
		GuiDraw.rounded(graphics, trackX, trackY, trackW, trackH, 1.6f, track);
		float thumbH = Math.max(16f, trackH * pageClipH / (pageClipH + tabScrollMax));
		float thumbY = trackY + (tabScrollMax <= 0f ? 0f : tabScroll / tabScrollMax) * (trackH - thumbH);
		GuiDraw.rounded(graphics, trackX, thumbY, trackW, thumbH, 1.6f, Theme.ACCENT);
	}

	private static float cornerClearance(float radius, float edgeInset) {
		float r = Math.max(1f, radius);
		float inset = Mth.clamp(edgeInset, 0.5f, r);
		float inner = r - inset;
		return r - (float) Math.sqrt(Math.max(0f, r * r - inner * inner));
	}

	private boolean pageHover(double mx, double my, float x, float y, float w, float h) {
		if (!GuiDraw.hovered(mx, my, pageClipX, pageClipY, pageClipW, pageClipH)) {
			return false;
		}
		return GuiDraw.hovered(mx, my + tabScroll, x, y, w, h);
	}

	private boolean mobListLive() {
		return mobListH >= 2f && mobListW >= 2f && (tab == Tab.CATALOG || !controlCenter() && tab == Tab.ESP);
	}

	private boolean nametagListLive() {
		return nametagEspListH >= 2f && nametagEspListW >= 2f && (tab == Tab.PLAYERS || !controlCenter() && tab == Tab.ESP);
	}

	private float innerX(float cardX) {
		return cardX + cardPad();
	}

	private float innerW(float cardW) {
		return cardW - cardPad() * 2;
	}

	private float cardHeight(int rows) {
		return cardTop() + cardHead() + rows * rowH() + cardPad();
	}

	private float contentBottom() {
		return windowY + windowH - pad();
	}

	private float fitH(float y, float h) {
		return Math.min(h, Math.max(cardHeight(0), contentBottom() - y));
	}

	private int autoClickerFieldRows() {
		return StrayConfig.get().autoClickerTerminatorOnly ? 6 : 8;
	}

	private void drawNametagEspList(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		float h,
		int mouseX,
		int mouseY,
		boolean scrollable
	) {
		List<String> labels = StrayConfig.get().nametagEspLabels();
		if (labels.isEmpty()) {
			GuiDraw.menu(graphics, font, "No nametag filters", x + 1, GuiDraw.middle(y, ROW), fade());
			GuiDraw.small(graphics, font, "/st esp <text>", x + 1, y + ROW + 1, fade());
			return;
		}
		float scroll = 0f;
		float maxScroll = Math.max(0f, labels.size() * ROW - h);
		if (scrollable) {
			nametagEspListX = x;
			nametagEspListY = y;
			nametagEspListW = w;
			nametagEspListH = h;
			nametagEspScroll = Mth.clamp(nametagEspScroll, 0f, maxScroll);
			scroll = nametagEspScroll;
		}
		boolean clipped = h < labels.size() * ROW && GuiDraw.scissor(graphics, x, y, w, h);
		int first = (int) (scroll / ROW);
		int last = Math.min(labels.size() - 1, first + (int) (h / ROW) + 1);
		for (int i = first; i <= last; i++) {
			String label = labels.get(i);
			float iy = y + i * ROW - scroll;
			boolean rowHover = GuiDraw.hovered(mouseX, mouseY, x, iy, w, ROW)
				&& GuiDraw.hovered(mouseX, mouseY, x, y, w, h);
			float xW = 12f;
			float xX = x + w - xW;
			boolean xHover = GuiDraw.hovered(mouseX, mouseY, xX, iy, xW, ROW)
				&& GuiDraw.hovered(mouseX, mouseY, x, y, w, h);
			if (rowHover && !xHover) {
				GuiDraw.rounded(graphics, x - 2, iy, w + 4, ROW, 5, 0x10FFFFFF);
			}
			if (xHover) {
				GuiDraw.rounded(graphics, xX - 1, iy + 1, xW + 2, ROW - 2, 4, Theme.withAlpha(Theme.WARN, 40));
			}
			GuiDraw.menu(graphics, font, clip(font, label, (int) (w - xW - 8)), x + 1, GuiDraw.middle(iy, ROW), ink());
			GuiDraw.menu(graphics, font, "x", xX + (xW - GuiDraw.menuWidth(font, "x")) * 0.5f, GuiDraw.middle(iy, ROW), xHover ? Theme.WARN : Theme.MUTED);
			float hitY = Math.max(iy, y);
			float hitB = Math.min(iy + ROW, y + h);
			if (hitB - hitY >= 3f) {
				hits.add(new Hit(xX, hitY, xW, hitB - hitY, () -> removeNametagEsp(label)));
			}
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
		if (scrollable && maxScroll > 1f) {
			float trackX = x + w + 3;
			float trackH = h;
			GuiDraw.rounded(graphics, trackX, y, 2.4f, trackH, 1.2f, Theme.TRACK);
			float thumbH = Math.max(14f, trackH * trackH / (trackH + maxScroll));
			float thumbY = y + (nametagEspScroll / maxScroll) * (trackH - thumbH);
			GuiDraw.rounded(graphics, trackX - 0.4f, thumbY, 3.2f, thumbH, 1.6f, Theme.ACCENT);
		}
	}

	private void removeNametagEsp(String label) {
		StrayConfig config = StrayConfig.get();
		if (!config.removeNametagEsp(label)) {
			return;
		}
		EspMobPrint.drop(label.toLowerCase(Locale.ROOT));
		config.save();
		UnloadState.markDirty();
	}

	private float featureCard(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, String title) {
		return featureCard(graphics, font, x, y, w, h, title, null, null, 0, 0, null, null);
	}

	private float featureCard(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		float h,
		String title,
		Boolean value,
		Consumer<Boolean> setter,
		int mouseX,
		int mouseY
	) {
		return featureCard(graphics, font, x, y, w, h, title, value, setter, mouseX, mouseY, null, null);
	}

	private float featureCard(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		float h,
		String title,
		Boolean value,
		Consumer<Boolean> setter,
		int mouseX,
		int mouseY,
		String action,
		Runnable onAction
	) {
		if (controlCenter()) {
			ControlChrome.card(graphics, x, y, w, h);
			pageExtent = Math.max(pageExtent, y + h);
			float headY = y + cardTop();
			GuiDraw.menu(graphics, font, title, x + cardPad(), GuiDraw.middle(headY, cardHead()), ControlChrome.cardText());
			float right = x + w - cardPad();
			if (setter != null && value != null) {
				float trackW = 28;
				float trackH = 16;
				float tx = right - trackW;
				float ty = headY + (cardHead() - trackH) * 0.5f;
				float t = anim("tog-card-" + title + "@" + Math.round(x) + ":" + Math.round(y), value ? 1f : 0f);
				ControlChrome.toggle(graphics, tx, ty, trackW, trackH, t);
				hits.add(new Hit(tx - 2, headY, trackW + 4, cardHead(), () -> {
					setter.accept(!value);
					UnloadState.markDirty();
				}));
				right = tx - 6;
			}
			if (action != null && onAction != null) {
				float aw = GuiDraw.menuWidth(font, action) + 10;
				float ah = 16;
				float ax = right - aw;
				float ay = headY + (cardHead() - ah) * 0.5f;
				boolean hover = GuiDraw.hovered(mouseX, mouseY, ax, ay, aw, ah);
				ControlChrome.search(graphics, ax, ay, aw, ah);
				GuiDraw.menu(graphics, font, action, ax + 5, GuiDraw.middle(ay, ah), hover ? Theme.ACCENT : ControlChrome.cardText());
				hits.add(new Hit(ax, headY, aw, cardHead(), onAction));
			}
			return headY + cardHead();
		}
		GuiDraw.panel(graphics, x, y, w, h, Math.min(14f, h / 2f), Theme.CARD, Theme.LINE);
		pageExtent = Math.max(pageExtent, y + h);
		GuiDraw.small(graphics, font, title, x + CARD_PAD, y + 5, Theme.HEADER);
		GuiDraw.hline(graphics, x + CARD_PAD, y + 16, w - CARD_PAD * 2, Theme.LINE);
		return y + CARD_HEAD;
	}

	private float clickRow(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		int mouseX,
		int mouseY,
		String label,
		Runnable click
	) {
		float row = rowH();
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, row);
		if (hovered) {
			GuiDraw.rounded(graphics, x - 3, y, w + 6, row, 6, Anim.fade(0x08FFFFFF, 1f));
		}
		GuiDraw.menu(graphics, font, label, x + 1, GuiDraw.middle(y, row), hovered ? Theme.ACCENT : ink());
		hits.add(new Hit(x, y, w, row, click));
		return y + row;
	}

	private float toggle(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, boolean value, Consumer<Boolean> setter) {
		return toggle(graphics, font, x, y, w, mouseX, mouseY, label, value, setter, null);
	}

	private float toggle(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, boolean value, Consumer<Boolean> setter, Feature feature) {
		float row = rowH();
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, row);
		String animKey = rowAnimKey(label, x, y);
		float hover = anim("hov-" + animKey, hovered ? 1f : 0f);
		if (hover > 0.02f) {
			GuiDraw.rounded(graphics, x - 3, y, w + 6, row, 6, Anim.fade(0x08FFFFFF, hover));
		}
		float labelY = GuiDraw.middle(y, row);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, ink());

		float t = anim("tog-" + animKey, value ? 1f : 0f);
		float trackW = controlCenter() ? 28 : 22;
		float trackH = controlCenter() ? 16 : 11;
		float tx = x + w - trackW;
		float ty = y + (row - trackH) / 2f;
		boolean showCog = feature != null && !controlCenter();
		if (showCog) {
			float cogX = tx - COG_W - 2;
			boolean cogOn = featureOpen && featureId == feature;
			boolean cogHover = GuiDraw.hovered(mouseX, mouseY, cogX, y, COG_W, row);
			GuiDraw.icon(graphics, font, MenuFont.SETTINGS, cogX + 1, labelY, cogOn || cogHover ? Theme.ACCENT : fade());
			hits.add(new Hit(cogX, y, COG_W, ROW, () -> openFeature(feature)));
		}
		if (controlCenter()) {
			ControlChrome.toggle(graphics, tx, ty, trackW, trackH, t);
		} else {
			int fill = t > 0.5f ? Theme.ACCENT : Theme.TRACK;
			GuiDraw.pill(graphics, tx, ty, trackW, trackH, fill);
			float knob = tx + 6 + t * (trackW - 12);
			GuiDraw.circle(graphics, knob, ty + trackH / 2f, 4.6f, t > 0.5f ? Theme.TEXT : Theme.OFF);
		}
		if (showCog) {
			hits.add(new Hit(x, y, tx - COG_W - 4 - x, row, () -> {
				setter.accept(!value);
				UnloadState.markDirty();
			}));
			hits.add(new Hit(tx, y, trackW, row, () -> {
				setter.accept(!value);
				UnloadState.markDirty();
			}));
		} else {
			hits.add(new Hit(x, y, w, row, () -> {
				setter.accept(!value);
				UnloadState.markDirty();
			}));
		}
		return y + row;
	}

	private void openFeature(Feature feature) {
		if (featureOpen && featureId == feature) {
			featureOpen = false;
			return;
		}
		featureId = feature;
		featureOpen = true;
		bindListen = 0;
		settingsOpen = false;
		notesOpen = false;
		searchOpen = false;
		pickerTarget = null;
	}

	private void drawFeaturePanel(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Feature feature = featureId;
		if (feature == null) {
			return;
		}
		float h = feature.height();
		featureX = contentX() + contentW() - FEATURE_W;
		featureY = windowY + toolbarH() + 2;
		hits.add(new Hit(featureX, featureY, FEATURE_W, h, () -> {
		}));
		if (controlCenter()) {
			ControlChrome.sheet(graphics, featureX, featureY, FEATURE_W, h * Math.max(0.2f, featureT));
		} else {
			GuiDraw.sheet(graphics, featureX, featureY, FEATURE_W, h * Math.max(0.2f, featureT), 8, Anim.fade(Theme.SHEET, featureT), Anim.fade(Theme.ACCENT, featureT));
		}
		if (featureT < 0.85f) {
			return;
		}
		GuiDraw.menu(graphics, font, feature.title, featureX + 8, featureY + 6, Theme.HEADER);
		drawFeatureFields(graphics, font, mouseX, mouseY, featureX + 8, featureY + 20, FEATURE_W - 16, feature);
	}

	private void drawFeatureFields(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float ix,
		float y,
		float iw,
		Feature feature
	) {
		StrayConfig config = StrayConfig.get();
		String previousScope = fieldScope;
		if (fieldScope.isEmpty()) {
			fieldScope = feature.name();
		}
		switch (feature) {
			case WORLD -> {
				y = cycle(graphics, font, ix, y, iw, mouseX, mouseY, "Mode", config.worldTintModeLabel(), config::cycleWorldTintMode);
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.worldTintRgb, PickerTarget.WORLD);
				y = slider(graphics, font, ix, y, iw, "Strength", String.format(Locale.ROOT, "%.0f", config.worldTintStrength * 100), config.worldTintStrength, v -> config.worldTintStrength = v);
				if (config.worldTintUsesLightmap()) {
					hint(graphics, font, ix, y, iw, "Turn fullbright off");
				}
			}
			case SKY -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "End sky", config.endSkybox, v -> config.endSkybox = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Match world", config.matchSkyToWorld, v -> config.matchSkyToWorld = v);
				int skyPreview = config.matchSkyToWorld ? config.worldTintRgb : config.skyTintRgb;
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", skyPreview, PickerTarget.SKY);
				slider(graphics, font, ix, y, iw, "Strength", String.format(Locale.ROOT, "%.0f", config.skyTintStrength * 100), config.skyTintStrength, v -> config.skyTintStrength = v);
			}
			case FOG -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Match world", config.matchFogToWorld, v -> config.matchFogToWorld = v);
				int fogPreview = config.matchFogToWorld ? config.worldTintRgb : config.fogRgb;
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", fogPreview, PickerTarget.FOG);
				y = slider(graphics, font, ix, y, iw, "Start", String.format(Locale.ROOT, "%.0f%%", config.fogStart * 100), config.fogStart / 0.95f, v -> config.fogStart = StrayConfig.clamp(v * 0.95f, 0f, 0.95f));
				y = slider(graphics, font, ix, y, iw, "End", String.format(Locale.ROOT, "%.0f%%", config.fogEnd * 100), (config.fogEnd - 0.05f) / 0.95f, v -> config.fogEnd = StrayConfig.clamp(0.05f + v * 0.95f, 0.05f, 1f));
				slider(graphics, font, ix, y, iw, "Density", String.format(Locale.ROOT, "%.0f", config.fogDensity * 100), config.fogDensity, v -> config.fogDensity = v);
			}
			case VIEW -> {
				y = slider(graphics, font, ix, y, iw, "Aspect", aspectLabel(config.aspectRatio), (config.aspectRatio - 0.50f) / 0.70f, v -> config.aspectRatio = StrayConfig.clamp(0.50f + v * 0.70f, 0.50f, 1.20f));
				chipRow(graphics, font, ix, y, iw, mouseX, mouseY, new String[]{"Native", "16:10", "4:3", "5:4"}, aspectChipIndex(config.aspectRatio), index -> {
					float[] values = {1.00f, 0.90f, 0.75f, 0.70f};
					config.aspectEnabled = true;
					config.aspectRatio = values[index];
				});
			}
			case MOTION -> {
				y = cycle(graphics, font, ix, y, iw, mouseX, mouseY, "Algorithm", config.motionBlurAlgorithmLabel(), config::cycleMotionBlurAlgorithm);
				y = slider(graphics, font, ix, y, iw, "Strength", String.format(Locale.ROOT, "%.1f", config.motionBlurStrength), config.motionBlurStrength / 2.0f, v -> config.motionBlurStrength = StrayConfig.clamp(v * 2.0f, 0f, 2f));
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Refresh scale", config.motionBlurRefreshScale, v -> config.motionBlurRefreshScale = v);
			}
			case HITSOUND -> {
				y = slider(graphics, font, ix, y, iw, "Volume", Math.round(config.hitsoundVolume * 100) + "%", config.hitsoundVolume, v -> config.hitsoundVolume = StrayConfig.clamp(v, 0f, 1f));
				y = slider(graphics, font, ix, y, iw, "Pitch", String.format(Locale.ROOT, "%.2f", config.hitsoundPitch), (config.hitsoundPitch - 0.50f) / 1.00f, v -> config.hitsoundPitch = StrayConfig.clamp(0.50f + v, 0.50f, 1.50f));
				slider(graphics, font, ix, y, iw, "Marker", Math.round(config.hitmarkerScale * 100) + "%", (config.hitmarkerScale - 0.50f) / 1.50f, v -> config.hitmarkerScale = StrayConfig.clampHudScale(0.50f + v * 1.50f));
			}
			case HELD_ITEM -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Silhouette", config.heldItemShaderSilhouette, v -> config.heldItemShaderSilhouette = v);
				y = cycle(graphics, font, ix, y, iw, mouseX, mouseY, "Style", config.heldItemShaderStyleLabel(), config::cycleHeldItemShaderStyle);
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Tint", config.heldItemShaderRgb, PickerTarget.HELD_ITEM);
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Outline", config.heldItemShaderOutlineRgb, PickerTarget.HELD_ITEM_OUTLINE);
				y = slider(graphics, font, ix, y, iw, "Tint", Math.round(config.heldItemShaderFill * 100) + "%", (config.heldItemShaderFill - 0.08f) / 0.77f, v -> config.heldItemShaderFill = StrayConfig.clamp(0.08f + v * 0.77f, 0.08f, 0.85f));
				y = slider(graphics, font, ix, y, iw, "Thickness", Math.round(config.heldItemShaderOutline * 100) + "%", (config.heldItemShaderOutline - 0.15f) / 1.35f, v -> config.heldItemShaderOutline = StrayConfig.clamp(0.15f + v * 1.35f, 0.15f, 1.50f));
				slider(graphics, font, ix, y, iw, config.heldItemShaderStyleLabel(), Math.round(config.heldItemShaderSmoke * 100) + "%", (config.heldItemShaderSmoke - 0.10f) / 1.40f, v -> config.heldItemShaderSmoke = StrayConfig.clamp(0.10f + v * 1.40f, 0.10f, 1.50f));
			}
			case FILL -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Silhouette", config.playerFillSilhouette, v -> config.playerFillSilhouette = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.playerFillThroughWalls, v -> config.playerFillThroughWalls = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Shader mobs", config.playerFillMobs, v -> config.playerFillMobs = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Shader star mobs", config.playerFillStarMobs, v -> config.playerFillStarMobs = v);
				y = cycle(graphics, font, ix, y, iw, mouseX, mouseY, "Style", config.playerFillStyleLabel(), config::cyclePlayerFillStyle);
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Tint", config.playerFillRgb, PickerTarget.FILL);
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Outline", config.playerFillOutlineRgb, PickerTarget.FILL_OUTLINE);
				y = slider(graphics, font, ix, y, iw, "Tint", Math.round(config.playerFillFill * 100) + "%", (config.playerFillFill - 0.08f) / 0.77f, v -> config.playerFillFill = StrayConfig.clamp(0.08f + v * 0.77f, 0.08f, 0.85f));
				y = slider(graphics, font, ix, y, iw, "Thickness", Math.round(config.playerFillOutline * 100) + "%", (config.playerFillOutline - 0.15f) / 1.35f, v -> config.playerFillOutline = StrayConfig.clamp(0.15f + v * 1.35f, 0.15f, 1.50f));
				slider(graphics, font, ix, y, iw, config.playerFillStyleLabel(), Math.round(config.playerFillSmoke * 100) + "%", (config.playerFillSmoke - 0.10f) / 1.40f, v -> config.playerFillSmoke = StrayConfig.clamp(0.10f + v * 1.40f, 0.10f, 1.50f));
			}
			case AUTO_CLICKER -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Whitelist only", config.autoClickerWhiteListOnly, v -> config.autoClickerWhiteListOnly = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Allow breaking", config.autoClickerAllowBreaking, v -> config.autoClickerAllowBreaking = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Block dungeon breaker", config.autoClickerBlockBreaker, v -> config.autoClickerBlockBreaker = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Terminator only", config.autoClickerTerminatorOnly, v -> config.autoClickerTerminatorOnly = v);
				if (config.autoClickerTerminatorOnly) {
					y = slider(graphics, font, ix, y, iw, "CPS", cpsLabel(config.autoClickerCps), (config.autoClickerCps - 3.0f) / 12.0f, v -> config.autoClickerCps = snapCps(3.0f + v * 12.0f));
					hint(graphics, font, ix, y, iw, "/autoclicker add left|right");
				} else {
					y = clickerHand(graphics, font, ix, y, iw, mouseX, mouseY, "Left", config.autoClickerEnableLeftClick, v -> config.autoClickerEnableLeftClick = v, config.autoClickerLeftCps, v -> config.autoClickerLeftCps = snapCps(3.0f + v * 12.0f), 1, AutoClicker.leftKey());
					clickerHand(graphics, font, ix, y, iw, mouseX, mouseY, "Right", config.autoClickerEnableRightClick, v -> config.autoClickerEnableRightClick = v, config.autoClickerRightCps, v -> config.autoClickerRightCps = snapCps(3.0f + v * 12.0f), 2, AutoClicker.rightKey());
				}
			}
			case AUTO_EXPERIMENTS -> {
				y = slider(graphics, font, ix, y, iw, "Click delay", config.autoExperimentsClickDelay + "ms", (config.autoExperimentsClickDelay - 100) / 900f, v -> config.autoExperimentsClickDelay = snapInt(100 + v * 900f, 100, 1000, 10));
				y = slider(graphics, font, ix, y, iw, "Delay variety", config.autoExperimentsDelayVariety + "ms", config.autoExperimentsDelayVariety / 1000f, v -> config.autoExperimentsDelayVariety = snapInt(v * 1000f, 0, 1000, 10));
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Auto close", config.autoExperimentsAutoClose, v -> config.autoExperimentsAutoClose = v);
				y = slider(graphics, font, ix, y, iw, "Serum count", String.valueOf(config.autoExperimentsSerumCount), config.autoExperimentsSerumCount / 3f, v -> config.autoExperimentsSerumCount = snapInt(v * 3f, 0, 3, 1));
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Get max XP", config.autoExperimentsGetMaxXp, v -> config.autoExperimentsGetMaxXp = v);
			}
			case MOB -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.mobGlowThroughWalls, v -> config.mobGlowThroughWalls = v);
				y = slider(graphics, font, ix, y, iw, "Radius", String.format(Locale.ROOT, "%.0f", config.mobGlowRadius), (config.mobGlowRadius - GlowBlurRadius.MIN) / (GlowBlurRadius.MAX - GlowBlurRadius.MIN), v -> config.mobGlowRadius = StrayConfig.clamp(GlowBlurRadius.MIN + v * (GlowBlurRadius.MAX - GlowBlurRadius.MIN), GlowBlurRadius.MIN, GlowBlurRadius.MAX));
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.mobGlowRgb, PickerTarget.MOB);
			}
			case STAR -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Glow ESP", config.starMobEsp, v -> config.starMobEsp = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.starMobThroughWalls, v -> config.starMobThroughWalls = v);
				y = slider(graphics, font, ix, y, iw, "Radius", String.format(Locale.ROOT, "%.0f", config.starMobRadius), (config.starMobRadius - GlowBlurRadius.MIN) / (GlowBlurRadius.MAX - GlowBlurRadius.MIN), v -> config.starMobRadius = StrayConfig.clamp(GlowBlurRadius.MIN + v * (GlowBlurRadius.MAX - GlowBlurRadius.MIN), GlowBlurRadius.MIN, GlowBlurRadius.MAX));
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.starMobRgb, PickerTarget.STAR);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Highlight bats", config.starMobBats, v -> config.starMobBats = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Highlight fels", config.starMobFels, v -> config.starMobFels = v);
			}
			case BLOCK -> {
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.blockOutlineRgb, PickerTarget.BLOCK);
			}
			case CHEST -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Tracers", config.chestEspTracers, v -> config.chestEspTracers = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.chestEspThroughWalls, v -> config.chestEspThroughWalls = v);
				y = slider(graphics, font, ix, y, iw, "Speed", Math.round(config.chestAimSpeed * 100) + "%", (config.chestAimSpeed - 0.25f) / 1.75f, v -> config.chestAimSpeed = StrayConfig.clamp(0.25f + v * 1.75f, 0.25f, 2.00f));
				y = bindRow(graphics, font, ix, y, iw, mouseX, mouseY, "Chest Aim", 6, OdinClicks.parseKey(config.chestAimKey));
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.chestEspRgb, PickerTarget.CHEST);
			}
			case FAIRY -> toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.fairySoulThroughWalls, v -> config.fairySoulThroughWalls = v);
			case NODE_ESP -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Outline", config.boxOutline, v -> config.boxOutline = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Tracer", config.tracersEnabled, v -> config.tracersEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.throughWalls, v -> config.throughWalls = v);
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.colorRgb, PickerTarget.NODE);
			}
			case WATERMARK -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "FPS", config.watermarkFps, v -> config.watermarkFps = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Ping", config.watermarkPing, v -> config.watermarkPing = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Clock", config.watermarkTime, v -> config.watermarkTime = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Name", config.watermarkName, v -> config.watermarkName = v);
			}
			case MUSIC -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Hide when idle", config.musicHideIdle, v -> config.musicHideIdle = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Song Notification", config.musicChatAnnounce, v -> config.musicChatAnnounce = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Spotify", config.spotifyEnabled, v -> config.spotifyEnabled = v);
			}
			case RAWMATS -> cycle(graphics, font, ix, y, iw, mouseX, mouseY, "Materials", config.rawmatsModeLabel(), config::cycleRawmatsMode);
			case MINING -> toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Ability alert", config.miningAbilityAlert, v -> config.miningAbilityAlert = v);
			case FARMING -> slider(graphics, font, ix, y, iw, "Scale", Math.round(config.farmingYawPitchScale * 100) + "%", (config.farmingYawPitchScale - 0.50f) / 1.50f, v -> config.farmingYawPitchScale = StrayConfig.clampHudScale(0.50f + v * 1.50f));
			case PLOTS -> toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Close on click", config.gardenPlotsCloseOnClick, v -> config.gardenPlotsCloseOnClick = v);
			case PEST -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.pestEspThroughWalls, v -> config.pestEspThroughWalls = v);
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.pestEspRgb, PickerTarget.PEST);
			}
			case AUTO_DNA -> {
				y = slider(graphics, font, ix, y, iw, "Click delay", config.autoDnaClickDelay + "ms", (config.autoDnaClickDelay - 100) / 900f, v -> config.autoDnaClickDelay = snapInt(100 + v * 900f, 100, 1000, 10));
				y = slider(graphics, font, ix, y, iw, "Delay variety", config.autoDnaDelayVariety + "ms", config.autoDnaDelayVariety / 1000f, v -> config.autoDnaDelayVariety = snapInt(v * 1000f, 0, 1000, 10));
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Allow end columns", config.autoDnaAllowEnds, v -> config.autoDnaAllowEnds = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Block close", config.autoDnaBlockClose, v -> config.autoDnaBlockClose = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Middle click", config.autoDnaMiddleClick, v -> config.autoDnaMiddleClick = v);
			}
			case TITANIUM -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.titaniumEspThroughWalls, v -> config.titaniumEspThroughWalls = v);
				y = slider(graphics, font, ix, y, iw, "Range", config.titaniumEspRange + "m", (config.titaniumEspRange - 24) / 56f, v -> config.titaniumEspRange = StrayConfig.clamp(24 + Math.round(v * 56f), 24, 80));
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.titaniumEspRgb, PickerTarget.TITANIUM);
			}
			case CRYSTAL -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Find in chat", config.crystalHollowsFindChat, v -> config.crystalHollowsFindChat = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Entrance zones", config.crystalHollowsEntrances, v -> config.crystalHollowsEntrances = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.crystalHollowsThroughWalls, v -> config.crystalHollowsThroughWalls = v);
				clickRow(graphics, font, ix, y, iw, mouseX, mouseY, "Dump coords", CrystalHollows::dumpChat);
			}
			case CH_MAP -> toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Waypoint names", config.crystalHollowsMapLabels, v -> config.crystalHollowsMapLabels = v);
			case METAL -> toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Tool title", config.metalDetectorToolTitle, v -> config.metalDetectorToolTitle = v);
			case INVENTORY -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Minimal", config.inventoryHudMinimal, v -> config.inventoryHudMinimal = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Blur", config.inventoryHudBlur, v -> config.inventoryHudBlur = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Hotbar", config.inventoryHudHotbar, v -> config.inventoryHudHotbar = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Armor", config.inventoryHudArmor, v -> config.inventoryHudArmor = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Item count", config.inventoryHudCount, v -> config.inventoryHudCount = v);
			}
			case HEALTH -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Players", config.healthBarPlayers, v -> config.healthBarPlayers = v);
				y = cycle(graphics, font, ix, y, iw, mouseX, mouseY, "Side", config.healthBarSideLabel(), config::cycleHealthBarSide);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.healthBarThroughWalls, v -> config.healthBarThroughWalls = v);
				slider(graphics, font, ix, y, iw, "Range", config.healthBarRange + "m", (config.healthBarRange - 16) / 80f, v -> config.healthBarRange = StrayConfig.clamp(16 + Math.round(v * 80f), 16, 96));
			}
			case NAMETAGS -> {
				y = cycle(graphics, font, ix, y, iw, mouseX, mouseY, "Style", config.nametagStyleLabel(), config::cycleNametagStyle);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.nametagThroughWalls, v -> config.nametagThroughWalls = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Show distance", config.nametagDistance, v -> config.nametagDistance = v);
				y = slider(graphics, font, ix, y, iw, "Size", Math.round(config.nametagScale * 100) + "%", (config.nametagScale - 0.50f) / 1.50f, v -> config.nametagScale = StrayConfig.clamp(0.50f + v * 1.50f, 0.50f, 2.00f));
				y = slider(graphics, font, ix, y, iw, "Opacity", Math.round(config.nametagOpacity * 100) + "%", (config.nametagOpacity - 0.15f) / 0.85f, v -> config.nametagOpacity = StrayConfig.clamp(0.15f + v * 0.85f, 0.15f, 1f));
				slider(graphics, font, ix, y, iw, "Range", config.nametagRange + "m", (config.nametagRange - 64) / 192f, v -> config.nametagRange = StrayConfig.clamp(64 + Math.round(v * 192f), 64, 256));
			}
			case NODES -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Only in The End", config.onlyInTheEnd, v -> config.onlyInTheEnd = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Force enable", config.forceEnable, v -> config.forceEnable = v);
				y = slider(graphics, font, ix, y, iw, "Scan radius", config.scanRadius + "m", (config.scanRadius - 16) / 64f, v -> config.scanRadius = StrayConfig.clamp(16 + Math.round(v * 64f), 16, 80));
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Block scan", config.blockScan, v -> config.blockScan = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Particle hints", config.particleDetection, v -> config.particleDetection = v);
			}
		}
		fieldScope = previousScope;
	}

	private float clickerHand(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		int mouseX,
		int mouseY,
		String label,
		boolean enabled,
		Consumer<Boolean> setEnabled,
		float cps,
		Consumer<Float> setCps,
		int bindWhich,
		InputConstants.Key key
	) {
		float split = Math.min(w * 0.46f, w - 72f);
		toggle(graphics, font, x, y, split, mouseX, mouseY, label, enabled, setEnabled);
		slider(graphics, font, x + split + 4, y, Math.max(48f, w - split - 4), "CPS", cpsLabel(cps), (cps - 3.0f) / 12.0f, setCps);
		return bindRow(graphics, font, x, y + rowH(), w, mouseX, mouseY, "Bind", bindWhich, key);
	}

	private float drawMenuKeybinds(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		int mouseX,
		int mouseY
	) {
		StrayConfig config = StrayConfig.get();
		y = bindRow(graphics, font, x, y, w, mouseX, mouseY, "Open menu", 3, OdinClicks.parseKey(config.openGuiKey));
		y = bindRow(graphics, font, x, y, w, mouseX, mouseY, "Loadouts", 4, OdinClicks.parseKey(config.openLoadoutsKey));
		y = bindRow(graphics, font, x, y, w, mouseX, mouseY, "Wardrobe", 5, OdinClicks.parseKey(config.openWardrobeKey));
		return bindRow(graphics, font, x, y, w, mouseX, mouseY, "Profile", 7, OdinClicks.parseKey(config.openProfileKey));
	}

	private float bindRow(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		int mouseX,
		int mouseY,
		String label,
		int which,
		InputConstants.Key key
	) {
		float row = rowH();
		boolean listening = bindListen == which;
		String inner = listening ? "..." : OdinClicks.keyLabel(key);
		String value = "[ " + inner + " ]";
		float valueWidth = GuiDraw.menuWidth(font, value);
		float chipW = valueWidth + 8;
		float chipH = Math.min(row - 2f, controlCenter() ? 16f : 14f);
		float chipX = x + w - chipW;
		float chipY = y + (row - chipH) * 0.5f;
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, chipX, y, chipW, row);
		float hot = anim("bind-" + which, hovered || listening ? 1f : 0f);
		if (hot > 0.02f) {
			GuiDraw.rounded(graphics, chipX, chipY, chipW, chipH, 4, Anim.fade(Theme.withAlpha(Theme.ACCENT, controlCenter() ? 48 : 32), hot));
		}
		float labelY = GuiDraw.middle(y, row);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, ink());
		GuiDraw.menu(graphics, font, value, chipX + 4, labelY, Theme.ACCENT);
		hits.add(new Hit(chipX, y, chipW, row, () -> bindListen = which));
		return y + row;
	}

	private static String cpsLabel(float cps) {
		return String.format(java.util.Locale.ROOT, "%.1f", cps);
	}

	private static float snapCps(float value) {
		return Math.round(StrayConfig.clamp(value, 3.0f, 15.0f) * 2f) / 2f;
	}

	private static int snapInt(float value, int min, int max, int step) {
		int snapped = Math.round(value / step) * step;
		return Math.max(min, Math.min(max, snapped));
	}

	private void captureBind(InputConstants.Key key) {
		StrayConfig config = StrayConfig.get();
		String name = OdinClicks.keyName(key);
		switch (bindListen) {
			case 1 -> config.autoClickerLeftKey = name;
			case 2 -> config.autoClickerRightKey = name;
			case 3 -> config.openGuiKey = name;
			case 4 -> config.openLoadoutsKey = name;
			case 5 -> config.openWardrobeKey = name;
			case 6 -> config.chestAimKey = name;
			case 7 -> config.openProfileKey = name;
		}
		bindListen = 0;
		config.save();
		UnloadState.markDirty();
		StrayClient.syncMenuBindEdges();
	}

	private float cycle(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, String value, Runnable next) {
		float row = rowH();
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, row);
		if (hovered) {
			GuiDraw.rounded(graphics, x - 3, y, w + 6, row, 6, 0x08FFFFFF);
		}
		float labelY = GuiDraw.middle(y, row);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, ink());
		int valueWidth = GuiDraw.menuWidth(font, value);
		GuiDraw.menu(graphics, font, value, x + w - valueWidth, labelY, Theme.ACCENT);
		hits.add(new Hit(x, y, w, row, next));
		return y + row;
	}

	private float chipRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String[] labels, int selected, java.util.function.IntConsumer pick) {
		float cx = x;
		float rowY = y;
		for (int i = 0; i < labels.length; i++) {
			float cw = GuiDraw.menuWidth(font, labels[i]) + 10;
			if (cx > x && cx + cw > x + w) {
				cx = x;
				rowY += ROW;
			}
			boolean on = i == selected;
			boolean hover = GuiDraw.hovered(mouseX, mouseY, cx, rowY + 1, cw, ROW - 2);
			if (controlCenter()) {
				GuiDraw.rounded(graphics, cx, rowY + 1, cw, ROW - 2, 7, on ? Theme.ACCENT : hover ? 0x33FFFFFF : ControlChrome.searchFill());
				GuiDraw.menu(graphics, font, labels[i], cx + 5, GuiDraw.middle(rowY, ROW), on ? 0xFFFFFFFF : ink());
			} else {
				GuiDraw.panel(graphics, cx, rowY + 1, cw, ROW - 2, 5, on ? Theme.ACCENT : hover ? Theme.CARD_HOVER : Theme.CARD, on ? Theme.ACCENT : Theme.LINE);
				GuiDraw.menu(graphics, font, labels[i], cx + 5, GuiDraw.middle(rowY, ROW), on ? Theme.WINDOW_SOLID : Theme.TEXT);
			}
			int index = i;
			hits.add(new Hit(cx, rowY, cw, ROW, () -> pick.accept(index)));
			cx += cw + 4;
		}
		return rowY + ROW;
	}

	private static int aspectChipIndex(float ratio) {
		if (Math.abs(ratio - 1.00f) < 0.02f) {
			return 0;
		}
		if (Math.abs(ratio - 0.90f) < 0.02f) {
			return 1;
		}
		if (Math.abs(ratio - 0.75f) < 0.02f) {
			return 2;
		}
		if (Math.abs(ratio - 0.70f) < 0.02f) {
			return 3;
		}
		return -1;
	}

	private float slider(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, String valueText, float progress, Consumer<Float> setter) {
		float row = rowH();
		float labelY = GuiDraw.middle(y, row);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, ink());
		int valueWidth = GuiDraw.menuWidth(font, valueText);
		GuiDraw.menu(graphics, font, valueText, x + w - valueWidth, labelY, ink());
		float barX = x + GuiDraw.menuWidth(font, label) + 8;
		float barW = Math.max(24, w - GuiDraw.menuWidth(font, label) - valueWidth - 16);
		float barY = y + row * 0.45f;
		float t = Mth.clamp(progress, 0f, 1f);
		if (controlCenter()) {
			ControlChrome.slider(graphics, barX, barY - 1, barW, 5, t);
		} else {
			GuiDraw.pill(graphics, barX, barY, barW, 3, Theme.TRACK);
			GuiDraw.pill(graphics, barX, barY, Math.max(3, barW * t), 3, Theme.ACCENT);
			GuiDraw.circle(graphics, barX + barW * t, barY + 1.5f, 3.6f, Theme.ACCENT);
		}
		hits.add(new Hit(barX - 2, y, barW + 4, row, mx -> setter.accept(Mth.clamp((float) ((mx - barX) / barW), 0f, 1f)), true));
		return y + row;
	}

	private float colorRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, int rgb, PickerTarget target) {
		float row = rowH();
		float labelY = GuiDraw.middle(y, row);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, ink());
		float pw = 18;
		float ph = 10;
		float px = x + w - pw;
		float py = y + (row - ph) / 2f;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, px - 1, y, pw + 2, row);
		int swatch = Theme.withAlpha(rgb, Math.round(readOpacity(target) * 255f));
		GuiDraw.rounded(graphics, px - 1, py - 1, pw + 2, ph + 2, 3, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.rounded(graphics, px, py, pw, ph, 2, swatch);
		boolean fromPage = contentHitMode;
		hits.add(new Hit(px - 2, y, pw + 4, row, () -> {
			if (target == PickerTarget.SKY) {
				StrayConfig.get().matchSkyToWorld = false;
			}
			if (target == PickerTarget.FOG) {
				StrayConfig.get().matchFogToWorld = false;
			}
			float pickerAt = y + row + 2;
			if (fromPage) {
				pickerAt -= tabScroll;
			}
			openPicker(target, rgb, px - PICKER_W + pw, pickerAt);
		}));
		return y + row;
	}

	private float readout(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, boolean on) {
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, ink());
		GuiDraw.menu(graphics, font, on ? "ON" : "OFF", x + w - GuiDraw.menuWidth(font, on ? "ON" : "OFF"), labelY, on ? Theme.ACCENT : fade());
		return y + ROW;
	}

	private float statRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, String value) {
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, ink());
		GuiDraw.menu(graphics, font, value, x + w - GuiDraw.menuWidth(font, value), labelY, Theme.ACCENT);
		return y + ROW;
	}

	private float hint(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String text) {
		GuiDraw.small(graphics, font, text, x + 1, GuiDraw.middle(y, ROW) + 1, Theme.WARN);
		return y + ROW;
	}

	private void drawPicker(GuiGraphicsExtractor graphics, Font font) {
		float x = pickerX;
		float y = pickerY;
		float w = PICKER_W;
		float h = pickerHeight();
		GuiDraw.sheet(graphics, x, y, w, h, 8, Anim.fade(Theme.SHEET, pickerT), Anim.fade(Theme.ACCENT, pickerT));
		if (pickerT < 0.7f) {
			return;
		}
		GuiDraw.menu(graphics, font, "Color", x + 6, y + 5, Theme.MUTED);

		int current = WorldTint.hsvToRgb(pickerHue, pickerSat, pickerVal);
		int preview = Theme.withAlpha(current, Math.round(sliderToOpacity(pickerTarget, pickerAlpha) * 255f));
		GuiDraw.rounded(graphics, x + w - 22, y + 5, 14, 10, 3, Theme.LINE);
		GuiDraw.rounded(graphics, x + w - 21, y + 6, 12, 8, 2, preview);

		float svX = x + 6;
		float svY = y + 20;
		float svW = w - 12;
		float svH = 68;
		GuiDraw.hsvSquare(graphics, svX, svY, svW, svH, pickerHue);
		float cursorX = svX + pickerSat * svW;
		float cursorY = svY + (1f - pickerVal) * svH;
		GuiDraw.circle(graphics, cursorX, cursorY, 3.6f, 0xFF000000);
		GuiDraw.circle(graphics, cursorX, cursorY, 2.6f, 0xFFFFFFFF);
		GuiDraw.circle(graphics, cursorX, cursorY, 1.6f, 0xFF000000 | current);
		hits.add(new Hit(svX, svY, svW, svH, mx -> {
			pickerSat = Mth.clamp((float) ((mx - svX) / svW), 0f, 1f);
			pickerVal = Mth.clamp(1f - (float) ((lastClickY - svY) / svH), 0f, 1f);
			commitPicker();
		}, true));

		float hueY = svY + svH + 4;
		float hueH = 6;
		GuiDraw.hueBar(graphics, svX, hueY, svW, hueH);
		float hueMark = svX + (pickerHue / 360f) * svW;
		GuiDraw.fill(graphics, hueMark - 1.2f, hueY - 1, 2.4f, hueH + 2, 0xFF000000);
		GuiDraw.fill(graphics, hueMark - 0.5f, hueY - 1, 1f, hueH + 2, 0xFFFFFFFF);
		hits.add(new Hit(svX, hueY, svW, hueH, mx -> {
			pickerHue = Mth.clamp((float) ((mx - svX) / svW) * 360f, 0f, 359f);
			commitPicker();
		}, true));

		float alphaY = hueY + hueH + 4;
		float alphaH = 6;
		GuiDraw.alphaBar(graphics, svX, alphaY, svW, alphaH, current);
		float alphaMark = svX + pickerAlpha * svW;
		GuiDraw.fill(graphics, alphaMark - 1.2f, alphaY - 1, 2.4f, alphaH + 2, 0xFF000000);
		GuiDraw.fill(graphics, alphaMark - 0.5f, alphaY - 1, 1f, alphaH + 2, 0xFFFFFFFF);
		hits.add(new Hit(svX, alphaY, svW, alphaH, mx -> {
			pickerAlpha = Mth.clamp((float) ((mx - svX) / svW), 0f, 1f);
			commitPicker();
		}, true));

		GuiDraw.menu(graphics, font, hex(current) + "  " + Math.round(sliderToOpacity(pickerTarget, pickerAlpha) * 100f) + "%", x + 6, y + h - 12, Theme.MUTED);
	}

	private static float pickerHeight() {
		return PICKER_H;
	}

	private void openPicker(PickerTarget target, int rgb, float x, float y) {
		pickerTarget = target;
		float[] hsv = WorldTint.rgbToHsv(rgb);
		pickerHue = hsv[0];
		pickerSat = hsv[1];
		pickerVal = hsv[2];
		pickerAlpha = opacityToSlider(target);
		float h = pickerHeight();
		pickerX = Mth.clamp(x, windowX + sidebarW() + 4, windowX + windowW - PICKER_W - 4);
		pickerY = Mth.clamp(y, windowY + toolbarH(), windowY + windowH - h - 4);
	}

	private void commitPicker() {
		applyColor(pickerTarget, WorldTint.hsvToRgb(pickerHue, pickerSat, pickerVal));
		writeOpacity(pickerTarget, sliderToOpacity(pickerTarget, pickerAlpha));
	}

	private void applyColor(PickerTarget target, int rgb) {
		StrayConfig config = StrayConfig.get();
		int packed = rgb & 0xFFFFFF;
		switch (target) {
			case WORLD -> config.worldTintRgb = packed;
			case SKY -> config.skyTintRgb = packed;
			case FOG -> config.fogRgb = packed;
			case NODE -> config.colorRgb = packed;
			case MOB -> config.mobGlowRgb = packed;
			case STAR -> config.starMobRgb = packed;
			case BLOCK -> config.blockOutlineRgb = packed;
			case TITANIUM -> config.titaniumEspRgb = packed;
			case PEST -> config.pestEspRgb = packed;
			case CHEST -> config.chestEspRgb = packed;
			case HELD_ITEM -> config.heldItemShaderRgb = packed;
			case HELD_ITEM_OUTLINE -> config.heldItemShaderOutlineRgb = packed;
			case FILL -> config.playerFillRgb = packed;
			case FILL_OUTLINE -> config.playerFillOutlineRgb = packed;
			case THEME -> Theme.applyCustom(packed);
			case PANE -> Theme.applyPane(packed);
			case CONTROL -> {
				config.controlPaneRgb = packed == 0 ? 0x181818 : packed;
				Theme.refresh();
			}
			case PILL -> {
				config.controlPillRgb = packed == 0 ? 0x808080 : packed;
				Theme.refresh();
			}
		}
	}

	private static float opacityMin(PickerTarget target) {
		return switch (target) {
			case CONTROL, PILL -> 0.12f;
			case PANE -> 0.20f;
			case MOB, STAR, BLOCK -> 0.15f;
			case NODE, HELD_ITEM, FILL, CHEST, TITANIUM, PEST -> 0.08f;
			case THEME, HELD_ITEM_OUTLINE, FILL_OUTLINE -> 1f;
			default -> 0f;
		};
	}

	private static float opacityMax(PickerTarget target) {
		return switch (target) {
			case CONTROL, PILL -> 0.78f;
			case MOB, STAR, BLOCK -> 0.90f;
			case NODE, HELD_ITEM, FILL, CHEST, TITANIUM, PEST -> 0.85f;
			default -> 1f;
		};
	}

	private static float readOpacity(PickerTarget target) {
		StrayConfig config = StrayConfig.get();
		return switch (target) {
			case CONTROL -> config.controlPaneOpacity;
			case PILL -> config.controlPillOpacity;
			case PANE -> config.themePaneOpacity;
			case MOB -> config.mobGlowOpacity;
			case STAR -> config.starMobOpacity;
			case BLOCK -> config.blockOutlineOpacity;
			case NODE -> config.fillOpacity;
			case CHEST -> config.chestEspOpacity;
			case TITANIUM -> config.titaniumEspOpacity;
			case PEST -> config.pestEspOpacity;
			case HELD_ITEM -> config.heldItemShaderFill;
			case FILL -> config.playerFillFill;
			case WORLD -> config.worldTintStrength;
			case SKY -> config.skyTintStrength;
			case FOG -> config.fogDensity;
			case THEME, HELD_ITEM_OUTLINE, FILL_OUTLINE -> 1f;
		};
	}

	private static void writeOpacity(PickerTarget target, float value) {
		StrayConfig config = StrayConfig.get();
		float clamped = StrayConfig.clamp(value, opacityMin(target), opacityMax(target));
		switch (target) {
			case CONTROL -> config.controlPaneOpacity = clamped;
			case PILL -> config.controlPillOpacity = clamped;
			case PANE -> config.themePaneOpacity = clamped;
			case MOB -> config.mobGlowOpacity = clamped;
			case STAR -> config.starMobOpacity = clamped;
			case BLOCK -> config.blockOutlineOpacity = clamped;
			case NODE -> config.fillOpacity = clamped;
			case CHEST -> config.chestEspOpacity = clamped;
			case TITANIUM -> config.titaniumEspOpacity = clamped;
			case PEST -> config.pestEspOpacity = clamped;
			case HELD_ITEM -> config.heldItemShaderFill = clamped;
			case FILL -> config.playerFillFill = clamped;
			case WORLD -> config.worldTintStrength = clamped;
			case SKY -> config.skyTintStrength = clamped;
			case FOG -> config.fogDensity = clamped;
			case THEME, HELD_ITEM_OUTLINE, FILL_OUTLINE -> {
				return;
			}
		}
		if (target == PickerTarget.CONTROL || target == PickerTarget.PILL || target == PickerTarget.PANE) {
			Theme.refresh();
		}
	}

	private static float opacityToSlider(PickerTarget target) {
		float min = opacityMin(target);
		float max = opacityMax(target);
		if (max <= min) {
			return 1f;
		}
		return Mth.clamp((readOpacity(target) - min) / (max - min), 0f, 1f);
	}

	private static float sliderToOpacity(PickerTarget target, float slider) {
		float min = opacityMin(target);
		float max = opacityMax(target);
		if (max <= min) {
			return min;
		}
		return min + Mth.clamp(slider, 0f, 1f) * (max - min);
	}

	private void startDrag(double mx, double my) {
		dragging = true;
		dragOffX = mx - windowX;
		dragOffY = my - windowY;
	}

	private static String aspectLabel(float ratio) {
		if (Math.abs(ratio - 1.00f) < 0.02f) {
			return "Native";
		}
		if (Math.abs(ratio - 0.75f) < 0.02f) {
			return "4:3";
		}
		if (Math.abs(ratio - 0.70f) < 0.02f) {
			return "5:4";
		}
		if (Math.abs(ratio - 0.90f) < 0.02f) {
			return "16:10";
		}
		return Math.round(ratio * 100) + "%";
	}

	private static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	private static String modVersion() {
		return FabricLoader.getInstance()
			.getModContainer("stray")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("1.2.216");
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (bindListen != 0) {
			captureBind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			return true;
		}
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		lastClickY = localY(event.y());
		dragging = false;
		double lx = localX(event.x());
		double ly = lastClickY;
		boolean onCape = GuiDraw.hovered(lx, ly, capeFieldX, capeFieldY, capeFieldW, ROW);
		boolean onNick = GuiDraw.hovered(lx, ly, nickFieldX, nickFieldY, nickFieldW, 16);
		if (capeFocused && !onCape) {
			capeFocused = false;
			commitCapeUrl();
		}
		if (nickFocused && !onNick) {
			nickFocused = false;
		}
		boolean onMobSearch = GuiDraw.hovered(lx, ly, mobFieldX, mobFieldY, mobFieldW, 14);
		if (mobSearchFocused && !onMobSearch) {
			mobSearchFocused = false;
		}
		boolean onFontSearch = fontPickerOpen && GuiDraw.hovered(lx, ly, fontListX, fontListY - FONT_SEARCH_H - 2, fontListW, FONT_SEARCH_H);
		if (fontSearchFocused && !onFontSearch) {
			fontSearchFocused = false;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(lx, ly)) {
				hit.click(lx);
				return true;
			}
		}
		if (pickerTarget != null && !GuiDraw.hovered(lx, ly, pickerX, pickerY, PICKER_W, pickerHeight())) {
			pickerTarget = null;
			return true;
		}
		if (settingsOpen && !GuiDraw.hovered(lx, ly, settingsX, settingsY, PANEL_W, settingsHeight())) {
			settingsOpen = false;
			fontPickerOpen = false;
			fontSearchFocused = false;
			return true;
		}
		if (notesOpen && !GuiDraw.hovered(lx, ly, notesX, notesY, PANEL_W, notesH)) {
			notesOpen = false;
			return true;
		}
		if (featureOpen && featureId != null && !GuiDraw.hovered(lx, ly, featureX, featureY, FEATURE_W, featureId.height())) {
			featureOpen = false;
			bindListen = 0;
			return true;
		}
		if (searchOpen && !GuiDraw.hovered(lx, ly, searchFieldX, windowY + 5, searchFieldW, 80)) {
			searchOpen = false;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0 && dragging) {
			windowX = localX(event.x()) - (float) dragOffX;
			windowY = localY(event.y()) - (float) dragOffY;
			moved = true;
			return true;
		}
		if (event.button() == 0) {
			lastClickY = localY(event.y());
			double lx = localX(event.x());
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit hit = hits.get(i);
				if (hit.drag && hit.contains(lx, lastClickY)) {
					hit.click(lx);
					return true;
				}
			}
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && moved) {
			persistMenuPosition();
		}
		dragging = false;
		StrayConfig.get().save();
		WorldTint.syncChunkMeshes(minecraft);
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double lx = localX(mouseX);
		double ly = localY(mouseY);
		if (notesOpen && scrollY != 0 && GuiDraw.hovered(lx, ly, notesX, notesY, PANEL_W, notesH)) {
			float maxScroll = Math.max(0f, ReleaseNotes.contentHeight(font, PANEL_W - 16, 11f) - (notesH - 28));
			notesScroll = Mth.clamp(notesScroll - (float) scrollY * 18f, 0f, maxScroll);
			return true;
		}
		if (fontPickerOpen && scrollY != 0 && GuiDraw.hovered(lx, ly, fontListX, fontListY, fontListW, fontListH)) {
			float maxScroll = Math.max(0f, fontMatches().size() * FONT_ROW - fontListH);
			fontScroll = Mth.clamp(fontScroll - (float) scrollY * FONT_ROW * 2.2f, 0f, maxScroll);
			return true;
		}
		if (nametagListLive() && scrollY != 0 && pageHover(lx, ly, nametagEspListX, nametagEspListY, nametagEspListW, nametagEspListH)) {
			float maxScroll = Math.max(0f, StrayConfig.get().nametagEspLabels().size() * ROW - nametagEspListH);
			nametagEspScroll = Mth.clamp(nametagEspScroll - (float) scrollY * ROW * 2.2f, 0f, maxScroll);
			return true;
		}
		if (mobListLive() && scrollY != 0 && pageHover(lx, ly, mobFieldX, mobFieldY, mobListW, mobListY + mobListH - mobFieldY)) {
			List<MobCatalog.Entry> entries = MobCatalog.filtered(mobQuery);
			float row = rowH();
			float maxScroll = Math.max(0f, entries.size() * row - mobListH);
			mobScroll = Mth.clamp(mobScroll - (float) scrollY * row * 2.2f, 0f, maxScroll);
			return true;
		}
		if (scrollY != 0 && tabScrollMax > 0.5f && GuiDraw.hovered(lx, ly, pageClipX, pageClipY, pageClipW, pageClipH)) {
			tabScroll = Mth.clamp(tabScroll - (float) scrollY * 28f, 0f, tabScrollMax);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void persistMenuPosition() {
		StrayConfig config = StrayConfig.get();
		config.menuX = windowX;
		config.menuY = windowY;
		config.menuPlaced = true;
		config.menuTab = tab.name();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (bindListen != 0) {
			if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_BACKSPACE) {
				captureBind(InputConstants.UNKNOWN);
				return true;
			}
			if (event.key() == InputConstants.KEY_RETURN) {
				bindListen = 0;
				return true;
			}
			captureBind(InputConstants.Type.KEYSYM.getOrCreate(event.key()));
			return true;
		}
		if (event.isEscape()) {
			if (capeFocused) {
				capeFocused = false;
				commitCapeUrl();
				return true;
			}
			if (nickFocused) {
				nickFocused = false;
				return true;
			}
			if (mobSearchFocused) {
				if (!mobQuery.isEmpty()) {
					mobQuery = "";
					mobScroll = 0f;
				} else {
					mobSearchFocused = false;
				}
				return true;
			}
			if (fontSearchFocused) {
				if (!fontQuery.isEmpty()) {
					fontQuery = "";
					fontScroll = 0f;
				} else {
					fontSearchFocused = false;
				}
				return true;
			}
			if (fontPickerOpen) {
				fontPickerOpen = false;
				return true;
			}
			if (searchOpen) {
				searchOpen = false;
				searchQuery = "";
				return true;
			}
			if (settingsOpen) {
				settingsOpen = false;
				fontPickerOpen = false;
				fontSearchFocused = false;
				return true;
			}
			if (notesOpen) {
				notesOpen = false;
				return true;
			}
			if (pickerTarget != null) {
				pickerTarget = null;
				return true;
			}
			if (featureOpen) {
				featureOpen = false;
				return true;
			}
		}
		if (capeFocused && event.key() == InputConstants.KEY_BACKSPACE) {
			if (!capeUrlDraft.isEmpty()) {
				capeUrlDraft = capeUrlDraft.substring(0, capeUrlDraft.length() - 1);
			}
			return true;
		}
		if (nickFocused && event.key() == InputConstants.KEY_BACKSPACE) {
			String nick = StrayConfig.get().nick;
			if (nick != null && !nick.isEmpty()) {
				StrayConfig.get().nick = nick.substring(0, nick.length() - 1);
			}
			return true;
		}
		if (fontSearchFocused && event.key() == InputConstants.KEY_BACKSPACE) {
			if (!fontQuery.isEmpty()) {
				fontQuery = fontQuery.substring(0, fontQuery.length() - 1);
				fontScroll = 0f;
			}
			return true;
		}
		if (mobSearchFocused && event.key() == InputConstants.KEY_BACKSPACE) {
			if (!mobQuery.isEmpty()) {
				mobQuery = mobQuery.substring(0, mobQuery.length() - 1);
				mobScroll = 0f;
			}
			return true;
		}
		if (capeFocused && event.key() == InputConstants.KEY_RETURN) {
			capeFocused = false;
			commitCapeUrl();
			return true;
		}
		if (nickFocused && event.key() == InputConstants.KEY_RETURN) {
			nickFocused = false;
			return true;
		}
		if (capeFocused && event.key() == InputConstants.KEY_V && event.hasControlDown()) {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip != null && !clip.isBlank()) {
				capeUrlDraft += clip.replace("\n", "").replace("\r", "").trim();
			}
			return true;
		}
		if (nickFocused && event.key() == InputConstants.KEY_V && event.hasControlDown()) {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip != null && !clip.isBlank()) {
				StrayConfig config = StrayConfig.get();
				config.nick = (config.nick == null ? "" : config.nick) + clip.replace("\n", "").replace("\r", "");
			}
			return true;
		}
		if (fontSearchFocused && event.key() == InputConstants.KEY_V && event.hasControlDown()) {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip != null && !clip.isBlank()) {
				fontQuery += clip.replace("\n", "").replace("\r", "");
				fontScroll = 0f;
			}
			return true;
		}
		if (searchOpen && event.key() == InputConstants.KEY_BACKSPACE) {
			if (!searchQuery.isEmpty()) {
				searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
			}
			return true;
		}
		if (event.key() == InputConstants.KEY_F && event.hasControlDown()) {
			searchOpen = true;
			settingsOpen = false;
			notesOpen = false;
			featureOpen = false;
			capeFocused = false;
			nickFocused = false;
			mobSearchFocused = false;
			fontPickerOpen = false;
			fontSearchFocused = false;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (capeFocused && event.isAllowedChatCharacter()) {
			capeUrlDraft += event.codepointAsString();
			return true;
		}
		if (nickFocused && event.isAllowedChatCharacter()) {
			StrayConfig config = StrayConfig.get();
			if (config.nick == null) {
				config.nick = "";
			}
			if (config.nick.length() < 48) {
				config.nick += event.codepointAsString();
			}
			return true;
		}
		if (mobSearchFocused && event.isAllowedChatCharacter()) {
			if (mobQuery.length() < 32) {
				mobQuery += event.codepointAsString();
				mobScroll = 0f;
			}
			return true;
		}
		if (fontSearchFocused && event.isAllowedChatCharacter()) {
			if (fontQuery.length() < 48) {
				fontQuery += event.codepointAsString();
				fontScroll = 0f;
			}
			return true;
		}
		if (searchOpen && event.isAllowedChatCharacter()) {
			searchQuery += event.codepointAsString();
			return true;
		}
		return super.charTyped(event);
	}

	public boolean isCapturingBind() {
		return bindListen != 0;
	}

	public boolean shouldIgnoreMenuBinds() {
		return bindListen != 0 || capeFocused || nickFocused || searchOpen || mobSearchFocused || fontSearchFocused;
	}

	public void requestClose() {
		onClose();
	}

	@Override
	public void onClose() {
		if (moved) {
			persistMenuPosition();
		} else {
			StrayConfig.get().menuTab = tab.name();
		}
		StrayConfig.get().save();
		commitCapeUrl();
		if (!closing && StrayConfig.get().uiAnimations && appear > 0.04f) {
			closing = true;
			capeFocused = false;
			nickFocused = false;
			return;
		}
		finishClose();
	}

	private void finishClose() {
		if (finishedClose) {
			return;
		}
		finishedClose = true;
		WorldTint.syncChunkMeshes(minecraft);
		super.onClose();
	}

	private final class Hit {
		final float x, y, w, h;
		final Runnable click;
		final DoubleConsumer dragClick;
		final boolean drag;
		final boolean scrolled;

		Hit(float x, float y, float w, float h, Runnable click) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.click = click;
			this.dragClick = null;
			this.drag = false;
			this.scrolled = contentHitMode;
		}

		Hit(float x, float y, float w, float h, DoubleConsumer dragClick, boolean drag) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.click = null;
			this.dragClick = dragClick;
			this.drag = drag;
			this.scrolled = contentHitMode;
		}

		boolean contains(double mx, double my) {
			if (scrolled) {
				if (mx < pageClipX || mx > pageClipX + pageClipW || my < pageClipY || my > pageClipY + pageClipH) {
					return false;
				}
				my += tabScroll;
			}
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}

		void click(double mx) {
			if (dragClick != null) {
				dragClick.accept(mx);
			} else if (click != null) {
				click.run();
			}
		}
	}
}
