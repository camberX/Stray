package dev.stray.client.net;

import dev.stray.Stray;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;

/**
 * Server ticks, counted exactly like NoammAddons' ServerTickFixer (CC0): one tick per
 * {@code ClientboundPingPacket} with a non-zero id, seen as a packet of its own (never one inside a
 * bundle). In a world that sends no pings, each ~50 ms burst of packets counts as a tick, kept in
 * line with the server's game time from {@code ClientboundSetTimePacket} every second.
 *
 * <p>Called from the network thread for every top-level packet; the ticks are posted on the render
 * thread, before vanilla handles the same packet.
 */
public final class ServerTicks {
	private static final long BURST_WINDOW = 38_000_000L;
	private static final long TICK_NANOS = 50_000_000L;
	private static final long MAX_CORRECTION = 100L;
	private static final int PINGLESS_BURSTS = 20;
	private static final long MAX_PINGLESS_GAP = 250_000_000L;
	private static final long FREEZE_NANOS = 2_000_000_000L;

	private static long lastBurst;
	private static int pinglessBursts;
	private static long anchorGameTime = -1L;
	private static long postedTicks;
	private static long pendingSkips;
	private static boolean usingPings = true;
	private static boolean worldHasPings;

	private ServerTicks() {
	}

	public static synchronized void onPacket(Packet<?> packet) {
		long now = System.nanoTime();
		if (packet instanceof ClientboundKeepAlivePacket || packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundPongResponsePacket) {
			return;
		}
		if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
			resetWorld();
			return;
		}
		if (packet instanceof ClientboundPingPacket ping) {
			if (ping.getId() != 0) {
				pinglessBursts = 0;
				worldHasPings = true;
				anchorGameTime = -1L;
				usingPings = true;
				post(1);
				return;
			}
		}
		if (usingPings) {
			if (worldHasPings || now - lastBurst <= BURST_WINDOW) {
				return;
			}
			pinglessBursts = now - lastBurst > MAX_PINGLESS_GAP ? 1 : pinglessBursts + 1;
			lastBurst = now;
			if (pinglessBursts < PINGLESS_BURSTS) {
				return;
			}
			usingPings = false;
			Stray.LOGGER.info("[ServerTicks] no pings: counting packet bursts + SetTime as server ticks");
			reset();
		}
		if (now - lastBurst > BURST_WINDOW) {
			long gap = now - lastBurst;
			long ticks = lastBurst == 0L || gap > FREEZE_NANOS ? 1L : Math.max(1L, (long) (gap / (double) TICK_NANOS + 0.4));
			lastBurst = now;
			long skipped = Math.min(ticks, pendingSkips);
			pendingSkips -= skipped;
			post(ticks - skipped);
		}
		if (packet instanceof ClientboundSetTimePacket time) {
			correct(time.gameTime());
		}
	}

	private static void correct(long gameTime) {
		if (anchorGameTime == -1L) {
			anchorGameTime = gameTime;
			postedTicks = 0L;
			pendingSkips = 0L;
			return;
		}
		long deficit = gameTime - anchorGameTime - postedTicks;
		if (deficit < -MAX_CORRECTION || deficit > MAX_CORRECTION) {
			reset();
			return;
		}
		pendingSkips = Math.max(0L, -deficit);
		if (deficit > 0L) {
			post(deficit);
		}
	}

	private static void post(long count) {
		if (count <= 0L) {
			return;
		}
		if (anchorGameTime != -1L) {
			postedTicks += count;
		}
		int n = (int) Math.min(count, 200L);
		Minecraft.getInstance().execute(() -> {
			for (int i = 0; i < n; i++) {
				dev.stray.client.dungeon.m4.M4.serverTick();
				dev.stray.client.dungeon.RunSplits.serverTick();
			}
		});
	}

	private static void resetWorld() {
		worldHasPings = false;
		usingPings = true;
		pinglessBursts = 0;
		reset();
	}

	private static void reset() {
		lastBurst = 0L;
		anchorGameTime = -1L;
		postedTicks = 0L;
		pendingSkips = 0L;
	}
}
