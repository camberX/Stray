package dev.stray.client.mixin;

import dev.stray.client.render.VanillaHud;
import dev.stray.client.skill.SkillProgressTracker;
import dev.stray.client.ui.ChatPeek;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {
	@org.spongepowered.asm.mixin.Shadow
	@org.spongepowered.asm.mixin.Final
	private static net.minecraft.resources.Identifier HOTBAR_SELECTION_SPRITE;

	/** Smooth hotbar: the selection box is drawn shifted towards the slot it is gliding from. */
	@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
		method = "extractItemHotbar",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
		)
	)
	private void stray$smoothHotbar(
		GuiGraphicsExtractor graphics,
		com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
		net.minecraft.resources.Identifier sprite,
		int x,
		int y,
		int width,
		int height,
		com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original
	) {
		net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
		if (sprite != HOTBAR_SELECTION_SPRITE || client.player == null) {
			original.call(graphics, pipeline, sprite, x, y, width, height);
			return;
		}
		float offset = dev.stray.client.render.SmoothHotbar.offset(client.player.getInventory().getSelectedSlot());
		if (offset == 0f) {
			original.call(graphics, pipeline, sprite, x, y, width, height);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(offset, 0f);
		try {
			original.call(graphics, pipeline, sprite, x, y, width, height);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	/** Themed hotbar: Stray draws the hotbar in the theme's look instead. */
	@Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
	private void stray$themedHotbar(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (dev.stray.client.render.ThemedHotbar.active()) {
			dev.stray.client.render.ThemedHotbar.draw(graphics, delta, net.minecraft.client.Minecraft.getInstance().player);
			ci.cancel();
		}
	}

	/** Themed hotbar: its own XP bar replaces vanilla's (the locator and jump bars stay). */
	@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
		method = "extractHotbarAndDecorations",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")
	)
	private void stray$themedXpBack(net.minecraft.client.gui.contextualbar.ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta,
		com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
		if (bar instanceof net.minecraft.client.gui.contextualbar.ExperienceBar && dev.stray.client.render.ThemedHotbar.active()) {
			return;
		}
		original.call(bar, graphics, delta);
	}

	@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
		method = "extractHotbarAndDecorations",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")
	)
	private void stray$themedXp(net.minecraft.client.gui.contextualbar.ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta,
		com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
		if (bar instanceof net.minecraft.client.gui.contextualbar.ExperienceBar && dev.stray.client.render.ThemedHotbar.active()) {
			return;
		}
		original.call(bar, graphics, delta);
	}

	@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
		method = "extractHotbarAndDecorations",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V")
	)
	private void stray$themedLevel(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int level,
		com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
		if (dev.stray.client.render.ThemedHotbar.active()) {
			return;
		}
		original.call(graphics, font, level);
	}

	@Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
	private void stray$slayerTitle(Component title, CallbackInfo ci) {
		if (!dev.stray.client.slayer.SlayerBosses.onTitle(title) || !dev.stray.client.dungeon.F7Titles.onTitle(title)) {
			ci.cancel();
		}
	}

	@Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
	private void stray$f7Subtitle(Component subtitle, CallbackInfo ci) {
		if (!dev.stray.client.dungeon.F7Titles.onSubtitle(subtitle)) {
			ci.cancel();
		}
	}

	@Inject(method = "setOverlayMessage", at = @At("TAIL"))
	private void stray$skillOverlay(Component message, boolean animateColor, CallbackInfo ci) {
		SkillProgressTracker.onActionBar(message);
	}

	@ModifyArg(
		method = "extractChat",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"
		),
		index = 5
	)
	private ChatComponent.DisplayMode stray$peekChat(ChatComponent.DisplayMode mode) {
		return ChatPeek.holding() ? ChatComponent.DisplayMode.FOREGROUND : mode;
	}

	@Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void stray$skipScoreboard(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (VanillaHud.customScoreboard()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
	private void stray$skipBoss(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (VanillaHud.customBossBar()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void stray$skipEffects(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (VanillaHud.customEffects()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
	private void stray$skipHeldItem(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (VanillaHud.customHeldItem()) {
			ci.cancel();
		}
	}
}
