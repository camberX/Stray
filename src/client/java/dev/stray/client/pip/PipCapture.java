package dev.stray.client.pip;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Picture-in-picture capture. A daemon thread grabs the chosen window at
 * up to 60 FPS; the HUD only memcpy's and uploads when a frame is ready.
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
	private static WindowInfo cached;
	private static long cachedAt;
	private static int[] recycle;

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
		cached = next;
		cachedAt = System.nanoTime();
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
			int count = frame.width * frame.height;
			long pointer = image.getPointer();
			if (pointer != 0L) {
				for (int i = 0; i < count; i++) {
					MemoryUtil.memPutInt(pointer + ((long) i << 2), ARGB.toABGR(frame.argb[i]));
				}
			} else {
				int i = 0;
				for (int y = 0; y < frame.height; y++) {
					for (int x = 0; x < frame.width; x++) {
						image.setPixel(x, y, frame.argb[i++]);
					}
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
		cached = null;
		PipWin32.close();
	}

	private static void ensureWorker() {
		if (RUNNING.get() && worker != null && worker.isAlive()) {
			return;
		}
		RUNNING.set(true);
		Thread thread = new Thread(PipCapture::loop, "stray-pip");
		thread.setDaemon(true);
		thread.setPriority(Thread.NORM_PRIORITY + 1);
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
			int fps = StrayConfig.clamp(StrayConfig.get().pipFps, 15, 60);
			long period = 1_000_000_000L / fps;
			long remaining = started + period - System.nanoTime();
			if (remaining > 0L) {
				LockSupport.parkNanos(remaining);
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
		Frame shown = FRAME.get();
		int[] back = recycle;
		if (back != null && shown != null && back == shown.argb) {
			back = null;
		}
		int[] argb = PipWin32.capture(target.id(), size, back);
		if (argb == null || size[0] < 1 || size[1] < 1) {
			cached = null;
			status = "Can't capture";
			return;
		}
		recycle = shown != null && shown.argb != argb ? shown.argb : null;
		FRAME.set(new Frame(argb, size[0], size[1], target.title()));
		DIRTY.set(true);
		status = target.title();
		if (!target.title().equals(config.pipWindowTitle)) {
			config.pipWindowTitle = target.title();
			config.pipWindowId = target.id();
		}
	}

	private static WindowInfo resolve(StrayConfig config) {
		WindowInfo hold = cached;
		long now = System.nanoTime();
		if (hold != null && hold.id().equals(config.pipWindowId) && now - cachedAt < 2_000_000_000L) {
			return hold;
		}
		List<WindowInfo> list = windows();
		if (list.isEmpty()) {
			cached = null;
			return null;
		}
		WindowInfo found = null;
		for (WindowInfo info : list) {
			if (info.id().equals(config.pipWindowId)) {
				found = info;
				break;
			}
		}
		if (found == null && config.pipWindowTitle != null && !config.pipWindowTitle.isBlank()) {
			String want = config.pipWindowTitle.toLowerCase();
			for (WindowInfo info : list) {
				if (info.title().equalsIgnoreCase(config.pipWindowTitle)) {
					found = info;
					break;
				}
			}
			if (found == null) {
				for (WindowInfo info : list) {
					if (info.title().toLowerCase().contains(want) || want.contains(info.title().toLowerCase())) {
						found = info;
						break;
					}
				}
			}
		}
		cached = found;
		cachedAt = now;
		return found;
	}
}
