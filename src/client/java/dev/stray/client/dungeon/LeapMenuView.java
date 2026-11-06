package dev.stray.client.dungeon;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.PovCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.util.Mth;

/**
 * The look of the leap menu: the screen split into four panels, one per teammate,
 * with a head, name and class card in each. With POV on, the panel's background is
 * what that teammate sees right now. Which panel is hovered depends only on which
 * quarter of the window the cursor is in, same as the original grid menu.
 */
public final class LeapMenuView {
	private static final float[] HOVER = new float[4];
	private static long lastNs;

	private LeapMenuView() {
	}

	/** Same rule as the original grid: the window quarter the cursor is in. */
	public static Integer hoveredIndex() {
		Minecraft client = Minecraft.getInstance();
		double cx = client.getWindow().getScreenWidth() / 2.0;
		double cy = client.getWindow().getScreenHeight() / 2.0;
		double mx = client.mouseHandler.xpos();
		double my = client.mouseHandler.ypos();
		if (mx < cx && my < cy) {
			return 0;
		}
		if (mx > cx && my < cy) {
			return 1;
		}
		if (mx < cx && my > cy) {
			return 2;
		}
		if (mx > cx && my > cy) {
			return 3;
		}
		return null;
	}

	public static void render(GuiGraphicsExtractor graphics, int width, int height) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0.016f : Mth.clamp((now - lastNs) / 1e9f, 0.001f, 0.05f);
		lastNs = now;
		LeapMenu.Entry[] players = LeapMenu.players();
		boolean any = false;
		for (LeapMenu.Entry entry : players) {
			any |= entry != null;
		}
		GuiDraw.fill(graphics, 0, 0, width, height, 0xB0050709);
		if (!any) {
			String text = "No players found";
			GuiDraw.text(graphics, font, text, (width - font.width(text) * 2f) * 0.5f, height * 0.5f - 8, 2f, 0xFFFF5555, false);
			F7Titles.renderOverMenu(graphics, width, height);
			return;
		}
		Integer hovered = hoveredIndex();
		float gap = 3f;
		float halfW = width * 0.5f;
		float halfH = height * 0.5f;
		boolean pov = PovCapture.wanted();
		for (int i = 0; i < 4; i++) {
			float target = hovered != null && hovered == i && players[i] != null ? 1f : 0f;
			HOVER[i] += (target - HOVER[i]) * (1f - (float) Math.exp(-16f * dt));
			float x = (i % 2) * halfW + (i % 2 == 0 ? 0 : gap * 0.5f);
			float y = (i / 2) * halfH + (i / 2 == 0 ? 0 : gap * 0.5f);
			float w = halfW - gap * 0.5f;
			float h = halfH - gap * 0.5f;
			panel(graphics, font, i, players[i], x, y, w, h, HOVER[i], pov);
		}
		// Titles (ours and the game's) would sit under the menu; draw them on top.
		F7Titles.renderOverMenu(graphics, width, height);
	}

	private static void panel(
		GuiGraphicsExtractor graphics,
		Font font,
		int index,
		LeapMenu.Entry entry,
		float x,
		float y,
		float w,
		float h,
		float hover,
		boolean pov
	) {
		if (entry == null) {
			GuiDraw.fill(graphics, x, y, w, h, 0x60000000);
			return;
		}
		DungeonParty.Member player = entry.player();
		int classColor = player.clazz.color;
		StrayConfig config = StrayConfig.get();
		GpuTextureView view = pov ? PovCapture.view(index) : null;
		if (view != null) {
			GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(w, h);
			graphics.blit(view, sampler, 0, 0, 1, 1, 0f, 1f, 1f, 0f);
			graphics.pose().popMatrix();
			// Keeps the card readable over the picture; lifts away while hovered.
			GuiDraw.fill(graphics, x, y, w, h, alpha(0x000000, 0.30f - 0.22f * hover));
		} else {
			GuiDraw.fillGradient(graphics, x, y, w, h, alpha(classColor, 0.10f + 0.10f * hover), 0x40000000);
		}
		boolean dead = player.dead;
		boolean opener = config.leapShowDoorOpener && DungeonParty.lastDoorOpener() == player;
		if (dead && config.leapTintDead) {
			GuiDraw.fill(graphics, x, y, w, h, 0x38FF2020);
		}
		// Edge: a thin class-coloured line, brighter and a pixel wider while hovered.
		// The sides stop where the top and bottom start, so the corners are not
		// drawn twice (which made them brighter than the rest of the line).
		float px = GuiDraw.pixel();
		float edge = px * (hover > 0.5f ? 2f : 1f);
		int edgeColor = alpha(classColor, 0.45f + 0.5f * hover);
		GuiDraw.fill(graphics, x, y, w, edge, edgeColor);
		GuiDraw.fill(graphics, x, y + h - edge, w, edge, edgeColor);
		GuiDraw.fill(graphics, x, y + edge, edge, h - edge * 2f, edgeColor);
		GuiDraw.fill(graphics, x + w - edge, y + edge, edge, h - edge * 2f, edgeColor);

		// Card at the inner corner of each quarter, so the four sit around the centre.
		float scale = Mth.clamp(config.leapScale / 100f, 0.5f, 1.5f) * (1f + 0.04f * hover);
		float cardW = 170f * scale;
		float cardH = 52f * scale;
		float margin = 18f;
		float cx = index % 2 == 0 ? x + w - cardW - margin : x + margin;
		float cy = index / 2 == 0 ? y + h - cardH - margin : y + margin;
		GuiDraw.roundedFine(graphics, cx, cy, cardW, cardH, 12f * scale, alpha(0x0A0D12, 0.78f + 0.12f * hover));
		GuiDraw.hairline(graphics, cx, cy, cardW, cardH, 12f * scale, alpha(classColor, 0.25f + 0.55f * hover));
		float head = cardH - 14f * scale;
		float hx = cx + 7f * scale;
		float hy = cy + 7f * scale;
		if (player.skin != null) {
			PlayerFaceExtractor.extractRenderState(graphics, player.skin, Math.round(hx), Math.round(hy), Math.round(head));
		}
		if (dead) {
			GuiDraw.fill(graphics, hx, hy, head, head, 0x88550000);
		}
		float tx = hx + head + 8f * scale;
		float ts = 1.25f * scale;
		GuiDraw.text(graphics, font, player.name, tx, cy + 9f * scale, ts, 0xFFF2F4F7, false);
		String status = dead ? "DEAD" : player.clazz.label + (player.clazzLevel > 0 ? " " + player.clazzLevel : "");
		GuiDraw.text(graphics, font, status, tx, cy + 9f * scale + 12f * ts, 0.95f * scale, dead ? 0xFFFF5555 : classColor, false);
		if (opener) {
			String tag = "LAST DOOR";
			float tw = font.width(tag) * 0.7f * scale + 8f;
			GuiDraw.roundedFine(graphics, cx + cardW - tw - 6f, cy + 6f, tw, 9f * scale, 4f, 0xCCFFFFFF);
			GuiDraw.text(graphics, font, tag, cx + cardW - tw - 2f, cy + 7.5f, 0.7f * scale, 0xFF111111, false);
		}
		if (config.leapKeybinds) {
			String key = String.valueOf(index + 1);
			float kw = 12f * scale;
			float kx = cx + cardW - kw - 6f;
			float ky = cy + cardH - kw - 6f;
			GuiDraw.roundedFine(graphics, kx, ky, kw, kw, 3f, 0x22FFFFFF);
			GuiDraw.text(graphics, font, key, kx + (kw - font.width(key) * 0.8f * scale) * 0.5f, ky + (kw - 8f * 0.8f * scale) * 0.5f, 0.8f * scale, 0xFFC9D0D8, false);
		}
	}

	private static int alpha(int rgb, float a) {
		int value = Math.round(Mth.clamp(a, 0f, 1f) * 255f);
		return (value << 24) | (rgb & 0xFFFFFF);
	}
}
