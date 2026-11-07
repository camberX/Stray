package dev.stray.client.dungeon.m4;

import dev.stray.client.dungeon.DungeonParty.DungeonClass;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudChrome;
import dev.stray.client.render.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.wolf.Wolf;

import java.util.ArrayList;
import java.util.List;

/** BirdAddon's M4 HUD texts, each its own element in the HUD editor. */
public final class M4Hud {
	private static final int LINE = 9;
	private static final int GAP = 4;
	private static final int WHITE = 0xFFFFFFFF;

	public static final HudLayout.Id[] IDS = {
		HudLayout.Id.M4_TIMER, HudLayout.Id.M4_BEAR, HudLayout.Id.M4_SPLITS, HudLayout.Id.M4_STUN,
		HudLayout.Id.M4_TAC, HudLayout.Id.M4_RABBITS, HudLayout.Id.M4_OVERKILL, HudLayout.Id.M4_MOBS
	};

	private record Seg(String text, int color) {
	}

	private M4Hud() {
	}

	public static boolean enabled(HudLayout.Id id) {
		M4Config c = M4.config();
		return switch (id) {
			case M4_BEAR -> c.spiritBear;
			case M4_SPLITS -> c.spiritBear && c.bearSplits;
			case M4_TIMER -> c.timer;
			case M4_STUN -> c.stun && c.stunHud;
			case M4_OVERKILL -> c.overkill;
			case M4_MOBS -> c.mobCounter;
			case M4_RABBITS -> c.rabbits && c.rabbitCountdown;
			case M4_TAC -> c.tac && c.tacTimer;
			default -> false;
		};
	}

	// ---------------------------------------------------------------- layout

	public static float width(HudLayout.Id id, Font font) {
		int max = 0;
		for (List<Seg> row : rows(id, HudLayout.editorOpen())) {
			max = Math.max(max, rowWidth(row, font));
		}
		return max;
	}

	public static float height(HudLayout.Id id) {
		return rows(id, HudLayout.editorOpen()).size() * LINE;
	}

	/** Default spots: the timers stacked on the left, the counters on the right. */
	public static float defaultX(HudLayout.Id id, int guiW, float w) {
		return id == HudLayout.Id.M4_OVERKILL || id == HudLayout.Id.M4_MOBS ? guiW - w - HudLayout.MARGIN : HudLayout.MARGIN;
	}

	public static float defaultY(HudLayout.Id id, int guiH) {
		float top = guiH * 0.18f;
		return switch (id) {
			case M4_TIMER -> top;
			case M4_BEAR -> top + 11;
			case M4_SPLITS -> top + 22;
			case M4_STUN -> top + 80;
			case M4_TAC -> top + 91;
			case M4_RABBITS -> top + 102;
			case M4_OVERKILL -> guiH * 0.3f;
			case M4_MOBS -> guiH * 0.3f + 70;
			default -> top;
		};
	}

	private static int rowWidth(List<Seg> row, Font font) {
		int width = 0;
		for (int i = 0; i < row.size(); i++) {
			width += font.width(row.get(i).text) + (i > 0 ? GAP : 0);
		}
		return width;
	}

	// ---------------------------------------------------------------- drawing

	public static void extract(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden()) {
			return;
		}
		boolean example = HudLayout.editorOpen();
		Font font = client.font;
		for (HudLayout.Id id : IDS) {
			if (!enabled(id)) {
				continue;
			}
			List<List<Seg>> rows = rows(id, example);
			if (rows.isEmpty()) {
				continue;
			}
			HudLayout.apply(graphics, font, id, () -> {
				int y = 0;
				for (List<Seg> row : rows) {
					int x = 0;
					for (Seg seg : row) {
						GuiDraw.text(graphics, font, Component.literal(seg.text), x, y, seg.color, true);
						x += font.width(seg.text) + GAP;
					}
					y += LINE;
				}
			});
			HudChrome.element(null);
		}
	}

	private static List<List<Seg>> rows(HudLayout.Id id, boolean example) {
		if (!example && !M4.inBoss() && !(id == HudLayout.Id.M4_TAC && M4.config().tacOutsideM4)) {
			return List.of();
		}
		return switch (id) {
			case M4_BEAR -> single(bearText(example));
			case M4_SPLITS -> lines(splitLines(example));
			case M4_TIMER -> single(example ? "§c48.2" : "§c" + M4Stats.fixed(M4.timer / 20.0, 2) + "s");
			case M4_STUN -> single(stunText(example));
			case M4_OVERKILL -> overkill(example);
			case M4_MOBS -> mobCounter(example);
			case M4_RABBITS -> lines(rabbitLines(example));
			case M4_TAC -> single(tacText(example));
			default -> List.of();
		};
	}

	private static List<List<Seg>> single(String text) {
		return text == null || text.isEmpty() ? List.of() : List.of(List.of(new Seg(text, WHITE)));
	}

	private static List<List<Seg>> lines(List<String> lines) {
		List<List<Seg>> out = new ArrayList<>();
		for (String line : lines) {
			out.add(List.of(new Seg(line, WHITE)));
		}
		return out;
	}

	// ---------------------------------------------------------------- spirit bear

	private static String bearText(boolean example) {
		M4Config c = M4.config();
		if (example) {
			return bearTemplate(c.bearKillPhaseText, 1, "§c1.55s", 0, 30);
		}
		String template = M4.spawning() ? c.bearSpawningText : M4.spawned() ? c.bearSpawnedText : c.bearKillPhaseText;
		String timer = "n/a";
		if (M4.spawning()) {
			timer = c.bearShowTicks
				? String.format("%02d", M4.bearTimer)
				: M4Stats.fixed(M4.bearTimer / 20.0, c.bearDecimals) + "s";
		}
		return bearTemplate(template, M4.killTimes.size() + 1, timer, M4.kills, M4.maxKills());
	}

	private static String bearTemplate(String template, int bear, String timer, int kills, int cap) {
		return template
			.replace("$bear", Integer.toString(bear))
			.replace("$timer", timer)
			.replace("$kills", String.format("%02d", kills))
			.replace("$cap", Integer.toString(cap));
	}

	private static List<String> splitLines(boolean example) {
		M4Config c = M4.config();
		if (example) {
			return List.of(
				splitTemplate(c.bearSplitText, 1, "12.35s", "15.40s", "15.40s", killTime(3), "15.40s"),
				splitTemplate(c.bearSplitTotalText, 1, "12.35s", "15.40s", "15.40s", killTime(3), "15.40s")
			);
		}
		List<String> lines = new ArrayList<>();
		List<M4Stats.Split> splits = new ArrayList<>(M4Stats.completedSplits);
		splits.sort((a, b) -> Integer.compare(a.bear(), b.bear()));
		for (M4Stats.Split split : splits) {
			lines.add(splitTemplate(c.bearSplitText, split.bear(), seconds(split.spawnStart()), seconds(split.spawn()),
				seconds(split.death()), killTime(split.kill()), seconds(split.death())));
		}
		int current = splits.size() + 1;
		if (!M4.ended && current <= 4) {
			Integer spawnStart = current - 1 < M4.spawnStartTimes.size() ? M4.spawnStartTimes.get(current - 1) : null;
			Integer spawn = current - 1 < M4.spawnTimes.size() ? M4.spawnTimes.get(current - 1) : null;
			String kill = spawn != null ? killTime(M4.timer - spawn) : "§a+0.0s";
			lines.add(splitTemplate(c.bearSplitText, current, seconds(spawnStart == null ? M4.timer : spawnStart),
				seconds(spawn == null ? M4.timer : spawn), seconds(M4.timer), kill, seconds(M4.timer)));
		}
		int total = M4.ended && !M4.killTimes.isEmpty() ? M4.killTimes.getLast() : M4.timer;
		lines.add(splitTemplate(c.bearSplitTotalText, Math.min(current, 4), "", "", "", "", seconds(total)));
		return lines;
	}

	private static String splitTemplate(String template, int bear, String spawnStart, String spawn, String death, String kill, String total) {
		return template
			.replace("$bear", Integer.toString(bear))
			.replace("$spawnStart", spawnStart)
			.replace("$spawn", spawn)
			.replace("$death", death)
			.replace("$total", total)
			.replace("+$kill", kill)
			.replace("$kill", kill);
	}

	private static String seconds(int ticks) {
		return M4Stats.fixed(ticks / 20.0, M4.config().bearDecimals) + "s";
	}

	private static String killTime(int ticks) {
		String color = ticks > 7 ? "§c" : ticks > 2 ? "§e" : "§a";
		return color + "+" + seconds(ticks);
	}

	// ---------------------------------------------------------------- stun

	private static String stunText(boolean example) {
		M4Config c = M4.config();
		if (example) {
			return stunTemplate(c.notStunnedText);
		}
		if (!M4Features.stunShown()) {
			return "";
		}
		return stunTemplate(M4Features.stunTimer >= 0 ? c.stunnedText : c.notStunnedText);
	}

	private static String stunTemplate(String template) {
		return template.replace("$timer", M4Stats.fixed(M4Features.stunTimer / 20.0, M4.config().stunDecimals) + "s");
	}

	// ---------------------------------------------------------------- overkill

	private static List<List<Seg>> overkill(boolean example) {
		M4Config c = M4.config();
		List<Seg> counters = new ArrayList<>();
		int total = 0;
		for (int amount : M4.overkill.values()) {
			total += amount;
		}
		counters.add(counter("Overkill", "O", WHITE, total, c.overkillCompact, c.overkillPrefixes));
		for (Class<? extends Entity> type : M4.ANIMALS) {
			int amount = M4.overkill.getOrDefault(type, 0);
			if (c.overkillHideZero && amount == 0 && !example) {
				continue;
			}
			counters.add(counter(M4.mobName(type, false), M4.mobName(type, true), M4.mobColor(type), amount, c.overkillCompact, c.overkillPrefixes));
		}
		if (c.overkillCompact) {
			return List.of(counters);
		}
		List<List<Seg>> rows = new ArrayList<>();
		for (Seg seg : counters) {
			rows.add(List.of(seg));
		}
		return rows;
	}

	private static Seg counter(String name, String shortName, int color, int amount, boolean compact, boolean prefixes) {
		String text = compact ? shortName + ": " + amount : prefixes ? name + ": " + amount : Integer.toString(amount);
		return new Seg(text, color);
	}

	// ---------------------------------------------------------------- mob counter

	private static boolean underThorn(Entity entity) {
		double x = entity.getX();
		double z = entity.getZ();
		return entity.getY() < 75 && (x > 21 && z > 21 || x > 26 && z > 16 || x > 16 && z > 26);
	}

	private static List<List<Seg>> mobCounter(boolean example) {
		M4Config c = M4.config();
		List<Entity> all = M4.allAnimals();
		int underThorn = 0;
		for (Entity entity : all) {
			if (underThorn(entity)) {
				underThorn++;
			}
		}
		int rabbitsInMid = 0;
		for (Entity rabbit : M4.animalsOf(Rabbit.class)) {
			if (!underThorn(rabbit)) {
				rabbitsInMid++;
			}
		}
		List<Seg> inline = new ArrayList<>();
		List<Seg> separate = new ArrayList<>();
		boolean compact = c.mobCompact;
		if (c.mobTotal) {
			inline.add(counter("Mobs", "M", WHITE, example ? 0 : all.size(), compact, c.mobPrefixes));
		}
		boolean[] shown = {c.mobCows, c.mobSheep, c.mobChickens, c.mobBats, c.mobWolves, c.mobRabbits};
		for (int i = 0; i < M4.ANIMALS.size(); i++) {
			if (shown[i]) {
				Class<? extends Entity> type = M4.ANIMALS.get(i);
				inline.add(counter(M4.mobName(type, false), M4.mobName(type, true), M4.mobColor(type), example ? 0 : M4.animalsOf(type).size(), compact, c.mobPrefixes));
			}
		}
		// Under Thorn and Rabbits in Mid get their own lines even in compact mode.
		if (c.mobUnderThorn) {
			separate.add(counter("Under Thorn", "UT", M4.THORN_COLOR, example ? 0 : underThorn, false, c.mobPrefixes));
		}
		if (c.mobRabbitsInMid) {
			separate.add(counter("Rabbits in Mid", "RM", M4.mobColor(Rabbit.class), example ? 0 : rabbitsInMid, false, c.mobPrefixes));
		}
		List<List<Seg>> rows = new ArrayList<>();
		if (compact) {
			if (!inline.isEmpty()) {
				rows.add(inline);
			}
		} else {
			for (Seg seg : inline) {
				rows.add(List.of(seg));
			}
		}
		for (Seg seg : separate) {
			rows.add(List.of(seg));
		}
		return rows;
	}

	// ---------------------------------------------------------------- rabbits

	private static List<String> rabbitLines(boolean example) {
		M4Config c = M4.config();
		if (example) {
			return List.of("§b" + c.rabbitText + " 31.5s", "§dGround: 3", "§fBats: 1");
		}
		if (!M4.master()) {
			return List.of();
		}
		int ticks = M4Features.RABBIT_SPAWN_TIME - M4.timer;
		if (ticks <= 0) {
			return List.of();
		}
		boolean show = c.rabbitFromStart
			|| !c.rabbitAfterB1 && ticks <= c.rabbitSecondsBefore * 20
			|| c.rabbitAfterB1 && !M4.spawnStartTimes.isEmpty();
		if (!show) {
			return List.of();
		}
		List<String> lines = new ArrayList<>();
		lines.add("§b" + c.rabbitText + " " + M4Stats.fixed(ticks / 20.0, c.rabbitDecimals) + "s");
		DungeonClass mine = M4.myClass();
		if (c.rabbitGroundMobs && mine != DungeonClass.HEALER) {
			int ground = M4.animalsOf(Cow.class).size() + M4.animalsOf(Wolf.class).size();
			if (ground > 0 || !c.rabbitHideZero) {
				lines.add("§dGround: " + ground);
			}
		}
		if (c.rabbitBats && (mine == DungeonClass.BERSERK || mine == DungeonClass.MAGE)) {
			int bats = M4.animalsOf(Bat.class).size();
			if (bats > 0 || !c.rabbitHideZero) {
				lines.add("§fBats: " + bats);
			}
		}
		return lines;
	}

	// ---------------------------------------------------------------- tac

	private static String tacText(boolean example) {
		M4Config c = M4.config();
		if (example) {
			return c.tacPrefix + "1.55s";
		}
		return M4Features.tacTime >= 0 ? c.tacPrefix + M4Stats.fixed(M4Features.tacTime / 20.0, 2) + "s" : "";
	}
}
