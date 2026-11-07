package dev.stray.client.dungeon.m4;

/**
 * Settings for the M4 (Thorn) features ported from BirdAddon. Saved inside the Stray config
 * as {@code "m4"}. Module toggles are off by default like everything else in Stray; their
 * options keep BirdAddon's defaults. The text templates have no field in the menu and can be
 * edited in the config file ({@code $bear}, {@code $timer}, ... as in BirdAddon).
 */
public final class M4Config {
	// Box style used by highlights and waypoints: 0 outline, 1 filled, 2 filled + outline.
	public static final String[] STYLES = {"Outline", "Filled", "Filled + outline"};

	// Spirit Bear
	public boolean spiritBear = false;
	public boolean bearSplits = true;
	public boolean bearExperimental = false;
	public boolean bearExperimentalRender = false;
	public boolean bearSmoothLine = false;
	public boolean bearShowTicks = false;
	public int bearDecimals = 2;
	public String bearKillPhaseText = "§6Bear $bear: §a$kills/$cap";
	public String bearSpawningText = "§6Bear $bear: §c$timer";
	public String bearSpawnedText = "§6Bear $bear: §4!";
	public String bearSplitText = "§6Bear $bear: §b$spawn Kill: $kill";
	public String bearSplitTotalText = "§7Total: §b$total";

	// Highlight
	public boolean highlight = false;
	public int highlightStyle = 0;
	public boolean highlightHideNametags = true;
	public boolean highlightHideMobs = false;
	public boolean highlightThorn = true;
	public boolean highlightTransThorn = true;
	public boolean highlightThornFlash = true;
	public int highlightFlashTicks = 10;
	public boolean highlightBear = true;
	public boolean highlightBearOnlyMage = false;
	public boolean highlightBearNoInterpolation = true;
	public boolean highlightWolves = true;
	public boolean highlightBats = true;
	public boolean highlightSheep = true;
	public boolean highlightCows = true;
	public boolean highlightChickens = true;
	public boolean highlightRabbits = true;

	// Waypoints
	public boolean waypoints = false;
	public int waypointStyle = 0;
	public boolean waypointDepth = true;
	public boolean waypointsOnCgm4 = true;
	public boolean waypointsOnM4Miku = true;
	public boolean bearSpawnWaypoint = true;
	public boolean bearSpawnOnlyMage = true;
	public boolean bearSpawnOnlyWhenSpawning = false;
	public boolean bearSpawnColors = true;
	public boolean bowPickupWaypoint = true;
	public boolean bowPickupOnlyTank = true;

	// Timer
	public boolean timer = false;

	// Stun
	public boolean stun = false;
	public boolean stunHud = false;
	public boolean stunOnlyHealer = true;
	public int stunDecimals = 2;
	public boolean stunHelper = true;
	public int stunSecondOffset = 5;
	public String stunnedText = "§5Stunned for $timer";
	public String notStunnedText = "§5Stunned for §cN/A";

	// Overkill
	public boolean overkill = false;
	public boolean overkillHideZero = false;
	public boolean overkillPrefixes = true;
	public boolean overkillCompact = false;

	// Titles
	public boolean titles = false;
	public boolean titlesHideDefault = true;
	public int titleDurationTicks = 20;
	public boolean titleMiss = true;
	public boolean titlePickup = true;
	public boolean titleTankPickup = true;
	public boolean titleDisintegrate = true;
	public boolean titleBearTimer = true;
	public boolean titleBearSpawned = true;
	public boolean titleBearKilled = true;
	public String missText = "§cBow Missed";
	public String pickupText = "§cBow Picked Up";
	public String tankPickupText = "§aBow Picked Up";
	public String disintegrateText = "§0SHOOT §4THE §5BOW";
	public String bearTimerText = "§cSTOP KILLING";
	public String bearSpawnedTitleText = "§5Bear Spawned";
	public String bearKilledText = "§aResume Killing";

	// Mob counter
	public boolean mobCounter = false;
	public boolean mobTotal = true;
	public boolean mobCows = true;
	public boolean mobSheep = true;
	public boolean mobChickens = true;
	public boolean mobBats = true;
	public boolean mobWolves = true;
	public boolean mobRabbits = true;
	public boolean mobUnderThorn = true;
	public boolean mobRabbitsInMid = true;
	public boolean mobPrefixes = true;
	public boolean mobCompact = false;

	// Decoy
	public boolean decoy = false;
	public boolean decoyBestSpot = true;
	public boolean decoyHideAfterB1 = true;
	public boolean decoyCave = true;
	public int decoyStyle = 0;
	public boolean decoyDepth = true;
	public boolean decoyOnlyBers = true;

	// Tac
	public boolean tac = false;
	public boolean tacTimer = true;
	public boolean tacOutsideM4 = false;
	public String tacPrefix = "§6Tac: ";
	public boolean tacPrint = true;
	public boolean tacPrintOnlyTank = true;
	public boolean tacPrintParty = true;

	// Hide messages
	public boolean hideMessages = false;
	public boolean hideCrowd = true;
	public boolean hideThorn = true;
	public boolean hideSheepExplosion = true;
	public boolean hideChickenMines = true;
	public boolean hideChickenLightning = true;
	public boolean hideBearSpawn = true;
	public boolean hideBowDrop = true;
	public boolean hideBowPickup = true;
	public boolean hideBowShot = true;

	// Extra stats
	public boolean extraStats = false;
	public boolean extraStatsMenu = true;
	public boolean extraStatsRunTime = true;
	public boolean extraStatsLastBear = true;

	// Sounds
	public boolean sounds = false;
	public boolean muteAnimals = true;
	public boolean muteThorn = true;
	public boolean muteMisc = true;

	// Render optimizer
	public boolean renderOptimizer = false;
	public boolean hideParticles = true;
	public boolean keepHealerCircle = true;
	public boolean keepMageBeam = true;
	/** 0 all, 1 relevant only, 2 none. */
	public int npcVisibility = 1;
	/** 0 none, 1 relevant only, 2 all. */
	public int npcHighlight = 0;
	public int npcStyle = 0;
	public boolean hideInvisArmorStands = true;

	// Trajectories
	public boolean trajectories = false;
	public int trajectoryStyle = 0;
	public boolean trajectoryCows = true;
	public boolean trajectorySheep = true;
	public boolean trajectoryChickens = true;

	// Rabbits
	public boolean rabbits = false;
	public boolean rabbitsInHutch = true;
	public boolean rabbitsHideEmptyHutch = true;
	public boolean rabbitCountdown = true;
	public int rabbitDecimals = 2;
	public boolean rabbitFromStart = false;
	public boolean rabbitAfterB1 = true;
	public int rabbitSecondsBefore = 4;
	public boolean rabbitHideZero = true;
	public String rabbitText = "Rabbits spawning in";
	public boolean rabbitGroundMobs = true;
	public boolean rabbitBats = true;
}
