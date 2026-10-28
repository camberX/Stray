var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// worker.js
var MAX_BYTES = 2 * 1024 * 1024;
var UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
var NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
var SERVER_RE = /^[A-Za-z0-9_\-]{1,64}$/;
var IRC_COOL_MS = 1500;
var PING_COOL_MS = 3e3;
var IRC_MAX = 180;
var HISTORY_MAX = 30;
var PING_LIFE_MS = 3e4;
var StrayLive = class {
  static {
    __name(this, "StrayLive");
  }
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.recent = [];
  }
  async fetch(request) {
    const casinoUrl = new URL(request.url);
    if (casinoUrl.hostname === CASINO_HOST) {
      return casinoHandle(this.ctx.storage, casinoUrl.pathname.slice("/casino".length), await request.json());
    }
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
      return;
    }
    if (type === "ping_clear") {
      this.onPingClear(ws, meta, msg);
      return;
    }
    if (type === "users") {
      this.onUsers(ws);
    }
  }
  async webSocketClose(ws) {
    try {
      ws.close(1e3, "bye");
    } catch {
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
    ws.send(JSON.stringify({ type: "hello", ok: true, users: this.onlineUsers() }));
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
  onUsers(ws) {
    ws.send(JSON.stringify({ type: "users", users: this.onlineUsers() }));
  }
  onlineUsers() {
    const seen = /* @__PURE__ */ new Set();
    for (const socket of this.ctx.getWebSockets()) {
      const meta = socket.deserializeAttachment() || {};
      if (meta.uuid) {
        seen.add(meta.uuid);
      }
    }
    return seen.size;
  }
  onPing(ws, meta, msg) {
    const now = Date.now();
    if (now - meta.pingAt < PING_COOL_MS) {
      ws.send(JSON.stringify({ type: "error", message: "Slow down." }));
      return;
    }
    const server = cleanServer(msg.server) || meta.server || "world";
    if (!server) {
      ws.send(JSON.stringify({ type: "error", message: "Join a lobby to ping." }));
      return;
    }
    if (server !== meta.server) {
      meta.server = server;
    }
    const x = toCoord(msg.x);
    const y = toCoord(msg.y);
    const z = toCoord(msg.z);
    if (x === null || y === null || z === null || y < -2048 || y > 2048) {
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
  onPingClear(ws, meta, msg) {
    const server = cleanServer(msg.server) || meta.server || "world";
    if (!server) {
      ws.send(JSON.stringify({ type: "error", message: "Join a lobby to ping." }));
      return;
    }
    if (server !== meta.server) {
      meta.server = server;
      ws.serializeAttachment(meta);
    }
    this.broadcast(
      { type: "ping_clear", name: meta.name, uuid: meta.uuid, server, at: Date.now() },
      (other) => other.server === meta.server
    );
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
      }
    }
  }
};
function cleanServer(value) {
  const text = String(value || "").trim();
  return SERVER_RE.test(text) ? text : "";
}
__name(cleanServer, "cleanServer");
function cleanIrc(value) {
  let text = String(value || "").replace(/[\r\n\u0000-\u001f]/g, " ").replace(/§./g, "").trim();
  if (text.length > IRC_MAX) {
    text = text.slice(0, IRC_MAX);
  }
  return text;
}
__name(cleanIrc, "cleanIrc");
function toCoord(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return null;
  }
  const v = Math.round(n * 20) / 20;
  if (v < -3e7 || v > 3e7) {
    return null;
  }
  return v;
}
__name(toCoord, "toCoord");
var worker_default = {
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
  if (path === "/api/casino" || path.startsWith("/api/casino/")) {
    // Tag every casino answer, so the mod can tell it apart from a 404 served by some other
    // Worker version or route (gradual deployments, an old version still bound somewhere).
    const casinoRes = await casinoRoute(request, env, path);
    const tagged = new Response(casinoRes.body, casinoRes);
    tagged.headers.set("X-Stray-Casino", "4");
    return tagged;
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
  if (request.method === "GET" && path === "/api/mod/dev") {
    return serveR2Json(env, "mod/dev/dev.json", "No dev build published yet");
  }
  if (request.method === "GET" && path === "/download/dev") {
    return serveR2Jar(env, "mod/dev/stray-dev.jar", "stray-dev.jar");
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
__name(route, "route");
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
    return json(429, { error: "Cape can be changed once per 24 hours", retryIn: Math.ceil(locked / 1e3) });
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
__name(handlePublish, "handlePublish");
async function handleImport(request, env) {
  if (!await adminCapeOk(request, env)) {
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
      signal: AbortSignal.timeout(1e4)
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
__name(handleImport, "handleImport");
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
    return json(429, { error: "Cape can be changed once per 24 hours", retryIn: Math.ceil(locked / 1e3) });
  }
  await env.CAPES.delete(capeKey(uuid));
  if (!adminOk) {
    touchCapeAt(state, uuid);
    await saveState(env, state);
  }
  return json(200, { ok: true });
}
__name(handleDelete, "handleDelete");
async function handleWhitelist(request, env) {
  const admin = env.ADMIN || "";
  if (!admin) {
    return json(500, { error: "Admin key is not set on the Worker" });
  }
  if (request.method === "GET") {
    if (!await deskSessionOk(request, env)) {
      return json(403, { error: "Not authorized" });
    }
    const state2 = await loadState(env);
    const players2 = await playersFor(env, state2, true);
    await saveState(env, state2);
    return json(200, { uuids: state2.whitelist, players: players2 });
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
  if (!await deskCookieOk(request, env)) {
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
__name(handleWhitelist, "handleWhitelist");
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
__name(handleTag, "handleTag");
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
__name(handleWings, "handleWings");
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
__name(handleBypass, "handleBypass");
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
__name(handleNote, "handleNote");
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
__name(handleCooldown, "handleCooldown");
async function handleBulk(request, env) {
  const checked = await adminBody(request, env);
  if (checked.error) {
    return checked.error;
  }
  const raw = Array.isArray(checked.body.names) ? checked.body.names : String(checked.body.text || "").split(/[\n,]+/);
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
__name(handleBulk, "handleBulk");
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
__name(handleShopConfig, "handleShopConfig");
async function adminBody(request, env) {
  const admin = env.ADMIN || "";
  if (!admin) {
    return { error: json(500, { error: "Admin key is not set on the Worker" }) };
  }
  if (!await deskCookieOk(request, env)) {
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
__name(adminBody, "adminBody");
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
        signal: AbortSignal.timeout(1e4)
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
__name(readCapeBytes, "readCapeBytes");
function tagFor(state, uuid) {
  return sanitizeTag(objectMap(state.tags)[uuid]);
}
__name(tagFor, "tagFor");
var WING_STYLES = ["angel", "demon", "fairy", "phoenix", "butterfly"];
var WING_DEFAULT_RGB = { angel: "F4F6FF", demon: "2B1D2E", fairy: "9AE6FF", phoenix: "FF7A1A", butterfly: "3D98CB" };
function sanitizeWingStyle(value) {
  const style = String(value || "").trim().toLowerCase();
  return WING_STYLES.includes(style) ? style : "";
}
__name(sanitizeWingStyle, "sanitizeWingStyle");
function sanitizeRgb(value) {
  const hex = String(value || "").trim().replace(/^#/, "").toUpperCase();
  return /^[0-9A-F]{6}$/.test(hex) ? hex : "";
}
__name(sanitizeRgb, "sanitizeRgb");
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
__name(wingsFor, "wingsFor");
function noteFor(state, uuid) {
  return sanitizeNote(objectMap(state.notes)[uuid]);
}
__name(noteFor, "noteFor");
function shopConfig(state, env) {
  const stored = objectMap(state.config);
  return {
    title: stored.title || env.TITLE || "STRAY Capes",
    discord: "@evilkitten911",
    blurb: stored.blurb || ""
  };
}
__name(shopConfig, "shopConfig");
var MAX_TAG = 48;
var MAX_NOTE = 160;
var DAY_MS = 24 * 60 * 60 * 1e3;
function sanitizeTag(value) {
  return String(value || "").replace(/[\u0000-\u001f\\"]/g, "").replace(/\s+/g, " ").trim().slice(0, MAX_TAG);
}
__name(sanitizeTag, "sanitizeTag");
function sanitizeNote(value) {
  return String(value || "").replace(/[\u0000-\u001f]/g, "").replace(/\s+/g, " ").trim().slice(0, MAX_NOTE);
}
__name(sanitizeNote, "sanitizeNote");
function sanitizePaypal(value) {
  return String(value || "").replace(/\s+/g, "").slice(0, 80);
}
__name(sanitizePaypal, "sanitizePaypal");
function sanitizePrice(value) {
  const price = String(value || "").replace(/\s+/g, " ").trim().slice(0, 24);
  return price || "$1";
}
__name(sanitizePrice, "sanitizePrice");
function sanitizeTitle(value) {
  const title = String(value || "").replace(/\s+/g, " ").trim().slice(0, 48);
  return title || "STRAY Capes";
}
__name(sanitizeTitle, "sanitizeTitle");
function sanitizeBlurb(value) {
  return String(value || "").replace(/[\u0000-\u001f]/g, "").replace(/\s+/g, " ").trim().slice(0, 280);
}
__name(sanitizeBlurb, "sanitizeBlurb");
function objectMap(value) {
  return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}
__name(objectMap, "objectMap");
function hasBypass(state, uuid) {
  return Boolean(objectMap(state.bypass)[uuid]);
}
__name(hasBypass, "hasBypass");
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
__name(capeRetryMs, "capeRetryMs");
function capeRetrySec(state, uuid) {
  return Math.ceil(capeRetryMs(state, uuid) / 1e3);
}
__name(capeRetrySec, "capeRetrySec");
function touchCapeAt(state, uuid) {
  state.capeAt = objectMap(state.capeAt);
  state.capeAt[uuid] = Date.now();
}
__name(touchCapeAt, "touchCapeAt");
function adminHeaderOk(request, env) {
  const admin = env.ADMIN || "";
  return Boolean(admin) && (request.headers.get("x-admin") || "") === admin;
}
__name(adminHeaderOk, "adminHeaderOk");
async function adminCapeOk(request, env) {
  return adminHeaderOk(request, env) && await deskCookieOk(request, env);
}
__name(adminCapeOk, "adminCapeOk");
async function deskSessionOk(request, env) {
  return adminHeaderOk(request, env) && await deskCookieOk(request, env);
}
__name(deskSessionOk, "deskSessionOk");
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
__name(playersFor, "playersFor");
var NAME_TTL_MS = 10 * 60 * 1e3;
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
__name(rememberName, "rememberName");
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
__name(forgetPlayer, "forgetPlayer");
function sanitizeUsername(value) {
  const name = String(value || "").trim();
  return /^[A-Za-z0-9_]{1,16}$/.test(name) ? name : "";
}
__name(sanitizeUsername, "sanitizeUsername");
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
__name(resolvePlayer, "resolvePlayer");
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
__name(mojangName, "mojangName");
async function lookupName(uuid) {
  const id = String(uuid || "").replaceAll("-", "");
  if (id.length !== 32) {
    return "";
  }
  const dashed = `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`;
  const attempts = [
    { url: "https://sessionserver.mojang.com/session/minecraft/profile/" + id, read: /* @__PURE__ */ __name((data) => data.name, "read") },
    { url: "https://crafthead.net/profile/" + id, read: /* @__PURE__ */ __name((data) => data.name, "read") },
    { url: "https://mowojang.matdoes.dev/" + dashed, read: /* @__PURE__ */ __name((data) => data.name, "read") },
    { url: "https://playerdb.co/api/player/minecraft/" + dashed, read: /* @__PURE__ */ __name((data) => data?.data?.player?.username, "read") },
    { url: "https://api.ashcon.app/mojang/v2/user/" + dashed, read: /* @__PURE__ */ __name((data) => data.username || data.name, "read") }
  ];
  return firstString(attempts);
}
__name(lookupName, "lookupName");
async function lookupUuid(name) {
  const encoded = encodeURIComponent(name);
  const attempts = [
    { url: "https://api.mojang.com/users/profiles/minecraft/" + encoded, read: /* @__PURE__ */ __name((data) => data.id, "read") },
    { url: "https://mowojang.matdoes.dev/" + encoded, read: /* @__PURE__ */ __name((data) => data.id, "read") },
    { url: "https://crafthead.net/profile/" + encoded, read: /* @__PURE__ */ __name((data) => data.id, "read") },
    { url: "https://playerdb.co/api/player/minecraft/" + encoded, read: /* @__PURE__ */ __name((data) => data?.data?.player?.id || data?.data?.player?.raw_id, "read") },
    { url: "https://api.ashcon.app/mojang/v2/user/" + encoded, read: /* @__PURE__ */ __name((data) => data.uuid, "read") }
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
__name(lookupUuid, "lookupUuid");
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
__name(firstString, "firstString");
async function fetchJson(url) {
  try {
    const response = await fetch(url, {
      headers: { "User-Agent": "Stray" },
      signal: AbortSignal.timeout(5e3)
    });
    if (!response.ok) {
      return null;
    }
    return await response.json();
  } catch {
    return null;
  }
}
__name(fetchJson, "fetchJson");
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
__name(loadState, "loadState");
async function saveState(env, state) {
  const copy = { ...state };
  delete copy.bans;
  await env.CAPES.put("state.json", JSON.stringify(copy));
}
__name(saveState, "saveState");
function capeKey(uuid) {
  return `capes/${uuid}.png`;
}
__name(capeKey, "capeKey");
async function hashBytes(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 16);
}
__name(hashBytes, "hashBytes");
function normalizeUuid(value) {
  const raw = (value || "").trim().toLowerCase().replace(/[^0-9a-f]/g, "");
  if (raw.length !== 32) {
    return "";
  }
  const dashed = `${raw.slice(0, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20)}`;
  return UUID_RE.test(dashed) ? dashed : "";
}
__name(normalizeUuid, "normalizeUuid");
function isPng(bytes) {
  return bytes.length >= 24 && bytes.length <= MAX_BYTES && bytes[0] === 137 && bytes[1] === 80 && bytes[2] === 78 && bytes[3] === 71;
}
__name(isPng, "isPng");
function json(status, body, extraHeaders) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...cors(),
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      ...extraHeaders || {}
    }
  });
}
__name(json, "json");
var DESK_COOKIE = "stray_desk";
var DESK_TTL_SEC = 60 * 60 * 24 * 7;
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
__name(canonicalizePath, "canonicalizePath");
function isManagePath(path) {
  return path === "/manage";
}
__name(isManagePath, "isManagePath");
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
__name(cookieOf, "cookieOf");
function deskSecure(request) {
  try {
    return new URL(request.url).protocol === "https:";
  } catch {
    return true;
  }
}
__name(deskSecure, "deskSecure");
function deskCookieHeader(request, token, clear) {
  const parts = [
    DESK_COOKIE + "=" + (clear ? "" : token),
    "Path=/",
    "HttpOnly",
    "SameSite=Lax",
    clear ? "Max-Age=0" : "Max-Age=" + DESK_TTL_SEC
  ];
  if (deskSecure(request)) {
    parts.push("Secure");
  }
  return parts.join("; ");
}
__name(deskCookieHeader, "deskCookieHeader");
function bytesToHex(bytes) {
  const arr = new Uint8Array(bytes);
  let out = "";
  for (let i = 0; i < arr.length; i++) {
    out += arr[i].toString(16).padStart(2, "0");
  }
  return out;
}
__name(bytesToHex, "bytesToHex");
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
__name(timingSafeEqualStr, "timingSafeEqualStr");
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
__name(hmacHex, "hmacHex");
async function mintDeskToken(secret) {
  const exp = Math.floor(Date.now() / 1e3) + DESK_TTL_SEC;
  const msg = "v1." + exp;
  return msg + "." + await hmacHex(secret, msg);
}
__name(mintDeskToken, "mintDeskToken");
async function verifyDeskToken(secret, token) {
  const parts = String(token || "").split(".");
  if (parts.length !== 3 || parts[0] !== "v1") {
    return false;
  }
  const exp = Number(parts[1]);
  if (!Number.isFinite(exp) || exp < Date.now() / 1e3) {
    return false;
  }
  const sig = await hmacHex(secret, "v1." + parts[1]);
  return timingSafeEqualStr(sig, parts[2]);
}
__name(verifyDeskToken, "verifyDeskToken");
async function deskCookieOk(request, env) {
  const secret = env.ADMIN || "";
  if (!secret) {
    return false;
  }
  return verifyDeskToken(secret, cookieOf(request, DESK_COOKIE));
}
__name(deskCookieOk, "deskCookieOk");
async function adminJson(request, env, status, body) {
  const token = await mintDeskToken(env.ADMIN || "");
  return json(status, body, { "Set-Cookie": deskCookieHeader(request, token, false) });
}
__name(adminJson, "adminJson");
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
__name(redirectLogin, "redirectLogin");
async function serveManage(request, env) {
  if (!await deskCookieOk(request, env)) {
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
__name(serveManage, "serveManage");
var DEFAULT_MOD_GITHUB = "camberX/Stray";
var LEGACY_MOD_GITHUB = "camberX/voidmark";
var DEFAULT_MOD_BRANCH = "main";
var DEFAULT_MOD_PATH = "web/public/mod";
var MOD_R2_PREFIX = "mod/";
async function readR2Meta(env) {
  if (!env.CAPES) {
    return null;
  }
  try {
    const object = await env.CAPES.get(MOD_R2_PREFIX + "latest.json");
    if (!object) {
      return null;
    }
    const meta = await object.json();
    if (!meta || !meta.version) {
      return null;
    }
    meta.source = "r2";
    return meta;
  } catch {
    return null;
  }
}
__name(readR2Meta, "readR2Meta");
async function readModMeta(request, env) {
  // Jars are published to R2. Git (jsDelivr / raw) and the bundled assets are only
  // fallbacks for old commits until R2 has been seeded.
  const r2 = await readR2Meta(env);
  if (r2) {
    return r2;
  }
  const github = await readGithubMeta(env);
  const asset = await readAssetMeta(request, env);
  if (github && github.version && asset && asset.version) {
    return compareModVersion(github.version, asset.version) >= 0 ? github : asset;
  }
  return github || asset || null;
}
__name(readModMeta, "readModMeta");
async function readAssetMeta(request, env) {
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
    }
  }
  return null;
}
__name(readAssetMeta, "readAssetMeta");
function cleanGithubRepo(value) {
  return String(value || "").trim().replace(/^https?:\/\/github\.com\//i, "").replace(/\.git$/i, "").replace(/\/+$/, "");
}
__name(cleanGithubRepo, "cleanGithubRepo");
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
  __name(add, "add");
  add(DEFAULT_MOD_GITHUB);
  add("camberX/Eisenmann");
  add(LEGACY_MOD_GITHUB);
  add(env && env.MOD_GITHUB);
  return list;
}
__name(modGithubRepos, "modGithubRepos");
function modGithubBranch(env, meta) {
  return String(meta && meta.branch || env && env.MOD_GITHUB_BRANCH || DEFAULT_MOD_BRANCH).trim() || DEFAULT_MOD_BRANCH;
}
__name(modGithubBranch, "modGithubBranch");
function modGithubDir(env, meta) {
  return String(meta && meta.dir || env && env.MOD_GITHUB_PATH || DEFAULT_MOD_PATH).replace(/^\/+|\/+$/g, "") || DEFAULT_MOD_PATH;
}
__name(modGithubDir, "modGithubDir");
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
__name(githubFileUrls, "githubFileUrls");
function githubFetchHeaders(url) {
  const headers = { "User-Agent": "Stray-Shop" };
  if (url.includes("api.github.com")) {
    headers.Accept = "application/vnd.github.raw";
  }
  return headers;
}
__name(githubFetchHeaders, "githubFetchHeaders");
async function fetchGithubFile(url) {
  const response = await fetch(url, {
    headers: githubFetchHeaders(url),
    redirect: "follow",
    cf: { cacheTtl: 0, cacheEverything: false }
  });
  return response.ok ? response : null;
}
__name(fetchGithubFile, "fetchGithubFile");
function githubModFileUrls(env, meta, fileName) {
  const repo = meta && meta.repo || DEFAULT_MOD_GITHUB;
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
__name(githubModFileUrls, "githubModFileUrls");
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
      }
    }
  }
  return best;
}
__name(readGithubMeta, "readGithubMeta");
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
__name(compareModVersion, "compareModVersion");
async function serveModInfo(request, env) {
  const meta = await readModMeta(request, env);
  if (!meta || !meta.version) {
    return json(404, { error: "Mod build is not published yet" });
  }
  return json(200, {
    version: String(meta.version),
    minecraft: String(meta.minecraft || "26.1.2"),
    file: String(meta.file || "stray-" + meta.version + ".jar"),
    url: "/download",
    source: meta.source || "assets"
  });
}
__name(serveModInfo, "serveModInfo");
async function fetchModBytes(request, env, meta) {
  const names = [];
  if (meta && meta.file) {
    names.push(String(meta.file));
  }
  if (env.CAPES) {
    for (let i = 0; i < names.length; i++) {
      if (!/^[A-Za-z0-9._-]+\.jar$/.test(names[i])) {
        continue;
      }
      try {
        const object = await env.CAPES.get(MOD_R2_PREFIX + names[i]);
        if (object) {
          return { body: object.body, file: names[i] };
        }
      } catch {
      }
    }
  }
  for (let i = 0; i < names.length; i++) {
    const urls = githubModFileUrls(env, meta, names[i]);
    for (let u = 0; u < urls.length; u++) {
      try {
        const upstream = await fetchGithubFile(urls[u]);
        if (upstream) {
          return { body: upstream.body, file: names[i] };
        }
      } catch {
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
      }
    }
  }
  return null;
}
__name(fetchModBytes, "fetchModBytes");
async function serveModJar(request, env) {
  const meta = await readModMeta(request, env);
  const got = await fetchModBytes(request, env, meta);
  if (!got) {
    return json(404, { error: "Mod build is not published yet" });
  }
  const name = String(meta && meta.file || got.file || "stray.jar").replace(/"/g, "");
  const headers = new Headers();
  headers.set("Content-Type", "application/java-archive");
  headers.set("Content-Disposition", 'attachment; filename="' + name + '"');
  headers.set("Cache-Control", "no-store");
  const extra = cors();
  for (const key of Object.keys(extra)) {
    headers.set(key, extra[key]);
  }
  return new Response(got.body, { status: 200, headers });
}
__name(serveModJar, "serveModJar");
async function serveR2Json(env, key, missing) {
  const object = env.CAPES ? await env.CAPES.get(key) : null;
  if (!object) {
    return json(404, { error: missing });
  }
  const headers = new Headers({ "Content-Type": "application/json", "Cache-Control": "no-store" });
  const extra = cors();
  for (const name of Object.keys(extra)) {
    headers.set(name, extra[name]);
  }
  return new Response(object.body, { status: 200, headers });
}
__name(serveR2Json, "serveR2Json");
async function serveR2Jar(env, key, fileName) {
  const object = env.CAPES ? await env.CAPES.get(key) : null;
  if (!object) {
    return json(404, { error: "Mod build is not published yet" });
  }
  const headers = new Headers();
  headers.set("Content-Type", "application/java-archive");
  headers.set("Content-Disposition", 'attachment; filename="' + fileName + '"');
  headers.set("Cache-Control", "no-store");
  const extra = cors();
  for (const name of Object.keys(extra)) {
    headers.set(name, extra[name]);
  }
  return new Response(object.body, { status: 200, headers });
}
__name(serveR2Jar, "serveR2Jar");
function cors() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET,PUT,POST,DELETE,OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, X-UUID, X-Key, X-Code, X-Token, X-Admin"
  };
}
__name(cors, "cors");
function page(html) {
  return new Response(html, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "private, no-store, no-cache",
      Pragma: "no-cache"
    }
  });
}
__name(page, "page");
var CAPE_CROP_JS = `window.StrayCapeCrop = (function () {
	var ASPECT = 10 / 16;
	var FACE_W = 10;
	var FACE_H = 16;
	var LAYOUT_W = 64;
	var LAYOUT_H = 32;
	var MAX_SCALE = 16;
	var overlay = null;

	function isVanilla(w, h) {
		return w >= 64 && h >= 32 && w % 64 === 0 && h % 32 === 0 && w / 64 === h / 32;
	}

	function cover(srcW, srcH) {
		var srcAspect = srcW / Math.max(1, srcH);
		var crop = { x: 0, y: 0, w: 1, h: 1 };
		if (srcAspect > ASPECT) {
			crop.h = 1;
			crop.w = ASPECT / srcAspect;
			crop.x = (1 - crop.w) * 0.5;
			crop.y = 0;
		} else {
			crop.w = 1;
			crop.h = srcAspect / ASPECT;
			crop.x = 0;
			crop.y = (1 - crop.h) * 0.5;
		}
		return crop;
	}

	function clampCrop(crop, srcW, srcH) {
		srcW = Math.max(1, srcW);
		srcH = Math.max(1, srcH);
		var srcAspect = srcW / srcH;
		var normAspect = ASPECT / srcAspect;
		var max = cover(srcW, srcH);
		var minW = Math.max(10 / srcW, max.w * 0.12);
		crop.w = Math.min(max.w, Math.max(minW, crop.w));
		crop.h = crop.w / normAspect;
		if (crop.h > max.h) {
			crop.h = max.h;
			crop.w = crop.h * normAspect;
		}
		crop.x = Math.min(Math.max(0, crop.x), Math.max(0, 1 - crop.w));
		crop.y = Math.min(Math.max(0, crop.y), Math.max(0, 1 - crop.h));
	}

	function loadImage(file) {
		return new Promise(function (resolve, reject) {
			var url = URL.createObjectURL(file);
			var img = new Image();
			img.onload = function () {
				URL.revokeObjectURL(url);
				resolve(img);
			};
			img.onerror = function () {
				URL.revokeObjectURL(url);
				reject(new Error("Could not read that image."));
			};
			img.src = url;
		});
	}

	function pickScale(sw, sh) {
		var needed = Math.max(4, Math.min(MAX_SCALE, Math.max(Math.floor(sw / FACE_W), Math.floor(sh / FACE_H))));
		return Math.min(MAX_SCALE, Math.max(4, needed));
	}

	function bakeAtlas(img, crop) {
		var sw = img.width;
		var sh = img.height;
			var scale = pickScale(img.width, img.height);
		var aw = LAYOUT_W * scale;
		var ah = LAYOUT_H * scale;
		var out = document.createElement("canvas");
		out.width = aw;
		out.height = ah;
		var ctx = out.getContext("2d");
		ctx.fillStyle = "#000";
		ctx.fillRect(0, 0, aw, ah);
		ctx.imageSmoothingEnabled = crop.w * sw > 10 * scale || crop.h * sh > 16 * scale;
		var fu = scale;
		var fv = scale;
		var fw = FACE_W * scale;
		var fh = FACE_H * scale;
		var sx = crop.x * sw;
		var sy = crop.y * sh;
		var sWidth = crop.w * sw;
		var sHeight = crop.h * sh;
		ctx.drawImage(img, sx, sy, sWidth, sHeight, fu, fv, fw, fh);
		ctx.drawImage(out, fu, fv, fw, fh, 12 * scale, fv, fw, fh);
		var edge = ctx.getImageData(fu, fv, fw, fh);
		for (var y = 0; y < fh; y++) {
			var left = (y * fw) * 4;
			var right = (y * fw + fw - 1) * 4;
			for (var x = 0; x < scale; x++) {
				ctx.fillStyle = "rgb(" + edge.data[left] + "," + edge.data[left + 1] + "," + edge.data[left + 2] + ")";
				ctx.fillRect(x, fv + y, 1, 1);
				ctx.fillStyle = "rgb(" + edge.data[right] + "," + edge.data[right + 1] + "," + edge.data[right + 2] + ")";
				ctx.fillRect(11 * scale + x, fv + y, 1, 1);
			}
		}
		for (var i = 0; i < fw; i++) {
			var top = i * 4;
			var bot = ((fh - 1) * fw + i) * 4;
			for (var t = 0; t < scale; t++) {
				ctx.fillStyle = "rgb(" + edge.data[top] + "," + edge.data[top + 1] + "," + edge.data[top + 2] + ")";
				ctx.fillRect(fu + i, t, 1, 1);
				ctx.fillStyle = "rgb(" + edge.data[bot] + "," + edge.data[bot + 1] + "," + edge.data[bot + 2] + ")";
				ctx.fillRect(11 * scale + i, t, 1, 1);
			}
		}
		return out;
	}

	function canvasPng(canvas) {
		return new Promise(function (resolve, reject) {
			canvas.toBlob(function (blob) {
				if (!blob) reject(new Error("Could not encode cape."));
				else resolve(blob);
			}, "image/png");
		});
	}

	function close() {
		if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay);
		overlay = null;
	}

	function open(file, onDone, onCancel) {
		close();
		loadImage(file).then(function (img) {
			if (isVanilla(img.width, img.height)) {
				onDone(file);
				return;
			}
			var crop = cover(img.width, img.height);
			overlay = document.createElement("div");
			overlay.className = "overlay center";
			overlay.innerHTML = ""
				+ '<div class="sheet crop-sheet">'
				+ "<h1 style=\\"font-size:16px\\">CAPE CREATOR</h1>"
				+ '<p class="who">Drag to pan. Scroll or pinch to zoom. The box is the 10\xD716 face other players see.</p>'
				+ '<div class="crop-wrap">'
				+ '<div class="crop-stage" id="crop-stage"><div class="crop-holder"><canvas id="crop-src"></canvas><div id="crop-box"></div></div></div>'
				+ '<div class="crop-side"><div class="crop-face"><canvas id="crop-face"></canvas></div><p class="hint">In-game face</p></div>'
				+ "</div>"
				+ '<div class="row">'
				+ '<button type="button" class="ghost" id="crop-reset">Reset</button>'
				+ '<button type="button" class="ghost" id="crop-cancel">Cancel</button>'
				+ '<button type="button" class="primary" id="crop-apply">Apply cape</button>'
				+ "</div></div>";
			document.body.appendChild(overlay);
			var stage = overlay.querySelector("#crop-stage");
			var srcCanvas = overlay.querySelector("#crop-src");
			var faceCanvas = overlay.querySelector("#crop-face");
			var box = overlay.querySelector("#crop-box");
			var dragging = false;
			var lastX = 0;
			var lastY = 0;

			function layout() {
				var maxW = Math.min(420, stage.clientWidth || 420);
				var maxH = 280;
				var fit = Math.min(maxW / img.width, maxH / img.height);
				srcCanvas.width = Math.max(1, Math.round(img.width * fit));
				srcCanvas.height = Math.max(1, Math.round(img.height * fit));
				var sctx = srcCanvas.getContext("2d");
				sctx.imageSmoothingEnabled = true;
				sctx.drawImage(img, 0, 0, srcCanvas.width, srcCanvas.height);
				box.style.left = crop.x * srcCanvas.width + "px";
				box.style.top = crop.y * srcCanvas.height + "px";
				box.style.width = crop.w * srcCanvas.width + "px";
				box.style.height = crop.h * srcCanvas.height + "px";
				faceCanvas.width = 50;
				faceCanvas.height = 80;
				var fctx = faceCanvas.getContext("2d");
				fctx.imageSmoothingEnabled = true;
				fctx.drawImage(
					img,
					crop.x * img.width,
					crop.y * img.height,
					crop.w * img.width,
					crop.h * img.height,
					0,
					0,
					50,
					80
				);
			}

			overlay.querySelector("#crop-reset").onclick = function () {
				crop = cover(img.width, img.height);
				layout();
			};
			overlay.querySelector("#crop-cancel").onclick = function () {
				close();
				if (onCancel) onCancel();
			};
			overlay.querySelector("#crop-apply").onclick = function () {
				canvasPng(bakeAtlas(img, crop)).then(function (blob) {
					close();
					onDone(blob);
				}).catch(function (error) {
					alert(error.message);
				});
			};
			overlay.addEventListener("click", function (event) {
				if (event.target === overlay) {
					close();
					if (onCancel) onCancel();
				}
			});
			stage.addEventListener("pointerdown", function (event) {
				dragging = true;
				lastX = event.clientX;
				lastY = event.clientY;
				stage.setPointerCapture(event.pointerId);
			});
			stage.addEventListener("pointerup", function () { dragging = false; });
			stage.addEventListener("pointermove", function (event) {
				if (!dragging) return;
				var dx = (event.clientX - lastX) / srcCanvas.width;
				var dy = (event.clientY - lastY) / srcCanvas.height;
				lastX = event.clientX;
				lastY = event.clientY;
				crop.x += dx;
				crop.y += dy;
				clampCrop(crop, img.width, img.height);
				layout();
			});
			stage.addEventListener("wheel", function (event) {
				event.preventDefault();
				var rect = srcCanvas.getBoundingClientRect();
				var px = (event.clientX - rect.left) / srcCanvas.width;
				var py = (event.clientY - rect.top) / srcCanvas.height;
				var factor = event.deltaY < 0 ? 0.88 : 1.14;
				crop.x = px - (px - crop.x) * factor;
				crop.y = py - (py - crop.y) * factor;
				crop.w *= factor;
				crop.h *= factor;
				clampCrop(crop, img.width, img.height);
				layout();
			}, { passive: false });
			requestAnimationFrame(layout);
		}).catch(function (error) {
			if (onCancel) onCancel();
			alert(error.message);
		});
	}

	return { open: open, isVanilla: isVanilla };
})();
`;
var STORE_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>Stray</title>
	<meta name="description" content="A visuals oriented Hypixel Skyblock mod. World tint, ESP, HUD, mining, and custom capes.">
	<meta name="theme-color" content="#0b0c10">
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,500,1,0&display=block" rel="stylesheet">
	<style>
		/* Hyprland look shared by the site and the desk: the click GUI's Material roles,
		   frosted windows with a 1px accent border, pill bar groups and gaps between tiles. */
		@font-face { font-family: "Google Sans Flex"; src: url("/fonts/googlesansflex_regular.ttf") format("truetype"); font-weight: 400; font-display: swap; }
		@font-face { font-family: "Google Sans Flex"; src: url("/fonts/googlesansflex_medium.ttf") format("truetype"); font-weight: 500 900; font-display: swap; }
		:root {
			--primary: #2fb5ff;
			--primary-2: #322fff;
			--primary-light: color-mix(in srgb, var(--primary) 78%, #fff);
			--on-primary: #04131c;
			--bg: #0b0c10;
			--surface: #121318;
			--surface-low: #1a1b21;
			--container: #1f2027;
			--high: #292a32;
			--highest: #34353d;
			--text: #e6e7ee;
			--muted: #c4c6d0;
			--faint: #8e909b;
			--line: #46474f;
			--ok: #46dc95;
			--warn: #f5c16c;
			--danger: #f0565b;
			--radius: 18px;
			--gap: 10px;
		}
		* { box-sizing: border-box; }
		html, body { margin: 0; min-height: 100%; background: var(--bg); color: var(--text); font: 14px/1.45 "Google Sans Flex", "Google Sans", system-ui, sans-serif; }
		body::before {
			content: ""; position: fixed; inset: 0; z-index: 0; pointer-events: none;
			background:
				radial-gradient(1100px 620px at 8% -10%, color-mix(in srgb, var(--primary) 26%, transparent), transparent 62%),
				radial-gradient(900px 600px at 105% 110%, color-mix(in srgb, var(--primary-2) 22%, transparent), transparent 64%),
				radial-gradient(600px 400px at 70% 20%, color-mix(in srgb, var(--primary) 8%, transparent), transparent 70%),
				linear-gradient(180deg, #0d0e13, #08090c);
		}
		body::after {
			content: ""; position: fixed; inset: 0; z-index: 0; pointer-events: none; opacity: 0.05; mix-blend-mode: overlay;
			background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
		}
		a { color: var(--primary); }
		.win {
			position: relative; background: color-mix(in srgb, var(--surface-low) 84%, transparent);
			border: 1px solid color-mix(in srgb, var(--primary) 30%, transparent); border-radius: var(--radius);
			backdrop-filter: blur(22px) saturate(1.3); -webkit-backdrop-filter: blur(22px) saturate(1.3);
			box-shadow: 0 18px 50px #0007; transition: border-color 0.18s ease;
		}
		.win:hover, .win:focus-within { border-color: var(--primary); }
		.bar {
			position: sticky; top: 8px; z-index: 5; display: flex; align-items: center; gap: 6px;
			height: 44px; padding: 5px; border-radius: 999px;
			background: color-mix(in srgb, var(--surface) 82%, transparent);
			backdrop-filter: blur(22px) saturate(1.3); -webkit-backdrop-filter: blur(22px) saturate(1.3);
			border: 1px solid #ffffff10; box-shadow: 0 12px 40px #0008;
		}
		.group { display: flex; align-items: center; gap: 4px; height: 100%; padding: 0 4px; border-radius: 999px; background: var(--surface-low); }
		.group.pad { padding: 0 14px; }
		.ws {
			display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 12px; border-radius: 999px;
			color: var(--muted); text-decoration: none; font-weight: 500; border: 0; background: transparent; cursor: pointer; font: inherit;
			transition: background 0.18s, color 0.18s;
		}
		.ws:hover { background: #ffffff14; color: var(--text); }
		.ws.on { background: linear-gradient(90deg, var(--primary), var(--primary-2)); color: var(--on-primary); }
		.spacer { flex: 1; }
		.btn {
			display: inline-flex; align-items: center; justify-content: center; gap: 8px; height: 38px; padding: 0 18px;
			border-radius: 999px; border: 1px solid #ffffff1e; cursor: pointer; text-decoration: none;
			font: inherit; font-weight: 600; color: var(--text);
			background: linear-gradient(180deg, var(--high), var(--container));
			transition: filter 0.15s, transform 0.15s, border-color 0.15s;
		}
		.btn:hover { border-color: #ffffff38; filter: brightness(1.12); }
		.btn:active { transform: translateY(1px); }
		.btn.primary {
			border: 0; color: var(--on-primary);
			background: linear-gradient(90deg, var(--primary-light), var(--primary) 45%, var(--primary-2));
			box-shadow: 0 8px 26px color-mix(in srgb, var(--primary) 30%, transparent);
		}
		.btn.warn { color: var(--warn); background: color-mix(in srgb, var(--warn) 12%, var(--container)); border-color: color-mix(in srgb, var(--warn) 40%, transparent); }
		.btn.danger { color: var(--danger); background: color-mix(in srgb, var(--danger) 12%, var(--container)); border-color: color-mix(in srgb, var(--danger) 40%, transparent); }
		.btn:disabled, .btn.dead { opacity: 0.45; pointer-events: none; }
		input, textarea, select {
			background: var(--surface); border: 1px solid #ffffff1e; border-radius: 12px; color: var(--text);
			padding: 10px 14px; font: inherit; outline: none; transition: border-color 0.15s, box-shadow 0.15s;
		}
		input:focus, textarea:focus, select:focus { border-color: var(--primary); box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 22%, transparent); }
		.pill { display: inline-flex; align-items: center; height: 22px; padding: 0 10px; border-radius: 999px; font-size: 12px; font-weight: 600; background: var(--high); color: var(--muted); }
		.pill.accent { background: linear-gradient(90deg, var(--primary), var(--primary-2)); color: var(--on-primary); }
		.icon { font-family: "Material Symbols Rounded"; font-size: 18px; line-height: 1; font-variation-settings: "FILL" 1; display: inline-block; width: 1em; height: 1em; overflow: hidden; white-space: nowrap; flex: none; }
		.logo-catbet {
			display: inline-block; aspect-ratio: 416 / 100;
			background: linear-gradient(90deg, var(--primary-light), var(--primary));
			-webkit-mask: url("/catbet.svg") center / contain no-repeat; mask: url("/catbet.svg") center / contain no-repeat;
		}

		.shell { position: relative; z-index: 1; width: min(1080px, calc(100% - 24px)); margin: 0 auto; padding: 8px 0 40px; }
		.brandpill { gap: 8px; padding: 0 14px 0 10px; font-weight: 600; letter-spacing: 0.02em; }
		.brandpill .dot { width: 22px; height: 22px; border-radius: 50%; display: grid; place-items: center; background: linear-gradient(135deg, var(--primary), var(--primary-2)); color: var(--on-primary); }
		.brandpill .dot .icon { font-size: 15px; }
		.clock { font-variant-numeric: tabular-nums; color: var(--muted); font-weight: 500; }
		.tiles { display: grid; grid-template-columns: 1.35fr 1fr; gap: var(--gap); margin-top: var(--gap); }
		.tiles > .win { padding: 22px; }
		.hero { grid-row: span 2; display: flex; flex-direction: column; justify-content: center; gap: 34px; min-height: 330px; overflow: hidden; }
		.hero::after {
			content: ""; position: absolute; inset: auto -20% -45% 30%; height: 70%; border-radius: 50%; pointer-events: none;
			background: radial-gradient(closest-side, color-mix(in srgb, var(--primary) 30%, transparent), transparent);
		}
		.kicker { color: var(--primary); font-weight: 600; font-size: 13px; display: flex; gap: 8px; align-items: center; }
		h1 { margin: 12px 0 8px; font-size: clamp(32px, 5vw, 48px); line-height: 1.04; font-weight: 600; letter-spacing: -0.02em; }
		h1 span { background: linear-gradient(90deg, var(--primary-light), var(--primary) 50%, var(--primary-2)); -webkit-background-clip: text; background-clip: text; color: transparent; }
		.lede { color: var(--muted); font-size: 15px; max-width: 46ch; margin: 0; }
		.cta { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 22px; position: relative; z-index: 1; }
		.meta { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 18px; position: relative; z-index: 1; }
		h2 { margin: 0 0 12px; font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
		h2 .icon { color: var(--primary); }
		.feats { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
		.feats li { display: grid; grid-template-columns: 34px 1fr; gap: 10px; align-items: center; padding: 8px 10px; border-radius: 14px; background: var(--container); }
		.feats .ic { width: 34px; height: 34px; border-radius: 11px; display: grid; place-items: center; background: color-mix(in srgb, var(--primary) 16%, var(--high)); color: var(--primary); }
		.feats b { display: block; font-weight: 600; }
		.feats span { color: var(--faint); font-size: 13px; }
		.catbet { grid-column: 1 / -1; display: grid; grid-template-columns: 1fr 1fr; gap: 22px; align-items: start; }
		.catbet .logo-catbet { height: 46px; }
		.catbet p { color: var(--muted); margin: 12px 0 0; max-width: 44ch; }
		.games { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 14px; }
		.board { display: grid; gap: 4px; }
		.board .row { display: grid; grid-template-columns: 28px 1fr auto; align-items: center; gap: 10px; height: 34px; padding: 0 12px 0 6px; border-radius: 999px; background: var(--container); }
		.board .rank { width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center; font-size: 12px; font-weight: 700; color: var(--faint); }
		.board .row:nth-child(1) .rank { background: linear-gradient(180deg, #ffe08a, #e0a930); color: #2a1d02; }
		.board .row:nth-child(2) .rank { background: linear-gradient(180deg, #eef2f7, #aab4c0); color: #1b1f24; }
		.board .row:nth-child(3) .rank { background: linear-gradient(180deg, #f2b27a, #b8743c); color: #251305; }
		.board .amt { color: var(--primary); font-weight: 600; font-variant-numeric: tabular-nums; }
		.board .empty { color: var(--faint); padding: 8px 4px; }
		.who { display: flex; align-items: center; gap: 8px; margin: 10px 0; }
		.handle { font-family: ui-monospace, Consolas, monospace; font-weight: 600; padding: 6px 12px; border-radius: 999px; background: var(--container); }
		.cape p, .note { margin: 0; color: var(--faint); font-size: 13px; }
		.foot { display: flex; justify-content: space-between; color: var(--faint); font-size: 12px; margin-top: 14px; padding: 0 8px; }
		.foot a { color: inherit; text-decoration: none; }
		.foot a:hover { color: var(--primary); }
		@media (max-width: 820px) {
			.tiles, .catbet { grid-template-columns: 1fr; }
			.hero { grid-row: auto; }
			.bar .nav { display: none; }
		}
	</style>
</head>
<body>
	<div class="shell">
		<header class="bar">
			<div class="group brandpill"><span class="dot"><span class="icon">tune</span></span>Stray</div>
			<nav class="group nav">
				<a class="ws on" href="#top">Home</a>
				<a class="ws" href="#features">Features</a>
				<a class="ws" href="#catbet">Catbet</a>
				<a class="ws" href="#cape">Capes</a>
			</nav>
			<div class="spacer"></div>
			<div class="group pad"><span class="pill accent" id="mod-ver">…</span></div>
			<div class="group pad clock" id="clock">00:00</div>
		</header>
		<main class="tiles" id="top">
			<section class="win hero">
				<div>
					<div class="kicker"><span class="icon">bolt</span>Hypixel SkyBlock client</div>
					<h1>See SkyBlock <span>your way.</span></h1>
					<p class="lede">World tint, ESP, a movable HUD, slayer, mining and farming tools, all in a Hyprland-style menu. Right Shift opens it in game.</p>
					<div class="cta">
						<a class="btn primary" id="mod-download" href="/download"><span class="icon">download</span>Download</a>
						<a class="btn" href="https://github.com/camberX/Stray" target="_blank" rel="noopener noreferrer"><span class="icon">code</span>GitHub</a>
					</div>
				</div>
				<div class="meta">
					<span class="pill">Minecraft <span id="mc-ver" style="margin-left:4px">26.1.2</span></span>
					<span class="pill">Fabric</span>
					<span class="pill">Latest build <span id="stat-ver" style="margin-left:4px">—</span></span>
				</div>
			</section>
			<section class="win" id="features">
				<h2><span class="icon">widgets</span>Features</h2>
				<ul class="feats">
					<li><span class="ic"><span class="icon">public</span></span><div><b>World</b><span>Terrain tint, skybox, fog and aspect.</span></div></li>
					<li><span class="ic"><span class="icon">visibility</span></span><div><b>Visuals</b><span>Mob glow, player fill, chest ESP and nametags.</span></div></li>
					<li><span class="ic"><span class="icon">dashboard</span></span><div><b>HUD</b><span>Watermark, music, raw mats and a movable editor.</span></div></li>
					<li><span class="ic"><span class="icon">swords</span></span><div><b>Slayer, mining, farming</b><span>Boss ESP, commission HUD, farm camera.</span></div></li>
				</ul>
			</section>
			<section class="win cape" id="cape">
				<h2><span class="icon">styler</span>Custom cape</h2>
				<p>Message this Discord with your Minecraft name. Once you are added, open the Cape card in Stray.</p>
				<div class="who">
					<span class="handle">@evilkitten911</span>
					<button type="button" class="btn" id="copy">Copy</button>
				</div>
			</section>
			<section class="win catbet" id="catbet">
				<div>
					<span class="logo-catbet" role="img" aria-label="catbet"></span>
					<p>The play-money casino inside Stray. Type <b>/st casino</b> in game for slots, blackjack and roulette. Everyone starts with 1,000 chips and gets 1,000 more every day. Chips are just for fun and can't be bought or cashed out.</p>
					<div class="games"><span class="pill">Slots</span><span class="pill">Blackjack 3:2</span><span class="pill">European roulette</span><span class="pill accent">+1,000 daily</span></div>
				</div>
				<div>
					<h2><span class="icon">leaderboard</span>Top players</h2>
					<div class="board" id="board"><div class="empty">Loading…</div></div>
				</div>
			</section>
		</main>
		<div class="foot"><span>stray.gay</span><a href="/admin">Desk</a></div>
	</div>
	<script>
		document.getElementById("copy").onclick = function () {
			var btn = this;
			navigator.clipboard.writeText("@evilkitten911").then(function () {
				btn.textContent = "Copied";
				setTimeout(function () { btn.textContent = "Copy"; }, 1400);
			}).catch(function () { btn.textContent = "Copy failed"; });
		};
		(function clock() {
			var el = document.getElementById("clock");
			function tick() { var d = new Date(); el.textContent = String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0"); }
			tick();
			setInterval(tick, 10000);
		})();
		(function workspaces() {
			var links = Array.prototype.slice.call(document.querySelectorAll(".nav .ws"));
			function pick() {
				var best = links[0];
				links.forEach(function (a) {
					var el = document.querySelector(a.getAttribute("href"));
					if (el && window.scrollY > 40 && el.getBoundingClientRect().top < window.innerHeight * 0.3) best = a;
				});
				links.forEach(function (a) { a.classList.toggle("on", a === best); });
			}
			window.addEventListener("scroll", pick, { passive: true });
			pick();
		})();
		(function board() {
			var box = document.getElementById("board");
			fetch("/api/casino/top-public", { cache: "no-store" }).then(function (r) { return r.ok ? r.json() : null; }).then(function (data) {
				var rows = data && data.top ? data.top : [];
				box.textContent = "";
				if (!rows.length) {
					box.innerHTML = '<div class="empty">No players yet.</div>';
					return;
				}
				rows.forEach(function (row, i) {
					var el = document.createElement("div");
					el.className = "row";
					var rank = document.createElement("span"); rank.className = "rank"; rank.textContent = String(i + 1);
					var name = document.createElement("span"); name.textContent = row.name;
					var amt = document.createElement("span"); amt.className = "amt"; amt.textContent = Number(row.balance).toLocaleString("en-US");
					el.append(rank, name, amt);
					box.append(el);
				});
			}).catch(function () { box.innerHTML = '<div class="empty">Leaderboard unavailable.</div>'; });
		})();
		(function loadMod() {
			var ver = document.getElementById("mod-ver");
			var stat = document.getElementById("stat-ver");
			var mc = document.getElementById("mc-ver");
			var link = document.getElementById("mod-download");
			var mirrors = [
				{ url: "/api/mod", repo: "" }
			];
			function fileUrl(data) {
				if (data.url && data.url.charAt(0) === "/") return data.url;
				return "https://raw.githubusercontent.com/" + (data.repo || "camberX/Stray") + "/main/web/public/mod/" + (data.file || ("stray-" + data.version + ".jar"));
			}
			function apply(data) {
				ver.textContent = "v" + data.version;
				stat.textContent = data.version;
				if (data.minecraft) mc.textContent = data.minecraft;
				link.classList.remove("dead");
				link.setAttribute("download", data.file || ("stray-" + data.version + ".jar"));
				link.href = fileUrl(data);
			}
			function versionParts(value) {
				return String(value || "").split(/[^0-9]+/).filter(Boolean).map(Number);
			}
			function newer(a, b) {
				var left = versionParts(a), right = versionParts(b);
				var n = Math.max(left.length, right.length);
				for (var i = 0; i < n; i++) {
					var av = i < left.length ? left[i] : 0;
					var bv = i < right.length ? right[i] : 0;
					if (av !== bv) return av - bv;
				}
				return 0;
			}
			var best = null;
			var pending = mirrors.length;
			function consider(data) {
				if (data && data.version && (!best || newer(data.version, best.version) > 0)) best = data;
				pending--;
				if (pending === 0 && best) apply(best);
			}
			mirrors.forEach(function (mirror) {
				fetch(mirror.url, { cache: "no-store" }).then(function (r) { return r.ok ? r.json() : null; }).then(function (data) {
					if (data && !data.repo && mirror.repo) data.repo = mirror.repo;
					if (data && data.repo) data.url = "";
					consider(data);
				}).catch(function () { consider(null); });
			});
		})();
	<\/script>
</body>
</html>
`;
var LOGIN_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>Stray Desk</title>
	<meta name="theme-color" content="#0b0c10">
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,500,1,0&display=block" rel="stylesheet">
	<style>
		/* Hyprland look shared by the site and the desk: the click GUI's Material roles,
		   frosted windows with a 1px accent border, pill bar groups and gaps between tiles. */
		@font-face { font-family: "Google Sans Flex"; src: url("/fonts/googlesansflex_regular.ttf") format("truetype"); font-weight: 400; font-display: swap; }
		@font-face { font-family: "Google Sans Flex"; src: url("/fonts/googlesansflex_medium.ttf") format("truetype"); font-weight: 500 900; font-display: swap; }
		:root {
			--primary: #2fb5ff;
			--primary-2: #322fff;
			--primary-light: color-mix(in srgb, var(--primary) 78%, #fff);
			--on-primary: #04131c;
			--bg: #0b0c10;
			--surface: #121318;
			--surface-low: #1a1b21;
			--container: #1f2027;
			--high: #292a32;
			--highest: #34353d;
			--text: #e6e7ee;
			--muted: #c4c6d0;
			--faint: #8e909b;
			--line: #46474f;
			--ok: #46dc95;
			--warn: #f5c16c;
			--danger: #f0565b;
			--radius: 18px;
			--gap: 10px;
		}
		* { box-sizing: border-box; }
		html, body { margin: 0; min-height: 100%; background: var(--bg); color: var(--text); font: 14px/1.45 "Google Sans Flex", "Google Sans", system-ui, sans-serif; }
		body::before {
			content: ""; position: fixed; inset: 0; z-index: 0; pointer-events: none;
			background:
				radial-gradient(1100px 620px at 8% -10%, color-mix(in srgb, var(--primary) 26%, transparent), transparent 62%),
				radial-gradient(900px 600px at 105% 110%, color-mix(in srgb, var(--primary-2) 22%, transparent), transparent 64%),
				radial-gradient(600px 400px at 70% 20%, color-mix(in srgb, var(--primary) 8%, transparent), transparent 70%),
				linear-gradient(180deg, #0d0e13, #08090c);
		}
		body::after {
			content: ""; position: fixed; inset: 0; z-index: 0; pointer-events: none; opacity: 0.05; mix-blend-mode: overlay;
			background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
		}
		a { color: var(--primary); }
		.win {
			position: relative; background: color-mix(in srgb, var(--surface-low) 84%, transparent);
			border: 1px solid color-mix(in srgb, var(--primary) 30%, transparent); border-radius: var(--radius);
			backdrop-filter: blur(22px) saturate(1.3); -webkit-backdrop-filter: blur(22px) saturate(1.3);
			box-shadow: 0 18px 50px #0007; transition: border-color 0.18s ease;
		}
		.win:hover, .win:focus-within { border-color: var(--primary); }
		.bar {
			position: sticky; top: 8px; z-index: 5; display: flex; align-items: center; gap: 6px;
			height: 44px; padding: 5px; border-radius: 999px;
			background: color-mix(in srgb, var(--surface) 82%, transparent);
			backdrop-filter: blur(22px) saturate(1.3); -webkit-backdrop-filter: blur(22px) saturate(1.3);
			border: 1px solid #ffffff10; box-shadow: 0 12px 40px #0008;
		}
		.group { display: flex; align-items: center; gap: 4px; height: 100%; padding: 0 4px; border-radius: 999px; background: var(--surface-low); }
		.group.pad { padding: 0 14px; }
		.ws {
			display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 12px; border-radius: 999px;
			color: var(--muted); text-decoration: none; font-weight: 500; border: 0; background: transparent; cursor: pointer; font: inherit;
			transition: background 0.18s, color 0.18s;
		}
		.ws:hover { background: #ffffff14; color: var(--text); }
		.ws.on { background: linear-gradient(90deg, var(--primary), var(--primary-2)); color: var(--on-primary); }
		.spacer { flex: 1; }
		.btn {
			display: inline-flex; align-items: center; justify-content: center; gap: 8px; height: 38px; padding: 0 18px;
			border-radius: 999px; border: 1px solid #ffffff1e; cursor: pointer; text-decoration: none;
			font: inherit; font-weight: 600; color: var(--text);
			background: linear-gradient(180deg, var(--high), var(--container));
			transition: filter 0.15s, transform 0.15s, border-color 0.15s;
		}
		.btn:hover { border-color: #ffffff38; filter: brightness(1.12); }
		.btn:active { transform: translateY(1px); }
		.btn.primary {
			border: 0; color: var(--on-primary);
			background: linear-gradient(90deg, var(--primary-light), var(--primary) 45%, var(--primary-2));
			box-shadow: 0 8px 26px color-mix(in srgb, var(--primary) 30%, transparent);
		}
		.btn.warn { color: var(--warn); background: color-mix(in srgb, var(--warn) 12%, var(--container)); border-color: color-mix(in srgb, var(--warn) 40%, transparent); }
		.btn.danger { color: var(--danger); background: color-mix(in srgb, var(--danger) 12%, var(--container)); border-color: color-mix(in srgb, var(--danger) 40%, transparent); }
		.btn:disabled, .btn.dead { opacity: 0.45; pointer-events: none; }
		input, textarea, select {
			background: var(--surface); border: 1px solid #ffffff1e; border-radius: 12px; color: var(--text);
			padding: 10px 14px; font: inherit; outline: none; transition: border-color 0.15s, box-shadow 0.15s;
		}
		input:focus, textarea:focus, select:focus { border-color: var(--primary); box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 22%, transparent); }
		.pill { display: inline-flex; align-items: center; height: 22px; padding: 0 10px; border-radius: 999px; font-size: 12px; font-weight: 600; background: var(--high); color: var(--muted); }
		.pill.accent { background: linear-gradient(90deg, var(--primary), var(--primary-2)); color: var(--on-primary); }
		.icon { font-family: "Material Symbols Rounded"; font-size: 18px; line-height: 1; font-variation-settings: "FILL" 1; display: inline-block; width: 1em; height: 1em; overflow: hidden; white-space: nowrap; flex: none; }
		.logo-catbet {
			display: inline-block; aspect-ratio: 416 / 100;
			background: linear-gradient(90deg, var(--primary-light), var(--primary));
			-webkit-mask: url("/catbet.svg") center / contain no-repeat; mask: url("/catbet.svg") center / contain no-repeat;
		}

		main { position: relative; z-index: 1; width: min(400px, calc(100% - 28px)); margin: 16vh auto 0; padding: 26px; }
		.top { display: flex; align-items: center; gap: 12px; }
		.dot { width: 40px; height: 40px; border-radius: 14px; display: grid; place-items: center; background: linear-gradient(135deg, var(--primary), var(--primary-2)); color: var(--on-primary); }
		.dot .icon { font-size: 22px; }
		h1 { margin: 0; font-size: 22px; font-weight: 600; }
		.sub { color: var(--faint); font-size: 13px; }
		p { color: var(--muted); margin: 18px 0; }
		label { display: block; margin: 0 0 6px; font-size: 12px; font-weight: 600; color: var(--muted); }
		input { width: 100%; }
		.btn.primary { width: 100%; margin-top: 14px; height: 42px; }
		.status { min-height: 20px; margin-top: 12px; font-size: 13px; color: var(--muted); }
		.status.err { color: var(--warn); }
		.back { display: inline-flex; gap: 6px; align-items: center; margin-top: 10px; color: var(--faint); text-decoration: none; font-size: 13px; }
		.back:hover { color: var(--primary); }
	</style>
</head>
<body>
	<main class="win">
		<div class="top">
			<div class="dot"><span class="icon">shield_lock</span></div>
			<div><h1>Stray Desk</h1><div class="sub">Restricted</div></div>
		</div>
		<p>Enter the Worker admin secret to open the desk.</p>
		<label for="admin">Admin key</label>
		<input id="admin" type="password" autocomplete="current-password" placeholder="Secret">
		<button type="button" class="btn primary" id="go">Enter</button>
		<div class="status" id="status"></div>
		<a class="back" href="/"><span class="icon">arrow_back</span>Back to the site</a>
	</main>
	<script>
		const admin = document.getElementById("admin");
		const status = document.getElementById("status");
		const go = document.getElementById("go");
		admin.value = sessionStorage.getItem("stray-admin") || "";
		async function enter() {
			const key = admin.value.trim();
			status.textContent = "Checking\u2026";
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
	<\/script>
</body>
</html>
`;
var MANAGE_HTML = `<!DOCTYPE html>
<html lang="en" class="locked">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>Stray Desk</title>
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,500,1,0&display=block" rel="stylesheet">
	<style>
		/* Hyprland look shared by the site and the desk: the click GUI's Material roles,
		   frosted windows with a 1px accent border, pill bar groups and gaps between tiles. */
		@font-face { font-family: "Google Sans Flex"; src: url("/fonts/googlesansflex_regular.ttf") format("truetype"); font-weight: 400; font-display: swap; }
		@font-face { font-family: "Google Sans Flex"; src: url("/fonts/googlesansflex_medium.ttf") format("truetype"); font-weight: 500 900; font-display: swap; }
		:root {
			--primary: #2fb5ff;
			--primary-2: #322fff;
			--primary-light: color-mix(in srgb, var(--primary) 78%, #fff);
			--on-primary: #04131c;
			--bg: #0b0c10;
			--surface: #121318;
			--surface-low: #1a1b21;
			--container: #1f2027;
			--high: #292a32;
			--highest: #34353d;
			--text: #e6e7ee;
			--muted: #c4c6d0;
			--faint: #8e909b;
			--line: #46474f;
			--ok: #46dc95;
			--warn: #f5c16c;
			--danger: #f0565b;
			--radius: 18px;
			--gap: 10px;
		}
		* { box-sizing: border-box; }
		html, body { margin: 0; min-height: 100%; background: var(--bg); color: var(--text); font: 14px/1.45 "Google Sans Flex", "Google Sans", system-ui, sans-serif; }
		body::before {
			content: ""; position: fixed; inset: 0; z-index: 0; pointer-events: none;
			background:
				radial-gradient(1100px 620px at 8% -10%, color-mix(in srgb, var(--primary) 26%, transparent), transparent 62%),
				radial-gradient(900px 600px at 105% 110%, color-mix(in srgb, var(--primary-2) 22%, transparent), transparent 64%),
				radial-gradient(600px 400px at 70% 20%, color-mix(in srgb, var(--primary) 8%, transparent), transparent 70%),
				linear-gradient(180deg, #0d0e13, #08090c);
		}
		body::after {
			content: ""; position: fixed; inset: 0; z-index: 0; pointer-events: none; opacity: 0.05; mix-blend-mode: overlay;
			background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
		}
		a { color: var(--primary); }
		.win {
			position: relative; background: color-mix(in srgb, var(--surface-low) 84%, transparent);
			border: 1px solid color-mix(in srgb, var(--primary) 30%, transparent); border-radius: var(--radius);
			backdrop-filter: blur(22px) saturate(1.3); -webkit-backdrop-filter: blur(22px) saturate(1.3);
			box-shadow: 0 18px 50px #0007; transition: border-color 0.18s ease;
		}
		.win:hover, .win:focus-within { border-color: var(--primary); }
		.bar {
			position: sticky; top: 8px; z-index: 5; display: flex; align-items: center; gap: 6px;
			height: 44px; padding: 5px; border-radius: 999px;
			background: color-mix(in srgb, var(--surface) 82%, transparent);
			backdrop-filter: blur(22px) saturate(1.3); -webkit-backdrop-filter: blur(22px) saturate(1.3);
			border: 1px solid #ffffff10; box-shadow: 0 12px 40px #0008;
		}
		.group { display: flex; align-items: center; gap: 4px; height: 100%; padding: 0 4px; border-radius: 999px; background: var(--surface-low); }
		.group.pad { padding: 0 14px; }
		.ws {
			display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 12px; border-radius: 999px;
			color: var(--muted); text-decoration: none; font-weight: 500; border: 0; background: transparent; cursor: pointer; font: inherit;
			transition: background 0.18s, color 0.18s;
		}
		.ws:hover { background: #ffffff14; color: var(--text); }
		.ws.on { background: linear-gradient(90deg, var(--primary), var(--primary-2)); color: var(--on-primary); }
		.spacer { flex: 1; }
		.btn {
			display: inline-flex; align-items: center; justify-content: center; gap: 8px; height: 38px; padding: 0 18px;
			border-radius: 999px; border: 1px solid #ffffff1e; cursor: pointer; text-decoration: none;
			font: inherit; font-weight: 600; color: var(--text);
			background: linear-gradient(180deg, var(--high), var(--container));
			transition: filter 0.15s, transform 0.15s, border-color 0.15s;
		}
		.btn:hover { border-color: #ffffff38; filter: brightness(1.12); }
		.btn:active { transform: translateY(1px); }
		.btn.primary {
			border: 0; color: var(--on-primary);
			background: linear-gradient(90deg, var(--primary-light), var(--primary) 45%, var(--primary-2));
			box-shadow: 0 8px 26px color-mix(in srgb, var(--primary) 30%, transparent);
		}
		.btn.warn { color: var(--warn); background: color-mix(in srgb, var(--warn) 12%, var(--container)); border-color: color-mix(in srgb, var(--warn) 40%, transparent); }
		.btn.danger { color: var(--danger); background: color-mix(in srgb, var(--danger) 12%, var(--container)); border-color: color-mix(in srgb, var(--danger) 40%, transparent); }
		.btn:disabled, .btn.dead { opacity: 0.45; pointer-events: none; }
		input, textarea, select {
			background: var(--surface); border: 1px solid #ffffff1e; border-radius: 12px; color: var(--text);
			padding: 10px 14px; font: inherit; outline: none; transition: border-color 0.15s, box-shadow 0.15s;
		}
		input:focus, textarea:focus, select:focus { border-color: var(--primary); box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 22%, transparent); }
		.pill { display: inline-flex; align-items: center; height: 22px; padding: 0 10px; border-radius: 999px; font-size: 12px; font-weight: 600; background: var(--high); color: var(--muted); }
		.pill.accent { background: linear-gradient(90deg, var(--primary), var(--primary-2)); color: var(--on-primary); }
		.icon { font-family: "Material Symbols Rounded"; font-size: 18px; line-height: 1; font-variation-settings: "FILL" 1; display: inline-block; width: 1em; height: 1em; overflow: hidden; white-space: nowrap; flex: none; }
		.logo-catbet {
			display: inline-block; aspect-ratio: 416 / 100;
			background: linear-gradient(90deg, var(--primary-light), var(--primary));
			-webkit-mask: url("/catbet.svg") center / contain no-repeat; mask: url("/catbet.svg") center / contain no-repeat;
		}
		@font-face {
			font-family: "Minecraft";
			src: url("https://cdn.jsdelivr.net/npm/skinview3d@3.4.1/assets/minecraft.woff2") format("woff2");
			font-display: swap;
		}
		#stars { position: fixed; inset: 0; z-index: 0; opacity: 0.35; pointer-events: none; }
		.nebula { display: none; }
		.app { position: relative; z-index: 2; display: grid; grid-template-columns: 230px minmax(0, 1fr); gap: var(--gap); min-height: 100vh; padding: var(--gap); }
		.rail { position: sticky; top: var(--gap); align-self: start; padding: 18px 14px; display: flex; flex-direction: column; gap: 10px; }
		.brand { display: flex; align-items: center; gap: 10px; font-weight: 600; font-size: 17px; }
		.brand::before {
			content: "tune"; font-family: "Material Symbols Rounded"; font-variation-settings: "FILL" 1; font-size: 18px;
			width: 32px; height: 32px; border-radius: 11px; display: grid; place-items: center;
			background: linear-gradient(135deg, var(--primary), var(--primary-2)); color: var(--on-primary); overflow: hidden;
		}
		.tick, .recycle { display: none; }
		.sub { color: var(--faint); font-size: 13px; margin: 0; }
		nav { display: grid; gap: 4px; }
		.out { width: 100%; margin-top: 6px; }
		.content { padding: 0 0 40px; min-width: 0; }
		.content > .panel { display: grid; gap: var(--gap); }
		.content > .panel > *:not(.card):not(.list):not(.status) { margin: 0; }
		.top { display: flex; justify-content: space-between; gap: 12px; align-items: center; flex-wrap: wrap; }
		.panel > .top { padding: 18px 20px; }
		h1 { margin: 0; font-size: 22px; font-weight: 600; letter-spacing: 0; }
		.hint { color: var(--faint); font-size: 13px; margin: 4px 0 0; }
		.stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--gap); }
		.stat { padding: 16px 18px; }
		.stat b { display: block; font-size: 26px; font-weight: 600; background: linear-gradient(90deg, var(--primary-light), var(--primary)); -webkit-background-clip: text; background-clip: text; color: transparent; }
		.stat span { color: var(--faint); font-size: 12px; font-weight: 500; }
		.toolbar, .add, .row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
		.grow { flex: 1; min-width: 160px; }
		nav button, .ghost, .warn, .danger, .primary {
			display: inline-flex; align-items: center; justify-content: center; height: 38px; padding: 0 16px; border-radius: 999px;
			border: 1px solid #ffffff1e; cursor: pointer; font: inherit; font-weight: 600; color: var(--text);
			background: linear-gradient(180deg, var(--high), var(--container)); transition: filter 0.15s, border-color 0.15s;
		}
		.ghost:hover, nav button:hover { border-color: #ffffff38; filter: brightness(1.12); }
		.primary { border: 0; color: var(--on-primary); background: linear-gradient(90deg, var(--primary-light), var(--primary) 45%, var(--primary-2)); box-shadow: 0 8px 22px color-mix(in srgb, var(--primary) 28%, transparent); }
		.primary:hover { filter: brightness(1.08); }
		.warn { color: var(--warn); background: color-mix(in srgb, var(--warn) 12%, var(--container)); border-color: color-mix(in srgb, var(--warn) 40%, transparent); }
		.danger { color: var(--danger); background: color-mix(in srgb, var(--danger) 12%, var(--container)); border-color: color-mix(in srgb, var(--danger) 40%, transparent); }
		button:disabled { opacity: 0.45; }
		.chips { display: flex; gap: 6px; flex-wrap: wrap; padding: 5px; border-radius: 999px; background: color-mix(in srgb, var(--surface) 80%, transparent); width: fit-content; max-width: 100%; }
		.chip { height: 28px; padding: 0 12px; border-radius: 999px; border: 0; background: transparent; color: var(--muted); font: inherit; font-weight: 500; font-size: 13px; cursor: pointer; }
		.chip:hover { background: #ffffff14; color: var(--text); }
		.chip.on { background: linear-gradient(90deg, var(--primary), var(--primary-2)); color: var(--on-primary); }
		.status { min-height: 0; font-size: 13px; }
		.status:not(:empty) { padding: 10px 16px; border-radius: 999px; background: var(--surface-low); width: fit-content; }
		.status.ok { color: var(--ok); }
		.status.err { color: var(--warn); }
		.list { display: grid; gap: 6px; }
		.player {
			display: grid; grid-template-columns: 52px minmax(0, 1fr) 54px; gap: 12px; align-items: center; cursor: pointer;
			background: color-mix(in srgb, var(--surface-low) 84%, transparent); border: 1px solid #ffffff12; border-radius: 16px; padding: 10px 12px;
			backdrop-filter: blur(18px); transition: border-color 0.15s, background 0.15s;
		}
		.player:hover { border-color: var(--primary); background: color-mix(in srgb, var(--container) 90%, transparent); }
		.head { width: 52px; height: 52px; border-radius: 14px; background: #000; image-rendering: pixelated; }
		.name { font-weight: 600; }
		.uuid { color: var(--faint); font-size: 12px; word-break: break-all; margin-top: 2px; }
		.badges { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 6px; }
		.badge { font-size: 11px; font-weight: 600; border-radius: 999px; padding: 2px 9px; background: var(--high); color: var(--muted); }
		.badge.on { background: linear-gradient(90deg, var(--primary), var(--primary-2)); color: var(--on-primary); }
		.badge.warn { background: color-mix(in srgb, var(--warn) 16%, var(--high)); color: var(--warn); }
		.cape { width: 42px; height: 66px; object-fit: contain; image-rendering: pixelated; justify-self: center; }
		.nocape { color: var(--faint); font-size: 11px; text-align: center; }
		.empty { color: var(--faint); padding: 18px 8px; }
		.panel { display: block; }
		.card { padding: 18px 20px; }
		.card .logo-catbet { height: 30px; margin-bottom: 8px; }
		label { display: block; margin: 0 0 8px; font-size: 13px; font-weight: 600; color: var(--muted); }
		textarea { width: 100%; min-height: 140px; resize: vertical; }
		.overlay { position: fixed; inset: 0; background: #0b0c10b0; display: flex; align-items: stretch; justify-content: flex-end; z-index: 20; backdrop-filter: blur(8px); padding: var(--gap); }
		.overlay[hidden] { display: none; }
		.overlay.center { align-items: center; justify-content: center; padding: 16px; }
		.drawer, .sheet {
			width: min(440px, 100%); padding: 20px; overflow: auto;
			background: color-mix(in srgb, var(--surface-low) 92%, transparent); border: 1px solid var(--primary); border-radius: var(--radius);
			backdrop-filter: blur(24px) saturate(1.3); box-shadow: 0 24px 80px #000a;
		}
		.sheet { width: min(460px, 100%); }
		.crop-sheet { width: min(740px, 100%) !important; }
		.crop-wrap { display: grid; grid-template-columns: minmax(0, 1fr) 90px; gap: 14px; margin: 12px 0 16px; align-items: start; }
		.crop-stage { position: relative; background: var(--surface); border: 1px solid #ffffff14; border-radius: 14px; min-height: 220px; display: flex; justify-content: center; align-items: center; overflow: hidden; }
		.crop-holder { position: relative; display: inline-block; }
		.crop-stage canvas { display: block; }
		#crop-box { position: absolute; border: 2px solid var(--primary); box-shadow: 0 0 0 9999px #0009; pointer-events: none; }
		.crop-face canvas { width: 50px; height: 80px; background: #000; border: 1px solid #ffffff1e; border-radius: 8px; display: block; }
		.who { color: var(--faint); font-size: 13px; margin: 4px 0 14px; }
		.preview { margin-bottom: 16px; }
		.stage { position: relative; height: 360px; border-radius: 16px; overflow: hidden; background: radial-gradient(circle at 50% 30%, color-mix(in srgb, var(--primary) 14%, var(--surface)), var(--surface)); border: 1px solid #ffffff14; }
		.stage canvas { display: block; width: 100%; height: 100%; }
		.labels { position: absolute; left: 8px; right: 8px; top: 6%; display: flex; flex-direction: column; align-items: center; gap: 1px; pointer-events: none; z-index: 1; }
		.mc-tag { font-family: "Minecraft", monospace; font-size: 16px; line-height: 1; -webkit-font-smoothing: none; image-rendering: pixelated; background: rgba(0, 0, 0, 0.25); color: #fff; text-shadow: 1px 1px 0 #3f3f3f; padding: 1px 4px; white-space: nowrap; max-width: 100%; overflow: hidden; }
		.drag { position: absolute; left: 12px; bottom: 8px; margin: 0; color: var(--faint); font-size: 11px; pointer-events: none; }
		.preview .nocape { font-size: 12px; margin: 8px 0 0; text-align: left; }
		.codes { display: flex; flex-wrap: wrap; gap: 6px; margin: 0 0 12px; }
		.swatch { width: 28px; height: 28px; border-radius: 9px; border: 1px solid #ffffff33; padding: 0; color: #111; font-size: 11px; font-weight: 700; cursor: pointer; }
		.swatch.fmt { background: var(--high); color: var(--text); width: auto; padding: 0 10px; }
		.preview-plate { background: rgba(0, 0, 0, 0.25); min-height: 22px; display: flex; align-items: center; justify-content: center; padding: 4px 8px; margin: 0 0 14px; border-radius: 8px; font-family: "Minecraft", monospace; font-size: 16px; line-height: 1; image-rendering: pixelated; -webkit-font-smoothing: none; }
		.field { margin: 0 0 12px; }
		.bypass { display: flex; align-items: center; gap: 8px; font-weight: 600; cursor: pointer; color: var(--text); }
		input[type="color"] { padding: 4px; width: 48px; height: 38px; }
		input[type="checkbox"] { accent-color: var(--primary); width: 16px; height: 16px; }
		html.locked .app, html.locked .overlay { visibility: hidden !important; pointer-events: none !important; user-select: none !important; }
		html.locked button, html.locked input, html.locked textarea, html.locked select, html.locked a { pointer-events: none !important; }
		@media (max-width: 860px) {
			.app { grid-template-columns: 1fr; }
			.rail { position: static; flex-direction: row; flex-wrap: wrap; align-items: center; }
			.sub { margin: 0 auto 0 0; }
			.out { margin: 0; width: auto; }
			.stats { grid-template-columns: 1fr 1fr; }
		}
	</style>
</head>
<body>
	<canvas id="stars"></canvas>
	<div class="nebula"></div>
	<div class="app" inert>
		<aside class="rail win">
			<div class="brand">Stray Desk</div>
			<div class="tick"></div>
			<div class="sub">Capes, tags and catbet</div>
			<button type="button" class="out ghost" id="out">Log out</button>
			<div class="recycle">\u2672</div>
		</aside>
		<main class="content">
			<div class="status" id="status"></div>
			<section class="panel on" id="view-players">
				<div class="top win">
					<div>
						<h1>Players</h1>
						<p class="hint">Click a row for cape, tag, note, cooldown, and dewhitelist.</p>
					</div>
					<div class="toolbar">
						<button type="button" class="ghost" id="refresh">Refresh names</button>
						<button type="button" class="ghost" id="export">Export JSON</button>
					</div>
				</div>
				<div class="stats">
					<div class="stat win"><b id="s-all">0</b><span>Listed</span></div>
					<div class="stat win"><b id="s-cape">0</b><span>Capes</span></div>
					<div class="stat win"><b id="s-tag">0</b><span>Tags</span></div>
					<div class="stat win"><b id="s-bypass">0</b><span>Bypass</span></div>
				</div>
				<div class="add win" style="padding:12px">
					<input class="grow" id="uuid" type="text" autocomplete="off" spellcheck="false" placeholder="Username or UUID">
					<button type="button" class="primary" id="add">Add</button>
				</div>
				<div class="toolbar win" style="padding:12px">
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
				<div class="card win">
					<label for="bulktext">Bulk add</label>
					<textarea id="bulktext" placeholder="One username or UUID per line" style="min-height:88px"></textarea>
					<div class="row" style="margin-top:12px">
						<button type="button" class="primary" id="bulkadd">Add all</button>
					</div>
				</div>
				<div class="card win">
					<span class="logo-catbet" role="img" aria-label="catbet"></span>
					<label for="casino-player">Chips</label>
					<div class="row">
						<input class="grow" id="casino-player" type="text" autocomplete="off" spellcheck="false" placeholder="Username or UUID">
						<input id="casino-amount" type="number" step="1" placeholder="Amount" style="width:140px">
						<button type="button" class="primary" id="casino-add">Add</button>
						<button type="button" class="ghost" id="casino-set">Set to</button>
					</div>
					<p class="hint">Add takes negative amounts too. New players start with 1,000 and everyone gets 1,000 a day when they open the casino.</p>
					<div class="row" style="margin-top:12px">
						<button type="button" class="ghost" id="casino-load">Show balances</button>
					</div>
					<label for="casino-keys" style="margin-top:14px">Mojang keys for casino logins</label>
					<p class="hint">Mojang blocks this Worker, so logins are checked against Mojang's public keys. Open https://api.minecraftservices.com/publickeys in your browser, copy all of it and paste it here. Only needed again if Mojang changes its keys.</p>
					<textarea id="casino-keys" placeholder="Paste the whole publickeys page" style="min-height:70px"></textarea>
					<div class="row" style="margin-top:8px">
						<button type="button" class="ghost" id="casino-keys-save">Save keys</button>
					</div>
					<div class="list" id="casino-list"></div>
				</div>
			</section>
		</main>
	</div>
	<div class="overlay" id="drawer" hidden inert>
		<div class="drawer">
			<div class="top">
				<h1 style="font-size:18px">Player</h1>
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
				<input id="d-note" maxlength="160" placeholder="Paid, cape pending\u2026">
			</div>
			<div class="field">
				<label>Cape from URL</label>
				<div class="row">
					<input class="grow" id="d-url" placeholder="https://\u2026/cape.png">
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
			<h1 style="font-size:18px">Head tag</h1>
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
	<script src="https://cdn.jsdelivr.net/npm/skinview3d@3.4.1/bundles/skinview3d.bundle.js"><\/script>
	<script src="/cape-crop.js"><\/script>
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
				ctx.clearRect(0, 0, c.width, c.height);
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
			document.getElementById("d-who").textContent = (player.name || "Unknown") + "  \xB7  " + player.uuid;
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
		function casinoAdjust(mode) {
			const player = document.getElementById("casino-player").value.trim();
			const amount = Number(document.getElementById("casino-amount").value);
			if (!player) { setStatus(false, "Enter a username or UUID"); return; }
			if (!Number.isFinite(amount)) { setStatus(false, "Enter an amount"); return; }
			admin("/api/casino/admin", "POST", { player: player, amount: Math.trunc(amount), mode: mode }).then(function (data) {
				setStatus(true, (data.name || player) + " now has " + Number(data.balance).toLocaleString() + " chips.");
				casinoLoad();
			}).catch(function (error) { setStatus(false, error.message); });
		}
		function casinoLoad() {
			return admin("/api/casino/admin/list", "POST", {}).then(function (data) {
				const list = document.getElementById("casino-list");
				list.innerHTML = "";
				(data.players || []).forEach(function (p) {
					const row = document.createElement("div");
					row.className = "row";
					row.style.justifyContent = "space-between";
					row.style.padding = "6px 2px";
					const who = document.createElement("span");
					who.textContent = p.name || p.uuid;
					const bal = document.createElement("b");
					bal.textContent = Number(p.balance).toLocaleString();
					row.appendChild(who);
					row.appendChild(bal);
					row.onclick = function () { document.getElementById("casino-player").value = p.name || p.uuid; };
					list.appendChild(row);
				});
				setStatus(true, data.count + " casino accounts.");
			}).catch(function (error) { setStatus(false, error.message); });
		}
		document.getElementById("casino-add").onclick = function () { casinoAdjust("add"); };
		document.getElementById("casino-set").onclick = function () { casinoAdjust("set"); };
		document.getElementById("casino-load").onclick = casinoLoad;
		document.getElementById("casino-keys-save").onclick = function () {
			admin("/api/casino/admin/keys", "POST", { keys: document.getElementById("casino-keys").value }).then(function (data) {
				document.getElementById("casino-keys").value = "";
				setStatus(true, "Saved " + data.keys + " Mojang keys. Casino logins work now.");
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
	<\/script>
</body>
</html>
`;
// ---------------------------------------------------------------------------
// Stray casino (play money only). Balances and every game result live on the
// server: the mod only sends what you want to do and animates what comes back.
//
// Accounts are kept in a Durable Object (the STRAY_LIVE class, instance
// "casino"), so balance changes never race each other. Players log in by
// proving their Minecraft account to Mojang (the same "join server" check
// servers use); the Worker then hands out a signed token.
// ---------------------------------------------------------------------------
var CASINO_START = 1e3;
var CASINO_MAX_BET = 1e9;
var CASINO_TOKEN_MS = 12 * 60 * 60 * 1e3;
var CASINO_HOST = "casino.internal";

// Slots: one strip per reel (32 stops), the middle row pays.
// Paytable (total return per 1 bet), RTP 96.1 %, a win on 27.6 % of spins:
//   3 Diamonds 500, 3 Sevens 150, 3 Bars 50, 3 Bells 25, 3 Grapes 15,
//   3 Lemons 10, 3 Cherries 8, any 3 of Bar/Seven/Diamond 10,
//   Cherry Cherry on reels 1-2 pays 4, any other 3 fruits (Cherry/Lemon/Grape) 1.
var SLOT_STRIP = ["C", "L", "G", "B", "C", "R", "L", "G", "C", "B", "L", "S", "G", "C", "R", "L", "B", "G", "C", "D", "L", "R", "C", "G", "B", "L", "C", "S", "G", "R", "L", "B"];
var SLOT_THREE = { D: 500, S: 150, R: 50, B: 25, G: 15, L: 10, C: 8 };
var SLOT_ANY_HIGH = 10;
var SLOT_TWO_CHERRY = 4;
var SLOT_ANY_FRUIT = 1;

// European roulette (single zero).
var ROULETTE_RED = [1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36];

function casinoSecret(env) {
  return String(env.CASINO_SECRET || env.ADMIN || "");
}
__name(casinoSecret, "casinoSecret");

function casinoRandom(n) {
  // Uniform integer in [0, n) without modulo bias.
  const limit = Math.floor(4294967296 / n) * n;
  const buf = new Uint32Array(1);
  for (; ; ) {
    crypto.getRandomValues(buf);
    if (buf[0] < limit) {
      return buf[0] % n;
    }
  }
}
__name(casinoRandom, "casinoRandom");

function casinoB64(text) {
  return btoa(unescape(encodeURIComponent(text))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
__name(casinoB64, "casinoB64");

function casinoUnB64(text) {
  const pad = text.replace(/-/g, "+").replace(/_/g, "/");
  return decodeURIComponent(escape(atob(pad + "===".slice((pad.length + 3) % 4))));
}
__name(casinoUnB64, "casinoUnB64");

async function casinoMintToken(env, uuid, name) {
  const payload = uuid + "|" + name + "|" + (Date.now() + CASINO_TOKEN_MS);
  const sig = await hmacHex(casinoSecret(env), "casino|" + payload);
  return casinoB64(payload) + "." + sig;
}
__name(casinoMintToken, "casinoMintToken");

async function casinoAuth(request, env) {
  const header = request.headers.get("Authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  const dot = token.indexOf(".");
  if (dot < 1) {
    return null;
  }
  let payload;
  try {
    payload = casinoUnB64(token.slice(0, dot));
  } catch {
    return null;
  }
  const sig = await hmacHex(casinoSecret(env), "casino|" + payload);
  if (!timingSafeEqualStr(sig, token.slice(dot + 1))) {
    return null;
  }
  const parts = payload.split("|");
  if (parts.length !== 3 || Number(parts[2]) < Date.now()) {
    return null;
  }
  return { uuid: parts[0], name: parts[1] };
}
__name(casinoAuth, "casinoAuth");

async function casinoRoute(request, env, path) {
  const sub = path.slice("/api/casino".length) || "/";
  if (sub === "/ping" || sub === "/") {
    // Open https://<your site>/api/casino/ping in a browser to check the casino is deployed.
    return json(200, { ok: true, casino: "stray-casino-4", durableObject: !!env.STRAY_LIVE, secret: !!casinoSecret(env) });
  }
  if (!env.STRAY_LIVE) {
    return json(501, { error: "The casino needs the STRAY_LIVE Durable Object binding." });
  }
  if (!casinoSecret(env)) {
    return json(500, { error: "Set CASINO_SECRET (or ADMIN) on the Worker." });
  }
  if (sub === "/admin" || sub === "/admin/list" || sub === "/admin/keys") {
    return casinoAdmin(request, env, sub);
  }
  if (request.method === "POST" && sub === "/login") {
    return casinoLogin(request, env);
  }
  if (request.method === "GET" && sub === "/top-public") {
    // The site's leaderboard: names and balances only, cached briefly.
    const res = await casinoBank(env, "/top-public", { who: {}, body: {} });
    const out = new Response(res.body, res);
    out.headers.set("Cache-Control", "public, max-age=30");
    return out;
  }
  const who = await casinoAuth(request, env);
  if (!who) {
    return json(401, { error: "Log in again." });
  }
  let body = {};
  if (request.method === "POST") {
    try {
      body = await request.json();
    } catch {
      body = {};
    }
  }
  if (sub === "/send") {
    const target = await resolvePlayer(String(body.to || ""));
    if (!target.uuid) {
      return json(404, { error: "No Minecraft player called " + String(body.to || "").slice(0, 16) + "." });
    }
    body = { amount: body.amount, toUuid: target.uuid, toName: target.name || String(body.to || "") };
  }
  const ops = ["/me", "/slots", "/roulette", "/blackjack", "/send", "/top"];
  if (!ops.includes(sub)) {
    return json(404, { error: "Unknown casino call." });
  }
  return casinoBank(env, sub, { who, body });
}
__name(casinoRoute, "casinoRoute");

async function casinoBank(env, op, payload) {
  const stub = env.STRAY_LIVE.get(env.STRAY_LIVE.idFromName("casino"));
  const response = await stub.fetch(new Request("https://" + CASINO_HOST + "/casino" + op, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  }));
  const text = await response.text();
  return new Response(text, {
    status: response.status,
    headers: { ...cors(), "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }
  });
}
__name(casinoBank, "casinoBank");

async function casinoLogin(request, env) {
  // Proof one: the mod joined a "server" with this id at Mojang; Mojang confirms it.
  // Proof two (when Mojang can't be reached from the Worker): the mod's chat signing key,
  // which Mojang signed for this account, signs this login.
  const name = String(request.headers.get("x-minecraft-username") || "").trim();
  const server = String(request.headers.get("x-minecraft-server") || "").trim();
  if (!NAME_RE.test(name) || !/^[A-Za-z0-9_\-]{8,64}$/.test(server)) {
    return json(400, { error: "Bad login." });
  }
  let body = {};
  try {
    body = await request.json();
  } catch {
    body = {};
  }
  let uuid = "";
  let realName = "";
  const joined = await casinoHasJoined(name, server);
  if (joined.profile) {
    uuid = normalizeUuid(joined.profile.id);
    realName = String(joined.profile.name || "");
  }
  let why = "Mojang said " + joined.status;
  if (!uuid && body && body.cert) {
    const checked = await casinoCertLogin(env, name, server, body);
    if (checked.uuid) {
      uuid = checked.uuid;
      realName = await lookupName(uuid) || name;
    } else {
      why += ", key check: " + checked.error;
    }
  }
  if (!uuid || !realName) {
    return json(403, { error: "Could not verify your account (" + why + ")." });
  }
  const token = await casinoMintToken(env, uuid, realName);
  const bank = await casinoBank(env, "/me", { who: { uuid, name: realName }, body: {} });
  const data = await bank.json();
  return json(200, { ...data, token, uuid, name: realName });
}
__name(casinoLogin, "casinoLogin");

async function casinoHasJoined(name, server) {
  const url = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" + encodeURIComponent(name) + "&serverId=" + encodeURIComponent(server);
  let status = "nothing";
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const response = await fetch(url, { headers: { "User-Agent": "Stray casino" } });
      status = String(response.status);
      if (response.status === 200) {
        return { profile: await response.json(), status };
      }
      if (response.status !== 204) {
        break;
      }
    } catch (error) {
      status = "unreachable";
    }
    await new Promise((resolve) => setTimeout(resolve, 600 * (attempt + 1)));
  }
  return { profile: null, status };
}
__name(casinoHasJoined, "casinoHasJoined");

var CASINO_BUILTIN_CERT_KEYS = [
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==",
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ=="
];
var casinoMojangKeys = null;
var casinoMojangKeysAt = 0;
// Mojang's keys for player chat certificates. Fetched when Mojang answers the Worker;
// otherwise the copy pasted into the admin panel (saved in the CAPES bucket) is used.
async function casinoCertKeys(env) {
  if (casinoMojangKeys && Date.now() - casinoMojangKeysAt < 6 * 60 * 60 * 1e3) {
    return casinoMojangKeys;
  }
  let data = null;
  try {
    const response = await fetch("https://api.minecraftservices.com/publickeys", { headers: { "User-Agent": "Stray casino" } });
    if (response.ok) {
      data = await response.json();
    }
  } catch {
    data = null;
  }
  if (!data && env.CAPES) {
    const saved = await env.CAPES.get("casino-mojang-keys.json");
    if (saved) {
      data = JSON.parse(await saved.text());
    }
  }
  if (!data || !Array.isArray(data.playerCertificateKeys)) {
    // Built-in copy from api.minecraftservices.com/publickeys (October 2026).
    data = { playerCertificateKeys: CASINO_BUILTIN_CERT_KEYS.map((publicKey) => ({ publicKey })) };
  }
  const keys = [];
  for (const entry of data.playerCertificateKeys || []) {
    keys.push(await crypto.subtle.importKey("spki", casinoBytes(entry.publicKey), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-1" }, false, ["verify"]));
  }
  casinoMojangKeys = keys;
  casinoMojangKeysAt = Date.now();
  return keys;
}
__name(casinoCertKeys, "casinoCertKeys");

function casinoBytes(b64) {
  const raw = atob(String(b64 || "").replace(/-/g, "+").replace(/_/g, "/"));
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) {
    out[i] = raw.charCodeAt(i);
  }
  return out;
}
__name(casinoBytes, "casinoBytes");

async function casinoCertLogin(env, name, server, body) {
  try {
    const cert = body.cert || {};
    const uuid = normalizeUuid(String(body.uuid || ""));
    const expires = Number(cert.expires);
    if (!uuid || !Number.isFinite(expires) || expires < Date.now()) {
      return { error: "expired key" };
    }
    const time = Number(body.time);
    if (!Number.isFinite(time) || Math.abs(Date.now() - time) > 5 * 60 * 1e3) {
      return { error: "clock" };
    }
    const keyDer = casinoBytes(cert.key);
    // Mojang signed: uuid (16 bytes) + expiry in ms (8 bytes) + the player's public key.
    const signed = new Uint8Array(24 + keyDer.length);
    const view = new DataView(signed.buffer);
    const hex = uuid.replace(/-/g, "");
    for (let i = 0; i < 16; i++) {
      signed[i] = parseInt(hex.substr(i * 2, 2), 16);
    }
    view.setBigInt64(16, BigInt(Math.trunc(expires)));
    signed.set(keyDer, 24);
    const mojangSig = casinoBytes(cert.sig);
    let trusted = false;
    for (const key of await casinoCertKeys(env)) {
      if (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, mojangSig, signed)) {
        trusted = true;
        break;
      }
    }
    if (!trusted) {
      return { error: "key not signed by Mojang" };
    }
    const playerKey = await crypto.subtle.importKey("spki", keyDer, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
    const message = new TextEncoder().encode("stray-casino|" + name + "|" + server + "|" + Math.trunc(time) + "|" + uuid);
    if (!await crypto.subtle.verify("RSASSA-PKCS1-v1_5", playerKey, casinoBytes(body.proof), message)) {
      return { error: "bad proof" };
    }
    return { uuid };
  } catch (error) {
    return { error: String(error && error.message || error).slice(0, 80) };
  }
}
__name(casinoCertLogin, "casinoCertLogin");

async function casinoAdmin(request, env, sub) {
  const checked = await adminBody(request, env);
  if (checked.error) {
    return checked.error;
  }
  const body = checked.body || {};
  if (sub === "/admin/list") {
    return casinoBank(env, "/admin-list", { body: {} });
  }
  if (sub === "/admin/keys") {
    let data;
    try {
      data = JSON.parse(String(body.keys || ""));
    } catch {
      return json(400, { error: "That is not the JSON from the publickeys page." });
    }
    const list = data && Array.isArray(data.playerCertificateKeys) ? data.playerCertificateKeys.filter((k) => k && typeof k.publicKey === "string") : [];
    if (!list.length) {
      return json(400, { error: "No playerCertificateKeys in that text." });
    }
    for (const k of list) {
      try {
        await crypto.subtle.importKey("spki", casinoBytes(k.publicKey), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-1" }, false, ["verify"]);
      } catch {
        return json(400, { error: "One of the keys is not a valid public key." });
      }
    }
    if (!env.CAPES) {
      return json(500, { error: "The CAPES bucket is not bound." });
    }
    await env.CAPES.put("casino-mojang-keys.json", JSON.stringify({ playerCertificateKeys: list }), { httpMetadata: { contentType: "application/json" } });
    casinoMojangKeys = null;
    return json(200, { ok: true, keys: list.length });
  }
  const target = await resolvePlayer(String(body.player || ""));
  if (!target.uuid) {
    return json(404, { error: "Unknown player." });
  }
  return casinoBank(env, "/admin-adjust", {
    body: { uuid: target.uuid, name: target.name || "", amount: body.amount, mode: body.mode === "set" ? "set" : "add" }
  });
}
__name(casinoAdmin, "casinoAdmin");

// ----------------------------------------------------------------- bank (Durable Object)

async function casinoHandle(storage, op, payload) {
  const who = payload.who || {};
  const body = payload.body || {};
  const reply = /* @__PURE__ */ __name((status, data) => new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } }), "reply");
  try {
    if (op === "/admin-list") {
      const all = await storage.list({ prefix: "acct:" });
      const rows = [];
      for (const [key, acct] of all) {
        rows.push({ uuid: key.slice(5), name: acct.name || "", balance: acct.bal, wagered: acct.wagered || 0, won: acct.won || 0 });
      }
      rows.sort((a, b) => b.balance - a.balance);
      return reply(200, { ok: true, count: rows.length, players: rows.slice(0, 200) });
    }
    if (op === "/admin-adjust") {
      const amount = Math.trunc(Number(body.amount));
      if (!Number.isFinite(amount) || Math.abs(amount) > 1e12) {
        return reply(400, { error: "Bad amount." });
      }
      const acct = await casinoAccount(storage, body.uuid, body.name);
      const before = acct.bal;
      acct.bal = body.mode === "set" ? Math.max(0, amount) : Math.max(0, acct.bal + amount);
      casinoNote(acct, "admin", before < acct.bal ? "An admin gave you " + (acct.bal - before) : before > acct.bal ? "An admin took " + (before - acct.bal) : "An admin set your balance");
      await casinoSave(storage, body.uuid, acct);
      return reply(200, { ok: true, uuid: body.uuid, name: acct.name, balance: acct.bal });
    }
    if (op === "/top-public") {
      const all = await storage.list({ prefix: "acct:" });
      const rows = [];
      for (const [, a] of all) {
        rows.push({ name: a.name || "?", balance: a.bal });
      }
      rows.sort((a, b) => b.balance - a.balance);
      return reply(200, { ok: true, top: rows.slice(0, 10) });
    }
    if (!who.uuid) {
      return reply(401, { error: "Log in again." });
    }
    const acct = await casinoAccount(storage, who.uuid, who.name);
    if (op === "/me") {
      // Daily reward: once per UTC day, paid the first time the player opens the casino that day.
      const today = new Date().toISOString().slice(0, 10);
      let daily = 0;
      if (acct.daily !== today) {
        acct.daily = today;
        daily = CASINO_DAILY;
        acct.bal += daily;
      }
      const notes = acct.inbox || [];
      acct.inbox = [];
      await casinoSave(storage, who.uuid, acct);
      const bj = await storage.get("bj:" + who.uuid);
      const next = new Date(today + "T00:00:00Z").getTime() + 864e5;
      return reply(200, { ok: true, balance: acct.bal, name: acct.name, inbox: notes, daily, nextDaily: next, blackjack: bj ? casinoBjView(bj, acct.bal) : null });
    }
    if (op === "/top") {
      const all = await storage.list({ prefix: "acct:" });
      const rows = [];
      for (const [, a] of all) {
        rows.push({ name: a.name || "?", balance: a.bal });
      }
      rows.sort((a, b) => b.balance - a.balance);
      return reply(200, { ok: true, top: rows.slice(0, 10), balance: acct.bal });
    }
    if (op === "/slots") {
      const bet = casinoBet(body.bet, acct.bal);
      if (typeof bet === "string") {
        return reply(400, { error: bet, balance: acct.bal });
      }
      const stops = [casinoRandom(SLOT_STRIP.length), casinoRandom(SLOT_STRIP.length), casinoRandom(SLOT_STRIP.length)];
      const grid = stops.map((s) => [-1, 0, 1].map((o) => SLOT_STRIP[(s + o + SLOT_STRIP.length) % SLOT_STRIP.length]));
      const line = grid.map((col) => col[1]);
      const mult = slotMultiplier(line);
      const win = bet * mult;
      acct.bal += win - bet;
      acct.wagered = (acct.wagered || 0) + bet;
      acct.won = (acct.won || 0) + win;
      await casinoSave(storage, who.uuid, acct);
      return reply(200, { ok: true, stops, grid, line, multiplier: mult, bet, win, balance: acct.bal });
    }
    if (op === "/roulette") {
      const bets = Array.isArray(body.bets) ? body.bets.slice(0, 60) : [];
      if (!bets.length) {
        return reply(400, { error: "Place a bet first.", balance: acct.bal });
      }
      let total = 0;
      const clean = [];
      for (const b of bets) {
        const type = String(b.type || "");
        const value = Math.trunc(Number(b.value) || 0);
        const amount = Math.trunc(Number(b.amount));
        if (!Number.isFinite(amount) || amount < 1) {
          return reply(400, { error: "Bad bet amount.", balance: acct.bal });
        }
        if (!rouletteValid(type, value)) {
          return reply(400, { error: "Bad bet.", balance: acct.bal });
        }
        total += amount;
        clean.push({ type, value, amount });
      }
      if (total > acct.bal) {
        return reply(400, { error: "Not enough chips.", balance: acct.bal });
      }
      if (total > CASINO_MAX_BET) {
        return reply(400, { error: "Bet too large.", balance: acct.bal });
      }
      const number = casinoRandom(37);
      let win = 0;
      const results = clean.map((b) => {
        const mult = rouletteMultiplier(b.type, b.value, number);
        win += b.amount * mult;
        return { ...b, win: b.amount * mult };
      });
      acct.bal += win - total;
      acct.wagered = (acct.wagered || 0) + total;
      acct.won = (acct.won || 0) + win;
      await casinoSave(storage, who.uuid, acct);
      return reply(200, { ok: true, number, color: rouletteColor(number), bets: results, bet: total, win, balance: acct.bal });
    }
    if (op === "/blackjack") {
      return casinoBlackjack(storage, who, acct, body, reply);
    }
    if (op === "/send") {
      const amount = Math.trunc(Number(body.amount));
      if (!Number.isFinite(amount) || amount < 1) {
        return reply(400, { error: "Enter an amount.", balance: acct.bal });
      }
      if (body.toUuid === who.uuid) {
        return reply(400, { error: "You can't send chips to yourself.", balance: acct.bal });
      }
      if (amount > acct.bal) {
        return reply(400, { error: "Not enough chips.", balance: acct.bal });
      }
      const other = await casinoAccount(storage, body.toUuid, body.toName);
      acct.bal -= amount;
      other.bal += amount;
      casinoNote(other, "gift", acct.name + " sent you " + amount);
      await casinoSave(storage, who.uuid, acct);
      await casinoSave(storage, body.toUuid, other);
      return reply(200, { ok: true, to: other.name || body.toName, amount, balance: acct.bal });
    }
    return reply(404, { error: "Unknown casino call." });
  } catch (error) {
    console.error(error);
    return reply(500, { error: "Casino error." });
  }
}
__name(casinoHandle, "casinoHandle");

var CASINO_DAILY = 1e3;

async function casinoAccount(storage, uuid, name) {
  let acct = await storage.get("acct:" + uuid);
  if (!acct) {
    acct = { name: name || "", bal: CASINO_START, created: Date.now(), wagered: 0, won: 0, inbox: [] };
  }
  if (name && acct.name !== name) {
    acct.name = name;
  }
  return acct;
}
__name(casinoAccount, "casinoAccount");

async function casinoSave(storage, uuid, acct) {
  await storage.put("acct:" + uuid, acct);
}
__name(casinoSave, "casinoSave");

function casinoNote(acct, kind, text) {
  acct.inbox = (acct.inbox || []).concat([{ kind, text, at: Date.now() }]).slice(-10);
}
__name(casinoNote, "casinoNote");

function casinoBet(raw, balance) {
  const bet = Math.trunc(Number(raw));
  if (!Number.isFinite(bet) || bet < 1) {
    return "Enter a bet.";
  }
  if (bet > CASINO_MAX_BET) {
    return "Bet too large.";
  }
  if (bet > balance) {
    return "Not enough chips.";
  }
  return bet;
}
__name(casinoBet, "casinoBet");

function slotMultiplier(line) {
  const [a, b, c] = line;
  const high = /* @__PURE__ */ __name((s) => s === "R" || s === "S" || s === "D", "high");
  const fruit = /* @__PURE__ */ __name((s) => s === "C" || s === "L" || s === "G", "fruit");
  if (a === b && b === c) {
    return SLOT_THREE[a] || 0;
  }
  if (high(a) && high(b) && high(c)) {
    return SLOT_ANY_HIGH;
  }
  if (a === "C" && b === "C") {
    return SLOT_TWO_CHERRY;
  }
  if (fruit(a) && fruit(b) && fruit(c)) {
    return SLOT_ANY_FRUIT;
  }
  return 0;
}
__name(slotMultiplier, "slotMultiplier");

function rouletteColor(n) {
  return n === 0 ? "green" : ROULETTE_RED.includes(n) ? "red" : "black";
}
__name(rouletteColor, "rouletteColor");

function rouletteValid(type, value) {
  switch (type) {
    case "straight":
      return value >= 0 && value <= 36;
    case "red":
    case "black":
    case "odd":
    case "even":
    case "low":
    case "high":
      return true;
    case "dozen":
    case "column":
      return value >= 1 && value <= 3;
    default:
      return false;
  }
}
__name(rouletteValid, "rouletteValid");

// Total return per 1 staked (0 = lost). Zero loses every outside bet.
function rouletteMultiplier(type, value, n) {
  switch (type) {
    case "straight":
      return n === value ? 36 : 0;
    case "red":
      return rouletteColor(n) === "red" ? 2 : 0;
    case "black":
      return rouletteColor(n) === "black" ? 2 : 0;
    case "odd":
      return n !== 0 && n % 2 === 1 ? 2 : 0;
    case "even":
      return n !== 0 && n % 2 === 0 ? 2 : 0;
    case "low":
      return n >= 1 && n <= 18 ? 2 : 0;
    case "high":
      return n >= 19 ? 2 : 0;
    case "dozen":
      return n !== 0 && Math.ceil(n / 12) === value ? 3 : 0;
    case "column":
      return n !== 0 && (n - 1) % 3 + 1 === value ? 3 : 0;
    default:
      return 0;
  }
}
__name(rouletteMultiplier, "rouletteMultiplier");

// ----------------------------------------------------------------- blackjack
// 6 decks shuffled for every round. Dealer stands on soft 17 and peeks for
// blackjack under an Ace or a ten. Blackjack pays 3:2, insurance 2:1. Double
// on any first two cards (also after a split); split pairs (same value, so
// any two tens) up to 4 hands; split Aces get one card each and can't be
// split again. No surrender.

var BJ_RANKS = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"];
var BJ_SUITS = ["S", "H", "D", "C"];

function bjShoe() {
  const shoe = [];
  for (let d = 0; d < 6; d++) {
    for (const s of BJ_SUITS) {
      for (const r of BJ_RANKS) {
        shoe.push(r + s);
      }
    }
  }
  for (let i = shoe.length - 1; i > 0; i--) {
    const j = casinoRandom(i + 1);
    const t = shoe[i];
    shoe[i] = shoe[j];
    shoe[j] = t;
  }
  return shoe;
}
__name(bjShoe, "bjShoe");

function bjRank(card) {
  return card.slice(0, -1);
}
__name(bjRank, "bjRank");

function bjValue(card) {
  const r = bjRank(card);
  if (r === "A") {
    return 11;
  }
  if (r === "J" || r === "Q" || r === "K") {
    return 10;
  }
  return Number(r);
}
__name(bjValue, "bjValue");

function bjTotal(cards) {
  let total = 0;
  let aces = 0;
  for (const c of cards) {
    total += bjValue(c);
    if (bjRank(c) === "A") {
      aces++;
    }
  }
  while (total > 21 && aces > 0) {
    total -= 10;
    aces--;
  }
  return { total, soft: aces > 0 };
}
__name(bjTotal, "bjTotal");

function bjNatural(cards) {
  return cards.length === 2 && bjTotal(cards).total === 21;
}
__name(bjNatural, "bjNatural");

function bjView(g, balance) {
  const done = g.phase === "done";
  const dealer = done ? g.dealer : [g.dealer[0], null];
  return {
    phase: g.phase,
    active: g.active,
    hands: g.hands.map((h) => ({ cards: h.cards, bet: h.bet, total: bjTotal(h.cards).total, soft: bjTotal(h.cards).soft, done: h.done, doubled: !!h.doubled, result: h.result || null, payout: h.payout || 0 })),
    dealer,
    dealerTotal: done ? bjTotal(g.dealer).total : bjValue(g.dealer[0]),
    insurance: g.insurance || 0,
    insurancePayout: g.insurancePayout || 0,
    canDouble: g.phase === "player" && bjCanDouble(g, balance),
    canSplit: g.phase === "player" && bjCanSplit(g, balance),
    message: g.message || "",
    balance
  };
}
__name(bjView, "bjView");
var casinoBjView = bjView;

function bjActive(g) {
  return g.hands[g.active];
}
__name(bjActive, "bjActive");

function bjCanDouble(g, balance) {
  const h = bjActive(g);
  return !!h && !h.done && h.cards.length === 2 && balance >= h.bet;
}
__name(bjCanDouble, "bjCanDouble");

function bjCanSplit(g, balance) {
  const h = bjActive(g);
  return !!h && !h.done && h.cards.length === 2 && g.hands.length < 4 && !h.splitAces && bjValue(h.cards[0]) === bjValue(h.cards[1]) && balance >= h.bet;
}
__name(bjCanSplit, "bjCanSplit");

async function casinoBlackjack(storage, who, acct, body, reply) {
  const key = "bj:" + who.uuid;
  let g = await storage.get(key);
  const action = String(body.action || "");
  const save = /* @__PURE__ */ __name(async () => {
    if (g.phase === "done") {
      await storage.delete(key);
    } else {
      await storage.put(key, g);
    }
    await casinoSave(storage, who.uuid, acct);
    return reply(200, { ok: true, blackjack: bjView(g, acct.bal), balance: acct.bal });
  }, "save");
  if (action === "state") {
    return reply(200, { ok: true, blackjack: g ? bjView(g, acct.bal) : null, balance: acct.bal });
  }
  if (action === "start") {
    if (g) {
      return reply(400, { error: "Finish the current hand first.", blackjack: bjView(g, acct.bal), balance: acct.bal });
    }
    const bet = casinoBet(body.bet, acct.bal);
    if (typeof bet === "string") {
      return reply(400, { error: bet, balance: acct.bal });
    }
    acct.bal -= bet;
    acct.wagered = (acct.wagered || 0) + bet;
    const shoe = bjShoe();
    const p1 = shoe.pop();
    const d1 = shoe.pop();
    const p2 = shoe.pop();
    const d2 = shoe.pop();
    g = { shoe, dealer: [d1, d2], hands: [{ cards: [p1, p2], bet, done: false }], active: 0, phase: "player", insurance: 0, base: bet };
    if (bjRank(d1) === "A") {
      g.phase = "insurance";
      g.message = "Insurance?";
      return save();
    }
    bjAfterPeek(g, acct);
    return save();
  }
  if (!g) {
    return reply(400, { error: "Deal a hand first.", balance: acct.bal });
  }
  if (g.phase === "insurance") {
    if (action !== "insurance" && action !== "noinsurance") {
      return reply(400, { error: "Take or decline insurance first.", blackjack: bjView(g, acct.bal), balance: acct.bal });
    }
    if (action === "insurance") {
      const cost = Math.floor(g.hands[0].bet / 2);
      if (cost < 1 || cost > acct.bal) {
        return reply(400, { error: "Not enough chips for insurance.", blackjack: bjView(g, acct.bal), balance: acct.bal });
      }
      acct.bal -= cost;
      acct.wagered = (acct.wagered || 0) + cost;
      g.insurance = cost;
    }
    if (bjNatural(g.dealer) && g.insurance) {
      g.insurancePayout = g.insurance * 3;
      acct.bal += g.insurancePayout;
      acct.won = (acct.won || 0) + g.insurancePayout;
    }
    g.phase = "player";
    bjAfterPeek(g, acct);
    return save();
  }
  if (g.phase !== "player") {
    return reply(400, { error: "This hand is over.", balance: acct.bal });
  }
  const h = bjActive(g);
  if (action === "hit") {
    h.cards.push(g.shoe.pop());
    if (bjTotal(h.cards).total >= 21) {
      h.done = true;
      bjNext(g, acct);
    }
    return save();
  }
  if (action === "stand") {
    h.done = true;
    bjNext(g, acct);
    return save();
  }
  if (action === "double") {
    if (!bjCanDouble(g, acct.bal)) {
      return reply(400, { error: "You can't double now.", blackjack: bjView(g, acct.bal), balance: acct.bal });
    }
    acct.bal -= h.bet;
    acct.wagered = (acct.wagered || 0) + h.bet;
    h.bet *= 2;
    h.doubled = true;
    h.cards.push(g.shoe.pop());
    h.done = true;
    bjNext(g, acct);
    return save();
  }
  if (action === "split") {
    if (!bjCanSplit(g, acct.bal)) {
      return reply(400, { error: "You can't split this hand.", blackjack: bjView(g, acct.bal), balance: acct.bal });
    }
    acct.bal -= h.bet;
    acct.wagered = (acct.wagered || 0) + h.bet;
    const aces = bjRank(h.cards[0]) === "A";
    const second = { cards: [h.cards[1]], bet: h.bet, done: false, split: true, splitAces: aces };
    h.cards = [h.cards[0]];
    h.split = true;
    h.splitAces = aces;
    h.cards.push(g.shoe.pop());
    second.cards.push(g.shoe.pop());
    g.hands.splice(g.active + 1, 0, second);
    if (aces) {
      // One card on each split Ace, nothing more.
      h.done = true;
      second.done = true;
    } else if (bjTotal(h.cards).total === 21) {
      h.done = true;
    }
    bjNext(g, acct);
    return save();
  }
  return reply(400, { error: "Unknown action.", blackjack: bjView(g, acct.bal), balance: acct.bal });
}
__name(casinoBlackjack, "casinoBlackjack");

// After the deal (and insurance): the dealer's blackjack ends the round, a
// player blackjack pays at once, otherwise play starts.
function bjAfterPeek(g, acct) {
  const up = bjValue(g.dealer[0]);
  const dealerBj = (up === 10 || up === 11) && bjNatural(g.dealer);
  const playerBj = bjNatural(g.hands[0].cards);
  if (dealerBj || playerBj) {
    g.hands[0].done = true;
    bjSettle(g, acct);
    return;
  }
  g.message = "";
}
__name(bjAfterPeek, "bjAfterPeek");

function bjNext(g, acct) {
  while (g.active < g.hands.length && g.hands[g.active].done) {
    g.active++;
  }
  if (g.active < g.hands.length) {
    const h = g.hands[g.active];
    // A split hand that reached 21 needs no more cards.
    if (bjTotal(h.cards).total === 21) {
      h.done = true;
      bjNext(g, acct);
    }
    return;
  }
  g.active = g.hands.length - 1;
  bjSettle(g, acct);
}
__name(bjNext, "bjNext");

function bjSettle(g, acct) {
  const single = g.hands.length === 1;
  const live = g.hands.some((h) => bjTotal(h.cards).total <= 21 && !(single && bjNatural(h.cards)));
  if (live) {
    // Dealer stands on all 17s.
    while (bjTotal(g.dealer).total < 17) {
      g.dealer.push(g.shoe.pop());
    }
  }
  const dealer = bjTotal(g.dealer).total;
  const dealerBj = bjNatural(g.dealer);
  let paid = 0;
  for (const h of g.hands) {
    const t = bjTotal(h.cards).total;
    const natural = single && !h.split && bjNatural(h.cards);
    if (natural && dealerBj) {
      h.result = "push";
      h.payout = h.bet;
    } else if (natural) {
      h.result = "blackjack";
      h.payout = h.bet + Math.floor(h.bet * 3 / 2);
    } else if (dealerBj) {
      h.result = "lose";
      h.payout = 0;
    } else if (t > 21) {
      h.result = "bust";
      h.payout = 0;
    } else if (dealer > 21 || t > dealer) {
      h.result = "win";
      h.payout = h.bet * 2;
    } else if (t === dealer) {
      h.result = "push";
      h.payout = h.bet;
    } else {
      h.result = "lose";
      h.payout = 0;
    }
    paid += h.payout;
  }
  acct.bal += paid;
  acct.won = (acct.won || 0) + paid;
  g.phase = "done";
  g.message = dealerBj ? "Dealer blackjack" : dealer > 21 ? "Dealer busts" : "Dealer has " + dealer;
}
__name(bjSettle, "bjSettle");

export {
  StrayLive,
  worker_default as default
};
