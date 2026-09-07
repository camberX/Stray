package dev.voidmark.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmark.Voidmark;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Optional launch gate. When {@code autoUpdate} is on, Minecraft waits here
 * for voidmark.cloud. A newer jar is written into mods, the old one is
 * retired, and the current Java launch is started again with the new jar.
 */
public final class AutoUpdate implements PreLaunchEntrypoint {
	private static final String SHOP = "https://voidmark.cloud";
	private static final String META = SHOP + "/api/mod";
	private static final String DOWNLOAD = SHOP + "/download";
	private static final String GITHUB_META =
		"https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/latest.json";
	private static final long MAX_BYTES = 12L * 1024L * 1024L;
	private static final String RELAUNCH_GUARD = "voidmark.relaunchVersion";

	@Override
	public void onPreLaunch() {
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(Voidmark.MOD_ID);
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
		log("Checking voidmark.cloud for a newer jar (you have " + installed + ")…");
		try {
			Remote remote = fetchRemote();
			if (remote == null) {
				log("No update info. Continuing launch.");
				return;
			}
			if (compare(remote.version, installed) <= 0) {
				log("Already up to date (" + installed + ").");
				return;
			}
			if (remote.version.equals(System.getProperty(RELAUNCH_GUARD))) {
				log("Automatic relaunch already attempted for " + remote.version + ". Continuing this launch to avoid a loop.");
				return;
			}
			log("Found " + remote.version + ". Downloading and relaunching Minecraft.");
			Path dest = apply(current, remote);
			if (dest == null) {
				log("Update failed. Continuing with " + installed + ".");
				return;
			}
			if (relaunch(current, dest, remote.version)) {
				log("Updated to " + remote.version + " at " + dest.getFileName() + ". Relaunch started.");
			} else {
				log("Updated to " + remote.version + " at " + dest.getFileName() + ". Relaunch Minecraft manually.");
			}
			System.exit(0);
		} catch (Exception exception) {
			log("Update check failed: " + exception.getMessage());
			Voidmark.LOGGER.warn("Auto-update failed", exception);
		}
	}

	private static boolean enabled() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("voidmark.json");
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
		JsonObject json = getJson(http, META, 12);
		if (json == null) {
			json = getJson(http, GITHUB_META, 12);
		}
		if (json == null || !json.has("version")) {
			return null;
		}
		String version = json.get("version").getAsString().trim();
		if (version.isEmpty()) {
			return null;
		}
		String file = json.has("file") ? json.get("file").getAsString().trim() : "voidmark-" + version + ".jar";
		if (file.isBlank() || file.contains("/") || file.contains("\\") || !file.endsWith(".jar")) {
			file = "voidmark-" + version + ".jar";
		}
		String download = DOWNLOAD;
		if (json.has("url")) {
			String url = json.get("url").getAsString().trim();
			if (url.startsWith("https://voidmark.cloud/")) {
				download = url;
			} else if (url.startsWith("/")) {
				download = SHOP + url;
			}
		}
		List<String> urls = new ArrayList<>();
		urls.add(download);
		urls.add(SHOP + "/voidmark.jar");
		urls.add("https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/" + file);
		urls.add("https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/voidmark.jar");
		return new Remote(version, file, urls);
	}

	private static JsonObject getJson(HttpClient http, String url, int timeoutSec) {
		try {
			HttpResponse<String> response = http.send(
				request(url, timeoutSec).build(),
				HttpResponse.BodyHandlers.ofString()
			);
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return null;
			}
			return JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Path apply(Path current, Remote remote) throws Exception {
		Path mods = current.getParent() == null ? null : current.getParent().toAbsolutePath().normalize();
		if (mods == null) {
			return null;
		}
		Path dest = mods.resolve(remote.file).toAbsolutePath().normalize();
		if (!mods.equals(dest.getParent()) || dest.equals(current)) {
			dest = mods.resolve("voidmark-" + remote.version + ".jar");
		}
		if (dest.equals(current)) {
			dest = mods.resolve("voidmark-" + remote.version + "-new.jar");
		}
		Path part = dest.resolveSibling(dest.getFileName() + ".part");
		Files.deleteIfExists(part);
		HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(8))
			.build();
		boolean downloaded = false;
		for (String url : remote.urls) {
			if (download(http, url, part)) {
				downloaded = true;
				break;
			}
		}
		if (!downloaded || !validJar(part, remote)) {
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

	private static boolean relaunch(Path current, Path updated, String version) {
		List<String> recovered = launchCommand();
		if (recovered.size() < 2) {
			log("Could not recover the launcher's Java command, so automatic relaunch is unavailable.");
			return false;
		}
		String executable = recovered.getFirst();
		List<String> launch = new ArrayList<>(recovered.size() + 1);
		launch.add(executable);
		launch.add("-D" + RELAUNCH_GUARD + "=" + version);
		for (int i = 1; i < recovered.size(); i++) {
			String argument = recovered.get(i);
			if (argument.startsWith("-D" + RELAUNCH_GUARD + "=")) {
				continue;
			}
			launch.add(rewriteLaunchArgument(argument, current, updated));
		}
		try {
			String cwd = System.getProperty("user.dir");
			Path directory = cwd == null || cwd.isBlank()
				? updated.getParent()
				: Path.of(cwd).toAbsolutePath().normalize();
			List<String> helper = new ArrayList<>(launch.size() + 8);
			helper.add(executable);
			helper.add("-cp");
			helper.add(updated.toString());
			helper.add(RelaunchHelper.class.getName());
			helper.add(Long.toString(ProcessHandle.current().pid()));
			helper.add(directory.toString());
			helper.add(current.toString());
			helper.add(updated.toString());
			helper.add(Integer.toString(launch.size()));
			helper.addAll(launch);
			ProcessBuilder builder = new ProcessBuilder(helper);
			if (Files.isDirectory(directory)) {
				builder.directory(directory.toFile());
			}
			builder.inheritIO();
			Process helperProcess = builder.start();
			try {
				Thread.sleep(400L);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			if (!helperProcess.isAlive()) {
				log("The relaunch helper exited before Minecraft stopped.");
				return false;
			}
			return true;
		} catch (Exception exception) {
			Voidmark.LOGGER.warn("Could not relaunch Minecraft after updating", exception);
			return false;
		}
	}

	private static List<String> launchCommand() {
		List<String> command;
		if (windows()) {
			command = splitWindowsCommandLine(windowsCommandLine(ProcessHandle.current().pid()));
			if (command.size() >= 2) {
				return command;
			}
		} else {
			command = linuxCommandLine();
			if (command.size() >= 2) {
				return command;
			}
		}
		command = processHandleCommand();
		if (command.size() >= 2) {
			return command;
		}
		return List.of();
	}

	private static List<String> processHandleCommand() {
		ProcessHandle.Info info = ProcessHandle.current().info();
		Optional<String> executable = info.command();
		Optional<String[]> arguments = info.arguments();
		if (executable.isPresent() && arguments.isPresent()) {
			List<String> command = new ArrayList<>(arguments.get().length + 1);
			command.add(executable.get());
			command.addAll(List.of(arguments.get()));
			return command;
		}
		String raw = info.commandLine().orElse("");
		return windows() ? splitWindowsCommandLine(raw) : splitUnixCommandLine(raw);
	}

	private static List<String> linuxCommandLine() {
		Path path = Path.of("/proc/self/cmdline");
		if (!Files.isReadable(path)) {
			return List.of();
		}
		try {
			byte[] raw = Files.readAllBytes(path);
			List<String> command = new ArrayList<>();
			int start = 0;
			for (int i = 0; i <= raw.length; i++) {
				if (i == raw.length || raw[i] == 0) {
					if (i > start) {
						command.add(new String(raw, start, i - start, StandardCharsets.UTF_8));
					}
					start = i + 1;
				}
			}
			return command;
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private static String windowsCommandLine(long pid) {
		try {
			Process process = new ProcessBuilder(
				"powershell.exe",
				"-NoProfile",
				"-Command",
				"[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
					+ "(Get-CimInstance Win32_Process -Filter \"ProcessId=" + pid + "\").CommandLine"
			).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> {
				try {
					return process.getInputStream().readAllBytes();
				} catch (Exception ignored) {
					return new byte[0];
				}
			});
			if (!process.waitFor(4, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				output.cancel(true);
				return "";
			}
			return new String(output.get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8).trim();
		} catch (Exception ignored) {
			return ProcessHandle.current().info().commandLine().orElse("");
		}
	}

	private static List<String> splitWindowsCommandLine(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		List<String> args = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		boolean started = false;
		int slashes = 0;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '\\') {
				slashes++;
				started = true;
				continue;
			}
			if (c == '"') {
				started = true;
				current.append("\\".repeat(slashes / 2));
				if (slashes % 2 == 0) {
					quoted = !quoted;
				} else {
					current.append('"');
				}
				slashes = 0;
				continue;
			}
			if (slashes > 0) {
				current.append("\\".repeat(slashes));
				slashes = 0;
			}
			if (!quoted && Character.isWhitespace(c)) {
				if (started) {
					args.add(current.toString());
					current.setLength(0);
					started = false;
				}
			} else {
				current.append(c);
				started = true;
			}
		}
		if (slashes > 0) {
			current.append("\\".repeat(slashes));
		}
		if (started) {
			args.add(current.toString());
		}
		return args;
	}

	private static List<String> splitUnixCommandLine(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		List<String> args = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		char quote = 0;
		boolean escaped = false;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (escaped) {
				current.append(c);
				escaped = false;
			} else if (c == '\\' && quote != '\'') {
				escaped = true;
			} else if ((c == '\'' || c == '"') && (quote == 0 || quote == c)) {
				quote = quote == 0 ? c : 0;
			} else if (quote == 0 && Character.isWhitespace(c)) {
				if (!current.isEmpty()) {
					args.add(current.toString());
					current.setLength(0);
				}
			} else {
				current.append(c);
			}
		}
		if (escaped) {
			current.append('\\');
		}
		if (!current.isEmpty()) {
			args.add(current.toString());
		}
		return args;
	}

	private static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static String rewriteLaunchArgument(String argument, Path current, Path updated) {
		if (argument == null || argument.isEmpty()) {
			return argument;
		}
		String currentPath = current.toAbsolutePath().normalize().toString();
		String updatedPath = updated.toAbsolutePath().normalize().toString();
		String rewritten = argument.replace(currentPath, updatedPath);
		if (rewritten.equals(argument)) {
			rewritten = argument.replace(current.getFileName().toString(), updated.getFileName().toString());
		}
		return rewritten;
	}

	private static boolean download(HttpClient http, String url, Path part) {
		try {
			HttpResponse<InputStream> response = http.send(
				request(url, 90).header("Accept", "application/java-archive,application/octet-stream,*/*").build(),
				HttpResponse.BodyHandlers.ofInputStream()
			);
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return false;
			}
			try (InputStream in = response.body()) {
				Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
			}
			if (Files.size(part) <= 64 || Files.size(part) > MAX_BYTES) {
				Files.deleteIfExists(part);
				return false;
			}
			return true;
		} catch (Exception ignored) {
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
			.header("User-Agent", "Voidmark-AutoUpdate")
			.GET();
	}

	private static boolean validJar(Path path, Remote remote) {
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path.toFile())) {
			java.util.zip.ZipEntry entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				return false;
			}
			try (Reader reader = new java.io.InputStreamReader(zip.getInputStream(entry))) {
				JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
				return metadata.has("id")
					&& Voidmark.MOD_ID.equals(metadata.get("id").getAsString())
					&& metadata.has("version")
					&& remote.version.equals(metadata.get("version").getAsString());
			}
		} catch (Exception ignored) {
			return false;
		}
	}

	private static List<Path> staleJars(Path mods, Path keep) throws Exception {
		List<Path> stale = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "voidmark*.jar")) {
			for (Path path : stream) {
				Path absolute = path.toAbsolutePath().normalize();
				if (!absolute.equals(keep)) {
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
			targets.add(Path.of(System.getProperty("java.io.tmpdir")).resolve("voidmark-" + name + ".old"));
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
		return FabricLoader.getInstance().getConfigDir().resolve("voidmark-purge.txt");
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
			Path file = purgeFile();
			if (Files.isRegularFile(file)) {
				for (String line : Files.readAllLines(file)) {
					if (!line.isBlank()) {
						deleteQuiet(Path.of(line.trim()));
					}
				}
				deleteQuiet(file);
			}
		} catch (Exception ignored) {
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "voidmark*")) {
			for (Path path : stream) {
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

	private static int compare(String left, String right) {
		int[] a = parts(left);
		int[] b = parts(right);
		int n = Math.max(a.length, b.length);
		for (int i = 0; i < n; i++) {
			int av = i < a.length ? a[i] : 0;
			int bv = i < b.length ? b[i] : 0;
			if (av != bv) {
				return Integer.compare(av, bv);
			}
		}
		return 0;
	}

	private static int[] parts(String version) {
		String[] bits = version == null ? new String[0] : version.split("[^0-9]+");
		int[] out = new int[Math.max(1, bits.length)];
		int n = 0;
		for (String bit : bits) {
			if (bit.isEmpty()) {
				continue;
			}
			try {
				out[n++] = Integer.parseInt(bit);
			} catch (NumberFormatException ignored) {
			}
		}
		if (n == out.length) {
			return out;
		}
		int[] trimmed = new int[n];
		System.arraycopy(out, 0, trimmed, 0, n);
		return trimmed;
	}

	private static void log(String message) {
		String line = "Voidmark | " + message;
		System.out.println(line);
		Voidmark.LOGGER.info(message);
	}

	private record Remote(String version, String file, List<String> urls) {
	}
}
