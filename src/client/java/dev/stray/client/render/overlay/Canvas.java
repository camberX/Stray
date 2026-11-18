package dev.stray.client.render.overlay;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * 2D drawing that works both in the game's own HUD and in the streamproof overlay
 * window, so one renderer (the dungeon map, star mob boxes) can target either.
 * Coordinates are GUI units.
 */
public interface Canvas {
	void push();

	void pop();

	void translate(float x, float y);

	void scale(float s);

	void rotate(float radians);

	void fill(float x, float y, float w, float h, int argb);

	/** A region of a texture (in texture pixels) stretched over the rect. */
	void texture(Identifier id, float x, float y, float w, float h, float u, float v, float regionW, float regionH, float texW, float texH, int tint);

	void line(float x0, float y0, float x1, float y1, float width, int argb);

	/** Text with § colour codes; the given colour is the start colour. */
	void text(String text, float x, float y, int argb, boolean shadow);

	float width(String text);

	default float lineHeight() {
		return 9f;
	}

	default void border(float x, float y, float w, float h, int argb, float t) {
		fill(x - t, y - t, w + t * 2, t, argb);
		fill(x - t, y + h, w + t * 2, t, argb);
		fill(x - t, y, t, h, argb);
		fill(x + w, y, t, h, argb);
	}

	default void centeredText(String text, float cx, float y, int argb, float scale) {
		push();
		translate(cx, y);
		scale(scale);
		text(text, -width(text) / 2f, 0f, argb, true);
		pop();
	}

	default void head(PlayerSkin skin, float x, float y, float size) {
		if (skin == null) {
			fill(x, y, size, size, 0xFF555555);
			return;
		}
		Identifier id = skin.body().texturePath();
		texture(id, x, y, size, size, 8, 8, 8, 8, 64, 64, -1);
		texture(id, x, y, size, size, 40, 8, 8, 8, 64, 64, -1);
	}
}
