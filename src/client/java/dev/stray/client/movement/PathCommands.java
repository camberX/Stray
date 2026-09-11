package dev.stray.client.movement;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** {@code /stray path start|stop|list|show|hide|rename|delete}. */
public final class PathCommands {
	private PathCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("path")
			.executes(context -> {
				PathRecorder.toggle();
				return Command.SINGLE_SUCCESS;
			})
			.then(ClientCommands.literal("start").executes(context -> {
				if (!PathRecorder.recording()) {
					PathRecorder.start();
				}
				return Command.SINGLE_SUCCESS;
			}))
			.then(ClientCommands.literal("stop").executes(context -> {
				PathRecorder.stop(true);
				return Command.SINGLE_SUCCESS;
			}))
			.then(ClientCommands.literal("list").executes(context -> list()))
			.then(ClientCommands.literal("showall").executes(context -> {
				PathRecorder.showAll(true);
				StrayConfig.get().pathsEnabled = true;
				StrayConfig.get().save();
				tell("Showing every path on " + PathRecorder.islandLabel() + ".", ChatFormatting.GREEN);
				return Command.SINGLE_SUCCESS;
			}))
			.then(ClientCommands.literal("hideall").executes(context -> {
				PathRecorder.showAll(false);
				tell("Hid every path on " + PathRecorder.islandLabel() + ".", ChatFormatting.GRAY);
				return Command.SINGLE_SUCCESS;
			}))
			.then(ClientCommands.literal("toggle")
				.then(ClientCommands.argument("name", StringArgumentType.greedyString())
					.suggests(names())
					.executes(context -> {
						String name = StringArgumentType.getString(context, "name");
						if (PathRecorder.toggleVisible(name)) {
							PathRecorder.Recording recording = PathRecorder.find(name);
							tell(name + (recording != null && recording.visible ? " shown." : " hidden."), ChatFormatting.YELLOW);
						} else {
							tell("No path called " + name + ".", ChatFormatting.RED);
						}
						return Command.SINGLE_SUCCESS;
					})))
			.then(ClientCommands.literal("delete")
				.then(ClientCommands.argument("name", StringArgumentType.greedyString())
					.suggests(names())
					.executes(context -> {
						String name = StringArgumentType.getString(context, "name");
						tell(PathRecorder.delete(name) ? "Deleted " + name + "." : "No path called " + name + ".", ChatFormatting.YELLOW);
						return Command.SINGLE_SUCCESS;
					})))
			.then(ClientCommands.literal("rename")
				.then(ClientCommands.argument("from", StringArgumentType.string())
					.suggests(names())
					.then(ClientCommands.argument("to", StringArgumentType.greedyString())
						.executes(context -> {
							String from = StringArgumentType.getString(context, "from");
							String to = StringArgumentType.getString(context, "to");
							tell(PathRecorder.rename(from, to) ? "Renamed " + from + " to " + to + "." : "No path called " + from + ".", ChatFormatting.YELLOW);
							return Command.SINGLE_SUCCESS;
						}))));
	}

	private static SuggestionProvider<FabricClientCommandSource> names() {
		return (context, builder) -> {
			List<String> out = new ArrayList<>();
			for (PathRecorder.Recording recording : PathRecorder.here()) {
				out.add(recording.name.contains(" ") ? "\"" + recording.name + "\"" : recording.name);
			}
			return SharedSuggestionProvider.suggest(out, builder);
		};
	}

	private static int list() {
		List<PathRecorder.Recording> here = PathRecorder.here();
		tell("Paths on " + PathRecorder.islandLabel() + " (" + here.size() + ")", ChatFormatting.AQUA);
		if (PathRecorder.recording()) {
			PathRecorder.Recording live = PathRecorder.liveRecording();
			tell("  recording " + live.name + " · " + live.size() + " pts", ChatFormatting.GREEN);
		}
		for (PathRecorder.Recording recording : here) {
			tell("  " + (recording.visible ? "● " : "○ ") + recording.name + " · " + Math.round(PathRecorder.length(recording)) + "m", recording.visible ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
		}
		if (here.isEmpty()) {
			tell("  None. /stray path start to record one.", ChatFormatting.GRAY);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static void tell(String text, ChatFormatting color) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(Component.literal(text).withStyle(color));
		}
	}
}
