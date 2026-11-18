package dev.stray.client.dungeon.autoleap;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code /stray debug dev swing}: plays Auto Leap's client side swing and, for the next second,
 * watches what could reach the server. Two things are counted: calls to the game's own
 * {@code swing(...)} for you (the method ViaFabricPlus turns into a swing packet on older
 * servers) and every packet this client sends, apart from movement and keep-alives. Only looks.
 * Full edition only.
 */
public final class SwingCheck {
	private static final int TICKS = 20;
	private static final Set<String> NOISE = Set.of(
		"ServerboundMovePlayerPacket", "Pos", "PosRot", "Rot", "StatusOnly",
		"ServerboundClientTickEndPacket", "ServerboundKeepAlivePacket", "ServerboundPongPacket",
		"ServerboundChunkBatchReceivedPacket", "ServerboundPlayerInputPacket", "ServerboundPingRequestPacket");

	private static volatile int left;
	private static volatile int swingCalls;
	private static final List<String> SENT = new ArrayList<>();

	private SwingCheck() {
	}

	public static void start() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		synchronized (SENT) {
			SENT.clear();
		}
		swingCalls = 0;
		left = TICKS;
		LeapRunner.swingOnScreen(client.player);
		say("§7Swing played. Watching for 1s…");
	}

	/** From the LivingEntity.swing hook: the game's swing ran for you. */
	public static void onSwingCall() {
		if (left > 0) {
			swingCalls++;
		}
	}

	/** From Connection.send. */
	public static void onSend(Packet<?> packet) {
		if (left <= 0 || packet == null) {
			return;
		}
		Class<?> type = packet.getClass();
		String name = type.getSimpleName();
		boolean serverbound = name.startsWith("Serverbound")
			|| type.getEnclosingClass() != null && type.getEnclosingClass().getSimpleName().startsWith("Serverbound");
		if (!serverbound || NOISE.contains(name)) {
			return;
		}
		synchronized (SENT) {
			SENT.add(name);
		}
	}

	/** Every tick: the report once the second is over. */
	public static void tick() {
		if (left <= 0 || --left > 0) {
			return;
		}
		List<String> sent;
		synchronized (SENT) {
			sent = new ArrayList<>(SENT);
		}
		say((swingCalls == 0 ? "§a" : "§c") + "swing() calls: " + swingCalls
			+ (swingCalls == 0 ? " §7(nothing for ViaFabricPlus to turn into a packet)" : ""));
		say(sent.isEmpty() ? "§aPackets sent: none §7(movement and keep-alives not counted)" : "§cPackets sent: §f" + String.join(", ", sent));
	}

	private static void say(String text) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("§bStray §7» " + text));
			}
		});
	}
}
