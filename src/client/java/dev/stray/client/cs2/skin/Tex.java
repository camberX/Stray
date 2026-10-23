package dev.stray.client.cs2.skin;

/** Float RGBA image with bilinear sampling (wrapping or clamped). */
public final class Tex {
	public final int w;
	public final int h;
	public final float[] px;

	public Tex(int w, int h, float[] px) {
		this.w = w;
		this.h = h;
		this.px = px;
	}

	public void sample(float u, float v, boolean wrap, float[] out) {
		float x = u * w - 0.5f;
		float y = v * h - 0.5f;
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		float fx = x - x0;
		float fy = y - y0;
		int x1 = x0 + 1;
		int y1 = y0 + 1;
		if (wrap) {
			x0 = Math.floorMod(x0, w);
			x1 = Math.floorMod(x1, w);
			y0 = Math.floorMod(y0, h);
			y1 = Math.floorMod(y1, h);
		} else {
			x0 = Math.max(0, Math.min(w - 1, x0));
			x1 = Math.max(0, Math.min(w - 1, x1));
			y0 = Math.max(0, Math.min(h - 1, y0));
			y1 = Math.max(0, Math.min(h - 1, y1));
		}
		int a = (y0 * w + x0) * 4;
		int b = (y0 * w + x1) * 4;
		int c = (y1 * w + x0) * 4;
		int d = (y1 * w + x1) * 4;
		for (int k = 0; k < 4; k++) {
			float top = px[a + k] + (px[b + k] - px[a + k]) * fx;
			float bot = px[c + k] + (px[d + k] - px[c + k]) * fx;
			out[k] = top + (bot - top) * fy;
		}
	}
}
