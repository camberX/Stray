package dev.stray.client.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skins set through Stray: everyone with Stray sees them, the real Minecraft skin is untouched.
 * The skin hash and arm model come with the player's cosmetic status; the PNG is downloaded
 * once per hash from stray.gay/skins/{uuid}.png.
 */
public final class ShopSkin {
	private static final String BASE = "https://stray.gay";
	private static final int MAX_BYTES = 64 * 1024;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Map<UUID, Entry> SKINS = new ConcurrentHashMap<>();

	private ShopSkin() {
	}

	private static final class Entry {
		volatile String hash = "";
		volatile boolean slim;
		volatile ClientAsset.Texture asset;
		volatile boolean loading;
		PlayerSkin patched;
		PlayerSkin patchedFrom;
		ClientAsset.Texture patchedAsset;
		boolean patchedSlim;
	}

	/** From the cosmetic status: an empty hash means no Stray skin. */
	public static void set(UUID uuid, String hash, boolean slim) {
		if (uuid == null) {
			return;
		}
		if (hash == null || hash.isBlank()) {
			SKINS.remove(uuid);
			return;
		}
		Entry entry = SKINS.computeIfAbsent(uuid, id -> new Entry());
		entry.slim = slim;
		if (hash.equals(entry.hash) && (entry.asset != null || entry.loading)) {
			return;
		}
		entry.hash = hash;
		entry.loading = true;
		Util.nonCriticalIoPool().execute(() -> download(uuid, entry, hash));
	}

	/** Shows a skin right away (your own upload), before the server copy is fetched. */
	public static void setLocal(UUID uuid, byte[] png, String hash, boolean slim) {
		if (uuid == null || png == null) {
			return;
		}
		Entry entry = SKINS.computeIfAbsent(uuid, id -> new Entry());
		entry.slim = slim;
		entry.hash = hash == null ? "local" : hash;
		install(uuid, entry, png, entry.hash);
	}

	public static void setSlim(UUID uuid, boolean slim) {
		Entry entry = uuid == null ? null : SKINS.get(uuid);
		if (entry != null) {
			entry.slim = slim;
		}
	}

	public static boolean has(UUID uuid) {
		Entry entry = uuid == null ? null : SKINS.get(uuid);
		return entry != null && entry.asset != null;
	}

	public static PlayerSkin patch(UUID uuid, PlayerSkin skin) {
		Entry entry = uuid == null || skin == null ? null : SKINS.get(uuid);
		if (entry == null || entry.asset == null) {
			return skin;
		}
		synchronized (entry) {
			if (entry.patched == null || entry.patchedFrom != skin || entry.patchedAsset != entry.asset || entry.patchedSlim != entry.slim) {
				entry.patched = new PlayerSkin(entry.asset, skin.cape(), skin.elytra(), entry.slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, skin.secure());
				entry.patchedFrom = skin;
				entry.patchedAsset = entry.asset;
				entry.patchedSlim = entry.slim;
			}
			return entry.patched;
		}
	}

	private static void download(UUID uuid, Entry entry, String hash) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/skins/" + uuid + ".png?h=" + hash))
				.timeout(Duration.ofSeconds(12))
				.header("User-Agent", "Stray")
				.GET()
				.build();
			HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
			byte[] body = response.body();
			if (response.statusCode() != 200 || body == null || body.length > MAX_BYTES) {
				entry.loading = false;
				return;
			}
			install(uuid, entry, body, hash);
		} catch (Exception exception) {
			entry.loading = false;
		}
	}

	private static void install(UUID uuid, Entry entry, byte[] png, String hash) {
		NativeImage image;
		try {
			image = NativeImage.read(png);
		} catch (Exception exception) {
			entry.loading = false;
			return;
		}
		if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
			image.close();
			entry.loading = false;
			return;
		}
		NativeImage ready = image.getHeight() == 64 ? image : CustomSkin.legacy(image);
		Minecraft.getInstance().execute(() -> {
			if (!hash.equals(entry.hash)) {
				ready.close();
				return;
			}
			try {
				String safe = hash.replaceAll("[^a-z0-9_]", "");
				Identifier id = Stray.id("shop_skin/" + uuid.toString().replace("-", "") + "_" + (safe.isEmpty() ? "x" : safe));
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "stray-shop-skin-" + uuid, ready));
				entry.asset = new ClientAsset.ResourceTexture(id, id);
			} catch (Exception exception) {
				ready.close();
			}
			entry.loading = false;
		});
	}
}
