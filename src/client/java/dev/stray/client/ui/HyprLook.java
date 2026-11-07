package dev.stray.client.ui;

import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.GuiFrostBlur;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The Hyprland (illogical-impulse) look, still used by the Hyprland themed GUIs and chat:
 * a blurred Material window and the Material 3 switch.
 */
public final class HyprLook {
	/** ii rounds windows at 18px; at GUI scale 2 that is 9. */
	private static final float RADIUS = 9f;

	private HyprLook() {
	}

	/**
	 * One Hyprland window: blur, Material surface and a 1px border that is
	 * primary when focused and a third of that when not (ii's colors.lua).
	 */
	public static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float focus, float t) {
		window(graphics, x, y, w, h, focus, t, 0xE6);
	}

	/** Same window with a chosen surface opacity (chat uses a lighter one). */
	public static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float focus, float t, int fillAlpha) {
		MaterialPalette.refresh();
		GuiFrostBlur.blitWindow(graphics, x, y, w, h, RADIUS, t);
		GuiDraw.roundedFine(graphics, x, y, w, h, RADIUS, Anim.fade(withAlpha(MaterialPalette.surfaceLow, fillAlpha), t));
		// ii: active border is primary, inactive is primary at a third (colors.lua)
		int border = Anim.mix(withAlpha(MaterialPalette.primary, 0x40), MaterialPalette.primary, focus);
		GuiDraw.hairline(graphics, x, y, w, h, RADIUS, Anim.fade(border, t));
	}

	/**
	 * Material 3 switch. Off: outlined track, small outline-colored thumb.
	 * On: primary track, bigger on-primary thumb that grows as it slides.
	 * The outline is a filled pill under an inset pill, not a stroked ring,
	 * so its edge stays smooth at small sizes.
	 */
	public static void m3Switch(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float k, boolean hot, float t) {
		MaterialPalette.refresh();
		float half = h * 0.5f;
		int edge = Anim.mix(MaterialPalette.outline, MaterialPalette.primary, k);
		GuiDraw.roundedFine(graphics, x, y, w, h, half, Anim.fade(edge, t));
		float inset = 1f - k;
		if (inset > 0.02f) {
			float ih = h - inset * 2f;
			int inner = Anim.mix(MaterialPalette.surfaceHighest, MaterialPalette.primary, k);
			GuiDraw.roundedFine(graphics, x + inset, y + inset, w - inset * 2f, ih, ih * 0.5f, Anim.fade(inner, t));
		}
		float radius = h * (0.22f + 0.14f * k) + (hot ? 0.35f : 0f);
		float cx = x + half + k * (w - h);
		int thumb = Anim.mix(MaterialPalette.outline, MaterialPalette.onPrimary, k);
		GuiDraw.circle(graphics, cx, y + half, radius, Anim.fade(thumb, t));
	}

	private static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	// ------------------------------------------------------------- animation
}
