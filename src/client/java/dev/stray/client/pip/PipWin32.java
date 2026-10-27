package dev.stray.client.pip;

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
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lists and captures other Win32 windows off the game thread.
 * GDI objects are reused so 60 FPS capture does not allocate every frame.
 */
final class PipWin32 {
	private static final int MAX_EDGE = 640;
	private static final int PW_RENDERFULLCONTENT = 2;
	private static final int SRCCOPY = 0x00CC0020;
	private static final int COLORONCOLOR = 3;
	private static final int DIB_RGB_COLORS = 0;
	private static final int BI_RGB = 0;
	private static final int GWL_EXSTYLE = -20;
	private static final int WS_EX_TOOLWINDOW = 0x00000080;
	private static final int WS_EX_APPWINDOW = 0x00040000;
	private static final int GA_ROOT = 2;

	private static Session session;

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

		HBITMAP CreateDIBSection(HDC hdc, BITMAPINFO info, int usage, PointerByReference bits, Pointer section, int offset);
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

	static int[] capture(String id, int[] sizeOut, int[] reuse) {
		HWND hwnd = hwnd(id);
		if (hwnd == null || !User32.INSTANCE.IsWindow(hwnd)) {
			close();
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
		Session slot = session;
		if (slot == null || !slot.matches(hwnd, srcW, srcH, dstW, dstH)) {
			close();
			slot = Session.open(hwnd, srcW, srcH, dstW, dstH);
			session = slot;
		}
		if (slot == null) {
			return null;
		}
		if (!ExtraUser.INSTANCE.PrintWindow(hwnd, slot.srcDc, PW_RENDERFULLCONTENT)) {
			GDI32.INSTANCE.BitBlt(slot.srcDc, 0, 0, srcW, srcH, slot.windowDc, 0, 0, SRCCOPY);
		}
		if (!ExtraGdi.INSTANCE.StretchBlt(slot.dstDc, 0, 0, dstW, dstH, slot.srcDc, 0, 0, srcW, srcH, SRCCOPY)) {
			return null;
		}
		int count = dstW * dstH;
		int[] argb = reuse != null && reuse.length == count ? reuse : new int[count];
		slot.bits.read(0, argb, 0, count);
		for (int i = 0; i < count; i++) {
			argb[i] |= 0xFF000000;
		}
		sizeOut[0] = dstW;
		sizeOut[1] = dstH;
		return argb;
	}

	static void close() {
		if (session != null) {
			session.free();
			session = null;
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

	private static final class Session {
		private final HWND hwnd;
		private final int srcW;
		private final int srcH;
		private final int dstW;
		private final int dstH;
		private final HDC windowDc;
		private final HDC srcDc;
		private final HDC dstDc;
		private final HBITMAP srcBmp;
		private final HBITMAP dstBmp;
		private final HANDLE oldSrc;
		private final HANDLE oldDst;
		private final Pointer bits;

		private Session(
			HWND hwnd,
			int srcW,
			int srcH,
			int dstW,
			int dstH,
			HDC windowDc,
			HDC srcDc,
			HDC dstDc,
			HBITMAP srcBmp,
			HBITMAP dstBmp,
			HANDLE oldSrc,
			HANDLE oldDst,
			Pointer bits
		) {
			this.hwnd = hwnd;
			this.srcW = srcW;
			this.srcH = srcH;
			this.dstW = dstW;
			this.dstH = dstH;
			this.windowDc = windowDc;
			this.srcDc = srcDc;
			this.dstDc = dstDc;
			this.srcBmp = srcBmp;
			this.dstBmp = dstBmp;
			this.oldSrc = oldSrc;
			this.oldDst = oldDst;
			this.bits = bits;
		}

		private boolean matches(HWND other, int width, int height, int outW, int outH) {
			return nativeHwnd(hwnd) == nativeHwnd(other)
				&& srcW == width
				&& srcH == height
				&& dstW == outW
				&& dstH == outH;
		}

		private static Session open(HWND hwnd, int srcW, int srcH, int dstW, int dstH) {
			HDC windowDc = User32.INSTANCE.GetDC(hwnd);
			if (windowDc == null) {
				return null;
			}
			HDC srcDc = GDI32.INSTANCE.CreateCompatibleDC(windowDc);
			HDC dstDc = GDI32.INSTANCE.CreateCompatibleDC(windowDc);
			HBITMAP srcBmp = GDI32.INSTANCE.CreateCompatibleBitmap(windowDc, srcW, srcH);
			BITMAPINFO info = new BITMAPINFO();
			info.bmiHeader.biSize = info.bmiHeader.size();
			info.bmiHeader.biWidth = dstW;
			info.bmiHeader.biHeight = -dstH;
			info.bmiHeader.biPlanes = 1;
			info.bmiHeader.biBitCount = 32;
			info.bmiHeader.biCompression = BI_RGB;
			PointerByReference bitsRef = new PointerByReference();
			HBITMAP dstBmp = ExtraGdi.INSTANCE.CreateDIBSection(dstDc, info, DIB_RGB_COLORS, bitsRef, null, 0);
			if (srcDc == null || dstDc == null || srcBmp == null || dstBmp == null || bitsRef.getValue() == null) {
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
				return null;
			}
			HANDLE oldSrc = GDI32.INSTANCE.SelectObject(srcDc, srcBmp);
			HANDLE oldDst = GDI32.INSTANCE.SelectObject(dstDc, dstBmp);
			ExtraGdi.INSTANCE.SetStretchBltMode(dstDc, COLORONCOLOR);
			return new Session(hwnd, srcW, srcH, dstW, dstH, windowDc, srcDc, dstDc, srcBmp, dstBmp, oldSrc, oldDst, bitsRef.getValue());
		}

		private void free() {
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
			if (windowDc != null) {
				User32.INSTANCE.ReleaseDC(hwnd, windowDc);
			}
		}
	}
}
