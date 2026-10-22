package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.custom.CustomItems;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Item customization editor for the held item. Every item gets a name, a
 * model (another Minecraft item's texture) and glint; dyeable armor gets a
 * color, trimmable armor a trim, heads a skin. Dyes, skulls and models are
 * picked from a scrolling list with previews. Changes apply live and save
 * on close. Options follow SkyOcean's customization
 * (https://github.com/meowdding/SkyOcean, MIT).
 */
public final class CustomizeScreen extends Screen {
	private static final int W = 390;
	private static final int H = 272;
	private static final int ROW = 22;
	private static final int SIDE = 128;
	private static final int ITEM_H = 14;
	private static final String[] COLOR_MODES = {"None", "Hex", "Gradient", "Dye", "Animated"};
	private static final String[] SKIN_MODES = {"None", "Animated", "SkyBlock", "Texture"};

	private enum ListKind { NONE, DYE, ANIMATED_DYE, SKULL, MODEL }

	private final ItemStack stack;
	private final CustomItems.Data data;
	private final boolean head;
	private final boolean dyeable;
	private final boolean trimmable;
	private final EquipmentSlot slot;
	private final boolean shared;
	private final List<String> patterns = new ArrayList<>();
	private final List<String> materials = new ArrayList<>();
	private final Map<String, ItemStack> icons = new HashMap<>();
	private ArmorStand stand;

	private int left;
	private int top;
	private int colorMode;
	private int skinMode;
	private int patternIndex;
	private int materialIndex;
	private EditBox nameBox;
	private EditBox modelBox;
	private EditBox colorBox;
	private EditBox skinBox;

	private ListKind listKind = ListKind.NONE;
	private List<String> listItems = List.of();
	private float listScroll;
	private int listX;
	private int listY;
	private int listW;
	private int listH;

	public CustomizeScreen(ItemStack stack) {
		super(Component.literal("Customize item"));
		this.stack = stack;
		this.data = CustomItems.getOrCreate(stack);
		this.head = stack.is(Items.PLAYER_HEAD);
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		EquipmentSlot equipSlot = equippable == null ? null : equippable.slot();
		boolean armorSlot = equipSlot != null && equipSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
		this.slot = armorSlot ? equipSlot : null;
		this.dyeable = armorSlot && !head && stack.is(ItemTags.DYEABLE);
		this.trimmable = armorSlot && !head && stack.is(ItemTags.TRIMMABLE_ARMOR);
		this.shared = CustomItems.sharedKey(CustomItems.keyOf(stack));
		CustomItems.ColorSpec color = data.color;
		if (color != null) {
			colorMode = switch (color.type == null ? "" : color.type) {
				case "static" -> 1;
				case "gradient" -> 2;
				case "dye" -> 3;
				case "animated" -> 4;
				default -> 0;
			};
		}
		if (data.skin != null) {
			skinMode = switch (data.skin.type == null ? "" : data.skin.type) {
				case "animated" -> 1;
				case "skyblock" -> 2;
				case "texture" -> 3;
				default -> 0;
			};
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			var access = client.level.registryAccess();
			access.lookupOrThrow(Registries.TRIM_PATTERN).listElementIds()
				.map(key -> key.identifier().toString()).sorted().forEach(patterns::add);
			access.lookupOrThrow(Registries.TRIM_MATERIAL).listElementIds()
				.map(key -> key.identifier().toString()).sorted().forEach(materials::add);
			if (slot != null) {
				// Item icons draw one generic trim overlay for every pattern, so
				// show the armor worn on a stand where patterns and dyes are real.
				stand = new ArmorStand(EntityType.ARMOR_STAND, client.level);
				stand.setSilent(true);
				stand.setItemSlot(slot, stack.copy());
			}
		}
		patternIndex = data.trimPattern == null ? 0 : patterns.indexOf(data.trimPattern) + 1;
		materialIndex = data.trimMaterial == null ? 0 : Math.max(0, materials.indexOf(data.trimMaterial));
	}

	/** Opens the editor for the held item, or explains why it can't. */
	public static int openForHeld() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0;
		}
		ItemStack held = client.player.getMainHandItem();
		if (held.isEmpty()) {
			say("Hold an item to customize it.");
			return 0;
		}
		if (CustomItems.keyOf(held) == null) {
			say("This item can't be customized (it has no SkyBlock id).");
			return 0;
		}
		if (CustomItems.sharedKey(CustomItems.keyOf(held))) {
			say("This item has no UUID, so changes apply to every item with the same id.");
		}
		client.execute(() -> client.setScreen(new CustomizeScreen(held)));
		return 1;
	}

	public static int resetHeld() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0;
		}
		ItemStack held = client.player.getMainHandItem();
		say(CustomItems.reset(held) ? "Removed this item's customization." : "This item isn't customized.");
		return 1;
	}

	private static void say(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("§bStray §8» §7" + message));
		}
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = (height - H) / 2;
		int x = left + SIDE;
		int fieldX = x + 48;
		int fieldW = W - SIDE - 12 - 48;
		int y = top + 26;

		nameBox = box(fieldX, y, fieldW, "Name", "Use & for colors", data.name == null ? "" : data.name);
		nameBox.setResponder(value -> data.name = value.isBlank() ? null : value);
		y += ROW;

		modelBox = box(fieldX, y, fieldW, "Model", "Minecraft id, e.g. diamond_sword", data.model == null ? "" : data.model);
		modelBox.setResponder(value -> {
			data.model = CustomItems.item(value) != null ? value.trim().toLowerCase(Locale.ROOT) : null;
			showList(ListKind.MODEL, value);
		});
		y += ROW;

		if (dyeable) {
			addRenderableWidget(Button.builder(Component.literal(COLOR_MODES[colorMode]), button -> {
				colorMode = (colorMode + 1) % COLOR_MODES.length;
				button.setMessage(Component.literal(COLOR_MODES[colorMode]));
				colorBox.setValue("");
				applyColor("");
				colorBox.setHint(Component.literal(colorHint()));
				showList(colorMode == 3 ? ListKind.DYE : colorMode == 4 ? ListKind.ANIMATED_DYE : ListKind.NONE, "");
			}).bounds(fieldX, y, 70, 18).build());
			colorBox = box(fieldX + 74, y, fieldW - 74, "Color", colorHint(), colorText());
			colorBox.setResponder(value -> {
				applyColor(value);
				if (colorMode == 3 || colorMode == 4) {
					showList(colorMode == 3 ? ListKind.DYE : ListKind.ANIMATED_DYE, value);
				}
			});
			y += ROW;
		}

		if (trimmable) {
			int half = (fieldW - 4) / 2;
			addRenderableWidget(Button.builder(Component.literal(patternLabel()), button -> {
				patternIndex = (patternIndex + 1) % (patterns.size() + 1);
				button.setMessage(Component.literal(patternLabel()));
				applyTrim();
			}).bounds(fieldX, y, half, 18).build());
			addRenderableWidget(Button.builder(Component.literal(materialLabel()), button -> {
				materialIndex = materials.isEmpty() ? 0 : (materialIndex + 1) % materials.size();
				button.setMessage(Component.literal(materialLabel()));
				applyTrim();
			}).bounds(fieldX + half + 4, y, half, 18).build());
			y += ROW;
		}

		if (head) {
			addRenderableWidget(Button.builder(Component.literal(SKIN_MODES[skinMode]), button -> {
				skinMode = (skinMode + 1) % SKIN_MODES.length;
				button.setMessage(Component.literal(SKIN_MODES[skinMode]));
				skinBox.setValue("");
				applySkin("");
				showList(skinMode == 1 ? ListKind.SKULL : ListKind.NONE, "");
			}).bounds(fieldX, y, 70, 18).build());
			skinBox = box(fieldX + 74, y, fieldW - 74, "Skin", "Search skulls", data.skin == null || data.skin.value == null ? "" : data.skin.value);
			skinBox.setMaxLength(32767);
			skinBox.setResponder(value -> {
				applySkin(value);
				if (skinMode == 1) {
					showList(ListKind.SKULL, value);
				}
			});
			y += ROW;
		}

		addRenderableWidget(Button.builder(Component.literal(glintLabel()), button -> {
			data.glint = data.glint == null ? Boolean.TRUE : data.glint ? Boolean.FALSE : null;
			button.setMessage(Component.literal(glintLabel()));
		}).bounds(fieldX, y, 70, 18).build());
		y += ROW + 4;

		listX = x;
		listY = y;
		listW = W - SIDE - 12;
		listH = top + H - 32 - y;

		int bottom = top + H - 26;
		addRenderableWidget(Button.builder(Component.literal("Reset all"), button -> {
			CustomItems.reset(stack);
			onClose();
		}).bounds(left + 12, bottom, 96, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
			.bounds(left + W - 12 - 96, bottom, 96, 18).build());

		if (listKind == ListKind.NONE) {
			if (dyeable && (colorMode == 3 || colorMode == 4)) {
				showList(colorMode == 3 ? ListKind.DYE : ListKind.ANIMATED_DYE, "");
			} else if (head && skinMode == 1) {
				showList(ListKind.SKULL, "");
			}
		}
	}

	private EditBox box(int x, int y, int w, String name, String hint, String value) {
		EditBox box = new EditBox(font, x, y, w, 18, Component.literal(name));
		box.setMaxLength(256);
		box.setHint(Component.literal(hint));
		box.setValue(value);
		addRenderableWidget(box);
		return box;
	}

	// ------------------------------------------------------------ applying

	private void applyColor(String raw) {
		String value = raw == null ? "" : raw.trim();
		switch (colorMode) {
			case 1 -> {
				Integer rgb = CustomItems.hex(value);
				data.color = rgb == null ? null : CustomItems.ColorSpec.hex(rgb);
			}
			case 2 -> {
				List<Integer> colors = new ArrayList<>();
				int time = 40;
				for (String part : value.split("[,\\s|]+")) {
					if (part.isEmpty()) {
						continue;
					}
					Integer rgb = CustomItems.hex(part);
					if (rgb != null) {
						colors.add(rgb);
					} else {
						try {
							time = Math.max(2, Integer.parseInt(part));
						} catch (NumberFormatException ignored) {
						}
					}
				}
				data.color = colors.size() < 2 ? null : CustomItems.ColorSpec.gradient(colors, time);
			}
			case 3, 4 -> {
				// The list sets the dye; typing only filters it.
			}
			default -> data.color = null;
		}
	}

	private void pickDye(String id, boolean animated) {
		data.color = CustomItems.ColorSpec.dye(id, animated);
	}

	private void applyTrim() {
		if (patternIndex == 0 || materials.isEmpty()) {
			data.trimPattern = null;
			data.trimMaterial = null;
			return;
		}
		data.trimPattern = patterns.get(patternIndex - 1);
		data.trimMaterial = materials.get(materialIndex);
	}

	private void applySkin(String raw) {
		String value = raw == null ? "" : raw.trim();
		switch (skinMode) {
			case 2 -> data.skin = value.isEmpty() ? null : CustomItems.SkinSpec.of("skyblock", value.toUpperCase(Locale.ROOT));
			case 3 -> data.skin = value.isEmpty() ? null : CustomItems.SkinSpec.of("texture", value);
			case 1 -> {
				// Picked from the list.
			}
			default -> data.skin = null;
		}
	}

	private String colorHint() {
		return switch (colorMode) {
			case 1 -> "#RRGGBB";
			case 2 -> "#a #b #c, then ticks per loop";
			case 3, 4 -> "Search dyes";
			default -> "";
		};
	}

	private String colorText() {
		CustomItems.ColorSpec color = data.color;
		if (color == null) {
			return "";
		}
		return switch (colorMode) {
			case 1 -> String.format(Locale.ROOT, "#%06X", color.rgb);
			case 2 -> {
				StringBuilder out = new StringBuilder();
				for (int rgb : color.colors) {
					out.append(String.format(Locale.ROOT, "#%06X ", rgb));
				}
				yield out.append(color.time).toString();
			}
			default -> "";
		};
	}

	// ----------------------------------------------------------------- list

	private void showList(ListKind kind, String typed) {
		listKind = kind;
		listScroll = 0f;
		List<String> pool = switch (kind) {
			case DYE -> CustomItems.staticDyes();
			case ANIMATED_DYE -> CustomItems.animatedDyes();
			case SKULL -> CustomItems.animatedSkulls();
			case MODEL -> CustomItems.itemIds();
			default -> List.of();
		};
		String needle = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		if (kind == ListKind.MODEL && (needle.isEmpty() || CustomItems.item(needle) != null && pool.contains(withNamespace(needle)))) {
			listKind = needle.isEmpty() ? ListKind.NONE : listKind;
		}
		List<String> hits = new ArrayList<>();
		for (String id : pool) {
			String hay = id.toLowerCase(Locale.ROOT);
			if (needle.isEmpty() || hay.contains(needle) || CustomItems.displayName(id).toLowerCase(Locale.ROOT).contains(needle.replace('_', ' '))) {
				hits.add(id);
			}
		}
		listItems = hits;
	}

	private static String withNamespace(String id) {
		return id.contains(":") ? id : "minecraft:" + id;
	}

	private int columns() {
		return listKind == ListKind.MODEL || listKind == ListKind.SKULL ? 2 : 3;
	}

	private boolean selected(String id) {
		return switch (listKind) {
			case DYE -> data.color != null && "dye".equals(data.color.type) && id.equals(data.color.id);
			case ANIMATED_DYE -> data.color != null && "animated".equals(data.color.type) && id.equals(data.color.id);
			case SKULL -> data.skin != null && "animated".equals(data.skin.type) && id.equals(data.skin.value);
			case MODEL -> data.model != null && withNamespace(data.model).equals(id);
			default -> false;
		};
	}

	private void pick(String id) {
		switch (listKind) {
			case DYE -> pickDye(id, false);
			case ANIMATED_DYE -> pickDye(id, true);
			case SKULL -> data.skin = CustomItems.SkinSpec.of("animated", id);
			case MODEL -> {
				data.model = id;
				modelBox.setValue(id);
			}
			default -> {
			}
		}
	}

	private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hypr) {
		if (listKind == ListKind.NONE || listH < ITEM_H) {
			return;
		}
		int cols = columns();
		int cellW = (listW - (cols - 1) * 3) / cols;
		int rows = (listItems.size() + cols - 1) / cols;
		float content = rows * (ITEM_H + 2f);
		listScroll = Math.max(0f, Math.min(listScroll, Math.max(0f, content - listH)));
		int track = hypr ? MaterialPalette.withAlpha(MaterialPalette.surfaceHigh, 0x90) : Theme.CARD;
		GuiDraw.rounded(graphics, listX - 3, listY - 3, listW + 6, listH + 6, 6f, track);
		if (listItems.isEmpty()) {
			label(graphics, "No matches", listX + 4, listY + 3, hypr ? MaterialPalette.onSurfaceVariant : Theme.MUTED, hypr);
			return;
		}
		boolean clip = GuiDraw.scissor(graphics, listX - 2, listY - 2, listW + 4, listH + 4);
		int ink = hypr ? MaterialPalette.onSurface : Theme.TEXT;
		int accent = hypr ? MaterialPalette.primary : Theme.ACCENT;
		for (int i = 0; i < listItems.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			float cx = listX + col * (cellW + 3);
			float cy = listY + row * (ITEM_H + 2f) - listScroll;
			if (cy + ITEM_H < listY || cy > listY + listH) {
				continue;
			}
			String id = listItems.get(i);
			boolean on = selected(id);
			boolean hot = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + ITEM_H
				&& mouseY >= listY && mouseY < listY + listH;
			if (on || hot) {
				GuiDraw.rounded(graphics, cx, cy, cellW, ITEM_H, 4f, on
					? (hypr ? MaterialPalette.secondaryContainer : Theme.withAlpha(Theme.ACCENT, 90))
					: (hypr ? MaterialPalette.withAlpha(MaterialPalette.onSurface, 0x18) : Theme.CARD_HOVER));
			}
			float tx = cx + 4;
			switch (listKind) {
				case DYE, ANIMATED_DYE -> {
					Integer rgb = listKind == ListKind.DYE ? CustomItems.staticDye(id) : CustomItems.animatedDyePreview(id);
					GuiDraw.rounded(graphics, cx + 3, cy + 2, 10, 10, 3f, 0xFF000000 | (rgb == null ? 0 : rgb));
					tx = cx + 17;
				}
				case SKULL, MODEL -> {
					ItemStack icon = icons.computeIfAbsent(listKind + id, k -> listKind == ListKind.SKULL ? CustomItems.skullIcon(id) : iconFor(id));
					graphics.pose().pushMatrix();
					graphics.pose().translate(cx + 2, cy + 1);
					graphics.pose().scale(0.75f, 0.75f);
					graphics.item(icon, 0, 0);
					graphics.pose().popMatrix();
					tx = cx + 17;
				}
				default -> {
				}
			}
			String name = listKind == ListKind.MODEL ? shortId(id) : CustomItems.displayName(id);
			label(graphics, fit(name, (int) (cx + cellW - tx - 2)), (int) tx, (int) (cy + 3), on ? accent : ink, hypr);
		}
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private static ItemStack iconFor(String id) {
		Item item = CustomItems.item(id);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	private String fit(String text, int max) {
		if (font.width(text) <= max) {
			return text;
		}
		String trimmed = text;
		while (trimmed.length() > 1 && font.width(trimmed + ".") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + ".";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0 && listKind != ListKind.NONE
			&& event.x() >= listX && event.x() < listX + listW && event.y() >= listY && event.y() < listY + listH) {
			int cols = columns();
			int cellW = (listW - (cols - 1) * 3) / cols;
			int col = (int) ((event.x() - listX) / (cellW + 3));
			int row = (int) ((event.y() - listY + listScroll) / (ITEM_H + 2f));
			int index = row * cols + Math.min(cols - 1, col);
			if (index >= 0 && index < listItems.size()) {
				pick(listItems.get(index));
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (listKind != ListKind.NONE && mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
			listScroll = Math.max(0f, listScroll - (float) scrollY * (ITEM_H + 2f) * 2f);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private String patternLabel() {
		return patternIndex == 0 ? "No trim" : CustomItems.displayName(patterns.get(patternIndex - 1));
	}

	private String materialLabel() {
		return materials.isEmpty() ? "-" : CustomItems.displayName(materials.get(materialIndex));
	}

	private String glintLabel() {
		return data.glint == null ? "Default" : data.glint ? "On" : "Off";
	}

	private static String shortId(String id) {
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}

	// ------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Theme.refresh();
		boolean hypr = StrayConfig.get().guiStyleHypr();
		if (hypr) {
			HyprMenu.window(graphics, left, top, W, H, 1f, 1f);
		} else {
			ClickLook.panel(graphics, left, top, W, H, 8f, Theme.WINDOW, Theme.LINE);
		}
		int ink = hypr ? MaterialPalette.onSurface : Theme.TEXT;
		int muted = hypr ? MaterialPalette.onSurfaceVariant : Theme.MUTED;
		label(graphics, "Customize", left + 12, top + 9, hypr ? MaterialPalette.primary : Theme.ACCENT, hypr);

		// preview: armor on a stand (shows trims and dyes), anything else as a big icon
		int px = left + 12;
		int py = top + 26;
		int pw = SIDE - 24;
		int ph = 128;
		GuiDraw.rounded(graphics, px, py, pw, ph, 8f, hypr ? MaterialPalette.withAlpha(MaterialPalette.surfaceHigh, 0xC0) : Theme.CARD);
		if (stand != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, px, py + 4, px + pw, py + ph - 4, 46, 0.0625f, mouseX, mouseY, stand);
		} else {
			graphics.pose().pushMatrix();
			graphics.pose().translate(px + (pw - 64) / 2f, py + (ph - 64) / 2f);
			graphics.pose().scale(4f, 4f);
			graphics.item(stack, 0, 0);
			graphics.pose().popMatrix();
		}
		graphics.text(font, Component.literal(fit(stack.getStyledHoverName().getString(), pw)).withStyle(stack.getStyledHoverName().getStyle()), px, py + ph + 6, ink, !hypr);
		if (shared) {
			label(graphics, "Applies to every", px, py + ph + 20, muted, hypr);
			label(graphics, "item with this id", px, py + ph + 30, muted, hypr);
		}

		int x = left + SIDE;
		int y = top + 31;
		label(graphics, "Name", x, y, muted, hypr);
		y += ROW;
		label(graphics, "Model", x, y, muted, hypr);
		y += ROW;
		if (dyeable) {
			label(graphics, "Color", x, y, muted, hypr);
			Integer swatch = CustomItems.color(stack, data);
			if (swatch != null) {
				GuiDraw.rounded(graphics, x + 32, y - 1, 10, 10, 3f, 0xFF000000 | swatch);
			}
			y += ROW;
		}
		if (trimmable) {
			label(graphics, "Trim", x, y, muted, hypr);
			y += ROW;
		}
		if (head) {
			label(graphics, "Skin", x, y, muted, hypr);
			y += ROW;
		}
		label(graphics, "Glint", x, y, muted, hypr);
		drawList(graphics, mouseX, mouseY, hypr);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void label(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean hypr) {
		if (text.isEmpty()) {
			return;
		}
		Component line = hypr ? MenuFont.hypr(text) : Component.literal(text);
		graphics.text(font, line, x, y, color, !hypr);
	}

	@Override
	public void onClose() {
		CustomItems.save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
