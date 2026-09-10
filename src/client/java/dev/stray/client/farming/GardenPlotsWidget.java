package dev.stray.client.farming;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class GardenPlotsWidget {
	static final int[] PLOT_TO_SLOT = {
		12, 7, 11, 13, 17,
		6, 8, 16, 18, 2,
		10, 14, 22, 1, 3,
		5, 9, 15, 19, 21,
		23, 0, 4, 20, 24
	};
	private static final String[] CUSTOM_ICONS = {
		"",
		"WHEAT",
		"CARROT_ITEM",
		"POTATO_ITEM",
		"SUGAR_CANE",
		"DOUBLE_PLANT",
		"MOONFLOWER",
		"WILD_ROSE",
		"NETHER_STALK",
		"RED_MUSHROOM",
		"CACTUS",
		"MELON",
		"PUMPKIN",
		"INK_SACK-3"
	};
	private static final int WIDTH = 104;
	private static final int HEIGHT = 132;
	private static final int GAP = 4;
	private static final int BARN = 12;

	private Bounds inventory;
	private int x;
	private int y;
	private int hovered = -1;
	private int editing = -1;
	private boolean titleHover;
	private int dragOffX;
	private int dragOffY;
	private boolean dragging;
	private long lastPestRead;
	private final int[] pestPlot = new int[25];
	private final int[] pestCount = new int[25];

	GardenPlotsWidget(Bounds inventory) {
		this.inventory = inventory;
		place();
	}

	void setInventory(Bounds inventory) {
		this.inventory = inventory;
		if (!dragging) {
			place();
		}
	}

	void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		refreshPests(client);
		hovered = -1;
		titleHover = GuiDraw.hovered(mouseX, mouseY, x + 4, y + 3, WIDTH - 8, 14);
		GuiDraw.panel(graphics, x, y, WIDTH, HEIGHT, 8, Theme.CARD, Theme.LINE);
		String title = editing >= 0 ? "Custom icon" : "Garden plots";
		GuiDraw.small(graphics, font, title, x + 8, GuiDraw.middle(y + 3, 14), Theme.HEADER);

		ItemStack[] stacks = editing >= 0 ? customStacks() : plotStacks();
		long now = System.currentTimeMillis();
		for (int i = 0; i < 25; i++) {
			int slotX = x + 7 + (i % 5) * 18;
			int slotY = y + 17 + (i / 5) * 18;
			boolean over = GuiDraw.hovered(mouseX, mouseY, slotX, slotY, 18, 18);
			if (over) {
				hovered = i;
				GuiDraw.fill(graphics, slotX, slotY, 18, 18, 0x33FFFFFF);
			}
			ItemStack stack = i < stacks.length ? stacks[i] : ItemStack.EMPTY;
			if (stack != null && !stack.isEmpty()) {
				paintItem(graphics, client.player, stack, slotX + 1, slotY + 1, over && !isGlass(stack) ? 18 : 16);
			}
			if (editing < 0 && pestPlot[i] > 0) {
				if ((now & 512) != 0) {
					GuiDraw.border(graphics, slotX + 1, slotY + 1, 16, 16, 0xFFFF5555, 1.2f);
				}
				String number = Integer.toString(pestPlot[i]);
				int nw = font.width(number);
				graphics.text(font, number, slotX + 18 - nw, slotY + 10, 0xFFFF5555, true);
			}
			if (over) {
				tip(graphics, font, tooltip(stack, i), mouseX, mouseY);
			}
		}

		drawButton(graphics, font, client.player, mouseX, mouseY, x + 7, y + HEIGHT - 24, new ItemStack(Items.BOOK), "Desk");
		drawButton(graphics, font, client.player, mouseX, mouseY, x + WIDTH - 49, y + HEIGHT - 24, new ItemStack(Items.ENDER_EYE), "Garden spawn");
		drawButton(graphics, font, client.player, mouseX, mouseY, x + WIDTH - 27, y + HEIGHT - 24, new ItemStack(Items.RED_BED), "Set spawn");
	}

	boolean mouseClicked(MouseButtonEvent event) {
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		if (!GuiDraw.hovered(mouseX, mouseY, x, y, WIDTH, HEIGHT)) {
			return false;
		}
		if (titleHover) {
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && Minecraft.getInstance().hasShiftDown()) {
				StrayConfig config = StrayConfig.get();
				config.gardenPlotsX = 0;
				config.gardenPlotsY = 0;
				config.save();
				place();
				return true;
			}
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				dragging = true;
				dragOffX = mouseX - x;
				dragOffY = mouseY - y;
				return true;
			}
		}
		if (hoveredButton(mouseX, mouseY, x + 7, y + HEIGHT - 24)) {
			command("desk");
			return true;
		}
		if (hoveredButton(mouseX, mouseY, x + WIDTH - 49, y + HEIGHT - 24)) {
			command("warp garden");
			return true;
		}
		if (hoveredButton(mouseX, mouseY, x + WIDTH - 27, y + HEIGHT - 24)) {
			command("setspawn");
			return true;
		}
		if (hovered < 0) {
			return true;
		}
		if (editing >= 0) {
			GardenPlots.Plot plot = GardenPlots.PLOTS[editing];
			if (plot != null && hovered < CUSTOM_ICONS.length) {
				GardenPlots.PLOTS[editing] = plot.withCustom(CUSTOM_ICONS[hovered]);
				GardenPlots.save();
			}
			editing = -1;
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (hovered != BARN && GardenPlots.PLOTS[hovered] != null) {
				editing = hovered;
			}
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return true;
		}
		if (StrayConfig.get().gardenPlotsCloseOnClick) {
			Minecraft client = Minecraft.getInstance();
			if (client.screen != null) {
				client.screen.onClose();
			}
		}
		if (hovered == BARN) {
			command("plottp barn");
			return true;
		}
		GardenPlots.Plot plot = GardenPlots.PLOTS[hovered];
		if (plot != null && !plot.name.isBlank()) {
			command("plottp " + plot.name);
		}
		return true;
	}

	boolean mouseReleased(MouseButtonEvent event) {
		if (!dragging) {
			return false;
		}
		dragging = false;
		StrayConfig config = StrayConfig.get();
		config.gardenPlotsX = x - inventory.left() - inventory.width() - GAP;
		config.gardenPlotsY = y - inventory.top();
		config.save();
		return true;
	}

	boolean mouseDragged(MouseButtonEvent event) {
		if (!dragging) {
			return false;
		}
		int nextX = Math.max((int) event.x() - dragOffX, inventory.left() + inventory.width() + GAP);
		int nextY = (int) event.y() - dragOffY;
		x = nextX;
		y = nextY;
		return true;
	}

	private void place() {
		StrayConfig config = StrayConfig.get();
		x = inventory.left() + inventory.width() + GAP + config.gardenPlotsX;
		y = inventory.top() + config.gardenPlotsY;
	}

	private ItemStack[] plotStacks() {
		ItemStack[] stacks = new ItemStack[25];
		for (int i = 0; i < 25; i++) {
			if (i == BARN) {
				ItemStack barn = new ItemStack(Items.LODESTONE);
				barn.set(DataComponents.CUSTOM_NAME, Component.literal("The Barn").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
				stacks[i] = barn;
				continue;
			}
			GardenPlots.Plot plot = GardenPlots.PLOTS[i];
			if (plot == null) {
				continue;
			}
			ItemStack stack = resolve(plot.customIcon.isBlank() ? plot.icon : plot.customIcon);
			if (stack.isEmpty()) {
				stack = new ItemStack(Items.WHEAT);
			}
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(plot.name).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			stacks[i] = stack;
		}
		return stacks;
	}

	private ItemStack[] customStacks() {
		ItemStack[] stacks = new ItemStack[25];
		stacks[0] = named(new ItemStack(Items.BARRIER), "None");
		for (int i = 1; i < CUSTOM_ICONS.length; i++) {
			stacks[i] = named(resolve(CUSTOM_ICONS[i]), CUSTOM_ICONS[i]);
		}
		return stacks;
	}

	private List<Component> tooltip(ItemStack stack, int slot) {
		List<Component> lines = new ArrayList<>();
		if (editing >= 0) {
			lines.add(stack == null || stack.isEmpty() ? Component.literal("None") : stack.getHoverName());
			return lines;
		}
		Component name = slot == BARN
			? Component.literal("The Barn")
			: stack != null && !stack.isEmpty()
				? stack.getHoverName()
				: Component.literal("Empty plot");
		if (slot == BARN) {
			lines.add(name);
		} else {
			int plot = plotNumber(slot);
			Component title = Component.literal(plot > 0 ? "Plot " + plot + " " : "Plot ").append(name);
			lines.add(title);
		}
		if (pestPlot[slot] > 0) {
			String pests = pestCount[slot] > 0
				? pestCount[slot] + (pestCount[slot] == 1 ? " pest" : " pests")
				: "Pests";
			lines.add(Component.literal(pests).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
		lines.add(Component.empty());
		lines.add(Component.literal("Click to warp").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
		if (slot != BARN) {
			lines.add(Component.literal("Right-click to set icon").withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	private static int plotNumber(int slot) {
		for (int plot = 0; plot < PLOT_TO_SLOT.length; plot++) {
			if (PLOT_TO_SLOT[plot] == slot) {
				return plot;
			}
		}
		return -1;
	}

	private void refreshPests(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now - lastPestRead < 3000) {
			return;
		}
		lastPestRead = now;
		Arrays.fill(pestPlot, 0);
		Arrays.fill(pestCount, 0);
		if (client.player == null || client.player.connection == null) {
			return;
		}
		List<net.minecraft.client.multiplayer.PlayerInfo> infos = new ArrayList<>(client.player.connection.getListedOnlinePlayers());
		infos.sort(Comparator.comparingInt((net.minecraft.client.multiplayer.PlayerInfo info) -> -info.getTabListOrder()));
		for (var info : infos) {
			var display = info.getTabListDisplayName();
			if (display == null) {
				continue;
			}
			String line = ChatFormatting.stripFormatting(display.getString());
			if (line == null) {
				continue;
			}
			line = line.trim();
			if (line.startsWith("Plots:")) {
				String[] parts = line.split(":", 2);
				if (parts.length < 2) {
					continue;
				}
				for (String part : parts[1].split(",")) {
					markPest(part.strip());
				}
			} else if (line.regionMatches(true, 0, "Plot ", 0, 5)) {
				markPest(line.substring(5).strip());
			}
		}
	}

	private void markPest(String raw) {
		if (raw.isEmpty()) {
			return;
		}
		java.util.regex.Matcher match = java.util.regex.Pattern.compile("(\\d+)\\s*(?:x|:)?\\s*(\\d+)?").matcher(raw);
		if (!match.find()) {
			return;
		}
		int plot;
		int count = 0;
		try {
			plot = Integer.parseInt(match.group(1));
			if (match.group(2) != null && !match.group(2).isEmpty()) {
				count = Integer.parseInt(match.group(2));
			}
		} catch (NumberFormatException ignored) {
			return;
		}
		if (plot <= 0 || plot >= PLOT_TO_SLOT.length) {
			return;
		}
		int slot = PLOT_TO_SLOT[plot];
		if (slot < 0 || slot >= pestPlot.length) {
			return;
		}
		pestPlot[slot] = plot;
		if (count > 0) {
			pestCount[slot] = count;
		}
	}

	private void drawButton(
		GuiGraphicsExtractor graphics,
		Font font,
		LocalPlayer player,
		int mouseX,
		int mouseY,
		int bx,
		int by,
		ItemStack icon,
		String label
	) {
		boolean over = hoveredButton(mouseX, mouseY, bx, by);
		GuiDraw.panel(graphics, bx, by, 20, 20, 4, over ? Theme.CARD_HOVER : Theme.PANEL, Theme.LINE);
		paintItem(graphics, player, icon, bx + 2, by + 2, 16);
		if (over) {
			tip(graphics, font, List.of(Component.literal(label)), mouseX, mouseY);
		}
	}

	private static boolean hoveredButton(int mouseX, int mouseY, int bx, int by) {
		return GuiDraw.hovered(mouseX, mouseY, bx, by, 20, 20);
	}

	private static void paintItem(GuiGraphicsExtractor graphics, LocalPlayer player, ItemStack stack, float x, float y, float size) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		float scale = size / 16f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1f) {
			graphics.pose().scale(scale, scale);
		}
		if (player == null) {
			graphics.item(stack, 0, 0);
		} else {
			graphics.item(player, stack, 0, 0, 1);
		}
		graphics.pose().popMatrix();
	}

	private static ItemStack resolve(String id) {
		if (id == null || id.isBlank()) {
			return ItemStack.EMPTY;
		}
		ItemIds.Preview preview = ItemIds.resolve(id.startsWith("sb:") || id.startsWith("minecraft:") ? id : "sb:" + id);
		if (preview.stack() != null && !preview.stack().isEmpty()) {
			return preview.stack().copy();
		}
		return switch (id) {
			case "WHEAT" -> new ItemStack(Items.WHEAT);
			case "CARROT_ITEM" -> new ItemStack(Items.CARROT);
			case "POTATO_ITEM" -> new ItemStack(Items.POTATO);
			case "SUGAR_CANE" -> new ItemStack(Items.SUGAR_CANE);
			case "DOUBLE_PLANT" -> new ItemStack(Items.SUNFLOWER);
			case "MOONFLOWER" -> new ItemStack(Items.OPEN_EYEBLOSSOM);
			case "WILD_ROSE" -> new ItemStack(Items.ROSE_BUSH);
			case "NETHER_STALK" -> new ItemStack(Items.NETHER_WART);
			case "RED_MUSHROOM" -> new ItemStack(Items.RED_MUSHROOM);
			case "CACTUS" -> new ItemStack(Items.CACTUS);
			case "MELON" -> new ItemStack(Items.MELON);
			case "PUMPKIN" -> new ItemStack(Items.PUMPKIN);
			case "INK_SACK-3" -> new ItemStack(Items.COCOA_BEANS);
			default -> ItemStack.EMPTY;
		};
	}

	private static ItemStack named(ItemStack stack, String name) {
		if (stack == null || stack.isEmpty()) {
			stack = new ItemStack(Items.BARRIER);
		}
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return stack;
	}

	private static boolean isGlass(ItemStack stack) {
		String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.endsWith("glass_pane") || path.endsWith("glass");
	}

	private static void tip(GuiGraphicsExtractor graphics, Font font, List<Component> lines, int mouseX, int mouseY) {
		graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
	}

	private static void command(String command) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			return;
		}
		client.player.connection.sendCommand(command);
	}

	record Bounds(int left, int top, int width, int height) {
	}
}
