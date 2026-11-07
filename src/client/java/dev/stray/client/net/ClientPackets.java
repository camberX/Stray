package dev.stray.client.net;

import dev.stray.client.combat.AutoExperiments;
import dev.stray.client.farming.AutoDna;
import dev.stray.client.farming.GardenHud;
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

	/**
	 * M4 (BirdAddon). Queued onto the render thread here, which runs before vanilla's own
	 * handling of the same packet, so a block update still sees the old block.
	 */
	private static void m4(Packet<?> packet) {
		Minecraft client = Minecraft.getInstance();
		if (packet instanceof net.minecraft.network.protocol.common.ClientboundPingPacket ping) {
			// Hypixel pings every server tick; Odin (BirdAddon's base) counts ticks the same way.
			if (ping.getId() != 0) {
				client.execute(dev.stray.client.dungeon.m4.M4::serverTick);
			}
		} else if (packet instanceof net.minecraft.network.protocol.game.ClientboundEntityEventPacket event) {
			if (event.getEventId() == 3) {
				client.execute(() -> {
					if (client.level != null) {
						dev.stray.client.dungeon.m4.M4.onEntityDeath(event.getEntity(client.level));
					}
				});
			}
		} else if (packet instanceof net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket hurt) {
			client.execute(() -> {
				if (client.level != null) {
					dev.stray.client.dungeon.m4.M4.onHurt(client.level.getEntity(hurt.id()));
				}
			});
		} else if (packet instanceof net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket update) {
			client.execute(() -> {
				if (client.level != null) {
					dev.stray.client.dungeon.m4.M4.onBlockUpdate(update.getPos(), client.level.getBlockState(update.getPos()), update.getBlockState());
				}
			});
		} else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket section) {
			client.execute(() -> {
				if (client.level != null) {
					section.runUpdates((pos, state) -> {
						net.minecraft.core.BlockPos at = pos.immutable();
						dev.stray.client.dungeon.m4.M4.onBlockUpdate(at, client.level.getBlockState(at), state);
					});
				}
			});
		}
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
			//? if >=26.3 {
			ParticleOptions options = particles.particle();
			double x = particles.x();
			double y = particles.y();
			double z = particles.z();
			//?} else {
			/*ParticleOptions options = particles.getParticle();
			double x = particles.getX();
			double y = particles.getY();
			double z = particles.getZ();*///?}
			var type = options.getType();
			Minecraft.getInstance().execute(() -> {
				EnderNodeTracker.get().onParticle(x, y, z, type);
				dev.stray.client.dungeon.m4.M4.onParticle(options, x, y, z);
			});
		}
		m4(packet);
		ChestEsp.onPacket(packet);
		EspNamePackets.onPacket(packet);
		if (!dev.stray.Edition.LEGIT) { AutoExperiments.onPacket(packet); }
		if (!dev.stray.Edition.LEGIT) { AutoDna.onPacket(packet); }
		DisabledPotions.onPacket(packet);
		GardenHud.onPacket(packet);
	}
}
