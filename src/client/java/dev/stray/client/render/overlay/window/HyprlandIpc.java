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
 * Hyprland: Wayland windows can't place themselves, so the overlay is made floating, pinned,
 * unfocusable and hidden from screen sharing with window rules, then kept on top of the game
 * window with moves over Hyprland's socket. Runs off the render thread.
 */
final class HyprlandIpc {
	static final String TITLE = "Stray overlay";
	private static final String MATCH = "^(Stray overlay)$";
	private static volatile Thread follower;
	private static volatile boolean stop;

	private HyprlandIpc() {
	}

	static boolean running() {
		return System.getenv("HYPRLAND_INSTANCE_SIGNATURE") != null;
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

	/** Rules for every Hyprland syntax (0.53+ first); the ones a version doesn't know just fail. */
	static void addRules() {
		String[] modern = {
			"float on", "pin on", "no_focus on", "no_initial_focus on", "border_size 0", "no_shadow on", "rounding 0",
			"no_blur on", "no_anim on", "no_dim on", "no_screen_share on", "decorate off", "opacity 1.0 override"
		};
		for (String effect : modern) {
			request("keyword windowrule " + effect + ", match:title " + MATCH);
		}
		String[] legacy = {"float", "pin", "nofocus", "noinitialfocus", "noborder", "noshadow", "rounding 0", "noblur", "noanim", "nodim", "noscreenshare", "nodecorate"};
		for (String rule : legacy) {
			request("keyword windowrulev2 " + rule + ",title:" + MATCH);
			request("keyword windowrule " + rule + ",title:" + MATCH);
		}
	}

	/** Moves and sizes the overlay onto the game window whenever that moves. */
	static void follow() {
		if (follower != null) {
			return;
		}
		stop = false;
		Thread thread = new Thread(() -> {
			long pid = ProcessHandle.current().pid();
			String last = "";
			while (!stop) {
				try {
					String json = request("j/clients");
					JsonElement root = json.isEmpty() ? null : JsonParser.parseString(json);
					if (root != null && root.isJsonArray()) {
						JsonObject game = null;
						JsonObject overlay = null;
						for (JsonElement el : (JsonArray) root) {
							JsonObject c = el.getAsJsonObject();
							if (c.get("pid").getAsLong() != pid) {
								continue;
							}
							if (TITLE.equals(c.get("title").getAsString())) {
								overlay = c;
							} else if (game == null || c.get("mapped").getAsBoolean()) {
								game = c;
							}
						}
						if (game != null && overlay != null) {
							JsonArray at = game.getAsJsonArray("at");
							JsonArray size = game.getAsJsonArray("size");
							String want = at.get(0).getAsInt() + " " + at.get(1).getAsInt() + " " + size.get(0).getAsInt() + " " + size.get(1).getAsInt();
							JsonArray oat = overlay.getAsJsonArray("at");
							JsonArray osize = overlay.getAsJsonArray("size");
							String have = oat.get(0).getAsInt() + " " + oat.get(1).getAsInt() + " " + osize.get(0).getAsInt() + " " + osize.get(1).getAsInt();
							if (!want.equals(have) || !want.equals(last)) {
								String address = overlay.get("address").getAsString();
								if (!overlay.get("floating").getAsBoolean()) {
									request("dispatch setfloating address:" + address);
								}
								if (!overlay.get("pinned").getAsBoolean()) {
									request("dispatch pin address:" + address);
								}
								request("dispatch resizewindowpixel exact " + size.get(0).getAsInt() + " " + size.get(1).getAsInt() + ",address:" + address);
								request("dispatch movewindowpixel exact " + at.get(0).getAsInt() + " " + at.get(1).getAsInt() + ",address:" + address);
								last = want;
							}
						}
					}
					Thread.sleep(150L);
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

	static void stop() {
		stop = true;
		follower = null;
	}
}
