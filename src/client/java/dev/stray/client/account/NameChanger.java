package dev.stray.client.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Changes the Minecraft username of the account you are playing on, the same way minecraft.net
 * does: Minecraft Services' profile API with the session's own access token.
 * <ul>
 * <li>{@code GET /minecraft/profile/namechange}: whether a change is allowed now (one every 30 days).</li>
 * <li>{@code GET /minecraft/profile/name/<name>/available}: AVAILABLE, DUPLICATE or NOT_ALLOWED.</li>
 * <li>{@code PUT /minecraft/profile/name/<name>}: the change itself (3 tries per account and IP).</li>
 * </ul>
 * Nothing here runs unless you press the buttons; the change itself needs a second press.
 */
public final class NameChanger {
	private static final String API = "https://api.minecraftservices.com/minecraft/profile";
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static volatile boolean busy;

	private NameChanger() {
	}

	public static boolean busy() {
		return busy;
	}

	/** Why a name can't be used at all, or null: 3 to 16 letters, digits and underscores. */
	public static String invalid(String name) {
		if (name == null || name.isBlank()) {
			return "Type the new name first.";
		}
		if (!NAME.matcher(name).matches()) {
			return "Names are 3 to 16 letters, digits or underscores.";
		}
		return null;
	}

	/** Whether the account may change its name now, and from when if not. */
	public static void eligibility(Consumer<String> status) {
		run(status, token -> {
			HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(API + "/namechange")).GET(), token);
			if (response.statusCode() != 200) {
				return problem(response);
			}
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			if (json.has("nameChangeAllowed") && json.get("nameChangeAllowed").getAsBoolean()) {
				return "You can change this account's name now.";
			}
			if (json.has("changedAt")) {
				Instant next = Instant.parse(json.get("changedAt").getAsString()).plus(Duration.ofDays(30));
				return "Name changed recently: the next change is possible from " + DAY.format(next) + ".";
			}
			return "This account can't change its name right now.";
		});
	}

	/** Whether {@code name} is free for this account. */
	public static void check(String name, Consumer<String> status) {
		String bad = invalid(name);
		if (bad != null) {
			status.accept(bad);
			return;
		}
		run(status, token -> {
			HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(API + "/name/" + encode(name) + "/available")).GET(), token);
			if (response.statusCode() != 200) {
				return problem(response);
			}
			String state = JsonParser.parseString(response.body()).getAsJsonObject().get("status").getAsString();
			return switch (state) {
				case "AVAILABLE" -> name + " is available.";
				case "DUPLICATE" -> name + " is taken.";
				case "NOT_ALLOWED" -> name + " isn't allowed by Mojang.";
				default -> name + ": " + state.toLowerCase(Locale.ROOT) + ".";
			};
		});
	}

	/**
	 * Renames the account in use to {@code name}, then carries on with the new name in this game
	 * session and in the saved accounts list. {@code done} gets the new name once it worked.
	 */
	public static void change(String name, Consumer<String> status, Consumer<String> done) {
		String bad = invalid(name);
		if (bad != null) {
			status.accept(bad);
			return;
		}
		run(status, token -> {
			HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(API + "/name/" + encode(name))).PUT(HttpRequest.BodyPublishers.noBody()), token);
			if (response.statusCode() != 200) {
				return problem(response);
			}
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			String now = json.has("name") ? json.get("name").getAsString() : name;
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> {
				User user = client.getUser();
				UUID id = user.getProfileId();
				try {
					SessionApplier.apply(client, now, id, user.getAccessToken());
				} catch (Exception exception) {
					Stray.LOGGER.warn("Couldn't carry the new name into this session", exception);
				}
				AccountStore.renamed(id, now);
				done.accept(now);
			});
			return "Renamed to " + now + ". Servers see the new name after you reconnect.";
		});
	}

	private interface Call {
		String run(String token) throws Exception;
	}

	private static void run(Consumer<String> status, Call call) {
		Minecraft client = Minecraft.getInstance();
		User user = client == null ? null : client.getUser();
		String token = user == null ? null : user.getAccessToken();
		if (token == null || token.isBlank() || "0".equals(token)) {
			status.accept("This session has no Microsoft sign-in to change a name with.");
			return;
		}
		if (busy) {
			status.accept("Still waiting for Mojang…");
			return;
		}
		busy = true;
		status.accept("Asking Mojang…");
		Thread thread = new Thread(() -> {
			try {
				status.accept(call.run(token));
			} catch (Exception exception) {
				Stray.LOGGER.warn("Name change request failed", exception);
				String message = exception.getMessage();
				status.accept("Couldn't reach Mojang" + (message == null || message.isBlank() ? "." : ": " + message));
			} finally {
				busy = false;
			}
		}, "stray-name-change");
		thread.setDaemon(true);
		thread.start();
	}

	private static HttpResponse<String> send(HttpRequest.Builder request, String token) throws Exception {
		return HTTP.send(request
			.timeout(Duration.ofSeconds(15))
			.header("Authorization", "Bearer " + token)
			.header("Accept", "application/json")
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** What a refused request means, in words. */
	private static String problem(HttpResponse<String> response) {
		int code = response.statusCode();
		String detail = "";
		try {
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			if (json.has("details") && json.getAsJsonObject("details").has("status")) {
				detail = json.getAsJsonObject("details").get("status").getAsString();
			}
		} catch (Exception ignored) {
			// Not JSON: the status code alone says enough.
		}
		return switch (code) {
			case 400 -> "Mojang rejected that name: 3 to 16 letters, digits or underscores.";
			case 401 -> "The sign-in has expired: switch to the account again, then retry.";
			case 403 -> "DUPLICATE".equals(detail) ? "That name is taken."
				: "NOT_ALLOWED".equals(detail) ? "Mojang doesn't allow that name."
				: "This account can't change its name now (once every 30 days).";
			case 404 -> "This account has no Minecraft profile.";
			case 429 -> "Too many tries: Mojang allows a few per account and connection. Wait a while.";
			default -> "Mojang answered " + code + ".";
		};
	}

	private static String encode(String name) {
		return URLEncoder.encode(name, StandardCharsets.UTF_8);
	}
}
