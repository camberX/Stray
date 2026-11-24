package dev.stray.client.net;

/**
 * Never two hotbar slot changes in one tick. Vanilla sends a pending slot change (e.g. from
 * scrolling) at the start of the tick; a feature that swaps after that in the same tick would
 * send a second one. Features that swap ask {@link #canSwap()} first and swap a tick later if not.
 * Watching the packet only looks; nothing is cancelled or changed.
 */
public final class SlotGuard {
	private static boolean sentThisTick;

	private SlotGuard() {
	}

	/** Tick start (START_CLIENT_TICK, before vanilla sends a pending slot change). */
	public static void onTickStart() {
		sentThisTick = false;
	}

	/** A slot change on its way to the server. */
	public static void onSlotSent() {
		sentThisTick = true;
	}

	/** False when a slot change already went to the server this tick. */
	public static boolean canSwap() {
		return !sentThisTick;
	}
}
