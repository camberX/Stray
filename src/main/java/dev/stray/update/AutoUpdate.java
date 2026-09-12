package dev.stray.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Optional launch gate. When {@code autoUpdate} is on, Minecraft waits here
 * for stray.gay. A newer jar is written into mods, the old one is
 * retired, and this process exits so the next launch loads the new jar.
 */
public final class AutoUpdate implements PreLaunchEntrypoint {
	private static final String DOWNLOAD = UpdateMeta.SHOP + "/download";
	private static final long MAX_BYTES = 96L * 1024L * 1024L;

	@Override
	public void onPreLaunch() {
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(Stray.MOD_ID);
		if (container.isEmpty()) {
			return;
		}
		Path current = jarPath(container.get());
		if (current == null) {
			if (enabled()) {
				log("Auto-update skipped (dev run, not a jar).");
			}
			return;
		}
		Path mods = current.getParent();
		if (mods != null) {
			sweep(mods, current);
		}
		if (!enabled()) {
			return;
		}
		String installed = container.get().getMetadata().getVersion().getFriendlyString();
		log("Checking stray.gay for a newer jar (you have " + installed + ")…");
		try {
			Remote remote = fetchRemote();
			if (remote == null) {
				log("No update info. Continuing launch.");
				return;
			}
			if (UpdateMeta.compare(remote.version, installed) <= 0) {
				log("Already up to date (" + installed + ").");
				return;
			}
			log("Found " + remote.version + ". Downloading and closing Minecraft so the new jar can load.");
			Path dest = apply(current, remote);
			if (dest == null) {
				log("Update failed. Continuing with " + installed + ".");
				return;
			}
			log("Updated to " + remote.version + " at " + dest.getFileName() + ". Closing Minecraft.");
			killGame();
		} catch (Exception exception) {
			log("Update check failed: " + exception.getMessage());
			Stray.LOGGER.warn("Auto-update failed", exception);
		}
	}

	private static boolean enabled() {
		Path dir = FabricLoader.getInstance().getConfigDir();
		Path path = dir.resolve("stray.json");
		if (!Files.isRegularFile(path)) {
			path = dir.resolve("voidmark.json");
		}
		if (!Files.isRegularFile(path)) {
			return false;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			return json.has("autoUpdate") && json.get("autoUpdate").getAsBoolean();
		} catch (Exception ignored) {
			return false;
		}
	}

	private static Path jarPath(ModContainer container) {
		List<Path> paths = container.getOrigin().getPaths();
		if (paths == null || paths.isEmpty()) {
			return null;
		}
		Path path = paths.getFirst();
		if (path == null || !Files.isRegularFile(path)) {
			return null;
		}
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".jar") ? path.toAbsolutePath().normalize() : null;
	}

	private static Remote fetchRemote() {
		HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(6))
			.build();
		JsonObject json = null;
		String version = null;
		for (String url : List.of(
			UpdateMeta.META,
			UpdateMeta.GITHUB_META,
			UpdateMeta.EISENMANN_GITHUB_META,
			UpdateMeta.LEGACY_GITHUB_META
		)) {
			JsonObject candidate = UpdateMeta.getJson(http, url, 12);
			if (candidate == null || !candidate.has("version")) {
				continue;
			}
			String next = candidate.get("version").getAsString().trim();
			if (next.isEmpty()) {
				continue;
			}
			if (version == null || UpdateMeta.compare(next, version) > 0) {
				json = candidate;
				version = next;
			}
		}
		if (json == null || version == null) {
			return null;
		}
		String file = json.has("file") ? json.get("file").getAsString().trim() : "stray-" + version + ".jar";
		if (file.isBlank() || file.contains("/") || file.contains("\\") || !file.endsWith(".jar")) {
			file = "stray-" + version + ".jar";
		}
		String download = DOWNLOAD;
		if (json.has("url")) {
			String url = json.get("url").getAsString().trim();
			if (url.startsWith("https://stray.gay/")) {
				download = url;
			} else if (url.startsWith("/")) {
				download = UpdateMeta.SHOP + url;
			}
		}
		List<String> urls = new ArrayList<>();
		urls.add(download);
		urls.add(UpdateMeta.SHOP + "/stray.jar");
		urls.add(UpdateMeta.SHOP + "/eisenmann.jar");
		urls.add(UpdateMeta.SHOP + "/voidmark.jar");
		urls.add("https://raw.githubusercontent.com/camberX/Stray/main/web/public/mod/" + file);
		urls.add("https://raw.githubusercontent.com/camberX/Eisenmann/main/web/public/mod/" + file);
		urls.add("https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/" + file);
		urls.add("https://raw.githubusercontent.com/camberX/Stray/main/web/public/mod/stray.jar");
		urls.add("https://raw.githubusercontent.com/camberX/Eisenmann/main/web/public/mod/stray.jar");
		urls.add("https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/stray.jar");
		urls.add("https://raw.githubusercontent.com/camberX/Eisenmann/main/web/public/mod/eisenmann.jar");
		urls.add("https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/eisenmann.jar");
		return new Remote(version, file, urls);
	}

	private static Path apply(Path current, Remote remote) throws Exception {
		Path mods = current.getParent() == null ? null : current.getParent().toAbsolutePath().normalize();
		if (mods == null) {
			return null;
		}
		Path dest = mods.resolve(remote.file).toAbsolutePath().normalize();
		if (!mods.equals(dest.getParent()) || dest.equals(current)) {
			dest = mods.resolve("stray-" + remote.version + ".jar");
		}
		if (dest.equals(current)) {
			dest = mods.resolve("stray-" + remote.version + "-new.jar");
		}
		Path part = dest.resolveSibling(dest.getFileName() + ".part");
		Files.deleteIfExists(part);
		HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(8))
			.build();
		boolean downloaded = false;
		for (String url : remote.urls) {
			if (!download(http, url, part)) {
				continue;
			}
			if (validJar(part, remote)) {
				downloaded = true;
				break;
			}
			String found = jarVersion(part);
			log("Skipped " + url + " (got " + (found == null ? "an invalid jar" : "Stray " + found) + ", wanted " + remote.version + ").");
			Files.deleteIfExists(part);
		}
		if (!downloaded) {
			log("Could not download a valid " + remote.file + " from any mirror.");
			Files.deleteIfExists(part);
			return null;
		}
		try {
			Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception ignored) {
			Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
		}
		for (Path old : staleJars(mods, dest)) {
			retire(old);
		}
		sweep(mods, dest);
		return dest;
	}

	private static boolean download(HttpClient http, String url, Path part) {
		try {
			HttpResponse<InputStream> response = http.send(
				request(UpdateMeta.cacheBust(url), 90).header("Accept", "application/java-archive,application/octet-stream,*/*").build(),
				HttpResponse.BodyHandlers.ofInputStream()
			);
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return false;
			}
			try (InputStream in = response.body()) {
				Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
			}
			long size = Files.size(part);
			if (size <= 64 || size > MAX_BYTES) {
				log("Rejected " + url + " (" + size + " bytes).");
				Files.deleteIfExists(part);
				return false;
			}
			return true;
		} catch (Exception exception) {
			log("Download from " + url + " failed: " + exception.getMessage());
			try {
				Files.deleteIfExists(part);
			} catch (Exception ignoredToo) {
			}
			return false;
		}
	}

	private static HttpRequest.Builder request(String url, int timeoutSec) {
		return HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(timeoutSec))
			.header("User-Agent", "Stray-AutoUpdate")
			.GET();
	}

	private static boolean validJar(Path path, Remote remote) {
		String version = jarVersion(path);
		return version != null && remote.version.equals(version);
	}

	private static String jarVersion(Path path) {
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path.toFile())) {
			java.util.zip.ZipEntry entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				return null;
			}
			try (Reader reader = new java.io.InputStreamReader(zip.getInputStream(entry))) {
				JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
				if (!metadata.has("id") || !Stray.MOD_ID.equals(metadata.get("id").getAsString())) {
					return null;
				}
				if (!metadata.has("version")) {
					return null;
				}
				String version = metadata.get("version").getAsString().trim();
				return version.isEmpty() ? null : version;
			}
		} catch (Exception ignored) {
			return null;
		}
	}

	private static List<Path> staleJars(Path mods, Path keep) throws Exception {
		List<Path> stale = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "*.jar")) {
			for (Path path : stream) {
				Path absolute = path.toAbsolutePath().normalize();
				if (!absolute.equals(keep) && branded(path)) {
					stale.add(absolute);
				}
			}
		}
		return stale;
	}

	private static void retire(Path old) {
		if (old == null || !Files.exists(old)) {
			return;
		}
		if (deleteQuiet(old)) {
			log("Removed old jar " + old.getFileName());
			return;
		}
		for (Path dest : retireTargets(old)) {
			if (moveQuiet(old, dest)) {
				log("Moved in-use jar " + old.getFileName() + " to " + dest.getFileName() + ".");
				dest.toFile().deleteOnExit();
				rememberPurge(dest);
				deleteQuiet(dest);
				return;
			}
		}
		old.toFile().deleteOnExit();
		rememberPurge(old);
		log("Could not remove " + old.getFileName() + ". Delete it from the mods folder before the next launch.");
	}

	private static List<Path> retireTargets(Path old) {
		String name = old.getFileName().toString();
		List<Path> targets = new ArrayList<>();
		targets.add(old.resolveSibling(name + ".old"));
		targets.add(old.resolveSibling(name + ".disabled"));
		Path mods = old.getParent();
		if (mods != null && mods.getParent() != null) {
			targets.add(mods.getParent().resolve(name + ".old"));
		}
		try {
			targets.add(Path.of(System.getProperty("java.io.tmpdir")).resolve("stray-" + name + ".old"));
		} catch (Exception ignored) {
		}
		return targets;
	}

	private static boolean deleteQuiet(Path path) {
		try {
			return Files.deleteIfExists(path);
		} catch (Exception ignored) {
			return false;
		}
	}

	private static boolean moveQuiet(Path from, Path to) {
		if (to == null || from.equals(to)) {
			return false;
		}
		try {
			Files.createDirectories(to.getParent());
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static Path purgeFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("stray-purge.txt");
	}

	private static void rememberPurge(Path path) {
		try {
			Path file = purgeFile();
			String line = path.toAbsolutePath().normalize() + System.lineSeparator();
			Files.writeString(
				file,
				Files.isRegularFile(file) ? Files.readString(file) + line : line
			);
		} catch (Exception ignored) {
		}
	}

	private static void sweep(Path mods, Path keep) {
		Path keepAbs = keep == null ? null : keep.toAbsolutePath().normalize();
		try {
			Path dir = FabricLoader.getInstance().getConfigDir();
			for (String name : List.of("stray-purge.txt", "voidmark-purge.txt")) {
				Path file = dir.resolve(name);
				if (Files.isRegularFile(file)) {
					for (String line : Files.readAllLines(file)) {
						if (!line.isBlank()) {
							deleteQuiet(Path.of(line.trim()));
						}
					}
					deleteQuiet(file);
				}
			}
		} catch (Exception ignored) {
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "*")) {
			for (Path path : stream) {
				if (!branded(path)) {
					continue;
				}
				Path absolute = path.toAbsolutePath().normalize();
				String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
				boolean trash = name.endsWith(".old")
					|| name.endsWith(".part")
					|| name.endsWith(".disabled")
					|| name.endsWith(".jar.old");
				boolean extraJar = name.endsWith(".jar") && (keepAbs == null || !absolute.equals(keepAbs));
				if (trash || extraJar) {
					if (deleteQuiet(absolute)) {
						if (extraJar) {
							log("Removed leftover jar " + path.getFileName());
						}
					} else if (extraJar) {
						retire(absolute);
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static boolean branded(Path path) {
		if (path == null || path.getFileName() == null) {
			return false;
		}
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.startsWith("stray") || name.startsWith("eisenmann") || name.startsWith("voidmark");
	}

	private static void killGame() {
		try {
			ProcessHandle.current().descendants().forEach(child -> {
				try {
					child.destroyForcibly();
				} catch (Exception ignored) {
				}
			});
		} catch (Exception ignored) {
		}
		try {
			ProcessHandle.current().destroyForcibly();
		} catch (Exception ignored) {
		}
		try {
			System.out.flush();
			System.err.flush();
		} catch (Exception ignored) {
		}
		Runtime.getRuntime().halt(0);
	}

	private static void log(String message) {
		String line = "Stray | " + message;
		System.out.println(line);
		Stray.LOGGER.info(message);
	}

	private record Remote(String version, String file, List<String> urls) {
	}
}
