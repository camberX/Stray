package dev.stray.client.net;

import dev.stray.client.combat.AutoExperiments;
import dev.stray.client.farming.AutoDna;
import dev.stray.client.menu.DisabledPotions;
import dev.stray.client.mining.ChestEsp;
import dev.stray.client.node.EnderNodeTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;

public final class ClientPackets {
	private ClientPackets() {
	}

	public static void onReceived(Packet<?> packet) {
		if (packet instanceof BundlePacket<?> bundle) {
			for (Packet<?> inner : bundle.subPackets()) {
				onReceived(inner);
			}
			return;
		}
		if (packet instanceof ClientboundPongResponsePacket pong) {
			ConnectionPing.onPong(pong.time());
			return;
		}
		if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
			ConnectionPing.onKeepAlive(keepAlive.getId());
			return;
		}
		if (packet instanceof ClientboundLevelParticlesPacket particles) {
			ParticleOptions options = particles.getParticle();
			double x = particles.getX();
			double y = particles.getY();
			double z = particles.getZ();
			Minecraft.getInstance().execute(() ->
				EnderNodeTracker.get().onParticle(x, y, z, options.getType())
			);
		}
		ChestEsp.onPacket(packet);
		EspNamePackets.onPacket(packet);
		AutoExperiments.onPacket(packet);
		AutoDna.onPacket(packet);
		DisabledPotions.onPacket(packet);
	}
}
