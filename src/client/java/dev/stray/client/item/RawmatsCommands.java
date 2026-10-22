package dev.stray.client.item;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RawmatsCommands {
	private static final Pattern COUNT_TAIL = Pattern.compile("(?i)^(.+?)(?:\\s+[x×*]\\s*(\\d+)|\\s+(\\d+))$");

	private RawmatsCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("rawmats")
			.executes(context -> trackHeld())
			.then(ClientCommands.argument("id", StringArgumentType.greedyString())
				.suggests(suggest())
				.executes(context -> track(StringArgumentType.getString(context, "id"))));
	}

	private static SuggestionProvider<FabricClientCommandSource> suggest() {
		return (context, builder) -> {
			String remaining = builder.getRemaining();
			String lower = remaining == null ? "" : remaining.trim().toLowerCase(Locale.ROOT);
			if (lower.isEmpty() || "clear".startsWith(lower)) {
				builder.suggest("clear");
			}
			if (lower.isEmpty() || "raw".startsWith(lower)) {
				builder.suggest("raw");
			}
			if (lower.isEmpty() || "enchanted".startsWith(lower)) {
				builder.suggest("enchanted");
			}
			if (lower.isEmpty() || "refresh".startsWith(lower)) {
				builder.suggest("refresh");
			}
			if (lower.isEmpty()) {
				return builder.buildFuture();
			}
			List<String> hits;
			if (lower.startsWith("sb:") || lower.startsWith("skyblock:")) {
				hits = ItemIds.suggest(remaining, 40);
			} else {
				hits = ItemIds.suggest("sb:" + remaining.trim(), 40);
				if (hits.isEmpty()) {
					hits = ItemIds.suggest(remaining, 20);
				}
			}
			for (String hit : hits) {
				builder.suggest(hit);
			}
			return builder.buildFuture();
		};
	}

	public static int trackHeld() {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		ItemStack held = ItemIds.held(player);
		String id = ItemStorage.idOf(held);
		if (id == null) {
			tell(muted("Hold a Skyblock item or type /st rawmats <id> [count]."));
			return 0;
		}
		return track(id);
	}

	public static int track(String query) {
		if (query == null || query.isBlank()) {
			return trackHeld();
		}
		String trimmed = query.trim();
		if (trimmed.equalsIgnoreCase("clear") || trimmed.equalsIgnoreCase("off") || trimmed.equalsIgnoreCase("none")) {
			RawmatsTracker.clear();
			tell(brand().append(sep()).append(Component.literal("RAW MATS").withStyle(style(Theme.ACCENT).withBold(true)))
				.append(Component.literal(" cleared").withStyle(style(Theme.MUTED))));
			return Command.SINGLE_SUCCESS;
		}
		if (trimmed.equalsIgnoreCase("refresh")) {
			SkyblockProfileApi.refresh();
			tell(brand().append(sep()).append(Component.literal("RAW MATS").withStyle(style(Theme.ACCENT).withBold(true)))
				.append(Component.literal(" refreshing storage").withStyle(style(Theme.MUTED))));
			return Command.SINGLE_SUCCESS;
		}
		if (trimmed.equalsIgnoreCase("raw") || trimmed.equalsIgnoreCase("enchanted") || trimmed.equalsIgnoreCase("ench")) {
			StrayConfig config = StrayConfig.get();
			config.rawmatsEnchanted = trimmed.toLowerCase(Locale.ROOT).startsWith("ench");
			config.save();
			tell(brand().append(sep()).append(Component.literal("RAW MATS").withStyle(style(Theme.ACCENT).withBold(true)))
				.append(Component.literal(" " + config.rawmatsModeLabel()).withStyle(style(Theme.TEXT))));
			return Command.SINGLE_SUCCESS;
		}
		Query parsed = parse(trimmed);
		ItemIds.Preview preview = ItemIds.resolve(parsed.id());
		String id = preview.kind() == ItemIds.Kind.SKYBLOCK
			? SkyblockRecipes.normalize(preview.canonical())
			: SkyblockRecipes.normalize(parsed.id());
		if (id.isBlank()) {
			tell(muted("Unknown item. Try sb:HYPERION or hold the item and run /st rawmats."));
			return 0;
		}
		RawmatsTracker.set(id, parsed.count());
		RawmatsTracker.Snapshot snap = RawmatsTracker.snapshot();
		MutableComponent line = brand()
			.append(sep())
			.append(Component.literal("RAW MATS").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal(" " + snap.name()).withStyle(style(Theme.TEXT)));
		if (parsed.count() > 1L) {
			line.append(Component.literal(" ×" + parsed.count()).withStyle(style(Theme.ACCENT)));
		}
		if (snap.recipe()) {
			line.append(Component.literal("  " + StrayConfig.get().rawmatsModeLabel().toLowerCase(Locale.ROOT) + "  " + snap.complete() + "/" + snap.total() + " materials").withStyle(style(Theme.MUTED)));
		} else {
			line.append(Component.literal("  no craft tree, tracking the item itself").withStyle(style(Theme.MUTED)));
		}
		tell(line);
		int shown = 0;
		for (RawmatsTracker.Line row : snap.lines()) {
			if (shown >= 8) {
				tell(muted("  +" + (snap.lines().size() - shown) + " more on the HUD"));
				break;
			}
			String mark = row.done() ? "done" : row.have() + "/" + row.need();
			tell(Component.literal("  " + row.name() + "  " + mark).withStyle(style(row.done() ? Theme.ACCENT : Theme.TEXT)));
			if (row.hasNote()) {
				tell(muted("    " + row.note()));
			}
			shown++;
		}
		if (!ItemStorage.hasApiStorage() && (!snap.sawEnder() || !snap.sawBackpack() || !snap.sawSacks())) {
			tell(muted("Ender Chest, backpacks, and sacks load when you join a server or run /st rawmats."));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static Query parse(String trimmed) {
		Matcher matcher = COUNT_TAIL.matcher(trimmed);
		if (!matcher.matches()) {
			return new Query(trimmed, 1L);
		}
		String id = matcher.group(1).trim();
		if (id.isEmpty() || keyword(id)) {
			return new Query(trimmed, 1L);
		}
		String digits = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
		long count = 1L;
		try {
			count = Long.parseLong(digits);
		} catch (NumberFormatException ignored) {
			return new Query(trimmed, 1L);
		}
		return new Query(id, StrayConfig.clampRawmatsCount(count));
	}

	private static boolean keyword(String value) {
		String key = value.toLowerCase(Locale.ROOT);
		return key.equals("clear") || key.equals("off") || key.equals("none")
			|| key.equals("refresh") || key.equals("raw") || key.equals("enchanted") || key.equals("ench");
	}

	private record Query(String id, long count) {
	}

	private static MutableComponent brand() {
		return Component.literal("STRAY").withStyle(style(Theme.ACCENT).withBold(true));
	}

	private static MutableComponent sep() {
		return Component.literal(" | ").withStyle(style(Theme.MUTED));
	}

	private static MutableComponent muted(String value) {
		return Component.literal(value).withStyle(style(Theme.MUTED));
	}

	private static Style style(int color) {
		return Style.EMPTY.withColor(color & 0xFFFFFF);
	}

	private static void tell(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(message);
		}
	}
}
