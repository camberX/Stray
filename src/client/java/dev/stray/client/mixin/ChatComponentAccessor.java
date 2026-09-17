package dev.stray.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> stray$allMessages();

	@Accessor("trimmedMessages")
	List<GuiMessage.Line> stray$trimmedMessages();

	@Accessor("chatScrollbarPos")
	int stray$scroll();

	@Invoker("refreshTrimmedMessages")
	void stray$refreshTrimmedMessages();

	@Invoker("getWidth")
	int stray$chatWidth();

	@Invoker("getHeight")
	int stray$chatHeight();

	@Invoker("getScale")
	double stray$chatScale();

	@Invoker("getLineHeight")
	int stray$lineHeight();
}
