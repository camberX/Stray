package dev.stray.client.debug;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.stray.client.item.ItemAppearance;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Session debug flags toggled with {@code /stray debug <flag>}. */
public final class StrayDebug {
	private static final Set<String> KNOWN = Set.of("composter");
	private static final int[] COMPOSTER_SLOTS = {2, 7};
	private static final Map<String, Boolean> FLAGS = new LinkedHashMap<>();
	private static String lastComposterDump = "";

	private StrayDebug() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("debug")
			.executes(context -> list())
			.then(ClientCommands.argument("flag", StringArgumentType.word())
				.suggests(suggest())
				.executes(context -> toggle(StringArgumentType.getString(context, "flag"))));
	}

	public static void tick(Minecraft client) {
		if (!enabled("composter") || client == null || client.player == null) {
			lastComposterDump = "";
			return;
		}
		dumpComposter(false);
	}

	public static boolean enabled(String flag) {
		return Boolean.TRUE.equals(FLAGS.get(flag.toLowerCase(Locale.ROOT)));
	}

	private static SuggestionProvider<FabricClientCommandSource> suggest() {
		return (context, builder) -> SharedSuggestionProvider.suggest(KNOWN, builder);
	}

	private static int list() {
		tell("Debug flags:");
		for (String flag : KNOWN) {
			tell("  " + flag + " " + (enabled(flag) ? "on" : "off"));
		}
		tell("Use /stray debug <flag> to toggle.");
		return Command.SINGLE_SUCCESS;
	}

	private static int toggle(String raw) {
		String flag = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
		if (!KNOWN.contains(flag)) {
			tell("Unknown debug flag \"" + raw + "\". Known: " + String.join(", ", KNOWN));
			return 0;
		}
		boolean next = !enabled(flag);
		FLAGS.put(flag, next);
		tell("Debug " + flag + " " + (next ? "on" : "off"));
		if (next && "composter".equals(flag)) {
			lastComposterDump = "";
			dumpComposter(true);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static void dumpComposter(boolean force) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			if (force) {
				tell("Open a chest (Composter) so slots 2 and 7 can be dumped.");
			}
			lastComposterDump = "";
			return;
		}
		StringBuilder signature = new StringBuilder();
		signature.append(clean(screen.getTitle())).append('\n');
		List<String> lines = new java.util.ArrayList<>();
		lines.add("title=" + quote(clean(screen.getTitle()))
			+ " slots=" + screen.getMenu().slots.size()
			+ " screen=" + screen.getClass().getSimpleName());
		for (int index : COMPOSTER_SLOTS) {
			describeSlot(screen, index, lines, signature);
		}
		String dump = signature.toString();
		if (!force && dump.equals(lastComposterDump)) {
			return;
		}
		lastComposterDump = dump;
		tell("Composter slots 2 and 7:");
		for (String line : lines) {
			tell(line);
		}
	}

	private static void describeSlot(
		AbstractContainerScreen<?> screen,
		int index,
		List<String> lines,
		StringBuilder signature
	) {
		List<Slot> slots = screen.getMenu().slots;
		if (index < 0 || index >= slots.size()) {
			lines.add("slot " + index + " missing (menu has " + slots.size() + ")");
			signature.append("missing ").append(index).append('\n');
			return;
		}
		ItemStack stack = slots.get(index).getItem();
		boolean prior = ItemAppearance.suppress();
		try {
			if (stack == null || stack.isEmpty()) {
				lines.add("slot " + index + " empty");
				signature.append("empty ").append(index).append('\n');
				return;
			}
			String name = clean(stack.getHoverName());
			Component custom = stack.get(DataComponents.CUSTOM_NAME);
			String customName = custom == null ? "" : clean(custom);
			ItemLore lore = stack.get(DataComponents.LORE);
			List<Component> loreLines = lore == null ? List.of() : lore.lines();
			List<Component> styled = lore == null ? List.of() : lore.styledLines();
			lines.add("slot " + index + " name=" + quote(name)
				+ (customName.isBlank() ? "" : " custom=" + quote(customName))
				+ " lore=" + loreLines.size()
				+ " styled=" + styled.size());
			signature.append(index).append('|').append(name).append('|').append(loreLines.size()).append('\n');
			if (loreLines.isEmpty() && styled.isEmpty()) {
				lines.add("  (no lore)");
				return;
			}
			int count = Math.max(loreLines.size(), styled.size());
			for (int i = 0; i < count; i++) {
				if (i < loreLines.size()) {
					String text = loreLines.get(i).getString();
					lines.add("  lore[" + i + "]=" + quote(text));
					signature.append(text).append('\n');
				}
				if (i < styled.size()) {
					String text = styled.get(i).getString();
					if (i >= loreLines.size() || !text.equals(loreLines.get(i).getString())) {
						lines.add("  styled[" + i + "]=" + quote(text));
						signature.append("s:").append(text).append('\n');
					}
				}
			}
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static String clean(Component component) {
		return component == null ? "" : component.getString().replace('\u00A0', ' ').trim();
	}

	private static String quote(String value) {
		return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
	}

	private static void tell(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(
				Component.literal("[Stray debug] ").withStyle(ChatFormatting.YELLOW)
					.append(Component.literal(text).withStyle(ChatFormatting.GRAY))
			);
		}
	}
}
