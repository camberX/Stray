package dev.stray.client.pip;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Picture-in-picture capture. A daemon thread grabs the chosen window at a
 * low rate and the HUD only uploads when a new frame is ready.
 */
public final class PipCapture {
	public static final Identifier TEXTURE = Stray.id("dynamic/pip");
	private static final AtomicReference<Frame> FRAME = new AtomicReference<>();
	private static final AtomicBoolean DIRTY = new AtomicBoolean();
	private static final AtomicBoolean RUNNING = new AtomicBoolean();

	private static Thread worker;
	private static NativeImage image;
	private static DynamicTexture texture;
	private static int texW;
	private static int texH;
	private static boolean registered;
	private static volatile String status = "Pick a window";

	public record WindowInfo(String id, String title, int width, int height) {
	}

	private record Frame(int[] argb, int width, int height, String title) {
	}

	private PipCapture() {
	}

	public static boolean supported() {
		return PipWin32.available();
	}

	public static String status() {
		return status;
	}

	public static List<WindowInfo> windows() {
		if (!supported()) {
			return List.of();
		}
		try {
			return PipWin32.list();
		} catch (Throwable exception) {
			Stray.LOGGER.debug("PiP window list failed", exception);
			return List.of();
		}
	}

	public static String currentTitle() {
		StrayConfig config = StrayConfig.get();
		if (config.pipWindowTitle != null && !config.pipWindowTitle.isBlank()) {
			return config.pipWindowTitle;
		}
		return "None";
	}

	public static void cycleWindow() {
		List<WindowInfo> list = windows();
		if (list.isEmpty()) {
			status = supported() ? "No windows found" : "Windows only";
			return;
		}
		StrayConfig config = StrayConfig.get();
		int at = -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id().equals(config.pipWindowId) || list.get(i).title().equals(config.pipWindowTitle)) {
				at = i;
				break;
			}
		}
		WindowInfo next = list.get((at + 1) % list.size());
		config.pipWindowId = next.id();
		config.pipWindowTitle = next.title();
		FRAME.set(null);
		DIRTY.set(false);
		status = next.title();
	}

	public static void tick(Minecraft client) {
		boolean on = client != null && StrayConfig.get().pipEnabled;
		if (on) {
			ensureWorker();
		} else {
			stop();
		}
	}

	public static void upload() {
		Frame frame = FRAME.get();
		if (frame == null || !DIRTY.getAndSet(false)) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		try {
			if (image == null || texture == null || texW != frame.width || texH != frame.height) {
				if (image != null) {
					image.close();
				}
				image = new NativeImage(frame.width, frame.height, false);
				texture = new DynamicTexture(() -> "stray-pip", image);
				client.getTextureManager().register(TEXTURE, texture);
				registered = true;
				texW = frame.width;
				texH = frame.height;
			}
			int i = 0;
			for (int y = 0; y < frame.height; y++) {
				for (int x = 0; x < frame.width; x++) {
					image.setPixel(x, y, frame.argb[i++]);
				}
			}
			texture.upload();
		} catch (Exception exception) {
			Stray.LOGGER.debug("PiP upload failed", exception);
		}
	}

	public static boolean hasFrame() {
		return registered && texW > 0 && texH > 0 && FRAME.get() != null;
	}

	public static int texWidth() {
		return Math.max(1, texW);
	}

	public static int texHeight() {
		return Math.max(1, texH);
	}

	public static float aspect() {
		Frame frame = FRAME.get();
		if (frame != null && frame.width > 0 && frame.height > 0) {
			return frame.width / (float) frame.height;
		}
		return 16f / 9f;
	}

	public static void stop() {
		RUNNING.set(false);
		worker = null;
	}

	private static void ensureWorker() {
		if (RUNNING.get() && worker != null && worker.isAlive()) {
			return;
		}
		RUNNING.set(true);
		Thread thread = new Thread(PipCapture::loop, "stray-pip");
		thread.setDaemon(true);
		worker = thread;
		thread.start();
	}

	private static void loop() {
		while (RUNNING.get()) {
			long started = System.nanoTime();
			try {
				grab();
			} catch (Throwable exception) {
				status = "Capture failed";
				Stray.LOGGER.debug("PiP capture failed", exception);
			}
			int fps = StrayConfig.clamp(StrayConfig.get().pipFps, 4, 20);
			long sleep = Math.max(20L, 1000L / fps - (System.nanoTime() - started) / 1_000_000L);
			try {
				Thread.sleep(sleep);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static void grab() {
		if (!supported()) {
			status = "Windows only";
			return;
		}
		StrayConfig config = StrayConfig.get();
		WindowInfo target = resolve(config);
		if (target == null) {
			status = "Pick a window";
			return;
		}
		int[] size = new int[2];
		int[] argb = PipWin32.capture(target.id(), size);
		if (argb == null || size[0] < 1 || size[1] < 1) {
			status = "Can't capture";
			return;
		}
		FRAME.set(new Frame(argb, size[0], size[1], target.title()));
		DIRTY.set(true);
		status = target.title();
		if (!target.title().equals(config.pipWindowTitle)) {
			config.pipWindowTitle = target.title();
			config.pipWindowId = target.id();
		}
	}

	private static WindowInfo resolve(StrayConfig config) {
		List<WindowInfo> list = windows();
		if (list.isEmpty()) {
			return null;
		}
		for (WindowInfo info : list) {
			if (info.id().equals(config.pipWindowId)) {
				return info;
			}
		}
		if (config.pipWindowTitle != null && !config.pipWindowTitle.isBlank()) {
			String want = config.pipWindowTitle.toLowerCase();
			for (WindowInfo info : list) {
				if (info.title().equalsIgnoreCase(config.pipWindowTitle)) {
					return info;
				}
			}
			for (WindowInfo info : list) {
				if (info.title().toLowerCase().contains(want) || want.contains(info.title().toLowerCase())) {
					return info;
				}
			}
		}
		return null;
	}
}
