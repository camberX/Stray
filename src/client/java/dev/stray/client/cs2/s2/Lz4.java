package dev.stray.client.cs2.s2;

/** LZ4 block decoder. Decodes into a shared output so later blocks can reference earlier ones (chained frames). */
public final class Lz4 {
	private Lz4() {
	}

	/** Decodes one block from src[sp, sp+len) into dst starting at dp; returns the new dst position. */
	public static int decode(byte[] src, int sp, int len, byte[] dst, int dp, int dstEnd) {
		int end = sp + len;
		while (sp < end) {
			int token = src[sp++] & 0xFF;
			int lit = token >>> 4;
			if (lit == 15) {
				int b;
				do {
					b = src[sp++] & 0xFF;
					lit += b;
				} while (b == 255);
			}
			System.arraycopy(src, sp, dst, dp, lit);
			sp += lit;
			dp += lit;
			if (sp >= end) {
				break;
			}
			int offset = (src[sp] & 0xFF) | ((src[sp + 1] & 0xFF) << 8);
			sp += 2;
			int match = token & 15;
			if (match == 15) {
				int b;
				do {
					b = src[sp++] & 0xFF;
					match += b;
				} while (b == 255);
			}
			match += 4;
			int from = dp - offset;
			if (offset >= match) {
				System.arraycopy(dst, from, dst, dp, match);
				dp += match;
			} else {
				for (int i = 0; i < match; i++) {
					dst[dp++] = dst[from + i];
				}
			}
			if (dp > dstEnd) {
				throw new IllegalStateException("lz4 overrun");
			}
		}
		return dp;
	}

	public static byte[] decode(byte[] src, int sp, int len, int outSize) {
		byte[] out = new byte[outSize];
		int n = decode(src, sp, len, out, 0, outSize);
		if (n != outSize) {
			throw new IllegalStateException("lz4: expected " + outSize + " got " + n);
		}
		return out;
	}
}
