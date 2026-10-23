package dev.stray.client.cs2.skin;

import dev.stray.client.cs2.s2.Resource;
import dev.stray.client.cs2.s2.Vtex;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reads compiled CS2 files by game path ("materials/x.vtex" or with _c) from a VPK or a folder. */
public abstract class Assets {
	private final Map<String, Object> cache = new ConcurrentHashMap<>();

	/** Raw bytes of a compiled file (path already ends in _c), or null if absent. */
	protected abstract byte[] readRaw(String path) throws IOException;

	public abstract boolean exists(String path);

	public static String compiled(String path) {
		String p = path.replace('\\', '/');
		return p.endsWith("_c") || p.endsWith(".txt") ? p : p + "_c";
	}

	public byte[] read(String path) {
		try {
			return readRaw(compiled(path));
		} catch (IOException e) {
			return null;
		}
	}

	public Resource resource(String path) {
		String key = "res:" + compiled(path);
		Object hit = cache.get(key);
		if (hit != null) {
			return (Resource) hit;
		}
		byte[] b = read(path);
		Resource r = b == null ? null : new Resource(b);
		if (r != null) {
			cache.put(key, r);
		}
		return r;
	}

	/** Decoded texture sampler at a mip whose width is at most maxSize. */
	public Tex texture(String path, int maxSize, boolean floats) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		String key = "tex:" + compiled(path) + ":" + maxSize;
		Object hit = cache.get(key);
		if (hit != null) {
			return (Tex) hit;
		}
		Resource r = resource(path);
		if (r == null) {
			return null;
		}
		Vtex v = new Vtex(r);
		int mip = 0;
		while (mip < v.mips - 1 && v.mipWidth(mip) > maxSize) {
			mip++;
		}
		Tex t = new Tex(v.mipWidth(mip), v.mipHeight(mip), v.rgbaFloat(mip));
		cache.put(key, t);
		return t;
	}

	public void clearCache() {
		cache.clear();
	}
}
