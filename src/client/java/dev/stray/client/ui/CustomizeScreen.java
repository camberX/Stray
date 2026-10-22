package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.custom.CustomItems;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Item customization editor for the held item: name, dye color, armor trim,
 * head skin and glint, with a live preview. Changes apply as you type and
 * are saved when the screen closes. Layout and options follow SkyOcean's
 * customization (https://github.com/meowdding/SkyOcean, MIT).
 */
public final class CustomizeScreen extends Screen {
	private static final int W = 360;
	private static final int H = 236;
	private static final int ROW = 24;
	private static final String[] COLOR_MODES = {"None", "Hex", "Gradient", "Dye", "Animated"};
	private static final String[] SKIN_MODES = {"None", "Animated", "SkyBlock", "Texture"};
	private static final String[] GLINT = {"Default", "On", "Off"};

	private final ItemStack stack;
	private final CustomItems.Data data;
	private final boolean head;
	private final boolean shared;
	private final List<String> patterns = new ArrayList<>();
	private final List<String> materials = new ArrayList<>();
	private final List<Button> suggestions = new ArrayList<>();

	private int left;
	private int top;
	private int colorMode;
	private int skinMode;
	private int patternIndex;
	private int materialIndex;
	private EditBox nameBox;
	private EditBox colorBox;
	private EditBox skinBox;
	private String suggestFor = "";

	public CustomizeScreen(ItemStack stack) {
		super(Component.literal("Customize item"));
		this.stack = stack;
		this.data = CustomItems.getOrCreate(stack);
		this.head = stack.is(Items.PLAYER_HEAD);
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
		int x = left + 120;
		int fieldW = W - 120 - 12;
		int y = top + 28;

		nameBox = new EditBox(font, x + 52, y, fieldW - 52, 18, Component.literal("Name"));
		nameBox.setMaxLength(128);
		nameBox.setHint(Component.literal("Use & for colors"));
		nameBox.setValue(data.name == null ? "" : data.name);
		nameBox.setResponder(value -> data.name = value.isBlank() ? null : value);
		addRenderableWidget(nameBox);
		y += ROW;

		addRenderableWidget(Button.builder(Component.literal(COLOR_MODES[colorMode]), button -> {
			colorMode = (colorMode + 1) % COLOR_MODES.length;
			button.setMessage(Component.literal(COLOR_MODES[colorMode]));
			colorBox.setValue("");
			applyColor("");
			refreshSuggestions("color", "");
		}).bounds(x + 52, y, 70, 18).build());
		colorBox = new EditBox(font, x + 126, y, fieldW - 126, 18, Component.literal("Color"));
		colorBox.setMaxLength(256);
		colorBox.setValue(colorText());
		colorBox.setResponder(value -> {
			applyColor(value);
			refreshSuggestions("color", value);
		});
		addRenderableWidget(colorBox);
		y += ROW;

		addRenderableWidget(Button.builder(Component.literal(patternLabel()), button -> {
			patternIndex = (patternIndex + 1) % (patterns.size() + 1);
			button.setMessage(Component.literal(patternLabel()));
			applyTrim();
		}).bounds(x + 52, y, (fieldW - 56) / 2, 18).build());
		addRenderableWidget(Button.builder(Component.literal(materialLabel()), button -> {
			materialIndex = materials.isEmpty() ? 0 : (materialIndex + 1) % materials.size();
			button.setMessage(Component.literal(materialLabel()));
			applyTrim();
		}).bounds(x + 52 + (fieldW - 56) / 2 + 4, y, (fieldW - 56) / 2, 18).build());
		y += ROW;

		if (head) {
			addRenderableWidget(Button.builder(Component.literal(SKIN_MODES[skinMode]), button -> {
				skinMode = (skinMode + 1) % SKIN_MODES.length;
				button.setMessage(Component.literal(SKIN_MODES[skinMode]));
				skinBox.setValue("");
				applySkin("");
				refreshSuggestions("skin", "");
			}).bounds(x + 52, y, 70, 18).build());
			skinBox = new EditBox(font, x + 126, y, fieldW - 126, 18, Component.literal("Skin"));
			skinBox.setMaxLength(32767);
			skinBox.setValue(data.skin == null || data.skin.value == null ? "" : data.skin.value);
			skinBox.setResponder(value -> {
				applySkin(value);
				refreshSuggestions("skin", value);
			});
			addRenderableWidget(skinBox);
			y += ROW;
		}

		addRenderableWidget(Button.builder(Component.literal(glintLabel()), button -> {
			data.glint = data.glint == null ? Boolean.TRUE : data.glint ? Boolean.FALSE : null;
			button.setMessage(Component.literal(glintLabel()));
		}).bounds(x + 52, y, 70, 18).build());

		int bottom = top + H - 26;
		addRenderableWidget(Button.builder(Component.literal("Reset all"), button -> {
			CustomItems.reset(stack);
			onClose();
		}).bounds(left + 12, bottom, 96, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
			.bounds(left + W - 12 - 96, bottom, 96, 18).build());

		refreshSuggestions(suggestFor, suggestFor.equals("skin") && skinBox != null ? skinBox.getValue() : colorBox.getValue());
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
			case 3 -> data.color = CustomItems.staticDye(value) == null ? null : CustomItems.ColorSpec.dye(value, false);
			case 4 -> data.color = CustomItems.animatedDyePreview(value) == null ? null : CustomItems.ColorSpec.dye(value, true);
			default -> data.color = null;
		}
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
		String type = switch (skinMode) {
			case 1 -> "animated";
			case 2 -> "skyblock";
			case 3 -> "texture";
			default -> null;
		};
		boolean valid = type != null && !value.isEmpty()
			&& (!"animated".equals(type) || CustomItems.animatedSkulls().contains(value.toUpperCase(Locale.ROOT)));
		data.skin = valid ? CustomItems.SkinSpec.of(type, "texture".equals(type) ? value : value.toUpperCase(Locale.ROOT)) : null;
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
			case 3, 4 -> color.id == null ? "" : color.id;
			default -> "";
		};
	}

	// --------------------------------------------------------- suggestions

	/** Up to four matching dye or skull ids as buttons under the fields. */
	private void refreshSuggestions(String field, String typed) {
		for (Button button : suggestions) {
			removeWidget(button);
		}
		suggestions.clear();
		suggestFor = field;
		List<String> pool;
		if ("color".equals(field) && colorMode == 3) {
			pool = CustomItems.staticDyes();
		} else if ("color".equals(field) && colorMode == 4) {
			pool = CustomItems.animatedDyes();
		} else if ("skin".equals(field) && skinMode == 1) {
			pool = CustomItems.animatedSkulls();
		} else {
			return;
		}
		String needle = typed == null ? "" : typed.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		List<String> hits = new ArrayList<>();
		for (String id : pool) {
			if (id.equalsIgnoreCase(needle)) {
				return;
			}
			if (needle.isEmpty() || id.contains(needle)) {
				hits.add(id);
			}
			if (hits.size() >= 4) {
				break;
			}
		}
		int x = left + 120;
		int w = (W - 120 - 12 - 12) / 4;
		int y = top + H - 52;
		for (int i = 0; i < hits.size(); i++) {
			String id = hits.get(i);
			Button button = Button.builder(Component.literal(pretty(id)), b -> {
				EditBox box = "skin".equals(field) ? skinBox : colorBox;
				box.setValue(id);
			}).bounds(x + i * (w + 4), y, w, 18).build();
			suggestions.add(button);
			addRenderableWidget(button);
		}
	}

	private static String pretty(String id) {
		String shown = id.startsWith("DYE_") ? id.substring(4) : id;
		shown = shown.replace('_', ' ').toLowerCase(Locale.ROOT);
		return shown.length() > 14 ? shown.substring(0, 13) + "." : shown;
	}

	private String patternLabel() {
		return patternIndex == 0 ? "No trim" : shortId(patterns.get(patternIndex - 1));
	}

	private String materialLabel() {
		return materials.isEmpty() ? "-" : shortId(materials.get(materialIndex));
	}

	private String glintLabel() {
		return data.glint == null ? GLINT[0] : data.glint ? GLINT[1] : GLINT[2];
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
		label(graphics, "Customize", left + 12, top + 10, hypr ? MaterialPalette.primary : Theme.ACCENT, hypr);

		// preview
		int px = left + 20;
		int py = top + 44;
		GuiDraw.rounded(graphics, px - 6, py - 6, 76, 76, 8f, hypr ? MaterialPalette.withAlpha(MaterialPalette.surfaceHigh, 0xC0) : Theme.CARD);
		graphics.pose().pushMatrix();
		graphics.pose().translate(px, py);
		graphics.pose().scale(4f, 4f);
		graphics.item(stack, 0, 0);
		graphics.pose().popMatrix();
		Component name = stack.getStyledHoverName();
		graphics.text(font, name, left + 12, py + 78, ink, true);
		if (shared) {
			label(graphics, "Applies to every", left + 12, py + 92, muted, hypr);
			label(graphics, "item with this id", left + 12, py + 102, muted, hypr);
		}

		int x = left + 120;
		int y = top + 33;
		label(graphics, "Name", x, y, muted, hypr);
		y += ROW;
		label(graphics, "Color", x, y, muted, hypr);
		Integer swatch = CustomItems.color(stack, data);
		if (swatch != null) {
			GuiDraw.rounded(graphics, x + 34, y - 1, 12, 12, 3f, 0xFF000000 | swatch);
		}
		y += ROW;
		label(graphics, "Trim", x, y, muted, hypr);
		y += ROW;
		if (head) {
			label(graphics, "Skin", x, y, muted, hypr);
			y += ROW;
		}
		label(graphics, "Glint", x, y, muted, hypr);
		label(graphics, hint(), x, top + H - 64, muted, hypr);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private String hint() {
		if (colorMode == 2 && colorBox != null && colorBox.isFocused()) {
			return "Two or more hex colors, then ticks per loop";
		}
		if (!suggestions.isEmpty()) {
			return "Pick one or keep typing";
		}
		return "";
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
