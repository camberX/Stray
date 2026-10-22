package dev.stray.client.farming;

import dev.stray.client.StrayClient;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.farming.GardenPlotsWidget.PestSpot;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Keybind: {@code /plottp} to a plot the pests tab widget lists.
 * Another press while more than one plot is infested goes to the next one.
 */
public final class PestPlotTp {
	private static boolean wasHeld;
	private static int lastPlot = -1;

	private PestPlotTp() {
	}

	public static void syncEdge() {
		wasHeld = held();
	}

	public static void reset() {
		lastPlot = -1;
		wasHeld = held();
	}

	public static void poll(Minecraft client, boolean ignore) {
		boolean down = held();
		if (!ignore && down && !wasHeld && client.screen == null) {
			go(client);
		}
		wasHeld = down;
	}

	private static boolean held() {
		return StrayClient.menuKeyHeld(StrayConfig.get().pestPlotKey);
	}

	private static void go(Minecraft client) {
		if (client.player == null || client.player.connection == null || !wherePestsLive()) {
			return;
		}
		List<PestSpot> spots = new ArrayList<>(GardenPlotsWidget.pestSpots(client));
		spots.sort(Comparator.comparingInt((PestSpot spot) -> spot.count() > 0 ? -spot.count() : 0).thenComparingInt(PestSpot::number));
		if (spots.isEmpty()) {
			client.player.sendSystemMessage(Component.literal("No pest plots").withStyle(ChatFormatting.RED));
			return;
		}
		int index = 0;
		for (int i = 0; i < spots.size(); i++) {
			if (spots.get(i).number() == lastPlot) {
				index = (i + 1) % spots.size();
				break;
			}
		}
		PestSpot pick = spots.get(index);
		lastPlot = pick.number();
		String name = "";
		GardenPlots.Plot plot = GardenPlots.PLOTS[pick.slot()];
		if (plot != null && plot.name != null) {
			name = plot.name.trim();
		}
		String arg = name.isEmpty() ? Integer.toString(pick.number()) : name;
		client.player.connection.sendCommand("plottp " + arg);
	}

	private static boolean wherePestsLive() {
		if (SkyblockLocation.inGarden()) {
			return true;
		}
		if (!SkyblockLocation.inSkyblock) {
			return false;
		}
		String area = SkyblockLocation.area == null ? "" : SkyblockLocation.area.toLowerCase(Locale.ROOT);
		String poi = SkyblockLocation.poi == null ? "" : SkyblockLocation.poi.toLowerCase(Locale.ROOT);
		return area.contains("greenhouse") || poi.contains("greenhouse");
	}
}
