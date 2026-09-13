package dev.stray.client.skill;

/**
 * XP required to reach each level, from
 * https://hypixel-skyblock.fandom.com/wiki/Skills#Leveling
 * Index is the level being reached (1..max). Index 0 is unused.
 */
public final class SkillXp {
	private SkillXp() {
	}

	/** Combat, Mining, Farming, Foraging, Fishing, Enchanting, Alchemy, Taming, Carpentry. */
	public static final long[] MAIN = {
		0,
		50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500,
		5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000, 75_000, 100_000, 200_000,
		300_000, 400_000, 500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000, 1_100_000, 1_200_000,
		1_300_000, 1_400_000, 1_500_000, 1_600_000, 1_700_000, 1_800_000, 1_900_000, 2_000_000, 2_100_000, 2_200_000,
		2_300_000, 2_400_000, 2_500_000, 2_600_000, 2_750_000, 2_900_000, 3_100_000, 3_400_000, 3_700_000, 4_000_000,
		4_300_000, 4_600_000, 4_900_000, 5_200_000, 5_500_000, 5_800_000, 6_100_000, 6_400_000, 6_700_000, 7_000_000
	};

	public static final long[] RUNECRAFTING = {
		0,
		50, 100, 125, 160, 200, 250, 315, 400, 500, 625,
		785, 1_000, 1_250, 1_600, 2_000, 2_465, 3_125, 4_000, 5_000, 6_200,
		7_800, 9_800, 12_200, 15_300, 19_050
	};

	public static final long[] SOCIAL = {
		0,
		50, 100, 150, 250, 500, 750, 1_000, 1_250, 1_500, 2_000,
		2_500, 3_000, 3_750, 4_500, 6_000, 8_000, 10_000, 12_500, 15_000, 20_000,
		25_000, 30_000, 35_000, 40_000, 50_000
	};

	public static final long[] DUNGEONEERING = {
		0,
		50, 75, 110, 160, 230, 330, 470, 670, 950, 1_340,
		1_890, 2_665, 3_760, 5_260, 7_380, 10_300, 14_400, 20_000, 27_600, 38_000,
		52_500, 71_500, 97_000, 132_000, 180_000, 243_000, 328_000, 445_000, 600_000, 800_000,
		1_065_000, 1_410_000, 1_900_000, 2_500_000, 3_300_000, 4_300_000, 5_600_000, 7_200_000, 9_200_000, 12_000_000,
		15_000_000, 19_000_000, 24_000_000, 30_000_000, 38_000_000, 48_000_000, 60_000_000, 75_000_000, 93_000_000, 116_250_000
	};

	public static int nextLevel(SkillKind kind, long needed) {
		if (kind == null || needed <= 0L) {
			return -1;
		}
		for (int level = 1; level < kind.toReach.length; level++) {
			if (kind.toReach[level] == needed) {
				return level;
			}
		}
		return -1;
	}

	public static long neededFor(SkillKind kind, int nextLevel) {
		if (kind == null || nextLevel <= 0 || nextLevel >= kind.toReach.length) {
			return -1L;
		}
		return kind.toReach[nextLevel];
	}
}
