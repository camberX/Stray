package dev.stray.client.dungeon.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * NoammAddons' room list (rooms-modern.json, used with noamm's permission). A copy ships in
 * the jar; at start a newer one is fetched from noamm's data server, like NoammAddons does.
 */
public final class Rooms {
	private static final Logger LOGGER = LoggerFactory.getLogger("Stray-Rooms");
	private static final String FILE = "rooms-modern.json";
	private static final String VERSION_URL = "https://api.noamm.org/na/data/version";
	private static final String DOWNLOAD_URL = "https://api.noamm.org/na/data/download";
	private static volatile Map<Integer, RoomData> byCore = Map.of();
	private static volatile Map<String, RoomData> byName = Map.of();
	private static volatile String source = "none";
	private static volatile String download = "not started";

	private Rooms() {
	}

	public static RoomData byCore(int core) {
		return byCore.get(core);
	}

	public static RoomData byName(String name) {
		return byName.get(name);
	}

	public static int size() {
		return byName.size();
	}

	public static String source() {
		return source + ", update: " + download;
	}

	private static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("rooms");
	}

	public static void start() {
		Path cached = folder().resolve(FILE);
		if (!(Files.isRegularFile(cached) && loadFile(cached, "downloaded"))) {
			try (InputStream in = Rooms.class.getResourceAsStream("/assets/stray/dungeon/" + FILE)) {
				if (in != null) {
					load(new InputStreamReader(in, StandardCharsets.UTF_8), "bundled");
				}
			} catch (Exception e) {
				LOGGER.warn("Bundled room list unreadable: {}", e.toString());
			}
		}
		Thread thread = new Thread(Rooms::update, "Stray-Rooms");
		thread.setDaemon(true);
		thread.start();
	}

	private static boolean loadFile(Path path, String label) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return load(reader, label);
		} catch (Exception e) {
			return false;
		}
	}

	private static void update() {
		try {
			HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
			Path dir = folder();
			Path versionFile = dir.resolve("version.txt");
			download = "checking";
			HttpResponse<String> version = http.send(request(VERSION_URL), HttpResponse.BodyHandlers.ofString());
			String remote = version.body() == null ? "" : version.body().trim();
			if (version.statusCode() != 200 || remote.isEmpty() || remote.length() > 128 || remote.contains("<") || remote.contains(" ")) {
				download = "bad version reply (HTTP " + version.statusCode() + ")";
				return;
			}
			String local = Files.isRegularFile(versionFile) ? Files.readString(versionFile).trim() : "";
			if (remote.equals(local) && Files.isRegularFile(dir.resolve(FILE))) {
				download = "up to date";
				return;
			}
			download = "downloading";
			HttpResponse<InputStream> zip = http.send(request(DOWNLOAD_URL), HttpResponse.BodyHandlers.ofInputStream());
			if (zip.statusCode() != 200) {
				download = "HTTP " + zip.statusCode();
				return;
			}
			byte[] json = null;
			try (ZipInputStream in = new ZipInputStream(zip.body())) {
				ZipEntry entry;
				while ((entry = in.getNextEntry()) != null) {
					String name = entry.getName();
					if (!entry.isDirectory() && (name.equals(FILE) || name.endsWith("/" + FILE))) {
						json = in.readAllBytes();
						break;
					}
				}
			}
			if (json == null) {
				download = "no room list in the download";
				return;
			}
			if (!load(new InputStreamReader(new java.io.ByteArrayInputStream(json), StandardCharsets.UTF_8), "downloaded")) {
				download = "downloaded list unreadable";
				return;
			}
			Files.createDirectories(dir);
			Files.write(dir.resolve(FILE), json);
			Files.writeString(versionFile, remote);
			download = "updated";
		} catch (Exception e) {
			download = "failed: " + e.getClass().getSimpleName();
		}
	}

	private static HttpRequest request(String url) {
		return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", "Stray").GET().build();
	}

	private static boolean load(Reader reader, String label) {
		JsonElement root = JsonParser.parseReader(reader);
		if (!root.isJsonArray()) {
			return false;
		}
		Map<Integer, RoomData> cores = new HashMap<>();
		Map<String, RoomData> names = new HashMap<>();
		for (JsonElement element : root.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			List<Integer> list = new ArrayList<>();
			if (obj.has("cores") && obj.get("cores").isJsonArray()) {
				for (JsonElement core : obj.getAsJsonArray("cores")) {
					list.add(core.getAsInt());
				}
			}
			RoomData data = new RoomData(
				str(obj, "name", "Unknown"),
				RoomType.parse(str(obj, "type", "NORMAL")),
				RoomShape.parse(str(obj, "shape", "Unknown")),
				List.copyOf(list),
				num(obj, "secrets"),
				num(obj, "crypts"),
				num(obj, "trappedChests")
			);
			for (int core : list) {
				cores.put(core, data);
			}
			names.put(data.name(), data);
		}
		if (names.isEmpty()) {
			return false;
		}
		byCore = cores;
		byName = names;
		source = names.size() + " rooms, " + label;
		return true;
	}

	private static String str(JsonObject obj, String key, String fallback) {
		JsonElement v = obj.get(key);
		return v == null || v.isJsonNull() ? fallback : v.getAsString();
	}

	private static int num(JsonObject obj, String key) {
		JsonElement v = obj.get(key);
		return v == null || v.isJsonNull() ? 0 : v.getAsInt();
	}
}
