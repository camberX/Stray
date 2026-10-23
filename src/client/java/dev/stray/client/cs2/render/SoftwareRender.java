package dev.stray.client.cs2.render;

import dev.stray.client.cs2.s2.S2Model;
import dev.stray.client.cs2.skin.Composer;

/**
 * Small CPU renderer for the knife picker's live preview: orthographic side view, the
 * baked finish, and a simple metal/paint shading close to the in-game shader.
 */
public final class SoftwareRender {
	private SoftwareRender() {
	}

	/** Returns ARGB pixels (transparent background). */
	public static int[] render(S2Model model, Composer.Result tex, int w, int h, float yawDegrees) {
		int[] out = new int[w * h];
		float[] depth = new float[w * h];
		java.util.Arrays.fill(depth, -Float.MAX_VALUE);
		float cy = (float) Math.cos(Math.toRadians(yawDegrees));
		float sy = (float) Math.sin(Math.toRadians(yawDegrees));
		float[] mn = {Float.MAX_VALUE, Float.MAX_VALUE};
		float[] mx = {-Float.MAX_VALUE, -Float.MAX_VALUE};
		for (S2Model.Part p : model.parts) {
			for (int i = 0; i < p.vertexCount; i++) {
				float sx = screenX(p.pos, i, cy, sy);
				float syy = p.pos[i * 3 + 2];
				mn[0] = Math.min(mn[0], sx);
				mx[0] = Math.max(mx[0], sx);
				mn[1] = Math.min(mn[1], syy);
				mx[1] = Math.max(mx[1], syy);
			}
		}
		float scale = Math.min((w - 16) / Math.max(1e-3f, mx[0] - mn[0]), (h - 16) / Math.max(1e-3f, mx[1] - mn[1]));
		float ox = (w - (mx[0] - mn[0]) * scale) / 2f;
		float oy = (h - (mx[1] - mn[1]) * scale) / 2f;
		float[] light = normalize(new float[]{0.35f, 0.55f, 0.75f});
		int n = tex.size;
		for (S2Model.Part p : model.parts) {
			int[] idx = p.indices;
			float[] X = new float[3];
			float[] Y = new float[3];
			float[] Z = new float[3];
			for (int t = 0; t + 2 < idx.length; t += 3) {
				for (int k = 0; k < 3; k++) {
					int i = idx[t + k];
					X[k] = ox + (screenX(p.pos, i, cy, sy) - mn[0]) * scale;
					Y[k] = h - oy - (p.pos[i * 3 + 2] - mn[1]) * scale;
					Z[k] = depthOf(p.pos, i, cy, sy);
				}
				float area = (X[1] - X[0]) * (Y[2] - Y[0]) - (X[2] - X[0]) * (Y[1] - Y[0]);
				if (Math.abs(area) < 1e-6f) {
					continue;
				}
				int x0 = (int) Math.max(0, Math.floor(Math.min(X[0], Math.min(X[1], X[2]))));
				int x1 = (int) Math.min(w - 1, Math.ceil(Math.max(X[0], Math.max(X[1], X[2]))));
				int y0 = (int) Math.max(0, Math.floor(Math.min(Y[0], Math.min(Y[1], Y[2]))));
				int y1 = (int) Math.min(h - 1, Math.ceil(Math.max(Y[0], Math.max(Y[1], Y[2]))));
				for (int y = y0; y <= y1; y++) {
					for (int x = x0; x <= x1; x++) {
						float px = x + 0.5f;
						float py = y + 0.5f;
						float w0 = ((X[1] - px) * (Y[2] - py) - (X[2] - px) * (Y[1] - py)) / area;
						float w1 = ((X[2] - px) * (Y[0] - py) - (X[0] - px) * (Y[2] - py)) / area;
						float w2 = 1f - w0 - w1;
						if (w0 < 0 || w1 < 0 || w2 < 0) {
							continue;
						}
						float z = w0 * Z[0] + w1 * Z[1] + w2 * Z[2];
						int pi = y * w + x;
						if (z <= depth[pi]) {
							continue;
						}
						depth[pi] = z;
						int a = idx[t];
						int b = idx[t + 1];
						int c = idx[t + 2];
						float u = w0 * p.uv[a * 2] + w1 * p.uv[b * 2] + w2 * p.uv[c * 2];
						float v = w0 * p.uv[a * 2 + 1] + w1 * p.uv[b * 2 + 1] + w2 * p.uv[c * 2 + 1];
						float[] nrm = new float[3];
						for (int q = 0; q < 3; q++) {
							nrm[q] = w0 * p.normal[a * 3 + q] + w1 * p.normal[b * 3 + q] + w2 * p.normal[c * 3 + q];
						}
						float[] vn = viewNormal(nrm, cy, sy);
						if (vn[2] < 0) {
							vn[0] = -vn[0];
							vn[1] = -vn[1];
							vn[2] = -vn[2];
						}
						int tx = Math.floorMod((int) (u * n), n);
						int ty = Math.floorMod((int) (v * n), n);
						int ti = ty * n + tx;
						out[pi] = shade(tex.albedo[ti], tex.material, ti, vn, light);
					}
				}
			}
		}
		return out;
	}

	private static float screenX(float[] pos, int i, float cy, float sy) {
		return pos[i * 3] * cy + pos[i * 3 + 1] * sy;
	}

	private static float depthOf(float[] pos, int i, float cy, float sy) {
		return -pos[i * 3] * sy + pos[i * 3 + 1] * cy;
	}

	private static float[] viewNormal(float[] n, float cy, float sy) {
		float x = n[0] * cy + n[1] * sy;
		float z = -n[0] * sy + n[1] * cy;
		return normalize(new float[]{x, n[2], z});
	}

	private static int shade(int argb, byte[] material, int ti, float[] n, float[] light) {
		float r = ((argb >> 16) & 255) / 255f;
		float g = ((argb >> 8) & 255) / 255f;
		float b = (argb & 255) / 255f;
		float rough = (material[ti * 4] & 255) / 255f;
		float metal = (material[ti * 4 + 1] & 255) / 255f;
		float ao = 0.55f + 0.45f * (material[ti * 4 + 2] & 255) / 255f;
		float ndl = Math.max(0f, n[0] * light[0] + n[1] * light[1] + n[2] * light[2]);
		// Reflection of a soft studio: brighter where the normal faces up.
		float env = 0.35f + 0.65f * Math.max(0f, n[1] * 0.6f + n[2] * 0.5f);
		float[] half = normalize(new float[]{light[0], light[1], light[2] + 1f});
		float spec = (float) Math.pow(Math.max(0f, n[0] * half[0] + n[1] * half[1] + n[2] * half[2]), 2 + (1 - rough) * (1 - rough) * 140) * (1 - rough) * 1.5f;
		float[] c = {r, g, b};
		int[] o = new int[3];
		for (int k = 0; k < 3; k++) {
			float f0 = 0.04f + (c[k] - 0.04f) * metal;
			float diff = c[k] * (1 - metal) * (0.3f + 0.8f * ndl);
			float refl = f0 * (env * (1.25f - rough) + spec + 0.2f);
			float v = (diff + refl) * ao;
			o[k] = Math.max(0, Math.min(255, (int) (Math.pow(v, 1 / 1.15) * 255)));
		}
		return 0xFF000000 | (o[0] << 16) | (o[1] << 8) | o[2];
	}

	private static float[] normalize(float[] a) {
		float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
		return l < 1e-9f ? new float[]{0, 0, 1} : new float[]{a[0] / l, a[1] / l, a[2] / l};
	}
}
