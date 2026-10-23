package dev.stray.client.cs2.s2.zstd;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Stray: replaces aircompressor's sun.misc.Unsafe access with array-backed reads.
 * Bases are always byte[] and addresses are plain array indices.
 */
final class UnsafeUtil {
	static final long ARRAY_BYTE_BASE_OFFSET = 0;
	static final Mem UNSAFE = new Mem();

	private UnsafeUtil() {
	}

	static final class Mem {
		private static final VarHandle S = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
		private static final VarHandle I = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
		private static final VarHandle L = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

		byte getByte(Object base, long a) {
			return ((byte[]) base)[(int) a];
		}

		short getShort(Object base, long a) {
			return (short) S.get((byte[]) base, (int) a);
		}

		int getInt(Object base, long a) {
			return (int) I.get((byte[]) base, (int) a);
		}

		long getLong(Object base, long a) {
			return (long) L.get((byte[]) base, (int) a);
		}

		void putByte(Object base, long a, byte v) {
			((byte[]) base)[(int) a] = v;
		}

		void putShort(Object base, long a, short v) {
			S.set((byte[]) base, (int) a, v);
		}

		void putInt(Object base, long a, int v) {
			I.set((byte[]) base, (int) a, v);
		}

		void putLong(Object base, long a, long v) {
			L.set((byte[]) base, (int) a, v);
		}

		void copyMemory(Object src, long sa, Object dst, long da, long n) {
			System.arraycopy(src, (int) sa, dst, (int) da, (int) n);
		}
	}
}
