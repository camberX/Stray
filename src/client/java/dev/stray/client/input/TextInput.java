package dev.stray.client.input;

//? if >=26.3 {
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
//?}

/**
 * Stray's own text fields (search, nick, hex colour, cape URL, ...) are drawn by hand, not
 * vanilla EditBoxes. On 26.3 the window is SDL, which only sends typed characters while
 * text input is started; vanilla starts it when an EditBox gains focus, so Stray screens
 * start it themselves for as long as they are open (the same way the sign editor does).
 * 26.2 is GLFW, where characters always arrive.
 */
public final class TextInput {
	private TextInput() {
	}

	public static void init() {
		//? if >=26.3 {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!screen.getClass().getName().startsWith("dev.stray.")) {
				return;
			}
			client.textInputManager().startTextInput(screen);
			ScreenEvents.remove(screen).register(removed -> Minecraft.getInstance().textInputManager().stopTextInput(removed));
		});
		//?}
	}
}
