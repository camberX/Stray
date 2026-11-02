package dev.stray.client.farming;

import dev.stray.client.util.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The garden pest count from the sidebar: {@code xN} on the garden / plot row, 0 once that row is clear. */
public final class PestScoreboard {
	/** Scoreboard pest row keeps the pest icon and ends in {@code xN}. */
	private static final Pattern BOARD_COUNT = Pattern.compile(
		"(?i)(?:the garden|plot\\s*-\\s*.+?)\\s*[\\uE018\\uE07F]?\\s*x\\s*(\\d+)\\s*$"
	);
	/** Same row with the icon gone means the garden is clear. The {@code ⏣} area line is not this row. */
	private static final Pattern BOARD_CLEAR = Pattern.compile(
		"(?i)^[^\\p{L}⏣]*+(?:the garden|plot\\s*-\\s*\\S.*)$"
	);

	private PestScoreboard() {
	}

	/** Pests on the sidebar row, 0 when it reads clear, -1 when there is no such row. */
	public static int count(Minecraft client) {
		return scoreboardPests(client);
	}

	private static int scoreboardPests(Minecraft client) {
		int count = -1;
		for (String line : sidebar(client)) {
			Matcher counted = BOARD_COUNT.matcher(line);
			if (counted.find()) {
				try {
					count = Math.max(count, Integer.parseInt(counted.group(1)));
				} catch (NumberFormatException ignored) {
					count = Math.max(count, 1);
				}
				continue;
			}
			if (count <= 0 && BOARD_CLEAR.matcher(line).matches()) {
				count = 0;
			}
		}
		return count;
	}

	private static List<String> sidebar(Minecraft client) {
		if (client.level == null || client.player == null) {
			return List.of();
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = sidebarObjective(scoreboard, client.player.getScoreboardName());
		if (objective == null) {
			return List.of();
		}
		List<PlayerScoreEntry> entries = new ArrayList<>();
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			if (!entry.isHidden()) {
				entries.add(entry);
			}
		}
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
		List<String> lines = new ArrayList<>(entries.size());
		for (PlayerScoreEntry entry : entries) {
			Component raw = entry.display() != null ? entry.display() : Component.literal(entry.owner());
			String text = clean(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), raw));
			if (!text.isEmpty()) {
				lines.add(text);
			}
		}
		return lines;
	}

	private static Objective sidebarObjective(Scoreboard scoreboard, String playerName) {
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		PlayerTeam team = scoreboard.getPlayersTeam(playerName);
		DisplaySlot colored = team == null ? null : team.getColor().map(TeamColor::displaySlot).orElse(null);
		if (colored != null) {
			Objective teamObjective = scoreboard.getDisplayObjective(colored);
			if (teamObjective != null) {
				return teamObjective;
			}
		}
		return objective;
	}

	private static String clean(Component component) {
		if (component == null) {
			return "";
		}
		String text = component.getString();
		if (text == null) {
			return "";
		}
		return Text.collapse(Text.strip(text).replace('\u00A0', ' ')).trim();
	}
}
