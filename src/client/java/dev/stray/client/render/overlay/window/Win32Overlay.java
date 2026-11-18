package dev.stray.client.render.overlay.window;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Windows: click-through (layered + transparent), owned by the game window so it stays right
 * above it, and excluded from capture (OBS, Discord, Game Bar, display capture too).
 */
final class Win32Overlay {
	private static final int GWL_EXSTYLE = -20;
	private static final int GWLP_HWNDPARENT = -8;
	private static final int WS_EX_LAYERED = 0x00080000;
	private static final int WS_EX_TRANSPARENT = 0x00000020;
	private static final int WS_EX_TOOLWINDOW = 0x00000080;
	private static final int WS_EX_NOACTIVATE = 0x08000000;
	private static final int WS_EX_APPWINDOW = 0x00040000;
	private static final int LWA_ALPHA = 0x2;
	private static final int WDA_EXCLUDEFROMCAPTURE = 0x11;

	private interface User extends StdCallLibrary {
		User INSTANCE = Native.load("user32", User.class, W32APIOptions.DEFAULT_OPTIONS);

		BaseTSD.LONG_PTR GetWindowLongPtr(HWND hwnd, int index);

		BaseTSD.LONG_PTR SetWindowLongPtr(HWND hwnd, int index, BaseTSD.LONG_PTR value);

		boolean SetLayeredWindowAttributes(HWND hwnd, int key, byte alpha, int flags);

		boolean SetWindowDisplayAffinity(HWND hwnd, int affinity);
	}

	private Win32Overlay() {
	}

	static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
	}

	/** Returns whether capture exclusion took (Windows 10 2004 and newer). */
	static boolean setup(long overlay, long game) {
		HWND hwnd = new HWND(new Pointer(overlay));
		long style = User.INSTANCE.GetWindowLongPtr(hwnd, GWL_EXSTYLE).longValue();
		style |= WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE;
		style &= ~WS_EX_APPWINDOW;
		User.INSTANCE.SetWindowLongPtr(hwnd, GWL_EXSTYLE, new BaseTSD.LONG_PTR(style));
		User.INSTANCE.SetLayeredWindowAttributes(hwnd, 0, (byte) 255, LWA_ALPHA);
		if (game != 0L) {
			User.INSTANCE.SetWindowLongPtr(hwnd, GWLP_HWNDPARENT, new BaseTSD.LONG_PTR(game));
		}
		return User.INSTANCE.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE);
	}
}
