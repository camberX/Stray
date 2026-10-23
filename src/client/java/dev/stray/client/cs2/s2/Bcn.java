package dev.stray.client.cs2.s2;

/** CPU block-compression decoders (BC1, BC3, BC4, BC5, BC7) into RGBA8 (r,g,b,a byte order). */
public final class Bcn {
	private Bcn() {
	}

	public interface BlockDecoder {
		/** Decodes one 4x4 block at src[off] into 16 RGBA pixels (out, 64 bytes). */
		void decode(byte[] src, int off, byte[] out);
	}

	public static byte[] decode(byte[] src, int off, int w, int h, int blockBytes, BlockDecoder dec) {
		byte[] rgba = new byte[w * h * 4];
		byte[] block = new byte[64];
		int bw = (w + 3) / 4;
		int bh = (h + 3) / 4;
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				dec.decode(src, off + (by * bw + bx) * blockBytes, block);
				for (int py = 0; py < 4; py++) {
					int y = by * 4 + py;
					if (y >= h) {
						break;
					}
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px;
						if (x >= w) {
							break;
						}
						System.arraycopy(block, (py * 4 + px) * 4, rgba, (y * w + x) * 4, 4);
					}
				}
			}
		}
		return rgba;
	}

	private static int u16(byte[] b, int o) {
		return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
	}

	/** BC1 color part; writes rgb and alpha (alpha=0 for punch-through when allowed). */
	private static void color(byte[] s, int o, byte[] out, boolean bc1) {
		int c0 = u16(s, o);
		int c1 = u16(s, o + 2);
		int[] r = new int[4], g = new int[4], b = new int[4], a = {255, 255, 255, 255};
		r[0] = ((c0 >> 11) & 31) * 255 / 31;
		g[0] = ((c0 >> 5) & 63) * 255 / 63;
		b[0] = (c0 & 31) * 255 / 31;
		r[1] = ((c1 >> 11) & 31) * 255 / 31;
		g[1] = ((c1 >> 5) & 63) * 255 / 63;
		b[1] = (c1 & 31) * 255 / 31;
		if (c0 > c1 || !bc1) {
			r[2] = (2 * r[0] + r[1]) / 3;
			g[2] = (2 * g[0] + g[1]) / 3;
			b[2] = (2 * b[0] + b[1]) / 3;
			r[3] = (r[0] + 2 * r[1]) / 3;
			g[3] = (g[0] + 2 * g[1]) / 3;
			b[3] = (b[0] + 2 * b[1]) / 3;
		} else {
			r[2] = (r[0] + r[1]) / 2;
			g[2] = (g[0] + g[1]) / 2;
			b[2] = (b[0] + b[1]) / 2;
			a[3] = 0;
		}
		int bits = (s[o + 4] & 0xFF) | ((s[o + 5] & 0xFF) << 8) | ((s[o + 6] & 0xFF) << 16) | ((s[o + 7] & 0xFF) << 24);
		for (int i = 0; i < 16; i++) {
			int idx = (bits >>> (i * 2)) & 3;
			out[i * 4] = (byte) r[idx];
			out[i * 4 + 1] = (byte) g[idx];
			out[i * 4 + 2] = (byte) b[idx];
			out[i * 4 + 3] = (byte) a[idx];
		}
	}

	/** BC4 single channel into out[i*4+channel]. */
	private static void single(byte[] s, int o, byte[] out, int channel) {
		int a0 = s[o] & 0xFF;
		int a1 = s[o + 1] & 0xFF;
		int[] v = new int[8];
		v[0] = a0;
		v[1] = a1;
		if (a0 > a1) {
			for (int i = 1; i < 7; i++) {
				v[i + 1] = ((7 - i) * a0 + i * a1) / 7;
			}
		} else {
			for (int i = 1; i < 5; i++) {
				v[i + 1] = ((5 - i) * a0 + i * a1) / 5;
			}
			v[6] = 0;
			v[7] = 255;
		}
		long bits = 0;
		for (int i = 0; i < 6; i++) {
			bits |= (long) (s[o + 2 + i] & 0xFF) << (8 * i);
		}
		for (int i = 0; i < 16; i++) {
			out[i * 4 + channel] = (byte) v[(int) ((bits >>> (3 * i)) & 7)];
		}
	}

	public static final BlockDecoder BC1 = (s, o, out) -> color(s, o, out, true);

	public static final BlockDecoder BC3 = (s, o, out) -> {
		color(s, o + 8, out, false);
		single(s, o, out, 3);
	};

	public static final BlockDecoder BC4 = (s, o, out) -> {
		single(s, o, out, 0);
		for (int i = 0; i < 16; i++) {
			out[i * 4 + 1] = out[i * 4];
			out[i * 4 + 2] = out[i * 4];
			out[i * 4 + 3] = (byte) 255;
		}
	};

	public static final BlockDecoder BC5 = (s, o, out) -> {
		single(s, o, out, 0);
		single(s, o + 8, out, 1);
		for (int i = 0; i < 16; i++) {
			out[i * 4 + 2] = 0;
			out[i * 4 + 3] = (byte) 255;
		}
	};

	// ---- BC7

	private static final int[][] W = {null, null, {0, 21, 43, 64}, {0, 9, 18, 27, 37, 46, 55, 64},
		{0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64}};

	private static final int[] P2 = {0xCCCC, 0x8888, 0xEEEE, 0xECC8, 0xC880, 0xFEEC, 0xFEC8, 0xEC80, 0xC800, 0xFFEC, 0xFE80, 0xE800, 0xFFE8, 0xFF00, 0xFFF0, 0xF000, 0xF710, 0x008E, 0x7100, 0x08CE, 0x008C, 0x7310, 0x3100, 0x8CCE, 0x088C, 0x3110, 0x6666, 0x366C, 0x17E8, 0x0FF0, 0x718E, 0x399C, 0xAAAA, 0xF0F0, 0x5A5A, 0x33CC, 0x3C3C, 0x55AA, 0x9696, 0xA55A, 0x73CE, 0x13C8, 0x324C, 0x3BDC, 0x6996, 0xC33C, 0x9966, 0x0660, 0x0272, 0x04E4, 0x4E40, 0x2720, 0xC936, 0x936C, 0x39C6, 0x639C, 0x9336, 0x9CC6, 0x817E, 0xE718, 0xCCF0, 0x0FCC, 0x7744, 0xEE22};

	private static final String P3S = "0011001102212222000100112211222100002001221122110222002200110111000000001122112200110011002200220022002211111111001100112211221100000000111122220000111111112222000011112222222200120012001200120112011201120112012201220122012200110112112212220011200122002220000100110112112201110011200122000000112211221122002200220022111101110111022202220001000122212221000000110122012200001100221022100122012200110000001200121122222201101221122101100000011012211221002211021102002201100110200222220011012201220011000020002211222100000002112212220222002200120011001100120022022201200120012001200000111122220000012012012012012001202012120101200011220011220011001111222200001101010101222222220000000021212121002211220022112200220011002200110220122102201221010122222222010100002121212121210101010101012222022201110222011100021112000211120000211221122112022201110111022200021112111200020110011001102222000000002112211201100110222222220022001100110022002211221122002200000000000021120002000100020001022212220222122201012222222222220111201122012220";

	private static final int[] A2 = {15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 2, 8, 2, 2, 8, 8, 15, 2, 8, 2, 2, 8, 8, 2, 2, 15, 15, 6, 8, 2, 8, 15, 15, 2, 8, 2, 2, 2, 15, 15, 6, 6, 2, 6, 8, 15, 15, 2, 2, 15, 15, 15, 15, 15, 2, 2, 15};
	private static final int[] A3A = {3, 3, 15, 15, 8, 3, 15, 15, 8, 8, 6, 6, 6, 5, 3, 3, 3, 3, 8, 15, 3, 3, 6, 10, 5, 8, 8, 6, 8, 5, 15, 15, 8, 15, 3, 5, 6, 10, 8, 15, 15, 3, 15, 5, 15, 15, 15, 15, 3, 15, 5, 5, 5, 8, 5, 10, 5, 10, 8, 13, 15, 12, 3, 3};
	private static final int[] A3B = {15, 8, 8, 3, 15, 15, 3, 8, 15, 15, 15, 15, 15, 15, 15, 8, 15, 8, 15, 3, 15, 8, 15, 8, 3, 15, 6, 10, 15, 15, 10, 8, 15, 3, 15, 10, 10, 8, 9, 10, 6, 15, 8, 15, 3, 6, 6, 8, 15, 3, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 3, 15, 15, 8};

	private static final class Bits {
		final byte[] s;
		final int o;
		int p;

		Bits(byte[] s, int o) {
			this.s = s;
			this.o = o;
		}

		int get(int n) {
			int v = 0;
			for (int i = 0; i < n; i++) {
				int bit = p + i;
				v |= ((s[o + (bit >> 3)] >> (bit & 7)) & 1) << i;
			}
			p += n;
			return v;
		}
	}

	private static int subset(int mode, int ns, int part, int i) {
		if (ns == 1) {
			return 0;
		}
		if (ns == 2) {
			return (P2[part] >> i) & 1;
		}
		return P3S.charAt(part * 16 + i) - '0';
	}

	private static boolean anchor(int ns, int part, int s, int i) {
		if (i == 0) {
			return true;
		}
		if (ns == 2) {
			return s == 1 && i == A2[part];
		}
		if (ns == 3) {
			return (s == 1 && i == A3A[part]) || (s == 2 && i == A3B[part]);
		}
		return false;
	}

	private static int interp(int a, int b, int w) {
		return (a * (64 - w) + b * w + 32) >> 6;
	}

	public static final BlockDecoder BC7 = (s, o, out) -> {
		int first = s[o] & 0xFF;
		int mode = 0;
		while (mode < 8 && (first & (1 << mode)) == 0) {
			mode++;
		}
		if (mode == 8) {
			for (int i = 0; i < 64; i++) {
				out[i] = 0;
			}
			return;
		}
		Bits b = new Bits(s, o);
		b.get(mode + 1);
		int[] NS = {3, 2, 3, 2, 1, 1, 1, 2};
		int[] PB = {4, 6, 6, 6, 0, 0, 0, 6};
		int[] CB = {4, 6, 5, 7, 5, 7, 7, 5};
		int[] AB = {0, 0, 0, 0, 6, 8, 7, 5};
		int[] EPB = {1, 0, 0, 1, 0, 0, 1, 1};
		int[] SPB = {0, 1, 0, 0, 0, 0, 0, 0};
		int[] IB = {3, 3, 2, 2, 2, 2, 4, 2};
		int[] IB2 = {0, 0, 0, 0, 3, 2, 0, 0};
		int ns = NS[mode];
		int part = b.get(PB[mode]);
		int rot = mode == 4 || mode == 5 ? b.get(2) : 0;
		int isel = mode == 4 ? b.get(1) : 0;
		int ne = ns * 2;
		int[][] ep = new int[ne][4];
		for (int c = 0; c < 3; c++) {
			for (int e = 0; e < ne; e++) {
				ep[e][c] = b.get(CB[mode]);
			}
		}
		if (AB[mode] > 0) {
			for (int e = 0; e < ne; e++) {
				ep[e][3] = b.get(AB[mode]);
			}
		}
		int cb = CB[mode];
		int ab = AB[mode];
		if (EPB[mode] == 1) {
			for (int e = 0; e < ne; e++) {
				int pbit = b.get(1);
				for (int c = 0; c < 4; c++) {
					ep[e][c] = (ep[e][c] << 1) | pbit;
				}
			}
			cb++;
			if (ab > 0) {
				ab++;
			}
		} else if (SPB[mode] == 1) {
			for (int sub = 0; sub < ns; sub++) {
				int pbit = b.get(1);
				for (int e = sub * 2; e < sub * 2 + 2; e++) {
					for (int c = 0; c < 4; c++) {
						ep[e][c] = (ep[e][c] << 1) | pbit;
					}
				}
			}
			cb++;
			if (ab > 0) {
				ab++;
			}
		}
		for (int e = 0; e < ne; e++) {
			for (int c = 0; c < 3; c++) {
				ep[e][c] = (ep[e][c] << (8 - cb)) | (ep[e][c] >> (2 * cb - 8));
			}
			if (ab > 0) {
				ep[e][3] = (ep[e][3] << (8 - ab)) | (ep[e][3] >> (2 * ab - 8));
			} else {
				ep[e][3] = 255;
			}
		}
		int ib = IB[mode];
		int ib2 = IB2[mode];
		int[] idx = new int[16];
		int[] idx2 = new int[16];
		for (int i = 0; i < 16; i++) {
			int sub = subset(mode, ns, part, i);
			idx[i] = b.get(anchor(ns, part, sub, i) ? ib - 1 : ib);
		}
		if (ib2 > 0) {
			for (int i = 0; i < 16; i++) {
				idx2[i] = b.get(i == 0 ? ib2 - 1 : ib2);
			}
		}
		for (int i = 0; i < 16; i++) {
			int sub = subset(mode, ns, part, i);
			int[] e0 = ep[sub * 2];
			int[] e1 = ep[sub * 2 + 1];
			int r, g, bl, a;
			if (ib2 == 0) {
				int w = W[ib][idx[i]];
				r = interp(e0[0], e1[0], w);
				g = interp(e0[1], e1[1], w);
				bl = interp(e0[2], e1[2], w);
				a = interp(e0[3], e1[3], w);
			} else {
				int cw = isel == 0 ? W[ib][idx[i]] : W[ib2][idx2[i]];
				int aw = isel == 0 ? W[ib2][idx2[i]] : W[ib][idx[i]];
				r = interp(e0[0], e1[0], cw);
				g = interp(e0[1], e1[1], cw);
				bl = interp(e0[2], e1[2], cw);
				a = interp(e0[3], e1[3], aw);
			}
			switch (rot) {
				case 1 -> {
					int t = a;
					a = r;
					r = t;
				}
				case 2 -> {
					int t = a;
					a = g;
					g = t;
				}
				case 3 -> {
					int t = a;
					a = bl;
					bl = t;
				}
				default -> {
				}
			}
			out[i * 4] = (byte) r;
			out[i * 4 + 1] = (byte) g;
			out[i * 4 + 2] = (byte) bl;
			out[i * 4 + 3] = (byte) a;
		}
	};
}
