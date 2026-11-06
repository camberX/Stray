package dev.stray.client.dungeon.terminal;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The offline terminal from /stray debug dev terminal: same window and clicks, no server. */
final class TerminalSimScreen extends Screen {
	private final TerminalSim sim;
	private boolean started;

	TerminalSimScreen(TerminalSim sim) {
		super(Component.literal("Terminal"));
		this.sim = sim;
	}

	@Override
	protected void init() {
		super.init();
		// init also runs on resize; start the board only once.
		if (!started) {
			started = true;
			TerminalSession.startSim(sim);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		TerminalSession.render(graphics, width, height, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		return TerminalSession.mouseClicked(event.x(), event.y());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (minecraft != null && minecraft.options.keyInventory.matches(event)) {
			onClose();
			return true;
		}
		if (TerminalSession.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void tick() {
		TerminalSession.simTick();
	}

	@Override
	public void removed() {
		TerminalSession.stopSim();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
