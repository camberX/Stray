package dev.stray.client.cs2.pack;

import dev.stray.client.cs2.Cs2Library;
import dev.stray.client.cs2.s2.Resource;
import dev.stray.client.cs2.s2.S2Model;
import dev.stray.client.cs2.s2.Vtex;
import dev.stray.client.cs2.skin.Assets;
import dev.stray.client.cs2.skin.KnifeMaterial;
import dev.stray.client.cs2.skin.PaintKit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

/**
 * Builds Stray's CS2 data pack from a CS2 install: every knife, its finishes, animations
 * and preview images. Run once (gradlew cs2Pack); the result goes into the local jar.
 */
public final class Cs2PackBuilder {
	private static final int SKIN = 1024;
	/** Knife input maps (masks, AO, surface, position) are smooth; half size is plenty. */
	private static final int INPUT = 512;
	private static final int PREVIEW = 128;

	private final Cs2Library lib;
	private final Assets assets;
	private final Map<String, int[]> wanted = new LinkedHashMap<>(); // path -> {type hint, max size}

	public Cs2PackBuilder(Cs2Library lib) {
		this.lib = lib;
		this.assets = lib.assets();
	}

	public static void main(String[] args) throws IOException {
		Path out = Path.of(args.length > 0 ? args[0] : "cs2.pack");
		Cs2Library lib = Cs2Library.open(args.length > 1 ? args[1] : null);
		if (lib == null) {
			System.err.println("Counter-Strike 2 was not found.");
			System.exit(1);
		}
		System.out.println("Reading CS2 from " + lib.csgo());
		new Cs2PackBuilder(lib).write(out);
	}

	private void raw(String path) {
		wanted.putIfAbsent(Assets.compiled(path), new int[]{Cs2Pack.RAW, 0});
	}

	private void texture(String path, int max) {
		if (path == null || path.isEmpty()) {
			return;
		}
		String p = Assets.compiled(path);
		int[] prev = wanted.get(p);
		if (prev == null || prev[1] < max) {
			wanted.put(p, new int[]{Cs2Pack.RGBA8, max});
		}
	}

	public void collect() {
		raw("scripts/items/items_game.txt");
		raw("resource/csgo_english.txt");
		raw("animation/skeletons/characters/viewmodel.vnmskel");
		raw("weapons/paints/legacy/_shared_paint_generic.vcompmat");
		for (Cs2Library.Knife knife : lib.knives().values()) {
			raw(knife.model());
			Resource model = assets.resource(knife.model());
			if (model == null) {
				continue;
			}
			for (String ref : model.externalRefs().values()) {
				if (ref.endsWith(".vnmskel")) {
					raw(ref);
				}
			}
			S2Model m = new S2Model(model);
			for (S2Model.Part part : m.parts) {
				if (part.material == null) {
					continue;
				}
				raw(part.material);
				KnifeMaterial mat = new KnifeMaterial(assets, part.material);
				for (String t : mat.base.values()) {
					texture(t, SKIN);
				}
				for (Map.Entry<String, String> t : mat.inputs.entrySet()) {
					texture(t.getValue(), "g_tPosition".equals(t.getKey()) ? INPUT / 2 : INPUT);
				}
				compositeInputs(part.material);
			}
			String folder = knife.model().replaceAll("^.*/(knife_[a-z0-9_]+)/[^/]+$", "$1");
			for (String clip : lib.list("animation/anims/viewmodel/knife/" + folder + "/")) {
				if (clip.endsWith(".vnmclip_c")) {
					raw(clip);
				}
			}
			texture(lib.previewPath(knife.id(), ""), PREVIEW);
			for (Cs2Library.Finish finish : knife.finishes()) {
				if (finish.kit().isEmpty()) {
					continue;
				}
				texture(lib.previewPath(knife.id(), finish.kit()), PREVIEW);
				raw("materials/models/weapons/customization/paints/vmats/" + finish.kit() + ".vmat");
				raw("weapons/paints/legacy/" + finish.kit() + ".vcompmat");
				PaintKit kit = lib.kit(finish.kit());
				if (kit != null) {
					for (String t : kit.textures.values()) {
						texture(t, SKIN);
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void compositeInputs(String material) {
		Resource r = assets.resource(material);
		if (r == null) {
			return;
		}
		Map<String, Object> d = (Map<String, Object>) r.kv3("DATA");
		for (Object o : (List<Object>) d.getOrDefault("m_stringAttributes", List.of())) {
			Map<String, Object> m = (Map<String, Object>) o;
			if ("composite_inputs".equals(m.get("m_name"))) {
				raw(String.valueOf(m.get("m_value")).replace('\\', '/'));
			}
		}
	}

	public void write(Path out) throws IOException {
		collect();
		System.out.println("Packing " + wanted.size() + " files...");
		Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
		ByteArrayOutputStream index = new ByteArrayOutputStream();
		ByteBuffer rec = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
		Path data = out.resolveSibling(out.getFileName() + ".data");
		long offset = 0;
		int count = 0;
		long rawTotal = 0;
		try (OutputStream body = Files.newOutputStream(data)) {
			for (Map.Entry<String, int[]> w : wanted.entrySet()) {
				String path = w.getKey();
				int type = w.getValue()[0];
				int max = w.getValue()[1];
				byte[] bytes;
				int width = 0;
				int height = 0;
				if (type == Cs2Pack.RAW) {
					bytes = assets.read(path);
				} else {
					Resource r = assets.resource(path);
					if (r == null) {
						continue;
					}
					Vtex v = new Vtex(r);
					int mip = 0;
					while (mip < v.mips - 1 && v.mipWidth(mip) > max) {
						mip++;
					}
					width = v.mipWidth(mip);
					height = v.mipHeight(mip);
					if (width <= max && v.blockCompressed()) {
						// Keep the game's own block compression for this mip: about 4x smaller than RGBA.
						byte[] mipBytes = v.mipBytes(mip);
						bytes = new byte[mipBytes.length + 1];
						bytes[0] = (byte) v.format;
						System.arraycopy(mipBytes, 0, bytes, 1, mipBytes.length);
						type = Cs2Pack.VTEX;
						byte[] packedV = deflate(bytes);
						body.write(packedV);
						byte[] nameV = path.getBytes(StandardCharsets.UTF_8);
						rec.clear();
						rec.putShort((short) nameV.length).put(nameV).put((byte) type).putShort((short) width).putShort((short) height)
							.putLong(offset).putInt(packedV.length).putInt(bytes.length);
						index.write(rec.array(), 0, rec.position());
						offset += packedV.length;
						rawTotal += bytes.length;
						count++;
						continue;
					}
					boolean floats = v.format == Vtex.RGBA16161616F || v.format == 14;
					if (floats) {
						type = Cs2Pack.RGBA16F;
						float[] f = v.rgbaFloat(mip);
						ByteBuffer b = ByteBuffer.allocate(f.length * 2).order(ByteOrder.LITTLE_ENDIAN);
						for (float x : f) {
							b.putShort(Float.floatToFloat16(x));
						}
						bytes = b.array();
					} else {
						bytes = v.rgba(mip);
					}
					// Images larger than the cap (single-mip textures) are halved down.
					while (width > max && width > 1) {
						float[] f = new float[width * height * 4];
						if (type == Cs2Pack.RGBA16F) {
							ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
							for (int i = 0; i < f.length; i++) {
								f[i] = Float.float16ToFloat(b.getShort());
							}
						} else {
							for (int i = 0; i < f.length; i++) {
								f[i] = (bytes[i] & 0xFF) / 255f;
							}
						}
						var t = Cs2Pack.half(new dev.stray.client.cs2.skin.Tex(width, height, f));
						width = t.w;
						height = t.h;
						if (type == Cs2Pack.RGBA16F) {
							ByteBuffer b = ByteBuffer.allocate(t.px.length * 2).order(ByteOrder.LITTLE_ENDIAN);
							for (float x : t.px) {
								b.putShort(Float.floatToFloat16(x));
							}
							bytes = b.array();
						} else {
							bytes = new byte[t.px.length];
							for (int i = 0; i < bytes.length; i++) {
								bytes[i] = (byte) Math.max(0, Math.min(255, Math.round(t.px[i] * 255f)));
							}
						}
					}
				}
				if (bytes == null) {
					continue;
				}
				byte[] packed = deflate(bytes);
				body.write(packed);
				byte[] name = path.getBytes(StandardCharsets.UTF_8);
				rec.clear();
				rec.putShort((short) name.length).put(name).put((byte) type).putShort((short) width).putShort((short) height)
					.putLong(offset).putInt(packed.length).putInt(bytes.length);
				index.write(rec.array(), 0, rec.position());
				offset += packed.length;
				rawTotal += bytes.length;
				count++;
			}
		}
		byte[] idx = index.toByteArray();
		long base = 8 + 4 + 4 + idx.length;
		// Offsets in the index are relative to the data section; shift them now that its start is known.
		ByteBuffer ib = ByteBuffer.wrap(idx).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < count; i++) {
			int len = ib.getShort() & 0xFFFF;
			ib.position(ib.position() + len + 1 + 2 + 2);
			long off = ib.getLong(ib.position());
			ib.putLong(ib.position(), off + base);
			ib.position(ib.position() + 8 + 4 + 4);
		}
		try (OutputStream o = Files.newOutputStream(tmp)) {
			o.write(Cs2Pack.MAGIC.getBytes(StandardCharsets.US_ASCII));
			o.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(count).putInt(idx.length).array());
			o.write(idx);
			Files.copy(data, o);
		}
		Files.delete(data);
		Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		System.out.printf("Wrote %s: %d files, %.1f MB (%.1f MB unpacked)%n", out, count, Files.size(out) / 1e6, rawTotal / 1e6);
	}

	private static byte[] deflate(byte[] in) {
		Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
		d.setInput(in);
		d.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, in.length / 3));
		byte[] buf = new byte[65536];
		while (!d.finished()) {
			out.write(buf, 0, d.deflate(buf));
		}
		d.end();
		return out.toByteArray();
	}
}
