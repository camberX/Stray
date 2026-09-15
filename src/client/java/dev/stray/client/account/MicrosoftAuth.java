package dev.stray.client.account;

import com.google.gson.JsonObject;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import net.raphimc.minecraftauth.java.request.MinecraftProfileRequest;
import net.raphimc.minecraftauth.msa.data.MsaConstants;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;

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
		var builder = JavaAuthManager.create(HTTP);
		if (clientId != null && !clientId.isBlank()) {
			builder = builder.msaApplicationConfig(new MsaApplicationConfig(clientId, MsaConstants.SCOPE_OFFLINE_ACCESS));
		}
		return fromManager(builder.login(refreshToken));
	}

	static Session restore(JsonObject stored) throws Exception {
		JavaAuthManager manager = JavaAuthManager.fromJson(HTTP, stored);
		return fromManager(manager);
	}

	static Session fromAccessToken(String accessToken) throws Exception {
		MinecraftToken token = new MinecraftToken(Long.MAX_VALUE, "Bearer", accessToken);
		MinecraftProfile profile = HTTP.executeAndHandle(new MinecraftProfileRequest(token));
		return new Session(profile.getName(), profile.getId(), accessToken, null);
	}

	private static Session fromManager(JavaAuthManager manager) throws Exception {
		MinecraftToken token = manager.getMinecraftToken().getUpToDate();
		MinecraftProfile profile = manager.getMinecraftProfile().getUpToDate();
		return new Session(profile.getName(), profile.getId(), token.getToken(), JavaAuthManager.toJson(manager));
	}

	record Session(String name, UUID uuid, String accessToken, JsonObject authManager) {
	}
}
