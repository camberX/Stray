package dev.stray.client.render.overlay;

import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** {@link Canvas} on the game's own GUI (shows up in recordings). */
public final class GuiCanvas implements Canvas {
	private final GuiGraphicsExtractor graphics;
	private final Font font;

	public GuiCanvas(GuiGraphicsExtractor graphics, Font font) {
		this.graphics = graphics;
		this.font = font;
	}

	@Override
	public void push() {
		graphics.pose().pushMatrix();
	}

	@Override
	public void pop() {
		graphics.pose().popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		graphics.pose().translate(x, y);
	}

	@Override
	public void scale(float s) {
		graphics.pose().scale(s, s);
	}

	@Override
	public void rotate(float radians) {
		graphics.pose().rotate(radians);
	}

	@Override
	public void fill(float x, float y, float w, float h, int argb) {
		GuiDraw.fillSmooth(graphics, x, y, w, h, argb);
	}

	@Override
	public void texture(Identifier id, float x, float y, float w, float h, float u, float v, float regionW, float regionH, float texW, float texH, int tint) {
		GuiDraw.blit(graphics, id, x, y, w, h, u, v, Math.round(regionW), Math.round(regionH), Math.round(texW), Math.round(texH), tint);
	}

	@Override
	public void line(float x0, float y0, float x1, float y1, float width, int argb) {
		GuiDraw.stroke(graphics, x0, y0, x1, y1, width, argb);
	}

	@Override
	public void text(String text, float x, float y, int argb, boolean shadow) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.text(font, text, 0, 0, argb, shadow);
		graphics.pose().popMatrix();
	}

	@Override
	public void panel(float x, float y, float w, float h, float radius, int fill, int edge) {
		dev.stray.client.ui.ShadowLook.panel(graphics, x, y, w, h, radius, fill);
	}

	@Override
	public float width(String text) {
		return font.width(text);
	}
}
