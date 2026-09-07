package dev.voidmark.update;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small standalone process that waits for the old JVM to release its mod jar
 * before starting the replacement launch.
 */
public final class RelaunchHelper {
	private static final int PREFIX_ARGS = 5;

	private RelaunchHelper() {
	}

	public static void main(String[] args) {
		if (args.length < PREFIX_ARGS) {
			return;
		}
		try {
			long parentPid = Long.parseLong(args[0]);
			Path cwd = Path.of(args[1]).toAbsolutePath().normalize();
			Path oldJar = Path.of(args[2]).toAbsolutePath().normalize();
			Path newJar = Path.of(args[3]).toAbsolutePath().normalize();
			int count = Integer.parseInt(args[4]);
			if (count < 2 || args.length != PREFIX_ARGS + count || !Files.isRegularFile(newJar)) {
				return;
			}
			ProcessHandle.of(parentPid).ifPresent(parent -> parent.onExit().join());
			Thread.sleep(500L);
			if (!cleanOldFiles(oldJar, newJar)) {
				System.err.println("Voidmark | Relaunch cancelled because the old mod jar is still locked.");
				return;
			}
			List<String> command = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				command.add(args[PREFIX_ARGS + i]);
			}
			ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile()).inheritIO();
			builder.start();
		} catch (Exception exception) {
			System.err.println("Voidmark | Could not relaunch Minecraft: " + exception.getMessage());
		}
	}

	private static boolean cleanOldFiles(Path oldJar, Path newJar) {
		Path mods = newJar.getParent();
		for (int attempt = 0; attempt < 30; attempt++) {
			delete(oldJar, newJar);
			if (mods != null) {
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "voidmark*")) {
					for (Path path : stream) {
						Path absolute = path.toAbsolutePath().normalize();
						if (!absolute.equals(newJar) && disposable(path)) {
							delete(absolute, newJar);
						}
					}
				} catch (Exception ignored) {
				}
			}
			if (!hasConflictingJar(mods, newJar)) {
				return true;
			}
			try {
				Thread.sleep(100L);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private static boolean hasConflictingJar(Path mods, Path newJar) {
		if (mods == null) {
			return false;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "voidmark*.jar")) {
			for (Path path : stream) {
				if (!path.toAbsolutePath().normalize().equals(newJar)) {
					return true;
				}
			}
		} catch (Exception ignored) {
			return true;
		}
		return false;
	}

	private static boolean disposable(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".jar")
			|| name.endsWith(".old")
			|| name.endsWith(".disabled")
			|| name.endsWith(".part")
			|| name.endsWith(".jar.old");
	}

	private static void delete(Path path, Path keep) {
		if (path == null || path.equals(keep)) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (Exception ignored) {
		}
	}
}
