package dev.stray.client.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Space Roof's nebula, worked out once instead of every pixel every frame: the sky's slow
 * clouds (the expensive noise) by view direction, stored in an octahedral sky texture (the
 * sphere folded onto a square, so finding a direction's pixel needs no trigonometry). Same noise as the shader, without its very slow drift. The stars and their
 * twinkle stay live in the shader. Built on a background thread; the sky draws live until then.
 *
 * <p>Per pixel: red = the main cloud (over 2), green = the second colour's cloud (over 0.8),
 * blue = the milky band.
 */
final class NebulaCache {
	static final Identifier TEXTURE = Stray.id("space_nebula");
	private static final int W = 1536;
	private static final int H = 1536;

	private static volatile int[] pixels;
	private static boolean started;
	private static boolean uploaded;

	private NebulaCache() {
	}

	/** True once the texture is on the GPU; starts building it on first call. */
	static boolean ready() {
		if (uploaded) {
			return true;
		}
		if (!started) {
			started = true;
			Thread thread = new Thread(NebulaCache::build, "Stray space nebula");
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			thread.start();
		}
		int[] done = pixels;
		if (done == null) {
			return false;
		}
		try {
			NativeImage image = new NativeImage(W, H, false);
			for (int y = 0; y < H; y++) {
				for (int x = 0; x < W; x++) {
					image.setPixel(x, y, done[y * W + x]);
				}
			}
			DynamicTexture texture = new DynamicTexture(() -> "stray-space-nebula", image);
			Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
			texture.upload();
			uploaded = true;
			pixels = null;
		} catch (RuntimeException e) {
			Stray.LOGGER.warn("Space roof: nebula texture failed, the sky stays live", e);
			pixels = null;
		}
		return uploaded;
	}

	private static void build() {
		try {
			int[] out = new int[W * H];
			float bx = 0.35f;
			float by = 0.15f;
			float bz = 1.0f;
			float bl = (float) Math.sqrt(bx * bx + by * by + bz * bz);
			bx /= bl;
			by /= bl;
			bz /= bl;
			for (int y = 0; y < H; y++) {
				float py = (y + 0.5f) / H * 2f - 1f;
				for (int x = 0; x < W; x++) {
					// Octahedral: the square back to a direction (as octUv in space_roof.fsh, reversed;
					// world y is the octahedron's up).
					float px = (x + 0.5f) / W * 2f - 1f;
					float ox = px;
					float oy = py;
					float oz = 1f - Math.abs(px) - Math.abs(py);
					if (oz < 0f) {
						ox = (1f - Math.abs(py)) * (px >= 0f ? 1f : -1f);
						oy = (1f - Math.abs(px)) * (py >= 0f ? 1f : -1f);
					}
					float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
					float dx = ox / len;
					float dz = oy / len;
					float dy = oz / len;
					// As spaceColor in space_roof.fsh, at time 0.
					float b = dx * bx + dy * by + dz * bz;
					float band = (float) Math.exp(-Math.pow(b * 3.2f, 2.0));
					float qx = dx * 2.1f;
					float qy = dy * 2.1f;
					float qz = dz * 2.1f;
					float warp = fbm(qx * 1.6f + 4f, qy * 1.6f + 4f, qz * 1.6f + 4f, 3);
					float n = fbm(qx + warp * 0.9f, qy + warp * 0.9f, qz + warp * 0.9f, 4);
					float m = fbm(qx * 2.7f + 11f - warp * 0.5f, qy * 2.7f + 11f - warp * 0.5f, qz * 2.7f + 11f - warp * 0.5f, 3);
					float main = (float) Math.pow(n, 2.6) * (0.9f + 1.1f * band);
					float second = (float) Math.pow(m, 4.0) * 0.8f;
					int r = Math.round(Math.clamp(main / 2f, 0f, 1f) * 255f);
					int g = Math.round(Math.clamp(second / 0.8f, 0f, 1f) * 255f);
					int bl8 = Math.round(Math.clamp(band, 0f, 1f) * 255f);
					out[y * W + x] = 0xFF000000 | r << 16 | g << 8 | bl8;
				}
			}
			pixels = out;
		} catch (RuntimeException e) {
			Stray.LOGGER.warn("Space roof: building the nebula failed, the sky stays live", e);
		}
	}

	// The shader's noise, in Java (same constants and order).

	private static float fract(float v) {
		return v - (float) Math.floor(v);
	}

	private static float hash13(float x, float y, float z) {
		x = fract(x * 0.1031f);
		y = fract(y * 0.1031f);
		z = fract(z * 0.1031f);
		float d = x * (z + 31.32f) + y * (y + 31.32f) + z * (x + 31.32f);
		x += d;
		y += d;
		z += d;
		return fract((x + y) * z);
	}

	private static float valueNoise(float px, float py, float pz) {
		float ix = (float) Math.floor(px);
		float iy = (float) Math.floor(py);
		float iz = (float) Math.floor(pz);
		float fx = px - ix;
		float fy = py - iy;
		float fz = pz - iz;
		fx = fx * fx * (3f - 2f * fx);
		fy = fy * fy * (3f - 2f * fy);
		fz = fz * fz * (3f - 2f * fz);
		float a = hash13(ix, iy, iz);
		float b = hash13(ix + 1, iy, iz);
		float c = hash13(ix, iy + 1, iz);
		float d = hash13(ix + 1, iy + 1, iz);
		float e = hash13(ix, iy, iz + 1);
		float g = hash13(ix + 1, iy, iz + 1);
		float h = hash13(ix, iy + 1, iz + 1);
		float k = hash13(ix + 1, iy + 1, iz + 1);
		float x1 = mix(mix(a, b, fx), mix(c, d, fx), fy);
		float x2 = mix(mix(e, g, fx), mix(h, k, fx), fy);
		return mix(x1, x2, fz);
	}

	private static float mix(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float fbm(float x, float y, float z, int octaves) {
		float v = 0f;
		float a = 0.5f;
		for (int i = 0; i < octaves; i++) {
			v += a * valueNoise(x, y, z);
			x = x * 2.03f + 1.7f;
			y = y * 2.03f + 9.2f;
			z = z * 2.03f + 3.1f;
			a *= 0.5f;
		}
		return v;
	}
}
