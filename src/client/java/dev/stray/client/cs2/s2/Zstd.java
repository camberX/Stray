package dev.stray.client.cs2.s2;

import dev.stray.client.cs2.s2.zstd.ZstdFrameDecompressor;

/** Zstandard frames, via a vendored copy of aircompressor's decoder (Apache 2.0). */
public final class Zstd {
	private Zstd() {
	}

	/** Frame whose decompressed size is not known up front; maxSize bounds it. */
	public static byte[] decompressUnknown(byte[] src, int off, int len, int maxSize) {
		byte[] out = new byte[maxSize];
		int n = new ZstdFrameDecompressor().decompress(src, off, off + len, out, 0, maxSize);
		return java.util.Arrays.copyOf(out, n);
	}

	public static byte[] decompress(byte[] src, int off, int len, int outSize) {
		byte[] out = new byte[outSize];
		int n = new ZstdFrameDecompressor().decompress(src, off, off + len, out, 0, outSize);
		if (n != outSize) {
			throw new IllegalStateException("zstd: expected " + outSize + " got " + n);
		}
		return out;
	}
}
