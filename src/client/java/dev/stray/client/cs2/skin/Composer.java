package dev.stray.client.cs2.skin;

/**
 * Builds a finished knife texture the way CS2's composite materials do: the knife's
 * composite inputs, a paint kit and a seed/wear go in; albedo and a roughness/metal/AO
 * map come out, both in the knife's UV space.
 *
 * Paint styles (F_PAINT_STYLE): 0 solid, 1 hydrographic, 2 spray, 3 anodized,
 * 4 anodized multicolored, 5 anodized airbrushed, 6 custom, 7 patina, 8 gunsmith.
 */
public final class Composer {
	public static final class Result {
		public final int size;
		/** ARGB. */
		public final int[] albedo;
		/** RGBA bytes: r = roughness, g = metalness, b = ambient occlusion, a = paint coverage. */
		public final byte[] material;

		Result(int size) {
			this.size = size;
			albedo = new int[size * size];
			material = new byte[size * size * 4];
		}
	}

	private Composer() {
	}

	public static Result compose(Assets assets, KnifeMaterial knife, PaintKit kit, int seed, float wear, int size) {
		Result out = new Result(size);
		int texSize = Math.max(256, size);
		Tex color = assets.texture(knife.base.get("g_tColor"), texSize, false);
		Tex metal = assets.texture(knife.base.get("g_tMetalness"), texSize, false);
		Tex masks = assets.texture(knife.inputs.get("g_tMasks"), texSize, false);
		Tex ao = assets.texture(knife.inputs.get("g_tAmbientOcclusion"), texSize, false);
		Tex pos = assets.texture(knife.inputs.get("g_tPosition"), texSize, true);
		Tex surface = assets.texture(knife.inputs.get("g_tSurface"), texSize, false);

		Tex pattern = null;
		Tex patternRough = null;
		Tex wearTex = null;
		Tex grunge = null;
		PaintKit.Roll roll = null;
		if (kit != null) {
			pattern = assets.texture(kit.textures.get("g_tPattern"), 2048, false);
			patternRough = assets.texture(kit.textures.get("g_tPatternRoughness"), 1024, false);
			wearTex = assets.texture(kit.textures.getOrDefault("g_tWear", "materials/models/weapons/customization/shared/paint_wear.vtex"), 1024, false);
			grunge = assets.texture(kit.textures.get("g_tGrunge"), 1024, false);
			roll = kit.roll(seed);
			wear = kit.wearMin + Math.max(0f, Math.min(1f, wear)) * (kit.wearMax - kit.wearMin);
		}

		float[] s = new float[4];
		float[] c = new float[4];
		float[] m = new float[4];
		float[] mt = new float[4];
		float[] p = new float[4];
		float[] pr = new float[4];
		float[] wv = new float[4];
		float[] gv = new float[4];
		float[] ps = new float[4];
		float[] sf = new float[4];
		float[] col = new float[3];

		float patternScale = kit == null ? 1f : kit.fl("g_flPatternTexCoordScale", 1f);
		if (kit != null && kit.ints.getOrDefault("g_bIgnoreWeaponSizeScale", 0) == 0) {
			patternScale *= knife.uvScale;
		}
		int style = kit == null ? -1 : kit.style;
		float paintRough = kit == null ? 0.5f : kit.fl("g_flPaintRoughness", 0.5f);
		float brightness = kit == null ? 1f : kit.fl("g_flColorBrightness", 1f);
		float[] c0 = kit == null ? null : kit.vec("g_vColor0");
		float[] c1 = kit == null ? null : kit.vec("g_vColor1");
		float[] c2 = kit == null ? null : kit.vec("g_vColor2");
		float[] c3 = kit == null ? null : kit.vec("g_vColor3");
		boolean projected = style == 2 || style == 5;
		// Spray projections run along the weapon: positions in the map are 0..1 of its bounds.
		float projectScale = kit == null || kit.ints.getOrDefault("g_bIgnoreWeaponSizeScale", 0) == 1
			? 1f
			: knife.weaponLength / Math.max(1f, kit.fl("g_flWeaponLength1", 32f));

		for (int y = 0; y < size; y++) {
			float v = (y + 0.5f) / size;
			for (int x = 0; x < size; x++) {
				float u = (x + 0.5f) / size;
				sample(color, u, v, c, 0.5f);
				sample(metal, u, v, mt, 0f);
				sample(masks, u, v, m, 0f);
				sample(ao, u, v, s, 1f);
				float occlusion = s[0];
				float baseRough = mt[0];
				float baseMetal = mt[1];
				float r = c[0];
				float g = c[1];
				float b = c[2];
				float rough = baseRough;
				float metalness = baseMetal;
				float coverage = 0f;

				if (kit != null) {
					// Pattern coordinates: rotate about the centre, scale, then offset (all rolled from the seed).
					float pu;
					float pv;
					if (projected && pos != null) {
						// Position map = model position / weapon length + 0.5: z runs along the knife, y across it.
						sample(pos, u, v, ps, 0f);
						pu = ps[2] * projectScale;
						pv = ps[1] * projectScale;
					} else {
						pu = u;
						pv = v;
					}
					float[] t = transform(pu, pv, roll.patternRotation, patternScale, roll.patternOffsetX, roll.patternOffsetY, projected ? 0 : variant);
					if (pattern != null) {
						boolean clampU = kit.ints.getOrDefault("g_nPatternTextureHorizontalSampling", 0) != 0;
						boolean clampV = kit.ints.getOrDefault("g_nPatternTextureVerticalSampling", 0) != 0;
						pattern.sample(clampU ? Math.max(0f, Math.min(1f, t[0])) : t[0], clampV ? Math.max(0f, Math.min(1f, t[1])) : t[1], true, p);
					} else {
						p[0] = p[1] = p[2] = 0f;
						p[3] = 1f;
					}

					// Wear: the wear map is scratched off first where the knife's wear mask (base alpha) is bright.
					float[] wt = transform(u, v, roll.wearRotation, roll.wearScale, roll.wearOffsetX, roll.wearOffsetY);
					if (wearTex != null) {
						wearTex.sample(wt[0], wt[1], true, wv);
					} else {
						wv[0] = 1f;
					}
					float durability = 1f - c[3];
					float threshold = wear * (0.55f + 1.1f * (1f - durability));
					float paint = smooth(threshold - 0.03f, threshold + 0.03f, wv[0]);
					float[] gt = transform(u, v, roll.grungeRotation, roll.grungeScale, roll.grungeOffsetX, roll.grungeOffsetY);
					if (grunge != null) {
						grunge.sample(gt[0], gt[1], true, gv);
					} else {
						gv[0] = gv[1] = gv[2] = 1f;
					}

					boolean metallicStyle = style == 3 || style == 4 || style == 5 || style == 7;
					float region; // where the finish applies
					switch (style) {
						case 0 -> {
							col[0] = c0[0];
							col[1] = c0[1];
							col[2] = c0[2];
							mix(col, c1, m[0]);
							mix(col, c2, m[1]);
							mix(col, c3, m[2]);
							region = 1f;
						}
						case 1, 2 -> {
							colors(col, c0, c1, c2, c3, p);
							mix(col, c2, m[1]);
							mix(col, c3, m[2]);
							region = 1f;
						}
						case 3 -> {
							col[0] = c0[0];
							col[1] = c0[1];
							col[2] = c0[2];
							region = m[0];
						}
						case 4, 5 -> {
							colors(col, c0, c1, c2, c3, p);
							region = m[0];
						}
						case 6, 8 -> {
							col[0] = p[0];
							col[1] = p[1];
							col[2] = p[2];
							region = 1f;
						}
						case 7 -> {
							// Patina: the pattern is a colour image over the bare metal.
							col[0] = p[0];
							col[1] = p[1];
							col[2] = p[2];
							region = m[0];
						}
						default -> region = 0f;
					}
					if (kit.ints.getOrDefault("g_nColorAdjustmentMode", 0) == 1) {
						// Adjustment mode 1 pushes saturation and contrast: a power curve.
						col[0] = (float) Math.pow(clamp(col[0]), brightness);
						col[1] = (float) Math.pow(clamp(col[1]), brightness);
						col[2] = (float) Math.pow(clamp(col[2]), brightness);
					} else {
						col[0] *= brightness;
						col[1] *= brightness;
						col[2] *= brightness;
					}
					if (style == 4 || style == 5 || style == 3) {
						// Anodized paint is a tinted clear coat over polished metal: keep the metal's detail.
						float detail = 0.65f + 0.7f * luminance(r, g, b);
						col[0] *= detail;
						col[1] *= detail;
						col[2] *= detail;
					}
					// Grunge darkens the paint a little more as it wears.
					float dirt = 1f - wear * 0.5f * (1f - luminance(gv[0], gv[1], gv[2]));
					col[0] *= dirt;
					col[1] *= dirt;
					col[2] *= dirt;

					coverage = Math.max(0f, Math.min(1f, region * paint));
					r = r + (clamp(col[0]) - r) * coverage;
					g = g + (clamp(col[1]) - g) * coverage;
					b = b + (clamp(col[2]) - b) * coverage;
					float paintMetal = metallicStyle ? 1f : style == 8 ? (p[3] > 0.5f ? 1f : 0f) : 0f;
					float pRough = paintRough;
					if (patternRough != null) {
						float[] rt = transform(pu, pv, roll.patternRotation, patternScale, roll.patternOffsetX, roll.patternOffsetY, projected ? 0 : variant);
						patternRough.sample(rt[0], rt[1], true, pr);
						pRough = pr[0];
					}
					rough = rough + (pRough - rough) * coverage;
					metalness = metalness + (paintMetal - metalness) * coverage;
				}

				int i = y * size + x;
				out.albedo[i] = 0xFF000000 | (to8(r) << 16) | (to8(g) << 8) | to8(b);
				out.material[i * 4] = (byte) to8(rough);
				out.material[i * 4 + 1] = (byte) to8(metalness);
				out.material[i * 4 + 2] = (byte) to8(occlusion);
				out.material[i * 4 + 3] = (byte) to8(coverage);
			}
		}
		return out;
	}

	private static void sample(Tex t, float u, float v, float[] out, float def) {
		if (t == null) {
			out[0] = out[1] = out[2] = def;
			out[3] = 1f;
			return;
		}
		t.sample(u, v, false, out);
	}

	/**
	 * Pattern transform convention for UV-mapped styles: rotate about the origin, scale, then add
	 * the offset (bits 4 and 8). Calibrated on Case Hardened Karambits: with it the known backside
	 * blue gems (417, 150, 406, ...) rank in the top 2% of all 1001 seeds for blue, and 387 shows
	 * a blue playside. Spray projections keep their own calibration (variant 0).
	 */
	public static int variant = Integer.getInteger("stray.cs2.variant", 12);

	/** Rotate by -degrees, scale, then subtract the offset (matches the game's spray projection). */
	static float[] transform(float u, float v, float degrees, float scale, float ox, float oy) {
		return transform(u, v, degrees, scale, ox, oy, variant);
	}

	static float[] transform(float u, float v, float degrees, float scale, float ox, float oy, int variant) {
		if ((variant & 4) != 0) {
			ox = -ox;
			oy = -oy;
		}
		float a = (float) Math.toRadians((variant & 8) != 0 ? degrees : -degrees);
		float cs = (float) Math.cos(a);
		float sn = (float) Math.sin(a);
		if ((variant & 16) != 0) {
			// Offset first, then rotate about the origin and scale.
			float qu = u - ox;
			float qv = v - oy;
			return new float[]{(qu * cs - qv * sn) * scale, (qu * sn + qv * cs) * scale};
		}
		boolean centered = (variant & 1) != 0;
		float c = centered ? 0.5f : 0f;
		float du = u - c;
		float dv = v - c;
		float ru = du * cs - dv * sn;
		float rv = du * sn + dv * cs;
		if ((variant & 2) != 0) {
			return new float[]{(ru + c) * scale - ox, (rv + c) * scale - oy};
		}
		return new float[]{ru * scale + c - ox, rv * scale + c - oy};
	}

	private static void colors(float[] col, float[] c0, float[] c1, float[] c2, float[] c3, float[] p) {
		col[0] = c0[0];
		col[1] = c0[1];
		col[2] = c0[2];
		mix(col, c1, p[0]);
		mix(col, c2, p[1]);
		mix(col, c3, p[2]);
	}

	private static void mix(float[] col, float[] c, float t) {
		t = clamp(t);
		col[0] += (c[0] - col[0]) * t;
		col[1] += (c[1] - col[1]) * t;
		col[2] += (c[2] - col[2]) * t;
	}

	private static float luminance(float r, float g, float b) {
		return 0.2126f * r + 0.7152f * g + 0.0722f * b;
	}

	private static float smooth(float a, float b, float x) {
		float t = clamp((x - a) / (b - a));
		return t * t * (3 - 2 * t);
	}

	private static float clamp(float v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	private static int to8(float v) {
		return Math.round(clamp(v) * 255f);
	}
}
