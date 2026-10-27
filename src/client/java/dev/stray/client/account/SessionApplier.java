package dev.stray.client.account;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilEnvironment;
import dev.stray.client.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.server.Services;

import java.net.Proxy;
import java.util.Optional;
import java.util.UUID;

/**
 * Hands a new Microsoft session to the running client. {@code Minecraft.user}
 * is final, so this goes through accessors.
 */
public final class SessionApplier {
	private SessionApplier() {
	}

	public static void apply(Minecraft client, String name, UUID uuid, String accessToken) {
		if (client == null || name == null || name.isBlank() || uuid == null || accessToken == null || accessToken.isBlank()) {
			throw new IllegalArgumentException("Missing session fields");
		}
		User user = new User(
			name,
			uuid,
			accessToken,
			Optional.empty(),
			Optional.of(UUID.randomUUID().toString())
		);
		YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(
			Proxy.NO_PROXY,
			YggdrasilEnvironment.PROD.getEnvironment()
		);
		UserApiService userApi = auth.createUserApiService(accessToken);
		ProfileKeyPairManager keys;
		try {
			keys = ProfileKeyPairManager.create(userApi, user, client.gameDirectory.toPath());
		} catch (Exception exception) {
			keys = ProfileKeyPairManager.EMPTY_KEY_MANAGER;
		}
		Services previous = client.services();
		Services next = new Services(
			auth.createMinecraftSessionService(),
			auth.getServicesKeySet(),
			auth.createProfileRepository(),
			previous.nameToIdCache(),
			previous.profileResolver()
		);
		MinecraftAccessor access = (MinecraftAccessor) client;
		access.stray$user(user);
		access.stray$services(next);
		access.stray$userApiService(userApi);
		access.stray$profileKeys(keys);
	}

	public static Snapshot snapshot(Minecraft client) {
		return new Snapshot(
			client.getUser(),
			client.services(),
			((MinecraftAccessor) client).stray$profileKeys(),
			userApi(client)
		);
	}

	public static void restore(Minecraft client, Snapshot snapshot) {
		if (snapshot == null || snapshot.user == null) {
			throw new IllegalStateException("No launcher session to restore");
		}
		MinecraftAccessor access = (MinecraftAccessor) client;
		access.stray$user(snapshot.user);
		if (snapshot.services != null) {
			access.stray$services(snapshot.services);
		}
		if (snapshot.userApi != null) {
			access.stray$userApiService(snapshot.userApi);
		}
		access.stray$profileKeys(snapshot.keys == null ? ProfileKeyPairManager.EMPTY_KEY_MANAGER : snapshot.keys);
	}

	private static UserApiService userApi(Minecraft client) {
		return ((MinecraftAccessor) client).stray$userApi();
	}

	public record Snapshot(User user, Services services, ProfileKeyPairManager keys, UserApiService userApi) {
	}
}
