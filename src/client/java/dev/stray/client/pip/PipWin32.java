package dev.stray.client.pip;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lists and captures other Win32 windows off the game thread.
 * {@code PrintWindow} can grab a window even when Minecraft is covering it.
 */
final class PipWin32 {
	private static final int MAX_EDGE = 480;
	private static final int PW_RENDERFULLCONTENT = 2;
	private static final int SRCCOPY = 0x00CC0020;
	private static final int HALFTONE = 4;
	private static final int DIB_RGB_COLORS = 0;
	private static final int BI_RGB = 0;
	private static final int GWL_EXSTYLE = -20;
	private static final int WS_EX_TOOLWINDOW = 0x00000080;
	private static final int WS_EX_APPWINDOW = 0x00040000;
	private static final int GA_ROOT = 2;

	private interface ExtraGdi extends StdCallLibrary {
		ExtraGdi INSTANCE = Native.load("gdi32", ExtraGdi.class, W32APIOptions.DEFAULT_OPTIONS);

		int SetStretchBltMode(HDC hdc, int mode);

		boolean StretchBlt(
			HDC hdcDest,
			int xDest,
			int yDest,
			int wDest,
			int hDest,
			HDC hdcSrc,
			int xSrc,
			int ySrc,
			int wSrc,
			int hSrc,
			int rop
		);
	}

	private interface ExtraUser extends StdCallLibrary {
		ExtraUser INSTANCE = Native.load("user32", ExtraUser.class, W32APIOptions.DEFAULT_OPTIONS);

		boolean PrintWindow(HWND hwnd, HDC hdc, int flags);

		HWND GetAncestor(HWND hwnd, int flags);
	}

	private PipWin32() {
	}

	static boolean available() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	static List<PipCapture.WindowInfo> list() {
		List<PipCapture.WindowInfo> out = new ArrayList<>();
		long self = selfHwnd();
		User32.INSTANCE.EnumWindows((hwnd, data) -> {
			PipCapture.WindowInfo info = describe(hwnd, self);
			if (info != null) {
				out.add(info);
			}
			return true;
		}, null);
		out.sort((a, b) -> a.title().compareToIgnoreCase(b.title()));
		return out;
	}

	static int[] capture(String id, int[] sizeOut) {
		HWND hwnd = hwnd(id);
		if (hwnd == null || !User32.INSTANCE.IsWindow(hwnd)) {
			return null;
		}
		RECT client = new RECT();
		if (!User32.INSTANCE.GetClientRect(hwnd, client)) {
			return null;
		}
		int srcW = Math.max(0, client.right - client.left);
		int srcH = Math.max(0, client.bottom - client.top);
		if (srcW < 8 || srcH < 8) {
			return null;
		}
		float scale = Math.min(1f, MAX_EDGE / (float) Math.max(srcW, srcH));
		int dstW = Math.max(1, Math.round(srcW * scale));
		int dstH = Math.max(1, Math.round(srcH * scale));
		HDC windowDc = User32.INSTANCE.GetDC(hwnd);
		if (windowDc == null) {
			return null;
		}
		HDC srcDc = GDI32.INSTANCE.CreateCompatibleDC(windowDc);
		HDC dstDc = GDI32.INSTANCE.CreateCompatibleDC(windowDc);
		HBITMAP srcBmp = GDI32.INSTANCE.CreateCompatibleBitmap(windowDc, srcW, srcH);
		HBITMAP dstBmp = GDI32.INSTANCE.CreateCompatibleBitmap(windowDc, dstW, dstH);
		HANDLE oldSrc = srcDc == null ? null : GDI32.INSTANCE.SelectObject(srcDc, srcBmp);
		HANDLE oldDst = dstDc == null ? null : GDI32.INSTANCE.SelectObject(dstDc, dstBmp);
		Memory pixels = null;
		try {
			if (srcDc == null || dstDc == null || srcBmp == null || dstBmp == null) {
				return null;
			}
			if (!ExtraUser.INSTANCE.PrintWindow(hwnd, srcDc, PW_RENDERFULLCONTENT)) {
				GDI32.INSTANCE.BitBlt(srcDc, 0, 0, srcW, srcH, windowDc, 0, 0, SRCCOPY);
			}
			ExtraGdi.INSTANCE.SetStretchBltMode(dstDc, HALFTONE);
			if (!ExtraGdi.INSTANCE.StretchBlt(dstDc, 0, 0, dstW, dstH, srcDc, 0, 0, srcW, srcH, SRCCOPY)) {
				return null;
			}
			BITMAPINFO info = new BITMAPINFO();
			info.bmiHeader.biSize = info.bmiHeader.size();
			info.bmiHeader.biWidth = dstW;
			info.bmiHeader.biHeight = -dstH;
			info.bmiHeader.biPlanes = 1;
			info.bmiHeader.biBitCount = 32;
			info.bmiHeader.biCompression = BI_RGB;
			pixels = new Memory((long) dstW * dstH * 4L);
			int rows = GDI32.INSTANCE.GetDIBits(dstDc, dstBmp, 0, dstH, pixels, info, DIB_RGB_COLORS);
			if (rows == 0) {
				return null;
			}
			int[] argb = new int[dstW * dstH];
			for (int i = 0; i < argb.length; i++) {
				int bgra = pixels.getInt((long) i * 4L);
				int b = bgra & 0xFF;
				int g = bgra >>> 8 & 0xFF;
				int r = bgra >>> 16 & 0xFF;
				argb[i] = 0xFF000000 | r << 16 | g << 8 | b;
			}
			sizeOut[0] = dstW;
			sizeOut[1] = dstH;
			return argb;
		} finally {
			if (srcDc != null && oldSrc != null) {
				GDI32.INSTANCE.SelectObject(srcDc, oldSrc);
			}
			if (dstDc != null && oldDst != null) {
				GDI32.INSTANCE.SelectObject(dstDc, oldDst);
			}
			if (srcBmp != null) {
				GDI32.INSTANCE.DeleteObject(srcBmp);
			}
			if (dstBmp != null) {
				GDI32.INSTANCE.DeleteObject(dstBmp);
			}
			if (srcDc != null) {
				GDI32.INSTANCE.DeleteDC(srcDc);
			}
			if (dstDc != null) {
				GDI32.INSTANCE.DeleteDC(dstDc);
			}
			User32.INSTANCE.ReleaseDC(hwnd, windowDc);
		}
	}

	private static PipCapture.WindowInfo describe(HWND hwnd, long self) {
		if (hwnd == null || !User32.INSTANCE.IsWindowVisible(hwnd)) {
			return null;
		}
		long value = nativeHwnd(hwnd);
		if (value == 0L || value == self) {
			return null;
		}
		HWND root = ExtraUser.INSTANCE.GetAncestor(hwnd, GA_ROOT);
		if (root != null && nativeHwnd(root) != value) {
			return null;
		}
		int ex = User32.INSTANCE.GetWindowLong(hwnd, GWL_EXSTYLE);
		boolean tool = (ex & WS_EX_TOOLWINDOW) != 0;
		boolean app = (ex & WS_EX_APPWINDOW) != 0;
		if (tool && !app) {
			return null;
		}
		char[] buf = new char[512];
		int len = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
		if (len <= 0) {
			return null;
		}
		String title = Native.toString(buf).trim();
		if (title.isEmpty() || skipTitle(title)) {
			return null;
		}
		RECT box = new RECT();
		if (!User32.INSTANCE.GetClientRect(hwnd, box)) {
			return null;
		}
		int w = box.right - box.left;
		int h = box.bottom - box.top;
		if (w < 32 || h < 32) {
			return null;
		}
		return new PipCapture.WindowInfo(Long.toUnsignedString(value), title, w, h);
	}

	private static boolean skipTitle(String title) {
		String lower = title.toLowerCase(Locale.ROOT);
		return lower.equals("program manager")
			|| lower.equals("windows input experience")
			|| lower.startsWith("msctfime")
			|| lower.contains("nvidia geforce overlay")
			|| lower.contains("game bar");
	}

	private static HWND hwnd(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		try {
			long value = Long.parseUnsignedLong(id);
			return new HWND(Pointer.createConstant(value));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static long nativeHwnd(HWND hwnd) {
		if (hwnd == null) {
			return 0L;
		}
		Pointer pointer = hwnd.getPointer();
		return pointer == null ? 0L : Pointer.nativeValue(pointer);
	}

	private static long selfHwnd() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.getWindow() == null) {
				return 0L;
			}
			return GLFWNativeWin32.glfwGetWin32Window(client.getWindow().handle());
		} catch (Throwable ignored) {
			return 0L;
		}
	}
}
