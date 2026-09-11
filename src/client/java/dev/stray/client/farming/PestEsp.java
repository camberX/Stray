package dev.stray.client.farming;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Garden pest ESP using Skyblocker's armor-stand pest-head textures.
 * Tracers draw only while a held item's lore contains {@code VACUUM}.
 */
public final class PestEsp {
	private static final double RANGE_SQ = 96.0 * 96.0;
	private static List<Mark> view = List.of();

	private PestEsp() {
	}

	public record Mark(AABB box, Vec3 center) {
	}

	public static void tick(Minecraft client) {
		if (!active()) {
			if (!view.isEmpty()) {
				view = List.of();
			}
			return;
		}
		if (client.player == null || client.level == null) {
			view = List.of();
			return;
		}
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		List<Mark> next = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand)) {
				continue;
			}
			if (entity.distanceToSqr(camera) > RANGE_SQ) {
				continue;
			}
			if (!isPestHead(stand)) {
				continue;
			}
			AABB box = stand.getBoundingBox();
			if (box.getXsize() < 0.35 || box.getYsize() < 0.35) {
				box = box.inflate(0.22);
			}
			next.add(new Mark(box, box.getCenter()));
		}
		view = List.copyOf(next);
	}

	public static void reset() {
		view = List.of();
	}

	public static boolean active() {
		return StrayConfig.get().pestEspEnabled && SkyblockLocation.inGarden();
	}

	public static List<Mark> snapshot() {
		return view;
	}

	public static boolean holdingVacuum(LocalPlayer player) {
		if (player == null) {
			return false;
		}
		return isVacuum(player.getMainHandItem()) || isVacuum(player.getOffhandItem());
	}

	static boolean isPestHead(ArmorStand stand) {
		ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
		return isPestTexture(headTexture(helmet));
	}

	static boolean isPestTexture(String texture) {
		if (texture == null || texture.isEmpty()) {
			return false;
		}
		if (PestHeads.TEXTURES.contains(texture)) {
			return true;
		}
		String hash = textureHash(texture);
		return !hash.isEmpty() && PestHeads.HASHES.contains(hash);
	}

	static boolean isVacuum(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		boolean prior = ItemAppearance.suppress();
		try {
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) {
				return false;
			}
			for (Component line : lore.lines()) {
				if (plain(line).contains("VACUUM")) {
					return true;
				}
			}
			for (Component line : lore.styledLines()) {
				if (plain(line).contains("VACUUM")) {
					return true;
				}
			}
			return false;
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static String headTexture(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) {
			return "";
		}
		ResolvableProfile profile = stack.get(DataComponents.PROFILE);
		if (profile == null) {
			return "";
		}
		GameProfile partial = profile.partialProfile();
		if (partial == null) {
			return "";
		}
		for (Property property : partial.properties().get("textures")) {
			if (property == null) {
				continue;
			}
			String value = property.value();
			if (value != null && !value.isEmpty()) {
				return value;
			}
		}
		return "";
	}

	private static String textureHash(String texture) {
		try {
			byte[] decoded = Base64.getDecoder().decode(texture);
			String json = new String(decoded, StandardCharsets.UTF_8);
			int at = json.indexOf("/texture/");
			if (at < 0) {
				return "";
			}
			int start = at + "/texture/".length();
			int end = start;
			while (end < json.length()) {
				char c = json.charAt(end);
				if (c == '"' || c == '\\' || Character.isWhitespace(c)) {
					break;
				}
				end++;
			}
			return json.substring(start, end);
		} catch (IllegalArgumentException ignored) {
			return "";
		}
	}

	private static String plain(Component line) {
		if (line == null) {
			return "";
		}
		return ChatFormatting.stripFormatting(line.getString()).toUpperCase(Locale.ROOT);
	}
}
