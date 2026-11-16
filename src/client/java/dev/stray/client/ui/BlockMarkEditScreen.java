package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.item.ItemIds;
import dev.stray.client.render.BlockMarks;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Name + item icon for one block mark. Enter saves, Esc cancels, Tab swaps fields. */
public class BlockMarkEditScreen extends SubScreen {
	private static final float PREVIEW = 46f;
	private static final int SUGGESTIONS = 4;

	private final BlockPos pos;
	private final Input name = input("block-mark-0", 32, "Unnamed");
	private final Input icon = input("block-mark-1", 128, "minecraft:diamond or sb:HYPERION");
	private ItemIds.Preview preview = ItemIds.Preview.empty();
	private List<String> suggestions = List.of();

	public BlockMarkEditScreen(BlockPos pos, String name, String icon) {
		super("Mark", null, 320);
		this.pos = pos;
		this.name.set(name == null ? "" : name);
		this.icon.set(icon == null ? "" : icon);
	}


	@Override
	protected float body(float x, float y, float w) {
		refresh();
		// The icon, big, beside the two fields.
		GuiDraw.roundedFine(graphics, x, y, PREVIEW, PREVIEW, ShadowLook.CONTROL_R, ShadowLook.CARD);
		ItemStack stack = preview.stack();
		if (!stack.isEmpty()) {
			float scale = 2f;
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
		float fx = x + PREVIEW + 10f;
		float fw = x + w - fx;
		float labelW = labelColumn("Name", "Icon");
		float fy = field(name, "Name", labelW, fx, y, fw) + (PREVIEW - FIELD * 2);
		y = Math.max(y + PREVIEW, field(icon, "Icon", labelW, fx, fy, fw));

		if (icon.focused() && !suggestions.isEmpty()) {
			y += GAP;
			for (String id : suggestions) {
				boolean hot = hovered(x, y, w, 18f);
				card(x, y, w, 18f, hot, false);
				text(fit(id, w - 16f), x + 8f, y, 18f, hot ? TEXT : MUTED);
				hit(x, y, w, 18f, () -> icon.set(id));
				y += 21f;
			}
			y -= 3f;
		}
		y += GAP + 8f;
		return buttons(x, y, w,
			new Object[][] {{"Delete", Style.DANGER, (Runnable) () -> {
				BlockMarks.remove(pos);
				onClose();
			}}},
			new Object[][] {{"Cancel", Style.SECONDARY, (Runnable) this::onClose}, {"Save", Style.PRIMARY, (Runnable) this::submit}});
	}

	private boolean canComplete() {
		return icon.focused() && !suggestions.isEmpty() && preview.kind() == ItemIds.Kind.UNKNOWN;
	}

	@Override
	protected void submit() {
		if (canComplete()) {
			icon.set(suggestions.getFirst());
			return;
		}
		BlockMarks.rename(pos, name.get(), icon.get());
		onClose();
	}

	@Override
	protected boolean keyFirst(KeyEvent event) {
		if (event.key() == InputConstants.KEY_TAB && canComplete()) {
			icon.set(suggestions.getFirst());
			return true;
		}
		return false;
	}

	private void refresh() {
		String value = icon.get();
		preview = value.isBlank() ? ItemIds.Preview.empty() : ItemIds.resolve(value);
		String trimmed = value.trim();
		boolean exact = preview.kind() != ItemIds.Kind.UNKNOWN
			&& !trimmed.isEmpty()
			&& (trimmed.equalsIgnoreCase(preview.canonical())
				|| (preview.kind() == ItemIds.Kind.SKYBLOCK && trimmed.equalsIgnoreCase(preview.title())));
		suggestions = exact || trimmed.isEmpty() ? List.of() : ItemIds.suggest(value, SUGGESTIONS);
	}
}
