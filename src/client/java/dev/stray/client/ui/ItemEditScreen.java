package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.item.ItemIds;
import dev.stray.client.item.SkyblockItems;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** Swap how the held item looks, on this client only. Tab or Enter takes the first suggestion. */
public class ItemEditScreen extends SubScreen {
	private static final float PREVIEW = 56f;
	private static final int SUGGESTIONS = 6;

	private final ItemStack original;
	private final String originalId;
	private final Input query = input("item-edit", 128, "minecraft: or sb:");
	private ItemIds.Preview preview = ItemIds.Preview.empty();
	private List<String> suggestions = List.of();
	private String status = "";

	public ItemEditScreen() {
		super("Item", null, 300);
		Minecraft client = Minecraft.getInstance();
		ItemStack held = client.player == null ? ItemStack.EMPTY : ItemIds.held(client.player);
		this.original = held.isEmpty() ? ItemStack.EMPTY : held.copy();
		this.originalId = ItemIds.idOf(this.original);
		String shown = ItemAppearance.displayId(held);
		query.set(shown == null || shown.isBlank() ? originalId : shown);
	}

	@Override
	protected void init() {
		super.init();
		if (minecraft != null && minecraft.player != null) {
			ItemIds.rememberInventory(minecraft.player);
		}
		refresh();
	}

	@Override
	protected String subtitle() {
		return "held item look · your client only";
	}

	@Override
	protected float body(float x, float y, float w) {
		refresh();
		// The look, big, with its name and where it comes from beside it.
		GuiDraw.roundedFine(graphics, x, y, PREVIEW, PREVIEW, ShadowLook.CONTROL_R, ShadowLook.CARD);
		ItemStack stack = previewStack();
		if (!stack.isEmpty()) {
			float scale = 2.5f;
			float size = 16f * scale;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + (PREVIEW - size) * 0.5f, y + (PREVIEW - size) * 0.5f);
			graphics.pose().scale(scale, scale);
			graphics.item(stack, 0, 0);
			graphics.pose().popMatrix();
		} else {
			String mark = preview.kind() == ItemIds.Kind.UNKNOWN ? "?" : "—";
			text(mark, x + (PREVIEW - textWidth(mark)) * 0.5f, y, PREVIEW, FAINT);
		}
		float tx = x + PREVIEW + 12f;
		float tw = x + w - tx;
		boolean unknown = preview.kind() == ItemIds.Kind.UNKNOWN;
		String title = preview.title() == null || preview.title().isBlank() ? (unknown ? "Unknown item" : "Nothing") : preview.title();
		text(fit(title, tw), tx, y + 10f, 14f, unknown ? DANGER : TEXT);
		String kind = switch (preview.kind()) {
			case VANILLA -> "Vanilla";
			case SKYBLOCK -> "Skyblock";
			case UNKNOWN -> "Unknown";
			case EMPTY -> "Empty";
		};
		tag(kind, tx, y + 30f, 16f, preview.kind() == ItemIds.Kind.SKYBLOCK ? TEXT : MUTED);
		y += PREVIEW + GAP + 2f;

		y = field(query, "Look", labelColumn("Look"), x, y, w);
		if (!suggestions.isEmpty()) {
			y += GAP;
			for (String id : suggestions) {
				boolean hot = hovered(x, y, w, 18f);
				card(x, y, w, 18f, hot, false);
				String name = suggestionName(id);
				float nx = x + 8f;
				if (name != null) {
					text(fit(name, w * 0.55f), nx, y, 18f, hot ? TEXT : MUTED);
					nx += Math.min(textWidth(name), w * 0.55f) + 8f;
				}
				GuiDraw.small(graphics, font, dev.stray.client.util.TextFit.small(font, id, (int) (x + w - 8f - nx)), nx, smallMiddle(y, 18f), FAINT);
				hit(x, y, w, 18f, () -> apply(id));
				y += 21f;
			}
			y -= 3f;
		}
		String help = !status.isBlank() ? status
			: suggestions.isEmpty() && query.get().isBlank() ? "Type Hyperion, sb:HYPERION or minecraft:stone"
			: "Tab takes the first suggestion · Esc closes";
		y = hint(help, x, y + GAP + 2f, w, false) + 6f;
		return buttons(x, y, w,
			new Object[][] {{"Reset", Style.SECONDARY, (Runnable) () -> {
				status = "";
				apply(originalId);
			}}},
			new Object[][] {{"Done", Style.PRIMARY, (Runnable) this::onClose}});
	}

	private static String suggestionName(String id) {
		if (id != null && id.toLowerCase(Locale.ROOT).startsWith("sb:")) {
			SkyblockItems.Entry entry = SkyblockItems.get(id.substring(3));
			if (entry != null && entry.name() != null && !entry.name().isBlank()) {
				return entry.name();
			}
		}
		return null;
	}

	private ItemStack previewStack() {
		if (!original.isEmpty() && query.get().trim().equalsIgnoreCase(originalId)) {
			return original;
		}
		return preview.stack();
	}

	private void refresh() {
		String value = query.get();
		preview = ItemIds.resolve(value);
		String trimmed = value.trim();
		boolean exact = preview.kind() != ItemIds.Kind.UNKNOWN
			&& !trimmed.isEmpty()
			&& (trimmed.equalsIgnoreCase(preview.canonical())
				|| trimmed.equalsIgnoreCase(originalId)
				|| (preview.kind() == ItemIds.Kind.SKYBLOCK && trimmed.equalsIgnoreCase(preview.title())));
		suggestions = exact ? List.of() : ItemIds.suggest(value, SUGGESTIONS);
		syncHand();
	}

	private void syncHand() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		ItemStack live = ItemIds.held(client.player);
		if (live.isEmpty() || preview.kind() == ItemIds.Kind.UNKNOWN) {
			return;
		}
		if (preview.kind() == ItemIds.Kind.EMPTY) {
			ItemAppearance.revertModel(client.player, live);
			return;
		}
		boolean sameItem = preview.canonical().equalsIgnoreCase(originalId)
			|| query.get().trim().equalsIgnoreCase(originalId);
		if (sameItem) {
			ItemAppearance.revertModel(client.player, live);
			return;
		}
		if (preview.kind() == ItemIds.Kind.VANILLA || preview.kind() == ItemIds.Kind.SKYBLOCK) {
			ItemAppearance.set(client.player, live, preview.stack(), preview.canonical());
			status = "Showing " + preview.title();
		}
	}

	private void apply(String id) {
		query.set(id);
		query.focus();
		refresh();
	}

	@Override
	protected void submit() {
		if (!suggestions.isEmpty()) {
			apply(suggestions.getFirst());
		} else {
			onClose();
		}
	}

	@Override
	protected boolean keyFirst(KeyEvent event) {
		if (event.key() == InputConstants.KEY_TAB && !suggestions.isEmpty()) {
			apply(suggestions.getFirst());
			return true;
		}
		return false;
	}
}
