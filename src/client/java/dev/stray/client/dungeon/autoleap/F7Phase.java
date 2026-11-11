package dev.stray.client.dungeon.autoleap;

import dev.stray.client.dungeon.DevMode;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the F7 boss fight is: the phase from the bosses' lines (and the M7 boss bar), the
 * phase-3 section from the task messages and gates, and which phase or section area the
 * player is standing in. Ported from quoi's Floor7 / Stage (jcnlk/quoi, used with
 * permission); the areas and messages are the same.
 */
public final class F7Phase {
	public enum Phase { UNKNOWN, P1, P2, P3, P4, P5 }

	/** Section done: {@code full} once its gate is down too (or the Core opens for section 4). */
	public interface StageListener {
		void complete(int stage, boolean full);
	}

	private static final Pattern TASK = Pattern.compile("^(.{1,16}) (?:activated|completed) a (?:terminal|lever|device)! \\((\\d)/(\\d)\\)$");
	private static final List<StageListener> LISTENERS = new ArrayList<>();

	private static Phase phase = Phase.UNKNOWN;
	/** 1-4 the P3 sections, 5 the Core; 0 unknown. */
	private static int stage;
	private static int current;
	private static int total;
	private static boolean gate;
	private static boolean objectivesFired;
	private static ClientLevel lastLevel;

	private F7Phase() {
	}

	public static void listen(StageListener listener) {
		LISTENERS.add(listener);
	}

	public static boolean inF7Boss() {
		return DevMode.on() || SkyblockLocation.inDungeon && SkyblockLocation.inBoss && SkyblockLocation.dungeonFloor == 7;
	}

	public static Phase phase() {
		return inF7Boss() ? phase : Phase.UNKNOWN;
	}

	public static int stage() {
		return inF7Boss() ? stage : 0;
	}

	public static boolean inPhase(Phase... phases) {
		Phase now = phase();
		for (Phase p : phases) {
			if (p == now) {
				return true;
			}
		}
		return false;
	}

	/** The phase area by height, like quoi. */
	public static Phase phaseAt(Player player) {
		if (!inF7Boss() || player == null) {
			return Phase.UNKNOWN;
		}
		double y = player.getY();
		if (y > 210) {
			return Phase.P1;
		}
		if (y > 155) {
			return Phase.P2;
		}
		if (y > 100) {
			return Phase.P3;
		}
		if (y > 45) {
			return Phase.P4;
		}
		return Phase.P5;
	}

	public static boolean inPhaseAt(Phase target) {
		return phaseAt(Minecraft.getInstance().player) == target;
	}

	/** The P3 section you stand in (1-4, 5 the Core area), 0 elsewhere. */
	public static int stageAt(Player player) {
		if (player == null || phaseAt(player) != Phase.P3) {
			return 0;
		}
		double x = player.getX();
		double z = player.getZ();
		if (x >= 89 && x <= 113 && z >= 30 && z <= 122) {
			return 1;
		}
		if (x >= 19 && x <= 111 && z >= 121 && z <= 145) {
			return 2;
		}
		if (x >= -6 && x <= 19 && z >= 51 && z <= 143) {
			return 3;
		}
		if (x >= -2 && x <= 90 && z >= 27 && z <= 51) {
			return 4;
		}
		if (x >= 41 && x <= 68 && z >= 59 && z <= 117) {
			return 5;
		}
		return 0;
	}

	public static void tick(Minecraft client) {
		if (client.level != lastLevel) {
			lastLevel = client.level;
			reset();
		}
	}

	private static void reset() {
		phase = Phase.UNKNOWN;
		stage = 0;
		resetStage();
	}

	private static void resetStage() {
		current = 0;
		total = 0;
		gate = false;
		objectivesFired = false;
	}

	public static void onChat(String plain) {
		if (!inF7Boss() || plain == null) {
			return;
		}
		switch (plain) {
			case "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> {
				phase = Phase.P1;
				stage = 0;
				resetStage();
				return;
			}
			case "[BOSS] Storm: Pathetic Maxor, just like expected." -> {
				phase = Phase.P2;
				return;
			}
			case "[BOSS] Goldor: Who dares trespass into my domain?" -> {
				phase = Phase.P3;
				stage = 1;
				resetStage();
				return;
			}
			case "[BOSS] Necron: Finally, I heard so much about you. The Eye likes you very much.",
				"[BOSS] Necron: You went further than any human before, congratulations." -> {
				phase = Phase.P4;
				stage = 0;
				resetStage();
				return;
			}
			case "The Core entrance is opening!" -> {
				int old = stage;
				stage = 5;
				resetStage();
				if (old >= 1 && old <= 4) {
					fire(old, true);
				}
				return;
			}
			default -> {
			}
		}
		if (phase != Phase.P3 || stage < 1 || stage > 4) {
			return;
		}
		Matcher m = TASK.matcher(plain);
		if (m.matches()) {
			current = Integer.parseInt(m.group(2));
			total = Integer.parseInt(m.group(3));
			if (total > 0 && current == total && !objectivesFired) {
				objectivesFired = true;
				fire(stage, false);
			}
		}
		if ("The gate has been destroyed!".equals(plain)) {
			gate = true;
		}
		boolean done = total > 0 && current == total;
		if (done && (gate || stage == 4) && stage < 4) {
			int old = stage;
			stage++;
			resetStage();
			fire(old, true);
		}
	}

	/** M7: the Wither King's boss bar starts phase 5. */
	public static void onBossBar(String name) {
		if (inF7Boss() && phase == Phase.P4 && name != null && name.toLowerCase(java.util.Locale.ROOT).contains("wither king")) {
			phase = Phase.P5;
		}
	}

	private static void fire(int finished, boolean full) {
		for (StageListener listener : LISTENERS) {
			listener.complete(finished, full);
		}
	}
}
