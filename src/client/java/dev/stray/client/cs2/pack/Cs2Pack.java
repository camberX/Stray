package dev.stray.client.cs2.pack;

import dev.stray.client.cs2.skin.Assets;
import dev.stray.client.cs2.skin.Tex;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Stray's own CS2 data pack: the files the knives need, taken once from a CS2 install so the
 * mod works without one. Text/model files are stored as-is; textures are stored decoded
 * (RGBA8 or RGBA half floats) at the size the knives use. Every entry is deflated.
 */
public final class Cs2Pack implements AutoCloseable {
	public static final String MAGIC = "SCS2PAK1";
	public static final int RAW = 0;
	public static final int RGBA8 = 1;
	public static final int RGBA16F = 2;
	/** One mip of a CS2 texture in its own (block-compressed) format: w/h in the entry, format in the first byte. */
	public static final int VTEX = 3;

	record Entry(String path, int type, int w, int h, long offset, int compressed, int raw) {
	}

	private final RandomAccessFile file;
	private final Map<String, Entry> entries = new HashMap<>();
	private final List<String> paths = new ArrayList<>();

	public Cs2Pack(Path path) throws IOException {
		file = new RandomAccessFile(path.toFile(), "r");
		byte[] magic = new byte[8];
		file.readFully(magic);
		if (!MAGIC.equals(new String(magic, StandardCharsets.US_ASCII))) {
			throw new IOException("not a Stray CS2 pack");
		}
		int count = Integer.reverseBytes(file.readInt());
		int indexSize = Integer.reverseBytes(file.readInt());
		byte[] index = new byte[indexSize];
		file.readFully(index);
		ByteBuffer b = ByteBuffer.wrap(index).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < count; i++) {
			byte[] name = new byte[b.getShort() & 0xFFFF];
			b.get(name);
			String p = new String(name, StandardCharsets.UTF_8);
			Entry e = new Entry(p, b.get(), b.getShort() & 0xFFFF, b.getShort() & 0xFFFF, b.getLong(), b.getInt(), b.getInt());
			entries.put(p.toLowerCase(Locale.ROOT), e);
			paths.add(p);
		}
	}

	public Collection<String> paths() {
		return paths;
	}

	Entry find(String path) {
		return entries.get(path.toLowerCase(Locale.ROOT).replace('\\', '/'));
	}

	synchronized byte[] bytes(Entry e) throws IOException {
		byte[] packed = new byte[e.compressed()];
		file.seek(e.offset());
		file.readFully(packed);
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(packed);
			byte[] out = new byte[e.raw()];
			int n = 0;
			while (n < out.length && !inflater.finished()) {
				n += inflater.inflate(out, n, out.length - n);
			}
			return out;
		} catch (DataFormatException ex) {
			throw new IOException(ex);
		} finally {
			inflater.end();
		}
	}

	@Override
	public void close() throws IOException {
		file.close();
	}

	/** Assets backed by this pack. */
	public Assets assets() {
		Map<String, Tex> textures = new ConcurrentHashMap<>();
		return new Assets() {
			@Override
			protected byte[] readRaw(String path) throws IOException {
				Entry e = find(path);
				return e == null || e.type() != RAW ? null : bytes(e);
			}

			@Override
			public boolean exists(String path) {
				return find(compiled(path)) != null;
			}

			@Override
			public Tex texture(String path, int maxSize, boolean floats) {
				if (path == null || path.isEmpty()) {
					return null;
				}
				Entry e = find(compiled(path));
				if (e == null || e.type() == RAW) {
					return null;
				}
				String key = e.path() + ":" + maxSize;
				Tex hit = textures.get(key);
				if (hit != null) {
					return hit;
				}
				try {
					Tex t = decode(e, bytes(e));
					while (t.w > maxSize && t.w > 1 && t.h > 1) {
						t = half(t);
					}
					textures.put(key, t);
					return t;
				} catch (IOException ex) {
					return null;
				}
			}

			@Override
			public void clearCache() {
				super.clearCache();
				textures.clear();
			}
		};
	}

	private static Tex decode(Entry e, byte[] raw) {
		if (e.type() == VTEX) {
			int format = raw[0] & 0xFF;
			byte[] body = java.util.Arrays.copyOfRange(raw, 1, raw.length);
			return new Tex(e.w(), e.h(), dev.stray.client.cs2.s2.Vtex.decodeFloat(format, body, e.w(), e.h()));
		}
		int n = e.w() * e.h() * 4;
		float[] px = new float[n];
		if (e.type() == RGBA8) {
			for (int i = 0; i < n; i++) {
				px[i] = (raw[i] & 0xFF) / 255f;
			}
		} else {
			ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
			for (int i = 0; i < n; i++) {
				px[i] = Float.float16ToFloat(b.getShort());
			}
		}
		return new Tex(e.w(), e.h(), px);
	}

	static Tex half(Tex t) {
		int w = Math.max(1, t.w / 2);
		int h = Math.max(1, t.h / 2);
		float[] px = new float[w * h * 4];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				for (int k = 0; k < 4; k++) {
					int x0 = Math.min(t.w - 1, x * 2);
					int y0 = Math.min(t.h - 1, y * 2);
					int x1 = Math.min(t.w - 1, x0 + 1);
					int y1 = Math.min(t.h - 1, y0 + 1);
					px[(y * w + x) * 4 + k] = (t.px[(y0 * t.w + x0) * 4 + k] + t.px[(y0 * t.w + x1) * 4 + k]
						+ t.px[(y1 * t.w + x0) * 4 + k] + t.px[(y1 * t.w + x1) * 4 + k]) * 0.25f;
				}
			}
		}
		return new Tex(w, h, px);
	}
}
