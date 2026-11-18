package dev.stray.client.render.overlay.window;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import java.util.ArrayList;
import java.util.List;

/**
 * Just enough of libwayland-client (through JNA) for the overlay: proxies, a private event
 * queue, and the wl_interface tables of the two extra protocols it uses (wlr layer-shell and
 * viewporter), which libwayland itself doesn't ship.
 */
final class Wayland {
	static final NativeLibrary WL = NativeLibrary.getInstance("wayland-client");
	private static final Function MARSHAL = WL.getFunction("wl_proxy_marshal_flags");
	private static final Function VERSION = WL.getFunction("wl_proxy_get_version");
	/** Native memory the interface tables point into; kept reachable for the whole session. */
	private static final List<Memory> KEEP = new ArrayList<>();
	static final int MARSHAL_DESTROY = 1;

	static final Pointer REGISTRY = WL.getGlobalVariableAddress("wl_registry_interface");
	static final Pointer COMPOSITOR = WL.getGlobalVariableAddress("wl_compositor_interface");
	static final Pointer SURFACE = WL.getGlobalVariableAddress("wl_surface_interface");
	static final Pointer REGION = WL.getGlobalVariableAddress("wl_region_interface");
	static final Pointer OUTPUT = WL.getGlobalVariableAddress("wl_output_interface");
	static final Pointer LAYER_SURFACE;
	static final Pointer LAYER_SHELL;
	static final Pointer VIEWPORT;
	static final Pointer VIEWPORTER;

	static {
		Pointer none = types(null, null, null, null, null, null, null, null);
		LAYER_SURFACE = iface("zwlr_layer_surface_v1", 4,
			new Object[][]{
				{"set_size", "uu", none}, {"set_anchor", "u", none}, {"set_exclusive_zone", "i", none},
				{"set_margin", "iiii", none}, {"set_keyboard_interactivity", "u", none}, {"get_popup", "o", none},
				{"ack_configure", "u", none}, {"destroy", "", none}, {"set_layer", "2u", none}
			},
			new Object[][]{{"configure", "uuu", none}, {"closed", "", none}});
		LAYER_SHELL = iface("zwlr_layer_shell_v1", 4,
			new Object[][]{
				{"get_layer_surface", "no?ous", types(LAYER_SURFACE, SURFACE, OUTPUT, null, null)},
				{"destroy", "3", none}
			},
			new Object[][]{});
		VIEWPORT = iface("wp_viewport", 1,
			new Object[][]{{"destroy", "", none}, {"set_source", "ffff", none}, {"set_destination", "ii", none}},
			new Object[][]{});
		VIEWPORTER = iface("wp_viewporter", 1,
			new Object[][]{{"destroy", "", none}, {"get_viewport", "no", types(VIEWPORT, SURFACE)}},
			new Object[][]{});
	}

	private Wayland() {
	}

	private static Memory keep(Memory m) {
		KEEP.add(m);
		return m;
	}

	private static Pointer str(String s) {
		byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		Memory m = keep(new Memory(bytes.length + 1L));
		m.write(0, bytes, 0, bytes.length);
		m.setByte(bytes.length, (byte) 0);
		return m;
	}

	private static Pointer types(Pointer... entries) {
		Memory m = keep(new Memory((long) Native.POINTER_SIZE * entries.length));
		for (int i = 0; i < entries.length; i++) {
			m.setPointer((long) Native.POINTER_SIZE * i, entries[i]);
		}
		return m;
	}

	/** struct wl_message { name; signature; types; } */
	private static Pointer messages(Object[][] list) {
		if (list.length == 0) {
			return null;
		}
		int p = Native.POINTER_SIZE;
		Memory m = keep(new Memory((long) p * 3 * list.length));
		for (int i = 0; i < list.length; i++) {
			m.setPointer((long) p * 3 * i, str((String) list[i][0]));
			m.setPointer((long) p * (3 * i + 1), str((String) list[i][1]));
			m.setPointer((long) p * (3 * i + 2), (Pointer) list[i][2]);
		}
		return m;
	}

	/** struct wl_interface { name; int version; int method_count; methods; int event_count; events; } */
	private static Pointer iface(String name, int version, Object[][] methods, Object[][] events) {
		int p = Native.POINTER_SIZE;
		Memory m = keep(new Memory(p * 5L));
		m.clear();
		m.setPointer(0, str(name));
		m.setInt(p, version);
		m.setInt(p + 4L, methods.length);
		m.setPointer(p + 8L, messages(methods));
		m.setInt(p * 2 + 8L, events.length);
		m.setPointer(p * 3 + 8L, messages(events));
		return m;
	}

	static int version(Pointer proxy) {
		return (Integer) VERSION.invoke(Integer.class, new Object[]{proxy});
	}

	/** A request; returns the new object for constructor requests (pass the interface). */
	static Pointer request(Pointer proxy, int opcode, Pointer newInterface, int flags, Object... args) {
		Object[] all = new Object[5 + args.length];
		all[0] = proxy;
		all[1] = opcode;
		all[2] = newInterface;
		all[3] = version(proxy);
		all[4] = flags;
		System.arraycopy(args, 0, all, 5, args.length);
		return (Pointer) MARSHAL.invoke(Pointer.class, all);
	}

	/** wl_registry.bind with an explicit version. */
	static Pointer bind(Pointer registry, int name, Pointer iface, String ifaceName, int version) {
		return (Pointer) MARSHAL.invoke(Pointer.class, new Object[]{registry, 0, iface, version, 0, name, ifaceName, version, null});
	}

	static Object call(String fn, Class<?> ret, Object... args) {
		return WL.getFunction(fn).invoke(ret, args);
	}
}
