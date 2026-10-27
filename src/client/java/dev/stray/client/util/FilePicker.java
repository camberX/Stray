package dev.stray.client.util;

import dev.stray.Stray;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

/**
 * Native "open file" dialog through SDL. 26.3 no longer ships tinyfd, but the
 * game window is an SDL window, so SDL's own dialog (xdg portal / zenity on
 * Linux, the system dialog on Windows and macOS) is always there.
 */
public final class FilePicker {
	private FilePicker() {
	}

	/**
	 * Opens the dialog. {@code onResult} runs on the client thread with the chosen
	 * path, or {@code null} when the dialog was cancelled. {@code onError} gets a
	 * short message when no dialog could be shown.
	 *
	 * @param patterns extensions without dots, separated by {@code ;} (e.g. {@code "png;jpg"})
	 */
	public static void open(String title, String filterName, String patterns, Consumer<Path> onResult, Consumer<String> onError) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> show(client, title, filterName, patterns, onResult, onError));
	}

	private static void show(Minecraft client, String title, String filterName, String patterns, Consumer<Path> onResult, Consumer<String> onError) {
		// SDL reads these until the callback fires, so they live on the native heap.
		ByteBuffer name = MemoryUtil.memUTF8(filterName);
		ByteBuffer pattern = MemoryUtil.memUTF8(patterns);
		ByteBuffer location = MemoryUtil.memUTF8(System.getProperty("user.home", "") + "/");
		SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
		filters.get(0).name(name).pattern(pattern);
		SDL_DialogFileCallback[] holder = new SDL_DialogFileCallback[1];
		holder[0] = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
			Path picked = null;
			String error = null;
			if (filelist == 0L) {
				error = SDLError.SDL_GetError();
			} else {
				long first = MemoryUtil.memGetAddress(filelist);
				if (first != 0L) {
					picked = Path.of(MemoryUtil.memUTF8(first));
				}
			}
			final Path result = picked;
			final String failure = error;
			client.execute(() -> {
				holder[0].free();
				filters.free();
				MemoryUtil.memFree(name);
				MemoryUtil.memFree(pattern);
				MemoryUtil.memFree(location);
				if (failure != null) {
					Stray.LOGGER.warn("File dialog failed: {}", failure);
					onError.accept("Can't open file picker");
				} else {
					onResult.accept(result);
				}
			});
		});
		try {
			long window = client.getWindow().handle();
			SDLDialog.SDL_ShowOpenFileDialog(holder[0], 0L, window, filters, location, false);
		} catch (Throwable e) {
			Stray.LOGGER.warn("File dialog failed", e);
			holder[0].free();
			filters.free();
			MemoryUtil.memFree(name);
			MemoryUtil.memFree(pattern);
			MemoryUtil.memFree(location);
			onError.accept("Can't open file picker");
		}
	}
}
