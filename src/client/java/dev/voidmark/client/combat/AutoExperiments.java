package dev.voidmark.client.combat;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * OdinClient AutoExperiments.kt, including Ultrasequencer's inverted nextClick.
 */
public final class AutoExperiments {
	private static final Pattern DIGITS = Pattern.compile("\\d+");
	private static ExperimentHandler handler;
	private static long lastClick;

	private AutoExperiments() {
	}

	public static void reset() {
		handler = null;
		lastClick = 0;
	}

	public static void onOpen(Screen screen) {
		if (!VoidmarkConfig.get().autoExperimentsEnabled) {
			return;
		}
		if (screen == null) {
			return;
		}
		String title = screen.getTitle().getString();
		if (title.startsWith("Chronomatron (")) {
			handler = new ChronomatronHandler();
		} else if (title.startsWith("Ultrasequencer (")) {
			handler = new UltrasequencerHandler();
		} else {
			handler = null;
		}
	}

	public static boolean blockMouse(Screen screen) {
		if (handler == null) {
			return false;
		}
		if (!(screen instanceof AbstractContainerScreen<?>)) {
			return false;
		}
		return VoidmarkConfig.get().autoExperimentsEnabled;
	}

	public static void onPacket(Packet<?> packet) {
		if (!VoidmarkConfig.get().autoExperimentsEnabled) {
			return;
		}
		if (handler == null) {
			return;
		}
		if (packet instanceof ClientboundContainerSetSlotPacket) {
			handler.onSlotUpdate();
		}
	}

	public static void tick(Minecraft client) {
		if (!VoidmarkConfig.get().autoExperimentsEnabled) {
			return;
		}
		ExperimentHandler current = handler;
		if (current == null) {
			return;
		}
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastClick < delay()) {
			return;
		}

		Integer slotId = current.nextClick();
		if (slotId != null) {
			OdinClicks.guiClick(screen.getMenu().containerId, slotId, 0, ContainerInput.CLONE);
			lastClick = now;
		}

		if (!current.shouldClose(VoidmarkConfig.get().autoExperimentsAutoClose)) {
			return;
		}

		if (client.player != null) {
			client.player.closeContainer();
		}
		handler = null;
	}

	private static long delay() {
		VoidmarkConfig config = VoidmarkConfig.get();
		int clickDelay = config.autoExperimentsClickDelay;
		int delayVariety = config.autoExperimentsDelayVariety;
		int extra = delayVariety <= 0 ? 0 : (int) (Math.random() * (delayVariety + 1));
		return (long) (clickDelay + extra);
	}

	private static abstract class ExperimentHandler {
		protected int clicks;
		protected boolean hasData;

		abstract void onSlotUpdate();

		abstract Integer nextClick();

		abstract boolean shouldClose(boolean autoClose);
	}

	private static final class ChronomatronHandler extends ExperimentHandler {
		private final List<Integer> order = new ArrayList<>();
		private int lastAddedSlot = -1;
		private boolean close;

		@Override
		void onSlotUpdate() {
			Minecraft client = Minecraft.getInstance();
			if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
				return;
			}
			List<Slot> slots = screen.getMenu().slots;
			if (slots.size() <= 49) {
				return;
			}
			ItemStack center = slots.get(49).getItem();

			if (
				lastAddedSlot != -1 &&
				center.getItem() == Items.GLOWSTONE &&
				!OdinClicks.hasGlint(slots.get(lastAddedSlot).getItem())
			) {
				int serum = VoidmarkConfig.get().autoExperimentsSerumCount;
				close = order.size() > (VoidmarkConfig.get().autoExperimentsGetMaxXp ? 15 : 11 - serum);
				hasData = false;
				return;
			}

			if (hasData || center.getItem() != Items.CLOCK) {
				return;
			}

			Slot slot = null;
			for (Slot candidate : slots) {
				if (candidate.index >= 10 && candidate.index <= 43 && OdinClicks.hasGlint(candidate.getItem())) {
					slot = candidate;
					break;
				}
			}
			if (slot == null) {
				return;
			}

			order.add(slot.index);
			lastAddedSlot = slot.index;
			hasData = true;
			clicks = 0;
		}

		@Override
		Integer nextClick() {
			return hasData && clicks < order.size() ? order.get(clicks++) : null;
		}

		@Override
		boolean shouldClose(boolean autoClose) {
			if (!autoClose || !close) {
				return false;
			}
			if (clicks < order.size()) {
				return false;
			}
			close = false;
			return true;
		}
	}

	private static final class UltrasequencerHandler extends ExperimentHandler {
		private final ConcurrentHashMap<Integer, Integer> order = new ConcurrentHashMap<>();

		@Override
		void onSlotUpdate() {
			Minecraft client = Minecraft.getInstance();
			if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
				return;
			}
			List<Slot> slots = screen.getMenu().slots;
			if (slots.size() <= 49) {
				return;
			}
			ItemStack center = slots.get(49).getItem();

			if (center.getItem() == Items.CLOCK) {
				hasData = false;
				return;
			}

			if (hasData || center.getItem() != Items.GLOWSTONE) {
				return;
			}

			order.clear();

			for (Slot slot : slots) {
				if (slot.index >= 9 && slot.index <= 44
					&& DIGITS.matcher(OdinClicks.noControlCodes(slot.getItem().getHoverName().getString())).matches()) {
					order.put(slot.getItem().getCount() - 1, slot.index);
				}
			}

			hasData = true;
			clicks = 0;
		}

		@Override
		Integer nextClick() {
			return !hasData ? order.get(clicks++) : null;
		}

		@Override
		boolean shouldClose(boolean autoClose) {
			int serum = VoidmarkConfig.get().autoExperimentsSerumCount;
			return autoClose && order.size() > (VoidmarkConfig.get().autoExperimentsGetMaxXp ? 20 : 9 - serum);
		}
	}
}
