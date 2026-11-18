package dev.stray.client.render.overlay.window;

/** The transparent, click-through window kept over the game window. */
interface OverlayWindow {
	/** Null when it worked, else why not. */
	String create();

	/** Follow the game window; returns whether the overlay should show this frame. */
	boolean sync(boolean wanted);

	int pixelWidth();

	int pixelHeight();

	/** Makes the overlay's GL context current. */
	void begin();

	/** Shows the frame and gives the game its context back. */
	void end();

	void destroy();

	/** Short description for the debug command. */
	String describe();

	static OverlayWindow make() {
		//? if >=26.3 {
		return new SdlOverlayWindow();
		//?} else {
		/*return new GlfwOverlayWindow();*///?}
	}
}
