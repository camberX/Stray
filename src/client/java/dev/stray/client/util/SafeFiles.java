package dev.stray.client.util;

import dev.stray.Stray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Saving files without losing them: write a temp file next to the real one, then move it over
 * in one step. A crash or power loss mid-save leaves the old file intact instead of an empty
 * or cut-off one.
 */
public final class SafeFiles {
	private SafeFiles() {
	}

	/** Writes {@code text} to {@code file} atomically. */
	public static void write(Path file, String text) throws IOException {
		write(file, text, false);
	}

	/**
	 * Writes {@code text} to {@code file} atomically. {@code ownerOnly}: on Linux/macOS the file
	 * is readable by your user only (for tokens).
	 */
	public static void write(Path file, String text, boolean ownerOnly) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		if (ownerOnly && FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
			Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
		}
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * A file that couldn't be read: keep a copy as {@code <name>.bad} before defaults are saved
	 * over it, so nothing is lost for good.
	 */
	public static void keepBroken(Path file) {
		try {
			if (Files.isRegularFile(file)) {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not keep a copy of {}", file.getFileName(), exception);
		}
	}
}
