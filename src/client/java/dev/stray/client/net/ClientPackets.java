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
		// Server ticks for M4 and run splits come from ServerTicks (top-level packets only).
		if (packet instanceof net.minecraft.network.protocol.game.ClientboundEntityEventPacket event) {
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
					titaniumBroken(client, update.getPos(), client.level.getBlockState(update.getPos()), update.getBlockState());
				}
			});
		} else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket section) {
			client.execute(() -> {
				if (client.level != null) {
					section.runUpdates((pos, state) -> {
						net.minecraft.core.BlockPos at = pos.immutable();
						dev.stray.client.dungeon.m4.M4.onBlockUpdate(at, client.level.getBlockState(at), state);
						titaniumBroken(client, at, client.level.getBlockState(at), state);
					});
				}
			});
		}
	}

	/**
	 * Mining HUD: the titanium ore (polished diorite) you are mining turned into something else,
	 * before vanilla applies the change. You were looking at it with attack held.
	 */
	private static void titaniumBroken(Minecraft client, net.minecraft.core.BlockPos pos,
		net.minecraft.world.level.block.state.BlockState before, net.minecraft.world.level.block.state.BlockState after) {
		if (!before.is(net.minecraft.world.level.block.Blocks.POLISHED_DIORITE) || after.is(net.minecraft.world.level.block.Blocks.POLISHED_DIORITE)) {
			return;
		}
		if (!client.options.keyAttack.isDown() || !(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
			|| !hit.getBlockPos().equals(pos) || !dev.stray.client.mining.MiningTracker.inMiningIsland()) {
			return;
		}
		dev.stray.client.mining.MiningTracker.onTitaniumBroken(pos);
	}

	/** Every packet as it arrives (from {@code Connection.channelRead0}). */
	public static void onReceived(Packet<?> packet) {
		// Server ticks see the packet as it arrived, like NoammAddons: a ping inside a bundle is
		// not a tick of its own.
		ServerTicks.onPacket(packet);
		dispatch(packet);
	}

	private static void dispatch(Packet<?> packet) {
		if (packet instanceof BundlePacket<?> bundle) {
			for (Packet<?> inner : bundle.subPackets()) {
				dispatch(inner);
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
		if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket line && !line.overlay()) {
			// Run splits: read as it arrives, before any mod can hide the line.
			String text = dev.stray.client.util.Text.strip(line.content().getString());
			Minecraft.getInstance().execute(() -> {
				dev.stray.client.dungeon.RunSplits.onChat(text);
				dev.stray.client.dungeon.P3StartTimer.onChat(text);
			});
		} else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound) {
			var event = sound.getSound().value();
			float volume = sound.getVolume();
			float pitch = sound.getPitch();
			Minecraft.getInstance().execute(() -> {
				dev.stray.client.dungeon.RunSplits.onSound(event, volume, pitch);
				dev.stray.client.dungeon.P3StartTimer.onSound(event, volume, pitch);
			});
		}
		if (!dev.stray.Edition.LEGIT && packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat) {
			// Auto hotbar, as NoammAddons did it: every system chat packet (action bar too), read here
			// before any mod can hide it, with § and & codes removed.
			String text = dev.stray.client.item.AutoHotbar.removeFormatting(chat.content().getString());
			Minecraft.getInstance().execute(() -> dev.stray.client.item.AutoHotbar.onChat(text));
		}
		ChestEsp.onPacket(packet);
		EspNamePackets.onPacket(packet);
		if (!dev.stray.Edition.LEGIT) { AutoExperiments.onPacket(packet); }
		if (!dev.stray.Edition.LEGIT) { AutoDna.onPacket(packet); }
		DisabledPotions.onPacket(packet);
		GardenHud.onPacket(packet);
	}
}
