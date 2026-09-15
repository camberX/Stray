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
import net.raphimc.minecraftauth.msa.data.MsaConstants;
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
				int added = 0;
				int retried = 0;
				int skipped = 0;
				int checked = 0;
				MicrosoftAuth.Session play = null;
				List<String> missed = new ArrayList<>();
				List<String> clientIds = clientIds(found);
				List<PrismAccounts.Candidate> work = new ArrayList<>();
				for (PrismAccounts.Candidate candidate : found) {
					Entry existing = existing(candidate);
					if (existing != null && existing.kind == Kind.MICROSOFT) {
						skipped++;
						continue;
					}
					work.add(candidate);
				}
				if (work.isEmpty()) {
					status.accept("Prism is up to date. " + skipped + " already saved.");
					return;
				}
				status.accept("Checking " + work.size() + " Prism account" + (work.size() == 1 ? "" : "s") + "…");
				for (PrismAccounts.Candidate candidate : work) {
					if (checked > 0) {
						Thread.sleep(800L);
					}
					checked++;
					Entry existing = existing(candidate);
					try {
						MicrosoftAuth.Session session = importOne(candidate, clientIds);
						if (existing == null) {
							added++;
							if (candidate.active() || play == null) {
								play = session;
							}
						} else {
							retried++;
						}
						status.accept((existing == null ? "Added " : "Updated ") + session.name()
							+ " (" + checked + "/" + work.size() + ")");
					} catch (Exception exception) {
						missed.add(candidate.name());
						Stray.LOGGER.warn("Could not import Prism account {}", candidate.name(), exception);
					}
				}
				save();
				if (play != null && skipped == 0) {
					login(play, status);
				}
				String done = prismSummary(added, retried, skipped, work.size());
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

	private static Entry existing(PrismAccounts.Candidate candidate) {
		for (Entry entry : ACCOUNTS) {
			if (entry.uuid.equals(candidate.uuid()) || entry.name.equalsIgnoreCase(candidate.name())) {
				return entry;
			}
		}
		return null;
	}

	private static String prismSummary(int added, int retried, int skipped, int checked) {
		List<String> parts = new ArrayList<>();
		if (added > 0) {
			parts.add("added " + added);
		}
		if (retried > 0) {
			parts.add("retried " + retried + " session");
		}
		if (skipped > 0) {
			parts.add("skipped " + skipped);
		}
		if (parts.isEmpty()) {
			return "Checked " + checked + " from Prism.";
		}
		return "Prism " + String.join(", ", parts) + ".";
	}

	private static List<String> clientIds(List<PrismAccounts.Candidate> found) {
		List<String> ids = new ArrayList<>();
		for (PrismAccounts.Candidate candidate : found) {
			addClientId(ids, candidate.clientId());
		}
		addClientId(ids, PrismAccounts.DEFAULT_CLIENT_ID);
		addClientId(ids, MsaConstants.JAVA_TITLE_ID);
		return ids;
	}

	private static void addClientId(List<String> ids, String id) {
		if (id == null || id.isBlank()) {
			return;
		}
		for (String existing : ids) {
			if (existing.equalsIgnoreCase(id)) {
				return;
			}
		}
		ids.add(id);
	}

	private static MicrosoftAuth.Session importOne(PrismAccounts.Candidate candidate, List<String> clientIds) throws Exception {
		Exception last = null;
		if (candidate.msaAccess() != null && !candidate.msaAccess().isBlank()
			&& (candidate.msaExpireMs() <= 0L || candidate.msaExpireMs() > System.currentTimeMillis() + 30_000L)) {
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
		if (candidate.microsoft()) {
			List<String> ids = new ArrayList<>();
			addClientId(ids, candidate.clientId());
			for (String id : clientIds) {
				addClientId(ids, id);
			}
			for (String clientId : ids) {
				boolean wrongId = false;
				List<String> scopes = MicrosoftAuth.titleClient(clientId)
					? List.of(MsaConstants.SCOPE_TITLE_AUTH, MsaConstants.SCOPE_OFFLINE_ACCESS)
					: List.of(MsaConstants.SCOPE_OFFLINE_ACCESS, MsaConstants.SCOPE_TITLE_AUTH);
				List<MsaEnvironment> environments = MicrosoftAuth.titleClient(clientId)
					? List.of(MsaEnvironment.LIVE, MsaEnvironment.MICROSOFT_ONLINE_CONSUMERS)
					: List.of(MsaEnvironment.MICROSOFT_ONLINE_CONSUMERS, MsaEnvironment.LIVE);
				outer:
				for (String scope : scopes) {
					for (MsaEnvironment environment : environments) {
						try {
							MicrosoftAuth.Session session = MicrosoftAuth.loginRefreshToken(
								candidate.refreshToken(),
								clientId,
								environment,
								scope
							);
							replace(session, Kind.MICROSOFT, false);
							return session;
						} catch (Exception exception) {
							last = exception;
							if (MicrosoftAuth.wrongClientId(exception)) {
								wrongId = true;
								break outer;
							}
						}
					}
				}
				if (wrongId) {
					continue;
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
