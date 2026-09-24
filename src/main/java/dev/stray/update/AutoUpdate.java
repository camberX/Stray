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
	private static volatile boolean fetched;

	@Override
	public void onPreLaunch() {
		log("PreLaunch updater loaded.");
		boolean pending = takeNextLaunchRequest();
		if (!enabled() && !pending) {
			log("Auto-update is off in config, so PreLaunch will not download. Client launch still checks if the in-game toggle is on.");
			return;
		}
		if (pending) {
			log("Update was requested last session. Checking now, then replacing the older jar.");
		}
		checkAndInstall(true);
	}

	public static boolean enabled() {
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
			if (!json.has("autoUpdate") || json.get("autoUpdate").isJsonNull()) {
				return false;
			}
			var value = json.get("autoUpdate");
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
				return value.getAsBoolean();
			}
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				return value.getAsInt() != 0;
			}
			return "true".equalsIgnoreCase(value.getAsString().trim());
		} catch (Exception ignored) {
			return false;
		}
	}

	/**
	 * Always runs from client init after {@code stray.json} is loaded.
	 * PreLaunch does not skip this unless a network fetch already finished.
	 */
	/**
	 * One check at client start.
	 *
	 * @return a newer version when notify is on and nothing was installed, otherwise {@code null}
	 */
	public static String clientLaunchCheck(boolean autoUpdate, boolean notify) {
		log("Client launch: auto-update " + (autoUpdate ? "on" : "off") + ", notify " + (notify ? "on" : "off") + ".");
		if (autoUpdate) {
			if (fetched) {
				log("Already queried stray.gay during PreLaunch.");
				return null;
			}
			checkAndInstall(true);
			return null;
		}
		if (!notify) {
			log("Not checking for a new jar (both toggles off).");
			return null;
		}
		if (fetched) {
			return null;
		}
		fetched = true;
		String installed = installedVersion();
		log("Checking stray.gay for a newer jar (you have " + (installed.isEmpty() ? "unknown" : installed) + ")…");
		try {
			Remote remote = fetchRemote();
			if (remote == null) {
				log("No update info.");
				return null;
			}
			if (!installed.isEmpty() && UpdateMeta.compare(remote.version, installed) <= 0) {
				log("Already up to date (" + installed + ").");
				return null;
			}
			log("Found " + remote.version + ". Turn on Auto update to install it, or restart after downloading.");
			return remote.version;
		} catch (Exception exception) {
			log("Update check failed: " + exception.getMessage());
			Stray.LOGGER.warn("Update check failed", exception);
			return null;
		}
	}

	/**
	 * Download a newer jar even when auto-update is off.
	 * Does not exit. Call {@link #quit()} after a successful update.
	 */
	public static UpdateOutcome updateNow() {
		String installed = installedVersion();
		log("Checking stray.gay for a newer jar (you have " + (installed.isEmpty() ? "unknown" : installed) + ")…");
		UpdateOutcome onDisk = adoptNewerJar(installed);
		if (onDisk != null) {
			return onDisk;
		}
		UpdateOutcome outcome = fetchAndApply(true);
		if (outcome.status() == UpdateStatus.UPDATED) {
			log(outcome.message());
		}
		return outcome;
	}

	public static void quit() {
		killGame();
	}

	/**
	 * Ask the next Minecraft launch to check for an update.
	 * Does not download or remove the jar that is running now.
	 */
	public static boolean requestNextLaunch() {
		try {
			Path file = nextLaunchFlag();
			Files.createDirectories(file.getParent());
			Files.writeString(file, "1" + System.lineSeparator());
			log("Update check scheduled for the next launch. The current jar stays in place.");
			return true;
		} catch (Exception exception) {
			log("Could not schedule the next-launch update: " + exception.getMessage());
			return false;
		}
	}

	private static boolean takeNextLaunchRequest() {
		Path file = nextLaunchFlag();
		if (!Files.isRegularFile(file)) {
			return false;
		}
		deleteQuiet(file);
		return true;
	}

	private static Path nextLaunchFlag() {
		return FabricLoader.getInstance().getConfigDir().resolve("stray-update-next.txt");
	}

	private static void checkAndInstall(boolean closeGame) {
		UpdateOutcome outcome = updateNow();
		if (closeGame && outcome.status() == UpdateStatus.UPDATED) {
			killGame();
		}
	}

	private static UpdateOutcome adoptNewerJar(String installed) {
		Path mods = modsDir();
		Path current = currentJar();
		if (current == null) {
			current = newestJar(mods);
		}
		Path newest = newestJar(mods);
		if (newest == null || current == null || newest.equals(current)) {
			return null;
		}
		String onDisk = jarVersion(newest);
		if (onDisk == null || (!installed.isEmpty() && UpdateMeta.compare(onDisk, installed) <= 0)) {
			return null;
		}
		String message = "Newer jar " + onDisk + " is already in mods. Closing so it can load.";
		log(message);
		try {
			for (Path old : staleJars(mods, newest)) {
				retire(old);
			}
		} catch (Exception ignored) {
		}
		return UpdateOutcome.updated(onDisk, message, true);
	}

	/**
	 * Download a newer jar into {@code mods} when one exists.
	 *
	 * @return the new version, or {@code null} if nothing was installed
	 */
	public static String installNewer(boolean closeGame) {
		UpdateOutcome outcome = fetchAndApply(closeGame);
		return outcome.status() == UpdateStatus.UPDATED ? outcome.version() : null;
	}

	private static UpdateOutcome fetchAndApply(boolean closeGame) {
		fetched = true;
		Path mods = modsDir();
		Path current = currentJar();
		if (current == null) {
			current = newestJar(mods);
		}
		if (current == null) {
			current = mods.resolve("stray.jar");
		}
		String installed = installedVersion();
		try {
			Remote remote = fetchRemote();
			if (remote == null) {
				String message = "No update info.";
				log(message);
				return UpdateOutcome.failed(message);
			}
			if (!installed.isEmpty() && UpdateMeta.compare(remote.version, installed) <= 0) {
				String message = "Already up to date (" + installed + ").";
				log(message);
				return UpdateOutcome.current(installed, message);
			}
			if (closeGame) {
				log("Found " + remote.version + ". Downloading and closing Minecraft so the new jar can load.");
			} else {
				log("Found " + remote.version + ". Downloading. Restart Minecraft to load it.");
			}
			Path dest = apply(current, remote);
			if (dest == null) {
				String message = "Update failed. Continuing with " + (installed.isEmpty() ? "this build" : installed) + ".";
				log(message);
				return UpdateOutcome.failed(message);
			}
			return UpdateOutcome.updated(remote.version, "Updated to " + remote.version + ". Closing Minecraft.", false);
		} catch (Exception exception) {
			String message = "Update check failed: " + exception.getMessage();
			log(message);
			Stray.LOGGER.warn("Auto-update failed", exception);
			return UpdateOutcome.failed(message);
		}
	}

	private static String installedVersion() {
		return FabricLoader.getInstance()
			.getModContainer(Stray.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("");
	}

	private static Path currentJar() {
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(Stray.MOD_ID);
		if (container.isEmpty()) {
			return newestJar(modsDir());
		}
		Path path = jarPath(container.get());
		return path != null ? path : newestJar(modsDir());
	}

	private static Path modsDir() {
		Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
		try {
			Files.createDirectories(mods);
		} catch (Exception ignored) {
		}
		return mods.toAbsolutePath().normalize();
	}

	private static Path newestJar(Path mods) {
		if (mods == null || !Files.isDirectory(mods)) {
			return null;
		}
		Path best = null;
		String version = null;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "*.jar")) {
			for (Path path : stream) {
				if (!branded(path)) {
					continue;
				}
				String next = jarVersion(path);
				if (next == null) {
					continue;
				}
				if (version == null || UpdateMeta.compare(next, version) > 0) {
					best = path.toAbsolutePath().normalize();
					version = next;
				}
			}
		} catch (Exception ignored) {
		}
		return best;
	}

	private static Path jarPath(ModContainer container) {
		List<Path> paths = container.getOrigin().getPaths();
		if (paths == null || paths.isEmpty()) {
			return null;
		}
		Path path = paths.getFirst();
		if (path == null) {
			return null;
		}
		path = path.toAbsolutePath().normalize();
		if (Files.isRegularFile(path)) {
			String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
			if (name.endsWith(".jar") || branded(path)) {
				return path;
			}
		}
		Path parent = path.getParent();
		while (parent != null) {
			String name = parent.getFileName() == null ? "" : parent.getFileName().toString().toLowerCase(Locale.ROOT);
			if (name.endsWith(".jar") && Files.isRegularFile(parent)) {
				return parent;
			}
			parent = parent.getParent();
		}
		return null;
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
		urls.add("https://github.com/camberX/Stray/releases/download/v" + version + "/" + file);
		urls.add("https://github.com/camberX/Eisenmann/releases/download/v" + version + "/" + file);
		urls.add("https://github.com/camberX/voidmark/releases/download/v" + version + "/" + file);
		urls.add(download);
		urls.add(UpdateMeta.SHOP + "/stray.jar");
		return new Remote(version, file, urls);
	}

	private static Path apply(Path current, Remote remote) throws Exception {
		Path mods = current.getParent() == null ? modsDir() : current.getParent().toAbsolutePath().normalize();
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
				log("Download from " + url + " returned HTTP " + response.statusCode() + ".");
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

	public enum UpdateStatus {
		UPDATED,
		CURRENT,
		FAILED
	}

	public record UpdateOutcome(UpdateStatus status, String version, String message, boolean alreadyOnDisk) {
		public static UpdateOutcome updated(String version, String message, boolean alreadyOnDisk) {
			return new UpdateOutcome(UpdateStatus.UPDATED, version, message, alreadyOnDisk);
		}

		public static UpdateOutcome current(String version, String message) {
			return new UpdateOutcome(UpdateStatus.CURRENT, version, message, false);
		}

		public static UpdateOutcome failed(String message) {
			return new UpdateOutcome(UpdateStatus.FAILED, "", message, false);
		}
	}
}
