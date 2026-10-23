package dev.stray.client.cs2;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.cs2.anim.NmClip;
import dev.stray.client.cs2.anim.NmSkeleton;
import dev.stray.client.cs2.anim.Pose;
import dev.stray.client.cs2.s2.Resource;
import dev.stray.client.cs2.s2.S2Model;
import dev.stray.client.cs2.skin.Composer;
import dev.stray.client.cs2.skin.KnifeMaterial;
import dev.stray.client.cs2.skin.PaintKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CS2 knives in the player's hand. Everything is read from the player's own CS2 install
 * on a background thread; the finished knife (mesh, baked skin, animations) is swapped in
 * on the main thread.
 */
public final class Cs2Knives {
	public static final Identifier ALBEDO = Stray.id("cs2/knife_albedo");
	public static final Identifier MATERIAL = Stray.id("cs2/knife_material");
	public static final Identifier BLADE = Stray.id("cs2/knife_blade");
	public static final int SKIN_SIZE = 1024;

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Stray CS2 loader");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/** A knife ready to draw. */
	public static final class Knife {
		public final String id;
		public final String key;
		public final S2Model model;
		public final Pose pose;
		public final Map<String, NmClip> clips;
		final Composer.Result skin;

		Knife(String id, String key, S2Model model, Pose pose, Map<String, NmClip> clips, Composer.Result skin) {
			this.id = id;
			this.key = key;
			this.model = model;
			this.pose = pose;
			this.clips = clips;
			this.skin = skin;
		}

		private org.joml.Matrix4f[] rest;

		/** Bone matrices for the knife at rest in its own model space (no viewmodel rig). */
		public synchronized org.joml.Matrix4f[] restSkin() {
			if (rest == null) {
				rest = new org.joml.Matrix4f[Math.max(1, model.bones.size())];
				for (int i = 0; i < rest.length; i++) {
					rest[i] = new org.joml.Matrix4f();
				}
			}
			return rest;
		}

		/** The clip whose name starts with the prefix, preferring the plainest (shortest) name. */
		public NmClip clip(String prefix) {
			String best = null;
			for (String name : clips.keySet()) {
				if (name.startsWith(prefix) && (best == null || name.length() < best.length()
					|| name.length() == best.length() && name.compareTo(best) < 0)) {
					best = name;
				}
			}
			return best == null ? null : clips.get(best);
		}

		/** Inspect variants (lookat01, lookat02, ...), skipping the draw-into-inspect ones. */
		public java.util.List<NmClip> inspects() {
			java.util.List<String> names = new java.util.ArrayList<>();
			for (String name : clips.keySet()) {
				if (name.startsWith("lookat") && !name.contains("_draw")) {
					names.add(name);
				}
			}
			java.util.Collections.sort(names);
			java.util.List<NmClip> out = new java.util.ArrayList<>();
			for (String name : names) {
				out.add(clips.get(name));
			}
			return out;
		}
	}

	private static volatile Cs2Library library;
	private static volatile boolean opening;
	private static volatile String status = "";
	private static String libraryPath;
	private static Knife current;
	private static String wantedKey;
	private static String loadingKey;
	private static volatile CompletableFuture<Knife> pending;
	private static NmSkeleton viewmodel;
	private static boolean wasInspect;

	private Cs2Knives() {
	}

	public static Cs2Library library() {
		return library;
	}

	public static String status() {
		return status;
	}

	public static Knife current() {
		return current;
	}

	public static boolean enabled() {
		return StrayConfig.get().cs2KnifeEnabled;
	}

	/** True when the held stack should be drawn as the CS2 knife. */
	public static boolean applies(ItemStack stack) {
		if (!enabled() || current == null) {
			return false;
		}
		String rule = StrayConfig.get().cs2ApplyTo;
		if ("empty".equals(rule)) {
			return stack == null || stack.isEmpty();
		}
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if ("held".equals(rule)) {
			return true;
		}
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
			return true;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return id.endsWith("_sword") || id.endsWith("_axe");
	}

	public static String key() {
		StrayConfig c = StrayConfig.get();
		return c.cs2Knife + "|" + c.cs2Finish + "|" + c.cs2Seed + "|" + Math.round(c.cs2Wear * 10000f);
	}

	/** Called every client tick. */
	public static void tick(Minecraft client) {
		StrayConfig c = StrayConfig.get();
		if (!c.cs2KnifeEnabled) {
			return;
		}
		boolean inspect = client.screen == null && dev.stray.client.StrayClient.menuKeyHeld(c.cs2InspectKey);
		if (inspect && !wasInspect) {
			Cs2Viewmodel.inspect();
		}
		wasInspect = inspect;
		ensureLibrary();
		if (library == null) {
			return;
		}
		String key = key();
		wantedKey = key;
		CompletableFuture<Knife> job = pending;
		if (job != null && job.isDone()) {
			pending = null;
			Knife knife = job.getNow(null);
			if (knife != null) {
				upload(knife);
				current = knife;
				status = "";
				Cs2Viewmodel.draw();
			}
			loadingKey = null;
		}
		if (pending == null && (current == null || !current.key.equals(key)) && !key.equals(loadingKey)) {
			loadingKey = key;
			String knifeId = c.cs2Knife;
			String finish = c.cs2Finish;
			int seed = c.cs2Seed;
			float wear = c.cs2Wear;
			status = "Building knife...";
			pending = CompletableFuture.supplyAsync(() -> build(key, knifeId, finish, seed, wear), WORKER);
		}
	}

	/** Reopens the install (after changing the path). */
	public static void reload() {
		library = null;
		libraryPath = null;
		current = null;
		pending = null;
		loadingKey = null;
	}

	private static void ensureLibrary() {
		String path = StrayConfig.get().cs2Path;
		if (library != null && java.util.Objects.equals(path, libraryPath)) {
			return;
		}
		if (opening) {
			return;
		}
		opening = true;
		status = "Looking for Counter-Strike 2...";
		CompletableFuture.runAsync(() -> {
			try {
				// The local build carries its own CS2 data; the install is only a fallback.
				// A local build may carry the data in the jar; release builds download it once from
				// the repository's cs2-data branch. A CS2 install is the last fallback.
				Cs2Library lib = openBundled();
				if (lib == null) {
					lib = openDownloaded();
				}
				if (lib == null) {
					lib = Cs2Library.open(path);
				}
				if (lib == null) {
					status = "Could not get the knife data. Check your connection, then reopen the knife menu.";
				} else {
					Resource skel = lib.assets().resource("animation/skeletons/characters/viewmodel.vnmskel");
					viewmodel = skel == null ? null : new NmSkeleton(skel);
					status = "";
				}
				library = lib;
				libraryPath = path;
			} catch (RuntimeException e) {
				status = "Could not read Counter-Strike 2: " + e.getMessage();
				Stray.LOGGER.warn("CS2 knives: could not open the install", e);
			} finally {
				opening = false;
			}
		}, WORKER);
	}

	/** The pack bundled in the jar, unpacked once to config/stray/cs2.pack. */
	private static Cs2Library openBundled() {
		try (java.io.InputStream in = Cs2Knives.class.getResourceAsStream("/assets/stray/cs2/cs2.pack")) {
			if (in == null) {
				return null;
			}
			java.nio.file.Path out = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("stray/cs2.pack");
			java.nio.file.Files.createDirectories(out.getParent());
			java.nio.file.Path stamp = out.resolveSibling("cs2.pack.version");
			String version = dev.stray.client.cs2.Cs2Knives.class.getPackage().getImplementationVersion() + ":" + packSize();
			boolean fresh = java.nio.file.Files.isRegularFile(out) && java.nio.file.Files.isRegularFile(stamp)
				&& version.equals(java.nio.file.Files.readString(stamp));
			if (!fresh) {
				status = "Unpacking CS2 data...";
				java.nio.file.Path tmp = out.resolveSibling("cs2.pack.tmp");
				java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				java.nio.file.Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				java.nio.file.Files.writeString(stamp, version);
			}
			dev.stray.client.cs2.pack.Cs2Pack pack = new dev.stray.client.cs2.pack.Cs2Pack(out);
			return new Cs2Library(null, pack.paths(), pack.assets());
		} catch (java.io.IOException | RuntimeException e) {
			Stray.LOGGER.warn("CS2 knives: bundled data could not be opened", e);
			return null;
		}
	}

	private static final String DATA_URL = "https://raw.githubusercontent.com/camberX/Stray/cs2-data/";

	/**
	 * The knife data from the cs2-data branch: a manifest (cs2.json) plus the pack in parts under
	 * 100 MB. Downloaded once to config/stray/cs2.pack and refreshed when the manifest's hash
	 * changes; an existing copy is used as-is when offline.
	 */
	private static Cs2Library openDownloaded() {
		java.nio.file.Path out = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("stray/cs2.pack");
		java.nio.file.Path stamp = out.resolveSibling("cs2.pack.sha256");
		try {
			java.nio.file.Files.createDirectories(out.getParent());
			java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
				.followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
				.connectTimeout(java.time.Duration.ofSeconds(15))
				.build();
			com.google.gson.JsonObject manifest = null;
			try {
				var res = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(DATA_URL + "cs2.json"))
					.timeout(java.time.Duration.ofSeconds(20)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() == 200) {
					manifest = com.google.gson.JsonParser.parseString(res.body()).getAsJsonObject();
				}
			} catch (java.io.IOException | InterruptedException | RuntimeException e) {
				Stray.LOGGER.warn("CS2 knives: could not reach the knife data manifest", e);
			}
			boolean have = java.nio.file.Files.isRegularFile(out);
			if (manifest != null) {
				String sha = manifest.get("sha256").getAsString();
				boolean fresh = have && java.nio.file.Files.isRegularFile(stamp)
					&& sha.equalsIgnoreCase(java.nio.file.Files.readString(stamp).trim());
				if (!fresh) {
					long total = manifest.get("size").getAsLong();
					java.nio.file.Path tmp = out.resolveSibling("cs2.pack.download");
					java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
					long done = 0;
					try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(tmp)) {
						for (var part : manifest.getAsJsonArray("parts")) {
							var res = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(DATA_URL + part.getAsString()))
								.timeout(java.time.Duration.ofMinutes(10)).build(), java.net.http.HttpResponse.BodyHandlers.ofInputStream());
							if (res.statusCode() != 200) {
								throw new java.io.IOException("HTTP " + res.statusCode() + " for " + part.getAsString());
							}
							try (java.io.InputStream in = res.body()) {
								byte[] buf = new byte[1 << 16];
								int n;
								while ((n = in.read(buf)) > 0) {
									os.write(buf, 0, n);
									digest.update(buf, 0, n);
									done += n;
									status = String.format(java.util.Locale.ROOT, "Downloading knife data... %d / %d MB", done >> 20, total >> 20);
								}
							}
						}
					}
					String got = java.util.HexFormat.of().formatHex(digest.digest());
					if (!got.equalsIgnoreCase(sha)) {
						java.nio.file.Files.deleteIfExists(tmp);
						throw new java.io.IOException("knife data download is corrupt");
					}
					java.nio.file.Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					java.nio.file.Files.writeString(stamp, sha);
					have = true;
				}
			}
			if (!have) {
				return null;
			}
			dev.stray.client.cs2.pack.Cs2Pack pack = new dev.stray.client.cs2.pack.Cs2Pack(out);
			return new Cs2Library(null, pack.paths(), pack.assets());
		} catch (java.io.IOException | java.security.NoSuchAlgorithmException | InterruptedException | RuntimeException e) {
			Stray.LOGGER.warn("CS2 knives: knife data could not be downloaded", e);
			return null;
		}
	}

	private static long packSize() {
		try {
			var url = Cs2Knives.class.getResource("/assets/stray/cs2/cs2.pack");
			if (url == null) {
				return -1;
			}
			var c = url.openConnection();
			return c.getContentLengthLong();
		} catch (java.io.IOException e) {
			return -1;
		}
	}

	private static Knife build(String key, String knifeId, String finish, int seed, float wear) {
		try {
			Cs2Library lib = library;
			Cs2Library.Knife info = lib.knives().get(knifeId);
			if (info == null) {
				info = lib.knives().values().stream().findFirst().orElse(null);
			}
			if (info == null) {
				status = "No knives found in Counter-Strike 2.";
				return null;
			}
			S2Model model = new S2Model(lib.assets().resource(info.model()));
			KnifeMaterial material = new KnifeMaterial(lib.assets(), model.parts.get(0).material);
			PaintKit kit = finish == null || finish.isEmpty() ? null : lib.kit(finish);
			Composer.Result skin = Composer.compose(lib.assets(), material, kit, seed, wear, SKIN_SIZE);
			NmSkeleton knifeSkeleton = null;
			String knifeSkeletonId = null;
			for (String ref : lib.assets().resource(info.model()).externalRefs().values()) {
				if (ref.endsWith(".vnmskel")) {
					knifeSkeletonId = ref;
					Resource r = lib.assets().resource(ref);
					knifeSkeleton = r == null ? null : new NmSkeleton(r);
				}
			}
			Map<String, NmClip> clips = new HashMap<>();
			String folder = info.model().replaceAll("^.*/(knife_[a-z0-9_]+)/[^/]+$", "$1");
			if (viewmodel != null) {
				for (String path : lib.list("animation/anims/viewmodel/knife/" + folder + "/")) {
					String name = path.substring(path.lastIndexOf('/') + 1).replace(".vnmclip_c", "").toLowerCase(Locale.ROOT);
					if (!path.endsWith(".vnmclip_c") || path.substring(("animation/anims/viewmodel/knife/" + folder + "/").length()).contains("/")) {
						continue;
					}
					Resource r = lib.assets().resource(path);
					if (r != null) {
						clips.put(name, new NmClip(r));
					}
				}
			}
			Pose pose = viewmodel == null ? null : new Pose(viewmodel, knifeSkeleton, model).withKnifeSkeletonId(knifeSkeletonId);
			return new Knife(info.id(), key, model, pose, clips, skin);
		} catch (RuntimeException e) {
			status = "Could not build the knife: " + e.getMessage();
			Stray.LOGGER.warn("CS2 knives: build failed", e);
			return null;
		}
	}

	private static void upload(Knife knife) {
		Composer.Result skin = knife.skin;
		int n = skin.size;
		NativeImage albedo = new NativeImage(n, n, false);
		NativeImage material = new NativeImage(n, n, false);
		NativeImage blade = new NativeImage(n, n, false);
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				int i = y * n + x;
				albedo.setPixel(x, y, skin.albedo[i]);
				int r = skin.material[i * 4] & 0xFF;
				int g = skin.material[i * 4 + 1] & 0xFF;
				int b = skin.material[i * 4 + 2] & 0xFF;
				int a = skin.material[i * 4 + 3] & 0xFF;
				material.setPixel(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
				// Blade mask for the held item shader: metal (paintable) parts are opaque.
				int bladeAlpha = Math.max(a, g);
				blade.setPixel(x, y, (bladeAlpha << 24) | 0xFFFFFF);
			}
		}
		var textures = Minecraft.getInstance().getTextureManager();
		textures.register(ALBEDO, new DynamicTexture(() -> "stray-cs2-albedo", albedo));
		textures.register(MATERIAL, new DynamicTexture(() -> "stray-cs2-material", material));
		textures.register(BLADE, new DynamicTexture(() -> "stray-cs2-blade", blade));
	}

	public static List<Cs2Library.Knife> knives() {
		Cs2Library lib = library;
		return lib == null ? List.of() : List.copyOf(lib.knives().values());
	}
}
