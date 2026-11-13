package dev.stray.client.ui;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class ProfileCommands {
	private ProfileCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return command("pv");
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command(String literal) {
		return ClientCommands.literal(literal)
			.executes(context -> open(""))
			.then(ClientCommands.argument("player", StringArgumentType.greedyString())
				.suggests((context, builder) -> {
					String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
					for (String name : knownNames()) {
						if (name.toLowerCase(Locale.ROOT).startsWith(typed)) {
							builder.suggest(name);
						}
					}
					return builder.buildFuture();
				})
				.executes(context -> open(StringArgumentType.getString(context, "player"))));
	}

	private static final java.util.regex.Pattern MC_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_]{3,16}");
	/** SkyBlock tab lines: "[392] Name ♲", "[280] ◆ [MVP+] Name". */
	private static final java.util.regex.Pattern TAB_LINE = java.util.regex.Pattern.compile(
		"^\\[\\d{1,3}\\] (?:\\S{1,2} )?(?:\\[[^\\]]{1,24}\\] )*([A-Za-z0-9_]{3,16})\\b");

	/**
	 * Names for tab completion: you, your party, the players around you and the tab list (on
	 * SkyBlock the names are read from its lines, since its entries are not the real players).
	 */
	public static java.util.List<String> knownNames() {
		Minecraft client = Minecraft.getInstance();
		java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
		names.add(dev.stray.client.visual.NickHider.realName());
		for (dev.stray.client.dungeon.DungeonParty.Member member : dev.stray.client.dungeon.DungeonParty.all()) {
			names.add(member.name);
		}
		if (client.level != null) {
			for (net.minecraft.world.entity.player.Player player : client.level.players()) {
				java.util.UUID id = player.getUUID();
				if (id.version() == 4 || !dev.stray.client.location.SkyblockLocation.onHypixel) {
					names.add(player.getGameProfile().name());
				}
			}
		}
		if (client.getConnection() != null) {
			for (net.minecraft.client.multiplayer.PlayerInfo info : client.getConnection().getOnlinePlayers()) {
				if (info.getProfile().id().version() == 4 || !dev.stray.client.location.SkyblockLocation.onHypixel) {
					names.add(info.getProfile().name());
				}
				if (info.getTabListDisplayName() != null) {
					String line = net.minecraft.util.StringUtil.stripColor(info.getTabListDisplayName().getString()).trim();
					java.util.regex.Matcher m = TAB_LINE.matcher(line);
					if (m.find()) {
						names.add(m.group(1));
					}
				}
			}
		}
		names.removeIf(name -> name == null || !MC_NAME.matcher(name).matches());
		java.util.List<String> sorted = new java.util.ArrayList<>(names);
		sorted.sort(String.CASE_INSENSITIVE_ORDER);
		return sorted;
	}

	public static boolean handleTyped(String raw) {
		if (!StrayConfig.get().profileViewerEnabled) {
			return false;
		}
		String message = raw == null ? "" : raw.trim();
		if (message.startsWith("/")) {
			message = message.substring(1).trim();
		}
		if (message.isEmpty()) {
			return false;
		}
		int split = -1;
		for (int i = 0; i < message.length(); i++) {
			if (Character.isWhitespace(message.charAt(i))) {
				split = i;
				break;
			}
		}
		String cmd = (split < 0 ? message : message.substring(0, split)).toLowerCase(Locale.ROOT);
		if (!"pv".equals(cmd) && !"profile".equals(cmd)) {
			return false;
		}
		String name = split < 0 ? "" : message.substring(split).trim();
		if (!name.isEmpty()) {
			int end = 0;
			while (end < name.length() && !Character.isWhitespace(name.charAt(end))) {
				end++;
			}
			name = name.substring(0, end);
		}
		open(name);
		return true;
	}

	public static int open(String name) {
		Minecraft client = Minecraft.getInstance();
		if (name == null || name.isBlank()) {
			// No name: your own profile, not whoever you looked up last.
			name = dev.stray.client.visual.NickHider.realName();
		}
		String target = name;
		if (!StrayConfig.get().profileViewerEnabled) {
			client.gui.hud.getChat().addClientSystemMessage(
				Component.literal("Stray | Turn on Profile viewer in Menus.")
			);
			return 0;
		}
		client.execute(() -> {
			if (client.gui.screen() instanceof ProfileViewerScreen screen) {
				screen.lookup(target);
				return;
			}
			client.gui.setScreen(new ProfileViewerScreen(target));
		});
		return Command.SINGLE_SUCCESS;
	}
}
