package dev.stray.client.dungeon.map;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Fetches NoammAddons' public data pack (room data for the dungeon map) the same way
 * NoammAddons does: compare the server's version hash with the one we stored, and when
 * it changed download the zip and unpack it into config/stray/noamm-data/. Runs once in
 * the background at start; {@link RoomDatabase} picks the file up on its next look.
 */
public final class NoammData {
	private static final String DOWNLOAD_URL = "https://api.noamm.org/na/data/download";
	private static final String VERSION_URL = "https://api.noamm.org/na/data/version";
	private static final Logger LOGGER = LoggerFactory.getLogger("Stray-NoammData");
	private static volatile String status = "not started";
	/** Set after a successful download, so RoomDatabase reads the new file. */
	static volatile boolean fresh;
	/** Waits between attempts after a failure (offline at start, server down): 1, 5, then 15 min. */
	private static final long[] RETRY_MS = {60_000L, 300_000L, 900_000L};

	private NoammData() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("noamm-data");
	}

	public static String status() {
		return status;
	}

	public static void start() {
		Thread thread = new Thread(NoammData::run, "Stray-NoammData");
		thread.setDaemon(true);
		thread.start();
	}

	private static void run() {
		for (int attempt = 0; ; attempt++) {
			if (attempt() || attempt >= RETRY_MS.length) {
				return;
			}
			try {
				Thread.sleep(RETRY_MS[attempt]);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	/** One check (and download if needed). Returns false when it should be tried again later. */
	private static boolean attempt() {
		HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		Path dir = folder();
		Path versionFile = dir.resolve("version.txt");
		try {
			status = "checking";
			HttpResponse<String> version = http.send(request(VERSION_URL), HttpResponse.BodyHandlers.ofString());
			String remote = version.body() == null ? "" : version.body().trim();
			// An error page or proxy reply is not a version: never store it as one.
			if (version.statusCode() != 200 || remote.isEmpty() || remote.length() > 128 || remote.contains(" ") || remote.contains("<")) {
				throw new IOException("bad version reply (HTTP " + version.statusCode() + ")");
			}
			String local = Files.isRegularFile(versionFile) ? Files.readString(versionFile, StandardCharsets.UTF_8).trim() : null;
			if (!remote.isEmpty() && remote.equals(local)) {
				status = "up to date (" + remote + ")";
				return true;
			}
			status = "downloading";
			Path zip = Files.createTempFile("stray-noamm-data-", ".zip");
			try {
				HttpResponse<InputStream> response = http.send(request(DOWNLOAD_URL), HttpResponse.BodyHandlers.ofInputStream());
				if (response.statusCode() != 200) {
					throw new IOException("HTTP " + response.statusCode());
				}
				try (InputStream in = response.body()) {
					Files.copy(in, zip, StandardCopyOption.REPLACE_EXISTING);
				}
				Path fresh = dir.resolveSibling("noamm-data.new");
				delete(fresh);
				Files.createDirectories(fresh);
				unzip(zip, fresh);
				delete(dir);
				Files.move(fresh, dir);
				Files.writeString(versionFile, remote, StandardCharsets.UTF_8);
				status = "downloaded (" + remote + ")";
				NoammData.fresh = true;
				LOGGER.info("NoammAddons data updated to {}", remote);
			} finally {
				Files.deleteIfExists(zip);
			}
		} catch (Exception e) {
			status = "failed: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage());
			LOGGER.warn("Could not update NoammAddons data: {}", e.toString());
			return false;
		}
		return true;
	}

	private static HttpRequest request(String url) {
		return HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(30))
			.header("User-Agent", "Stray-NoammData")
			.GET()
			.build();
	}

	/** Same as NoammAddons: drop the zip's top folder, refuse paths that leave the target. */
	private static void unzip(Path zip, Path target) throws IOException {
		Path root = target.toAbsolutePath().normalize();
		String top = null;
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				if (top == null) {
					int slash = name.indexOf('/');
					top = slash >= 0 ? name.substring(0, slash + 1) : "";
				}
				if (!top.isEmpty() && name.startsWith(top)) {
					name = name.substring(top.length());
				}
				if (name.isEmpty()) {
					continue;
				}
				Path out = root.resolve(name).normalize();
				if (!out.startsWith(root)) {
					throw new IOException("Blocked path traversal entry: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(out);
				} else {
					Files.createDirectories(out.getParent());
					Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
				}
				zis.closeEntry();
			}
		}
	}

	private static void delete(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(p);
			}
		}
	}
}
