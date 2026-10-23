package dev.stray.client.cs2.s2;

/** meshoptimizer vertex and index buffer decoders, ported from ValveResourceFormat (MIT). */
public final class Meshopt {
	private Meshopt() {
	}

	private static final int VERTEX_BLOCK_SIZE_BYTES = 8192;
	private static final int VERTEX_BLOCK_MAX = 256;
	private static final int GROUP = 16;
	private static final int[] BITS_V0 = {0, 2, 4, 8};
	private static final int[] BITS_V1 = {0, 1, 2, 4, 8};

	private static final class In {
		final byte[] d;
		int p;
		final int end;

		In(byte[] d, int p, int end) {
			this.d = d;
			this.p = p;
			this.end = end;
		}

		int left() {
			return end - p;
		}
	}

	private static void group(In in, byte[] buf, int bo, int bits) {
		byte[] d = in.d;
		int p = in.p;
		switch (bits) {
			case 0 -> {
				for (int i = 0; i < GROUP; i++) {
					buf[bo + i] = 0;
				}
			}
			case 1 -> {
				int var = p + 2;
				for (int half = 0; half < 2; half++) {
					int b = d[p + half] & 0xFF;
					b = (int) ((((b * 0x80200802L) & 0x0884422110L) * 0x0101010101L) >>> 32) & 0xFF;
					for (int i = 0; i < 8; i++) {
						int enc = (b >> 7) & 1;
						b = (b << 1) & 0xFF;
						if (enc == 1) {
							buf[bo + half * 8 + i] = d[var++];
						} else {
							buf[bo + half * 8 + i] = (byte) enc;
						}
					}
				}
				in.p = var;
			}
			case 2, 4 -> {
				int per = 8 / bits;
				int headerBytes = GROUP / per;
				int var = p + headerBytes;
				int max = (1 << bits) - 1;
				int k = 0;
				for (int h = 0; h < headerBytes; h++) {
					int b = d[p + h] & 0xFF;
					for (int i = 0; i < per; i++) {
						int enc = (b >> (8 - bits)) & max;
						b = (b << bits) & 0xFF;
						buf[bo + k++] = enc == max ? d[var++] : (byte) enc;
					}
				}
				in.p = var;
			}
			case 8 -> {
				System.arraycopy(d, p, buf, bo, GROUP);
				in.p = p + GROUP;
			}
			default -> throw new IllegalStateException("bits " + bits);
		}
	}

	private static void bytes(In in, byte[] buf, int bo, int len, int[] bits, int bitsOff) {
		int headerSize = ((len / GROUP) + 3) / 4;
		int header = in.p;
		in.p += headerSize;
		for (int i = 0; i < len; i += GROUP) {
			int ho = i / GROUP;
			int k = ((in.d[header + ho / 4] & 0xFF) >> ((ho % 4) * 2)) & 3;
			group(in, buf, bo + i, bits[bitsOff + k]);
		}
	}

	private static void deltas(int size, byte[] buf, int bufOff, byte[] out, int outOff, int count, int vsize, byte[] last, int lastOff, int rot) {
		for (int k = 0; k < 4; k += size) {
			int vo = outOff + k;
			int p = 0;
			for (int j = 0; j < size; j++) {
				p |= (last[lastOff + k + j] & 0xFF) << (8 * j);
			}
			for (int i = 0; i < count; i++) {
				int v = 0;
				for (int j = 0; j < size; j++) {
					v |= (buf[bufOff + i + count * j] & 0xFF) << (8 * j);
				}
				if (size == 1) {
					v = (((-(v & 1)) ^ (v >>> 1)) + p) & 0xFF;
				} else if (size == 2) {
					v = (((-(v & 1)) ^ (v >>> 1)) + p) & 0xFFFF;
				} else {
					v = Integer.rotateLeft(v, rot) ^ p;
				}
				for (int j = 0; j < size; j++) {
					out[vo + j] = (byte) (v >>> (j * 8));
				}
				p = v;
				vo += vsize;
			}
			bufOff += count * size;
		}
	}

	public static byte[] decodeVertices(int count, int vsize, byte[] src, int off, int len) {
		if ((src[off] & 0xF0) != 0xA0) {
			throw new IllegalStateException("bad meshopt vertex header");
		}
		int version = src[off] & 0x0F;
		In in = new In(src, off + 1, off + len);
		int tailSize = vsize + (version == 0 ? 0 : vsize / 4);
		int tailMin = version == 0 ? 32 : 24;
		int tailPad = Math.max(tailSize, tailMin);
		byte[] result = new byte[count * vsize];
		byte[] last = new byte[vsize];
		System.arraycopy(src, in.end - tailSize, last, 0, vsize);
		int channelsOff = in.end - tailSize + vsize;
		int blockSize = Math.min((VERTEX_BLOCK_SIZE_BYTES / vsize) & ~(GROUP - 1), VERTEX_BLOCK_MAX);
		byte[] buffer = new byte[VERTEX_BLOCK_MAX * 4];
		byte[] transposed = new byte[VERTEX_BLOCK_SIZE_BYTES];
		int vo = 0;
		while (vo < count) {
			int n = Math.min(blockSize, count - vo);
			int aligned = (n + GROUP - 1) & ~(GROUP - 1);
			int controlSize = version == 0 ? 0 : vsize / 4;
			int control = in.p;
			in.p += controlSize;
			for (int k = 0; k < vsize; k += 4) {
				int ctrlByte = version == 0 ? 0 : src[control + k / 4] & 0xFF;
				for (int j = 0; j < 4; j++) {
					int ctrl = (ctrlByte >> (j * 2)) & 3;
					if (ctrl == 3) {
						System.arraycopy(src, in.p, buffer, j * n, n);
						in.p += n;
					} else if (ctrl == 2) {
						for (int i = 0; i < n; i++) {
							buffer[j * n + i] = 0;
						}
					} else {
						// decode into a padded scratch so group writes past n don't clobber the next lane
						byte[] scratch = new byte[aligned];
						bytes(in, scratch, 0, aligned, version == 0 ? BITS_V0 : BITS_V1, version == 0 ? 0 : ctrl);
						System.arraycopy(scratch, 0, buffer, j * n, n);
					}
				}
				int channel = version == 0 ? 0 : src[channelsOff + k / 4] & 0xFF;
				switch (channel & 3) {
					case 0 -> deltas(1, buffer, 0, transposed, k, n, vsize, last, k, 0);
					case 1 -> deltas(2, buffer, 0, transposed, k, n, vsize, last, k, 0);
					case 2 -> deltas(4, buffer, 0, transposed, k, n, vsize, last, k, (32 - (channel >> 4)) & 31);
					default -> throw new IllegalStateException("channel");
				}
			}
			System.arraycopy(transposed, 0, result, vo * vsize, n * vsize);
			System.arraycopy(transposed, vsize * (n - 1), last, 0, vsize);
			vo += n;
		}
		if (in.left() != tailPad) {
			throw new IllegalStateException("meshopt tail size " + in.left() + " != " + tailPad);
		}
		return result;
	}

	public static int[] decodeIndices(int count, byte[] src, int off, int len) {
		if ((src[off] & 0xF0) != 0xE0) {
			throw new IllegalStateException("bad meshopt index header");
		}
		int version = src[off] & 0x0F;
		int[] edgeA = new int[16];
		int[] edgeB = new int[16];
		int[] vfifo = new int[16];
		int eo = 0;
		int vo = 0;
		int next = 0;
		int last = 0;
		int fecmax = version >= 1 ? 13 : 15;
		int codeStart = off + 1;
		int dataStart = off + 1 + count / 3;
		int dataEnd = off + len;
		int safeEnd = dataEnd - 16;
		int pos = dataStart;
		int[] out = new int[count];
		int o = 0;
		int[] posRef = new int[1];
		for (int t = codeStart; t < dataStart; t++) {
			int codetri = src[t] & 0xFF;
			if (codetri < 0xF0) {
				int fe = codetri >> 4;
				int idx = (eo - 1 - fe) & 15;
				int a = edgeA[idx];
				int b = edgeB[idx];
				int c;
				int fec = codetri & 15;
				if (fec < fecmax) {
					int cf = vfifo[(vo - 1 - fec) & 15];
					c = fec == 0 ? next : cf;
					boolean f0 = fec == 0;
					if (f0) {
						next++;
					}
					vfifo[vo] = c;
					vo = (vo + (f0 ? 1 : 0)) & 15;
				} else {
					if (fec != 15) {
						c = last + (fec * 2 - 27);
					} else {
						posRef[0] = pos;
						c = last + zig(vbyte(src, posRef));
						pos = posRef[0];
					}
					last = c;
					vfifo[vo] = c;
					vo = (vo + 1) & 15;
				}
				edgeA[eo] = c;
				edgeB[eo] = b;
				eo = (eo + 1) & 15;
				edgeA[eo] = a;
				edgeB[eo] = c;
				eo = (eo + 1) & 15;
				out[o++] = a;
				out[o++] = b;
				out[o++] = c;
			} else if (codetri < 0xFE) {
				int codeaux = src[safeEnd + (codetri & 15)] & 0xFF;
				int feb = codeaux >> 4;
				int fec = codeaux & 15;
				int a = next++;
				int bf = vfifo[(vo - feb) & 15];
				int b = feb == 0 ? next : bf;
				if (feb == 0) {
					next++;
				}
				int cf = vfifo[(vo - fec) & 15];
				int c = fec == 0 ? next : cf;
				if (fec == 0) {
					next++;
				}
				out[o++] = a;
				out[o++] = b;
				out[o++] = c;
				vfifo[vo] = a;
				vo = (vo + 1) & 15;
				vfifo[vo] = b;
				vo = (vo + (feb == 0 ? 1 : 0)) & 15;
				vfifo[vo] = c;
				vo = (vo + (fec == 0 ? 1 : 0)) & 15;
				edgeA[eo] = b;
				edgeB[eo] = a;
				eo = (eo + 1) & 15;
				edgeA[eo] = c;
				edgeB[eo] = b;
				eo = (eo + 1) & 15;
				edgeA[eo] = a;
				edgeB[eo] = c;
				eo = (eo + 1) & 15;
			} else {
				int codeaux = src[pos++] & 0xFF;
				int fea = codetri == 0xFE ? 0 : 15;
				int feb = codeaux >> 4;
				int fec = codeaux & 15;
				if (codeaux == 0) {
					next = 0;
				}
				int a = fea == 0 ? next++ : 0;
				int b = feb == 0 ? next++ : vfifo[(vo - feb) & 15];
				int c = fec == 0 ? next++ : vfifo[(vo - fec) & 15];
				if (fea == 15) {
					posRef[0] = pos;
					last = a = last + zig(vbyte(src, posRef));
					pos = posRef[0];
				}
				if (feb == 15) {
					posRef[0] = pos;
					last = b = last + zig(vbyte(src, posRef));
					pos = posRef[0];
				}
				if (fec == 15) {
					posRef[0] = pos;
					last = c = last + zig(vbyte(src, posRef));
					pos = posRef[0];
				}
				out[o++] = a;
				out[o++] = b;
				out[o++] = c;
				vfifo[vo] = a;
				vo = (vo + 1) & 15;
				vfifo[vo] = b;
				vo = (vo + ((feb == 0 || feb == 15) ? 1 : 0)) & 15;
				vfifo[vo] = c;
				vo = (vo + ((fec == 0 || fec == 15) ? 1 : 0)) & 15;
				edgeA[eo] = b;
				edgeB[eo] = a;
				eo = (eo + 1) & 15;
				edgeA[eo] = c;
				edgeB[eo] = b;
				eo = (eo + 1) & 15;
				edgeA[eo] = a;
				edgeB[eo] = c;
				eo = (eo + 1) & 15;
			}
		}
		if (pos != safeEnd) {
			throw new IllegalStateException("meshopt index data malformed");
		}
		return out;
	}

	private static int vbyte(byte[] d, int[] pos) {
		int lead = d[pos[0]++] & 0xFF;
		if (lead < 128) {
			return lead;
		}
		int result = lead & 127;
		int shift = 7;
		for (int i = 0; i < 4; i++) {
			int g = d[pos[0]++] & 0xFF;
			result |= (g & 127) << shift;
			shift += 7;
			if (g < 128) {
				break;
			}
		}
		return result;
	}

	private static int zig(int v) {
		return (v >>> 1) ^ -(v & 1);
	}
}
