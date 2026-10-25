package dev.stray.client.config;

import java.util.HashMap;
import java.util.Map;

/** Every slayer option (ported from SkyHanni's slayer features) and the tracked slayer data. */
public final class SlayerSettings {
	// ---- Slayer HUD: quest and own boss
	public boolean hud = false;
	public boolean hudProgress = true;
	public boolean hudBoss = true;
	public boolean hudPhase = true;
	public boolean hudTimer = true;
	public boolean hudAreaOnly = false;

	// ---- profit tracker
	public boolean profit = false;
	public boolean profitSession = false;
	public int profitMinPrice = 0;
	public int profitRows = 8;

	// ---- RNG meter
	public boolean rng = false;
	public boolean rngHideChat = false;
	public boolean rngWarnEmpty = false;

	// ---- remaining kills
	public boolean kills = false;
	public boolean killsLevel = true;
	public boolean killsHealth = true;
	public boolean killsXp = false;
	public boolean killsOverkill = true;

	// ---- alerts
	public boolean alerts = false;
	public boolean spawnSoon = true;
	public int spawnSoonPercent = 80;
	public boolean spawnSoonRepeat = false;
	public boolean questWarning = true;
	public boolean questWarningTitle = true;
	public boolean cocoonTitle = true;
	public boolean cocoonSound = true;
	public boolean gummyWarning = false;
	public boolean timeToKill = true;
	public boolean timePb = true;
	public boolean questTime = false;
	public boolean timeCompact = false;

	// ---- ESP: mini-bosses, lines, drops, Tarantula
	public boolean esp = false;
	public boolean miniHighlight = true;
	public int miniRgb = 0x55FFFF;
	public boolean miniLine = false;
	public int lineWidth = 3;
	public boolean bossGlow = true;
	public int bossRgb = 0xFF5555;
	public boolean bossLine = true;
	public int bossLineRgb = 0x55FFFF;
	public boolean itemsOnGround = true;
	public int itemsMinPrice = 50_000;
	public boolean eggSacs = true;
	public int eggSacRgb = 0xFF5555;
	public boolean invincible = true;
	public int invincibleRgb = 0xAAAAAA;

	// ---- Voidgloom Seraph
	public boolean voidgloom = false;
	public boolean beaconHighlight = true;
	public boolean beaconWarning = true;
	public boolean beaconLine = true;
	public int beaconRgb = 0xAA0000;
	public boolean skulls = true;
	public boolean skullLine = false;
	public boolean voidHideParticles = false;

	// ---- Inferno Demonlord
	public boolean inferno = false;
	public boolean shieldColors = true;
	public boolean shieldFill = true;
	public float shieldFillOpacity = 0.35f;
	public boolean shieldText = true;
	public boolean daggers = true;
	public int firstDagger = 0;
	public boolean markShield = true;
	public boolean hideDaggerChat = true;
	public boolean firePits = true;
	public boolean pillar = true;
	public boolean clearView = false;

	// ---- Riftstalker Bloodfiend
	public boolean bloodfiend = false;
	public boolean ownBoss = true;
	public int ownRgb = 0xAA00AA;
	public boolean ownSteakAlert = true;
	public boolean ownTwinclaws = true;
	public boolean othersBoss = true;
	public int othersRgb = 0xFFAA00;
	public boolean coopBoss = false;
	public int coopRgb = 0x55FF55;
	public String coopMembers = "";
	public boolean steakColor = true;
	public int steakRgb = 0xFF5555;
	public int twinclawsDelay = 0;
	public boolean bloodLine = true;
	public int bloodLineRgb = 0xFF55FF;
	public boolean ichor = true;
	public int ichorRgb = 0xAA0000;
	public boolean ichorLines = true;
	public boolean spring = true;
	public int springRgb = 0xFF55FF;
	public boolean springLines = true;

	// ---- cleanup
	public boolean cleanup = false;
	public boolean hideMobNames = false;
	public boolean hideSpawnWhite = false;
	public boolean hideSpawnPurple = false;
	public boolean hideSpawnSpecific = false;
	public boolean hideIrrelevant = false;
	public boolean hideDamageNear = false;
	public boolean blockNotSpawnable = true;

	// ---- tracked data
	/** Profit per quest category ("Voidgloom Seraph IV"), across sessions. */
	public Map<String, Profit> profitData = new HashMap<>();
	/** RNG meter per slayer ("Voidgloom Seraph"). */
	public Map<String, Rng> rngData = new HashMap<>();
	/** Fastest kill per boss ("VOID 4"), in milliseconds. */
	public Map<String, Long> personalBests = new HashMap<>();

	public static final class Profit {
		public Map<String, Long> items = new HashMap<>();
		public Map<String, Long> drops = new HashMap<>();
		public long mobCoins;
		public long spawnCost;
		public long bosses;
		public long uptimeMs;
	}

	public static final class Rng {
		public long meter = -1;
		public long gainPerBoss = -1;
		public String item = "";
		public String itemName = "";
		public long needed = -1;
	}

	/** Colour fields by key, for the colour picker. */
	public int color(String key) {
		return switch (key) {
			case "mini" -> miniRgb;
			case "bossLine" -> bossLineRgb;
			case "boss" -> bossRgb;
			case "eggSac" -> eggSacRgb;
			case "invincible" -> invincibleRgb;
			case "beacon" -> beaconRgb;
			case "own" -> ownRgb;
			case "others" -> othersRgb;
			case "coop" -> coopRgb;
			case "steak" -> steakRgb;
			case "bloodLine" -> bloodLineRgb;
			case "ichor" -> ichorRgb;
			case "spring" -> springRgb;
			default -> 0xFFFFFF;
		};
	}

	public void setColor(String key, int rgb) {
		int c = rgb & 0xFFFFFF;
		switch (key) {
			case "mini" -> miniRgb = c;
			case "bossLine" -> bossLineRgb = c;
			case "boss" -> bossRgb = c;
			case "eggSac" -> eggSacRgb = c;
			case "invincible" -> invincibleRgb = c;
			case "beacon" -> beaconRgb = c;
			case "own" -> ownRgb = c;
			case "others" -> othersRgb = c;
			case "coop" -> coopRgb = c;
			case "steak" -> steakRgb = c;
			case "bloodLine" -> bloodLineRgb = c;
			case "ichor" -> ichorRgb = c;
			case "spring" -> springRgb = c;
			default -> {
			}
		}
	}
}
