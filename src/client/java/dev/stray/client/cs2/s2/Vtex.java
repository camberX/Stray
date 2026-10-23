package dev.stray.client.cs2.s2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Source 2 texture (.vtex_c): decodes one mip level of the first face/slice to RGBA8. */
public final class Vtex {
	public final int width;
	public final int height;
	public final int depth;
	public final int format;
	public final int mips;
	public final int flags;
	private final byte[] data;
	private final int dataOffset;
	private int[] compressedMips;
	private int mipCompression;

	public static final int DXT1 = 1, DXT5 = 2, I8 = 3, RGBA8888 = 4, BC6H = 19, BC7 = 20, ATI2N = 21, IA88 = 22,
		ATI1N = 27, BGRA8888 = 28, R16F = 8, RGBA16161616F = 10, PNG_RGBA = 16, PNG_DXT5 = 18, R8 = 33, A8 = 32;

	public Vtex(Resource res) {
		Resource.Block block = res.block("DATA");
		data = res.data;
		dataOffset = block.offset() + block.size();
		ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		b.position(block.offset());
		int version = b.getShort() & 0xFFFF;
		if (version != 1) {
			throw new IllegalStateException("vtex version " + version);
		}
		flags = b.getShort() & 0xFFFF;
		b.position(b.position() + 16); // reflectivity
		width = b.getShort() & 0xFFFF;
		height = b.getShort() & 0xFFFF;
		depth = b.getShort() & 0xFFFF;
		format = b.get() & 0xFF;
		mips = b.get() & 0xFF;
		b.getInt(); // picmip0
		int extraPos = b.position();
		int extraOffset = b.getInt();
		int extraCount = b.getInt();
		if (extraCount > 0) {
			b.position(extraPos + extraOffset);
			for (int i = 0; i < extraCount; i++) {
				int type = b.getInt();
				int entryPos = b.position();
				int off = b.getInt();
				int size = b.getInt();
				int next = b.position();
				if (type == 4) { // COMPRESSED_MIP_SIZE
					b.position(entryPos + off);
					mipCompression = b.getInt();
					int mipsPos = b.position();
					int mipsOffset = b.getInt();
					int count = b.getInt();
					b.position(mipsPos + mipsOffset);
					compressedMips = new int[count];
					for (int m = 0; m < count; m++) {
						compressedMips[m] = b.getInt();
					}
				}
				b.position(next);
			}
		}
	}

	public boolean blockCompressed() {
		return format == DXT1 || format == DXT5 || format == BC6H || format == BC7 || format == ATI1N || format == ATI2N;
	}

	private int blockBytes() {
		return switch (format) {
			case DXT1, ATI1N -> 8;
			case DXT5, BC6H, BC7, ATI2N -> 16;
			case RGBA8888, BGRA8888 -> 4;
			case IA88 -> 2;
			case RGBA16161616F -> 8;
			case R16F -> 2;
			default -> 1;
		};
	}

	private int faces() {
		return (flags & (1 << 4)) != 0 ? 6 : 1;
	}

	private int mipDepth(int mip) {
		return (flags & (1 << 5)) == 0 ? depth : Math.max(1, depth >> mip);
	}

	public int mipSize(int mip) {
		int w = Math.max(1, width >> mip);
		int h = Math.max(1, height >> mip);
		int d = mipDepth(mip) * faces();
		if (blockCompressed()) {
			w = Math.max(4, (w + 3) & ~3);
			h = Math.max(4, (h + 3) & ~3);
			if (d < 4 && d > 1) {
				d = 4;
			}
			return (w * h >> 4) * d * blockBytes();
		}
		return w * h * d * blockBytes();
	}

	/** Raw (uncompressed from LZ4) bytes of a mip level. Mips are stored smallest first. */
	public byte[] mipBytes(int mip) {
		int pos = dataOffset;
		for (int m = mips - 1; m > mip; m--) {
			int size = mipSize(m);
			if (compressedMips != null && compressedMips[m] < size) {
				size = compressedMips[m];
			}
			pos += size;
		}
		int size = mipSize(mip);
		if (compressedMips != null && compressedMips[mip] < size) {
			if (mipCompression == 2) {
				return Zstd.decompress(data, pos, compressedMips[mip], size);
			}
			return Lz4.decode(data, pos, compressedMips[mip], size);
		}
		byte[] out = new byte[size];
		System.arraycopy(data, pos, out, 0, size);
		return out;
	}

	public int mipWidth(int mip) {
		return Math.max(1, width >> mip);
	}

	public int mipHeight(int mip) {
		return Math.max(1, height >> mip);
	}

	/** RGBA8 pixels of the first face/slice of a mip. */
	public byte[] rgba(int mip) {
		return decode(format, mipBytes(mip), mipWidth(mip), mipHeight(mip));
	}

	/** Decodes raw mip bytes of a given vtex format to RGBA8. */
	public static byte[] decode(int format, byte[] raw, int w, int h) {
		switch (format) {
			case DXT1:
				return Bcn.decode(raw, 0, w, h, 8, Bcn.BC1);
			case DXT5:
				return Bcn.decode(raw, 0, w, h, 16, Bcn.BC3);
			case ATI1N:
				return Bcn.decode(raw, 0, w, h, 8, Bcn.BC4);
			case ATI2N:
				return Bcn.decode(raw, 0, w, h, 16, Bcn.BC5);
			case BC7:
				return Bcn.decode(raw, 0, w, h, 16, Bcn.BC7);
			case RGBA8888: {
				byte[] out = new byte[w * h * 4];
				System.arraycopy(raw, 0, out, 0, out.length);
				return out;
			}
			case BGRA8888: {
				byte[] out = new byte[w * h * 4];
				for (int i = 0; i < w * h; i++) {
					out[i * 4] = raw[i * 4 + 2];
					out[i * 4 + 1] = raw[i * 4 + 1];
					out[i * 4 + 2] = raw[i * 4];
					out[i * 4 + 3] = raw[i * 4 + 3];
				}
				return out;
			}
			case I8:
			case R8:
			case A8: {
				byte[] out = new byte[w * h * 4];
				for (int i = 0; i < w * h; i++) {
					out[i * 4] = out[i * 4 + 1] = out[i * 4 + 2] = raw[i];
					out[i * 4 + 3] = format == A8 ? raw[i] : (byte) 255;
				}
				return out;
			}
			case IA88: {
				byte[] out = new byte[w * h * 4];
				for (int i = 0; i < w * h; i++) {
					out[i * 4] = out[i * 4 + 1] = out[i * 4 + 2] = raw[i * 2];
					out[i * 4 + 3] = raw[i * 2 + 1];
				}
				return out;
			}
			case RGBA16161616F: {
				byte[] out = new byte[w * h * 4];
				ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
				for (int i = 0; i < w * h * 4; i++) {
					float f = Float.float16ToFloat(b.getShort());
					out[i] = (byte) Math.max(0, Math.min(255, Math.round(f * 255f)));
				}
				return out;
			}
			default:
				throw new IllegalStateException("unsupported vtex format " + format);
		}
	}

	/** Float RGBA of a 16-bit float texture (position maps are pfm / RGBA16F). */
	public float[] rgbaFloat(int mip) {
		return decodeFloat(format, mipBytes(mip), mipWidth(mip), mipHeight(mip));
	}

	public static float[] decodeFloat(int format, byte[] raw, int w, int h) {
		float[] out = new float[w * h * 4];
		ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
		if (format == RGBA16161616F) {
			for (int i = 0; i < out.length; i++) {
				out[i] = Float.float16ToFloat(b.getShort());
			}
		} else if (format == 14) { // RGBA32323232F
			for (int i = 0; i < out.length; i++) {
				out[i] = b.getFloat();
			}
		} else {
			byte[] rgba = decode(format, raw, w, h);
			for (int i = 0; i < out.length; i++) {
				out[i] = (rgba[i] & 0xFF) / 255f;
			}
		}
		return out;
	}
}
