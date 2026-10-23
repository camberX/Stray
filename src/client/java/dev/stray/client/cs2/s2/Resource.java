package dev.stray.client.cs2.s2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Source 2 compiled resource (_c files): a header and a list of typed blocks. */
public final class Resource {
	public record Block(String type, int offset, int size) {
	}

	public final byte[] data;
	public final int version;
	public final Map<String, Block> blocks = new LinkedHashMap<>();
	public final java.util.List<Block> list = new java.util.ArrayList<>();

	public Resource(byte[] data) {
		this.data = data;
		ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		b.getInt(); // file size
		int headerVersion = b.getShort() & 0xFFFF;
		if (headerVersion != 12) {
			throw new IllegalStateException("not a resource (header " + headerVersion + ")");
		}
		version = b.getShort() & 0xFFFF;
		int blockOffset = b.getInt();
		int count = b.getInt();
		b.position(b.position() + blockOffset - 8);
		for (int i = 0; i < count; i++) {
			byte[] t = new byte[4];
			b.get(t);
			int pos = b.position();
			int offset = pos + b.getInt();
			int size = b.getInt();
			String type = new String(t, java.nio.charset.StandardCharsets.US_ASCII);
			Block blk = new Block(type, offset, size);
			list.add(blk);
			blocks.putIfAbsent(type, blk);
			b.position(pos + 8);
		}
	}

	public Block block(String type) {
		return blocks.get(type);
	}

	/** KV3 content of a block, or null when the block is missing or not KV3. */
	public Object kv3(String type) {
		return kv3(blocks.get(type));
	}

	public Object kv3(Block block) {
		if (block == null || block.size < 4) {
			return null;
		}
		int magic = ByteBuffer.wrap(data, block.offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
		if (!Kv3.isKv3(magic)) {
			return null;
		}
		return Kv3.read(data, block.offset, block.size);
	}

	/** External references (RERL): resource ids to names. */
	public Map<Long, String> externalRefs() {
		Map<Long, String> out = new LinkedHashMap<>();
		Block block = blocks.get("RERL");
		if (block == null) {
			return out;
		}
		ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		b.position(block.offset);
		int pos = b.position();
		int off = b.getInt();
		int count = b.getInt();
		b.position(pos + off);
		for (int i = 0; i < count; i++) {
			long id = b.getLong();
			int namePos = b.position();
			int nameOff = b.getInt();
			b.getInt();
			int s = namePos + nameOff;
			int e = s;
			while (data[e] != 0) {
				e++;
			}
			out.put(id, new String(data, s, e - s, java.nio.charset.StandardCharsets.UTF_8));
			b.position(namePos + 8);
		}
		return out;
	}
}
