package dev.stray.client.farming;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Skyblocker Garden plot grid: capture Configure Plots, show it beside
 * inventory only while on the Garden.
 */
public final class GardenPlots {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("stray-garden-plots.json");
	static final Plot[] PLOTS = new Plot[25];
	private static GardenPlotsWidget widget;

	private GardenPlots() {
	}

	public static void init() {
		load();
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AbstractContainerScreen<?> container
				&& "Configure Plots".equals(screen.getTitle().getString().trim())) {
				ScreenEvents.remove(screen).register(closed -> capture(container));
				return;
			}
			if (!(screen instanceof InventoryScreen inventory)) {
				return;
			}
			if (!visible()) {
				return;
			}
			widget = new GardenPlotsWidget(bounds(inventory));
			ScreenEvents.remove(screen).register(closed -> widget = null);
			ScreenMouseEvents.allowMouseClick(screen).register((opened, event) -> {
				if (widget == null || !visible()) {
					return true;
				}
				return !widget.mouseClicked(event);
			});
			ScreenMouseEvents.allowMouseRelease(screen).register((opened, event) -> {
				if (widget == null) {
					return true;
				}
				return !widget.mouseReleased(event);
			});
			ScreenMouseEvents.allowMouseDrag(screen).register((opened, event, dx, dy) -> {
				if (widget == null) {
					return true;
				}
				return !widget.mouseDragged(event);
			});
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
	}

	public static void extract(AbstractContainerScreen<?> screen, net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (widget == null || !visible() || !(screen instanceof InventoryScreen inventory)) {
			return;
		}
		widget.setInventory(bounds(inventory));
		widget.extract(graphics, mouseX, mouseY);
	}

	static boolean visible() {
		return StrayConfig.get().gardenPlotsWidget && SkyblockLocation.inGarden();
	}

	static void save() {
		JsonArray array = new JsonArray();
		for (Plot plot : PLOTS) {
			if (plot == null) {
				array.add((JsonElement) null);
				continue;
			}
			JsonObject object = new JsonObject();
			object.addProperty("icon", plot.icon);
			object.addProperty("name", plot.name);
			if (plot.customIcon != null && !plot.customIcon.isBlank()) {
				object.addProperty("customIcon", plot.customIcon);
			}
			array.add(object);
		}
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(array, writer);
			}
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not write stray-garden-plots.json", exception);
		}
	}

	private static void load() {
		if (!Files.isRegularFile(PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(PATH)) {
			JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
			int limit = Math.min(PLOTS.length, array.size());
			for (int i = 0; i < limit; i++) {
				JsonElement element = array.get(i);
				if (element == null || element.isJsonNull() || !element.isJsonObject()) {
					PLOTS[i] = null;
					continue;
				}
				JsonObject object = element.getAsJsonObject();
				String name = object.has("name") ? object.get("name").getAsString() : "";
				if (name.isBlank()) {
					PLOTS[i] = null;
					continue;
				}
				String icon = object.has("icon") ? object.get("icon").getAsString() : "";
				String custom = object.has("customIcon") ? object.get("customIcon").getAsString() : "";
				PLOTS[i] = new Plot(icon, name, custom);
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read stray-garden-plots.json", exception);
		}
	}

	private static void capture(AbstractContainerScreen<?> screen) {
		var slots = screen.getMenu().slots;
		for (int row = 0; row < 5; row++) {
			for (int col = 2; col < 7; col++) {
				int slotId = row * 9 + col;
				if (slotId == 22 || slotId >= slots.size()) {
					continue;
				}
				Slot slot = slots.get(slotId);
				ItemStack stack = slot.getItem();
				if (skipIcon(stack)) {
					continue;
				}
				String raw = ChatFormatting.stripFormatting(stack.getHoverName().getString());
				if (raw == null) {
					continue;
				}
				String[] parts = raw.split("-", 2);
				if (parts.length < 2) {
					continue;
				}
				int index = row * 5 + (col - 2);
				String name = parts[1].trim();
				if (name.isEmpty() || index < 0 || index >= PLOTS.length) {
					continue;
				}
				Plot previous = PLOTS[index];
				PLOTS[index] = new Plot(ItemIds.idOf(stack), name, previous == null ? "" : previous.customIcon);
			}
		}
		save();
	}

	private static boolean skipIcon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return "oak_button".equals(path)
			|| "red_stained_glass_pane".equals(path)
			|| "black_stained_glass_pane".equals(path);
	}

	private static GardenPlotsWidget.Bounds bounds(InventoryScreen screen) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		return new GardenPlotsWidget.Bounds(
			accessor.stray$leftPos(),
			accessor.stray$topPos(),
			accessor.stray$imageWidth(),
			accessor.stray$imageHeight()
		);
	}

	static final class Plot {
		final String icon;
		final String name;
		final String customIcon;

		Plot(String icon, String name, String customIcon) {
			this.icon = icon == null ? "" : icon;
			this.name = name == null ? "" : name;
			this.customIcon = customIcon == null ? "" : customIcon;
		}

		Plot withCustom(String next) {
			return new Plot(icon, name, next == null ? "" : next);
		}
	}
}
