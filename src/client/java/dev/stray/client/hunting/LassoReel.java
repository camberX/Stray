package dev.stray.client.hunting;

import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.visual.NickHider;
import dev.stray.client.visual.NickSteal;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * While a lasso is held, mirror the pull bar above the leashed creature.
 * The first tick that bar says REEL sends one right click.
 */
public final class LassoReel {
	private static final int REARM_TICKS = 8;
	private static String line = "";
	private static boolean reel;
	private static boolean pulled;
	private static int quiet;

	private LassoReel() {
	}

	public static void tick(Minecraft client) {
		if (!StrayConfig.get().lassoDisplay) {
			reset();
			return;
		}
		if (client.player == null || client.level == null) {
			reset();
			return;
		}
		if (client.screen != null) {
			return;
		}
		if (!holdingLasso(client.player)) {
			reset();
			return;
		}
		boolean sawReel = false;
		String bar = "";
		for (Leashable leashed : Leashable.leashableLeashedTo(client.player)) {
			if (!(leashed instanceof Entity creature)) {
				continue;
			}
			Vec3 above = new Vec3(creature.getX(), creature.getY() + 2.0, creature.getZ());
			AABB area = new AABB(above, above).inflate(2.0);
			for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, area)) {
				Component name = stand.getCustomName();
				if (name == null) {
					continue;
				}
				String legacy = NickSteal.toLegacy(name);
				String plain = ChatFormatting.stripFormatting(legacy);
				if (plain != null && "REEL".equalsIgnoreCase(plain.trim())) {
					sawReel = true;
					break;
				}
				if (legacy.contains("§m") && bar.isEmpty()) {
					bar = legacy;
				}
			}
			if (sawReel) {
				break;
			}
		}
		if (sawReel) {
			quiet = 0;
			reel = true;
			line = "§e§lREEL";
			if (!pulled) {
				pulled = true;
				OdinClicks.rightClick();
			}
			return;
		}
		reel = false;
		line = bar;
		if (!pulled) {
			return;
		}
		quiet++;
		if (quiet >= REARM_TICKS) {
			pulled = false;
			quiet = 0;
		}
	}

	public static void reset() {
		line = "";
		reel = false;
		pulled = false;
		quiet = 0;
	}

	public static void extract(GuiGraphicsExtractor graphics) {
		if (line.isEmpty() || !StrayConfig.get().lassoDisplay) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.font == null) {
			return;
		}
		Component text = NickHider.parseLegacy(line);
		float scale = reel ? 2f : 1f;
		int width = client.font.width(text);
		float x = (graphics.guiWidth() - width * scale) * 0.5f;
		float y = graphics.guiHeight() * 0.36f;
		GuiDraw.text(graphics, client.font, text, x, y, scale, 0xFFFFFF, true);
	}

	private static boolean holdingLasso(LocalPlayer player) {
		return isLasso(player.getMainHandItem());
	}

	private static boolean isLasso(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		String id = OdinClicks.itemId(stack);
		if (id.toUpperCase(Locale.ROOT).contains("LASSO")) {
			return true;
		}
		boolean prior = ItemAppearance.suppress();
		try {
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) {
				return false;
			}
			for (Component row : lore.lines()) {
				if (lassoLine(row)) {
					return true;
				}
			}
			for (Component row : lore.styledLines()) {
				if (lassoLine(row)) {
					return true;
				}
			}
			return false;
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static boolean lassoLine(Component row) {
		if (row == null) {
			return false;
		}
		String plain = ChatFormatting.stripFormatting(row.getString());
		if (plain == null) {
			return false;
		}
		plain = plain.trim().toUpperCase(Locale.ROOT);
		return plain.equals("LASSO") || plain.endsWith(" LASSO");
	}
}
