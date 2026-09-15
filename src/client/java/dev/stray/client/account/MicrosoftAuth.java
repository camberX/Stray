package dev.stray.client.account;

import com.google.gson.JsonObject;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import net.raphimc.minecraftauth.java.request.MinecraftProfileRequest;
import net.raphimc.minecraftauth.msa.data.MsaConstants;
import net.raphimc.minecraftauth.msa.data.MsaEnvironment;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.model.MsaToken;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Microsoft device-code and token refresh, using MinecraftAuth the same way
 * LiquidBounce does — without copying their Kotlin.
 */
final class MicrosoftAuth {
	private static final HttpClient HTTP = MinecraftAuth.createHttpClient("Stray");

	private MicrosoftAuth() {
	}

	static Session loginDeviceCode(Consumer<MsaDeviceCode> onCode) throws Exception {
		JavaAuthManager manager = JavaAuthManager.create(HTTP).login(
			(http, config, callback) -> new DeviceCodeMsaAuthService(http, config, callback, 300_000),
			onCode
		);
		return fromManager(manager);
	}

	static Session loginRefreshToken(String refreshToken) throws Exception {
		return loginRefreshToken(refreshToken, null);
	}

	static Session loginRefreshToken(String refreshToken, String clientId) throws Exception {
		return loginRefreshToken(refreshToken, clientId, null);
	}

	static Session loginRefreshToken(String refreshToken, String clientId, MsaEnvironment environment) throws Exception {
		JavaAuthManager.Builder builder = JavaAuthManager.create(HTTP);
		if (clientId != null && !clientId.isBlank()) {
			MsaApplicationConfig config = new MsaApplicationConfig(clientId, MsaConstants.SCOPE_OFFLINE_ACCESS);
			if (environment != null) {
				config = config.withEnvironment(environment);
			} else if (clientId.contains("-")) {
				config = config.withEnvironment(MsaEnvironment.MICROSOFT_ONLINE_CONSUMERS);
			}
			builder = builder.msaApplicationConfig(config);
		}
		JavaAuthManager.Builder ready = builder;
		return fromManager(withRetry(() -> ready.login(refreshToken)));
	}

	static Session loginMsa(String clientId, long expireTimeMs, String accessToken, String refreshToken) throws Exception {
		MsaApplicationConfig config = new MsaApplicationConfig(clientId, MsaConstants.SCOPE_OFFLINE_ACCESS)
			.withEnvironment(clientId != null && clientId.contains("-")
				? MsaEnvironment.MICROSOFT_ONLINE_CONSUMERS
				: MsaEnvironment.LIVE);
		MsaToken token = new MsaToken(expireTimeMs <= 0 ? Long.MAX_VALUE : expireTimeMs, accessToken, refreshToken);
		return fromManager(withRetry(() -> JavaAuthManager.create(HTTP).msaApplicationConfig(config).login(token)));
	}

	static Session fromAccessToken(String accessToken) throws Exception {
		MinecraftToken token = new MinecraftToken(Long.MAX_VALUE, "Bearer", accessToken);
		MinecraftProfile profile = withRetry(() -> HTTP.executeAndHandle(new MinecraftProfileRequest(token)));
		return new Session(profile.getName(), profile.getId(), accessToken, null);
	}

	private static <T> T withRetry(IoCall<T> call) throws Exception {
		Exception last = null;
		for (int attempt = 0; attempt < 4; attempt++) {
			try {
				return call.get();
			} catch (Exception exception) {
				last = exception;
				if (!rateLimited(exception) || attempt == 3) {
					throw exception;
				}
				Thread.sleep(2_500L * (attempt + 1));
			}
		}
		throw last;
	}

	private static boolean rateLimited(Throwable exception) {
		while (exception != null) {
			String message = exception.getMessage();
			if (message != null) {
				String lower = message.toLowerCase(Locale.ROOT);
				if (lower.contains("429") || lower.contains("rate") || lower.contains("too many")) {
					return true;
				}
			}
			exception = exception.getCause();
		}
		return false;
	}

	@FunctionalInterface
	private interface IoCall<T> {
		T get() throws Exception;
	}

	static Session restore(JsonObject stored) throws Exception {
		JavaAuthManager manager = JavaAuthManager.fromJson(HTTP, stored);
		return fromManager(manager);
	}

	private static Session fromManager(JavaAuthManager manager) throws Exception {
		MinecraftToken token = withRetry(() -> manager.getMinecraftToken().getUpToDate());
		MinecraftProfile profile = withRetry(() -> manager.getMinecraftProfile().getUpToDate());
		return new Session(profile.getName(), profile.getId(), token.getToken(), JavaAuthManager.toJson(manager));
	}

	record Session(String name, UUID uuid, String accessToken, JsonObject authManager) {
	}
}
