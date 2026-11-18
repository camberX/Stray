package dev.stray.client.render.overlay.window;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hyprland: where the game window is (Wayland doesn't tell a client its own position), read
 * over Hyprland's socket off the render thread. Only reads; changes nothing in Hyprland.
 */
final class HyprlandIpc {
	static final String TITLE = "Stray overlay";

	/** The game window in its monitor's logical coordinates. */
	record Place(String monitor, int x, int y, int w, int h, double scale) {
	}

	private static volatile Place place;
	private static volatile Thread follower;
	private static volatile boolean stop;

	private HyprlandIpc() {
	}

	static boolean running() {
		return System.getenv("HYPRLAND_INSTANCE_SIGNATURE") != null;
	}

	static Place place() {
		return place;
	}

	private static Path socket() {
		String sig = System.getenv("HYPRLAND_INSTANCE_SIGNATURE");
		String runtime = System.getenv("XDG_RUNTIME_DIR");
		if (sig == null) {
			return null;
		}
		if (runtime != null) {
			Path p = Path.of(runtime, "hypr", sig, ".socket.sock");
			if (Files.exists(p)) {
				return p;
			}
		}
		Path old = Path.of("/tmp/hypr", sig, ".socket.sock");
		return Files.exists(old) ? old : null;
	}

	static String request(String command) {
		Path path = socket();
		if (path == null) {
			return "";
		}
		try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			channel.connect(UnixDomainSocketAddress.of(path));
			channel.write(ByteBuffer.wrap(command.getBytes(StandardCharsets.UTF_8)));
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ByteBuffer buf = ByteBuffer.allocate(16384);
			while (channel.read(buf) > 0) {
				out.write(buf.array(), 0, buf.position());
				buf.clear();
			}
			return out.toString(StandardCharsets.UTF_8);
		} catch (Exception e) {
			return "";
		}
	}

	static void follow() {
		if (follower != null || !running()) {
			return;
		}
		stop = false;
		Thread thread = new Thread(() -> {
			long pid = ProcessHandle.current().pid();
			while (!stop) {
				try {
					place = read(pid);
					Thread.sleep(100L);
				} catch (InterruptedException e) {
					return;
				} catch (Exception ignored) {
					try {
						Thread.sleep(500L);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		}, "Stray-Hyprland");
		thread.setDaemon(true);
		follower = thread;
		thread.start();
	}

	private static Place read(long pid) {
		JsonElement clients = JsonParser.parseString(request("j/clients"));
		JsonElement monitors = JsonParser.parseString(request("j/monitors"));
		if (!clients.isJsonArray() || !monitors.isJsonArray()) {
			return null;
		}
		JsonObject game = null;
		for (JsonElement el : (JsonArray) clients) {
			JsonObject c = el.getAsJsonObject();
			if (c.get("pid").getAsLong() == pid && (game == null || c.get("mapped").getAsBoolean())) {
				game = c;
			}
		}
		if (game == null) {
			return null;
		}
		int monitorId = game.get("monitor").getAsInt();
		for (JsonElement el : (JsonArray) monitors) {
			JsonObject m = el.getAsJsonObject();
			if (m.get("id").getAsInt() != monitorId) {
				continue;
			}
			JsonArray at = game.getAsJsonArray("at");
			JsonArray size = game.getAsJsonArray("size");
			return new Place(m.get("name").getAsString(),
				at.get(0).getAsInt() - m.get("x").getAsInt(), at.get(1).getAsInt() - m.get("y").getAsInt(),
				size.get(0).getAsInt(), size.get(1).getAsInt(), m.get("scale").getAsDouble());
		}
		return null;
	}

	static void stop() {
		stop = true;
		follower = null;
	}
}
