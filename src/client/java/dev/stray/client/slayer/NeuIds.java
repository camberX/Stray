package dev.stray.client.slayer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.client.item.ItemIds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;

/**
 * NEU-style internal names ("SMITE;6", "ZOMBIE_SLAYER_RUNE;1", "GHOUL;3"), which the slayer drop
 * lists, RNG meter scores and price lists use, from a SkyBlock item.
 */
public final class NeuIds {
	private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");

	private NeuIds() {
	}

	public static String of(ItemStack stack) {
		String id = ItemIds.skyblockId(stack);
		if (id == null) {
			return null;
		}
		id = id.toUpperCase(Locale.ROOT);
		CompoundTag tag = extra(stack);
		if (tag == null) {
			return id;
		}
		switch (id) {
			case "ENCHANTED_BOOK" -> {
				CompoundTag ench = tag.getCompoundOrEmpty("enchantments");
				if (ench.size() == 1) {
					String key = ench.keySet().iterator().next();
					return key.toUpperCase(Locale.ROOT) + ";" + ench.getIntOr(key, 1);
				}
			}
			case "RUNE", "UNIQUE_RUNE" -> {
				CompoundTag runes = tag.getCompoundOrEmpty("runes");
				if (runes.size() == 1) {
					String key = runes.keySet().iterator().next();
					return key.toUpperCase(Locale.ROOT) + "_RUNE;" + runes.getIntOr(key, 1);
				}
			}
			case "PET" -> {
				String info = tag.getStringOr("petInfo", "");
				if (!info.isEmpty()) {
					try {
						JsonObject pet = JsonParser.parseString(info).getAsJsonObject();
						String type = pet.get("type").getAsString().toUpperCase(Locale.ROOT);
						int tier = RARITIES.indexOf(pet.get("tier").getAsString().toUpperCase(Locale.ROOT));
						return type + ";" + Math.max(0, tier);
					} catch (RuntimeException ignored) {
					}
				}
			}
			case "ATTRIBUTE_SHARD" -> {
				CompoundTag attributes = tag.getCompoundOrEmpty("attributes");
				if (attributes.size() == 1) {
					String key = attributes.keySet().iterator().next();
					return "ATTRIBUTE_SHARD_" + key.toUpperCase(Locale.ROOT) + ";1";
				}
			}
			default -> {
			}
		}
		return id;
	}

	private static CompoundTag extra(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		CompoundTag nested = tag.getCompoundOrEmpty("ExtraAttributes");
		return nested.isEmpty() ? tag : nested;
	}

	/** The enchantment level on an item (0 when absent), e.g. "champion". */
	public static int enchant(ItemStack stack, String name) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		CompoundTag tag = extra(stack);
		if (tag == null) {
			return 0;
		}
		return tag.getCompoundOrEmpty("enchantments").getIntOr(name, 0);
	}
}
