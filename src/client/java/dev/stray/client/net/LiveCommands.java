package dev.stray.client.net;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** {@code /st irc} and {@code /st ping} on the Stray live websocket. */
public final class LiveCommands {
	private LiveCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> irc() {
		return ClientCommands.literal("irc")
			.then(ClientCommands.argument("message", StringArgumentType.greedyString())
				.executes(context -> {
					StrayLive.sendIrc(StringArgumentType.getString(context, "message"));
					return Command.SINGLE_SUCCESS;
				}));
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> ping() {
		return ClientCommands.literal("ping")
			.executes(context -> ping(""))
			.then(ClientCommands.argument("label", StringArgumentType.greedyString())
				.executes(context -> ping(StringArgumentType.getString(context, "label"))));
	}

	public static int ping(String label) {
		BlockPos pos = target();
		if (pos == null) {
			StrayLive.tell("Look at a block or stand still to ping.", ChatFormatting.GRAY);
			return 0;
		}
		StrayLive.sendPing(pos.getX(), pos.getY(), pos.getZ(), label);
		return Command.SINGLE_SUCCESS;
	}

	public static BlockPos target() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return null;
		}
		HitResult hit = client.hitResult;
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = block.getBlockPos();
			if (!client.level.getBlockState(pos).isAir()) {
				return pos.immutable();
			}
		}
		if (hit instanceof EntityHitResult entity && entity.getEntity() != null) {
			return entity.getEntity().blockPosition();
		}
		return client.player.blockPosition();
	}
}
