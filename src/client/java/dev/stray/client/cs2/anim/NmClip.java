package dev.stray.client.cs2.anim;

import dev.stray.client.cs2.s2.Resource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A compiled AnimGraph2 clip (.vnmclip), decoded like ValveResourceFormat's AnimationClip (MIT). */
public final class NmClip {
	public final String skeleton;
	public final int frames;
	public final float duration;
	private final short[] data;
	private final int[] offsets;
	private final Track[] tracks;
	public final List<NmClip> secondary = new ArrayList<>();

	private record Track(float tx, float txl, float ty, float tyl, float tz, float tzl, float s, float sl,
		float[] constRot, boolean rotStatic, boolean posStatic, boolean scaleStatic) {
	}

	@SuppressWarnings("unchecked")
	public NmClip(Resource res) {
		this((Map<String, Object>) res.kv3("DATA"));
	}

	@SuppressWarnings("unchecked")
	NmClip(Map<String, Object> d) {
		skeleton = String.valueOf(d.get("m_skeleton"));
		frames = ((Number) d.getOrDefault("m_nNumFrames", 1L)).intValue();
		duration = ((Number) d.getOrDefault("m_flDuration", 1.0)).floatValue();
		byte[] raw = (byte[]) d.get("m_compressedPoseData");
		if (raw == null) {
			raw = new byte[0];
		}
		ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
		data = new short[raw.length / 2];
		b.asShortBuffer().get(data);
		offsets = NmSkeleton.toInts(d.get("m_compressedPoseOffsets"), frames);
		List<Object> settings = (List<Object>) d.getOrDefault("m_trackCompressionSettings", List.of());
		tracks = new Track[settings.size()];
		for (int i = 0; i < tracks.length; i++) {
			Map<String, Object> s = (Map<String, Object>) settings.get(i);
			float[] x = range(s.get("m_translationRangeX"));
			float[] y = range(s.get("m_translationRangeY"));
			float[] z = range(s.get("m_translationRangeZ"));
			float[] sc = range(s.get("m_scaleRange"));
			float[] q = quat(s.get("m_constantRotation"));
			tracks[i] = new Track(x[0], x[1], y[0], y[1], z[0], z[1], sc[0], sc[1], q,
				Boolean.TRUE.equals(s.get("m_bIsRotationStatic")),
				Boolean.TRUE.equals(s.get("m_bIsTranslationStatic")),
				Boolean.TRUE.equals(s.get("m_bIsScaleStatic")));
		}
		for (Object o : (List<Object>) d.getOrDefault("m_secondaryAnimations", List.of())) {
			secondary.add(new NmClip((Map<String, Object>) o));
		}
	}

	public int bones() {
		return tracks.length;
	}

	@SuppressWarnings("unchecked")
	private static float[] range(Object o) {
		if (!(o instanceof Map<?, ?> m)) {
			return new float[]{0, 0};
		}
		Map<String, Object> r = (Map<String, Object>) m;
		return new float[]{((Number) r.getOrDefault("m_flRangeStart", 0.0)).floatValue(), ((Number) r.getOrDefault("m_flRangeLength", 0.0)).floatValue()};
	}

	private static float[] quat(Object o) {
		float[] q = {0, 0, 0, 1};
		if (o instanceof double[] d && d.length >= 4) {
			for (int i = 0; i < 4; i++) {
				q[i] = (float) d[i];
			}
		} else if (o instanceof float[] f && f.length >= 4) {
			System.arraycopy(f, 0, q, 0, 4);
		}
		return q;
	}

	private static final float RMIN = (float) (-1.0 / Math.sqrt(2.0));
	private static final float RLEN = (float) (2.0 / Math.sqrt(2.0));

	/** Parent-space pose of every track at a frame: per bone x y z, qx qy qz qw, scale. */
	public float[][] frame(int f) {
		f = Math.max(0, Math.min(frames - 1, f));
		float[][] out = new float[tracks.length][8];
		int p = offsets.length > f ? offsets[f] : 0;
		for (int i = 0; i < tracks.length; i++) {
			Track t = tracks[i];
			float[] o = out[i];
			o[0] = t.tx;
			o[1] = t.ty;
			o[2] = t.tz;
			System.arraycopy(t.constRot, 0, o, 3, 4);
			o[7] = t.s;
			if (!t.rotStatic) {
				int d0 = data[p] & 0xFFFF;
				int d1 = data[p + 1] & 0xFFFF;
				int d2 = data[p + 2] & 0xFFFF;
				float a = (d0 & 0x7FFF) * (RLEN / 0x7FFF) + RMIN;
				float bb = (d1 & 0x7FFF) * (RLEN / 0x7FFF) + RMIN;
				float c = d2 * (RLEN / 0x7FFF) + RMIN;
				float w = (float) Math.sqrt(Math.max(0f, 1f - (a * a + bb * bb + c * c)));
				int largest = ((d0 >> 14) & 2) | (d1 >> 15);
				switch (largest) {
					case 0 -> set(o, w, a, bb, c);
					case 1 -> set(o, a, w, bb, c);
					case 2 -> set(o, a, bb, w, c);
					default -> set(o, a, bb, c, w);
				}
				p += 3;
			}
			if (!t.posStatic) {
				o[0] = (data[p] & 0xFFFF) / 65535f * t.txl + t.tx;
				o[1] = (data[p + 1] & 0xFFFF) / 65535f * t.tyl + t.ty;
				o[2] = (data[p + 2] & 0xFFFF) / 65535f * t.tzl + t.tz;
				p += 3;
			}
			if (!t.scaleStatic) {
				o[7] = (data[p] & 0xFFFF) / 65535f * t.sl + t.s;
				p += 1;
			}
		}
		return out;
	}

	private static void set(float[] o, float x, float y, float z, float w) {
		o[3] = x;
		o[4] = y;
		o[5] = z;
		o[6] = w;
	}
}
