package dev.stray.client.mixin;

import dev.stray.client.ui.MenuChrome;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StringWidget.class)
public abstract class StringWidgetMixin {
	/**
	 * Titles in Inter. The layout placed the widget for the narrower-or-wider vanilla text, so the
	 * Inter text is drawn centred in that same space instead of from its left edge, which pushed
	 * centred titles off to the left.
	 */
	@Inject(method = "visitLines", at = @At("HEAD"), cancellable = true)
	private void stray$titleFont(ActiveTextCollector collector, CallbackInfo ci) {
		if (!MenuChrome.enabled()) {
			return;
		}
		StringWidget self = (StringWidget) (Object) this;
		Component message = MenuChrome.titleLabel(self.getMessage());
		self.setMessage(message);
		Font font = net.minecraft.client.Minecraft.getInstance().font;
		int vanilla = font.width(message.copy().withStyle(style -> style.withFont(FontDescription.DEFAULT)));
		int inter = font.width(message);
		// Only widgets sized to their vanilla text (titles); fixed-width labels stay left-aligned.
		if (inter > vanilla || Math.abs(self.getWidth() - vanilla) > 1 && Math.abs(self.getWidth() - inter) > 1) {
			return;
		}
		int x = self.getX() + (vanilla - inter) / 2;
		int y = self.getY() + (self.getHeight() - 9) / 2;
		collector.accept(x, y, message.getVisualOrderText());
		ci.cancel();
	}
}
