package dev.stray.client.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.raphimc.minecraftauth.msa.data.MsaEnvironment;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Saved Microsoft and session accounts. Tokens live in a dedicated file, not
 * stray.json.
 */
public final class AccountStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("accounts.json");
	private static final AtomicBoolean BUSY = new AtomicBoolean(false);
	private static final List<Entry> ACCOUNTS = new ArrayList<>();
	private static SessionApplier.Snapshot launcher;
	private static volatile String deviceUrl;
	private static volatile String deviceCode;

	private AccountStore() {
	}

	public static void load() {
		ACCOUNTS.clear();
		if (!Files.isRegularFile(PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(PATH)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonArray list = root.getAsJsonArray("accounts");
			if (list == null) {
				return;
			}
			for (JsonElement element : list) {
				if (!element.isJsonObject()) {
					continue;
				}
				Entry entry = Entry.fromJson(element.getAsJsonObject());
				if (entry != null) {
					ACCOUNTS.add(entry);
				}
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read accounts.json", exception);
		}
	}

	public static List<Entry> accounts() {
		return List.copyOf(ACCOUNTS);
	}

	public static boolean busy() {
		return BUSY.get();
	}

	public static String deviceUrl() {
		return deviceUrl;
	}

	public static String deviceCode() {
		return deviceCode;
	}

	public static String currentName() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getUser() == null) {
			return "";
		}
		return client.getUser().getName();
	}

	public static UUID currentId() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getUser() == null) {
			return null;
		}
		return client.getUser().getProfileId();
	}

	public static void rememberLauncher(Minecraft client) {
		if (launcher != null || client == null || client.getUser() == null) {
			return;
		}
		launcher = SessionApplier.snapshot(client);
	}

	public static void restoreLauncher(Consumer<String> status) {
		Minecraft client = Minecraft.getInstance();
		if (launcher == null) {
			status.accept("No launcher account stored.");
			return;
		}
		client.execute(() -> {
			try {
				SessionApplier.restore(client, launcher);
				status.accept("Restored " + client.getUser().getName() + ".");
			} catch (Exception exception) {
				status.accept(fail(exception));
			}
		});
	}

	public static void addMicrosoft(Consumer<String> status) {
		if (!BUSY.compareAndSet(false, true)) {
			status.accept("A sign-in is already running.");
			return;
		}
		deviceUrl = null;
		deviceCode = null;
		status.accept("Asking Microsoft for a device code…");
		Thread thread = new Thread(() -> {
			try {
				MicrosoftAuth.Session session = MicrosoftAuth.loginDeviceCode(code -> onDeviceCode(code, status));
				putMicrosoft(session);
				login(session, status);
			} catch (Exception exception) {
				status.accept(fail(exception));
			} finally {
				deviceUrl = null;
				deviceCode = null;
				BUSY.set(false);
			}
		}, "stray-ms-login");
		thread.setDaemon(true);
		thread.start();
	}

	public static void importPrism(Consumer<String> status) {
		if (!BUSY.compareAndSet(false, true)) {
			status.accept("A sign-in is already running.");
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Path file = PrismAccounts.file(client);
		if (file == null) {
			BUSY.set(false);
			status.accept("No Prism accounts.json found.");
			return;
		}
		status.accept("Importing Prism accounts…");
		Thread thread = new Thread(() -> {
			try {
				List<PrismAccounts.Candidate> found = PrismAccounts.load(file);
				if (found.isEmpty()) {
					status.accept("Prism has no Microsoft accounts.");
					return;
				}
				int ok = 0;
				int fail = 0;
				MicrosoftAuth.Session play = null;
				List<String> missed = new ArrayList<>();
				for (PrismAccounts.Candidate candidate : found) {
					if (ok + fail > 0) {
						Thread.sleep(800L);
					}
					try {
						MicrosoftAuth.Session session = importOne(candidate);
						if (candidate.active() || play == null) {
							play = session;
						}
						ok++;
						status.accept("Imported " + session.name() + " (" + ok + "/" + found.size() + ")");
					} catch (Exception exception) {
						fail++;
						missed.add(candidate.name());
						Stray.LOGGER.warn("Could not import Prism account {}", candidate.name(), exception);
					}
				}
				save();
				if (play != null) {
					login(play, status);
				}
				String done = "Imported " + ok + "/" + found.size() + " from Prism.";
				if (!missed.isEmpty()) {
					done += " Missed " + String.join(", ", missed) + ".";
				}
				status.accept(done);
			} catch (Exception exception) {
				status.accept(fail(exception));
			} finally {
				BUSY.set(false);
			}
		}, "stray-prism-import");
		thread.setDaemon(true);
		thread.start();
	}

	private static MicrosoftAuth.Session importOne(PrismAccounts.Candidate candidate) throws Exception {
		Exception last = null;
		if (candidate.microsoft()) {
			for (MsaEnvironment environment : List.of(MsaEnvironment.MICROSOFT_ONLINE_CONSUMERS, MsaEnvironment.LIVE)) {
				try {
					MicrosoftAuth.Session session = MicrosoftAuth.loginRefreshToken(
						candidate.refreshToken(),
						candidate.clientId(),
						environment
					);
					replace(session, Kind.MICROSOFT, false);
					return session;
				} catch (Exception exception) {
					last = exception;
				}
			}
			if (candidate.msaAccess() != null && !candidate.msaAccess().isBlank()) {
				try {
					MicrosoftAuth.Session session = MicrosoftAuth.loginMsa(
						candidate.clientId(),
						candidate.msaExpireMs(),
						candidate.msaAccess(),
						candidate.refreshToken()
					);
					replace(session, Kind.MICROSOFT, false);
					return session;
				} catch (Exception exception) {
					last = exception;
				}
			}
		}
		if (candidate.accessToken() != null && !candidate.accessToken().isBlank()) {
			try {
				MicrosoftAuth.Session session = MicrosoftAuth.fromAccessToken(candidate.accessToken());
				replace(session, Kind.SESSION, false);
				return session;
			} catch (Exception exception) {
				last = exception;
				MicrosoftAuth.Session session = new MicrosoftAuth.Session(
					candidate.name(),
					candidate.uuid(),
					candidate.accessToken(),
					null
				);
				replace(session, Kind.SESSION, false);
				return session;
			}
		}
		if (last != null) {
			throw last;
		}
		throw new IllegalStateException("Prism account " + candidate.name() + " has no usable token");
	}

	public static void addToken(String raw, Consumer<String> status) {
		String token = raw == null ? "" : raw.trim();
		if (token.isEmpty()) {
			status.accept("Paste a Microsoft refresh token or access token.");
			return;
		}
		if (!BUSY.compareAndSet(false, true)) {
			status.accept("A sign-in is already running.");
			return;
		}
		status.accept("Checking token…");
		Thread thread = new Thread(() -> {
			try {
				MicrosoftAuth.Session session;
				if (token.startsWith("M.")) {
					session = MicrosoftAuth.loginRefreshToken(token);
					putMicrosoft(session);
				} else {
					session = MicrosoftAuth.fromAccessToken(token);
					putSession(session);
				}
				login(session, status);
			} catch (Exception exception) {
				status.accept(fail(exception));
			} finally {
				BUSY.set(false);
			}
		}, "stray-token-login");
		thread.setDaemon(true);
		thread.start();
	}

	public static void switchTo(int index, Consumer<String> status) {
		if (index < 0 || index >= ACCOUNTS.size()) {
			status.accept("Account not found.");
			return;
		}
		if (!BUSY.compareAndSet(false, true)) {
			status.accept("A sign-in is already running.");
			return;
		}
		Entry entry = ACCOUNTS.get(index);
		status.accept("Signing in as " + entry.name + "…");
		Thread thread = new Thread(() -> {
			try {
				MicrosoftAuth.Session session = entry.refresh();
				replace(session, entry.kind);
				login(session, status);
			} catch (Exception exception) {
				status.accept(fail(exception));
			} finally {
				BUSY.set(false);
			}
		}, "stray-account-switch");
		thread.setDaemon(true);
		thread.start();
	}

	public static void remove(int index) {
		if (index < 0 || index >= ACCOUNTS.size()) {
			return;
		}
		ACCOUNTS.remove(index);
		save();
	}

	private static void onDeviceCode(MsaDeviceCode code, Consumer<String> status) {
		deviceUrl = code.getDirectVerificationUri();
		deviceCode = code.getUserCode();
		Minecraft client = Minecraft.getInstance();
		if (client != null) {
			client.execute(() -> {
				client.keyboardHandler.setClipboard(code.getUserCode());
				try {
					net.minecraft.util.Util.getPlatform().openUri(code.getDirectVerificationUri());
				} catch (Exception ignored) {
				}
			});
		}
		status.accept("Open Microsoft, code " + code.getUserCode() + " (copied).");
	}

	private static void login(MicrosoftAuth.Session session, Consumer<String> status) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			try {
				rememberLauncher(client);
				SessionApplier.apply(client, session.name(), session.uuid(), session.accessToken());
				status.accept("Playing as " + session.name() + ".");
			} catch (Exception exception) {
				status.accept(fail(exception));
			}
		});
	}

	private static void putMicrosoft(MicrosoftAuth.Session session) {
		replace(session, Kind.MICROSOFT, true);
	}

	private static void putSession(MicrosoftAuth.Session session) {
		replace(session, Kind.SESSION, true);
	}

	private static void replace(MicrosoftAuth.Session session, Kind kind) {
		replace(session, kind, true);
	}

	private static void replace(MicrosoftAuth.Session session, Kind kind, boolean write) {
		Entry next = new Entry(kind, session.name(), session.uuid(), session.accessToken(), session.authManager());
		for (int i = 0; i < ACCOUNTS.size(); i++) {
			Entry existing = ACCOUNTS.get(i);
			if (existing.uuid.equals(session.uuid()) || existing.name.equalsIgnoreCase(session.name())) {
				ACCOUNTS.set(i, next);
				if (write) {
					save();
				}
				return;
			}
		}
		ACCOUNTS.add(next);
		if (write) {
			save();
		}
	}

	private static void save() {
		JsonObject root = new JsonObject();
		JsonArray list = new JsonArray();
		for (Entry entry : ACCOUNTS) {
			list.add(entry.toJson());
		}
		root.add("accounts", list);
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not write accounts.json", exception);
		}
	}

	private static String fail(Exception exception) {
		Throwable cause = exception;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		String message = cause.getMessage();
		if (message == null || message.isBlank()) {
			message = exception.getClass().getSimpleName();
		}
		Stray.LOGGER.warn("Account sign-in failed", exception);
		return message;
	}

	public enum Kind {
		MICROSOFT,
		SESSION
	}

	public static final class Entry {
		public final Kind kind;
		public final String name;
		public final UUID uuid;
		private final String accessToken;
		private final JsonObject authManager;

		Entry(Kind kind, String name, UUID uuid, String accessToken, JsonObject authManager) {
			this.kind = kind;
			this.name = name;
			this.uuid = uuid;
			this.accessToken = accessToken;
			this.authManager = authManager;
		}

		public String kindLabel() {
			return kind == Kind.MICROSOFT ? "Microsoft" : "Session";
		}

		public boolean active() {
			UUID current = AccountStore.currentId();
			return current != null && current.equals(uuid);
		}

		MicrosoftAuth.Session refresh() throws Exception {
			if (kind == Kind.MICROSOFT && authManager != null) {
				return MicrosoftAuth.restore(authManager);
			}
			if (accessToken != null && !accessToken.isBlank()) {
				return MicrosoftAuth.fromAccessToken(accessToken);
			}
			throw new IllegalStateException("This account has no saved token");
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("type", kind == Kind.MICROSOFT ? "microsoft" : "session");
			json.addProperty("name", name);
			json.addProperty("uuid", uuid.toString());
			if (kind == Kind.MICROSOFT && authManager != null) {
				json.add("authManager", authManager);
			} else if (accessToken != null) {
				json.addProperty("accessToken", accessToken);
			}
			return json;
		}

		static Entry fromJson(JsonObject json) {
			try {
				String type = json.has("type") ? json.get("type").getAsString() : "microsoft";
				String name = json.get("name").getAsString();
				UUID uuid = UUID.fromString(json.get("uuid").getAsString());
				if ("session".equals(type)) {
					String token = json.get("accessToken").getAsString();
					return new Entry(Kind.SESSION, name, uuid, token, null);
				}
				JsonObject manager = json.getAsJsonObject("authManager");
				if (manager == null) {
					return null;
				}
				return new Entry(Kind.MICROSOFT, name, uuid, "", manager);
			} catch (Exception exception) {
				return null;
			}
		}
	}

	public static String launcherName() {
		if (launcher == null || launcher.user() == null) {
			User user = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getUser();
			return user == null ? "" : user.getName();
		}
		return launcher.user().getName();
	}
}
