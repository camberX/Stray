package dev.stray.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import dev.stray.client.render.GlowBlurRadius;
import dev.stray.client.render.MobCatalog;
import dev.stray.client.ui.Theme;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StrayConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("stray.json");
	private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("voidmark.json");
	private static StrayConfig instance = new StrayConfig();
	public boolean markersEnabled = false;
	public boolean hudEnabled = false;
	public boolean tracersEnabled = false;
	public boolean boxFill = false;
	public boolean boxOutline = false;
	public boolean throughWalls = false;
	public boolean onlyInTheEnd = false;
	public boolean forceEnable = false;
	public boolean particleDetection = false;
	public boolean blockScan = false;
	public int scanRadius = 48;
	public float fillOpacity = 0.32f;
	public int colorRgb = 0x2FB5FF;
	public boolean worldTintEnabled = false;
	public int worldTintRgb = 0x2FB5FF;
	public float worldTintStrength = 0.70f;
	public String worldTintMode = "shader";
	public boolean skyTintEnabled = false;
	public int skyTintRgb = 0x1B4F8A;
	public float skyTintStrength = 0.70f;
	public boolean matchSkyToWorld = false;
	public boolean fogEnabled = false;
	public int fogRgb = 0x8EC8FF;
	public float fogStart = 0.12f;
	public float fogEnd = 0.72f;
	public float fogDensity = 1.0f;
	public boolean matchFogToWorld = false;
	public boolean aspectEnabled = false;
	public float aspectRatio = 1.0f;
	public int themeAccentRgb = 0x2FB5FF;
	public int themePaneRgb = 0x0B0E14;
	public String themePreset = "cyan";
	public boolean uiAnimations = false;
	public boolean autoUpdate = false;
	public boolean updateNotify = true;
	public boolean watermarkEnabled = false;
	public boolean watermarkFps = false;
	public boolean watermarkPing = false;
	public boolean watermarkTime = false;
	public boolean watermarkName = false;
	public boolean musicHudEnabled = false;
	public boolean musicHideIdle = false;
	public boolean musicChatAnnounce = false;
	public boolean musicChatOffDefault = false;
	public boolean spotifyEnabled = false;
	public int musicApiPort = 0;
	public String musicApiToken = "";
	public String spotifyClientId = "";
	public String spotifyRefreshToken = "";
	public String spotifyAccessToken = "";
	public long spotifyAccessExpiresAt = 0L;
	public boolean rawmatsHudEnabled = false;
	public boolean pickupLogEnabled = false;
	public boolean miningHudEnabled = false;
	public boolean miningAbilityAlert = false;
	public boolean farmingYawPitch = false;
	public float farmingYawPitchScale = 1.00f;
	public boolean jacobContestHudEnabled = false;
	public boolean composterHudEnabled = false;
	public boolean composterUpgradesKnown = false;
	public String composterProfile = "";
	public int composterSpeed = 0;
	public int composterMultiDrop = 0;
	public int composterFuelCap = 0;
	public int composterOrganicMatterCap = 0;
	public int composterCostReduction = 0;
	public long composterMaxOrganic = 0;
	public long composterMaxFuel = 0;
	public boolean titaniumEsp = false;
	public boolean titaniumEspThroughWalls = false;
	public boolean chestEspEnabled = false;
	public boolean chestEspTracers = false;
	public boolean chestEspThroughWalls = false;
	public float chestAimSpeed = 1.00f;
	public String openGuiKey = "key.keyboard.right.shift";
	public String openLoadoutsKey = "key.keyboard.unknown";
	public String openWardrobeKey = "key.keyboard.unknown";
	public String openProfileKey = "key.keyboard.unknown";
	public String chestAimKey = "key.keyboard.unknown";
	public int chestEspRgb = 0xF4C14E;
	public float chestEspOpacity = 0.34f;
	public boolean loadoutsMenuEnabled = false;
	public boolean loadoutsOpenAnim = false;
	public boolean wardrobeMenuEnabled = false;
	public boolean profileViewerEnabled = false;
	public boolean hitsoundEnabled = false;
	public boolean hitsoundMelee = false;
	public boolean hitsoundArrows = false;
	public boolean hitmarkerEnabled = false;
	public boolean triggerbotEnabled = false;
	public boolean triggerbotPlayers = false;
	public float triggerbotHumanize = 0.50f;
	public boolean autoClickerEnabled = false;
	public boolean autoClickerWhiteListOnly = false;
	public boolean autoClickerAllowBreaking = false;
	public boolean autoClickerBlockBreaker = false;
	public boolean autoClickerTerminatorOnly = false;
	public float autoClickerCps = 5.0f;
	public boolean autoClickerEnableLeftClick = false;
	public boolean autoClickerEnableRightClick = false;
	public float autoClickerLeftCps = 5.0f;
	public float autoClickerRightCps = 5.0f;
	public String autoClickerLeftKey = "key.keyboard.unknown";
	public String autoClickerRightKey = "key.keyboard.unknown";
	public java.util.List<String> autoClickerLeftWhitelist = new java.util.ArrayList<>();
	public java.util.List<String> autoClickerRightWhitelist = new java.util.ArrayList<>();
	public boolean autoExperimentsEnabled = false;
	public int autoExperimentsClickDelay = 200;
	public int autoExperimentsDelayVariety = 50;
	public boolean autoExperimentsAutoClose = false;
	public int autoExperimentsSerumCount = 0;
	public boolean autoExperimentsGetMaxXp = false;
	public float hitmarkerScale = 1.00f;
	public float hitsoundVolume = 0.80f;
	public float hitsoundPitch = 1.00f;
	public boolean heldItemShaderEnabled = false;
	public int heldItemShaderRgb = 0x4FD6EA;
	public int heldItemShaderOutlineRgb = liftedOutlineRgb(0x4FD6EA);
	public float heldItemShaderFill = 0.32f;
	public float heldItemShaderOutline = 0.90f;
	public float heldItemShaderSmoke = 0.55f;
	public String heldItemShaderStyle = "smoke";
	public boolean playerFillEsp = false;
	public boolean playerFillThroughWalls = false;
	public boolean playerFillMobs = false;
	public boolean playerFillStarMobs = false;
	public int playerFillRgb = 0x4FD6EA;
	public int playerFillOutlineRgb = liftedOutlineRgb(0x4FD6EA);
	public float playerFillFill = 0.32f;
	public float playerFillOutline = 0.90f;
	public float playerFillSmoke = 0.55f;
	public String playerFillStyle = "smoke";
	public int titaniumEspRange = 48;
	public int titaniumEspRgb = 0xE8ECF2;
	public float titaniumEspOpacity = 0.38f;
	public boolean rawmatsEnchanted = false;
	public String rawmatsItemId = "";
	public boolean inventoryHudEnabled = false;
	public boolean inventoryHudHotbar = false;
	public boolean inventoryHudArmor = false;
	public boolean inventoryHudCount = false;
	public boolean hudHotbar = false;
	public boolean hudHealth = false;
	public boolean hudHunger = false;
	public boolean hudArmor = false;
	public boolean hudAir = false;
	public boolean hudExperience = false;
	public boolean hudScoreboard = false;
	public boolean hudBossBar = false;
	public boolean hudEffects = false;
	public boolean hudHeldItem = false;
	public boolean hudMountHealth = false;
	public HudSlot slotHotbar = new HudSlot();
	public HudSlot slotHealth = new HudSlot();
	public HudSlot slotHunger = new HudSlot();
	public HudSlot slotArmor = new HudSlot();
	public HudSlot slotAir = new HudSlot();
	public HudSlot slotExperience = new HudSlot();
	public HudSlot slotMount = new HudSlot();
	public HudSlot slotScoreboard = new HudSlot();
	public HudSlot slotBoss = new HudSlot();
	public HudSlot slotEffects = new HudSlot();
	public HudSlot slotHeldItem = new HudSlot();
	public String inventoryHudAnchor = "bottom_right";
	public float inventoryHudScale = 1.0f;
	public float hudWatermarkScale = 1.0f;
	public float hudNodesScale = 1.0f;
	public float hudMusicScale = 1.0f;
	public float hudRawmatsScale = 1.0f;
	public float hudPickupScale = 1.0f;
	public float hudMiningScale = 1.0f;
	public float hudJacobScale = 1.0f;
	public float hudComposterScale = 1.0f;
	public float hudInventoryX = -1f;
	public float hudInventoryY = -1f;
	public float hudWatermarkX = -1f;
	public float hudWatermarkY = -1f;
	public float hudNodesX = -1f;
	public float hudNodesY = -1f;
	public float hudMusicX = -1f;
	public float hudMusicY = -1f;
	public float hudRawmatsX = -1f;
	public float hudRawmatsY = -1f;
	public float hudPickupX = -1f;
	public float hudPickupY = -1f;
	public float hudMiningX = -1f;
	public float hudMiningY = -1f;
	public float hudJacobX = -1f;
	public float hudJacobY = -1f;
	public float hudComposterX = -1f;
	public float hudComposterY = -1f;
	public float menuX = -1f;
	public float menuY = -1f;
	public boolean menuPlaced = false;
	public String menuTab = "WORLD";
	public String changelogSeen = "";
	public float themePaneOpacity = 0.90f;
	public float hudOpacity = 0.90f;
	public String capeUrl = "";
	public String capePath = "";
	public String capeShopKey = "";
	public boolean nickEnabled = false;
	public String nick = "";
	public boolean nametagsEnabled = false;
	public boolean nametagSelf = false;
	public boolean nametagThroughWalls = false;
	public boolean nametagDistance = false;
	public String nametagStyle = "custom";
	public int nametagRange = 128;
	public float nametagScale = 1.0f;
	public float nametagOpacity = 1.0f;
	public float menuScale = 0.75f;
	public boolean menuScaleV2;
	public boolean menuStarfield = false;
	public boolean hudStarfield = false;
	public String guiDesign = "control";
	public int controlPaneRgb = 0x181818;
	public int controlPillRgb = 0x808080;
	public float controlPaneOpacity = 0.64f;
	public float controlPillOpacity = 0.17f;
	public float controlFrost = 0.62f;
	public boolean controlPaletteV2;
	public boolean featureTogglesOffV1;
	public String uiFont = "Minecraft";
	public boolean mobGlowEnabled = false;
	public boolean starMobEsp = false;
	public boolean starMobBats = false;
	public boolean starMobFels = false;
	public boolean starMobThroughWalls = false;
	public float starMobRadius = GlowBlurRadius.DEFAULT;
	public float starMobOpacity = 0.58f;
	public int starMobRgb = 0xFFD84A;
	public boolean mobGlowThroughWalls = false;
	public boolean blockOutlineGlow = false;
	public float blockOutlineOpacity = 0.58f;
	public int blockOutlineRgb = 0x2FB5FF;
	public String mobGlowId = "";
	public String mobGlowName = "";
	public java.util.List<String> mobGlowNames = new java.util.ArrayList<>();
	public java.util.List<String> mobGlowIds = new java.util.ArrayList<>();
	public float mobGlowSize = 0.48f;
	public float mobGlowRadius = GlowBlurRadius.DEFAULT;
	public float mobGlowOpacity = 0.58f;
	public int mobGlowRgb = 0x2FB5FF;
	public java.util.List<ItemSkin> itemSkins = new java.util.ArrayList<>();
	public String itemClipboardName = "";
	public String itemClipboardItemName = "";
	public String itemClipboardLore = "";

	private StrayConfig() {
	}

	public void normalizeMobGlowIds() {
		mobGlowIds = new java.util.ArrayList<>(MobCatalog.normalizeIds(mobGlowIds));
		mobGlowId = mobGlowIds.isEmpty() ? "" : mobGlowIds.get(0);
	}

	public boolean isMobGlowSelected(String id) {
		String key = MobCatalog.canonical(id);
		if (key == null || key.isEmpty()) {
			return false;
		}
		for (String selected : mobGlowIds) {
			if (key.equals(MobCatalog.canonical(selected))) {
				return true;
			}
		}
		return false;
	}

	/** Toggle a catalog id. Selecting a type while glow is off also turns glow on. */
	public void toggleMobGlow(String id) {
		String key = MobCatalog.canonical(id);
		if (key == null || key.isEmpty()) {
			return;
		}
		java.util.List<String> next = new java.util.ArrayList<>(MobCatalog.normalizeIds(mobGlowIds));
		if (next.contains(key)) {
			next.remove(key);
		} else {
			next.add(key);
			if (!mobGlowEnabled) {
				mobGlowEnabled = true;
			}
		}
		mobGlowIds = next;
		mobGlowId = next.isEmpty() ? "" : next.get(0);
	}

	public java.util.List<String> nametagEspLabels() {
		normalizeNametagEsp();
		return mobGlowNames;
	}

	public java.util.List<String> nametagEspNeedles() {
		java.util.List<String> needles = new java.util.ArrayList<>();
		for (String label : nametagEspLabels()) {
			needles.add(label.toLowerCase(java.util.Locale.ROOT));
		}
		return needles;
	}

	public boolean addNametagEsp(String raw) {
		String label = raw == null ? "" : raw.trim();
		if (label.isEmpty()) {
			return false;
		}
		normalizeNametagEsp();
		for (String existing : mobGlowNames) {
			if (existing.equalsIgnoreCase(label)) {
				return false;
			}
		}
		if (mobGlowNames.size() >= 24) {
			mobGlowNames.remove(0);
		}
		mobGlowNames.add(label);
		mobGlowEnabled = true;
		syncNametagEsp();
		return true;
	}

	public boolean removeNametagEsp(String raw) {
		String label = raw == null ? "" : raw.trim();
		if (label.isEmpty()) {
			return false;
		}
		normalizeNametagEsp();
		boolean removed = mobGlowNames.removeIf(existing -> existing.equalsIgnoreCase(label));
		if (removed) {
			syncNametagEsp();
		}
		return removed;
	}

	public void clearNametagEsp() {
		if (mobGlowNames == null) {
			mobGlowNames = new java.util.ArrayList<>();
		} else {
			mobGlowNames.clear();
		}
		syncNametagEsp();
	}

	public void normalizeNametagEsp() {
		java.util.List<String> next = new java.util.ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		if (mobGlowNames != null) {
			for (String raw : mobGlowNames) {
				String label = raw == null ? "" : raw.trim();
				if (label.isEmpty()) {
					continue;
				}
				String key = label.toLowerCase(java.util.Locale.ROOT);
				if (seen.add(key)) {
					next.add(label);
				}
			}
		}
		if (next.isEmpty() && mobGlowName != null && !mobGlowName.isBlank()) {
			next.add(mobGlowName.trim());
		}
		mobGlowNames = next;
		syncNametagEsp();
	}

	private void syncNametagEsp() {
		if (mobGlowNames == null) {
			mobGlowNames = new java.util.ArrayList<>();
		}
		mobGlowName = mobGlowNames.isEmpty() ? "" : mobGlowNames.get(0);
	}

	public static StrayConfig get() {
		return instance;
	}

	public static void load() {
		Path path = Files.isRegularFile(PATH) ? PATH : LEGACY_PATH;
		if (!Files.isRegularFile(path)) {
			instance.save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			boolean dropCapeServerUrl = json.remove("capeServerUrl") != null;
			StrayConfig loaded = GSON.fromJson(json, StrayConfig.class);
			if (loaded != null) {
				loaded.scanRadius = clamp(loaded.scanRadius, 16, 80);
				loaded.fillOpacity = clamp(loaded.fillOpacity, 0.08f, 0.85f);
				loaded.worldTintStrength = clamp(loaded.worldTintStrength, 0f, 1f);
				loaded.worldTintMode = normalizeWorldTintMode(loaded.worldTintMode);
				loaded.skyTintStrength = clamp(loaded.skyTintStrength, 0f, 1f);
				loaded.fogStart = clamp(loaded.fogStart, 0f, 0.95f);
				loaded.fogEnd = clamp(loaded.fogEnd, 0.05f, 1f);
				loaded.fogDensity = clamp(loaded.fogDensity, 0f, 1f);
				loaded.aspectRatio = clamp(loaded.aspectRatio, 0.50f, 1.20f);
				boolean legacyTheme = loaded.themePreset == null || loaded.themePreset.isBlank();
				if (legacyTheme) {
					loaded.themeAccentRgb = 0x2FB5FF;
					loaded.themePaneRgb = 0x0B0E14;
					loaded.themePreset = "cyan";
					loaded.uiAnimations = false;
				} else {
					loaded.themeAccentRgb = loaded.themeAccentRgb & 0xFFFFFF;
					if (loaded.themeAccentRgb == 0) {
						loaded.themeAccentRgb = 0x2FB5FF;
					}
					loaded.themePaneRgb = loaded.themePaneRgb & 0xFFFFFF;
					if (loaded.themePaneRgb == 0) {
						loaded.themePaneRgb = 0x0B0E14;
					}
				}
				if (loaded.capeUrl == null) {
					loaded.capeUrl = "";
				}
				if (loaded.capeShopKey == null) {
					loaded.capeShopKey = "";
				}
				if (loaded.nick == null) {
					loaded.nick = "";
				}
				if (loaded.changelogSeen == null) {
					loaded.changelogSeen = "";
				}
				loaded.nametagRange = clamp(loaded.nametagRange <= 0 ? 128 : loaded.nametagRange, 64, 256);
				loaded.nametagScale = clampHudScale(loaded.nametagScale);
				loaded.nametagOpacity = loaded.nametagOpacity <= 0f ? 1.0f : clamp(loaded.nametagOpacity, 0.15f, 1f);
				loaded.nametagStyle = normalizeNametagStyle(loaded.nametagStyle);
				loaded.menuScale = normalizeMenuScale(loaded.menuScale);
				boolean migratedMenuScale = !loaded.menuScaleV2;
				if (migratedMenuScale) {
					if (!json.has("menuScale") || Math.abs(loaded.menuScale - 1.00f) < 0.01f) {
						loaded.menuScale = 0.75f;
					}
					loaded.menuScaleV2 = true;
				}
				loaded.guiDesign = "control";
				if (!json.has("controlPaletteV2")) {
					loaded.controlPaneRgb = 0x181818;
					loaded.controlPillRgb = 0x808080;
					loaded.controlPaneOpacity = 0.64f;
					loaded.controlPillOpacity = 0.17f;
					loaded.controlPaletteV2 = true;
				} else {
					if (loaded.controlPaneRgb == 0) {
						loaded.controlPaneRgb = 0x181818;
					}
					if (loaded.controlPillRgb == 0) {
						loaded.controlPillRgb = 0x808080;
					}
					loaded.controlPaneOpacity = clamp(loaded.controlPaneOpacity, 0.12f, 0.78f);
					loaded.controlPillOpacity = clamp(loaded.controlPillOpacity, 0.12f, 0.78f);
				}
				loaded.controlFrost = json.has("controlFrost")
					? clamp(loaded.controlFrost, 0f, 1f)
					: 0.62f;
				loaded.uiFont = "Minecraft";
				if (!json.has("featureTogglesOffV1")) {
					loaded.boxFill = false;
					loaded.boxOutline = false;
					loaded.throughWalls = false;
					loaded.onlyInTheEnd = false;
					loaded.matchSkyToWorld = false;
					loaded.uiAnimations = false;
					loaded.spotifyEnabled = false;
					loaded.titaniumEspThroughWalls = false;
					loaded.chestEspTracers = false;
					loaded.chestEspThroughWalls = false;
					loaded.loadoutsOpenAnim = false;
					loaded.profileViewerEnabled = false;
					loaded.autoClickerBlockBreaker = false;
					loaded.autoClickerTerminatorOnly = false;
					loaded.autoClickerEnableLeftClick = false;
					loaded.autoClickerEnableRightClick = false;
					loaded.autoExperimentsAutoClose = false;
					loaded.playerFillThroughWalls = false;
					loaded.playerFillMobs = false;
					loaded.inventoryHudHotbar = false;
					loaded.inventoryHudArmor = false;
					loaded.inventoryHudCount = false;
					loaded.hudHotbar = false;
					loaded.hudHealth = false;
					loaded.hudHunger = false;
					loaded.hudArmor = false;
					loaded.hudAir = false;
					loaded.hudExperience = false;
					loaded.hudScoreboard = false;
					loaded.hudBossBar = false;
					loaded.hudEffects = false;
					loaded.hudHeldItem = false;
					loaded.hudMountHealth = false;
					loaded.starMobBats = false;
					loaded.starMobThroughWalls = false;
					loaded.mobGlowThroughWalls = false;
					loaded.featureTogglesOffV1 = true;
				}
				if (!json.has("updateNotify")) {
					loaded.updateNotify = true;
				}
				if (loaded.mobGlowName == null) {
					loaded.mobGlowName = "";
				}
				if (loaded.mobGlowNames == null) {
					loaded.mobGlowNames = new java.util.ArrayList<>();
				}
				loaded.normalizeNametagEsp();
				if (loaded.mobGlowIds == null) {
					loaded.mobGlowIds = new java.util.ArrayList<>();
				}
				if (loaded.mobGlowIds.isEmpty() && loaded.mobGlowId != null && !loaded.mobGlowId.isBlank()) {
					loaded.mobGlowIds.add(loaded.mobGlowId);
				}
				loaded.normalizeMobGlowIds();
				loaded.mobGlowSize = clamp(loaded.mobGlowSize <= 0f ? 0.48f : loaded.mobGlowSize, 0.12f, 1.20f);
				loaded.mobGlowRadius = clamp(loaded.mobGlowRadius <= 0f ? GlowBlurRadius.DEFAULT : loaded.mobGlowRadius, GlowBlurRadius.MIN, GlowBlurRadius.MAX);
				loaded.mobGlowOpacity = clamp(loaded.mobGlowOpacity <= 0f ? 0.58f : loaded.mobGlowOpacity, 0.15f, 0.90f);
				loaded.mobGlowRgb = loaded.mobGlowRgb & 0xFFFFFF;
				if (loaded.mobGlowRgb == 0) {
					loaded.mobGlowRgb = 0x2FB5FF;
				}
				loaded.starMobRadius = clamp(loaded.starMobRadius <= 0f ? GlowBlurRadius.DEFAULT : loaded.starMobRadius, GlowBlurRadius.MIN, GlowBlurRadius.MAX);
				loaded.starMobOpacity = clamp(loaded.starMobOpacity <= 0f ? 0.58f : loaded.starMobOpacity, 0.15f, 0.90f);
				loaded.starMobRgb = loaded.starMobRgb & 0xFFFFFF;
				if (loaded.starMobRgb == 0) {
					loaded.starMobRgb = 0xFFD84A;
				}
				loaded.blockOutlineOpacity = clamp(loaded.blockOutlineOpacity <= 0f ? 0.58f : loaded.blockOutlineOpacity, 0.15f, 0.90f);
				loaded.blockOutlineRgb = loaded.blockOutlineRgb & 0xFFFFFF;
				if (loaded.blockOutlineRgb == 0) {
					loaded.blockOutlineRgb = 0x2FB5FF;
				}
				loaded.titaniumEspRange = clamp(loaded.titaniumEspRange <= 0 ? 48 : loaded.titaniumEspRange, 24, 80);
				loaded.titaniumEspRgb = loaded.titaniumEspRgb & 0xFFFFFF;
				if (loaded.titaniumEspRgb == 0) {
					loaded.titaniumEspRgb = 0xE8ECF2;
				}
				loaded.titaniumEspOpacity = json.has("titaniumEspOpacity")
					? clamp(loaded.titaniumEspOpacity, 0.08f, 0.85f)
					: 0.38f;
				if (!json.has("chestEspEnabled")) {
					loaded.chestEspEnabled = false;
				}
				if (!json.has("chestEspTracers")) {
					loaded.chestEspTracers = false;
				}
				if (!json.has("chestEspThroughWalls")) {
					loaded.chestEspThroughWalls = false;
				}
				loaded.chestAimSpeed = clamp(loaded.chestAimSpeed <= 0f ? 1.00f : loaded.chestAimSpeed, 0.25f, 2.00f);
				loaded.chestEspRgb = loaded.chestEspRgb & 0xFFFFFF;
				if (loaded.chestEspRgb == 0) {
					loaded.chestEspRgb = 0xF4C14E;
				}
				loaded.chestEspOpacity = json.has("chestEspOpacity")
					? clamp(loaded.chestEspOpacity, 0.08f, 0.85f)
					: 0.34f;
				if (loaded.itemSkins == null) {
					loaded.itemSkins = new java.util.ArrayList<>();
				}
				if (loaded.itemClipboardName == null) {
					loaded.itemClipboardName = "";
				}
				if (loaded.itemClipboardItemName == null) {
					loaded.itemClipboardItemName = "";
				}
				if (loaded.itemClipboardLore == null) {
					loaded.itemClipboardLore = "";
				}
				if (loaded.rawmatsItemId == null) {
					loaded.rawmatsItemId = "";
				}
				if (loaded.musicApiToken == null) {
					loaded.musicApiToken = "";
				}
				if (!json.has("musicChatOffDefault")) {
					loaded.musicChatAnnounce = false;
					loaded.musicChatOffDefault = true;
				}
				if (!json.has("spotifyEnabled")) {
					loaded.spotifyEnabled = false;
				}
				if (loaded.spotifyClientId == null) {
					loaded.spotifyClientId = "";
				}
				if (loaded.spotifyRefreshToken == null) {
					loaded.spotifyRefreshToken = "";
				}
				if (loaded.spotifyAccessToken == null) {
					loaded.spotifyAccessToken = "";
				}
				loaded.uiFont = "Minecraft";
				if (!json.has("hitsoundEnabled")) {
					loaded.hitsoundEnabled = false;
				}
				if (!json.has("hitsoundMelee")) {
					loaded.hitsoundMelee = false;
				}
				if (!json.has("hitsoundArrows")) {
					loaded.hitsoundArrows = false;
				}
				if (!json.has("hitmarkerEnabled")) {
					loaded.hitmarkerEnabled = false;
				}
				if (!json.has("triggerbotEnabled")) {
					loaded.triggerbotEnabled = false;
				}
				if (!json.has("triggerbotPlayers")) {
					loaded.triggerbotPlayers = false;
				}
				if (!json.has("autoClickerEnabled")) {
					loaded.autoClickerEnabled = false;
				}
				if (!json.has("autoExperimentsEnabled")) {
					loaded.autoExperimentsEnabled = false;
				}
				loaded.autoClickerCps = json.has("autoClickerCps")
					? clamp(loaded.autoClickerCps, 3.0f, 15.0f)
					: 5.0f;
				loaded.autoClickerLeftCps = json.has("autoClickerLeftCps")
					? clamp(loaded.autoClickerLeftCps, 3.0f, 15.0f)
					: 5.0f;
				loaded.autoClickerRightCps = json.has("autoClickerRightCps")
					? clamp(loaded.autoClickerRightCps, 3.0f, 15.0f)
					: 5.0f;
				if (loaded.autoClickerLeftKey == null || loaded.autoClickerLeftKey.isBlank()) {
					loaded.autoClickerLeftKey = "key.keyboard.unknown";
				}
				if (loaded.autoClickerRightKey == null || loaded.autoClickerRightKey.isBlank()) {
					loaded.autoClickerRightKey = "key.keyboard.unknown";
				}
				loaded.openGuiKey = blankKey(loaded.openGuiKey, "key.keyboard.right.shift");
				loaded.openLoadoutsKey = blankKey(loaded.openLoadoutsKey, "key.keyboard.unknown");
				loaded.openWardrobeKey = blankKey(loaded.openWardrobeKey, "key.keyboard.unknown");
				loaded.openProfileKey = blankKey(loaded.openProfileKey, "key.keyboard.unknown");
				loaded.chestAimKey = blankKey(loaded.chestAimKey, "key.keyboard.unknown");
				if (!json.has("profileViewerEnabled")) {
					loaded.profileViewerEnabled = false;
				}
				if (loaded.autoClickerLeftWhitelist == null) {
					loaded.autoClickerLeftWhitelist = new java.util.ArrayList<>();
				}
				if (loaded.autoClickerRightWhitelist == null) {
					loaded.autoClickerRightWhitelist = new java.util.ArrayList<>();
				}
				loaded.autoExperimentsClickDelay = json.has("autoExperimentsClickDelay")
					? Math.round(clamp(loaded.autoExperimentsClickDelay, 100, 1000))
					: 200;
				loaded.autoExperimentsDelayVariety = json.has("autoExperimentsDelayVariety")
					? Math.round(clamp(loaded.autoExperimentsDelayVariety, 0, 1000))
					: 50;
				loaded.autoExperimentsSerumCount = json.has("autoExperimentsSerumCount")
					? Math.round(clamp(loaded.autoExperimentsSerumCount, 0, 3))
					: 0;
				if (!json.has("heldItemShaderEnabled")) {
					loaded.heldItemShaderEnabled = false;
				}
				if (!json.has("playerFillEsp")) {
					loaded.playerFillEsp = false;
				}
				if (!json.has("playerFillThroughWalls")) {
					loaded.playerFillThroughWalls = false;
				}
				if (!json.has("playerFillMobs")) {
					loaded.playerFillMobs = false;
				}
				if (!json.has("playerFillStarMobs")) {
					loaded.playerFillStarMobs = false;
				}
				loaded.heldItemShaderFill = json.has("heldItemShaderFill")
					? clamp(loaded.heldItemShaderFill, 0.08f, 0.85f)
					: 0.32f;
				loaded.heldItemShaderOutline = json.has("heldItemShaderOutline")
					? clamp(loaded.heldItemShaderOutline, 0.15f, 1.50f)
					: 0.90f;
				loaded.heldItemShaderSmoke = json.has("heldItemShaderSmoke")
					? clamp(loaded.heldItemShaderSmoke, 0.10f, 1.50f)
					: 0.55f;
				loaded.heldItemShaderStyle = normalizeHeldItemShaderStyle(loaded.heldItemShaderStyle);
				loaded.heldItemShaderRgb = loaded.heldItemShaderRgb & 0xFFFFFF;
				if (loaded.heldItemShaderRgb == 0) {
					loaded.heldItemShaderRgb = 0x4FD6EA;
				}
				loaded.heldItemShaderOutlineRgb = loaded.heldItemShaderOutlineRgb & 0xFFFFFF;
				if (!json.has("heldItemShaderOutlineRgb") || loaded.heldItemShaderOutlineRgb == 0) {
					loaded.heldItemShaderOutlineRgb = liftedOutlineRgb(loaded.heldItemShaderRgb);
				}
				loaded.playerFillRgb = loaded.playerFillRgb & 0xFFFFFF;
				if (!json.has("playerFillRgb") || loaded.playerFillRgb == 0) {
					loaded.playerFillRgb = loaded.heldItemShaderRgb == 0 ? 0x4FD6EA : loaded.heldItemShaderRgb & 0xFFFFFF;
				}
				loaded.playerFillOutlineRgb = loaded.playerFillOutlineRgb & 0xFFFFFF;
				if (!json.has("playerFillOutlineRgb") || loaded.playerFillOutlineRgb == 0) {
					loaded.playerFillOutlineRgb = liftedOutlineRgb(loaded.playerFillRgb);
				}
				loaded.playerFillFill = json.has("playerFillFill")
					? clamp(loaded.playerFillFill, 0.08f, 0.85f)
					: loaded.heldItemShaderFill;
				loaded.playerFillOutline = json.has("playerFillOutline")
					? clamp(loaded.playerFillOutline, 0.15f, 1.50f)
					: loaded.heldItemShaderOutline;
				loaded.playerFillSmoke = json.has("playerFillSmoke")
					? clamp(loaded.playerFillSmoke, 0.10f, 1.50f)
					: loaded.heldItemShaderSmoke;
				loaded.playerFillStyle = json.has("playerFillStyle")
					? normalizeHeldItemShaderStyle(loaded.playerFillStyle)
					: loaded.heldItemShaderStyle;
				loaded.triggerbotHumanize = json.has("triggerbotHumanize")
					? clamp(loaded.triggerbotHumanize, 0f, 1f)
					: 0.50f;
				loaded.hitmarkerScale = json.has("hitmarkerScale")
					? clampHudScale(loaded.hitmarkerScale)
					: 1.00f;
				loaded.hitsoundVolume = json.has("hitsoundVolume")
					? clamp(loaded.hitsoundVolume, 0f, 1f)
					: 0.80f;
				loaded.hitsoundPitch = json.has("hitsoundPitch")
					? clamp(loaded.hitsoundPitch, 0.50f, 1.50f)
					: 1.00f;
				loaded.musicApiPort = loaded.musicApiPort < 0 || loaded.musicApiPort > 65535
					? 0
					: loaded.musicApiPort;
				loaded.menuTab = normalizeMenuTab(loaded.menuTab);
				loaded.inventoryHudAnchor = normalizeInventoryHudAnchor(loaded.inventoryHudAnchor);
				loaded.inventoryHudScale = clampHudScale(loaded.inventoryHudScale);
				loaded.hudWatermarkScale = clampHudScale(loaded.hudWatermarkScale);
				loaded.hudNodesScale = clampHudScale(loaded.hudNodesScale);
				loaded.hudMusicScale = clampHudScale(loaded.hudMusicScale);
				loaded.hudRawmatsScale = clampHudScale(loaded.hudRawmatsScale);
				loaded.hudPickupScale = clampHudScale(loaded.hudPickupScale);
				loaded.hudMiningScale = clampHudScale(loaded.hudMiningScale);
				loaded.hudJacobScale = json.has("hudJacobScale")
					? clampHudScale(loaded.hudJacobScale)
					: 1.00f;
				loaded.hudComposterScale = json.has("hudComposterScale")
					? clampHudScale(loaded.hudComposterScale)
					: 1.00f;
				loaded.composterSpeed = clamp(loaded.composterSpeed, 0, 25);
				loaded.composterMultiDrop = clamp(loaded.composterMultiDrop, 0, 25);
				loaded.composterFuelCap = clamp(loaded.composterFuelCap, 0, 25);
				loaded.composterOrganicMatterCap = clamp(loaded.composterOrganicMatterCap, 0, 25);
				loaded.composterCostReduction = clamp(loaded.composterCostReduction, 0, 25);
				loaded.composterMaxOrganic = Math.max(0L, loaded.composterMaxOrganic);
				loaded.composterMaxFuel = Math.max(0L, loaded.composterMaxFuel);
				if (!json.has("farmingYawPitch")) {
					loaded.farmingYawPitch = false;
				}
				loaded.farmingYawPitchScale = json.has("farmingYawPitchScale")
					? clampHudScale(loaded.farmingYawPitchScale)
					: 1.00f;
				loaded.slotHotbar = hudSlot(loaded.slotHotbar);
				loaded.slotHealth = hudSlot(loaded.slotHealth);
				loaded.slotHunger = hudSlot(loaded.slotHunger);
				loaded.slotArmor = hudSlot(loaded.slotArmor);
				loaded.slotAir = hudSlot(loaded.slotAir);
				loaded.slotExperience = hudSlot(loaded.slotExperience);
				loaded.slotMount = hudSlot(loaded.slotMount);
				loaded.slotScoreboard = hudSlot(loaded.slotScoreboard);
				loaded.slotBoss = hudSlot(loaded.slotBoss);
				loaded.slotEffects = hudSlot(loaded.slotEffects);
				loaded.slotHeldItem = hudSlot(loaded.slotHeldItem);
				loaded.themePaneOpacity = loaded.themePaneOpacity <= 0f
					? 0.90f
					: clamp(loaded.themePaneOpacity, 0.20f, 1f);
				loaded.hudOpacity = loaded.hudOpacity <= 0f
					? 0.90f
					: clamp(loaded.hudOpacity, 0.20f, 1f);
				instance = loaded;
				if (dropCapeServerUrl || migratedMenuScale || !path.equals(PATH)) {
					instance.save();
				}
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read stray.json, using defaults", exception);
			instance = new StrayConfig();
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not write stray.json", exception);
		}
	}

	public int fillColor() {
		int alpha = Math.round(fillOpacity * 255.0f);
		return (alpha << 24) | (colorRgb & 0xFFFFFF);
	}

	public int lineColor() {
		return 0xFF000000 | (colorRgb & 0xFFFFFF);
	}

	public boolean worldTintUsesLightmap() {
		return "lightmap".equalsIgnoreCase(worldTintMode);
	}

	public void cycleWorldTintMode() {
		worldTintMode = worldTintUsesLightmap() ? "shader" : "lightmap";
	}

	public String worldTintModeLabel() {
		return worldTintUsesLightmap() ? "Lightmap" : "Shader";
	}

	public void cycleRawmatsMode() {
		rawmatsEnchanted = !rawmatsEnchanted;
	}

	public String rawmatsModeLabel() {
		return rawmatsEnchanted ? "Enchanted" : "Raw";
	}

	public boolean nametagCustomPlates() {
		return nametagsEnabled && nametagCustom();
	}

	public boolean nametagCustom() {
		return !"vanilla".equalsIgnoreCase(nametagStyle);
	}

	public void cycleGuiDesign() {
		guiDesign = "control";
		Theme.refresh();
	}

	public String guiDesignLabel() {
		return "Control";
	}

	public boolean guiDesignControl() {
		return true;
	}

	public static String normalizeGuiDesign(String style) {
		return "control";
	}

	public void cycleNametagStyle() {
		nametagStyle = nametagCustom() ? "vanilla" : "custom";
	}

	public String nametagStyleLabel() {
		return nametagCustom() ? "Stray" : "Vanilla";
	}

	public void cycleHeldItemShaderStyle() {
		heldItemShaderStyle = heldItemShaderStars() ? "smoke" : "stars";
	}

	public String heldItemShaderStyleLabel() {
		return heldItemShaderStars() ? "Stars" : "Smoke";
	}

	public boolean heldItemShaderStars() {
		return "stars".equals(heldItemShaderStyle);
	}

	public float heldItemShaderStyleIndex() {
		return heldItemShaderStars() ? 1f : 0f;
	}

	public void cyclePlayerFillStyle() {
		playerFillStyle = playerFillStars() ? "smoke" : "stars";
	}

	public String playerFillStyleLabel() {
		return playerFillStars() ? "Stars" : "Smoke";
	}

	public boolean playerFillStars() {
		return "stars".equals(playerFillStyle);
	}

	public static String normalizeHeldItemShaderStyle(String style) {
		if (style == null) {
			return "smoke";
		}
		return switch (style.toLowerCase(java.util.Locale.ROOT)) {
			case "stars", "star", "starry", "sky" -> "stars";
			default -> "smoke";
		};
	}

	public static String normalizeNametagStyle(String style) {
		return "vanilla".equalsIgnoreCase(style) ? "vanilla" : "custom";
	}

	public void cycleInventoryHudAnchor() {
		inventoryHudAnchor = switch (inventoryHudAnchor) {
			case "top_left" -> "top_right";
			case "top_right" -> "bottom_right";
			case "bottom_right" -> "bottom_left";
			default -> "top_left";
		};
	}

	public String inventoryHudAnchorLabel() {
		return switch (inventoryHudAnchor) {
			case "top_left" -> "Top left";
			case "top_right" -> "Top right";
			case "bottom_left" -> "Bottom left";
			default -> "Bottom right";
		};
	}

	public static String normalizeInventoryHudAnchor(String anchor) {
		if (anchor == null) {
			return "bottom_right";
		}
		return switch (anchor) {
			case "top_left", "top_right", "bottom_left", "bottom_right" -> anchor;
			default -> "bottom_right";
		};
	}

	public static String normalizeWorldTintMode(String mode) {
		return "lightmap".equalsIgnoreCase(mode) ? "lightmap" : "shader";
	}

	public static String normalizeMenuTab(String tab) {
		if (tab == null || tab.isBlank()) {
			return "WORLD";
		}
		String name = tab.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (name) {
			case "WORLD", "VIEW", "FOG", "CAMERA" -> "WORLD";
			case "COMBAT", "HITSOUND", "TRIGGERBOT", "AUTOCLICKER", "AUTOEXPERIMENTS" -> "COMBAT";
			case "ESP", "MOBS", "VISUALS", "HELDITEM", "SHADER" -> "ESP";
			case "OVERLAY", "DISPLAY", "INVENTORY", "HUD" -> "OVERLAY";
			case "BARS" -> "BARS";
			case "NODES", "MARKERS" -> "NODES";
			case "MINING" -> "MINING";
			case "FARMING", "YAW", "PITCH" -> "FARMING";
			case "MENUS", "LOADOUTS", "WARDROBE", "MISC" -> "MENUS";
			case "STATUS" -> "STATUS";
			case "PLAYER", "NICK", "CAPE" -> "PLAYER";
			case "SETTINGS", "THEME" -> "SETTINGS";
			default -> "WORLD";
		};
	}

	public static float normalizeMenuScale(float value) {
		if (value <= 0f) {
			return 0.75f;
		}
		float[] steps = {1.00f, 0.90f, 0.75f, 0.50f};
		float best = 0.75f;
		float err = Float.MAX_VALUE;
		for (float step : steps) {
			float d = Math.abs(value - step);
			if (d < err) {
				err = d;
				best = step;
			}
		}
		return best;
	}

	public static int liftedOutlineRgb(int fillRgb) {
		int rgb = fillRgb & 0xFFFFFF;
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = Math.min(255, r + Math.round((255 - r) * 0.62f));
		g = Math.min(255, g + Math.round((255 - g) * 0.62f));
		b = Math.min(255, b + Math.round((255 - b) * 0.62f));
		return (r << 16) | (g << 8) | b;
	}

	public static float clampHudScale(float value) {
		if (value <= 0f) {
			return 1.0f;
		}
		return clamp(value, 0.50f, 2.00f);
	}

	public static HudSlot hudSlot(HudSlot slot) {
		if (slot == null) {
			return new HudSlot();
		}
		slot.scale = clampHudScale(slot.scale);
		return slot;
	}

	public void resetHudSlots() {
		slotHotbar = new HudSlot();
		slotHealth = new HudSlot();
		slotHunger = new HudSlot();
		slotArmor = new HudSlot();
		slotAir = new HudSlot();
		slotExperience = new HudSlot();
		slotMount = new HudSlot();
		slotScoreboard = new HudSlot();
		slotBoss = new HudSlot();
		slotEffects = new HudSlot();
		slotHeldItem = new HudSlot();
	}

	public static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String blankKey(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	public static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	public static final class HudSlot {
		public float x = -1f;
		public float y = -1f;
		public float scale = 1f;
	}

	public static final class ItemSkin {
		public String key = "";
		public String displayId = "";
		public String originalId = "";
		public int slot = 0;
		public boolean offhand = false;
		public String nameJson = "";
		public String itemNameJson = "";
		public String loreJson = "";
		public boolean maxed = false;
	}
}
