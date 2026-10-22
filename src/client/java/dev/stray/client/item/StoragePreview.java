package dev.stray.client.item;

import com.mojang.serialization.Codec;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.mixin.AbstractContainerScreenAccessor;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skyblocker-style storage preview: after you open an Ender Chest page or
 * backpack once, hover it in {@code /storage} to see the last contents.
 */
public final class StoragePreview {
	private static final Pattern ECHEST = Pattern.compile(
		"ender\\s*chests?\\s*(?:page\\s*)?\\(?\\s*(\\d+)\\s*(?:/\\s*\\d+)?\\s*\\)?",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern BACKPACK = Pattern.compile(
		"backpack.*?slot\\s*#?\\s*(\\d+)|backpack\\s*(?:page\\s*)?#?\\s*(\\d+)",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern PROFILE = Pattern.compile("^Profile:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Codec<List<ItemStack>> ITEMS = ItemStack.OPTIONAL_CODEC.listOf();
	private static final int PAGES = 27;
	private static final int SLOT = 18;
	private static final int GAP = 1;
	private static final int PAD = 7;
	private static final int HEAD = 16;
	private static final int COLS = 9;

	private static final Page[] pages = new Page[PAGES];
	private static String loadedKey = "";
	private static String lastProfile = "";
	private static Path saveDir;
	private static boolean dirty;

	private StoragePreview() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container)) {
				return;
			}
			ScreenEvents.remove(screen).register(closed -> capture(container, false));
			ScreenEvents.afterExtract(screen).register((opened, graphics, mouseX, mouseY, tickDelta) -> {
				if (opened instanceof AbstractContainerScreen<?> open) {
					extract(open, graphics, mouseX, mouseY, hovered(open), false);
				}
			});
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> flush());
	}

	public static void tick(Minecraft client) {
		if (client == null || client.player == null) {
			return;
		}
		if (!StrayConfig.get().storagePreviewEnabled) {
			return;
		}
		if (client.screen instanceof AbstractContainerScreen<?> container) {
			capture(container, true);
		}
		if (!SkyblockLocation.inSkyblock) {
			return;
		}
		String key = profileKey(client);
		if (key.isEmpty()) {
			return;
		}
		if (!key.equals(loadedKey)) {
			flush();
			loadedKey = key;
			saveDir = FabricLoader.getInstance().getConfigDir()
				.resolve("stray")
				.resolve("storage-preview")
				.resolve(key);
			load();
		}
		if (dirty) {
			flush();
		}
	}

	public static boolean hideTooltip(AbstractContainerScreen<?> screen, Slot hovered) {
		return extract(screen, null, 0, 0, hovered, true);
	}

	public static boolean extract(
		AbstractContainerScreen<?> screen,
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY,
		Slot hovered
	) {
		return extract(screen, graphics, mouseX, mouseY, hovered, false);
	}

	private static boolean extract(
		AbstractContainerScreen<?> screen,
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY,
		Slot hovered,
		boolean probe
	) {
		if (!StrayConfig.get().storagePreviewEnabled) {
			return false;
		}
		if (hovered == null || hovered.getItem() == null || hovered.getItem().isEmpty()) {
			return false;
		}
		if (!storageMenu(title(screen))) {
			return false;
		}
		if (StrayConfig.get().storagePreviewHoldShift && !Minecraft.getInstance().hasShiftDown()) {
			return false;
		}
		int index = pageOf(hovered);
		if (index < 0) {
			return false;
		}
		if (probe) {
			return true;
		}
		if (graphics == null) {
			return true;
		}
		Page page = pages[index];
		draw(graphics, screen, page, index, mouseX, mouseY);
		return true;
	}

	private static Slot hovered(AbstractContainerScreen<?> screen) {
		return ((AbstractContainerScreenAccessor) screen).stray$hoveredSlot();
	}

	private static void capture(AbstractContainerScreen<?> screen, boolean live) {
		String name = title(screen);
		int index = pageFromText(name);
		if (index < 0) {
			return;
		}
		AbstractContainerMenu menu = screen.getMenu();
		if (menu == null) {
			return;
		}
		int chest = Math.max(0, menu.slots.size() - 36);
		if (chest < 9) {
			return;
		}
		List<ItemStack> items = new ArrayList<>(chest);
		int filled = 0;
		for (int i = 0; i < chest; i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (stack == null || stack.isEmpty()) {
				items.add(ItemStack.EMPTY);
			} else {
				items.add(stack.copy());
				filled++;
			}
		}
		if (live && filled == 0) {
			return;
		}
		Page prior = pages[index];
		if (filled == 0 && prior != null && !prior.empty()) {
			return;
		}
		if (prior != null && sameItems(prior.items, items)) {
			return;
		}
		pages[index] = new Page(name, items);
		dirty = true;
	}

	private static boolean sameItems(List<ItemStack> left, List<ItemStack> right) {
		if (left.size() != right.size()) {
			return false;
		}
		for (int i = 0; i < left.size(); i++) {
			if (!ItemStack.matches(left.get(i), right.get(i))) {
				return false;
			}
		}
		return true;
	}

	private static void draw(
		GuiGraphicsExtractor graphics,
		Screen screen,
		Page page,
		int index,
		int mouseX,
		int mouseY
	) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		boolean empty = page == null || page.empty();
		int start = page == null ? 9 : Math.min(9, page.items.size());
		int count = page == null ? 9 : Math.max(9, page.items.size() - start);
		int rows = empty ? 1 : Math.max(1, Mth.ceil(count / (float) COLS));
		float gridW = COLS * SLOT + (COLS - 1) * GAP;
		float panelW = PAD * 2 + gridW;
		float panelH = PAD + HEAD + rows * (SLOT + GAP) - GAP + PAD;
		if (empty) {
			panelH += 12f;
		}
		float x = mouseX + 8f;
		if (x + panelW > screen.width - 4f) {
			x = mouseX - panelW - 12f;
		}
		x = Mth.clamp(x, 4f, Math.max(4f, screen.width - panelW - 4f));
		float y = Mth.clamp(mouseY - 16f, 4f, Math.max(4f, screen.height - panelH - 4f));

		GuiDraw.panel(graphics, x, y, panelW, panelH, 6, Theme.HUD_WINDOW, Theme.HUD_LINE, Theme.ACCENT);
		String label = displayName(page == null ? "" : page.name, index);
		GuiDraw.small(graphics, font, label, x + PAD, y + PAD - 1, Theme.ACCENT);
		if (empty) {
			GuiDraw.small(graphics, font, "Open this page once", x + PAD, y + PAD + HEAD, Theme.MUTED);
			return;
		}
		String countLabel = filled(page, start) + "/" + count;
		GuiDraw.small(
			graphics,
			font,
			countLabel,
			x + panelW - PAD - GuiDraw.smallWidth(font, countLabel),
			y + PAD - 1,
			Theme.MUTED
		);

		for (int i = 0; i < rows * COLS; i++) {
			int col = i % COLS;
			int row = i / COLS;
			float sx = x + PAD + col * (SLOT + GAP);
			float sy = y + PAD + HEAD + row * (SLOT + GAP);
			ItemStack stack = i < count ? page.items.get(start + i) : ItemStack.EMPTY;
			GuiDraw.well(graphics, sx, sy, SLOT, Theme.HUD_TRACK, Theme.HUD_LINE);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			int ix = Math.round(sx + 1f);
			int iy = Math.round(sy + 1f);
			if (client.player == null) {
				graphics.item(stack, ix, iy);
			} else {
				graphics.item(client.player, stack, ix, iy, 200 + i);
			}
			graphics.itemDecorations(font, stack, ix, iy);
		}
	}

	private static int filled(Page page, int start) {
		int n = 0;
		for (int i = start; i < page.items.size(); i++) {
			ItemStack stack = page.items.get(i);
			if (stack != null && !stack.isEmpty()) {
				n++;
			}
		}
		return n;
	}

	private static String displayName(String stored, int index) {
		if (stored != null && !stored.isBlank()) {
			return strip(stored);
		}
		if (index < 9) {
			return "Ender Chest " + (index + 1);
		}
		return "Backpack " + (index - 8);
	}

	private static int pageOf(Slot slot) {
		int fromItem = pageFromStack(slot.getItem());
		if (fromItem >= 0) {
			return fromItem;
		}
		int fromIndex = pageFromSlot(slot.getContainerSlot());
		if (fromIndex >= 0) {
			return fromIndex;
		}
		return pageFromSlot(slot.index);
	}

	private static int pageFromStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return -1;
		}
		int fromName = pageFromText(stack.getHoverName().getString());
		if (fromName >= 0) {
			return fromName;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return -1;
		}
		for (Component line : lore.lines()) {
			int page = pageFromText(line.getString());
			if (page >= 0) {
				return page;
			}
		}
		for (Component line : lore.styledLines()) {
			int page = pageFromText(line.getString());
			if (page >= 0) {
				return page;
			}
		}
		return -1;
	}

	private static int pageFromText(String raw) {
		String text = strip(raw);
		if (text.isEmpty()) {
			return -1;
		}
		Matcher echest = ECHEST.matcher(text);
		if (echest.find()) {
			return parsePage(echest.group(1), 1, 9) - 1;
		}
		Matcher backpack = BACKPACK.matcher(text);
		if (backpack.find()) {
			String group = backpack.group(1) != null ? backpack.group(1) : backpack.group(2);
			int slot = parsePage(group, 1, 18);
			return slot < 0 ? -1 : slot + 8;
		}
		return -1;
	}

	private static int pageFromSlot(int slot) {
		if (slot >= 9 && slot < 18) {
			return slot - 9;
		}
		if (slot >= 27 && slot < 45) {
			return slot - 18;
		}
		return -1;
	}

	private static int parsePage(String raw, int min, int max) {
		if (raw == null || raw.isBlank()) {
			return -1;
		}
		try {
			int value = Integer.parseInt(raw.trim());
			return value < min || value > max ? -1 : value;
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	private static boolean storageMenu(String title) {
		String lower = strip(title).toLowerCase(Locale.ROOT);
		return lower.equals("storage") || lower.startsWith("storage ") || lower.endsWith(" storage");
	}

	private static String title(Screen screen) {
		Component title = screen.getTitle();
		return title == null ? "" : title.getString();
	}

	private static String strip(String raw) {
		return raw == null ? "" : raw.replaceAll("§.", "").replaceAll("[\\p{C}]", "").trim();
	}

	private static String profileKey(Minecraft client) {
		if (client.getUser() == null || client.getUser().getProfileId() == null) {
			return "";
		}
		String uuid = client.getUser().getProfileId().toString().replace("-", "");
		String profile = profileName(client);
		if (profile.isEmpty()) {
			profile = lastProfile;
		} else {
			lastProfile = profile;
		}
		if (profile.isEmpty()) {
			profile = "unknown";
		}
		return uuid + "/" + sanitize(profile);
	}

	private static String profileName(Minecraft client) {
		ClientPacketListener connection = client.getConnection();
		if (connection == null) {
			return "";
		}
		for (PlayerInfo info : connection.getListedOnlinePlayers()) {
			Component display = info.getTabListDisplayName();
			if (display == null) {
				continue;
			}
			Matcher matcher = PROFILE.matcher(strip(display.getString()));
			if (matcher.matches()) {
				return matcher.group(1).trim();
			}
		}
		return "";
	}

	private static String sanitize(String raw) {
		StringBuilder out = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			out.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
		}
		String key = out.toString().toLowerCase(Locale.ROOT);
		return key.isBlank() ? "unknown" : key;
	}

	private static void load() {
		for (int i = 0; i < PAGES; i++) {
			pages[i] = null;
		}
		if (saveDir == null || !Files.isDirectory(saveDir)) {
			return;
		}
		RegistryOps<Tag> ops = ops();
		if (ops == null) {
			return;
		}
		for (int i = 0; i < PAGES; i++) {
			Path file = saveDir.resolve(i + ".nbt");
			if (!Files.isRegularFile(file)) {
				continue;
			}
			try {
				CompoundTag tag = NbtIo.read(file);
				if (tag == null) {
					continue;
				}
				Tag items = tag.get("items");
				if (items == null) {
					continue;
				}
				List<ItemStack> stacks = ITEMS.parse(ops, items).result().orElse(List.of());
				if (stacks.isEmpty()) {
					continue;
				}
				pages[i] = new Page(tag.getStringOr("name", ""), List.copyOf(stacks));
			} catch (Exception exception) {
				Stray.LOGGER.warn("Could not read storage preview {}", file.getFileName(), exception);
			}
		}
		dirty = false;
	}

	private static void flush() {
		if (!dirty || saveDir == null) {
			return;
		}
		RegistryOps<Tag> ops = ops();
		if (ops == null) {
			return;
		}
		try {
			Files.createDirectories(saveDir);
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not create storage preview folder", exception);
			return;
		}
		for (int i = 0; i < PAGES; i++) {
			Page page = pages[i];
			if (page == null) {
				continue;
			}
			try {
				Tag items = ITEMS.encodeStart(ops, page.items).result().orElse(null);
				if (items == null) {
					continue;
				}
				CompoundTag tag = new CompoundTag();
				tag.putString("name", page.name);
				tag.put("items", items);
				NbtIo.write(tag, saveDir.resolve(i + ".nbt"));
			} catch (Exception exception) {
				Stray.LOGGER.warn("Could not write storage preview {}", i, exception);
			}
		}
		dirty = false;
	}

	private static RegistryOps<Tag> ops() {
		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			return client.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		}
		ClientPacketListener connection = client.getConnection();
		if (connection != null) {
			return connection.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		}
		return null;
	}

	private record Page(String name, List<ItemStack> items) {
		private boolean empty() {
			if (items == null || items.isEmpty()) {
				return true;
			}
			for (ItemStack stack : items) {
				if (stack != null && !stack.isEmpty()) {
					return false;
				}
			}
			return true;
		}
	}
}
