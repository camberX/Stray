const MAX_BYTES = 2 * 1024 * 1024;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
const SERVER_RE = /^[A-Za-z0-9_\-]{1,32}$/;
const IRC_COOL_MS = 1500;
const PING_COOL_MS = 3000;
const IRC_MAX = 180;
const HISTORY_MAX = 30;
const PING_LIFE_MS = 45000;

export class StrayLive {
	constructor(ctx, env) {
		this.ctx = ctx;
		this.env = env;
		this.recent = [];
	}

	async fetch(request) {
		if (request.headers.get("Upgrade") !== "websocket") {
			return new Response("Expected websocket", { status: 426 });
		}
		const pair = new WebSocketPair();
		this.ctx.acceptWebSocket(pair[1]);
		pair[1].serializeAttachment({
			uuid: "",
			name: "",
			server: "",
			ircAt: 0,
			pingAt: 0
		});
		return new Response(null, { status: 101, webSocket: pair[0] });
	}

	async webSocketMessage(ws, raw) {
		let msg;
		try {
			msg = JSON.parse(typeof raw === "string" ? raw : new TextDecoder().decode(raw));
		} catch {
			return;
		}
		if (!msg || typeof msg !== "object") {
			return;
		}
		const type = String(msg.type || "");
		if (type === "hello") {
			this.onHello(ws, msg);
			return;
		}
		const meta = ws.deserializeAttachment() || {};
		if (!meta.uuid || !meta.name) {
			ws.send(JSON.stringify({ type: "error", message: "Say hello first." }));
			return;
		}
		if (type === "server") {
			meta.server = cleanServer(msg.server);
			ws.serializeAttachment(meta);
			return;
		}
		if (type === "irc") {
			this.onIrc(ws, meta, msg);
			return;
		}
		if (type === "ping") {
			this.onPing(ws, meta, msg);
		}
	}

	async webSocketClose(ws) {
		try {
			ws.close(1000, "bye");
		} catch {
			/* already closed */
		}
	}

	onHello(ws, msg) {
		const name = String(msg.name || "").trim();
		const uuid = String(msg.uuid || "").trim().toLowerCase();
		if (!NAME_RE.test(name) || !UUID_RE.test(uuid)) {
			ws.send(JSON.stringify({ type: "error", message: "Bad hello." }));
			ws.close(1008, "bad hello");
			return;
		}
		const meta = {
			uuid,
			name,
			server: cleanServer(msg.server),
			ircAt: 0,
			pingAt: 0
		};
		ws.serializeAttachment(meta);
		ws.send(JSON.stringify({ type: "hello", ok: true, users: this.ctx.getWebSockets().length }));
		if (this.recent.length) {
			ws.send(JSON.stringify({ type: "history", messages: this.recent }));
		}
	}

	onIrc(ws, meta, msg) {
		const now = Date.now();
		if (now - meta.ircAt < IRC_COOL_MS) {
			ws.send(JSON.stringify({ type: "error", message: "Slow down." }));
			return;
		}
		const text = cleanIrc(msg.text);
		if (!text) {
			return;
		}
		meta.ircAt = now;
		ws.serializeAttachment(meta);
		const payload = { type: "irc", name: meta.name, uuid: meta.uuid, text, at: now };
		this.recent.push(payload);
		if (this.recent.length > HISTORY_MAX) {
			this.recent.shift();
		}
		this.broadcast(payload);
	}

	onPing(ws, meta, msg) {
		const now = Date.now();
		if (now - meta.pingAt < PING_COOL_MS) {
			ws.send(JSON.stringify({ type: "error", message: "Slow down." }));
			return;
		}
		const server = cleanServer(msg.server) || meta.server;
		if (!server) {
			ws.send(JSON.stringify({ type: "error", message: "Join a lobby to ping." }));
			return;
		}
		if (server !== meta.server) {
			meta.server = server;
		}
		const x = toInt(msg.x);
		const y = toInt(msg.y);
		const z = toInt(msg.z);
		if (x === null || y === null || z === null || y < -128 || y > 512) {
			return;
		}
		meta.pingAt = now;
		ws.serializeAttachment(meta);
		const payload = {
			type: "ping",
			name: meta.name,
			uuid: meta.uuid,
			server,
			x,
			y,
			z,
			label: cleanIrc(msg.label || ""),
			at: now,
			until: now + PING_LIFE_MS
		};
		this.broadcast(payload, (other) => other.server === meta.server);
	}

	broadcast(payload, filter) {
		const data = JSON.stringify(payload);
		for (const socket of this.ctx.getWebSockets()) {
			const meta = socket.deserializeAttachment() || {};
			if (filter && !filter(meta)) {
				continue;
			}
			try {
				socket.send(data);
			} catch {
				/* drop */
			}
		}
	}
}

function cleanServer(value) {
	const text = String(value || "").trim();
	return SERVER_RE.test(text) ? text : "";
}

function cleanIrc(value) {
	let text = String(value || "").replace(/[\r\n\u0000-\u001f]/g, " ").replace(/§./g, "").trim();
	if (text.length > IRC_MAX) {
		text = text.slice(0, IRC_MAX);
	}
	return text;
}

function toInt(value) {
	const n = Number(value);
	if (!Number.isFinite(n)) {
		return null;
	}
	const i = Math.round(n);
	if (i < -30000000 || i > 30000000) {
		return null;
	}
	return i;
}

export default {
	async fetch(request, env) {
		try {
			return await route(request, env);
		} catch (error) {
			console.error(error);
			return json(500, { error: "Server error" });
		}
	}
};

async function route(request, env) {
	const url = new URL(request.url);
	const path = canonicalizePath(url.pathname);

	if (request.headers.get("Upgrade") === "websocket" && (path === "/ws" || path === "/api/ws")) {
		if (!env.STRAY_LIVE) {
			return json(501, { error: "Live socket is not bound. Deploy wrangler.toml Durable Objects." });
		}
		const id = env.STRAY_LIVE.idFromName("hub");
		return env.STRAY_LIVE.get(id).fetch(request);
	}

	if (request.method === "OPTIONS") {
		return new Response(null, { status: 204, headers: cors() });
	}
	if (request.method === "GET" && path === "/api/config") {
		const state = await loadState(env);
		return json(200, shopConfig(state, env));
	}
	if (request.method === "GET" && path === "/api/mod") {
		return serveModInfo(request, env);
	}
	if (request.method === "GET" && path === "/api/spotify") {
		return json(200, { clientId: String(env.SPOTIFY_CLIENT_ID || "9d11fac0f4774593bc06a0edb93423e7") });
	}
	if (request.method === "GET" && (path === "/download" || path === "/stray.jar" || path === "/eisenmann.jar" || path === "/voidmark.jar")) {
		return serveModJar(request, env);
	}
	if (request.method === "PUT" && path === "/api/config") {
		return handleShopConfig(request, env);
	}
	if (request.method === "GET" && path.startsWith("/api/cape/")) {
		const id = normalizeUuid(path.slice("/api/cape/".length));
		if (!id) {
			return json(400, { error: "Bad UUID" });
		}
		const state = await loadState(env);
		const listed = state.whitelist.includes(id);
		const tag = tagFor(state, id);
		const head = await env.CAPES.head(capeKey(id));
		const wings = wingsFor(state, id);
		if (!head) {
			return json(200, { has: false, hash: "", allowed: listed, tag, wings: wings.style, wingsRgb: wings.rgb, bypass: hasBypass(state, id), retryIn: capeRetrySec(state, id) });
		}
		return json(200, { has: true, hash: head.customMetadata?.hash || "", allowed: listed, tag, wings: wings.style, wingsRgb: wings.rgb, bypass: hasBypass(state, id), retryIn: capeRetrySec(state, id) });
	}
	if (request.method === "GET" && path.startsWith("/capes/") && path.endsWith(".png")) {
		const id = normalizeUuid(path.slice("/capes/".length, -4));
		if (!id) {
			return new Response(null, { status: 400, headers: cors() });
		}
		const object = await env.CAPES.get(capeKey(id));
		if (!object) {
			return new Response(null, { status: 404, headers: cors() });
		}
		const hash = object.customMetadata?.hash || "";
		return new Response(object.body, {
			headers: {
				...cors(),
				"Content-Type": "image/png",
				"Cache-Control": "no-store",
				ETag: `"${hash}"`
			}
		});
	}
	if ((request.method === "POST" || request.method === "PUT") && path === "/api/cape") {
		return handlePublish(request, env);
	}
	if (request.method === "POST" && path === "/api/cape/import") {
		return handleImport(request, env);
	}
	if (request.method === "DELETE" && path === "/api/cape") {
		return handleDelete(request, env);
	}
	if ((request.method === "GET" || request.method === "POST" || request.method === "PUT" || request.method === "DELETE") && path === "/api/whitelist") {
		return handleWhitelist(request, env);
	}
	if ((request.method === "PUT" || request.method === "DELETE") && path === "/api/tag") {
		return handleTag(request, env);
	}
	if ((request.method === "PUT" || request.method === "DELETE") && path === "/api/wings") {
		return handleWings(request, env);
	}
	if (request.method === "PUT" && path === "/api/bypass") {
		return handleBypass(request, env);
	}
	if ((request.method === "PUT" || request.method === "DELETE") && path === "/api/note") {
		return handleNote(request, env);
	}
	if (request.method === "DELETE" && path === "/api/cooldown") {
		return handleCooldown(request, env);
	}
	if (request.method === "PUT" && path === "/api/bulk") {
		return handleBulk(request, env);
	}
	if (request.method === "POST" && path === "/api/logout") {
		return json(200, { ok: true }, { "Set-Cookie": deskCookieHeader(request, "", true) });
	}
	if (request.method === "GET" && (path === "/admin.html" || path === "/manage.html" || path === "/index.html")) {
		const pretty = path === "/admin.html" ? "/admin" : path === "/manage.html" ? "/manage" : "/";
		return new Response(null, { status: 302, headers: { Location: pretty, "Cache-Control": "no-store" } });
	}
	if (request.method === "GET" && isManagePath(path)) {
		return serveManage(request, env);
	}
	if (request.method === "GET" && path === "/admin") {
		return page(LOGIN_HTML);
	}
	if (request.method === "GET" && path === "/") {
		return page(STORE_HTML);
	}
	if (request.method === "GET" && env.ASSETS) {
		const asset = await env.ASSETS.fetch(request);
		if (asset.status !== 404) {
			return asset;
		}
	}
	if (request.method === "GET" && path === "/cape-crop.js") {
		return new Response(CAPE_CROP_JS, {
			headers: { ...cors(), "Content-Type": "text/javascript; charset=utf-8", "Cache-Control": "no-store" }
		});
	}
	return json(404, { error: "Not found" });
}

async function handlePublish(request, env) {
	const uuid = normalizeUuid(request.headers.get("x-uuid"));
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	const adminOk = await adminCapeOk(request, env);
	const locked = capeRetryMs(state, uuid);
	if (!adminOk && locked > 0) {
		return json(429, { error: "Cape can be changed once per 24 hours", retryIn: Math.ceil(locked / 1000) });
	}
	const body = await readCapeBytes(request, adminOk);
	if (!body) {
		return json(400, { error: adminOk ? "Need a PNG or a cape URL" : "Not a PNG" });
	}
	if (!isPng(body)) {
		return json(400, { error: "Not a PNG" });
	}
	const hash = await hashBytes(body);
	await env.CAPES.put(capeKey(uuid), body, {
		httpMetadata: { contentType: "image/png" },
		customMetadata: { hash }
	});
	if (!adminOk) {
		touchCapeAt(state, uuid);
		await saveState(env, state);
	}
	return json(200, { ok: true, uuid });
}

async function handleImport(request, env) {
	if (!(await adminCapeOk(request, env))) {
		return json(403, { error: "Not authorized" });
	}
	let payload;
	try {
		payload = await request.json();
	} catch {
		return json(400, { error: "Need a URL" });
	}
	const url = String(payload.url || "").trim();
	if (!/^https?:\/\//i.test(url)) {
		return json(400, { error: "Need http(s) URL" });
	}
	try {
		const response = await fetch(url, {
			headers: { "User-Agent": "Stray" },
			signal: AbortSignal.timeout(10000)
		});
		if (!response.ok) {
			return json(400, { error: "Fetch failed" });
		}
		const buf = new Uint8Array(await response.arrayBuffer());
		if (buf.length < 24 || buf.length > MAX_BYTES) {
			return json(400, { error: "Bad size" });
		}
		const type = response.headers.get("content-type") || "application/octet-stream";
		return new Response(buf, {
			headers: { ...cors(), "Content-Type": type, "Cache-Control": "no-store" }
		});
	} catch {
		return json(400, { error: "Fetch failed" });
	}
}

async function handleDelete(request, env) {
	const uuid = normalizeUuid(request.headers.get("x-uuid"));
	if (!uuid) {
		return json(400, { error: "Need a UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	const adminOk = await adminCapeOk(request, env);
	const locked = capeRetryMs(state, uuid);
	if (!adminOk && locked > 0) {
		return json(429, { error: "Cape can be changed once per 24 hours", retryIn: Math.ceil(locked / 1000) });
	}
	await env.CAPES.delete(capeKey(uuid));
	if (!adminOk) {
		touchCapeAt(state, uuid);
		await saveState(env, state);
	}
	return json(200, { ok: true });
}

async function handleWhitelist(request, env) {
	const admin = env.ADMIN || "";
	if (!admin) {
		return json(500, { error: "Admin key is not set on the Worker" });
	}
	if (request.method === "GET") {
		if (!(await deskSessionOk(request, env))) {
			return json(403, { error: "Not authorized" });
		}
		const state = await loadState(env);
		const players = await playersFor(env, state, true);
		await saveState(env, state);
		return json(200, { uuids: state.whitelist, players });
	}
	let body;
	try {
		body = await request.json();
	} catch {
		body = {};
	}
	if ((body.admin || "") !== admin) {
		return json(403, { error: "Bad admin key" }, { "Set-Cookie": deskCookieHeader(request, "", true) });
	}
	if (request.method === "POST" && !body.uuid && !body.name) {
		return adminJson(request, env, 200, { ok: true });
	}
	if (!(await deskCookieOk(request, env))) {
		return json(403, { error: "Not authorized" });
	}
	const state = await loadState(env);
	const resolved = await resolvePlayer(body.uuid || body.name);
	if (!resolved.uuid) {
		const raw = String(body.uuid || body.name || "").trim();
		if (!raw) {
			return json(400, { error: "Need a username or UUID" });
		}
		if (sanitizeUsername(raw)) {
			return json(404, { error: "Unknown player" });
		}
		return json(400, { error: "Need a username or UUID" });
	}
	const uuid = resolved.uuid;
	if (request.method === "DELETE") {
		state.whitelist = state.whitelist.filter((id) => id !== uuid);
		forgetPlayer(state, uuid);
		await env.CAPES.delete(capeKey(uuid));
	} else if (!state.whitelist.includes(uuid)) {
		state.whitelist.push(uuid);
	}
	if (request.method !== "DELETE" && resolved.name) {
		rememberName(state, uuid, resolved.name);
	}
	await saveState(env, state);
	const players = await playersFor(env, state, request.method !== "DELETE" && !resolved.name);
	if (request.method !== "DELETE" && resolved.name) {
		rememberName(state, uuid, resolved.name);
		await saveState(env, state);
	}
	return adminJson(request, env, 200, { ok: true, uuids: state.whitelist, players });
}

async function handleTag(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const uuid = normalizeUuid(checked.body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	state.tags = state.tags && typeof state.tags === "object" && !Array.isArray(state.tags) ? state.tags : {};
	const tag = request.method === "DELETE" ? "" : sanitizeTag(checked.body.tag);
	if (tag) {
		state.tags[uuid] = tag;
	} else {
		delete state.tags[uuid];
	}
	await saveState(env, state);
	return json(200, { ok: true, tag, players: await playersFor(env, state) });
}

async function handleWings(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const uuid = normalizeUuid(checked.body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	state.wings = objectMap(state.wings);
	const style = request.method === "DELETE" ? "" : sanitizeWingStyle(checked.body.style);
	const rgb = sanitizeRgb(checked.body.rgb);
	if (style) {
		state.wings[uuid] = { style, rgb: rgb || WING_DEFAULT_RGB[style] || "FFFFFF" };
	} else {
		delete state.wings[uuid];
	}
	await saveState(env, state);
	return json(200, { ok: true, wings: wingsFor(state, uuid), players: await playersFor(env, state) });
}

async function handleBypass(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const uuid = normalizeUuid(checked.body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	state.bypass = objectMap(state.bypass);
	if (checked.body.bypass) {
		state.bypass[uuid] = true;
	} else {
		delete state.bypass[uuid];
	}
	await saveState(env, state);
	return json(200, { ok: true, bypass: Boolean(state.bypass[uuid]), players: await playersFor(env, state) });
}

async function handleNote(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const uuid = normalizeUuid(checked.body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	state.notes = objectMap(state.notes);
	const note = request.method === "DELETE" ? "" : sanitizeNote(checked.body.note);
	if (note) {
		state.notes[uuid] = note;
	} else {
		delete state.notes[uuid];
	}
	await saveState(env, state);
	return json(200, { ok: true, note, players: await playersFor(env, state) });
}

async function handleCooldown(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const uuid = normalizeUuid(checked.body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	state.capeAt = objectMap(state.capeAt);
	delete state.capeAt[uuid];
	await saveState(env, state);
	return json(200, { ok: true, retryIn: 0, players: await playersFor(env, state) });
}

async function handleBulk(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const raw = Array.isArray(checked.body.names)
		? checked.body.names
		: String(checked.body.text || "").split(/[\n,]+/);
	const names = raw.map((value) => String(value || "").trim()).filter(Boolean).slice(0, 25);
	if (!names.length) {
		return json(400, { error: "Paste usernames or UUIDs, one per line" });
	}
	const state = await loadState(env);
	const added = [];
	const skipped = [];
	const failed = [];
	for (const name of names) {
		const resolved = await resolvePlayer(name);
		if (!resolved.uuid) {
			failed.push(name);
			continue;
		}
		if (state.whitelist.includes(resolved.uuid)) {
			skipped.push(name);
			continue;
		}
		state.whitelist.push(resolved.uuid);
		if (resolved.name) {
			rememberName(state, resolved.uuid, resolved.name);
		}
		added.push(resolved.name || resolved.uuid);
	}
	await saveState(env, state);
	const players = await playersFor(env, state, true);
	await saveState(env, state);
	return json(200, { ok: true, added: added.length, skipped: skipped.length, failed, players });
}

async function handleShopConfig(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const state = await loadState(env);
	state.config = {
		...shopConfig(state, env),
		paypal: sanitizePaypal(checked.body.paypal),
		price: sanitizePrice(checked.body.price),
		title: sanitizeTitle(checked.body.title),
		blurb: sanitizeBlurb(checked.body.blurb)
	};
	await saveState(env, state);
	return json(200, { ok: true, ...state.config });
}

async function adminBody(request, env) {
	const admin = env.ADMIN || "";
	if (!admin) {
		return { error: json(500, { error: "Admin key is not set on the Worker" }) };
	}
	if (!(await deskCookieOk(request, env))) {
		return { error: json(403, { error: "Not authorized" }) };
	}
	let body;
	try {
		body = await request.json();
	} catch {
		body = {};
	}
	if ((body.admin || "") !== admin) {
		return { error: json(403, { error: "Not authorized" }) };
	}
	return { body };
}

async function readCapeBytes(request, adminOk) {
	const type = (request.headers.get("content-type") || "").toLowerCase();
	if (type.includes("application/json")) {
		if (!adminOk) {
			return null;
		}
		let payload;
		try {
			payload = await request.json();
		} catch {
			return null;
		}
		const url = String(payload.url || "").trim();
		if (!/^https?:\/\//i.test(url)) {
			return null;
		}
		try {
			const response = await fetch(url, {
				headers: { "User-Agent": "Stray" },
				signal: AbortSignal.timeout(10000)
			});
			if (!response.ok) {
				return null;
			}
			return new Uint8Array(await response.arrayBuffer());
		} catch {
			return null;
		}
	}
	return new Uint8Array(await request.arrayBuffer());
}

function tagFor(state, uuid) {
	return sanitizeTag(objectMap(state.tags)[uuid]);
}

const WING_STYLES = ["angel", "demon", "fairy", "phoenix", "butterfly"];
const WING_DEFAULT_RGB = { angel: "F4F6FF", demon: "2B1D2E", fairy: "9AE6FF", phoenix: "FF7A1A", butterfly: "3D98CB" };

function sanitizeWingStyle(value) {
	const style = String(value || "").trim().toLowerCase();
	return WING_STYLES.includes(style) ? style : "";
}

function sanitizeRgb(value) {
	const hex = String(value || "").trim().replace(/^#/, "").toUpperCase();
	return /^[0-9A-F]{6}$/.test(hex) ? hex : "";
}

function wingsFor(state, uuid) {
	const entry = objectMap(state.wings)[uuid];
	if (!entry || typeof entry !== "object") {
		return { style: "", rgb: "" };
	}
	const style = sanitizeWingStyle(entry.style);
	if (!style) {
		return { style: "", rgb: "" };
	}
	return { style, rgb: sanitizeRgb(entry.rgb) || WING_DEFAULT_RGB[style] };
}

function noteFor(state, uuid) {
	return sanitizeNote(objectMap(state.notes)[uuid]);
}

function shopConfig(state, env) {
	const stored = objectMap(state.config);
	return {
		title: stored.title || env.TITLE || "STRAY Capes",
		discord: "@evilkitten911",
		blurb: stored.blurb || ""
	};
}

const MAX_TAG = 48;
const MAX_NOTE = 160;
const DAY_MS = 24 * 60 * 60 * 1000;

function sanitizeTag(value) {
	return String(value || "")
		.replace(/[\u0000-\u001f\\"]/g, "")
		.replace(/\s+/g, " ")
		.trim()
		.slice(0, MAX_TAG);
}

function sanitizeNote(value) {
	return String(value || "")
		.replace(/[\u0000-\u001f]/g, "")
		.replace(/\s+/g, " ")
		.trim()
		.slice(0, MAX_NOTE);
}

function sanitizePaypal(value) {
	return String(value || "")
		.replace(/\s+/g, "")
		.slice(0, 80);
}

function sanitizePrice(value) {
	const price = String(value || "").replace(/\s+/g, " ").trim().slice(0, 24);
	return price || "$1";
}

function sanitizeTitle(value) {
	const title = String(value || "").replace(/\s+/g, " ").trim().slice(0, 48);
	return title || "STRAY Capes";
}

function sanitizeBlurb(value) {
	return String(value || "")
		.replace(/[\u0000-\u001f]/g, "")
		.replace(/\s+/g, " ")
		.trim()
		.slice(0, 280);
}

function objectMap(value) {
	return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}

function hasBypass(state, uuid) {
	return Boolean(objectMap(state.bypass)[uuid]);
}

function capeRetryMs(state, uuid) {
	if (hasBypass(state, uuid)) {
		return 0;
	}
	const last = Number(objectMap(state.capeAt)[uuid]) || 0;
	if (!last) {
		return 0;
	}
	return Math.max(0, last + DAY_MS - Date.now());
}

function capeRetrySec(state, uuid) {
	return Math.ceil(capeRetryMs(state, uuid) / 1000);
}

function touchCapeAt(state, uuid) {
	state.capeAt = objectMap(state.capeAt);
	state.capeAt[uuid] = Date.now();
}

function adminHeaderOk(request, env) {
	const admin = env.ADMIN || "";
	return Boolean(admin) && (request.headers.get("x-admin") || "") === admin;
}

async function adminCapeOk(request, env) {
	return adminHeaderOk(request, env) && await deskCookieOk(request, env);
}

async function deskSessionOk(request, env) {
	return adminHeaderOk(request, env) && await deskCookieOk(request, env);
}

async function playersFor(env, state, forceNames) {
	return Promise.all(state.whitelist.map(async (uuid) => {
		const head = await env.CAPES.head(capeKey(uuid));
		return {
			uuid,
			name: await mojangName(uuid, state, forceNames),
			cape: Boolean(head),
			hash: head?.customMetadata?.hash || "",
			tag: tagFor(state, uuid),
			wings: wingsFor(state, uuid),
			bypass: hasBypass(state, uuid),
			retryIn: capeRetrySec(state, uuid),
			note: noteFor(state, uuid)
		};
	}));
}

const NAME_TTL_MS = 10 * 60 * 1000;

function rememberName(state, uuid, name) {
	const clean = String(name || "").trim();
	if (!uuid || !clean) {
		return;
	}
	state.names = objectMap(state.names);
	state.namesAt = objectMap(state.namesAt);
	state.names[uuid] = clean;
	state.namesAt[uuid] = Date.now();
}

function forgetPlayer(state, uuid) {
	if (state.names) {
		delete state.names[uuid];
	}
	if (state.namesAt) {
		delete state.namesAt[uuid];
	}
	if (state.tags) {
		delete state.tags[uuid];
	}
	if (state.bypass) {
		delete state.bypass[uuid];
	}
	if (state.capeAt) {
		delete state.capeAt[uuid];
	}
	if (state.notes) {
		delete state.notes[uuid];
	}
	if (state.wings) {
		delete state.wings[uuid];
	}
}

function sanitizeUsername(value) {
	const name = String(value || "").trim();
	return /^[A-Za-z0-9_]{1,16}$/.test(name) ? name : "";
}

async function resolvePlayer(raw) {
	const uuid = normalizeUuid(raw);
	if (uuid) {
		return { uuid, name: "" };
	}
	const name = sanitizeUsername(raw);
	if (!name) {
		return { uuid: "", name: "" };
	}
	const found = await lookupUuid(name);
	return { uuid: found, name: found ? name : "" };
}

async function mojangName(uuid, state, force) {
	state.names = objectMap(state.names);
	state.namesAt = objectMap(state.namesAt);
	const cached = typeof state.names[uuid] === "string" ? state.names[uuid] : "";
	const at = Number(state.namesAt[uuid]) || 0;
	if (!force && cached && Date.now() - at < NAME_TTL_MS) {
		return cached;
	}
	const name = await lookupName(uuid);
	if (name) {
		rememberName(state, uuid, name);
		return name;
	}
	return cached;
}

async function lookupName(uuid) {
	const id = String(uuid || "").replaceAll("-", "");
	if (id.length !== 32) {
		return "";
	}
	const dashed = `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`;
	const attempts = [
		{ url: "https://sessionserver.mojang.com/session/minecraft/profile/" + id, read: (data) => data.name },
		{ url: "https://crafthead.net/profile/" + id, read: (data) => data.name },
		{ url: "https://mowojang.matdoes.dev/" + dashed, read: (data) => data.name },
		{ url: "https://playerdb.co/api/player/minecraft/" + dashed, read: (data) => data?.data?.player?.username },
		{ url: "https://api.ashcon.app/mojang/v2/user/" + dashed, read: (data) => data.username || data.name }
	];
	return firstString(attempts);
}

async function lookupUuid(name) {
	const encoded = encodeURIComponent(name);
	const attempts = [
		{ url: "https://api.mojang.com/users/profiles/minecraft/" + encoded, read: (data) => data.id },
		{ url: "https://mowojang.matdoes.dev/" + encoded, read: (data) => data.id },
		{ url: "https://crafthead.net/profile/" + encoded, read: (data) => data.id },
		{ url: "https://playerdb.co/api/player/minecraft/" + encoded, read: (data) => data?.data?.player?.id || data?.data?.player?.raw_id },
		{ url: "https://api.ashcon.app/mojang/v2/user/" + encoded, read: (data) => data.uuid }
	];
	for (const attempt of attempts) {
		const data = await fetchJson(attempt.url);
		if (!data) {
			continue;
		}
		const uuid = normalizeUuid(attempt.read(data));
		if (uuid) {
			return uuid;
		}
	}
	return "";
}

async function firstString(attempts) {
	for (const attempt of attempts) {
		const data = await fetchJson(attempt.url);
		if (!data) {
			continue;
		}
		const value = String(attempt.read(data) || "").trim();
		if (value) {
			return value;
		}
	}
	return "";
}

async function fetchJson(url) {
	try {
		const response = await fetch(url, {
			headers: { "User-Agent": "Stray" },
			signal: AbortSignal.timeout(5000)
		});
		if (!response.ok) {
			return null;
		}
		return await response.json();
	} catch {
		return null;
	}
}

async function loadState(env) {
	const empty = { whitelist: [], names: {}, namesAt: {}, tags: {}, wings: {}, bypass: {}, capeAt: {}, notes: {}, config: {} };
	const object = await env.CAPES.get("state.json");
	if (!object) {
		return empty;
	}
	try {
		const parsed = JSON.parse(await object.text());
		return {
			whitelist: Array.isArray(parsed.whitelist) ? parsed.whitelist : [],
			names: objectMap(parsed.names),
			namesAt: objectMap(parsed.namesAt),
			tags: objectMap(parsed.tags),
			wings: objectMap(parsed.wings),
			bypass: objectMap(parsed.bypass),
			capeAt: objectMap(parsed.capeAt),
			notes: objectMap(parsed.notes),
			config: objectMap(parsed.config)
		};
	} catch {
		return empty;
	}
}

async function saveState(env, state) {
	const copy = { ...state };
	delete copy.bans;
	await env.CAPES.put("state.json", JSON.stringify(copy));
}

function capeKey(uuid) {
	return `capes/${uuid}.png`;
}

async function hashBytes(bytes) {
	const digest = await crypto.subtle.digest("SHA-256", bytes);
	return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 16);
}

function normalizeUuid(value) {
	const raw = (value || "").trim().toLowerCase().replace(/[^0-9a-f]/g, "");
	if (raw.length !== 32) {
		return "";
	}
	const dashed = `${raw.slice(0, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20)}`;
	return UUID_RE.test(dashed) ? dashed : "";
}

function isPng(bytes) {
	return bytes.length >= 24 && bytes.length <= MAX_BYTES && bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47;
}

function json(status, body, extraHeaders) {
	return new Response(JSON.stringify(body), {
		status,
		headers: {
			...cors(),
			"Content-Type": "application/json; charset=utf-8",
			"Cache-Control": "no-store",
			...(extraHeaders || {})
		}
	});
}

const DESK_COOKIE = "stray_desk";
const DESK_TTL_SEC = 60 * 60 * 24 * 7;

function canonicalizePath(pathname) {
	let raw = String(pathname || "/");
	try {
		raw = decodeURIComponent(raw);
	} catch {
		return "/";
	}
	raw = raw.replace(/\\/g, "/");
	const parts = [];
	for (const seg of raw.split("/")) {
		if (!seg || seg === ".") {
			continue;
		}
		if (seg === "..") {
			parts.pop();
			continue;
		}
		if (seg.includes("\0")) {
			return "/";
		}
		parts.push(seg);
	}
	return "/" + parts.join("/");
}

function isManagePath(path) {
	return path === "/manage";
}

function cookieOf(request, name) {
	const raw = request.headers.get("Cookie") || "";
	const parts = raw.split(";");
	for (let i = 0; i < parts.length; i++) {
		const piece = parts[i].trim();
		const eq = piece.indexOf("=");
		if (eq < 0) {
			continue;
		}
		if (piece.slice(0, eq) === name) {
			return piece.slice(eq + 1);
		}
	}
	return "";
}

function deskSecure(request) {
	try {
		return new URL(request.url).protocol === "https:";
	} catch {
		return true;
	}
}

function deskCookieHeader(request, token, clear) {
	const parts = [
		DESK_COOKIE + "=" + (clear ? "" : token),
		"Path=/",
		"HttpOnly",
		"SameSite=Lax",
		clear ? "Max-Age=0" : ("Max-Age=" + DESK_TTL_SEC)
	];
	if (deskSecure(request)) {
		parts.push("Secure");
	}
	return parts.join("; ");
}

function bytesToHex(bytes) {
	const arr = new Uint8Array(bytes);
	let out = "";
	for (let i = 0; i < arr.length; i++) {
		out += arr[i].toString(16).padStart(2, "0");
	}
	return out;
}

function timingSafeEqualStr(a, b) {
	if (a.length !== b.length) {
		return false;
	}
	let diff = 0;
	for (let i = 0; i < a.length; i++) {
		diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
	}
	return diff === 0;
}

async function hmacHex(secret, msg) {
	const key = await crypto.subtle.importKey(
		"raw",
		new TextEncoder().encode(secret),
		{ name: "HMAC", hash: "SHA-256" },
		false,
		["sign"]
	);
	const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(msg));
	return bytesToHex(sig);
}

async function mintDeskToken(secret) {
	const exp = Math.floor(Date.now() / 1000) + DESK_TTL_SEC;
	const msg = "v1." + exp;
	return msg + "." + await hmacHex(secret, msg);
}

async function verifyDeskToken(secret, token) {
	const parts = String(token || "").split(".");
	if (parts.length !== 3 || parts[0] !== "v1") {
		return false;
	}
	const exp = Number(parts[1]);
	if (!Number.isFinite(exp) || exp < Date.now() / 1000) {
		return false;
	}
	const sig = await hmacHex(secret, "v1." + parts[1]);
	return timingSafeEqualStr(sig, parts[2]);
}

async function deskCookieOk(request, env) {
	const secret = env.ADMIN || "";
	if (!secret) {
		return false;
	}
	return verifyDeskToken(secret, cookieOf(request, DESK_COOKIE));
}

async function adminJson(request, env, status, body) {
	const token = await mintDeskToken(env.ADMIN || "");
	return json(status, body, { "Set-Cookie": deskCookieHeader(request, token, false) });
}

function redirectLogin() {
	return new Response(null, {
		status: 302,
		headers: {
			Location: "/admin",
			"Cache-Control": "no-store",
			Pragma: "no-cache"
		}
	});
}

async function serveManage(request, env) {
	if (!(await deskCookieOk(request, env))) {
		return redirectLogin();
	}
	if (env.ASSETS) {
		const asset = await env.ASSETS.fetch(request);
		if (asset.status !== 404) {
			const headers = new Headers(asset.headers);
			headers.set("Cache-Control", "private, no-store, no-cache");
			headers.set("Pragma", "no-cache");
			return new Response(asset.body, { status: asset.status, headers });
		}
	}
	return page(MANAGE_HTML);
}

const DEFAULT_MOD_GITHUB = "camberX/Stray";
const LEGACY_MOD_GITHUB = "camberX/voidmark";
const DEFAULT_MOD_BRANCH = "main";
const DEFAULT_MOD_PATH = "web/public/mod";

async function readModMeta(request, env) {
	const github = await readGithubMeta(env);
	if (github && github.version) {
		return github;
	}
	if (!env.ASSETS) {
		return null;
	}
	const assetPaths = ["/mod.json", "/mod/latest.json"];
	for (let i = 0; i < assetPaths.length; i++) {
		try {
			const response = await env.ASSETS.fetch(new URL(assetPaths[i], request.url));
			if (response.ok) {
				const meta = await response.json();
				if (meta && meta.version) {
					meta.source = "assets";
					return meta;
				}
			}
		} catch {
			// try next
		}
	}
	return null;
}

function cleanGithubRepo(value) {
	return String(value || "")
		.trim()
		.replace(/^https?:\/\/github\.com\//i, "")
		.replace(/\.git$/i, "")
		.replace(/\/+$/, "");
}

function modGithubRepos(env) {
	const seen = {};
	const list = [];
	function add(value) {
		const repo = cleanGithubRepo(value);
		const key = repo.toLowerCase();
		if (!repo || seen[key]) {
			return;
		}
		seen[key] = true;
		list.push(repo);
	}
	add(DEFAULT_MOD_GITHUB);
	add("camberX/Eisenmann");
	add(LEGACY_MOD_GITHUB);
	add(env && env.MOD_GITHUB);
	return list;
}

function modGithubBranch(env, meta) {
	return String((meta && meta.branch) || (env && env.MOD_GITHUB_BRANCH) || DEFAULT_MOD_BRANCH).trim() || DEFAULT_MOD_BRANCH;
}

function modGithubDir(env, meta) {
	return String((meta && meta.dir) || (env && env.MOD_GITHUB_PATH) || DEFAULT_MOD_PATH).replace(/^\/+|\/+$/g, "") || DEFAULT_MOD_PATH;
}

function githubFileUrls(repo, branch, dir, fileName) {
	const file = String(fileName || "stray.jar").replace(/^\/+/, "");
	const path = dir + "/" + file;
	return [
		"https://cdn.jsdelivr.net/gh/" + repo + "@" + branch + "/" + path,
		"https://github.com/" + repo + "/raw/" + branch + "/" + path,
		"https://raw.githubusercontent.com/" + repo + "/" + branch + "/" + path,
		"https://api.github.com/repos/" + repo + "/contents/" + path + "?ref=" + encodeURIComponent(branch)
	];
}

function githubFetchHeaders(url) {
	const headers = { "User-Agent": "Stray-Shop" };
	if (url.includes("api.github.com")) {
		headers.Accept = "application/vnd.github.raw";
	}
	return headers;
}

async function fetchGithubFile(url) {
	const response = await fetch(url, {
		headers: githubFetchHeaders(url),
		redirect: "follow",
		cache: "no-store",
		cf: { cacheTtl: 0, cacheEverything: false }
	});
	return response.ok ? response : null;
}

function githubModFileUrls(env, meta, fileName) {
	const repo = (meta && meta.repo) || DEFAULT_MOD_GITHUB;
	if (!repo) {
		return [];
	}
	const urls = githubFileUrls(repo, modGithubBranch(env, meta), modGithubDir(env, meta), fileName);
	const version = meta && meta.version ? String(meta.version) : "";
	const file = String(fileName || "").replace(/^\/+/, "");
	if (version && file) {
		urls.push("https://github.com/" + repo + "/releases/download/v" + version + "/" + file);
	}
	return urls;
}

async function readGithubMeta(env) {
	const repos = modGithubRepos(env);
	const branch = modGithubBranch(env);
	const dir = modGithubDir(env);
	let best = null;
	for (let r = 0; r < repos.length; r++) {
		const repo = repos[r];
		const urls = githubFileUrls(repo, branch, dir, "latest.json");
		for (let i = 0; i < urls.length; i++) {
			try {
				const response = await fetchGithubFile(urls[i]);
				if (!response) {
					continue;
				}
				const meta = await response.json();
				if (!meta || !meta.version) {
					continue;
				}
				meta.repo = repo;
				meta.branch = branch;
				meta.dir = dir;
				meta.source = "github";
				if (!best || compareModVersion(meta.version, best.version) > 0) {
					best = meta;
				}
			} catch {
				// try next mirror
			}
		}
	}
	return best;
}

function compareModVersion(left, right) {
	const a = String(left || "").split(/[^0-9]+/).filter(Boolean).map(Number);
	const b = String(right || "").split(/[^0-9]+/).filter(Boolean).map(Number);
	const n = Math.max(a.length, b.length);
	for (let i = 0; i < n; i++) {
		const av = i < a.length ? a[i] : 0;
		const bv = i < b.length ? b[i] : 0;
		if (av !== bv) {
			return av - bv;
		}
	}
	return 0;
}

async function serveModInfo(request, env) {
	const meta = await readModMeta(request, env);
	if (!meta || !meta.version) {
		return json(404, { error: "Mod build is not published yet" });
	}
	return json(200, {
		version: String(meta.version),
		minecraft: String(meta.minecraft || "26.1.2"),
		file: String(meta.file || ("stray-" + meta.version + ".jar")),
		url: "/download",
		source: meta.source || "assets"
	});
}

async function fetchModBytes(request, env, meta) {
	const names = [];
	if (meta && meta.file) {
		names.push(String(meta.file));
	}
	// Do not fall back to stray.jar here. That file can still be the previous
	// build for a few seconds after latest.json flips, and the launcher then
	// rejects the download as the wrong version.
	for (let i = 0; i < names.length; i++) {
		const urls = githubModFileUrls(env, meta, names[i]);
		for (let u = 0; u < urls.length; u++) {
			try {
				const upstream = await fetchGithubFile(urls[u]);
				if (upstream) {
					return { body: upstream.body, file: names[i] };
				}
			} catch {
				// try next mirror
			}
		}
	}
	if (env.ASSETS) {
		for (let i = 0; i < names.length; i++) {
			try {
				const asset = await env.ASSETS.fetch(new URL("/mod/" + names[i], request.url));
				if (asset.ok) {
					return { body: asset.body, file: names[i] };
				}
			} catch {
				// try next
			}
		}
	}
	return null;
}

async function serveModJar(request, env) {
	const meta = await readModMeta(request, env);
	const got = await fetchModBytes(request, env, meta);
	if (!got) {
		return json(404, { error: "Mod build is not published yet" });
	}
	const name = String((meta && meta.file) || got.file || "stray.jar").replace(/"/g, "");
	const headers = new Headers();
	headers.set("Content-Type", "application/java-archive");
	headers.set("Content-Disposition", "attachment; filename=\"" + name + "\"");
	headers.set("Cache-Control", "no-store");
	const extra = cors();
	for (const key of Object.keys(extra)) {
		headers.set(key, extra[key]);
	}
	return new Response(got.body, { status: 200, headers });
}

function cors() {
	return {
		"Access-Control-Allow-Origin": "*",
		"Access-Control-Allow-Methods": "GET,PUT,POST,DELETE,OPTIONS",
		"Access-Control-Allow-Headers": "Content-Type, X-UUID, X-Key, X-Code, X-Token, X-Admin"
	};
}

function page(html) {
	return new Response(html, {
		headers: {
			"Content-Type": "text/html; charset=utf-8",
			"Cache-Control": "private, no-store, no-cache",
			Pragma: "no-cache"
		}
	});
}

const CAPE_CROP_JS = "window.StrayCapeCrop = (function () {\n\tvar ASPECT = 10 / 16;\n\tvar FACE_W = 10;\n\tvar FACE_H = 16;\n\tvar LAYOUT_W = 64;\n\tvar LAYOUT_H = 32;\n\tvar MAX_SCALE = 16;\n\tvar overlay = null;\n\n\tfunction isVanilla(w, h) {\n\t\treturn w >= 64 && h >= 32 && w % 64 === 0 && h % 32 === 0 && w / 64 === h / 32;\n\t}\n\n\tfunction cover(srcW, srcH) {\n\t\tvar srcAspect = srcW / Math.max(1, srcH);\n\t\tvar crop = { x: 0, y: 0, w: 1, h: 1 };\n\t\tif (srcAspect > ASPECT) {\n\t\t\tcrop.h = 1;\n\t\t\tcrop.w = ASPECT / srcAspect;\n\t\t\tcrop.x = (1 - crop.w) * 0.5;\n\t\t\tcrop.y = 0;\n\t\t} else {\n\t\t\tcrop.w = 1;\n\t\t\tcrop.h = srcAspect / ASPECT;\n\t\t\tcrop.x = 0;\n\t\t\tcrop.y = (1 - crop.h) * 0.5;\n\t\t}\n\t\treturn crop;\n\t}\n\n\tfunction clampCrop(crop, srcW, srcH) {\n\t\tsrcW = Math.max(1, srcW);\n\t\tsrcH = Math.max(1, srcH);\n\t\tvar srcAspect = srcW / srcH;\n\t\tvar normAspect = ASPECT / srcAspect;\n\t\tvar max = cover(srcW, srcH);\n\t\tvar minW = Math.max(10 / srcW, max.w * 0.12);\n\t\tcrop.w = Math.min(max.w, Math.max(minW, crop.w));\n\t\tcrop.h = crop.w / normAspect;\n\t\tif (crop.h > max.h) {\n\t\t\tcrop.h = max.h;\n\t\t\tcrop.w = crop.h * normAspect;\n\t\t}\n\t\tcrop.x = Math.min(Math.max(0, crop.x), Math.max(0, 1 - crop.w));\n\t\tcrop.y = Math.min(Math.max(0, crop.y), Math.max(0, 1 - crop.h));\n\t}\n\n\tfunction loadImage(file) {\n\t\treturn new Promise(function (resolve, reject) {\n\t\t\tvar url = URL.createObjectURL(file);\n\t\t\tvar img = new Image();\n\t\t\timg.onload = function () {\n\t\t\t\tURL.revokeObjectURL(url);\n\t\t\t\tresolve(img);\n\t\t\t};\n\t\t\timg.onerror = function () {\n\t\t\t\tURL.revokeObjectURL(url);\n\t\t\t\treject(new Error(\"Could not read that image.\"));\n\t\t\t};\n\t\t\timg.src = url;\n\t\t});\n\t}\n\n\tfunction pickScale(sw, sh) {\n\t\tvar needed = Math.max(4, Math.min(MAX_SCALE, Math.max(Math.floor(sw / FACE_W), Math.floor(sh / FACE_H))));\n\t\treturn Math.min(MAX_SCALE, Math.max(4, needed));\n\t}\n\n\tfunction bakeAtlas(img, crop) {\n\t\tvar sw = img.width;\n\t\tvar sh = img.height;\n\t\t\tvar scale = pickScale(img.width, img.height);\n\t\tvar aw = LAYOUT_W * scale;\n\t\tvar ah = LAYOUT_H * scale;\n\t\tvar out = document.createElement(\"canvas\");\n\t\tout.width = aw;\n\t\tout.height = ah;\n\t\tvar ctx = out.getContext(\"2d\");\n\t\tctx.fillStyle = \"#000\";\n\t\tctx.fillRect(0, 0, aw, ah);\n\t\tctx.imageSmoothingEnabled = crop.w * sw > 10 * scale || crop.h * sh > 16 * scale;\n\t\tvar fu = scale;\n\t\tvar fv = scale;\n\t\tvar fw = FACE_W * scale;\n\t\tvar fh = FACE_H * scale;\n\t\tvar sx = crop.x * sw;\n\t\tvar sy = crop.y * sh;\n\t\tvar sWidth = crop.w * sw;\n\t\tvar sHeight = crop.h * sh;\n\t\tctx.drawImage(img, sx, sy, sWidth, sHeight, fu, fv, fw, fh);\n\t\tctx.drawImage(out, fu, fv, fw, fh, 12 * scale, fv, fw, fh);\n\t\tvar edge = ctx.getImageData(fu, fv, fw, fh);\n\t\tfor (var y = 0; y < fh; y++) {\n\t\t\tvar left = (y * fw) * 4;\n\t\t\tvar right = (y * fw + fw - 1) * 4;\n\t\t\tfor (var x = 0; x < scale; x++) {\n\t\t\t\tctx.fillStyle = \"rgb(\" + edge.data[left] + \",\" + edge.data[left + 1] + \",\" + edge.data[left + 2] + \")\";\n\t\t\t\tctx.fillRect(x, fv + y, 1, 1);\n\t\t\t\tctx.fillStyle = \"rgb(\" + edge.data[right] + \",\" + edge.data[right + 1] + \",\" + edge.data[right + 2] + \")\";\n\t\t\t\tctx.fillRect(11 * scale + x, fv + y, 1, 1);\n\t\t\t}\n\t\t}\n\t\tfor (var i = 0; i < fw; i++) {\n\t\t\tvar top = i * 4;\n\t\t\tvar bot = ((fh - 1) * fw + i) * 4;\n\t\t\tfor (var t = 0; t < scale; t++) {\n\t\t\t\tctx.fillStyle = \"rgb(\" + edge.data[top] + \",\" + edge.data[top + 1] + \",\" + edge.data[top + 2] + \")\";\n\t\t\t\tctx.fillRect(fu + i, t, 1, 1);\n\t\t\t\tctx.fillStyle = \"rgb(\" + edge.data[bot] + \",\" + edge.data[bot + 1] + \",\" + edge.data[bot + 2] + \")\";\n\t\t\t\tctx.fillRect(11 * scale + i, t, 1, 1);\n\t\t\t}\n\t\t}\n\t\treturn out;\n\t}\n\n\tfunction canvasPng(canvas) {\n\t\treturn new Promise(function (resolve, reject) {\n\t\t\tcanvas.toBlob(function (blob) {\n\t\t\t\tif (!blob) reject(new Error(\"Could not encode cape.\"));\n\t\t\t\telse resolve(blob);\n\t\t\t}, \"image/png\");\n\t\t});\n\t}\n\n\tfunction close() {\n\t\tif (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay);\n\t\toverlay = null;\n\t}\n\n\tfunction open(file, onDone, onCancel) {\n\t\tclose();\n\t\tloadImage(file).then(function (img) {\n\t\t\tif (isVanilla(img.width, img.height)) {\n\t\t\t\tonDone(file);\n\t\t\t\treturn;\n\t\t\t}\n\t\t\tvar crop = cover(img.width, img.height);\n\t\t\toverlay = document.createElement(\"div\");\n\t\t\toverlay.className = \"overlay center\";\n\t\t\toverlay.innerHTML = \"\"\n\t\t\t\t+ '<div class=\"sheet crop-sheet\">'\n\t\t\t\t+ \"<h1 style=\\\"font-size:16px\\\">CAPE CREATOR</h1>\"\n\t\t\t\t+ '<p class=\"who\">Drag to pan. Scroll or pinch to zoom. The box is the 10\u00d716 face other players see.</p>'\n\t\t\t\t+ '<div class=\"crop-wrap\">'\n\t\t\t\t+ '<div class=\"crop-stage\" id=\"crop-stage\"><div class=\"crop-holder\"><canvas id=\"crop-src\"></canvas><div id=\"crop-box\"></div></div></div>'\n\t\t\t\t+ '<div class=\"crop-side\"><div class=\"crop-face\"><canvas id=\"crop-face\"></canvas></div><p class=\"hint\">In-game face</p></div>'\n\t\t\t\t+ \"</div>\"\n\t\t\t\t+ '<div class=\"row\">'\n\t\t\t\t+ '<button type=\"button\" class=\"ghost\" id=\"crop-reset\">Reset</button>'\n\t\t\t\t+ '<button type=\"button\" class=\"ghost\" id=\"crop-cancel\">Cancel</button>'\n\t\t\t\t+ '<button type=\"button\" class=\"primary\" id=\"crop-apply\">Apply cape</button>'\n\t\t\t\t+ \"</div></div>\";\n\t\t\tdocument.body.appendChild(overlay);\n\t\t\tvar stage = overlay.querySelector(\"#crop-stage\");\n\t\t\tvar srcCanvas = overlay.querySelector(\"#crop-src\");\n\t\t\tvar faceCanvas = overlay.querySelector(\"#crop-face\");\n\t\t\tvar box = overlay.querySelector(\"#crop-box\");\n\t\t\tvar dragging = false;\n\t\t\tvar lastX = 0;\n\t\t\tvar lastY = 0;\n\n\t\t\tfunction layout() {\n\t\t\t\tvar maxW = Math.min(420, stage.clientWidth || 420);\n\t\t\t\tvar maxH = 280;\n\t\t\t\tvar fit = Math.min(maxW / img.width, maxH / img.height);\n\t\t\t\tsrcCanvas.width = Math.max(1, Math.round(img.width * fit));\n\t\t\t\tsrcCanvas.height = Math.max(1, Math.round(img.height * fit));\n\t\t\t\tvar sctx = srcCanvas.getContext(\"2d\");\n\t\t\t\tsctx.imageSmoothingEnabled = true;\n\t\t\t\tsctx.drawImage(img, 0, 0, srcCanvas.width, srcCanvas.height);\n\t\t\t\tbox.style.left = crop.x * srcCanvas.width + \"px\";\n\t\t\t\tbox.style.top = crop.y * srcCanvas.height + \"px\";\n\t\t\t\tbox.style.width = crop.w * srcCanvas.width + \"px\";\n\t\t\t\tbox.style.height = crop.h * srcCanvas.height + \"px\";\n\t\t\t\tfaceCanvas.width = 50;\n\t\t\t\tfaceCanvas.height = 80;\n\t\t\t\tvar fctx = faceCanvas.getContext(\"2d\");\n\t\t\t\tfctx.imageSmoothingEnabled = true;\n\t\t\t\tfctx.drawImage(\n\t\t\t\t\timg,\n\t\t\t\t\tcrop.x * img.width,\n\t\t\t\t\tcrop.y * img.height,\n\t\t\t\t\tcrop.w * img.width,\n\t\t\t\t\tcrop.h * img.height,\n\t\t\t\t\t0,\n\t\t\t\t\t0,\n\t\t\t\t\t50,\n\t\t\t\t\t80\n\t\t\t\t);\n\t\t\t}\n\n\t\t\toverlay.querySelector(\"#crop-reset\").onclick = function () {\n\t\t\t\tcrop = cover(img.width, img.height);\n\t\t\t\tlayout();\n\t\t\t};\n\t\t\toverlay.querySelector(\"#crop-cancel\").onclick = function () {\n\t\t\t\tclose();\n\t\t\t\tif (onCancel) onCancel();\n\t\t\t};\n\t\t\toverlay.querySelector(\"#crop-apply\").onclick = function () {\n\t\t\t\tcanvasPng(bakeAtlas(img, crop)).then(function (blob) {\n\t\t\t\t\tclose();\n\t\t\t\t\tonDone(blob);\n\t\t\t\t}).catch(function (error) {\n\t\t\t\t\talert(error.message);\n\t\t\t\t});\n\t\t\t};\n\t\t\toverlay.addEventListener(\"click\", function (event) {\n\t\t\t\tif (event.target === overlay) {\n\t\t\t\t\tclose();\n\t\t\t\t\tif (onCancel) onCancel();\n\t\t\t\t}\n\t\t\t});\n\t\t\tstage.addEventListener(\"pointerdown\", function (event) {\n\t\t\t\tdragging = true;\n\t\t\t\tlastX = event.clientX;\n\t\t\t\tlastY = event.clientY;\n\t\t\t\tstage.setPointerCapture(event.pointerId);\n\t\t\t});\n\t\t\tstage.addEventListener(\"pointerup\", function () { dragging = false; });\n\t\t\tstage.addEventListener(\"pointermove\", function (event) {\n\t\t\t\tif (!dragging) return;\n\t\t\t\tvar dx = (event.clientX - lastX) / srcCanvas.width;\n\t\t\t\tvar dy = (event.clientY - lastY) / srcCanvas.height;\n\t\t\t\tlastX = event.clientX;\n\t\t\t\tlastY = event.clientY;\n\t\t\t\tcrop.x += dx;\n\t\t\t\tcrop.y += dy;\n\t\t\t\tclampCrop(crop, img.width, img.height);\n\t\t\t\tlayout();\n\t\t\t});\n\t\t\tstage.addEventListener(\"wheel\", function (event) {\n\t\t\t\tevent.preventDefault();\n\t\t\t\tvar rect = srcCanvas.getBoundingClientRect();\n\t\t\t\tvar px = (event.clientX - rect.left) / srcCanvas.width;\n\t\t\t\tvar py = (event.clientY - rect.top) / srcCanvas.height;\n\t\t\t\tvar factor = event.deltaY < 0 ? 0.88 : 1.14;\n\t\t\t\tcrop.x = px - (px - crop.x) * factor;\n\t\t\t\tcrop.y = py - (py - crop.y) * factor;\n\t\t\t\tcrop.w *= factor;\n\t\t\t\tcrop.h *= factor;\n\t\t\t\tclampCrop(crop, img.width, img.height);\n\t\t\t\tlayout();\n\t\t\t}, { passive: false });\n\t\t\trequestAnimationFrame(layout);\n\t\t}).catch(function (error) {\n\t\t\tif (onCancel) onCancel();\n\t\t\talert(error.message);\n\t\t});\n\t}\n\n\treturn { open: open, isVanilla: isVanilla };\n})();\n";

const STORE_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>Stray</title>
	<meta name="description" content="A visuals oriented Hypixel Skyblock mod. Control-glass click GUI, ESP, HUD, mining, and custom capes.">
	<meta name="theme-color" content="#05070d">
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@500;600;700;800&display=swap" rel="stylesheet">
	<style>
		:root {
			--bg: #05070d;
			--text: #f2f4f7;
			--muted: #8a9aab;
			--header: #c4ced8;
			--accent: #e5e7eb;
			--accent-dim: #4a5564;
			--pane: #0b0e14;
			--card: #12151c;
			--line: #1c2430;
			--track: #1a222c;
			--off: #3d4a58;
			--pill: #4a5564;
			--warn: #f5c16c;
			--glass: rgba(255, 255, 255, 0.10);
			--glass-2: rgba(255, 255, 255, 0.16);
			--glass-line: rgba(255, 255, 255, 0.18);
			--ease: cubic-bezier(0.2, 0.8, 0.2, 1);
		}
		* { box-sizing: border-box; }
		html { color-scheme: dark; scroll-behavior: smooth; }
		html, body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.45 "Nunito Sans", system-ui, sans-serif; }
		::selection { background: color-mix(in srgb, var(--accent) 55%, #000); color: #fff; }
		body { min-height: 100vh; overflow-x: hidden; }
		#sky { position: fixed; inset: 0; z-index: 0; }
		.nebula, .orb, .grain {
			position: fixed; pointer-events: none; z-index: 1;
		}
		.nebula {
			inset: -20%;
			background:
				radial-gradient(900px 520px at 18% 12%, color-mix(in srgb, var(--accent) 22%, transparent), transparent 62%),
				radial-gradient(700px 480px at 88% 80%, color-mix(in srgb, var(--accent) 10%, transparent), transparent 58%),
				radial-gradient(500px 300px at 70% 18%, rgba(168, 139, 250, 0.08), transparent 70%);
			filter: blur(8px);
		}
		.orb {
			width: 560px; height: 560px; border-radius: 50%;
			left: 50%; top: 30%;
			background: radial-gradient(circle, color-mix(in srgb, var(--accent) 20%, transparent), transparent 68%);
			transform: translate(-50%, -50%);
			transition: background 0.3s ease;
		}
		.grain {
			inset: 0; opacity: 0.045; mix-blend-mode: overlay;
			background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.55'/%3E%3C/svg%3E");
		}
		.wrap { position: relative; z-index: 2; width: min(1180px, calc(100% - 40px)); margin: 0 auto; padding: 28px 0 72px; }
		.top {
			display: flex; align-items: center; justify-content: space-between; gap: 16px;
			padding: 8px 0 28px;
		}
		.mark { display: flex; flex-direction: column; gap: 7px; }
		.word {
			font-size: 13px; font-weight: 800; letter-spacing: 0.34em;
			color: var(--text);
		}
		.tick { width: 28px; height: 2px; border-radius: 2px; background: var(--accent); box-shadow: 0 0 18px var(--accent); }
		.top-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
		.ghost, .dl, .copy {
			display: inline-flex; align-items: center; justify-content: center;
			text-decoration: none; border: 0; cursor: pointer; font: inherit; font-weight: 800;
		}
		.ghost {
			color: var(--header); padding: 9px 14px; border-radius: 999px;
			background: rgba(255,255,255,0.06); border: 1px solid var(--glass-line);
			backdrop-filter: blur(16px);
		}
		.ghost:hover { background: rgba(255,255,255,0.10); color: var(--text); }
		.ghost svg { width: 14px; height: 14px; margin-right: 7px; fill: currentColor; }
		.dl {
			background: var(--accent); color: #061018; padding: 9px 16px; border-radius: 999px;
			box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent) 40%, transparent), 0 10px 30px color-mix(in srgb, var(--accent) 28%, transparent);
			transition: transform 0.18s var(--ease), filter 0.18s ease;
		}
		.dl:hover { transform: translateY(-1px); filter: brightness(1.08); }
		.dl.dead { pointer-events: none; opacity: 0.38; box-shadow: none; }
		.ver { color: var(--muted); font-size: 13px; font-weight: 700; letter-spacing: 0.08em; }
		.hero {
			display: grid; grid-template-columns: minmax(280px, 0.92fr) minmax(0, 1.18fr);
			gap: 36px; align-items: center; min-height: calc(100vh - 120px);
		}
		.kicker {
			font-size: 11px; font-weight: 800; letter-spacing: 0.22em; text-transform: uppercase;
			color: var(--accent);
		}
		h1 {
			margin: 10px 0 0; font-size: clamp(44px, 7vw, 76px); line-height: 0.92;
			letter-spacing: -0.05em; font-weight: 800;
		}
		.lede { margin: 18px 0 0; color: var(--muted); font-size: 18px; max-width: 42ch; }
		.cta { display: flex; align-items: center; gap: 12px; margin-top: 26px; flex-wrap: wrap; }
		.swatch-row { display: flex; gap: 8px; margin-top: 28px; flex-wrap: wrap; align-items: center; }
		.swatch-row em { font-style: normal; color: var(--muted); font-size: 12px; letter-spacing: 0.12em; text-transform: uppercase; font-weight: 800; margin-right: 4px; }
		.swatch {
			width: 18px; height: 18px; border-radius: 6px; border: 1px solid #0006; padding: 0; cursor: pointer;
			transition: transform 0.16s var(--ease);
		}
		.swatch:hover { transform: scale(1.12); }
		.swatch.on { outline: 2px solid var(--text); outline-offset: 2px; }
		.mode {
			display: inline-flex; padding: 3px; border-radius: 999px;
			background: rgba(255,255,255,0.06); border: 1px solid var(--glass-line);
		}
		.mode button {
			border: 0; background: transparent; color: var(--muted); font: 800 12px/1 "Nunito Sans", sans-serif;
			letter-spacing: 0.08em; text-transform: uppercase; padding: 8px 12px; border-radius: 999px; cursor: pointer;
		}
		.mode button.on { background: rgba(255,255,255,0.16); color: var(--text); }
		.stage { position: relative; min-height: 560px; }
		.gui {
			width: 760px; height: 560px; max-width: 100%;
			display: grid; grid-template-columns: 88px 1fr;
			border-radius: 24px; overflow: hidden; user-select: none;
			background: linear-gradient(180deg, rgba(255,255,255,0.16), rgba(255,255,255,0.06));
			box-shadow:
				0 30px 80px #000a,
				inset 0 1px 0 rgba(255,255,255,0.34),
				inset 0 -1px 0 rgba(255,255,255,0.06);
			backdrop-filter: blur(28px) saturate(1.35);
			opacity: 0; transform: scale(0.94) translateY(18px);
			transform-origin: center;
			transition: transform 0.5s var(--ease), opacity 0.36s ease, background 0.25s ease;
		}
		.gui.in { opacity: 1; transform: none; }
		body.classic .gui {
			grid-template-columns: 168px 1fr;
			border-radius: 12px;
			background: var(--pane);
			box-shadow: 0 28px 70px #0009, inset 0 0 0 1px var(--line);
			backdrop-filter: none;
		}
		body.ctrl .classic-only { display: none !important; }
		body.classic .ctrl-only { display: none !important; }
		.rail {
			position: relative; display: flex; flex-direction: column; align-items: center;
			padding: 10px 8px 8px; gap: 0;
		}
		body.classic .rail { align-items: stretch; padding: 16px 0 10px; background: #121820; }
		body.classic .rail::after {
			content: ""; position: absolute; top: 0; right: 0; width: 2px; height: 100%;
			background: color-mix(in srgb, var(--accent) 38%, transparent);
		}
		.rail-pill {
			position: absolute; left: 10px; width: calc(100% - 20px); height: 48px; border-radius: 14px;
			background: rgba(255,255,255,0.16);
			transition: top 0.22s var(--ease), height 0.22s var(--ease), opacity 0.16s ease;
			pointer-events: none; z-index: 0;
		}
		body.classic .rail-pill {
			left: 12px; width: calc(100% - 24px); height: 32px; border-radius: 8px; background: var(--pill);
		}
		.rail-pill.hide { opacity: 0; }
		.brand-mini {
			position: relative; z-index: 1; font-size: 9px; font-weight: 800; letter-spacing: 0.18em;
			color: var(--header); padding: 4px 0 10px; display: none;
		}
		body.classic .brand-mini { display: flex; align-items: baseline; gap: 6px; padding: 0 18px 0; font-size: 14px; letter-spacing: 0.04em; color: var(--text); }
		body.classic .brand-mini b { color: var(--accent); font-size: 10px; letter-spacing: 0; font-weight: 700; }
		body.classic .rail-tick { display: block; }
		.rail-tick { display: none; width: 32px; height: 3px; margin: 7px 18px 10px; background: var(--accent); border-radius: 2px; }
		.grp { display: none; }
		body.classic .grp { display: block; padding: 8px 20px 2px; font-size: 10px; letter-spacing: 0.1em; color: var(--header); font-weight: 700; }
		.tab {
			position: relative; z-index: 1; width: 72px; height: 48px; border: 0; background: transparent;
			color: rgba(245,245,247,0.62); cursor: pointer; border-radius: 14px;
			display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px;
			font: 800 8px/1 "Nunito Sans", sans-serif; letter-spacing: 0.04em;
		}
		.tab svg { width: 16px; height: 16px; fill: currentColor; }
		.tab.on { color: #f5f5f7; }
		.tab:hover:not(.on) { color: #fff; }
		body.classic .tab {
			width: calc(100% - 24px); height: 32px; margin: 1px 12px; padding: 0 12px;
			flex-direction: row; justify-content: flex-start; gap: 10px;
			font: 700 15px/1 "Nunito Sans", sans-serif; color: var(--muted); border-radius: 8px;
		}
		body.classic .tab svg { fill: var(--accent); }
		body.classic .tab.on { color: var(--text); }
		body.classic .tab.on svg { fill: var(--text); }
		body.classic .tab:hover:not(.on) { background: #ffffff14; }
		.you {
			margin-top: auto; position: relative; z-index: 1; width: auto; padding: 12px 18px 4px;
			display: flex; flex-direction: row; align-items: center; justify-content: flex-start; gap: 8px;
			cursor: pointer; color: var(--header);
			font: 700 15px/1 "Nunito Sans", sans-serif;
			border-top: 2px solid var(--accent);
		}
		.you.on { color: var(--text); }
		.face, .head-face {
			width: 22px; height: 22px; border-radius: 4px; flex: 0 0 auto;
			background: linear-gradient(#d7b08a, #8a6240); box-shadow: inset 0 -8px 0 #6d4a2e;
		}
		.head-face {
			width: 22px; height: 22px; border: 0; padding: 0; cursor: pointer; border-radius: 7px;
		}
		.head-face.on { outline: 2px solid var(--accent); outline-offset: 1px; }
		.recycle {
			margin-top: auto; position: relative; z-index: 1; padding: 6px 0 2px;
			font: 800 11px/1.2 "Nunito Sans", sans-serif; color: var(--accent); text-align: center;
			letter-spacing: 0.02em; white-space: pre-line;
		}
		.recycle b { display: block; font-size: 9px; font-weight: 800; margin-top: 2px; }
		.main { position: relative; display: flex; flex-direction: column; min-width: 0; }
		.head {
			height: 44px; display: flex; align-items: center; gap: 8px; padding: 0 14px;
		}
		.head h2 {
			margin: 0; font-size: 15px; font-weight: 800; letter-spacing: 0.02em;
			position: relative; padding-bottom: 4px;
		}
		body.ctrl .head h2 { display: none; }
		.head h2::after {
			content: ""; position: absolute; left: 0; bottom: 0; width: 100%; height: 2px;
			background: var(--accent); border-radius: 2px; transform-origin: left;
			animation: under 0.28s var(--ease);
		}
		@keyframes under { from { transform: scaleX(0); } to { transform: scaleX(1); } }
		.head-tabs { display: flex; align-items: center; gap: 12px; min-width: 0; }
		.head-tab {
			border: 0; background: transparent; color: rgba(245,245,247,0.55); cursor: pointer;
			font: 800 13px/1 "Nunito Sans", sans-serif; padding: 0 1px 7px; position: relative;
		}
		.head-tab.on { color: #f5f5f7; }
		.head-tab.on::after {
			content: ""; position: absolute; left: 0; bottom: 0; width: 100%; height: 1.5px;
			background: currentColor; border-radius: 1px;
		}
		.search {
			margin-left: auto; height: 28px; width: 110px; border-radius: 999px; border: 0;
			background: rgba(0,0,0,0.18); color: var(--text); padding: 0 12px;
			font: 600 12px/1 "Nunito Sans", sans-serif;
		}
		body.classic .search { background: var(--card); width: 148px; }
		.iconbtn {
			width: 26px; height: 26px; border: 0; background: transparent; color: rgba(245,245,247,0.7);
			padding: 0; cursor: pointer; display: grid; place-items: center; flex: 0 0 auto;
		}
		.iconbtn:hover, .iconbtn.on { color: var(--accent); }
		.iconbtn svg { width: 15px; height: 15px; fill: currentColor; }
		.pane { position: relative; flex: 1; overflow: hidden; }
		#pane-stars { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; display: none; }
		body.classic #pane-stars { display: block; }
		.cols {
			position: absolute; inset: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 8px;
			padding: 4px 12px 12px; align-content: start; align-items: start;
			opacity: 0; transform: translateY(12px); pointer-events: none;
			transition: opacity 0.2s ease, transform 0.24s var(--ease);
			overflow: auto;
		}
		.cols.on { opacity: 1; transform: none; pointer-events: auto; }
		.cols.one { grid-template-columns: 1fr; }
		.stack { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
		.card {
			background: rgba(255,255,255,0.12); border-radius: 16px; padding: 10px 12px 8px;
			box-shadow: inset 0 1px 0 rgba(255,255,255,0.22);
		}
		body.classic .card { background: var(--card); border-radius: 12px; box-shadow: none; }
		.card h3 {
			margin: 0 0 6px; padding-bottom: 6px; border-bottom: 1px solid rgba(255,255,255,0.12);
			font-size: 10px; letter-spacing: 0.1em; color: var(--header); font-weight: 800;
		}
		body.classic .card h3 { border-bottom-color: var(--line); }
		.row { display: flex; align-items: center; justify-content: space-between; min-height: 26px; gap: 8px; }
		.row span { font-size: 13px; font-weight: 700; }
		.row em, .val { font-style: normal; font-size: 12px; color: var(--muted); font-weight: 700; }
		.val { color: var(--accent); }
		.hint { font-size: 11px; color: var(--muted); padding: 2px 0 6px; }
		.dot {
			width: 14px; height: 14px; border-radius: 4px; flex: 0 0 auto;
			background: var(--accent); border: 1px solid #0005;
		}
		.chips { display: flex; flex-wrap: wrap; gap: 6px; padding: 4px 0 6px; }
		.chip {
			border: 0; background: rgba(255,255,255,0.10); color: var(--muted); cursor: pointer;
			font: 800 10px/1 "Nunito Sans", sans-serif; letter-spacing: 0.04em; text-transform: uppercase;
			padding: 6px 8px; border-radius: 8px;
		}
		.chip.on { background: var(--accent); color: #061018; }
		.slide { display: flex; align-items: center; gap: 8px; min-height: 24px; }
		.slide span { font-size: 13px; font-weight: 700; min-width: 58px; }
		.slide em { margin-left: auto; font-style: normal; font-size: 11px; color: var(--muted); font-weight: 700; }
		.bar {
			flex: 1; height: 4px; border-radius: 99px; background: rgba(255,255,255,0.16); position: relative;
		}
		.bar i {
			position: absolute; top: -3px; width: 10px; height: 10px; border-radius: 50%;
			background: var(--accent); box-shadow: 0 0 8px var(--accent);
		}
		.bind {
			font: 800 11px/1 "Nunito Sans", sans-serif; color: var(--accent); letter-spacing: 0.02em;
			padding: 5px 8px; border-radius: 6px; background: color-mix(in srgb, var(--accent) 16%, transparent);
			cursor: pointer; border: 0;
		}
		.tog {
			width: 42px; height: 24px; border: 0; padding: 0; border-radius: 99px;
			background: rgba(255,255,255,0.28); position: relative; cursor: pointer; flex: 0 0 auto;
		}
		.tog::after {
			content: ""; position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%;
			background: #fff; box-shadow: 0 1px 4px #0005;
			transition: left 0.18s var(--ease), background 0.18s ease;
		}
		.tog.on { background: var(--accent); }
		.tog.on::after { left: 21px; }
		body.classic .tog { width: 44px; height: 22px; background: var(--track); }
		body.classic .tog::after { width: 16px; height: 16px; background: var(--off); box-shadow: none; }
		body.classic .tog.on::after { left: 25px; background: #061018; }
		.list { display: flex; flex-direction: column; gap: 2px; }
		.list button {
			border: 0; background: transparent; color: rgba(245,245,247,0.7);
			font: 700 14px/1.4 "Nunito Sans", sans-serif; text-align: left; padding: 5px 8px; border-radius: 8px; cursor: pointer;
		}
		.list button.on, .list button:hover { background: rgba(255,255,255,0.10); color: #fff; }
		.sheet {
			position: absolute; top: 8px; right: 14px; width: 188px; z-index: 3;
			background: rgba(12,16,24,0.72); border: 1px solid var(--glass-line); border-radius: 18px; padding: 12px;
			backdrop-filter: blur(18px);
			opacity: 0; transform: translateX(10px); pointer-events: none;
			transition: opacity 0.18s ease, transform 0.22s var(--ease);
		}
		.sheet.on { opacity: 1; transform: none; pointer-events: auto; }
		.sheet h3 { margin: 0 0 10px; font-size: 11px; letter-spacing: 0.08em; color: var(--header); }
		.swatches, #hero-swatches { display: flex; flex-wrap: wrap; gap: 8px; }
		.nick, .field {
			width: 100%; background: rgba(0,0,0,0.22); border: 1px solid rgba(255,255,255,0.12);
			color: var(--text); font: 700 13px "Nunito Sans", sans-serif; padding: 7px 10px; border-radius: 8px;
		}
		.skin { width: 72px; height: 96px; margin: 6px auto 8px; background: linear-gradient(#c2a27a, #8a6a4a); border-radius: 10px; }
		.filebtn {
			width: 100%; text-align: left; border: 1px solid rgba(255,255,255,0.12);
			background: rgba(0,0,0,0.18); color: var(--text); font: 700 13px "Nunito Sans", sans-serif;
			padding: 7px 10px; border-radius: 8px; cursor: pointer; margin-top: 4px;
		}
		.filebtn:hover { background: rgba(255,255,255,0.08); }
		.watermark {
			position: absolute; left: 18px; top: 12px; font-size: 11px; font-weight: 800; letter-spacing: 0.18em;
			color: color-mix(in srgb, var(--accent) 80%, #fff); opacity: 0.9; pointer-events: none;
		}
		.band { margin-top: 64px; }
		.band h2 { margin: 0 0 6px; font-size: 13px; letter-spacing: 0.18em; text-transform: uppercase; color: var(--header); }
		.band p { margin: 0 0 18px; color: var(--muted); }
		.feats {
			display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px;
		}
		.feat {
			background: rgba(255,255,255,0.06); border: 1px solid var(--glass-line); border-radius: 18px;
			padding: 16px 16px 14px; backdrop-filter: blur(18px);
			box-shadow: inset 0 1px 0 rgba(255,255,255,0.12);
			transition: transform 0.2s var(--ease), border-color 0.2s ease, background 0.2s ease;
		}
		.feat:hover {
			transform: translateY(-3px);
			background: rgba(255,255,255,0.09);
			border-color: color-mix(in srgb, var(--accent) 45%, transparent);
		}
		.feat svg { width: 18px; height: 18px; fill: var(--accent); }
		.feat b { display: block; margin: 10px 0 4px; font-size: 14px; letter-spacing: 0.04em; }
		.feat span { color: var(--muted); font-size: 13px; line-height: 1.4; }
		.cape {
			margin-top: 22px; display: grid; grid-template-columns: 1.2fr 0.8fr; gap: 12px;
		}
		.glass {
			background: rgba(255,255,255,0.06); border: 1px solid var(--glass-line); border-radius: 22px;
			padding: 22px 22px 20px; backdrop-filter: blur(18px);
			box-shadow: inset 0 1px 0 rgba(255,255,255,0.12);
		}
		.glass h2 { margin: 0 0 8px; font-size: 18px; }
		.who { display: flex; align-items: center; gap: 8px; margin: 10px 0 8px; }
		.handle { font-family: ui-monospace, Consolas, monospace; font-size: 16px; font-weight: 700; }
		.copy {
			border: 1px solid var(--glass-line); background: rgba(0,0,0,0.18); color: var(--muted);
			font-size: 12px; padding: 6px 10px; border-radius: 999px;
		}
		.copy:hover { color: var(--text); }
		.glass p { margin: 0; color: var(--muted); font-size: 14px; }
		.statline { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
		.statline div { padding: 8px 0; }
		.statline b { display: block; font-size: 22px; color: var(--accent); letter-spacing: -0.03em; }
		.statline span { color: var(--muted); font-size: 11px; letter-spacing: 0.12em; text-transform: uppercase; font-weight: 800; }
		.foot { margin-top: 28px; color: #5d6470; font-size: 13px; letter-spacing: 0.08em; display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
		.foot a { color: inherit; text-decoration: none; }
		.foot a:hover { color: var(--accent); }
		@media (max-width: 1020px) {
			.hero, .cape, .feats { grid-template-columns: 1fr; }
			.stage { min-height: 0; overflow: hidden; }
			.gui { width: 760px; transform: scale(0.68); transform-origin: top left; }
			.gui.in { transform: scale(0.68); }
			.stage { height: calc(560px * 0.68); }
			.hero { min-height: 0; padding-bottom: 12px; }
		}
		@media (max-width: 720px) {
			.wrap { width: calc(100% - 24px); }
			.gui { transform: scale(0.48); }
			.gui.in { transform: scale(0.48); }
			.stage { height: calc(560px * 0.48); }
			h1 { font-size: 42px; }
		}
	</style>
</head>
<body class="ctrl">
	<canvas id="sky"></canvas>
	<div class="nebula"></div>
	<div class="orb" id="orb"></div>
	<div class="grain"></div>
	<div class="wrap">
		<header class="top">
			<div class="mark">
				<div class="word">STRAY</div>
				<div class="tick"></div>
			</div>
			<div class="top-actions">
				<a class="ghost" href="https://github.com/camberX/Stray" target="_blank" rel="noopener noreferrer" aria-label="GitHub"><svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>GitHub</a>
				<a class="dl" id="mod-download" href="/download">Download</a>
				<span class="ver" id="mod-ver"></span>
			</div>
		</header>

		<section class="hero">
			<div>
				<div class="kicker">Hypixel Skyblock client</div>
				<h1>Stray</h1>
				<p class="lede">A Visuals oriented mod for Hypixel Skyblock</p>
				<div class="cta">
					<a class="dl" id="mod-download-2" href="/download">Get the jar</a>
					<a class="ghost" href="https://github.com/camberX/Stray" target="_blank" rel="noopener noreferrer">GitHub</a>
					<a class="ghost" href="#features">Browse modules</a>
				</div>
				<div class="swatch-row">
					<em>Accent</em>
					<div id="hero-swatches"></div>
				</div>
			</div>
			<div class="stage">
				<div class="gui in" id="menu">
					<aside class="rail">
						<div class="rail-pill" id="nav-pill"></div>
						<div class="brand-mini classic-only">STRAY<b id="menu-ver"></b></div>
						<div class="rail-tick classic-only"></div>
						<button type="button" class="tab ctrl-only on" data-group="world" data-tab="world"><svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm6.9 9h-3.2a15 15 0 0 0-1.4-6 8 8 0 0 1 4.6 6zM12 4c.8 1.3 1.5 3.4 1.8 6H10.2C10.5 7.4 11.2 5.3 12 4zM4.1 13h3.2c.2 2.2.7 4.2 1.4 6A8 8 0 0 1 4.1 13zM8.7 11H5.1A8 8 0 0 1 9.7 5a15 15 0 0 0-1 6zm1.5 2h3.6c-.3 2.6-1 4.7-1.8 6-.8-1.3-1.5-3.4-1.8-6zm5.1 6c.7-1.8 1.2-3.8 1.4-6h3.2a8 8 0 0 1-4.6 6z"/></svg>World</button>
						<button type="button" class="tab ctrl-only" data-group="combat" data-tab="combat"><svg viewBox="0 0 24 24"><path d="M6.5 2 4 6l2 2-6 6 4 4 6-6 2 2 4-2.5L14 8l2-2-1-3-3 1-2-2zM16 14l6 6-2 2-6-6z"/></svg>Combat</button>
						<button type="button" class="tab ctrl-only" data-group="visuals" data-tab="visuals"><svg viewBox="0 0 24 24"><path d="M12 5C7 5 2.7 8.1 1 12c1.7 3.9 6 7 11 7s9.3-3.1 11-7c-1.7-3.9-6-7-11-7zm0 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/></svg>Visuals</button>
						<button type="button" class="tab ctrl-only" data-group="hud" data-tab="overlay"><svg viewBox="0 0 24 24"><path d="M3 5h18v10H3zm2 2v6h14V7zM8 17h8v2H8z"/></svg>HUD</button>
						<button type="button" class="tab ctrl-only" data-group="mining" data-tab="mining"><svg viewBox="0 0 24 24"><path d="M12 2 4 7v10l8 5 8-5V7zm0 2.2 5.8 3.6L12 11.6 6.2 7.8zm-6 5.2 5 3.1v6.3L6 16.3zm8 9.4v-6.3l5-3.1v6.3z"/></svg>Mining</button>
						<button type="button" class="tab ctrl-only" data-group="farming" data-tab="farming"><svg viewBox="0 0 24 24"><path d="M12 2 4 7l8 5 8-5zM4 12l8 5 8-5M4 17l8 5 8-5"/></svg>Farming</button>
						<button type="button" class="tab ctrl-only" data-group="misc" data-tab="menus"><svg viewBox="0 0 24 24"><path d="M4 5h16v3H4zm0 5.5h16v3H4zM4 16h16v3H4z"/></svg>Misc</button>
						<button type="button" class="tab ctrl-only" data-group="theme" data-tab="theme"><svg viewBox="0 0 24 24"><path d="M12 3a9 9 0 1 0 9 9c0-.3 0-.6-.1-.9A6 6 0 0 1 12 3z"/></svg>Theme</button>
						<button type="button" class="tab classic-only on" data-tab="world"><svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z"/></svg>World</button>
						<button type="button" class="tab classic-only" data-tab="combat"><svg viewBox="0 0 24 24"><path d="M6.5 2 4 6l2 2-6 6 4 4 6-6 2 2 4-2.5L14 8l2-2z"/></svg>Combat</button>
						<button type="button" class="tab classic-only" data-tab="visuals"><svg viewBox="0 0 24 24"><path d="M12 5C7 5 2.7 8.1 1 12c1.7 3.9 6 7 11 7s9.3-3.1 11-7c-1.7-3.9-6-7-11-7z"/></svg>Visuals</button>
						<div class="grp classic-only">HUD</div>
						<button type="button" class="tab classic-only" data-tab="overlay"><svg viewBox="0 0 24 24"><path d="M3 5h18v10H3zM8 17h8v2H8z"/></svg>Overlay</button>
						<button type="button" class="tab classic-only" data-tab="bars"><svg viewBox="0 0 24 24"><path d="M4 18h3V9H4zm6.5 0h3V4h-3zM17 18h3v-7h-3z"/></svg>Bars</button>
						<div class="grp classic-only">MINING</div>
						<button type="button" class="tab classic-only" data-tab="mining"><svg viewBox="0 0 24 24"><path d="M12 2 4 7v10l8 5 8-5V7z"/></svg>Mining</button>
						<button type="button" class="tab classic-only" data-tab="nodes"><svg viewBox="0 0 24 24"><path d="M12 3 4 7.5v9L12 21l8-4.5v-9z"/></svg>Nodes</button>
						<button type="button" class="tab classic-only" data-tab="farming"><svg viewBox="0 0 24 24"><path d="M12 2 4 7l8 5 8-5zM4 12l8 5 8-5"/></svg>Farming</button>
						<div class="grp classic-only">MISC</div>
						<button type="button" class="tab classic-only" data-tab="menus"><svg viewBox="0 0 24 24"><path d="M4 6h16v12H4zM8 3h8v3H8z"/></svg>Menus</button>
						<button type="button" class="tab classic-only" data-tab="status"><svg viewBox="0 0 24 24"><path d="M4 18h16v2H4zM6 10h3v7H6zm5-5h3v12h-3zm5 8h3v4h-3z"/></svg>Status</button>
						<div class="you classic-only" id="you" data-tab="player">
							<div class="face"></div>
							<span>You</span>
						</div>
						<div class="recycle ctrl-only" id="rail-ver">♲<b id="rail-ver-num"></b></div>
					</aside>
					<section class="main">
						<div class="head">
							<h2 id="bar-title">World</h2>
							<nav class="head-tabs ctrl-only" id="head-tabs"></nav>
							<input class="search" placeholder="Search" spellcheck="false">
							<button type="button" class="iconbtn ctrl-only" title="Notes"><svg viewBox="0 0 24 24"><path d="M12 4a6 6 0 0 0-6 6v3.2L4 16h16l-2-2.8V10a6 6 0 0 0-6-6zm-2 16h4a2 2 0 0 1-4 0z"/></svg></button>
							<button type="button" class="iconbtn ctrl-only" title="HUD editor"><svg viewBox="0 0 24 24"><path d="M4 6h16v2H4zm0 5h10v2H4zm0 5h16v2H4z"/></svg></button>
							<button type="button" class="iconbtn classic-only" id="theme-btn" title="Theme"><svg viewBox="0 0 24 24"><path d="M12 3a9 9 0 1 0 9 9c0-.3 0-.6-.1-.9A6 6 0 0 1 12 3z"/></svg></button>
							<button type="button" class="head-face ctrl-only" id="head-face" title="Player"></button>
						</div>
						<div class="pane">
							<canvas id="pane-stars"></canvas>
							<div class="watermark" id="wm" hidden>STRAY</div>
							<div class="cols on" data-panel="world">
								<div class="stack">
									<div class="card">
										<h3>WORLD TINT</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Mode</span><em class="val">Lightmap</em></div>
										<div class="row"><span>Color</span><span class="dot"></span></div>
										<div class="slide"><span>Strength</span><div class="bar"><i style="left:70%"></i></div><em>70</em></div>
									</div>
									<div class="card">
										<h3>SKYBOX</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Match world</span><button type="button" class="tog on"></button></div>
										<div class="slide"><span>Strength</span><div class="bar"><i style="left:55%"></i></div><em>55</em></div>
									</div>
								</div>
								<div class="stack">
									<div class="card">
										<h3>FOG</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Match world</span><button type="button" class="tog on"></button></div>
										<div class="slide"><span>Start</span><div class="bar"><i style="left:20%"></i></div><em>20%</em></div>
										<div class="slide"><span>End</span><div class="bar"><i style="left:80%"></i></div><em>80%</em></div>
									</div>
									<div class="card">
										<h3>ASPECT RATIO</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog"></button></div>
										<div class="slide"><span>Aspect</span><div class="bar"><i style="left:71%"></i></div><em>Native</em></div>
										<div class="chips">
											<button type="button" class="chip on">Native</button>
											<button type="button" class="chip">16:10</button>
											<button type="button" class="chip">4:3</button>
											<button type="button" class="chip">5:4</button>
										</div>
									</div>
								</div>
							</div>
							<div class="cols" data-panel="combat">
								<div class="card">
									<h3>HITSOUND</h3>
									<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Melee</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Arrows</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Hitmarker</span><button type="button" class="tog on"></button></div>
									<div class="slide"><span>Volume</span><div class="bar"><i style="left:80%"></i></div><em>80%</em></div>
									<div class="slide"><span>Pitch</span><div class="bar"><i style="left:50%"></i></div><em>1.00</em></div>
								</div>
								<div class="stack">
									<div class="card">
										<h3>TRIGGERBOT</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Players</span><button type="button" class="tog on"></button></div>
										<div class="slide"><span>Humanize</span><div class="bar"><i style="left:35%"></i></div><em>35%</em></div>
									</div>
									<div class="card">
										<h3>AUTO CLICKER</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Whitelist only</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Terminator only</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Left</span><button type="button" class="bind">[ None ]</button></div>
										<div class="row"><span>Right</span><button type="button" class="bind">[ Button 5 ]</button></div>
									</div>
								</div>
							</div>
							<div class="cols" data-panel="visuals">
								<div class="stack">
									<div class="card">
										<h3>MOB GLOW</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Through walls</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Color</span><span class="dot"></span></div>
									</div>
									<div class="card">
										<h3>BLOCK OUTLINE</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Color</span><span class="dot"></span></div>
									</div>
									<div class="card">
										<h3>CHEST ESP</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Tracers</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Chest Aim</span><button type="button" class="bind">[ None ]</button></div>
									</div>
									<div class="card">
										<h3>HELD ITEM</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Style</span><em class="val">Glow</em></div>
										<div class="row"><span>Color</span><span class="dot"></span></div>
									</div>
								</div>
								<div class="stack">
									<div class="card">
										<h3>PLAYER FILL</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
									</div>
									<div class="card">
										<h3>NAMETAGS</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Own nametag</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Through walls</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Show distance</span><button type="button" class="tog"></button></div>
									</div>
									<div class="card">
										<h3>MOBS</h3>
										<input class="field" placeholder="Search mobs..." spellcheck="false">
										<div class="list">
											<button type="button" class="on">Player</button>
											<button type="button">Zombie</button>
											<button type="button">Enderman</button>
											<button type="button">Blaze</button>
											<button type="button">Creeper</button>
										</div>
									</div>
								</div>
							</div>
							<div class="cols" data-panel="overlay">
								<div class="stack">
									<div class="card">
										<h3>WATERMARK</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>FPS</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Ping</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Clock</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Name</span><button type="button" class="tog"></button></div>
									</div>
									<div class="card">
										<h3>RAW MATS</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Materials</span><em class="val">Compact</em></div>
									</div>
									<div class="card">
										<h3>PICKUP LOG</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog"></button></div>
									</div>
								</div>
								<div class="stack">
									<div class="card">
										<h3>MUSIC</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Hide when idle</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Spotify</span><button type="button" class="tog on"></button></div>
									</div>
									<div class="card">
										<h3>INVENTORY HUD</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Hotbar</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Armor</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Item count</span><button type="button" class="tog on"></button></div>
									</div>
								</div>
							</div>
							<div class="cols" data-panel="bars">
								<div class="card">
									<h3>BARS</h3>
									<div class="row"><span>Hotbar</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Health</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Hunger</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Armor</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Air</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Experience</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Mount health</span><button type="button" class="tog on"></button></div>
								</div>
								<div class="card">
									<h3>INFO</h3>
									<div class="row"><span>Scoreboard</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Boss bar</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Effects</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Held item</span><button type="button" class="tog on"></button></div>
									<p class="hint">Move and scale each piece from the toolbar HUD editor.</p>
								</div>
							</div>
							<div class="cols" data-panel="mining">
								<div class="stack">
									<div class="card">
										<h3>MINING HUD</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Ability alert</span><button type="button" class="tog on"></button></div>
									</div>
									<div class="card">
										<h3>TITANIUM ESP</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog"></button></div>
										<div class="row"><span>Through walls</span><button type="button" class="tog on"></button></div>
										<div class="slide"><span>Range</span><div class="bar"><i style="left:40%"></i></div><em>48m</em></div>
									</div>
								</div>
								<div class="card">
									<h3>LIVE</h3>
									<div class="row"><span>Pickobulus</span><em class="val">Ready</em></div>
									<div class="row"><span>Commissions</span><em>2 commissions</em></div>
									<div class="row"><span>Titanium</span><em>No titanium job</em></div>
								</div>
							</div>
							<div class="cols" data-panel="nodes">
								<div class="card">
									<h3>MARKERS</h3>
									<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Node HUD</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Only in The End</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Force enable</span><button type="button" class="tog"></button></div>
									<div class="slide"><span>Scan radius</span><div class="bar"><i style="left:50%"></i></div><em>48m</em></div>
									<div class="row"><span>Particle hints</span><button type="button" class="tog"></button></div>
								</div>
								<div class="card">
									<h3>NODE ESP</h3>
									<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Outline</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Tracer</span><button type="button" class="tog"></button></div>
									<div class="row"><span>Through walls</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Color</span><span class="dot"></span></div>
								</div>
							</div>
							<div class="cols" data-panel="farming">
								<div class="stack">
									<div class="card">
										<h3>YAW / PITCH</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
										<div class="slide"><span>Scale</span><div class="bar"><i style="left:33%"></i></div><em>100%</em></div>
									</div>
									<div class="card">
										<h3>JACOB CONTEST HUD</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog on"></button></div>
									</div>
								</div>
								<div class="card">
									<h3>CONTEST</h3>
									<p class="hint">No active Jacob contest</p>
									<p class="hint">Reads the live player-list widget</p>
								</div>
							</div>
							<div class="cols" data-panel="menus">
								<div class="stack">
									<div class="card">
										<h3>MENUS</h3>
										<div class="row"><span>Loadouts menu</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Wardrobe menu</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Open animation</span><button type="button" class="tog on"></button></div>
									</div>
									<div class="card">
										<h3>AUTO EXPERIMENTS</h3>
										<div class="row"><span>Enable</span><button type="button" class="tog"></button></div>
										<div class="slide"><span>Click delay</span><div class="bar"><i style="left:15%"></i></div><em>250ms</em></div>
										<div class="row"><span>Auto close</span><button type="button" class="tog on"></button></div>
										<div class="row"><span>Get max XP</span><button type="button" class="tog"></button></div>
									</div>
								</div>
								<div class="stack">
									<div class="card">
										<h3>KEYBINDS</h3>
										<div class="row"><span>Open menu</span><button type="button" class="bind">[ RShift ]</button></div>
										<div class="row"><span>Loadouts</span><button type="button" class="bind">[ None ]</button></div>
										<div class="row"><span>Wardrobe</span><button type="button" class="bind">[ None ]</button></div>
									</div>
									<div class="card">
										<h3>COMMANDS</h3>
										<p class="hint">/loadouts  /ld</p>
										<p class="hint">/wardrobe  /wd</p>
										<p class="hint">/autoclicker add left</p>
										<p class="hint">1-9 equips and closes</p>
									</div>
								</div>
							</div>
							<div class="cols" data-panel="status">
								<div class="card">
									<h3>LOCATION</h3>
									<div class="row"><span>Hypixel</span><em class="val">ON</em></div>
									<div class="row"><span>Skyblock</span><em class="val">ON</em></div>
									<div class="row"><span>The End</span><em>OFF</em></div>
									<p class="hint">Hub</p>
								</div>
								<div class="card">
									<h3>CLIENT</h3>
									<div class="row"><span>FPS</span><em>144</em></div>
									<div class="row"><span>Ping</span><em>32ms</em></div>
								</div>
							</div>
							<div class="cols" data-panel="player">
								<div class="card">
									<h3>YOU</h3>
									<div class="skin"></div>
									<p class="hint">Drag to rotate</p>
								</div>
								<div class="stack">
									<div class="card">
										<h3>NICK</h3>
										<div class="row"><span>Replace my name</span><button type="button" class="tog"></button></div>
										<p class="hint">Chat, tab, scoreboard. Use &amp;6 &amp;l.</p>
										<input class="nick" value="You" maxlength="16" spellcheck="false">
									</div>
									<div class="card">
										<h3>CAPE</h3>
										<p class="hint">Message @evilkitten911 to unlock</p>
										<input class="field" placeholder="https://...png" spellcheck="false">
										<button type="button" class="filebtn">Local file...</button>
										<button type="button" class="filebtn">Create cape...</button>
										<button type="button" class="filebtn">Remove cape</button>
									</div>
								</div>
							</div>
							<div class="cols" data-panel="theme">
								<div class="card">
									<h3>CONTROL</h3>
									<div class="row"><span>GUI</span><em class="val" id="gui-label">Control</em></div>
									<div class="row"><span>Glass</span><span class="dot"></span></div>
									<div class="row"><span>Pills</span><span class="dot"></span></div>
									<div class="slide"><span>Frost</span><div class="bar"><i style="left:70%"></i></div><em>70%</em></div>
									<div class="row"><span>Menu stars</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Animations</span><button type="button" class="tog on"></button></div>
									<div class="row"><span>Auto update</span><button type="button" class="tog on"></button></div>
								</div>
								<div class="stack">
									<div class="card">
										<h3>ACCENT</h3>
										<p class="hint">Preset</p>
										<div class="swatches" id="swatches"></div>
										<div class="row"><span>Custom</span><span class="dot"></span></div>
										<div class="slide"><span>HUD</span><div class="bar"><i style="left:80%"></i></div><em>80%</em></div>
										<div class="row"><span>HUD stars</span><button type="button" class="tog"></button></div>
									</div>
									<div class="card">
										<h3>SCALE</h3>
										<p class="hint">Menu</p>
										<div class="chips">
											<button type="button" class="chip on">100%</button>
											<button type="button" class="chip">90%</button>
											<button type="button" class="chip">75%</button>
											<button type="button" class="chip">50%</button>
										</div>
									</div>
								</div>
							</div>
							<div class="sheet classic-only" id="theme">
								<h3>ACCENT</h3>
								<div class="swatches" id="classic-swatches"></div>
							</div>
						</div>
					</section>
				</div>
			</div>
		</section>

		<section class="band" id="features">
			<h2>Modules</h2>
			<p>The same cards you click in-game, tuned for Skyblock.</p>
			<div class="feats">
				<article class="feat"><svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z"/></svg><b>World</b><span>Terrain tint, skybox, fog, and aspect without touching shaders.</span></article>
				<article class="feat"><svg viewBox="0 0 24 24"><path d="M6.5 2 4 6l2 2-6 6 4 4 6-6 2 2 4-2.5L14 8l2-2z"/></svg><b>Combat</b><span>Hitsounds, triggerbot, Terminator CPS, and in-menu clicker binds.</span></article>
				<article class="feat"><svg viewBox="0 0 24 24"><path d="M12 5C7 5 2.7 8.1 1 12c1.7 3.9 6 7 11 7s9.3-3.1 11-7c-1.7-3.9-6-7-11-7z"/></svg><b>Visuals</b><span>Mob glow, player fill, chest ESP, held-item shader, and nametag filters.</span></article>
				<article class="feat"><svg viewBox="0 0 24 24"><path d="M3 5h18v10H3zM8 17h8v2H8z"/></svg><b>HUD</b><span>Overlay and Bars: watermark, Spotify, raw mats, and a live HUD editor.</span></article>
				<article class="feat"><svg viewBox="0 0 24 24"><path d="M12 2 4 7v10l8 5 8-5V7z"/></svg><b>Mining</b><span>Commission HUD, titanium ESP, and End node markers on a second tab.</span></article>
				<article class="feat"><svg viewBox="0 0 24 24"><path d="M12 2 4 7l8 5 8-5zM4 12l8 5 8-5"/></svg><b>Farming</b><span>Yaw / pitch overlay and a Jacob contest tracker on the tab list.</span></article>
				<article class="feat"><svg viewBox="0 0 24 24"><path d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4zm0 2c-3 0-8 1.5-8 4.5V21h16v-2.5C20 15.5 15 14 12 14z"/></svg><b>You</b><span>Nick, custom capes, and head tags other Stray users see.</span></article>
				<article class="feat"><svg viewBox="0 0 24 24"><path d="M4 6h16v12H4zM8 3h8v3H8z"/></svg><b>Misc</b><span>Loadouts, wardrobe, auto experiments, keybinds, and a Status tab.</span></article>
			</div>
		</section>

		<section class="cape">
			<div class="glass">
				<h2>Want a custom cape?</h2>
				<div class="who">
					<span class="handle">@evilkitten911</span>
					<button type="button" class="copy" id="copy">Copy</button>
				</div>
				<p>Message that Discord with your Minecraft name. After you get added, open the Cape card in Stray and crop a photo or paste a PNG. Other Stray users see it when they join a world.</p>
			</div>
			<div class="glass statline">
				<div><b id="stat-ver">1.2</b><span>Latest build</span></div>
				<div><b>26.1</b><span>Minecraft</span></div>
				<div><b>Right Shift</b><span>Open menu</span></div>
				<div><b>stray.gay</b><span>Always this host</span></div>
			</div>
		</section>
		<div class="foot">
			<span>stray.gay</span>
			<a href="/admin">Desk</a>
		</div>
	</div>
	<script>
		(function sky() {
			var c = document.getElementById("sky");
			var ctx = c.getContext("2d");
			var stars = [];
			var colors = ["#f4f7ff", "#d7e6ff", "#c8f0ff", "#ffe9c8", "#b8d4ff"];
			var shot = null;
			var nextShot = 0;
			function resize() {
				c.width = window.innerWidth;
				c.height = window.innerHeight;
				stars = [];
				var n = Math.floor(c.width * c.height / 7800);
				for (var i = 0; i < n; i++) {
					stars.push({
						x: Math.random() * c.width,
						y: Math.random() * c.height,
						z: Math.random(),
						s: Math.random() * 1.6 + 0.3,
						c: colors[(Math.random() * colors.length) | 0],
						p: Math.random() * Math.PI * 2
					});
				}
			}
			function tick(now) {
				ctx.fillStyle = "#05070d";
				ctx.fillRect(0, 0, c.width, c.height);
				for (var i = 0; i < stars.length; i++) {
					var st = stars[i];
					st.y += 0.08 + st.z * 0.18;
					if (st.y > c.height) { st.y = 0; st.x = Math.random() * c.width; }
					var a = 0.18 + st.z * 0.55 + Math.sin(now * 0.002 + st.p) * 0.16;
					ctx.fillStyle = st.c;
					ctx.globalAlpha = Math.max(0.08, Math.min(0.95, a));
					ctx.fillRect(st.x, st.y, st.s, st.s);
				}
				ctx.globalAlpha = 1;
				if (!shot && now > nextShot) {
					shot = { x: Math.random() * c.width * 0.7, y: Math.random() * c.height * 0.4, vx: 4.2 + Math.random() * 2, vy: 1.6 + Math.random(), life: 0 };
					nextShot = now + 8200 + Math.random() * 4000;
				}
				if (shot) {
					shot.x += shot.vx;
					shot.y += shot.vy;
					shot.life += 1;
					var g = ctx.createLinearGradient(shot.x, shot.y, shot.x - shot.vx * 9, shot.y - shot.vy * 9);
					g.addColorStop(0, "rgba(244,247,255,0.95)");
					g.addColorStop(1, "rgba(244,247,255,0)");
					ctx.strokeStyle = g;
					ctx.lineWidth = 1.4;
					ctx.beginPath();
					ctx.moveTo(shot.x, shot.y);
					ctx.lineTo(shot.x - shot.vx * 9, shot.y - shot.vy * 9);
					ctx.stroke();
					if (shot.life > 42 || shot.x > c.width || shot.y > c.height) shot = null;
				}
				requestAnimationFrame(tick);
			}
			window.addEventListener("resize", resize);
			resize();
			nextShot = performance.now() + 2400;
			requestAnimationFrame(tick);
		})();
		(function orb() {
			var el = document.getElementById("orb");
			var x = window.innerWidth * 0.5, y = window.innerHeight * 0.3, tx = x, ty = y;
			window.addEventListener("pointermove", function (e) { tx = e.clientX; ty = e.clientY; });
			function loop() {
				x += (tx - x) * 0.06;
				y += (ty - y) * 0.06;
				el.style.left = x + "px";
				el.style.top = y + "px";
				requestAnimationFrame(loop);
			}
			loop();
		})();
		document.getElementById("copy").onclick = function () {
			var btn = this;
			navigator.clipboard.writeText("@evilkitten911").then(function () {
				btn.textContent = "Copied";
				setTimeout(function () { btn.textContent = "Copy"; }, 1400);
			}).catch(function () { btn.textContent = "Copy failed"; });
		};
		(function loadMod() {
			var ver = document.getElementById("mod-ver");
			var menuVer = document.getElementById("menu-ver");
			var stat = document.getElementById("stat-ver");
			var links = [document.getElementById("mod-download"), document.getElementById("mod-download-2")];
			var mirrors = [
				{ url: "/api/mod", repo: "" },
				{ url: "https://cdn.jsdelivr.net/gh/camberX/Stray@main/web/public/mod/latest.json", repo: "camberX/Stray" },
				{ url: "https://raw.githubusercontent.com/camberX/Stray/main/web/public/mod/latest.json", repo: "camberX/Stray" },
				{ url: "https://cdn.jsdelivr.net/gh/camberX/Eisenmann@main/web/public/mod/latest.json", repo: "camberX/Eisenmann" },
				{ url: "https://raw.githubusercontent.com/camberX/Eisenmann/main/web/public/mod/latest.json", repo: "camberX/Eisenmann" },
				{ url: "https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/latest.json", repo: "camberX/voidmark" }
			];
			function fileUrl(data) {
				if (data.url && data.url.charAt(0) === "/") return data.url;
				return "https://raw.githubusercontent.com/" + (data.repo || "camberX/Stray") + "/main/web/public/mod/" + (data.file || ("stray-" + data.version + ".jar"));
			}
			function apply(data) {
				ver.textContent = "v" + data.version;
				if (menuVer) menuVer.textContent = "v" + data.version;
				var railNum = document.getElementById("rail-ver-num");
				if (railNum) railNum.textContent = "v" + data.version;
				stat.textContent = data.version;
				for (var i = 0; i < links.length; i++) {
					links[i].classList.remove("dead");
					links[i].setAttribute("download", data.file || ("stray-" + data.version + ".jar"));
					links[i].href = fileUrl(data);
				}
			}
			function next(i) {
				if (i >= mirrors.length) return;
				fetch(mirrors[i].url, { cache: "no-store" }).then(function (r) { return r.ok ? r.json() : null; }).then(function (data) {
					if (data && data.version) {
						if (!data.repo && mirrors[i].repo) data.repo = mirrors[i].repo;
						apply(data);
					} else next(i + 1);
				}).catch(function () { next(i + 1); });
			}
			next(0);
		})();
		(function menu() {
			var GROUPS = {
				world: [{ id: "world", label: "World" }],
				combat: [{ id: "combat", label: "Combat" }],
				visuals: [{ id: "visuals", label: "Visuals" }],
				hud: [{ id: "overlay", label: "Overlay" }, { id: "bars", label: "Bars" }],
				mining: [{ id: "mining", label: "Mining" }, { id: "nodes", label: "Nodes" }],
				farming: [{ id: "farming", label: "Farming" }],
				misc: [{ id: "menus", label: "Menus" }, { id: "status", label: "Status" }],
				theme: [{ id: "theme", label: "Theme" }],
				player: [{ id: "player", label: "Player" }]
			};
			var TAB_GROUP = {};
			Object.keys(GROUPS).forEach(function (g) {
				GROUPS[g].forEach(function (t) { TAB_GROUP[t.id] = g; });
			});
			var titles = {
				world: "World", combat: "Combat", visuals: "Visuals", overlay: "Overlay", bars: "Bars",
				mining: "Mining", nodes: "Nodes", farming: "Farming", menus: "Menus", status: "Status",
				player: "Player", theme: "Theme"
			};
			var current = "world";
			var panels = document.querySelectorAll(".cols");
			var title = document.getElementById("bar-title");
			var theme = document.getElementById("theme");
			var themeBtn = document.getElementById("theme-btn");
			var pill = document.getElementById("nav-pill");
			var box = document.getElementById("menu");
			var wm = document.getElementById("wm");
			var headTabs = document.getElementById("head-tabs");
			var headFace = document.getElementById("head-face");
			var you = document.getElementById("you");
			var guiLabel = document.getElementById("gui-label");
			var nick = document.querySelector(".nick");
			if (box) box.classList.add("in");
			var binds = ["[ None ]", "[ RShift ]", "[ Button 5 ]", "[ Mouse 4 ]"];
			var colors = ["#e5e7eb", "#2fb5ff", "#4d8dff", "#a78bfa", "#f472b6", "#fb7185", "#fb923c", "#34d399"];
			function paintSwatches(wrap) {
				if (!wrap) return;
				colors.forEach(function (hex, i) {
					var b = document.createElement("button");
					b.type = "button";
					b.className = "swatch" + (i === 0 ? " on" : "");
					b.setAttribute("data-hex", hex);
					b.style.background = hex;
					b.onclick = function () { pick(hex); };
					wrap.appendChild(b);
				});
			}
			function pick(hex) {
				document.documentElement.style.setProperty("--accent", hex);
				document.documentElement.style.setProperty("--accent-dim", hex);
				document.documentElement.style.setProperty("--pill", hex === "#e5e7eb" ? "#4a5564" : hex);
				document.querySelectorAll(".swatch").forEach(function (x) {
					x.classList.toggle("on", x.getAttribute("data-hex") === hex);
				});
			}
			paintSwatches(document.getElementById("swatches"));
			paintSwatches(document.getElementById("classic-swatches"));
			paintSwatches(document.getElementById("hero-swatches"));
			function classicMode() {
				return false;
			}
			function setMode(classic) {
				document.body.classList.remove("classic");
				document.body.classList.add("ctrl");
				if (guiLabel) guiLabel.textContent = "Control";
			}
			function activeRail() {
				if (classicMode()) {
					if (current === "player") return you;
					return document.querySelector(".tab.classic-only.on");
				}
				return document.querySelector(".tab.ctrl-only.on");
			}
			function movePill(el) {
				if (!pill) return;
				if (!el || el.id === "you" || current === "player") {
					pill.classList.add("hide");
					return;
				}
				pill.classList.remove("hide");
				pill.style.top = el.offsetTop + "px";
				pill.style.height = el.offsetHeight + "px";
			}
			function paintHead(name) {
				if (!headTabs) return;
				var group = TAB_GROUP[name];
				var tabs = GROUPS[group] || [];
				headTabs.innerHTML = "";
				tabs.forEach(function (t) {
					var b = document.createElement("button");
					b.type = "button";
					b.className = "head-tab" + (t.id === name ? " on" : "");
					b.textContent = t.label;
					b.onclick = function () { show(t.id); };
					headTabs.appendChild(b);
				});
			}
			function show(name) {
				current = name;
				var group = TAB_GROUP[name];
				document.querySelectorAll(".tab.ctrl-only").forEach(function (t) {
					t.classList.toggle("on", t.getAttribute("data-group") === group && group !== "player");
				});
				document.querySelectorAll(".tab.classic-only").forEach(function (t) {
					t.classList.toggle("on", t.getAttribute("data-tab") === name);
				});
				if (you) you.classList.toggle("on", name === "player");
				if (headFace) headFace.classList.toggle("on", name === "player");
				panels.forEach(function (p) { p.classList.toggle("on", p.getAttribute("data-panel") === name); });
				if (title) {
					title.textContent = titles[name] || name;
					title.replaceWith(title.cloneNode(true));
					title = document.getElementById("bar-title");
				}
				if (wm) wm.hidden = name !== "overlay";
				if (theme) theme.classList.remove("on");
				if (themeBtn) themeBtn.classList.remove("on");
				paintHead(name);
				requestAnimationFrame(function () { movePill(activeRail()); });
			}
			function openGroup(group) {
				if (TAB_GROUP[current] === group) return;
				show(GROUPS[group][0].id);
			}
			document.querySelectorAll(".tab.ctrl-only").forEach(function (t) {
				t.onclick = function () { openGroup(t.getAttribute("data-group")); };
			});
			document.querySelectorAll(".tab.classic-only").forEach(function (t) {
				t.onclick = function () { show(t.getAttribute("data-tab")); };
			});
			if (you) you.onclick = function () { show("player"); };
			if (headFace) headFace.onclick = function () { show("player"); };
			if (themeBtn) themeBtn.onclick = function () {
				if (theme) theme.classList.toggle("on");
				themeBtn.classList.toggle("on");
			};
			if (box) box.addEventListener("click", function (e) {
				var tog = e.target.closest(".tog");
				if (tog) tog.classList.toggle("on");
				var row = e.target.closest(".list button");
				if (row) row.classList.toggle("on");
				var chip = e.target.closest(".chip");
				if (chip && chip.parentNode) {
					chip.parentNode.querySelectorAll(".chip").forEach(function (x) { x.classList.toggle("on", x === chip); });
				}
				var bind = e.target.closest(".bind");
				if (bind) {
					var i = binds.indexOf(bind.textContent.trim());
					bind.textContent = binds[(i + 1) % binds.length];
				}
			});
			if (nick) nick.oninput = function () {
				if (you && you.querySelector("span")) you.querySelector("span").textContent = this.value.trim() || "You";
			};
			setMode(false);
			paintHead(current);
			movePill(activeRail());
			var c = document.getElementById("pane-stars");
			if (!c || !c.getContext) return;
			var ctx = c.getContext("2d");
			if (!ctx) return;
			var stars = [];
			function resize() {
				c.width = c.clientWidth;
				c.height = c.clientHeight;
				stars = [];
				for (var i = 0; i < 48; i++) stars.push({ x: Math.random() * c.width, y: Math.random() * c.height, z: Math.random(), s: Math.random() * 1.3 + 0.3 });
			}
			function tick() {
				ctx.clearRect(0, 0, c.width, c.height);
				for (var i = 0; i < stars.length; i++) {
					var st = stars[i];
					st.y += 0.12 + st.z * 0.16;
					if (st.y > c.height) st.y = 0;
					ctx.fillStyle = "rgba(232,237,245," + (0.16 + st.z * 0.5) + ")";
					ctx.fillRect(st.x, st.y, st.s, st.s);
				}
				requestAnimationFrame(tick);
			}
			window.addEventListener("resize", resize);
			resize();
			tick();
		})();
	</script>
</body>
</html>
`;
const LOGIN_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>STRAY Admin</title>
	<meta name="theme-color" content="#05070d">
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@500;700;800&display=swap" rel="stylesheet">
	<style>
		:root {
			--bg: #05070d;
			--text: #f2f4f7;
			--muted: #8a9aab;
			--accent: #e5e7eb;
			--warn: #f5c16c;
			--line: rgba(255,255,255,0.16);
		}
		* { box-sizing: border-box; }
		html, body { margin: 0; min-height: 100%; background: var(--bg); color: var(--text); font-family: "Nunito Sans", sans-serif; }
		#sky { position: fixed; inset: 0; z-index: 0; }
		.nebula {
			position: fixed; inset: -15%; z-index: 1; pointer-events: none;
			background:
				radial-gradient(800px 460px at 50% 8%, color-mix(in srgb, var(--accent) 20%, transparent), transparent 62%),
				radial-gradient(520px 380px at 80% 90%, rgba(167,139,250,0.1), transparent 70%);
		}
		.grain {
			position: fixed; inset: 0; z-index: 1; pointer-events: none; opacity: 0.045; mix-blend-mode: overlay;
			background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.55'/%3E%3C/svg%3E");
		}
		main {
			position: relative; z-index: 2; width: min(420px, calc(100% - 28px)); margin: 14vh auto;
			background: linear-gradient(180deg, rgba(255,255,255,0.14), rgba(255,255,255,0.06));
			border-radius: 24px; padding: 28px 26px 24px;
			box-shadow: 0 30px 90px #000a, inset 0 1px 0 rgba(255,255,255,0.32);
			backdrop-filter: blur(28px) saturate(1.3);
		}
		.kicker { font-size: 11px; letter-spacing: 0.22em; text-transform: uppercase; color: var(--accent); font-weight: 800; }
		h1 { margin: 10px 0 0; font-size: 28px; letter-spacing: 0.28em; }
		.tick { width: 28px; height: 2px; background: var(--accent); border-radius: 2px; margin: 12px 0 16px; box-shadow: 0 0 16px var(--accent); }
		p, label { color: var(--muted); font-size: 14px; line-height: 1.5; }
		label { display: block; margin: 0 0 6px; font-weight: 800; color: var(--text); font-size: 11px; letter-spacing: 0.1em; text-transform: uppercase; }
		input {
			width: 100%; background: rgba(0,0,0,0.28); border: 1px solid var(--line); border-radius: 14px;
			color: var(--text); padding: 12px 14px; font: inherit; outline: none;
		}
		input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 22%, transparent); }
		button {
			margin-top: 16px; width: 100%; border: 0; border-radius: 999px; background: var(--accent); color: #041018;
			font-weight: 800; padding: 12px; cursor: pointer; letter-spacing: 0.08em; text-transform: uppercase;
			box-shadow: 0 10px 28px color-mix(in srgb, var(--accent) 28%, transparent);
		}
		button:disabled { opacity: 0.5; }
		.status { min-height: 20px; margin-top: 14px; font-size: 13px; }
		.status.err { color: var(--warn); }
		.back { display: inline-block; margin-top: 16px; color: var(--muted); text-decoration: none; font-size: 12px; letter-spacing: 0.12em; text-transform: uppercase; font-weight: 800; }
		.back:hover { color: var(--accent); }
	</style>
</head>
<body>
	<canvas id="sky"></canvas>
	<div class="nebula"></div>
	<div class="grain"></div>
	<main>
		<div class="kicker">Restricted</div>
		<h1>STRAY</h1>
		<div class="tick"></div>
		<p>Enter the Worker admin secret to open the cape desk.</p>
		<label for="admin">Admin key</label>
		<input id="admin" type="password" autocomplete="current-password" placeholder="Secret">
		<button type="button" id="go">Enter</button>
		<div class="status" id="status"></div>
		<a class="back" href="/">Back to shop</a>
	</main>
	<script>
		(function sky() {
			var c = document.getElementById("sky");
			var ctx = c.getContext("2d");
			var list = [];
			var colors = ["#f4f7ff", "#d7e6ff", "#c8f0ff", "#ffe9c8", "#b8d4ff"];
			function resize() {
				c.width = window.innerWidth;
				c.height = window.innerHeight;
				list = [];
				var n = Math.floor(c.width * c.height / 8500);
				for (var i = 0; i < n; i++) list.push({ x: Math.random() * c.width, y: Math.random() * c.height, z: Math.random() * 1.2 + 0.2, s: Math.random() * 1.5 + 0.2, c: colors[(Math.random() * colors.length) | 0] });
			}
			function tick() {
				ctx.fillStyle = "#05070d";
				ctx.fillRect(0, 0, c.width, c.height);
				for (var i = 0; i < list.length; i++) {
					var st = list[i];
					st.y += st.z * 0.16;
					if (st.y > c.height) st.y = 0;
					ctx.fillStyle = st.c;
					ctx.globalAlpha = 0.22 + st.z * 0.5;
					ctx.fillRect(st.x, st.y, st.s, st.s);
				}
				ctx.globalAlpha = 1;
				requestAnimationFrame(tick);
			}
			window.addEventListener("resize", resize);
			resize();
			tick();
		})();
		const admin = document.getElementById("admin");
		const status = document.getElementById("status");
		const go = document.getElementById("go");
		admin.value = sessionStorage.getItem("stray-admin") || "";
		async function enter() {
			const key = admin.value.trim();
			status.textContent = "Checking…";
			status.className = "status";
			go.disabled = true;
			try {
				const response = await fetch("/api/whitelist", {
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ admin: key })
				});
				const data = await response.json();
				if (!response.ok) throw new Error(data.error || "Bad admin key");
				sessionStorage.setItem("stray-admin", key);
				location.href = "/manage";
			} catch (error) {
				sessionStorage.removeItem("stray-admin");
				status.className = "status err";
				status.textContent = error.message;
			} finally {
				go.disabled = false;
			}
		}
		go.onclick = enter;
		admin.addEventListener("keydown", function (event) { if (event.key === "Enter") enter(); });
		if (admin.value) enter();
	</script>
</body>
</html>
`;
const MANAGE_HTML = `<!DOCTYPE html>
<html lang="en" class="locked">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>STRAY Desk</title>
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@500;700;800&display=swap" rel="stylesheet">
	<style>
		@font-face {
			font-family: "Minecraft";
			src: url("https://cdn.jsdelivr.net/npm/skinview3d@3.4.1/assets/minecraft.woff2") format("woff2");
			font-display: swap;
		}
		:root {
			--bg: #05070d;
			--pane: rgba(255,255,255,0.08);
			--card: rgba(255,255,255,0.10);
			--line: rgba(255,255,255,0.14);
			--text: #f2f4f7;
			--muted: #8a9aab;
			--accent: #e5e7eb;
			--warn: #f5c16c;
			--danger: #fb7185;
			--ok: #34d399;
		}
		* { box-sizing: border-box; }
		html, body { margin: 0; height: 100%; background: var(--bg); color: var(--text); font-family: "Nunito Sans", sans-serif; }
		#stars { position: fixed; inset: 0; z-index: 0; }
		.nebula {
			position: fixed; inset: -20%; z-index: 1; pointer-events: none;
			background:
				radial-gradient(900px 500px at 12% 0%, color-mix(in srgb, var(--accent) 18%, transparent), transparent 62%),
				radial-gradient(700px 480px at 100% 100%, rgba(167,139,250,0.1), transparent 70%);
		}
		.app { position: relative; z-index: 2; display: grid; grid-template-columns: 220px minmax(0, 1fr); min-height: 100%; }
		.rail {
			padding: 22px 16px;
			background: linear-gradient(180deg, rgba(255,255,255,0.10), rgba(255,255,255,0.04));
			border-right: 1px solid var(--line);
			backdrop-filter: blur(28px) saturate(1.25);
			box-shadow: inset -1px 0 0 rgba(255,255,255,0.06);
		}
		.brand { letter-spacing: 0.28em; font-weight: 800; font-size: 13px; }
		.brand span { color: var(--accent); }
		.tick { width: 22px; height: 2px; background: var(--accent); border-radius: 2px; margin: 8px 0 10px; box-shadow: 0 0 14px var(--accent); }
		.sub { color: var(--muted); font-size: 11px; letter-spacing: 0.16em; text-transform: uppercase; margin: 0 0 22px; }
		.recycle { color: var(--accent); font-size: 13px; margin-top: 18px; letter-spacing: 0.08em; }
		nav { display: grid; gap: 6px; }
		nav button, .ghost, .warn, .danger, .primary { border: 0; border-radius: 999px; cursor: pointer; font: inherit; font-weight: 800; }
		.out { width: 100%; margin-top: 18px; background: rgba(255,255,255,0.08); color: var(--text); border: 1px solid var(--line); padding: 10px; }
		.content { padding: 22px 22px 48px; }
		.top { display: flex; justify-content: space-between; gap: 12px; align-items: end; flex-wrap: wrap; }
		h1 { margin: 0; font-size: 22px; letter-spacing: 0.16em; }
		.hint { color: var(--muted); font-size: 13px; margin: 6px 0 0; }
		.stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin: 18px 0; }
		.stat {
			background: rgba(255,255,255,0.07); border: 1px solid var(--line); border-radius: 18px; padding: 14px;
			box-shadow: inset 0 1px 0 rgba(255,255,255,0.14); backdrop-filter: blur(18px);
		}
		.stat b { display: block; font-size: 22px; color: var(--accent); }
		.stat span { color: var(--muted); font-size: 11px; letter-spacing: 0.12em; text-transform: uppercase; }
		.toolbar, .add, .row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
		input, textarea, select {
			background: rgba(0,0,0,0.28); border: 1px solid var(--line); border-radius: 14px; color: var(--text);
			padding: 10px 12px; font: inherit; outline: none;
		}
		input:focus, textarea:focus { border-color: var(--accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 18%, transparent); }
		.grow { flex: 1; min-width: 160px; }
		.primary { background: var(--accent); color: #041018; padding: 10px 14px; box-shadow: 0 8px 22px color-mix(in srgb, var(--accent) 24%, transparent); }
		.ghost { background: rgba(255,255,255,0.08); color: var(--text); border: 1px solid var(--line); padding: 10px 12px; }
		.warn { background: rgba(245,193,108,0.12); color: var(--warn); border: 1px solid rgba(245,193,108,0.35); padding: 10px 12px; }
		.danger { background: rgba(251,113,133,0.12); color: var(--danger); border: 1px solid rgba(251,113,133,0.32); padding: 10px 12px; }
		button:disabled { opacity: 0.5; }
		.chips { display: flex; gap: 6px; flex-wrap: wrap; margin: 12px 0; }
		.chip { background: rgba(255,255,255,0.06); color: var(--muted); border: 1px solid var(--line); padding: 7px 10px; font-size: 12px; }
		.chip.on { color: var(--accent); border-color: var(--accent); background: color-mix(in srgb, var(--accent) 14%, transparent); }
		.status { min-height: 18px; margin: 10px 0 0; font-size: 13px; }
		.status.ok { color: var(--ok); }
		.status.err { color: var(--warn); }
		.list { display: grid; gap: 10px; margin-top: 14px; }
		.player {
			display: grid; grid-template-columns: 56px minmax(0, 1fr) 54px; gap: 12px; align-items: center;
			background: rgba(255,255,255,0.06); border: 1px solid var(--line); border-radius: 18px; padding: 12px; cursor: pointer;
			box-shadow: inset 0 1px 0 rgba(255,255,255,0.10);
		}
		.player:hover { border-color: color-mix(in srgb, var(--accent) 50%, transparent); box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent) 22%, transparent); }
		.head { width: 56px; height: 56px; border-radius: 14px; background: #000; image-rendering: pixelated; }
		.name { font-weight: 800; }
		.uuid { color: var(--muted); font-size: 12px; word-break: break-all; margin-top: 3px; }
		.badges { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
		.badge { font-size: 10px; letter-spacing: 0.1em; text-transform: uppercase; border-radius: 999px; padding: 3px 8px; border: 1px solid var(--line); color: var(--muted); }
		.badge.on { color: var(--accent); border-color: var(--accent); }
		.badge.warn { color: var(--warn); border-color: rgba(245,193,108,0.4); }
		.cape { width: 42px; height: 66px; object-fit: contain; image-rendering: pixelated; justify-self: center; }
		.nocape { color: var(--muted); font-size: 11px; text-align: center; }
		.empty { color: var(--muted); padding: 28px 8px; }
		.panel { display: block; }
		.card {
			background: rgba(255,255,255,0.07); border: 1px solid var(--line); border-radius: 18px; padding: 18px; margin-top: 16px;
			box-shadow: inset 0 1px 0 rgba(255,255,255,0.12); backdrop-filter: blur(18px);
		}
		label { display: block; margin: 0 0 6px; font-size: 11px; letter-spacing: 0.1em; text-transform: uppercase; font-weight: 800; }
		textarea { width: 100%; min-height: 140px; resize: vertical; }
		.overlay { position: fixed; inset: 0; background: #05070dcc; display: flex; align-items: stretch; justify-content: flex-end; z-index: 20; backdrop-filter: blur(10px); }
		.overlay[hidden] { display: none; }
		.overlay.center { align-items: center; justify-content: center; padding: 16px; }
		.drawer, .sheet {
			width: min(440px, 100%);
			background: linear-gradient(180deg, rgba(18,22,32,0.92), rgba(8,10,16,0.92));
			border-left: 1px solid var(--line); padding: 22px; overflow: auto;
			box-shadow: -20px 0 80px #000a, inset 0 1px 0 rgba(255,255,255,0.12);
			backdrop-filter: blur(28px);
		}
		.sheet { width: min(460px, 100%); border: 1px solid var(--line); border-radius: 24px; border-left: 1px solid var(--line); }
		.crop-sheet { width: min(740px, 100%) !important; }
		.crop-wrap { display: grid; grid-template-columns: minmax(0, 1fr) 90px; gap: 14px; margin: 12px 0 16px; align-items: start; }
		.crop-stage { position: relative; background: rgba(0,0,0,0.35); border: 1px solid var(--line); border-radius: 14px; min-height: 220px; display: flex; justify-content: center; align-items: center; overflow: hidden; }
		.crop-holder { position: relative; display: inline-block; }
		.crop-stage canvas { display: block; }
		#crop-box { position: absolute; border: 2px solid var(--accent); box-shadow: 0 0 0 9999px #0009; pointer-events: none; }
		.crop-face canvas { width: 50px; height: 80px; background: #000; border: 1px solid var(--line); border-radius: 8px; display: block; }
		.who { color: var(--muted); font-size: 13px; margin: 0 0 14px; }
		.preview { margin-bottom: 16px; }
		.stage { position: relative; height: 360px; border-radius: 16px; overflow: hidden; background: rgba(0,0,0,0.35); border: 1px solid var(--line); }
		.stage canvas { display: block; width: 100%; height: 100%; }
		.labels { position: absolute; left: 8px; right: 8px; top: 6%; display: flex; flex-direction: column; align-items: center; gap: 1px; pointer-events: none; z-index: 1; }
		.mc-tag { font-family: "Minecraft", monospace; font-size: 16px; line-height: 1; -webkit-font-smoothing: none; image-rendering: pixelated; background: rgba(0, 0, 0, 0.25); color: #fff; text-shadow: 1px 1px 0 #3f3f3f; padding: 1px 4px; white-space: nowrap; max-width: 100%; overflow: hidden; }
		.drag { position: absolute; left: 10px; bottom: 8px; margin: 0; color: var(--muted); font-size: 11px; letter-spacing: 0.06em; pointer-events: none; }
		.preview .nocape { font-size: 12px; margin: 8px 0 0; text-align: left; }
		.codes { display: flex; flex-wrap: wrap; gap: 6px; margin: 0 0 12px; }
		.swatch { width: 28px; height: 28px; border-radius: 8px; border: 1px solid #ffffff33; padding: 0; color: #111; font-size: 11px; font-weight: 800; }
		.swatch.fmt { background: rgba(255,255,255,0.08); color: var(--text); width: auto; padding: 0 8px; }
		.preview-plate { background: rgba(0, 0, 0, 0.25); min-height: 22px; display: flex; align-items: center; justify-content: center; padding: 4px 8px; margin: 0 0 14px; font-family: "Minecraft", monospace; font-size: 16px; line-height: 1; image-rendering: pixelated; -webkit-font-smoothing: none; }
		.field { margin: 0 0 12px; }
		.bypass { display: flex; align-items: center; gap: 8px; font-weight: 800; cursor: pointer; }
		html.locked .app, html.locked .overlay { visibility: hidden !important; pointer-events: none !important; user-select: none !important; }
		html.locked button, html.locked input, html.locked textarea, html.locked select, html.locked a { pointer-events: none !important; }
		@media (max-width: 860px) {
			.app { grid-template-columns: 1fr; }
			.rail { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; border-right: 0; border-bottom: 1px solid var(--line); }
			.sub { margin: 0 auto 0 0; }
			nav { display: flex; flex-wrap: wrap; }
			.out { margin: 0; width: auto; }
			.stats { grid-template-columns: 1fr 1fr; }
			.recycle { margin: 0; }
		}
	</style>
</head>
<body>
	<canvas id="stars"></canvas>
	<div class="nebula"></div>
	<div class="app" inert>
		<aside class="rail">
			<div class="brand">STRAY</div>
			<div class="tick"></div>
			<div class="sub">Cape desk</div>
			<button type="button" class="out ghost" id="out">Log out</button>
			<div class="recycle">♲</div>
		</aside>
		<main class="content">
			<div class="status" id="status"></div>
			<section class="panel on" id="view-players">
				<div class="top">
					<div>
						<h1>PLAYERS</h1>
						<p class="hint">Click a row for cape, tag, note, cooldown, and dewhitelist.</p>
					</div>
					<div class="toolbar">
						<button type="button" class="ghost" id="refresh">Refresh names</button>
						<button type="button" class="ghost" id="export">Export JSON</button>
					</div>
				</div>
				<div class="stats">
					<div class="stat"><b id="s-all">0</b><span>Listed</span></div>
					<div class="stat"><b id="s-cape">0</b><span>Capes</span></div>
					<div class="stat"><b id="s-tag">0</b><span>Tags</span></div>
					<div class="stat"><b id="s-bypass">0</b><span>Bypass</span></div>
				</div>
				<div class="add">
					<input class="grow" id="uuid" type="text" autocomplete="off" spellcheck="false" placeholder="Username or UUID">
					<button type="button" class="primary" id="add">Add</button>
				</div>
				<div class="toolbar" style="margin-top:12px">
					<input class="grow" id="search" type="search" placeholder="Search name or UUID">
					<select id="sort">
						<option value="name">Sort by name</option>
						<option value="cape">Capes first</option>
						<option value="tag">Tags first</option>
					</select>
				</div>
				<div class="chips" id="filters">
					<button type="button" class="chip on" data-filter="all">All</button>
					<button type="button" class="chip" data-filter="cape">Has cape</button>
					<button type="button" class="chip" data-filter="none">No cape</button>
					<button type="button" class="chip" data-filter="tag">Tagged</button>
					<button type="button" class="chip" data-filter="bypass">Bypass</button>
					<button type="button" class="chip" data-filter="lock">On cooldown</button>
				</div>
				<p class="empty" id="empty">No players yet.</p>
				<div class="list" id="list"></div>
				<div class="card">
					<label for="bulktext">Bulk add</label>
					<textarea id="bulktext" placeholder="One username or UUID per line" style="min-height:88px"></textarea>
					<div class="row" style="margin-top:12px">
						<button type="button" class="primary" id="bulkadd">Add all</button>
					</div>
				</div>
			</section>
		</main>
	</div>
	<div class="overlay" id="drawer" hidden inert>
		<div class="drawer">
			<div class="top">
				<h1 style="font-size:16px">PLAYER</h1>
				<button type="button" class="ghost" id="close">Close</button>
			</div>
			<p class="who" id="d-who"></p>
			<div class="preview">
				<div class="stage" id="d-stage">
					<canvas id="d-model"></canvas>
					<div class="labels">
						<div class="mc-tag" id="d-htag" hidden></div>
						<div class="mc-tag" id="d-nname"></div>
					</div>
					<p class="drag">Drag to rotate</p>
				</div>
				<p class="nocape" id="d-nocape">No cape</p>
			</div>
			<div class="field">
				<label>Note</label>
				<input id="d-note" maxlength="160" placeholder="Paid, cape pending…">
			</div>
			<div class="field">
				<label>Cape from URL</label>
				<div class="row">
					<input class="grow" id="d-url" placeholder="https://…/cape.png">
					<button type="button" class="ghost" id="d-urlgo">Fetch</button>
				</div>
			</div>
			<div class="field">
				<label>Wings</label>
				<div class="row">
					<select class="grow" id="d-wings">
						<option value="">None</option>
						<option value="angel">Angel</option>
						<option value="demon">Demon</option>
						<option value="fairy">Fairy</option>
						<option value="phoenix">Phoenix</option>
						<option value="butterfly">Butterfly</option>
					</select>
					<input id="d-wingrgb" type="color" value="#F4F6FF" title="Wing color">
					<button type="button" class="ghost" id="d-wingsave">Save</button>
				</div>
			</div>
			<input id="d-file" type="file" accept="image/png,image/jpeg,image/webp,.png,.jpg,.jpeg,.webp" hidden>
			<div class="row">
				<button type="button" class="ghost" id="d-copy">Copy UUID</button>
				<button type="button" class="ghost" id="d-upload">Create cape</button>
				<button type="button" class="ghost" id="d-tag">Head tag</button>
			</div>
			<div class="row" style="margin-top:8px">
				<button type="button" class="ghost" id="d-dl">Download cape</button>
				<button type="button" class="warn" id="d-reset">Reset cooldown</button>
			</div>
			<label class="bypass" style="margin:14px 0"><input id="d-bypass" type="checkbox"> Upload bypass</label>
			<button type="button" class="danger" id="d-kick" style="margin-top:12px">Dewhitelist</button>
		</div>
	</div>
	<div class="overlay center" id="tagbox" hidden inert>
		<div class="sheet">
			<h1 style="font-size:16px">HEAD TAG</h1>
			<p class="who" id="tagwho"></p>
			<label for="tagtext">Text</label>
			<input id="tagtext" type="text" maxlength="48" autocomplete="off" spellcheck="false" placeholder="&amp;bVIP  or  &amp;6&amp;lDonor">
			<div class="codes" id="codes"></div>
			<label>Preview</label>
			<div class="preview-plate" id="tagpreview"></div>
			<div class="row">
				<button type="button" class="primary" id="tagsave">Save</button>
				<button type="button" class="ghost" id="tagclear">Clear</button>
				<button type="button" class="ghost" id="tagcancel">Cancel</button>
			</div>
		</div>
	</div>
	<script src="https://cdn.jsdelivr.net/npm/skinview3d@3.4.1/bundles/skinview3d.bundle.js"></script>
	<script src="/cape-crop.js"></script>
	<script>
		const key = sessionStorage.getItem("stray-admin") || "";
		if (!key) {
			location.replace("/admin");
		}
		let deskLive = false;
		function armDesk() {
			deskLive = true;
			document.documentElement.classList.remove("locked");
			document.querySelectorAll(".app, .overlay").forEach(function (el) { el.inert = false; });
		}
		function disarmDesk() {
			deskLive = false;
			document.documentElement.classList.add("locked");
			document.querySelectorAll(".app, .overlay").forEach(function (el) { el.inert = true; });
		}
		function blockDesk(event) {
			if (deskLive) return;
			event.preventDefault();
			event.stopPropagation();
			event.stopImmediatePropagation();
		}
		["click", "pointerdown", "mousedown", "mouseup", "keydown", "keyup", "input", "change", "submit", "touchstart", "drop", "paste", "contextmenu"].forEach(function (type) {
			document.addEventListener(type, blockDesk, true);
		});
		const status = document.getElementById("status");
		const list = document.getElementById("list");
		const empty = document.getElementById("empty");
		const uuid = document.getElementById("uuid");
		const search = document.getElementById("search");
		const sort = document.getElementById("sort");
		const drawer = document.getElementById("drawer");
		const tagbox = document.getElementById("tagbox");
		const tagtext = document.getElementById("tagtext");
		const tagpreview = document.getElementById("tagpreview");
		const tagwho = document.getElementById("tagwho");
		let cache = [];
		let filter = "all";
		let selected = "";
		let tagTarget = "";
		let skinViewer = null;
		let capeLoadGen = 0;
		const atlasCache = {};
		const atlasWait = {};
		const STAGE_H = 360;
		const COLORS = [
			["0", "#000000"], ["1", "#0000aa"], ["2", "#00aa00"], ["3", "#00aaaa"],
			["4", "#aa0000"], ["5", "#aa00aa"], ["6", "#ffaa00"], ["7", "#aaaaaa"],
			["8", "#555555"], ["9", "#5555ff"], ["a", "#55ff55"], ["b", "#55ffff"],
			["c", "#ff5555"], ["d", "#ff55ff"], ["e", "#ffff55"], ["f", "#ffffff"]
		];
		const FORMATS = [["l", "Bold"], ["o", "Italic"], ["n", "Under"], ["m", "Strike"], ["r", "Reset"]];

		(function stars() {
			var c = document.getElementById("stars");
			var ctx = c.getContext("2d");
			var dots = [];
			var colors = ["#f4f7ff", "#d7e6ff", "#c8f0ff", "#ffe9c8", "#b8d4ff"];
			function resize() {
				c.width = window.innerWidth;
				c.height = window.innerHeight;
				dots = [];
				var n = Math.floor(c.width * c.height / 9000);
				for (var i = 0; i < n; i++) dots.push({ x: Math.random() * c.width, y: Math.random() * c.height, z: Math.random() * 1.2 + 0.2, s: Math.random() * 1.4 + 0.2, c: colors[(Math.random() * colors.length) | 0] });
			}
			function tick() {
				ctx.fillStyle = "#05070d";
				ctx.fillRect(0, 0, c.width, c.height);
				for (var i = 0; i < dots.length; i++) {
					var st = dots[i];
					st.y += st.z * 0.12;
					if (st.y > c.height) st.y = 0;
					ctx.fillStyle = st.c;
					ctx.globalAlpha = 0.18 + st.z * 0.45;
					ctx.fillRect(st.x, st.y, st.s, st.s);
				}
				ctx.globalAlpha = 1;
				requestAnimationFrame(tick);
			}
			window.addEventListener("resize", resize);
			resize();
			tick();
		})();

		(function paintCodes() {
			const box = document.getElementById("codes");
			for (const pair of COLORS) {
				const chip = document.createElement("button");
				chip.type = "button";
				chip.className = "swatch";
				chip.style.background = pair[1];
				chip.textContent = pair[0];
				chip.style.color = "018".indexOf(pair[0]) >= 0 ? "#fff" : "#111";
				chip.onclick = function () { insertCode(pair[0]); };
				box.append(chip);
			}
			for (const pair of FORMATS) {
				const chip = document.createElement("button");
				chip.type = "button";
				chip.className = "swatch fmt";
				chip.textContent = pair[1];
				chip.onclick = function () { insertCode(pair[0]); };
				box.append(chip);
			}
		})();

		function insertCode(code) {
			const start = tagtext.selectionStart || tagtext.value.length;
			const end = tagtext.selectionEnd || start;
			tagtext.value = tagtext.value.slice(0, start) + "&" + code + tagtext.value.slice(end);
			tagtext.focus();
			tagtext.setSelectionRange(start + 2, start + 2);
			paintPreview();
		}

		function paintPreview() {
			tagpreview.innerHTML = "";
			const rendered = renderLegacy(tagtext.value);
			if (!rendered.childNodes.length) {
				const hint = document.createElement("span");
				hint.className = "empty";
				hint.textContent = "No tag";
				tagpreview.append(hint);
				return;
			}
			tagpreview.append(rendered);
		}

		function shadowOf(hex) {
			const n = parseInt(hex.slice(1), 16);
			if (isNaN(n)) return "#3f3f3f";
			const r = Math.floor(((n >> 16) & 255) * 0.25);
			const g = Math.floor(((n >> 8) & 255) * 0.25);
			const b = Math.floor((n & 255) * 0.25);
			function hex2(v) { return (v < 16 ? "0" : "") + v.toString(16); }
			return "#" + hex2(r) + hex2(g) + hex2(b);
		}

		function renderLegacy(raw) {
			const root = document.createElement("span");
			let color = "#ffffff";
			let bold = false;
			let italic = false;
			let strike = false;
			let under = false;
			let buffer = "";
			function flush() {
				if (!buffer) return;
				const span = document.createElement("span");
				span.textContent = buffer;
				span.style.color = color;
				span.style.textShadow = "1px 1px 0 " + shadowOf(color);
				if (bold) span.style.fontWeight = "800";
				if (italic) span.style.fontStyle = "italic";
				const deco = [];
				if (strike) deco.push("line-through");
				if (under) deco.push("underline");
				if (deco.length) span.style.textDecoration = deco.join(" ");
				root.append(span);
				buffer = "";
			}
			for (let i = 0; i < raw.length; i++) {
				const current = raw.charAt(i);
				if (current === "&" && i + 1 < raw.length) {
					const code = raw.charAt(i + 1).toLowerCase();
					const next = COLORS.find(function (pair) { return pair[0] === code; });
					if (next) { flush(); color = next[1]; i++; continue; }
					if (code === "l") { flush(); bold = true; i++; continue; }
					if (code === "o") { flush(); italic = true; i++; continue; }
					if (code === "n") { flush(); under = true; i++; continue; }
					if (code === "m") { flush(); strike = true; i++; continue; }
					if (code === "r") { flush(); color = "#ffffff"; bold = italic = strike = under = false; i++; continue; }
					if (raw.charAt(i + 1) === "&") { buffer += "&"; i++; continue; }
				}
				buffer += current;
			}
			flush();
			return root;
		}

		function setStatus(ok, text) {
			status.className = "status " + (ok ? "ok" : "err");
			status.textContent = text;
		}

		function kickAuth(response) {
			if (response.status === 403) {
				disarmDesk();
				sessionStorage.removeItem("stray-admin");
				fetch("/api/logout", { method: "POST", credentials: "same-origin" }).finally(function () {
					location.replace("/admin");
				});
				return true;
			}
			return false;
		}

		async function api(method, id) {
			const response = await fetch("/api/whitelist", {
				method: method,
				credentials: "same-origin",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ admin: key, uuid: id || undefined })
			});
			const data = await response.json();
			if (kickAuth(response)) throw new Error("Not authorized");
			if (!response.ok) throw new Error(data.error || "Failed");
			return data;
		}

		async function admin(path, method, payload) {
			const response = await fetch(path, {
				method: method,
				credentials: "same-origin",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(Object.assign({ admin: key }, payload || {}))
			});
			const data = await response.json().catch(function () { return {}; });
			if (kickAuth(response)) throw new Error("Not authorized");
			if (!response.ok) throw new Error(data.error || "Failed");
			return data;
		}

		function formatWait(seconds) {
			const total = Math.max(0, Number(seconds) || 0);
			const hours = Math.floor(total / 3600);
			const minutes = Math.floor((total % 3600) / 60);
			if (hours >= 1) return hours + "h " + minutes + "m";
			if (minutes >= 1) return minutes + "m";
			return total + "s";
		}

		function playerBy(id) {
			return cache.find(function (player) { return player.uuid === id; });
		}

		function matches(player) {
			const q = search.value.trim().toLowerCase();
			if (q && (player.name || "").toLowerCase().indexOf(q) < 0 && player.uuid.indexOf(q) < 0 && (player.note || "").toLowerCase().indexOf(q) < 0) return false;
			if (filter === "cape") return player.cape;
			if (filter === "none") return !player.cape;
			if (filter === "tag") return Boolean(player.tag);
			if (filter === "bypass") return Boolean(player.bypass);
			if (filter === "lock") return !player.bypass && player.retryIn > 0;
			return true;
		}

		function ordered() {
			const rows = cache.filter(matches);
			rows.sort(function (a, b) {
				if (sort.value === "cape") return Number(b.cape) - Number(a.cape) || (a.name || "").localeCompare(b.name || "");
				if (sort.value === "tag") return Number(Boolean(b.tag)) - Number(Boolean(a.tag)) || (a.name || "").localeCompare(b.name || "");
				return (a.name || a.uuid).localeCompare(b.name || b.uuid);
			});
			return rows;
		}

		function paintStats() {
			document.getElementById("s-all").textContent = String(cache.length);
			document.getElementById("s-cape").textContent = String(cache.filter(function (p) { return p.cape; }).length);
			document.getElementById("s-tag").textContent = String(cache.filter(function (p) { return p.tag; }).length);
			document.getElementById("s-bypass").textContent = String(cache.filter(function (p) { return p.bypass; }).length);
		}

		function draw(players) {
			cache = players || cache;
			paintStats();
			const rows = ordered();
			list.innerHTML = "";
			empty.style.display = cache.length ? "none" : "block";
			if (cache.length && !rows.length) empty.style.display = "block";
			if (cache.length && !rows.length) empty.textContent = "No players match that filter.";
			else empty.textContent = "No players yet.";
			for (const player of rows) {
				const row = document.createElement("article");
				row.className = "player";
				const head = document.createElement("img");
				head.className = "head";
				head.width = 56;
				head.height = 56;
				head.alt = player.name || "Head";
				head.src = "https://crafthead.net/helm/" + player.uuid + "/64";
				head.onerror = function () { head.onerror = null; head.src = "https://mc-heads.net/avatar/" + player.uuid + "/64"; };
				const meta = document.createElement("div");
				const name = document.createElement("div");
				name.className = "name";
				name.textContent = player.name || "Unknown";
				const id = document.createElement("div");
				id.className = "uuid";
				id.textContent = player.uuid;
				meta.append(name, id);
				if (player.note) {
					const note = document.createElement("div");
					note.className = "uuid";
					note.textContent = player.note;
					meta.append(note);
				}
				const badges = document.createElement("div");
				badges.className = "badges";
				function badge(label, on, warn) {
					const el = document.createElement("span");
					el.className = "badge" + (on ? " on" : "") + (warn ? " warn" : "");
					el.textContent = label;
					badges.append(el);
				}
				if (player.cape) badge("Cape", true, false);
				if (player.tag) badge("Tag", true, false);
				if (player.bypass) badge("Bypass", true, false);
				if (!player.bypass && player.retryIn > 0) badge(formatWait(player.retryIn), false, true);
				meta.append(badges);
				let capeBox;
				if (player.cape) {
					capeBox = document.createElement("img");
					capeBox.className = "cape";
					capeBox.alt = "Cape";
					capeBox.dataset.uuid = player.uuid;
					loadCapeAtlas(player, function (atlas) {
						if (!atlas || capeBox.dataset.uuid !== player.uuid) return;
						capeBox.src = capeFaceUrl(atlas);
					});
				} else {
					capeBox = document.createElement("div");
					capeBox.className = "nocape";
					capeBox.textContent = "No cape";
				}
				row.append(head, meta, capeBox);
				row.onclick = function () { openPlayer(player.uuid); };
				list.append(row);
			}
			if (selected && playerBy(selected)) fillDrawer(playerBy(selected));
		}

		function fillDrawer(player) {
			selected = player.uuid;
			document.getElementById("d-who").textContent = (player.name || "Unknown") + "  ·  " + player.uuid;
			document.getElementById("d-note").value = player.note || "";
			document.getElementById("d-bypass").checked = Boolean(player.bypass);
			document.getElementById("d-url").value = "";
			document.getElementById("d-nname").textContent = player.name || "Unknown";
			const tagEl = document.getElementById("d-htag");
			tagEl.innerHTML = "";
			if (player.tag) {
				tagEl.hidden = false;
				tagEl.appendChild(renderLegacy(player.tag));
			} else {
				tagEl.hidden = true;
			}
			document.getElementById("d-nocape").hidden = Boolean(player.cape);
			const wings = player.wings || {};
			document.getElementById("d-wings").value = wings.style || "";
			document.getElementById("d-wingrgb").value = "#" + (wings.rgb || WING_COLORS[wings.style] || "F4F6FF");
			document.getElementById("d-dl").disabled = !player.cape;
			document.getElementById("d-reset").disabled = !(player.retryIn > 0);
			loadModel(player);
		}

		function ensureViewer() {
			if (skinViewer) return skinViewer;
			if (!window.skinview3d || !skinview3d.SkinViewer) return null;
			const canvas = document.getElementById("d-model");
			skinViewer = new skinview3d.SkinViewer({
				canvas: canvas,
				width: Math.max(220, canvas.clientWidth || 360),
				height: STAGE_H,
				zoom: 0.7
			});
			skinViewer.autoRotate = true;
			skinViewer.autoRotateSpeed = 0.38;
			skinViewer.controls.enableZoom = false;
			skinViewer.controls.enablePan = false;
			return skinViewer;
		}

		function loadModel(player) {
			const viewer = ensureViewer();
			if (!viewer) return;
			viewer.renderPaused = false;
			const stage = document.getElementById("d-stage");
			viewer.setSize(Math.max(220, stage.clientWidth || 360), STAGE_H);
			viewer.loadSkin("https://crafthead.net/skin/" + player.uuid).catch(function () {
				return viewer.loadSkin("https://mc-heads.net/skin/" + player.uuid);
			});
			if (player.cape) {
				applyCape(viewer, player);
			} else {
				capeLoadGen++;
				viewer.loadCape(null);
			}
		}

		function applyCape(viewer, player) {
			const gen = ++capeLoadGen;
			loadCapeAtlas(player, function (atlas) {
				if (gen !== capeLoadGen) return;
				if (!atlas) {
					viewer.loadCape(null);
					return;
				}
				try {
					viewer.loadCape(atlas);
				} catch (error) {
					viewer.loadCape(null);
				}
			});
		}

		function capeSrc(player) {
			return "/capes/" + player.uuid + ".png?h=" + encodeURIComponent(player.hash || Date.now());
		}

		function loadCapeAtlas(player, done) {
			const key = player.uuid + ":" + (player.hash || "");
			if (Object.prototype.hasOwnProperty.call(atlasCache, key)) {
				done(atlasCache[key]);
				return;
			}
			if (!atlasWait[key]) atlasWait[key] = [];
			atlasWait[key].push(done);
			if (atlasWait[key].length > 1) return;
			const img = new Image();
			img.crossOrigin = "anonymous";
			img.onload = function () {
				let atlas = null;
				try {
					atlas = capeAtlas(img);
				} catch (error) {
					atlas = null;
				}
				finishCapeAtlas(key, atlas);
			};
			img.onerror = function () {
				finishCapeAtlas(key, null);
			};
			img.src = capeSrc(player);
		}

		function finishCapeAtlas(key, atlas) {
			atlasCache[key] = atlas;
			const waiting = atlasWait[key] || [];
			delete atlasWait[key];
			for (let i = 0; i < waiting.length; i++) waiting[i](atlas);
		}

		function capeFaceUrl(atlas) {
			const scale = atlas.width / 64;
			const face = document.createElement("canvas");
			face.width = 40;
			face.height = 64;
			const ctx = face.getContext("2d");
			ctx.imageSmoothingEnabled = false;
			ctx.drawImage(atlas, scale, scale, 10 * scale, 16 * scale, 0, 0, 40, 64);
			return face.toDataURL("image/png");
		}

		function capeAtlas(img) {
			const w = img.width;
			const h = img.height;
			if (w >= 64 && h >= 32 && w % 64 === 0 && h % 32 === 0 && w / 64 === h / 32) {
				return img;
			}
			return bakeCape(img);
		}

		function bakeCape(img) {
			const srcCanvas = document.createElement("canvas");
			srcCanvas.width = img.width;
			srcCanvas.height = img.height;
			const srcCtx = srcCanvas.getContext("2d", { willReadFrequently: true });
			srcCtx.drawImage(img, 0, 0);
			const src = srcCtx.getImageData(0, 0, img.width, img.height);
			const sw = src.width;
			const sh = src.height;
			const sd = src.data;
			const scale = pickCapeScale(sw, sh);
			const aw = 64 * scale;
			const ah = 32 * scale;
			const out = document.createElement("canvas");
			out.width = aw;
			out.height = ah;
			const ctx = out.getContext("2d");
			const dest = ctx.createImageData(aw, ah);
			const dd = dest.data;
			for (let i = 0; i < dd.length; i += 4) {
				dd[i] = 0;
				dd[i + 1] = 0;
				dd[i + 2] = 0;
				dd[i + 3] = 255;
			}
			const pad = capeBorder(sd, sw, sh);
			const fu = scale;
			const fv = scale;
			const fw = 10 * scale;
			const fh = 16 * scale;
			paintCapeFace(sd, sw, sh, dd, aw, fu, fv, fw, fh, pad);
			copyCapeRect(dd, aw, fu, fv, 11 * scale, 0, fw, fh);
			paintCapeEdges(dd, aw, scale);
			ctx.putImageData(dest, 0, 0);
			return out;
		}

		function pickCapeScale(sw, sh) {
			const needed = Math.max(4, Math.min(16, Math.max(Math.floor(sw / 10), Math.floor(sh / 16))));
			return Math.min(16, Math.max(4, needed));
		}

		function capeBorder(sd, sw, sh) {
			let r = 0;
			let g = 0;
			let b = 0;
			let n = 0;
			function add(x, y) {
				const i = (y * sw + x) * 4;
				r += sd[i];
				g += sd[i + 1];
				b += sd[i + 2];
				n++;
			}
			for (let x = 0; x < sw; x++) {
				add(x, 0);
				add(x, sh - 1);
			}
			for (let y = 1; y < sh - 1; y++) {
				add(0, y);
				add(sw - 1, y);
			}
			if (!n) return [0, 0, 0, 255];
			return [Math.floor(r / n), Math.floor(g / n), Math.floor(b / n), 255];
		}

		function paintCapeFace(sd, sw, sh, dd, aw, dx, dy, dw, dh, pad) {
			const fit = Math.min(dw / sw, dh / sh);
			const rw = sw * fit;
			const rh = sh * fit;
			const ox = dx + (dw - rw) * 0.5;
			const oy = dy + (dh - rh) * 0.5;
			const bilinear = fit < 1;
			for (let y = 0; y < dh; y++) {
				for (let x = 0; x < dw; x++) {
					const px = dx + x + 0.5;
					const py = dy + y + 0.5;
					const di = ((dy + y) * aw + (dx + x)) * 4;
					if (px < ox || py < oy || px >= ox + rw || py >= oy + rh) {
						dd[di] = pad[0];
						dd[di + 1] = pad[1];
						dd[di + 2] = pad[2];
						dd[di + 3] = 255;
						continue;
					}
					const sx = (px - ox) / fit - 0.5;
					const sy = (py - oy) / fit - 0.5;
					const color = bilinear ? sampleBilinear(sd, sw, sh, sx, sy) : sampleNearest(sd, sw, sh, sx, sy);
					dd[di] = color[0];
					dd[di + 1] = color[1];
					dd[di + 2] = color[2];
					dd[di + 3] = 255;
				}
			}
		}

		function paintCapeEdges(dd, aw, scale) {
			const fu = scale;
			const fv = scale;
			const fw = 10 * scale;
			const fh = 16 * scale;
			for (let y = 0; y < fh; y++) {
				const left = ((fv + y) * aw + fu) * 4;
				const right = ((fv + y) * aw + (fu + fw - 1)) * 4;
				for (let x = 0; x < scale; x++) {
					copyPixel(dd, left, ((fv + y) * aw + x) * 4);
					copyPixel(dd, right, ((fv + y) * aw + (11 * scale + x)) * 4);
				}
			}
			for (let x = 0; x < fw; x++) {
				const top = (fv * aw + (fu + x)) * 4;
				const bottom = ((fv + fh - 1) * aw + (fu + x)) * 4;
				for (let y = 0; y < scale; y++) {
					copyPixel(dd, top, (y * aw + (fu + x)) * 4);
					copyPixel(dd, bottom, (y * aw + (11 * scale + x)) * 4);
				}
			}
		}

		function copyCapeRect(dd, aw, x, y, dx, dy, w, h) {
			for (let j = 0; j < h; j++) {
				for (let i = 0; i < w; i++) {
					copyPixel(dd, ((y + j) * aw + (x + i)) * 4, ((y + dy + j) * aw + (x + dx + i)) * 4);
				}
			}
		}

		function copyPixel(dd, from, to) {
			dd[to] = dd[from];
			dd[to + 1] = dd[from + 1];
			dd[to + 2] = dd[from + 2];
			dd[to + 3] = dd[from + 3];
		}

		function sampleNearest(sd, sw, sh, x, y) {
			const sx = clampCape(Math.floor(x + 0.5), 0, sw - 1);
			const sy = clampCape(Math.floor(y + 0.5), 0, sh - 1);
			const i = (sy * sw + sx) * 4;
			return [sd[i], sd[i + 1], sd[i + 2]];
		}

		function sampleBilinear(sd, sw, sh, x, y) {
			const x0 = clampCape(Math.floor(x), 0, sw - 1);
			const y0 = clampCape(Math.floor(y), 0, sh - 1);
			const x1 = clampCape(x0 + 1, 0, sw - 1);
			const y1 = clampCape(y0 + 1, 0, sh - 1);
			const tx = x - Math.floor(x);
			const ty = y - Math.floor(y);
			return mixCape(
				mixCape(pixelAt(sd, sw, x0, y0), pixelAt(sd, sw, x1, y0), tx),
				mixCape(pixelAt(sd, sw, x0, y1), pixelAt(sd, sw, x1, y1), tx),
				ty
			);
		}

		function pixelAt(sd, sw, x, y) {
			const i = (y * sw + x) * 4;
			return [sd[i], sd[i + 1], sd[i + 2]];
		}

		function mixCape(a, b, t) {
			if (t < 0) t = 0;
			if (t > 1) t = 1;
			return [
				Math.round(a[0] + (b[0] - a[0]) * t),
				Math.round(a[1] + (b[1] - a[1]) * t),
				Math.round(a[2] + (b[2] - a[2]) * t)
			];
		}

		function clampCape(value, min, max) {
			return Math.max(min, Math.min(max, value));
		}

		function openPlayer(id) {
			const player = playerBy(id);
			if (!player) return;
			drawer.hidden = false;
			fillDrawer(player);
			requestAnimationFrame(function () {
				if (selected === player.uuid) loadModel(player);
			});
		}

		function closeDrawer() {
			drawer.hidden = true;
			selected = "";
			if (skinViewer) skinViewer.renderPaused = true;
		}

		async function loadPlayers(force) {
			const response = await fetch("/api/whitelist", {
				method: "GET",
				credentials: "same-origin",
				headers: { "X-Admin": key }
			});
			const data = await response.json().catch(function () { return {}; });
			if (kickAuth(response)) throw new Error("Not authorized");
			if (!response.ok) throw new Error(data.error || "Failed");
			armDesk();
			draw(data.players || []);
			if (force) setStatus(true, "Names refreshed from Mojang.");
			else setStatus(true, "Loaded " + cache.length + " players.");
		}

		document.querySelectorAll("#filters .chip").forEach(function (btn) {
			btn.onclick = function () {
				filter = btn.dataset.filter;
				document.querySelectorAll("#filters .chip").forEach(function (el) { el.classList.toggle("on", el === btn); });
				draw(cache);
			};
		});
		search.oninput = function () { draw(cache); };
		sort.onchange = function () { draw(cache); };
		document.getElementById("add").onclick = function () {
			if (!uuid.value.trim()) { setStatus(false, "Enter a username or UUID"); return; }
			api("PUT", uuid.value.trim()).then(function (data) {
				draw(data.players || []);
				uuid.value = "";
				setStatus(true, "Whitelisted. They can set a cape in Stray.");
			}).catch(function (error) { setStatus(false, error.message); });
		};
		uuid.addEventListener("keydown", function (event) { if (event.key === "Enter") document.getElementById("add").click(); });
		document.getElementById("refresh").onclick = function () {
			loadPlayers(true).catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("export").onclick = function () {
			const blob = new Blob([JSON.stringify(cache, null, 2)], { type: "application/json" });
			const a = document.createElement("a");
			a.href = URL.createObjectURL(blob);
			a.download = "stray-whitelist.json";
			a.click();
		};
		document.getElementById("bulkadd").onclick = function () {
			admin("/api/bulk", "PUT", { text: document.getElementById("bulktext").value }).then(function (data) {
				draw(data.players || []);
				document.getElementById("bulktext").value = "";
				const fail = (data.failed || []).join(", ");
				setStatus(true, "Added " + data.added + ". Skipped " + data.skipped + "." + (fail ? " Unknown: " + fail : ""));
			}).catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("out").onclick = function () {
			disarmDesk();
			sessionStorage.removeItem("stray-admin");
			fetch("/api/logout", { method: "POST", credentials: "same-origin" }).finally(function () {
				location.replace("/admin");
			});
		};
		document.getElementById("close").onclick = closeDrawer;
		drawer.addEventListener("click", function (event) { if (event.target === drawer) closeDrawer(); });
		window.addEventListener("resize", function () {
			if (drawer.hidden || !skinViewer || !selected) return;
			skinViewer.setSize(Math.max(220, document.getElementById("d-stage").clientWidth), STAGE_H);
		});
		document.getElementById("d-copy").onclick = function () {
			if (!selected) return;
			navigator.clipboard.writeText(selected).then(function () { setStatus(true, "UUID copied."); });
		};
		function uploadCapeBlob(blob) {
			if (!deskLive) return Promise.reject(new Error("Not authorized"));
			return fetch("/api/cape", { method: "PUT", credentials: "same-origin", headers: { "X-UUID": selected, "X-Admin": key }, body: blob })
				.then(function (response) {
					return response.json().then(function (data) {
						if (kickAuth(response)) throw new Error("Not authorized");
						if (!response.ok) throw new Error(data.error || "Upload failed");
					});
				})
				.then(function () { return loadPlayers(false); })
				.then(function () { setStatus(true, "Cape updated."); });
		}
		document.getElementById("d-upload").onclick = function () { document.getElementById("d-file").click(); };
		document.getElementById("d-file").onchange = function () {
			const file = document.getElementById("d-file").files[0];
			if (!file || !selected) return;
			if (!window.StrayCapeCrop) {
				uploadCapeBlob(file).catch(function (error) { setStatus(false, error.message); }).finally(function () { document.getElementById("d-file").value = ""; });
				return;
			}
			StrayCapeCrop.open(file, function (png) {
				uploadCapeBlob(png).catch(function (error) { setStatus(false, error.message); }).finally(function () { document.getElementById("d-file").value = ""; });
			}, function () { document.getElementById("d-file").value = ""; });
		};
		document.getElementById("d-urlgo").onclick = function () {
			const url = document.getElementById("d-url").value.trim();
			if (!url || !selected) return;
			fetch("/api/cape/import", {
				method: "POST",
				credentials: "same-origin",
				headers: { "Content-Type": "application/json", "X-Admin": key },
				body: JSON.stringify({ url: url })
			}).then(function (response) {
				if (kickAuth(response)) throw new Error("Not authorized");
				if (!response.ok) {
					return response.json().then(function (data) { throw new Error(data.error || "Fetch failed"); });
				}
				return response.blob();
			}).then(function (blob) {
				if (!window.StrayCapeCrop) return uploadCapeBlob(blob);
				return new Promise(function (resolve, reject) {
					StrayCapeCrop.open(blob, function (png) { uploadCapeBlob(png).then(resolve).catch(reject); }, function () { resolve(); });
				});
			}).catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("d-note").addEventListener("change", function () {
			if (!selected) return;
			admin("/api/note", "PUT", { uuid: selected, note: document.getElementById("d-note").value })
				.then(function (data) { draw(data.players || []); setStatus(true, "Note saved."); })
				.catch(function (error) { setStatus(false, error.message); });
		});
		document.getElementById("d-bypass").onchange = function () {
			if (!selected) return;
			admin("/api/bypass", "PUT", { uuid: selected, bypass: document.getElementById("d-bypass").checked })
				.then(function (data) { draw(data.players || []); setStatus(true, document.getElementById("d-bypass").checked ? "Bypass on." : "Bypass off."); })
				.catch(function (error) { setStatus(false, error.message); });
		};
		const WING_COLORS = { angel: "F4F6FF", demon: "2B1D2E", fairy: "9AE6FF", phoenix: "FF7A1A", butterfly: "3D98CB" };
		document.getElementById("d-wings").onchange = function () {
			const style = document.getElementById("d-wings").value;
			if (style && WING_COLORS[style]) {
				document.getElementById("d-wingrgb").value = "#" + WING_COLORS[style];
			}
		};
		document.getElementById("d-wingsave").onclick = function () {
			if (!selected) return;
			const style = document.getElementById("d-wings").value;
			const rgb = document.getElementById("d-wingrgb").value.replace("#", "");
			admin("/api/wings", style ? "PUT" : "DELETE", { uuid: selected, style: style, rgb: rgb })
				.then(function (data) { draw(data.players || []); setStatus(true, style ? "Wings saved. They show on the next world join." : "Wings removed."); })
				.catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("d-reset").onclick = function () {
			if (!selected) return;
			admin("/api/cooldown", "DELETE", { uuid: selected })
				.then(function (data) { draw(data.players || []); setStatus(true, "Cooldown cleared."); })
				.catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("d-dl").onclick = function () {
			if (!selected) return;
			const a = document.createElement("a");
			a.href = "/capes/" + selected + ".png";
			a.download = selected + ".png";
			a.click();
		};
		document.getElementById("d-tag").onclick = function () {
			const player = playerBy(selected);
			if (!player) return;
			tagTarget = player.uuid;
			tagwho.textContent = (player.name || "Unknown") + "  " + player.uuid;
			tagtext.value = player.tag || "";
			tagbox.hidden = false;
			paintPreview();
		};
		document.getElementById("d-kick").onclick = function () {
			if (!selected) return;
			if (!confirm("Remove this player from the cape list?")) return;
			api("DELETE", selected).then(function (data) {
				closeDrawer();
				draw(data.players || []);
				setStatus(true, "Removed from the list.");
			}).catch(function (error) { setStatus(false, error.message); });
		};
		function closeTag() { tagbox.hidden = true; tagTarget = ""; }
		async function saveTag(clear) {
			if (!tagTarget) return;
			try {
				const data = await admin("/api/tag", clear ? "DELETE" : "PUT", { uuid: tagTarget, tag: tagtext.value });
				draw(data.players || []);
				closeTag();
				setStatus(true, clear || !tagtext.value.trim() ? "Head tag cleared." : "Head tag saved.");
			} catch (error) {
				setStatus(false, error.message);
			}
		}
		tagtext.addEventListener("input", paintPreview);
		document.getElementById("tagsave").onclick = function () { saveTag(false); };
		document.getElementById("tagclear").onclick = function () { saveTag(true); };
		document.getElementById("tagcancel").onclick = closeTag;
		tagbox.addEventListener("click", function (event) { if (event.target === tagbox) closeTag(); });
		if (key) {
			loadPlayers(false).catch(function (error) { setStatus(false, error.message); });
		}
	</script>
</body>
</html>
`;
