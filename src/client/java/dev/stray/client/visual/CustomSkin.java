package dev.stray.client.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A skin only you see: a PNG from disk replaces your own player's skin on this client (nothing
 * is uploaded), with the Steve (wide) or Alex (slim) arm model.
 */
public final class CustomSkin {
	private static final Identifier TEXTURE_ID = Stray.id("skin");
	private static final int MAX_BYTES = 4 * 1024 * 1024;

	private static volatile ClientAsset.Texture asset;
	private static volatile String status = "No skin";
	private static volatile boolean picking;
	private static PlayerSkin patched;
	private static PlayerSkin patchedFrom;
	private static boolean patchedSlim;
	private static ClientAsset.Texture patchedAsset;
	private static int generation;

	private CustomSkin() {
	}

	public static void init() {
		String path = StrayConfig.get().skinPath;
		if (path != null && !path.isBlank()) {
			load(Path.of(path), false);
		}
	}

	public static String status() {
		return picking ? "Selecting..." : status;
	}

	public static boolean active() {
		return StrayConfig.get().skinEnabled && asset != null;
	}

	/** The own player's skin with the custom texture and arm model swapped in. */
	public static PlayerSkin patch(PlayerSkin skin) {
		if (skin == null || !active()) {
			return skin;
		}
		boolean slim = StrayConfig.get().skinSlim;
		ClientAsset.Texture a = asset;
		if (patched == null || patchedFrom != skin || patchedSlim != slim || patchedAsset != a) {
			patched = new PlayerSkin(a, skin.cape(), skin.elytra(), slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, skin.secure());
			patchedFrom = skin;
			patchedSlim = slim;
			patchedAsset = a;
		}
		return patched;
	}

	public static void clear() {
		StrayConfig config = StrayConfig.get();
		config.skinPath = "";
		config.save();
		generation++;
		asset = null;
		status = "No skin";
	}

	public static void pick() {
		if (picking) {
			return;
		}
		picking = true;
		try {
			Minecraft.getInstance().mouseHandler.releaseMouse();
		} catch (Exception ignored) {
		}
		Thread thread = new Thread(() -> {
			String selected = null;
			try {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer filters = stack.mallocPointer(1);
					filters.put(stack.UTF8("*.png"));
					filters.flip();
					selected = TinyFileDialogs.tinyfd_openFileDialog("Select skin", System.getProperty("user.home", ""), filters, "PNG skin", false);
				} catch (Throwable e) {
					Stray.LOGGER.warn("Skin picker failed", e);
					status = "Can't open file picker";
				}
				final String picked = selected;
				if (picked != null && !picked.isBlank()) {
					Minecraft.getInstance().execute(() -> load(Path.of(picked), true));
				}
			} finally {
				picking = false;
			}
		}, "stray-skin-picker");
		thread.setDaemon(true);
		thread.start();
	}

	private static void load(Path path, boolean save) {
		StrayConfig config = StrayConfig.get();
		config.skinPath = path.toAbsolutePath().toString();
		if (save) {
			config.skinEnabled = true;
			config.save();
		}
		int gen = ++generation;
		status = "Loading...";
		Util.nonCriticalIoPool().execute(() -> {
			NativeImage image = null;
			try {
				if (!Files.isRegularFile(path)) {
					fail(gen, "Missing file");
					return;
				}
				if (Files.size(path) > MAX_BYTES) {
					fail(gen, "Too large");
					return;
				}
				image = NativeImage.read(Files.readAllBytes(path));
				int w = image.getWidth();
				int h = image.getHeight();
				if (w < 64 || w % 64 != 0 || (h != w && h * 2 != w)) {
					image.close();
					fail(gen, "Needs a 64x64 (or 64x32) skin");
					return;
				}
				NativeImage skin = h == w ? image : legacy(image);
				final NativeImage ready = skin;
				Minecraft.getInstance().execute(() -> {
					if (gen != generation) {
						ready.close();
						return;
					}
					Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, new DynamicTexture(() -> "stray-skin", ready));
					asset = new ClientAsset.ResourceTexture(TEXTURE_ID, TEXTURE_ID);
					status = path.getFileName() + " (" + ready.getWidth() + "x" + ready.getHeight() + ")";
				});
			} catch (Exception e) {
				if (image != null) {
					image.close();
				}
				fail(gen, "Can't read file");
			}
		});
	}

	/** Old 64x32 skins: copy the right arm and leg over to the left, like Minecraft does. */
	private static NativeImage legacy(NativeImage old) {
		int s = old.getWidth() / 64;
		NativeImage out = new NativeImage(old.getWidth(), old.getWidth(), true);
		out.copyFrom(old);
		old.close();
		copy(out, s, 4, 16, 16, 32, 4, 4, true);
		copy(out, s, 8, 16, 16, 32, 4, 4, true);
		copy(out, s, 0, 20, 24, 32, 4, 12, true);
		copy(out, s, 4, 20, 16, 32, 4, 12, true);
		copy(out, s, 8, 20, 8, 32, 4, 12, true);
		copy(out, s, 12, 20, 16, 32, 4, 12, true);
		copy(out, s, 44, 16, -8, 32, 4, 4, true);
		copy(out, s, 48, 16, -8, 32, 4, 4, true);
		copy(out, s, 40, 20, 0, 32, 4, 12, true);
		copy(out, s, 44, 20, -8, 32, 4, 12, true);
		copy(out, s, 48, 20, -16, 32, 4, 12, true);
		copy(out, s, 52, 20, -8, 32, 4, 12, true);
		return out;
	}

	private static void copy(NativeImage img, int s, int x, int y, int dx, int dy, int w, int h, boolean mirror) {
		img.copyRect(x * s, y * s, dx * s, dy * s, w * s, h * s, mirror, false);
	}

	private static void fail(int gen, String why) {
		Minecraft.getInstance().execute(() -> {
			if (gen == generation) {
				status = why;
			}
		});
	}
}
