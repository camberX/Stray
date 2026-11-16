package dev.stray.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.visual.CapeCrop;
import dev.stray.client.visual.CustomCape;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Crop a photo onto the 10×16 cape face. Drag pans the window; scroll zooms. */
public class CapeCreatorScreen extends SubScreen {
	private static final Identifier SOURCE_ID = Stray.id("cape_edit");
	private static final float STAGE_H = 176f;
	private static final float PREVIEW_W = 66f;
	private static final float PREVIEW_H = 106f;

	private final byte[] imageBytes;
	private CapeCrop crop;
	private int srcW;
	private int srcH;
	private float imgX;
	private float imgY;
	private float imgW;
	private float imgH;
	private boolean panning;
	private boolean ready;

	public CapeCreatorScreen(Screen parent, byte[] imageBytes) {
		super("Cape creator", parent, 380);
		this.imageBytes = imageBytes;
	}

	@Override
	protected void init() {
		super.init();
		if (ready) {
			return;
		}
		try {
			NativeImage source = NativeImage.read(imageBytes);
			srcW = source.getWidth();
			srcH = source.getHeight();
			NativeImage copy = new NativeImage(srcW, srcH, false);
			copy.copyFrom(source);
			source.close();
			minecraft.getTextureManager().register(SOURCE_ID, new DynamicTexture(() -> "stray-cape-edit", copy));
			crop = CapeCrop.cover(srcW, srcH);
			ready = true;
		} catch (Exception exception) {
			Stray.LOGGER.warn("Cape creator: could not open the image", exception);
			if (minecraft.player != null) {
				minecraft.player.sendSystemMessage(Component.literal("§bStray §8» §7Cape creator: couldn't open that image."));
			}
			onClose();
		}
	}

	@Override
	public void onClose() {
		ready = false;
		super.onClose();
	}

	@Override
	protected String subtitle() {
		return "crop an image onto your cape";
	}

	@Override
	protected float body(float x, float y, float w) {
		if (!ready || crop == null) {
			return y;
		}
		float gap = 14f;
		float stageW = w - PREVIEW_W - gap;
		GuiDraw.roundedFine(graphics, x, y, stageW, STAGE_H, ShadowLook.CONTROL_R, 0x0AFFFFFF);
		layoutImage(x, y, stageW);
		GuiDraw.blit(graphics, SOURCE_ID, imgX, imgY, imgW, imgH, 0f, 0f, srcW, srcH, srcW, srcH);
		float cx = imgX + crop.x * imgW;
		float cy = imgY + crop.y * imgH;
		float cw = crop.w * imgW;
		float ch = crop.h * imgH;
		int shade = 0xA0000000;
		GuiDraw.fill(graphics, imgX, imgY, imgW, Math.max(0f, cy - imgY), shade);
		GuiDraw.fill(graphics, imgX, cy + ch, imgW, Math.max(0f, imgY + imgH - cy - ch), shade);
		GuiDraw.fill(graphics, imgX, cy, Math.max(0f, cx - imgX), ch, shade);
		GuiDraw.fill(graphics, cx + cw, cy, Math.max(0f, imgX + imgW - cx - cw), ch, shade);
		GuiDraw.border(graphics, cx, cy, cw, ch, 0xE6FFFFFF, 1f);

		// The face as it will look.
		float px = x + w - PREVIEW_W;
		section("Face", px, y);
		float py = y + 16f;
		GuiDraw.roundedFine(graphics, px, py, PREVIEW_W, PREVIEW_H, ShadowLook.CONTROL_R, ShadowLook.CARD);
		int ru = Mth.clamp(Math.round(crop.x * srcW), 0, srcW - 1);
		int rv = Mth.clamp(Math.round(crop.y * srcH), 0, srcH - 1);
		int rw = Math.min(Math.max(1, Math.round(crop.w * srcW)), srcW - ru);
		int rh = Math.min(Math.max(1, Math.round(crop.h * srcH)), srcH - rv);
		GuiDraw.blit(graphics, SOURCE_ID, px + 4f, py + 4f, PREVIEW_W - 8f, PREVIEW_H - 8f, ru, rv, rw, rh, srcW, srcH);

		y = hint("Drag to pan · scroll to zoom · Enter applies", x, y + STAGE_H + GAP, w, false) + 6f;
		return buttons(x, y, w,
			new Object[][] {{"Reset", Style.SECONDARY, (Runnable) () -> crop = CapeCrop.cover(srcW, srcH)}},
			new Object[][] {{"Cancel", Style.SECONDARY, (Runnable) this::onClose}, {"Apply cape", Style.PRIMARY, (Runnable) this::submit}});
	}

	private void layoutImage(float x, float y, float stageW) {
		float fit = Math.min((stageW - 8f) / srcW, (STAGE_H - 8f) / srcH);
		imgW = srcW * fit;
		imgH = srcH * fit;
		imgX = x + (stageW - imgW) * 0.5f;
		imgY = y + (STAGE_H - imgH) * 0.5f;
	}

	@Override
	protected void submit() {
		if (crop == null) {
			return;
		}
		CustomCape.applyCrop(imageBytes, new CapeCrop(crop.x, crop.y, crop.w, crop.h));
		onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (super.mouseClicked(event, doubled)) {
			return true;
		}
		if (ready && MouseButtons.left(event) && GuiDraw.hovered(event.x(), event.y(), imgX, imgY, imgW, imgH)) {
			panning = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (MouseButtons.left(event) && panning && crop != null && imgW > 1f && imgH > 1f) {
			crop.pan((float) (dx / imgW), (float) (dy / imgH), srcW, srcH);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		panning = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!ready || crop == null || scrollY == 0 || !GuiDraw.hovered(mouseX, mouseY, imgX, imgY, imgW, imgH)) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		float px = Mth.clamp((float) ((mouseX - imgX) / imgW), 0f, 1f);
		float py = Mth.clamp((float) ((mouseY - imgY) / imgH), 0f, 1f);
		crop.zoom(scrollY > 0 ? 0.88f : 1.14f, px, py, srcW, srcH);
		return true;
	}
}
