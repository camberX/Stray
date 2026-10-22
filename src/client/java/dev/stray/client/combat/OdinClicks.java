package dev.stray.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.lwjgl.glfw.GLFW;

/**
 * OdinClient click / item helpers, copied from PlayerUtils.kt, Utils.kt,
 * ItemUtils.kt, and AutoClicker.kt. Do not "improve" these.
 */
public final class OdinClicks {
	private OdinClicks() {
	}

	public static void rightClick() {
		Minecraft client = Minecraft.getInstance();
		if (client.options == null) {
			return;
		}
		InputConstants.Key key = ((KeyMappingAccessor) client.options.keyUse).stray$boundKey();
		KeyMapping.set(key, true);
		KeyMapping.click(key);
		KeyMapping.set(key, false);
	}

	public static void leftClick() {
		Minecraft client = Minecraft.getInstance();
		if (client.options == null) {
			return;
		}
		InputConstants.Key key = ((KeyMappingAccessor) client.options.keyAttack).stray$boundKey();
		KeyMapping.set(key, true);
		KeyMapping.click(key);
		KeyMapping.set(key, false);
	}

	public static void holdAttack() {
		Minecraft client = Minecraft.getInstance();
		if (client.options == null) {
			return;
		}
		KeyMapping.set(((KeyMappingAccessor) client.options.keyAttack).stray$boundKey(), true);
	}

	public static void guiClick(int id, int index, int button, ContainerInput clickType) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null) {
			return;
		}
		if (client.gameMode == null) {
			return;
		}
		client.gameMode.handleContainerInput(id, index, button, clickType, player);
	}

	public static CompoundTag customData(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}

	public static String itemId(ItemStack stack) {
		return customData(stack).getString("id").orElse("");
	}

	public static String nullableId(ItemStack stack) {
		return customData(stack).getString("id").orElse(null);
	}

	public static String nullableUuid(ItemStack stack) {
		return customData(stack).getString("uuid").orElse(null);
	}

	public static String held() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return null;
		}
		ItemStack held = client.player.getMainHandItem();
		String uuid = nullableUuid(held);
		if (uuid != null) {
			return uuid;
		}
		String id = nullableId(held);
		if (id != null) {
			return id;
		}
		return held.getHoverName().getString();
	}

	public static boolean hasGlint(ItemStack stack) {
		return stack.getComponents().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != null;
	}

	public static String noControlCodes(String value) {
		if (value == null) {
			return "";
		}
		int len = value.length();
		if (value.indexOf('§') == -1) {
			return value;
		}
		char[] out = new char[len];
		int outPos = 0;
		int i = 0;
		while (i < len) {
			char c = value.charAt(i);
			if (c == '§') {
				i += 2;
			} else {
				out[outPos++] = c;
				i++;
			}
		}
		return new String(out, 0, outPos);
	}

	public static boolean isPressed(InputConstants.Key key) {
		if (!bound(key)) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		var window = client.getWindow();
		int value = key.getValue();
		if (value > 7) {
			return InputConstants.isKeyDown(window, value);
		}
		return GLFW.glfwGetMouseButton(window.handle(), value) == GLFW.GLFW_PRESS;
	}

	public static boolean bound(InputConstants.Key key) {
		return key != null && !key.equals(InputConstants.UNKNOWN) && key.getValue() != -1;
	}

	public static InputConstants.Key parseKey(String name) {
		if (name == null || name.isBlank()) {
			return InputConstants.UNKNOWN;
		}
		try {
			return InputConstants.getKey(name);
		} catch (RuntimeException ignored) {
			return InputConstants.UNKNOWN;
		}
	}

	public static String keyName(InputConstants.Key key) {
		return key == null ? InputConstants.UNKNOWN.getName() : key.getName();
	}

	public static String keyLabel(InputConstants.Key key) {
		if (key == null || key.equals(InputConstants.UNKNOWN)) {
			return "None";
		}
		return key.getDisplayName().getString();
	}
}
