package dev.stray.client.media;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Reads what Spotify is playing from the Windows media session (SMTC), straight from
 * Java through JNA (which Minecraft already ships): the WinRT
 * GlobalSystemMediaTransportControlsSessionManager, called through its COM vtables.
 * No PowerShell or other helper process.
 *
 * <p>Every half second it reports one line in the format the old script used, so
 * {@link SpotifySmtc} reads it unchanged: {@code FK\tstatus\ttitle\tartist\talbum\tpos\tdur\talbumArtist\tcover}
 * or just {@code FK} when Spotify has no session. The cover comes from the online
 * lookup in SpotifySmtc, not from the session.
 */
final class WindowsMediaSession {
	private static final String MANAGER_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
	/** IGlobalSystemMediaTransportControlsSessionManagerStatics. */
	private static final String IID_MANAGER_STATICS = "{2050c4ee-11a0-57de-aed7-c97c70338245}";
	/** IAsyncInfo. */
	private static final String IID_ASYNC_INFO = "{00000036-0000-0000-C000-000000000046}";

	// Vtable slots; 0-5 are IUnknown + IInspectable on every WinRT interface.
	private static final int RELEASE = 2;
	private static final int QUERY_INTERFACE = 0;
	private static final int STATICS_REQUEST_ASYNC = 6;
	private static final int ASYNC_OP_GET_RESULTS = 8;
	private static final int ASYNC_INFO_STATUS = 7;
	private static final int MANAGER_GET_CURRENT_SESSION = 6;
	private static final int MANAGER_GET_SESSIONS = 7;
	private static final int VECTOR_GET_AT = 6;
	private static final int VECTOR_SIZE = 7;
	private static final int SESSION_APP_ID = 6;
	private static final int SESSION_MEDIA_PROPERTIES_ASYNC = 7;
	private static final int SESSION_TIMELINE = 8;
	private static final int SESSION_PLAYBACK_INFO = 9;
	private static final int PROPS_TITLE = 6;
	private static final int PROPS_ALBUM_ARTIST = 8;
	private static final int PROPS_ARTIST = 9;
	private static final int PROPS_ALBUM_TITLE = 10;
	private static final int TIMELINE_END = 7;
	private static final int TIMELINE_POSITION = 10;
	private static final int PLAYBACK_STATUS = 7;

	private static final int PLAYING = 4;
	private static final String[] STATUS = {"Closed", "Opened", "Changing", "Stopped", "Playing", "Paused"};

	private final NativeLibrary combase = NativeLibrary.getInstance("combase");
	private final Consumer<String> out;
	private final BooleanSupplier running;

	private WindowsMediaSession(Consumer<String> out, BooleanSupplier running) {
		this.out = out;
		this.running = running;
	}

	/** Runs until {@code running} turns false; throws if the media session can't be reached. */
	static void run(Consumer<String> out, BooleanSupplier running) throws Exception {
		new WindowsMediaSession(out, running).loop();
	}

	private void loop() throws Exception {
		// MTA; S_FALSE / RPC_E_CHANGED_MODE just mean this thread was set up already.
		this.combase.getFunction("RoInitialize").invokeInt(new Object[] {1});
		Pointer manager = this.requestManager();
		int misses = 0;
		try {
			while (this.running.getAsBoolean()) {
				Pointer session = this.findSpotify(manager);
				if (session == null) {
					if (++misses >= 5) {
						this.out.accept("FK");
						misses = 0;
					}
				} else {
					misses = 0;
					try {
						String line = this.describe(session);
						if (line != null) {
							this.out.accept(line);
						}
					} catch (Exception skipped) {
						// One failed read (Spotify busy, track changing): skip this sample, keep going.
					} finally {
						release(session);
					}
				}
				Thread.sleep(500L);
			}
		} finally {
			release(manager);
		}
	}

	// ------------------------------------------------------------------ reading

	private Pointer requestManager() throws Exception {
		Pointer className = this.hstring(MANAGER_CLASS);
		PointerByReference statics = new PointerByReference();
		try {
			check(this.combase.getFunction("RoGetActivationFactory").invokeInt(new Object[] {className, guid(IID_MANAGER_STATICS), statics}), "RoGetActivationFactory");
		} finally {
			this.deleteHstring(className);
		}
		try {
			PointerByReference op = new PointerByReference();
			check(call(statics.getValue(), STATICS_REQUEST_ASYNC, op), "RequestAsync");
			return this.await(op.getValue());
		} finally {
			release(statics.getValue());
		}
	}

	/** Spotify's session: the playing one if there are several, else the current one if it is Spotify, else any. */
	private Pointer findSpotify(Pointer manager) throws Exception {
		List<Pointer> spotify = new ArrayList<>();
		PointerByReference sessions = new PointerByReference();
		if (call(manager, MANAGER_GET_SESSIONS, sessions) >= 0 && sessions.getValue() != null) {
			try {
				IntByReference size = new IntByReference();
				call(sessions.getValue(), VECTOR_SIZE, size);
				for (int i = 0; i < size.getValue(); i++) {
					PointerByReference session = new PointerByReference();
					if (call(sessions.getValue(), VECTOR_GET_AT, i, session) >= 0 && session.getValue() != null) {
						if (this.isSpotify(session.getValue())) {
							spotify.add(session.getValue());
						} else {
							release(session.getValue());
						}
					}
				}
			} finally {
				release(sessions.getValue());
			}
		}
		Pointer pick = null;
		for (Pointer session : spotify) {
			if (pick == null && this.status(session) == PLAYING) {
				pick = session;
			}
		}
		if (pick == null) {
			PointerByReference current = new PointerByReference();
			if (call(manager, MANAGER_GET_CURRENT_SESSION, current) >= 0 && current.getValue() != null) {
				if (this.isSpotify(current.getValue())) {
					pick = current.getValue();
				} else {
					release(current.getValue());
				}
			}
		}
		if (pick == null && !spotify.isEmpty()) {
			pick = spotify.get(0);
		}
		for (Pointer session : spotify) {
			if (session != pick) {
				release(session);
			}
		}
		return pick;
	}

	private boolean isSpotify(Pointer session) {
		String id = this.stringProperty(session, SESSION_APP_ID);
		return id != null && id.toLowerCase(java.util.Locale.ROOT).contains("spotify");
	}

	private int status(Pointer session) {
		PointerByReference info = new PointerByReference();
		if (call(session, SESSION_PLAYBACK_INFO, info) < 0 || info.getValue() == null) {
			return -1;
		}
		try {
			IntByReference status = new IntByReference(-1);
			call(info.getValue(), PLAYBACK_STATUS, status);
			return status.getValue();
		} finally {
			release(info.getValue());
		}
	}

	private String describe(Pointer session) throws Exception {
		PointerByReference op = new PointerByReference();
		check(call(session, SESSION_MEDIA_PROPERTIES_ASYNC, op), "TryGetMediaPropertiesAsync");
		Pointer props = this.await(op.getValue());
		if (props == null) {
			return null;
		}
		String title;
		String artist;
		String album;
		String albumArtist;
		try {
			title = clean(this.stringProperty(props, PROPS_TITLE));
			artist = clean(this.stringProperty(props, PROPS_ARTIST));
			album = clean(this.stringProperty(props, PROPS_ALBUM_TITLE));
			albumArtist = clean(this.stringProperty(props, PROPS_ALBUM_ARTIST));
		} finally {
			release(props);
		}
		long position = 0L;
		long duration = 0L;
		PointerByReference timeline = new PointerByReference();
		if (call(session, SESSION_TIMELINE, timeline) >= 0 && timeline.getValue() != null) {
			try {
				// TimeSpan: 100 ns ticks.
				LongByReference value = new LongByReference();
				if (call(timeline.getValue(), TIMELINE_POSITION, value) >= 0) {
					position = Math.max(0L, value.getValue() / 10_000L);
				}
				if (call(timeline.getValue(), TIMELINE_END, value) >= 0) {
					duration = Math.max(0L, value.getValue() / 10_000L);
				}
			} finally {
				release(timeline.getValue());
			}
		}
		int status = this.status(session);
		String statusName = status >= 0 && status < STATUS.length ? STATUS[status] : "Unknown";
		return "FK\t" + statusName + "\t" + title + "\t" + artist + "\t" + album + "\t" + position + "\t" + duration + "\t" + albumArtist + "\t";
	}

	// ------------------------------------------------------------------ WinRT plumbing

	/** Waits for an IAsyncOperation (up to 8 s), returns its result and releases the operation. */
	private Pointer await(Pointer operation) throws Exception {
		if (operation == null) {
			return null;
		}
		try {
			PointerByReference info = new PointerByReference();
			check(call(operation, QUERY_INTERFACE, guid(IID_ASYNC_INFO), info), "IAsyncInfo");
			try {
				long deadline = System.currentTimeMillis() + 8000L;
				while (true) {
					IntByReference status = new IntByReference();
					check(call(info.getValue(), ASYNC_INFO_STATUS, status), "get_Status");
					if (status.getValue() == 1) {
						break;
					}
					if (status.getValue() != 0 || System.currentTimeMillis() > deadline) {
						return null;
					}
					Thread.sleep(10L);
				}
			} finally {
				release(info.getValue());
			}
			PointerByReference result = new PointerByReference();
			check(call(operation, ASYNC_OP_GET_RESULTS, result), "GetResults");
			return result.getValue();
		} finally {
			release(operation);
		}
	}

	private String stringProperty(Pointer object, int slot) {
		PointerByReference value = new PointerByReference();
		if (call(object, slot, value) < 0) {
			return null;
		}
		Pointer hstring = value.getValue();
		if (hstring == null) {
			return "";
		}
		try {
			IntByReference length = new IntByReference();
			Pointer raw = (Pointer) this.combase.getFunction("WindowsGetStringRawBuffer").invoke(Pointer.class, new Object[] {hstring, length});
			return raw == null ? "" : new String(raw.getCharArray(0, length.getValue()));
		} finally {
			this.deleteHstring(hstring);
		}
	}

	private Pointer hstring(String value) throws Exception {
		PointerByReference out = new PointerByReference();
		check(this.combase.getFunction("WindowsCreateString").invokeInt(new Object[] {new WString(value), value.length(), out}), "WindowsCreateString");
		return out.getValue();
	}

	private void deleteHstring(Pointer hstring) {
		if (hstring != null) {
			this.combase.getFunction("WindowsDeleteString").invokeInt(new Object[] {hstring});
		}
	}

	/** Calls vtable slot {@code slot} on a COM object; returns the HRESULT. */
	private static int call(Pointer object, int slot, Object... args) {
		Pointer vtable = object.getPointer(0);
		Function function = Function.getFunction(vtable.getPointer((long) slot * Native.POINTER_SIZE), Function.ALT_CONVENTION);
		Object[] full = new Object[args.length + 1];
		full[0] = object;
		System.arraycopy(args, 0, full, 1, args.length);
		return function.invokeInt(full);
	}

	private static void release(Pointer object) {
		if (object != null) {
			call(object, RELEASE);
		}
	}

	private static void check(int hresult, String what) throws Exception {
		if (hresult < 0) {
			throw new Exception(what + " failed: 0x" + Integer.toHexString(hresult));
		}
	}

	/** A GUID string as the 16-byte struct COM expects. */
	private static Memory guid(String text) {
		String hex = text.replace("{", "").replace("}", "").replace("-", "");
		Memory memory = new Memory(16);
		memory.setInt(0, (int) Long.parseLong(hex.substring(0, 8), 16));
		memory.setShort(4, (short) Integer.parseInt(hex.substring(8, 12), 16));
		memory.setShort(6, (short) Integer.parseInt(hex.substring(12, 16), 16));
		for (int i = 0; i < 8; i++) {
			memory.setByte(8 + i, (byte) Integer.parseInt(hex.substring(16 + i * 2, 18 + i * 2), 16));
		}
		return memory;
	}

	private static String clean(String value) {
		return value == null ? "" : value.replaceAll("[\\t\\r\\n]", " ");
	}
}
