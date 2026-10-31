package dev.stray.client.editor;

import dev.stray.client.movement.Freecam;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.ui.ClickLook;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/** Freecam speed chip, and the editor workspace while it is not taking input (looking around, chat). */
public final class EditorHud {
	private EditorHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		Font font = client.font;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (!WorldEditor.active()) {
			if (Freecam.active()) {
				String text = "Freecam · " + String.format(Locale.ROOT, "%.2f", Freecam.speed()) + " · scroll changes speed";
				float w = GuiDraw.smallWidth(font, text) + 14;
				ClickLook.outside(graphics, (width - w) / 2f, 6, w, 14, 6, Theme.HUD_WINDOW, Theme.HUD_LINE);
				GuiDraw.small(graphics, font, text, (width - w) / 2f + 7, 9, Theme.MUTED);
			}
			return;
		}
		WorldEditor.frame(client);
		if (!(client.gui.screen() instanceof EditorScreen)) {
			// Looking around (right mouse) or chat open: keep the workspace on screen.
			EditorScreen.get().drawStatic(graphics);
		}
	}

	/** Item shown for a block (buckets for fluids, a barrier for "air"). */
	public static ItemStack icon(BlockState state) {
		if (state == null || state.isAir()) {
			return new ItemStack(Items.BARRIER);
		}
		if (state.is(Blocks.WATER)) {
			return new ItemStack(Items.WATER_BUCKET);
		}
		if (state.is(Blocks.LAVA)) {
			return new ItemStack(Items.LAVA_BUCKET);
		}
		var item = state.getBlock().asItem();
		return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
	}

	public static String label(BlockState state) {
		if (state == null || state.isAir()) {
			return "Air (eraser)";
		}
		return WorldEditor.name(state);
	}
}
