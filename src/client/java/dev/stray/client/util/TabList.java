package dev.stray.client.util;

import net.minecraft.client.multiplayer.PlayerInfo;
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

/** Reading the tab list and the sidebar the way vanilla shows them. */
public final class TabList {
	private TabList() {
	}

	/** A tab list entry's name as the tab list shows it: the server's display name, else the team-formatted profile name. */
	public static Component displayName(PlayerInfo info) {
		Component display = info.getTabListDisplayName();
		return display != null
			? display
			: PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
	}

	/** The objective vanilla shows in the sidebar for this player: their team colour's own sidebar when it has one, else the main one. */
	public static Objective sidebarObjective(Scoreboard scoreboard, String playerName) {
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

	/**
	 * The lines of a sidebar objective the way vanilla lists them: hidden entries left out,
	 * highest score first, each with its team prefix and suffix. Callers clean the text
	 * their own way.
	 */
	public static List<Component> sidebarLines(Scoreboard scoreboard, Objective objective) {
		List<PlayerScoreEntry> entries = new ArrayList<>();
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			if (!entry.isHidden()) {
				entries.add(entry);
			}
		}
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
		List<Component> lines = new ArrayList<>(entries.size());
		for (PlayerScoreEntry entry : entries) {
			Component raw = entry.display() != null ? entry.display() : Component.literal(entry.owner());
			lines.add(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), raw));
		}
		return lines;
	}
}
