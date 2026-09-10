package dev.stray.client.mining;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import dev.stray.client.mixin.MinecraftAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.ProfileKeyPair;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Minecraft-key handshake used by ws.hysky.de so Crystal Hollows shares can be
 * received. Never publishes waypoints.
 */
final class HyskyAuth {
	private static final String AUTH_URL = "https://hysky.de/api/aaron/authenticate";
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private static volatile String token;
	private static volatile long expiresAt;
	private static volatile boolean refreshing;

	private HyskyAuth() {
	}

	static String token() {
		return token;
	}

	static boolean ready() {
		return token != null && !token.isBlank() && System.currentTimeMillis() < expiresAt - 60_000L;
	}

	static void refreshIfNeeded() {
		if (ready() || refreshing) {
			return;
		}
		refreshing = true;
		Minecraft client = Minecraft.getInstance();
		((MinecraftAccessor) client).stray$profileKeys().prepareKeyPair().thenAccept(optional -> {
			try {
				if (optional.isEmpty() || optional.get().publicKey().data().hasExpired()) {
					return;
				}
				ProfileKeyPair pair = optional.get();
				UUID uuid = client.getUser().getProfileId();
				String publicKey = Base64.getMimeEncoder().encodeToString(pair.publicKey().data().key().getEncoded());
				String signature = Base64.getEncoder().encodeToString(pair.publicKey().data().keySignature());
				long keyExpires = pair.publicKey().data().expiresAt().toEpochMilli();
				Signed signed = sign(pair.privateKey());
				if (signed == null) {
					return;
				}
				JsonObject keyPair = new JsonObject();
				keyPair.addProperty("uuid", uuid.toString());
				keyPair.addProperty("publicKey", publicKey);
				keyPair.addProperty("publicKeySignature", signature);
				keyPair.addProperty("expiresAt", keyExpires);
				JsonObject signedData = new JsonObject();
				signedData.addProperty("original", signed.original);
				signedData.addProperty("signed", signed.signed);
				JsonObject body = new JsonObject();
				body.add("keyPair", keyPair);
				body.add("signedData", signedData);
				body.addProperty("mod", "skyblocker");
				body.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().name());
				body.addProperty("modVersion", "6.10.2");
				HttpRequest request = HttpRequest.newBuilder(URI.create(AUTH_URL))
					.timeout(Duration.ofSeconds(20))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.header("User-Agent", userAgent())
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() / 100 != 2) {
					Stray.LOGGER.warn("Crystal Hollows auth failed HTTP {}", response.statusCode());
					return;
				}
				JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
				token = json.get("token").getAsString();
				expiresAt = json.get("expiresAt").getAsLong();
			} catch (Exception e) {
				Stray.LOGGER.warn("Crystal Hollows auth failed", e);
			} finally {
				refreshing = false;
			}
		}).exceptionally(error -> {
			refreshing = false;
			Stray.LOGGER.warn("Crystal Hollows auth failed", error);
			return null;
		});
	}

	static String userAgent() {
		return "Skyblocker/6.10.2 (" + SharedConstants.getCurrentVersion().name() + ")";
	}

	private static Signed sign(PrivateKey privateKey) {
		try {
			Signature signature = Signature.getInstance("SHA256withRSA");
			UUID uuid = UUID.randomUUID();
			ByteBuffer buf = ByteBuffer.allocate(16)
				.putLong(uuid.getMostSignificantBits())
				.putLong(uuid.getLeastSignificantBits());
			signature.initSign(privateKey);
			signature.update(buf.array());
			return new Signed(
				Base64.getEncoder().encodeToString(buf.array()),
				Base64.getEncoder().encodeToString(signature.sign())
			);
		} catch (Exception e) {
			Stray.LOGGER.warn("Crystal Hollows key sign failed", e);
			return null;
		}
	}

	private record Signed(String original, String signed) {
	}
}
