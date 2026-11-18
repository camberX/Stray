package dev.stray.client.render.overlay.window;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * Wayland: gives the overlay's surface an empty input region, so the pointer goes straight
 * through to the game. Talks to libwayland-client on a private event queue so it never
 * dispatches the game's own Wayland events.
 */
final class WaylandInput {
	private static final int WL_MARSHAL_FLAG_DESTROY = 1;
	private static Pointer compositor;
	private static int compositorVersion;
	private static Function marshal;
	private static Pointer compositorInterface;
	private static String compositorName;
	@SuppressWarnings("unused")
	private static GlobalListener keepGlobal;
	@SuppressWarnings("unused")
	private static RemoveListener keepRemove;

	public interface GlobalListener extends Callback {
		void invoke(Pointer data, Pointer registry, int name, String iface, int version);
	}

	public interface RemoveListener extends Callback {
		void invoke(Pointer data, Pointer registry, int name);
	}

	private WaylandInput() {
	}

	static boolean clickThrough(long displayPtr, long surfacePtr) {
		try {
			NativeLibrary wl = NativeLibrary.getInstance("wayland-client");
			marshal = wl.getFunction("wl_proxy_marshal_flags");
			Function version = wl.getFunction("wl_proxy_get_version");
			Pointer display = new Pointer(displayPtr);
			Pointer surface = new Pointer(surfacePtr);
			Pointer registryInterface = wl.getGlobalVariableAddress("wl_registry_interface");
			compositorInterface = wl.getGlobalVariableAddress("wl_compositor_interface");
			Pointer regionInterface = wl.getGlobalVariableAddress("wl_region_interface");
			Pointer queue = (Pointer) wl.getFunction("wl_display_create_queue").invoke(Pointer.class, new Object[]{display});
			Pointer wrapper = (Pointer) wl.getFunction("wl_proxy_create_wrapper").invoke(Pointer.class, new Object[]{display});
			wl.getFunction("wl_proxy_set_queue").invoke(Void.class, new Object[]{wrapper, queue});
			int displayVersion = (Integer) version.invoke(Integer.class, new Object[]{wrapper});
			// wl_display.get_registry (opcode 1)
			Pointer registry = (Pointer) marshal.invoke(Pointer.class, new Object[]{wrapper, 1, registryInterface, displayVersion, 0, null});
			compositor = null;
			Memory listener = new Memory(Native.POINTER_SIZE * 2L);
			keepGlobal = (data, reg, name, iface, ver) -> {
				if ("wl_compositor".equals(iface) && compositor == null) {
					compositorVersion = Math.min(ver, 4);
					compositorName = iface;
					// wl_registry.bind (opcode 0): name, interface name, version, new id
					compositor = (Pointer) marshal.invoke(Pointer.class, new Object[]{reg, 0, compositorInterface, compositorVersion, 0, name, compositorName, compositorVersion, null});
				}
			};
			keepRemove = (data, reg, name) -> {
			};
			listener.setPointer(0, CallbackReference.getFunctionPointer(keepGlobal));
			listener.setPointer(Native.POINTER_SIZE, CallbackReference.getFunctionPointer(keepRemove));
			wl.getFunction("wl_proxy_add_listener").invoke(Integer.class, new Object[]{registry, listener, null});
			wl.getFunction("wl_display_roundtrip_queue").invoke(Integer.class, new Object[]{display, queue});
			if (compositor == null) {
				return false;
			}
			// wl_compositor.create_region (opcode 1), left empty
			Pointer region = (Pointer) marshal.invoke(Pointer.class, new Object[]{compositor, 1, regionInterface, compositorVersion, 0, null});
			int surfaceVersion = (Integer) version.invoke(Integer.class, new Object[]{surface});
			// wl_surface.set_input_region (opcode 5)
			marshal.invoke(Pointer.class, new Object[]{surface, 5, null, surfaceVersion, 0, region});
			// wl_region.destroy (opcode 0)
			marshal.invoke(Pointer.class, new Object[]{region, 0, null, compositorVersion, WL_MARSHAL_FLAG_DESTROY});
			wl.getFunction("wl_proxy_destroy").invoke(Void.class, new Object[]{registry});
			wl.getFunction("wl_proxy_wrapper_destroy").invoke(Void.class, new Object[]{wrapper});
			wl.getFunction("wl_display_flush").invoke(Integer.class, new Object[]{display});
			listener.close();
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}
