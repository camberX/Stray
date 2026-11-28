package dev.stray.client.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Space cracks' pattern, worked out once instead of a 27-cell search every pixel every frame:
 * a tiling texture of the distance to the nearest crack line (the border between random cells,
 * second-nearest minus nearest point), read by each face's own coordinates. 18 cells across,
 * which at the shader's scale is about 16 blocks before it repeats. Stored as a distance so it
 * stays sharp when filtered. Built on a background thread; the shader works it out live until then.
 */
final class CrackCache {
	static final Identifier TEXTURE = Stray.id("space_cracks");
	/** Cells across the texture (it tiles), and the edge value stored (0..MAX_EDGE). */
	static final int CELLS = 18;
	private static final float MAX_EDGE = 0.5f;
	private static final int SIZE = 1024;

	private static volatile int[] pixels;
	private static boolean started;
	private static boolean uploaded;

	private CrackCache() {
	}

	/** True once the texture is on the GPU; starts building it on first call. */
	static boolean ready() {
		if (uploaded) {
			return true;
		}
		if (!started) {
			started = true;
			Thread thread = new Thread(CrackCache::build, "Stray space cracks");
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			thread.start();
		}
		int[] done = pixels;
		if (done == null) {
			return false;
		}
		try {
			NativeImage image = new NativeImage(SIZE, SIZE, false);
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					image.setPixel(x, y, done[y * SIZE + x]);
				}
			}
			DynamicTexture texture = new DynamicTexture(() -> "stray-space-cracks", image);
			Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
			texture.upload();
			uploaded = true;
			pixels = null;
		} catch (RuntimeException e) {
			Stray.LOGGER.warn("Space roof: crack texture failed, cracks stay live", e);
			pixels = null;
		}
		return uploaded;
	}

	private static void build() {
		try {
			// One random point per cell, wrapping round so the texture tiles.
			float[] px = new float[CELLS * CELLS];
			float[] py = new float[CELLS * CELLS];
			java.util.Random random = new java.util.Random(0x5EED_C2AC4L);
			for (int i = 0; i < CELLS * CELLS; i++) {
				px[i] = random.nextFloat();
				py[i] = random.nextFloat();
			}
			int[] out = new int[SIZE * SIZE];
			for (int y = 0; y < SIZE; y++) {
				float fy = (y + 0.5f) / SIZE * CELLS;
				int cy = (int) Math.floor(fy);
				for (int x = 0; x < SIZE; x++) {
					float fx = (x + 0.5f) / SIZE * CELLS;
					int cx = (int) Math.floor(fx);
					float d1 = 64f;
					float d2 = 64f;
					for (int oy = -1; oy <= 1; oy++) {
						for (int ox = -1; ox <= 1; ox++) {
							int nx = Math.floorMod(cx + ox, CELLS);
							int ny = Math.floorMod(cy + oy, CELLS);
							float ax = cx + ox + px[ny * CELLS + nx] - fx;
							float ay = cy + oy + py[ny * CELLS + nx] - fy;
							float d = ax * ax + ay * ay;
							if (d < d1) {
								d2 = d1;
								d1 = d;
							} else if (d < d2) {
								d2 = d;
							}
						}
					}
					float edge = (float) (Math.sqrt(d2) - Math.sqrt(d1));
					int v = Math.round(Math.clamp(edge / MAX_EDGE, 0f, 1f) * 255f);
					out[y * SIZE + x] = 0xFF000000 | v << 16 | v << 8 | v;
				}
			}
			pixels = out;
		} catch (RuntimeException e) {
			Stray.LOGGER.warn("Space roof: building the crack pattern failed, cracks stay live", e);
		}
	}
}
