package dev.stray.client.ui;

import dev.stray.client.dungeon.EtherwarpAim;
import dev.stray.client.dungeon.EtherwarpRoutes;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Routes other players shared from Etherwarp aim. Search, look at which rooms a route has
 * (the eye), take it, share your own, or take yours down.
 */
public class EtherwarpRoutesScreen extends SubScreen {
	private static final String ICON_EYE = "\uE8F4";
	private static final String ICON_EYE_OFF = "\uE8F5";
	private static final float ROW_H = 30f;

	private final Input search = input("routes-search", 32, "Search routes or players");
	private final Input name = input("routes-name", 32, "Name your route");
	private List<EtherwarpRoutes.Summary> routes = List.of();
	private boolean loading = true;
	private String status = "";
	private boolean statusError;
	/** The route whose rooms are shown, or null. */
	private String peek;
	private String peekError = "";
	private String selected;
	private boolean busy;

	public EtherwarpRoutesScreen(Screen parent) {
		super("Routes", parent, 420);
		focusInput(null);
		refresh();
	}

	private void refresh() {
		loading = true;
		EtherwarpRoutes.list(result -> {
			loading = false;
			if (result.error() != null) {
				say(result.error(), true);
				return;
			}
			routes = result.value();
		});
	}

	private void say(String text, boolean error) {
		status = text == null ? "" : text;
		statusError = error;
	}

	private List<EtherwarpRoutes.Summary> shown() {
		String q = search.get().trim().toLowerCase(Locale.ROOT);
		List<EtherwarpRoutes.Summary> out = new ArrayList<>();
		for (EtherwarpRoutes.Summary route : routes) {
			if (q.isEmpty() || route.name().toLowerCase(Locale.ROOT).contains(q) || route.author().toLowerCase(Locale.ROOT).contains(q)) {
				out.add(route);
			}
		}
		// Yours first, then newest.
		out.sort((a, b) -> a.mine() != b.mine() ? (a.mine() ? -1 : 1) : Long.compare(b.at(), a.at()));
		return out;
	}

	@Override
	protected float body(float x, float y, float w) {
		y = field(search, null, 0f, x, y, w) + GAP;
		List<EtherwarpRoutes.Summary> rows = shown();
		EtherwarpRoutes.Detail detail = peek == null ? null : EtherwarpRoutes.cached(peek);
		// The rooms of the route you peek at, under the list.
		float peekH = peek == null ? 0f : 14f + 4f + Math.min(5, detail == null ? 1 : Math.max(1, (detail.rooms().size() + 1) / 2)) * 14f + GAP;
		float after = peekH + 4f + GAP + FIELD + 4f + 14f + 6f + BUTTON;
		String empty = loading ? "Loading…" : routes.isEmpty() ? "No shared routes yet" : "Nothing matches";
		y = list("routes", rows.size(), ROW_H, x, y, w, listHeight(y, after, ROW_H, Math.min(rows.size(), 6), 3), empty, (i, rx, ry, rw, rh, hot) -> {
			EtherwarpRoutes.Summary route = rows.get(i);
			boolean on = route.id().equals(selected);
			card(rx, ry, rw, rh, hot, on);
			float right = rx + rw - 4f;
			if (route.mine()) {
				right = rowDelete(right, ry, rh, () -> delete(route)) - 2f;
			}
			// The eye: show which rooms it has.
			boolean peeking = route.id().equals(peek);
			float ex = right - 20f;
			boolean eyeHot = hovered(ex, ry, 20f, rh);
			icon(peeking ? ICON_EYE_OFF : ICON_EYE, ex + 3f, ry, rh, peeking || eyeHot ? TEXT : FAINT);
			hit(ex, ry, 20f, rh, () -> togglePeek(route));
			right = ex - 6f;
			String counts = route.rooms() + (route.rooms() == 1 ? " room" : " rooms") + " · " + route.points() + " pts";
			float cw = GuiDraw.smallWidth(font, counts);
			GuiDraw.small(graphics, font, counts, right - cw, ry + 6f, FAINT);
			GuiDraw.text(graphics, font, MenuFont.shadow(fit(route.name(), right - rx - 8f - cw - 8f)), rx + 8f, ry + 5f, TEXT, false);
			String by = "by " + route.author() + (route.mine() ? " (you)" : "");
			GuiDraw.small(graphics, font, dev.stray.client.util.TextFit.small(font, by, (int) (right - rx - 8f)), rx + 8f, ry + 17f, MUTED);
			hit(rx, ry, ex - rx, rh, () -> selected = on ? null : route.id());
		});

		if (peek != null) {
			y += GAP;
			EtherwarpRoutes.Summary route = find(peek);
			y = section(route == null ? "Rooms" : "Rooms · " + route.name(), x, y);
			if (detail == null) {
				GuiDraw.small(graphics, font, peekError.isEmpty() ? "Loading…" : peekError, x, smallMiddle(y, 14f), peekError.isEmpty() ? MUTED : DANGER);
				y += 14f;
			} else {
				List<String> names = detail.roomNames();
				int perColumn = Math.min(5, Math.max(1, (names.size() + 1) / 2));
				float colW = w * 0.5f;
				for (int i = 0; i < names.size() && i < perColumn * 2; i++) {
					String room = names.get(i);
					int n = detail.rooms().get(room).getAsJsonArray().size();
					String line = displayRoom(room) + " · " + n + (n == 1 ? " pt" : " pts");
					if (i == perColumn * 2 - 1 && names.size() > perColumn * 2) {
						line = "+" + (names.size() - i) + " more";
					}
					float cx = x + (i / perColumn) * colW;
					float cy = y + (i % perColumn) * 14f;
					GuiDraw.small(graphics, font, dev.stray.client.util.TextFit.small(font, line, (int) (colW - 8f)), cx, smallMiddle(cy, 14f), MUTED);
				}
				y += perColumn * 14f;
			}
		}

		y += 4f + GAP;
		float shareW = buttonWidth("Share mine");
		field(name, null, 0f, x, y, w - shareW - GAP);
		int mine = EtherwarpAim.savedRooms();
		button("Share mine", x + w - shareW, y + (FIELD - BUTTON) * 0.5f, shareW, Style.SECONDARY,
			busy || mine == 0 || name.get().isBlank() ? null : this::share);
		y += FIELD + 4f;
		y = hint(status, x, y + 2f, w, statusError) + 6f;
		EtherwarpRoutes.Summary picked = selected == null ? null : find(selected);
		return buttons(x, y, w,
			new Object[][] {{"Reload", Style.SECONDARY, (Runnable) this::refresh}},
			new Object[][] {{"Done", Style.SECONDARY, (Runnable) this::onClose},
				{"Use route", Style.PRIMARY, picked == null || busy ? null : (Runnable) () -> take(picked)}});
	}

	/** "@boss:f7" → "Boss F7", "@minecraft:overworld" → "World minecraft:overworld"; room names as they are. */
	private static String displayRoom(String key) {
		if (key.startsWith("@boss:f")) {
			return "Boss F" + key.substring(7);
		}
		if (key.startsWith("@")) {
			return "Area " + key.substring(1);
		}
		return key;
	}

	private EtherwarpRoutes.Summary find(String id) {
		for (EtherwarpRoutes.Summary route : routes) {
			if (route.id().equals(id)) {
				return route;
			}
		}
		return null;
	}

	private void togglePeek(EtherwarpRoutes.Summary route) {
		if (route.id().equals(peek)) {
			peek = null;
			return;
		}
		peek = route.id();
		peekError = "";
		EtherwarpRoutes.detail(route.id(), result -> {
			if (result.error() != null && route.id().equals(peek)) {
				peekError = result.error();
			}
		});
	}

	private void take(EtherwarpRoutes.Summary route) {
		busy = true;
		say("Loading " + route.name() + "…", false);
		EtherwarpRoutes.detail(route.id(), result -> {
			busy = false;
			if (result.error() != null) {
				say(result.error(), true);
				return;
			}
			int rooms = EtherwarpAim.importRooms(result.value().rooms());
			say(rooms == 0 ? "That route has no points." : "Took " + rooms + (rooms == 1 ? " room" : " rooms") + " from " + route.name() + ". Your other rooms stay.", rooms == 0);
		});
	}

	private void share() {
		String title = name.get().trim();
		if (title.isEmpty()) {
			return;
		}
		busy = true;
		say("Sharing…", false);
		EtherwarpRoutes.upload(title, EtherwarpAim.exportRooms(), result -> {
			busy = false;
			if (result.error() != null) {
				say(result.error(), true);
				return;
			}
			say("Shared as " + title + ".", false);
			name.set("");
			refresh();
		});
	}

	private void delete(EtherwarpRoutes.Summary route) {
		busy = true;
		EtherwarpRoutes.delete(route.id(), result -> {
			busy = false;
			if (result.error() != null) {
				say(result.error(), true);
				return;
			}
			if (route.id().equals(selected)) {
				selected = null;
			}
			if (route.id().equals(peek)) {
				peek = null;
			}
			say("Deleted " + route.name() + ".", false);
			refresh();
		});
	}

	@Override
	protected void submit() {
		if (name.focused() && !name.get().isBlank() && !busy) {
			share();
		}
	}
}
