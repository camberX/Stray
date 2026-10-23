package dev.stray.client.cs2.s2;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Reads Valve VPK v2 packages (pak01_dir.vpk plus pak01_NNN.vpk archives). Read only. */
public final class Vpk implements AutoCloseable {
	public record Entry(String path, int archive, int offset, int length, byte[] preload) {
	}

	private static final int MAGIC = 0x55AA1234;
	private final Path dir;
	private final String prefix;
	private final Map<String, Entry> entries = new HashMap<>();
	private final Map<Integer, RandomAccessFile> archives = new HashMap<>();
	private byte[] dirFile;
	private int dataStart;

	public Vpk(Path dirVpk) throws IOException {
		this.dir = dirVpk.getParent();
		String name = dirVpk.getFileName().toString();
		this.prefix = name.substring(0, name.length() - "_dir.vpk".length());
		dirFile = Files.readAllBytes(dirVpk);
		ByteBuffer b = ByteBuffer.wrap(dirFile).order(ByteOrder.LITTLE_ENDIAN);
		if (b.getInt() != MAGIC) {
			throw new IOException("not a vpk");
		}
		int version = b.getInt();
		int treeSize = b.getInt();
		int header = 12;
		if (version == 2) {
			b.getInt();
			b.getInt();
			b.getInt();
			b.getInt();
			header = 28;
		}
		dataStart = header + treeSize;
		while (true) {
			String ext = cstr(b);
			if (ext.isEmpty()) {
				break;
			}
			while (true) {
				String folder = cstr(b);
				if (folder.isEmpty()) {
					break;
				}
				while (true) {
					String file = cstr(b);
					if (file.isEmpty()) {
						break;
					}
					b.getInt(); // crc
					int preload = b.getShort() & 0xFFFF;
					int archive = b.getShort() & 0xFFFF;
					int offset = b.getInt();
					int length = b.getInt();
					b.getShort(); // terminator 0xFFFF
					byte[] pre = new byte[preload];
					b.get(pre);
					String path = (folder.equals(" ") ? "" : folder + "/") + file + (ext.equals(" ") ? "" : "." + ext);
					entries.put(path.toLowerCase(), new Entry(path, archive, offset, length, pre));
				}
			}
		}
	}

	private static String cstr(ByteBuffer b) {
		int start = b.position();
		while (b.get() != 0) {
		}
		return new String(b.array(), start, b.position() - start - 1, StandardCharsets.UTF_8);
	}

	public Map<String, Entry> entries() {
		return entries;
	}

	public Entry find(String path) {
		return entries.get(path.toLowerCase().replace('\\', '/'));
	}

	public byte[] read(String path) throws IOException {
		Entry e = find(path);
		return e == null ? null : read(e);
	}

	public synchronized byte[] read(Entry e) throws IOException {
		byte[] out = new byte[e.preload.length + e.length];
		System.arraycopy(e.preload, 0, out, 0, e.preload.length);
		if (e.length > 0) {
			if (e.archive == 0x7FFF) {
				System.arraycopy(dirFile, dataStart + e.offset, out, e.preload.length, e.length);
			} else {
				RandomAccessFile raf = archives.get(e.archive);
				if (raf == null) {
					raf = new RandomAccessFile(dir.resolve(String.format("%s_%03d.vpk", prefix, e.archive)).toFile(), "r");
					archives.put(e.archive, raf);
				}
				raf.seek(e.offset & 0xFFFFFFFFL);
				raf.readFully(out, e.preload.length, e.length);
			}
		}
		return out;
	}

	@Override
	public synchronized void close() throws IOException {
		for (RandomAccessFile raf : archives.values()) {
			raf.close();
		}
		archives.clear();
	}
}
