package dev.stray.client.cs2.s2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary KV3 reader (versions 1 to 5), ported from ValveResourceFormat (MIT).
 * Objects become LinkedHashMap, arrays List or primitive arrays, blobs byte[].
 */
public final class Kv3 {
	private Kv3() {
	}

	public static boolean isKv3(int magic) {
		return magic == 0x03564B56 || (magic & 0xFFFFFF00) == 0x4B563300;
	}

	private static final class Lane {
		ByteBuffer b1;
		ByteBuffer b2;
		ByteBuffer b4;
		ByteBuffer b8;
	}

	private static final class Ctx {
		int version;
		ByteBuffer types;
		ByteBuffer objectLengths;
		byte[][] blobs = new byte[0][];
		int nextBlob;
		String[] strings;
		Lane buffer;
		Lane aux;
	}

	private static ByteBuffer le(byte[] a, int off, int len) {
		return ByteBuffer.wrap(a, off, len).slice().order(ByteOrder.LITTLE_ENDIAN);
	}

	private static int align(int v, int a) {
		return (v + a - 1) & -a;
	}

	/** Reads a KV3 document starting at data[offset], with blockSize bytes available. */
	public static Object read(byte[] data, int offset, int blockSize) {
		ByteBuffer r = le(data, offset, blockSize);
		int magic = r.getInt();
		if (magic == 0x03564B56) {
			return readV0(r, data, offset, blockSize);
		}
		int version = magic & 0xFF;
		if ((magic & 0xFFFFFF00) != 0x4B563300 || version < 1 || version > 5) {
			throw new IllegalStateException("bad kv3 magic " + Integer.toHexString(magic));
		}
		Ctx c = new Ctx();
		c.version = version;
		r.position(r.position() + 16); // format guid
		int compression = r.getInt();
		int frameSize = 0;
		int cb1, cb4, cb8, countTypes = 0, uncompTotal, compTotal = 0, countBlocks = 0, blobBytes = 0;
		if (version == 1) {
			cb1 = r.getInt();
			cb4 = r.getInt();
			cb8 = r.getInt();
			uncompTotal = r.getInt();
			compTotal = blockSize - r.position();
		} else {
			r.getShort(); // dictionary id
			frameSize = r.getShort() & 0xFFFF;
			cb1 = r.getInt();
			cb4 = r.getInt();
			cb8 = r.getInt();
			countTypes = r.getInt();
			r.getShort(); // objects
			r.getShort(); // arrays
			uncompTotal = r.getInt();
			compTotal = r.getInt();
			countBlocks = r.getInt();
			blobBytes = r.getInt();
		}
		int cb2 = 0;
		int blockSizesBytes = 0;
		if (version >= 4) {
			cb2 = r.getInt();
			blockSizesBytes = r.getInt();
		}
		int u1, c1, u2 = 0, c2 = 0, cb1b = 0, cb2b = 0, cb4b = 0, cb8b = 0, objs2 = 0;
		if (version >= 5) {
			u1 = r.getInt();
			c1 = r.getInt();
			u2 = r.getInt();
			c2 = r.getInt();
			cb1b = r.getInt();
			cb2b = r.getInt();
			cb4b = r.getInt();
			cb8b = r.getInt();
			r.getInt();
			objs2 = r.getInt();
			r.getInt();
			r.getInt();
		} else {
			c1 = compTotal;
			u1 = uncompTotal;
		}
		int dec1 = version < 5 && compression == 2 ? u1 + blobBytes : u1;
		byte[] buf1 = new byte[dec1];
		int base = offset + r.position();
		int pos = base;
		pos = decompress(compression, data, pos, c1, buf1, dec1, version >= 5);
		Lane lane1 = new Lane();
		int o = 0;
		if (cb1 > 0) {
			lane1.b1 = le(buf1, o, cb1);
			o += cb1;
		}
		if (cb2 > 0) {
			o = align(o, 2);
			lane1.b2 = le(buf1, o, cb2 * 2);
			o += cb2 * 2;
		}
		if (cb4 > 0) {
			o = align(o, 4);
			lane1.b4 = le(buf1, o, cb4 * 4);
			o += cb4 * 4;
		}
		if (cb8 > 0) {
			o = align(o, 8);
			lane1.b8 = le(buf1, o, cb8 * 8);
			o += cb8 * 8;
		} else if (version < 5) {
			o = align(o, 8);
		}
		int countStrings = lane1.b4.getInt();
		c.strings = new String[countStrings];
		c.aux = lane1;
		ByteBuffer blobSizes = null;
		if (version >= 5) {
			for (int i = 0; i < countStrings; i++) {
				c.strings[i] = cstr(lane1.b1);
			}
		} else {
			c.buffer = lane1;
			int stringsStart = o;
			ByteBuffer sb = le(buf1, o, u1 - o);
			for (int i = 0; i < countStrings; i++) {
				c.strings[i] = cstr(sb);
			}
			o += sb.position();
			int typesLength = version == 1 ? uncompTotal - o - 4 : countTypes - o + stringsStart;
			c.types = le(buf1, o, typesLength);
			o += typesLength;
			if (countBlocks > 0) {
				blobSizes = le(buf1, o, u1 - o);
			}
		}
		if (version >= 5) {
			byte[] buf2 = new byte[u2];
			pos = decompress(compression, data, pos, c2, buf2, u2, true);
			Lane lane2 = new Lane();
			c.buffer = lane2;
			int end = objs2 * 4;
			c.objectLengths = le(buf2, 0, end);
			o = end;
			if (cb1b > 0) {
				lane2.b1 = le(buf2, o, cb1b);
				o += cb1b;
			}
			if (cb2b > 0) {
				o = align(o, 2);
				lane2.b2 = le(buf2, o, cb2b * 2);
				o += cb2b * 2;
			}
			if (cb4b > 0) {
				o = align(o, 4);
				lane2.b4 = le(buf2, o, cb4b * 4);
				o += cb4b * 4;
			}
			if (cb8b > 0) {
				o = align(o, 8);
				lane2.b8 = le(buf2, o, cb8b * 8);
				o += cb8b * 8;
			}
			c.types = le(buf2, o, countTypes);
			o += countTypes;
			if (countBlocks > 0) {
				blobSizes = le(buf2, o, u2 - o);
			}
		}
		if (countBlocks > 0) {
			int[] lengths = new int[countBlocks];
			for (int i = 0; i < countBlocks; i++) {
				lengths[i] = blobSizes.getInt();
			}
			blobSizes.getInt(); // trailer
			c.blobs = new byte[countBlocks][];
			for (int i = 0; i < countBlocks; i++) {
				c.blobs[i] = new byte[lengths[i]];
			}
			if (compression == 0) {
				for (byte[] blob : c.blobs) {
					System.arraycopy(data, pos, blob, 0, blob.length);
					pos += blob.length;
				}
			} else if (compression == 1) {
				// Frames are chained: decode every blob into one contiguous window.
				byte[] all = new byte[blobBytes];
				int dp = 0;
				for (int i = 0; i < countBlocks; i++) {
					int target = dp + lengths[i];
					while (dp < target) {
						int len = blobSizes.getShort() & 0xFFFF;
						dp = Lz4.decode(data, pos, len, all, dp, all.length);
						pos += len;
					}
				}
				int p = 0;
				for (byte[] blob : c.blobs) {
					System.arraycopy(all, p, blob, 0, blob.length);
					p += blob.length;
				}
			} else {
				byte[] all;
				if (version >= 5) {
					int cBlobs = compTotal - c1 - c2;
					all = Zstd.decompress(data, pos, cBlobs, blobBytes);
					pos += cBlobs;
				} else {
					all = new byte[blobBytes];
					System.arraycopy(buf1, u1, all, 0, blobBytes);
				}
				int p = 0;
				for (byte[] blob : c.blobs) {
					System.arraycopy(all, p, blob, 0, blob.length);
					p += blob.length;
				}
			}
		}
		int[] t = readType(c);
		return value(c, t[0], c.buffer);
	}

	/** Decompresses one section; returns the new read position. */
	private static int decompress(int method, byte[] data, int pos, int compSize, byte[] out, int outSize, boolean v5) {
		if (method == 0) {
			int n = v5 ? outSize : Math.min(outSize, compSize);
			System.arraycopy(data, pos, out, 0, n);
			return pos + n;
		}
		if (method == 1) {
			int n = Lz4.decode(data, pos, compSize, out, 0, outSize);
			if (n != outSize) {
				throw new IllegalStateException("kv3 lz4 size");
			}
			return pos + compSize;
		}
		byte[] d = Zstd.decompress(data, pos, compSize, outSize);
		System.arraycopy(d, 0, out, 0, outSize);
		return pos + compSize;
	}

	private static String cstr(ByteBuffer b) {
		int start = b.position();
		int i = start;
		while (b.get(i) != 0) {
			i++;
		}
		byte[] bytes = new byte[i - start];
		b.get(bytes);
		b.get();
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static int[] readType(Ctx c) {
		int t = c.types.get() & 0xFF;
		if (c.version >= 3) {
			if ((t & 0x80) != 0) {
				c.types.get();
			}
			if ((t & 0x40) != 0) {
				c.types.get();
			}
			t &= 0x3F;
		} else {
			if ((t & 0x80) != 0) {
				c.types.get();
			}
			t &= 0x7F;
		}
		return new int[]{t};
	}

	private static String str(Ctx c, int id) {
		return id >= 0 && id < c.strings.length ? c.strings[id] : "";
	}

	private static Object packed(int type, Lane lane, int n) {
		switch (type) {
			case 2: {
				boolean[] v = new boolean[n];
				for (int i = 0; i < n; i++) {
					v[i] = lane.b1.get() != 0;
				}
				return v;
			}
			case 22: {
				int[] v = new int[n];
				for (int i = 0; i < n; i++) {
					v[i] = lane.b1.get();
				}
				return v;
			}
			case 23: {
				int[] v = new int[n];
				for (int i = 0; i < n; i++) {
					v[i] = lane.b1.get() & 0xFF;
				}
				return v;
			}
			case 20: {
				int[] v = new int[n];
				for (int i = 0; i < n; i++) {
					v[i] = lane.b2.getShort();
				}
				return v;
			}
			case 21: {
				int[] v = new int[n];
				for (int i = 0; i < n; i++) {
					v[i] = lane.b2.getShort() & 0xFFFF;
				}
				return v;
			}
			case 11:
			case 12: {
				int[] v = new int[n];
				for (int i = 0; i < n; i++) {
					v[i] = lane.b4.getInt();
				}
				return v;
			}
			case 19: {
				float[] v = new float[n];
				for (int i = 0; i < n; i++) {
					v[i] = lane.b4.getFloat();
				}
				return v;
			}
			case 3:
			case 4: {
				long[] v = new long[n];
				for (int i = 0; i < n; i++) {
					v[i] = lane.b8.getLong();
				}
				return v;
			}
			case 5: {
				double[] v = new double[n];
				for (int i = 0; i < n; i++) {
					v[i] = lane.b8.getDouble();
				}
				return v;
			}
			default:
				return null;
		}
	}

	private static Object value(Ctx c, int type, Lane lane) {
		Lane buf = c.buffer;
		switch (type) {
			case 1:
				return null;
			case 13:
				return Boolean.TRUE;
			case 14:
				return Boolean.FALSE;
			case 15:
				return 0L;
			case 16:
				return 1L;
			case 17:
				return 0.0;
			case 18:
				return 1.0;
			case 26:
				return 0L;
			case 27:
				return 1L;
			case 2:
				return lane.b1.get() != 0;
			case 22:
				return (long) lane.b1.get();
			case 23:
				return (long) (lane.b1.get() & 0xFF);
			case 20:
				return (long) lane.b2.getShort();
			case 21:
				return (long) (lane.b2.getShort() & 0xFFFF);
			case 11:
				return (long) lane.b4.getInt();
			case 12:
				return lane.b4.getInt() & 0xFFFFFFFFL;
			case 19:
				return (double) lane.b4.getFloat();
			case 3:
			case 4:
				return lane.b8.getLong();
			case 5:
				return lane.b8.getDouble();
			case 6:
				return str(c, buf.b4.getInt());
			case 7: {
				if (c.version < 2) {
					int len = buf.b4.getInt();
					byte[] out = new byte[Math.max(0, len)];
					buf.b1.get(out);
					return out;
				}
				return c.blobs[c.nextBlob++];
			}
			case 8: {
				int n = buf.b4.getInt();
				List<Object> list = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					int[] t = readType(c);
					list.add(value(c, t[0], c.buffer));
				}
				return list;
			}
			case 10:
			case 24:
			case 25: {
				int n = type == 10 ? buf.b4.getInt() : buf.b1.get() & 0xFF;
				int[] t = readType(c);
				Lane el = type == 25 ? c.aux : buf;
				Object p = packed(t[0], el, n);
				if (p != null) {
					return p;
				}
				List<Object> list = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					list.add(value(c, t[0], el));
				}
				return list;
			}
			case 9: {
				int n = c.version >= 5 ? c.objectLengths.getInt() : buf.b4.getInt();
				Map<String, Object> map = new LinkedHashMap<>(Math.max(4, n * 2));
				for (int i = 0; i < n; i++) {
					String name = str(c, buf.b4.getInt());
					int[] t = readType(c);
					map.put(name, value(c, t[0], c.buffer));
				}
				return map;
			}
			default:
				throw new IllegalStateException("kv3 type " + type);
		}
	}

	/** Legacy VKV3 (version 0) with block compression; rare in CS2. */
	private static Object readV0(ByteBuffer r, byte[] data, int offset, int size) {
		throw new IllegalStateException("legacy VKV3 not supported");
	}
}
