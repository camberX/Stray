package dev.stray.client.media;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts the bundled PowerShell script that reads the Windows media session (what Spotify
 * is playing). The script ships in the jar as {@code assets/stray/windows_nowplaying.ps1}
 * and is copied to {@code config/stray/} so it can be run and read like any other file.
 */
final class WindowsMediaHelper {
	private WindowsMediaHelper() {
	}

	static Process start() throws Exception {
		Path script = script();
		ProcessBuilder builder = new ProcessBuilder(
			powershell(),
			"-NoLogo",
			"-STA",
			"-NoProfile",
			"-ExecutionPolicy", "Bypass",
			"-File",
			script.toAbsolutePath().toString()
		);
		builder.environment().put("POWERSHELL_TELEMETRY_OPTOUT", "1");
		return builder.start();
	}

	private static Path script() throws Exception {
		Path script = FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("windows_nowplaying.ps1");
		InputStream raw = WindowsMediaHelper.class.getResourceAsStream("/assets/stray/windows_nowplaying.ps1");
		if (raw == null) {
			throw new IllegalStateException("windows_nowplaying.ps1 is missing from the mod jar");
		}
		byte[] bytes;
		try (InputStream in = raw) {
			bytes = in.readAllBytes();
		}
		Files.createDirectories(script.getParent());
		if (!Files.isRegularFile(script) || !java.util.Arrays.equals(Files.readAllBytes(script), bytes)) {
			Files.write(script, bytes);
		}
		return script;
	}

	private static String powershell() {
		String root = System.getenv("SystemRoot");
		if (root == null || root.isBlank()) {
			root = "C:\\Windows";
		}
		Path exe = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
		return Files.isRegularFile(exe) ? exe.toString() : "powershell.exe";
	}
}
