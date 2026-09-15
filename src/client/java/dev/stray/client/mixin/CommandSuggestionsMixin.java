package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.stray.client.ui.ChatChrome;
import dev.stray.client.ui.CommandShortcuts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {
	@Shadow
	@Final
	private EditBox input;

	@Shadow
	@Final
	private List<FormattedCharSequence> commandUsage;

	@Shadow
	private void recomputeUsageBoxWidth() {
	}

	@Inject(method = "updateUsageInfo", at = @At("RETURN"))
	private void stray$shortcutUsage(ParseResults<?> parse, Suggestions suggestions, CallbackInfo ci) {
		String hint = CommandShortcuts.usageHint(input.getValue());
		if (hint == null) {
			return;
		}
		commandUsage.clear();
		commandUsage.add(Component.literal(hint).withStyle(CommandSuggestions.USAGE_FORMAT).getVisualOrderText());
		recomputeUsageBoxWidth();
	}

	@WrapOperation(
		method = "updateCommandInfo",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/brigadier/CommandDispatcher;getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;"
		)
	)
	private CompletableFuture<Suggestions> stray$shortcutSuggestions(
		CommandDispatcher<?> dispatcher,
		ParseResults<?> parse,
		int cursor,
		Operation<CompletableFuture<Suggestions>> original
	) {
		CompletableFuture<Suggestions> future = original.call(dispatcher, parse, cursor);
		if (!CommandShortcuts.suggests()) {
			return future;
		}
		return future.thenApply(suggestions -> CommandShortcuts.mergeSuggestions(input.getValue(), cursor, suggestions));
	}

	@WrapOperation(
		method = "extractUsage",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"
		)
	)
	private void stray$liftUsageFill(
		GuiGraphicsExtractor graphics,
		int x0,
		int y0,
		int x1,
		int y1,
		int color,
		Operation<Void> original
	) {
		int lift = ChatChrome.usageLift();
		original.call(graphics, x0, y0 - lift, x1, y1 - lift, color);
	}

	@WrapOperation(
		method = "extractUsage",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"
		)
	)
	private void stray$liftUsageText(
		GuiGraphicsExtractor graphics,
		Font font,
		FormattedCharSequence text,
		int x,
		int y,
		int color,
		Operation<Void> original
	) {
		original.call(graphics, font, text, x, y - ChatChrome.usageLift(), color);
	}
}
