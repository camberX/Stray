package dev.stray.client.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

import java.util.Set;

/**
 * Prints the packets this client sends to chat, for checking what a feature sends.
 * Read-only: it only looks at packets that are already being sent. Movement,
 * keep-alive and tick packets are skipped so the chat stays readable.
 */
public final class PacketDebug {
	private static final Set<String> NOISE = Set.of(
		"ServerboundMovePlayerPacket", "Pos", "PosRot", "Rot", "StatusOnly",
		"ServerboundClientTickEndPacket", "ServerboundKeepAlivePacket", "ServerboundPongPacket",
		"ServerboundChunkBatchReceivedPacket", "ServerboundPlayerInputPacket", "ServerboundPingRequestPacket"
	);
	private static volatile boolean enabled;

	private PacketDebug() {
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void set(boolean on) {
		enabled = on;
	}

	public static void onSend(Packet<?> packet) {
		if (!enabled || packet == null) {
			return;
		}
		String name = packet.getClass().getSimpleName();
		// Only client -> server packets (in singleplayer the built-in server shares this class).
		if (!name.startsWith("Serverbound") && !(packet.getClass().getEnclosingClass() != null
			&& packet.getClass().getEnclosingClass().getSimpleName().startsWith("Serverbound"))) {
			return;
		}
		if (NOISE.contains(name)) {
			return;
		}
		String body = packet.getClass().isRecord() ? packet.toString() : name;
		if (body.length() > 400) {
			body = body.substring(0, 400) + "…";
		}
		note("§8[sent] §7" + body);
	}

	/** A local-only line in chat, e.g. to mark which click a packet came from. */
	public static void note(String text) {
		if (!enabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(text));
			}
		});
	}
}
