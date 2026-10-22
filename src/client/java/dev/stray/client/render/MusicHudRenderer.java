package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.media.CoverArt;
import dev.stray.client.media.MediaSession;
import dev.stray.client.media.NowPlaying;
import dev.stray.client.media.SpotifySmtc;
import dev.stray.client.ui.Anim;
import dev.stray.client.ui.MaterialPalette;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.HudEditorScreen;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class MusicHudRenderer {
	public static final float WIDTH = 248;
	public static final float HEIGHT = 46;
	public static final float HEIGHT_IDLE = 36;
	private static final float CONTROL_H = 18;
	private static final float COVER = 36;
	private static final float COVER_PAD = 5;

	private static final String ICON = "\uE405";
	private static final String PREV = "\uE045";
	private static final String PLAY = "\uE037";
	private static final String PAUSE = "\uE034";
	private static final String NEXT = "\uE044";

	private static Rect prevHit = Rect.EMPTY;
	private static Rect playHit = Rect.EMPTY;
	private static Rect nextHit = Rect.EMPTY;
	private static float reveal;
	private static NowPlaying cachedTrack;
	private static long cachedTrackNs;
	private static boolean cachedSpotify;
	private static String cachedTitleSrc = "";
	private static String cachedTitleOut = "";
	private static float cachedTitleMax = Float.NaN;
	private static String cachedArtistSrc = "";
	private static String cachedArtistOut = "";
	private static float cachedArtistMax = Float.NaN;

	private MusicHudRenderer() {
	}

	public static void init() {
	}

	public static float drawWidth() {
		if (layout() == Layout.BAR) {
			return barWidth(Minecraft.getInstance().font, resolveTrack());
		}
		return layout().width();
	}

	public static float drawHeight() {
		NowPlaying track = resolveTrack();
		Layout layout = layout();
		boolean idle = !track.present() && !HudLayout.editorOpen();
		if (idle && StrayConfig.get().musicHideIdle) {
			return 0;
		}
		boolean live = track.present() || HudLayout.editorOpen();
		float body = live ? layout.bodyHeight() : layout.idleHeight();
		if (HudLayout.editorOpen() && layout.expandControls()) {
			return body + CONTROL_H;
		}
		return body;
	}

	public static boolean interactive() {
		return Minecraft.getInstance().screen instanceof ChatScreen;
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != 0 || !StrayConfig.get().musicHudEnabled) {
			return false;
		}
		if (Minecraft.getInstance().screen instanceof HudEditorScreen) {
			return false;
		}
		if (!interactive() || reveal < 0.85f) {
			return false;
		}
		double x = event.x();
		double y = event.y();
		if (prevHit.contains(x, y)) {
			MediaSession.previous();
			return true;
		}
		if (playHit.contains(x, y)) {
			MediaSession.playPause();
			return true;
		}
		if (nextHit.contains(x, y)) {
			MediaSession.next();
			return true;
		}
		return false;
	}

	private static NowPlaying resolveTrack() {
		long now = System.nanoTime();
		boolean spotify = StrayConfig.get().spotifyEnabled;
		if (cachedTrack != null && spotify == cachedSpotify && now - cachedTrackNs < 2_000_000L) {
			return cachedTrack;
		}
		cachedSpotify = spotify;
		cachedTrackNs = now;
		if (spotify) {
			NowPlaying live = SpotifySmtc.current();
			if (live.present()) {
				return cachedTrack = live;
			}
		}
		return cachedTrack = MediaSession.current();
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui) {
			clearHits();
			reveal = 0f;
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.musicHudEnabled) {
			clearHits();
			reveal = 0f;
			return;
		}
		NowPlaying track = resolveTrack();
		if (!track.present() && config.musicHideIdle && !HudLayout.editorOpen()) {
			clearHits();
			reveal = 0f;
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.MUSIC, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.MUSIC), track, deltaTracker);
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, float scale, NowPlaying track) {
		draw(graphics, font, x, y, scale, track, DeltaTracker.ONE);
	}

	public static void draw(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float scale,
		NowPlaying track,
		DeltaTracker deltaTracker
	) {
		Layout layout = layout();
		boolean live = track.present();
		boolean editor = HudLayout.editorOpen();
		float width = layout.width();
		float body = live || editor ? layout.bodyHeight() : layout.idleHeight();
		boolean chat = interactive();
		float dt = Math.min(0.05f, Math.max(0f, deltaTracker.getRealtimeDeltaTicks() / 20f));
		float extraMax = layout.expandControls() ? CONTROL_H : 0f;
		float target = 0f;
		if (live || editor) {
			boolean over = mouseOver(x, y, scale, width, body + extraMax * Math.max(reveal, 0.02f))
				|| mouseOver(x, y, scale, width, body);
			target = editor || (chat && over) ? 1f : 0f;
		}
		if (editor) {
			reveal = 1f;
		} else if (!live || !chat) {
			reveal = Anim.exp(reveal, 0f, 18f, dt);
		} else {
			reveal = Anim.exp(reveal, target, 18f, dt);
		}
		float extra = extraMax * reveal;
		float h = body + extra;

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		if (layout == Layout.BAR) {
			drawBarLayout(graphics, font, x, y, scale, track, live, chat, editor);
			graphics.pose().popMatrix();
			return;
		}
		float radius = layout == Layout.COMPACT || layout == Layout.DOCK ? 5 : 6;
		HudChrome.panel(graphics, 0, 0, width, h, radius, Theme.WINDOW, Theme.LINE);

		if (!live) {
			drawIdle(graphics, font, layout, width, body);
			clearControlHits();
			graphics.pose().popMatrix();
			return;
		}

		switch (layout) {
			case COMPACT -> drawCompact(graphics, font, x, y, scale, width, body, track, chat, editor);
			case POSTER -> drawPoster(graphics, font, x, y, scale, width, body, track, chat, editor);
			case DOCK -> drawDock(graphics, font, x, y, scale, width, body, track, chat, editor);
			case CARD -> drawCard(graphics, font, x, y, scale, width, body, track, chat, editor);
		}
		graphics.pose().popMatrix();
	}

	private static void drawIdle(GuiGraphicsExtractor graphics, Font font, Layout layout, float width, float body) {
		if (layout == Layout.POSTER) {
			float cover = drawCover(graphics, font, NowPlaying.none(), false, 12, 12, 88);
			GuiDraw.menu(graphics, font, HudLayout.editorOpen() ? "Music" : "Nothing playing", 10, 12 + cover + 8, Theme.TEXT);
			GuiDraw.small(graphics, font, ellipsize(font, MediaSession.hint(), width - 20, true), 10, 12 + cover + 20, Theme.MUTED);
			return;
		}
		float cover = 0f;
		if (layout != Layout.COMPACT) {
			cover = drawCover(graphics, font, NowPlaying.none(), false, COVER_PAD, (body - Math.min(COVER, body - COVER_PAD * 2f)) * 0.5f, Math.min(layout == Layout.DOCK ? 22 : COVER, body - COVER_PAD * 2f));
		}
		float textX = layout == Layout.COMPACT ? 8 : COVER_PAD + cover + 6;
		GuiDraw.menu(graphics, font, HudLayout.editorOpen() ? "Music" : "Nothing playing", textX, layout == Layout.COMPACT || layout == Layout.DOCK ? body * 0.5f - 5 : 7, Theme.TEXT);
		if (layout != Layout.COMPACT && layout != Layout.DOCK) {
			GuiDraw.small(graphics, font, ellipsize(font, MediaSession.hint(), width - textX - 12, true), textX, 18, Theme.MUTED);
		}
	}

	private static void drawCard(
		GuiGraphicsExtractor graphics,
		Font font,
		float ox,
		float oy,
		float scale,
		float width,
		float body,
		NowPlaying track,
		boolean chat,
		boolean editor
	) {
		float cover = drawCover(graphics, font, track, true, COVER_PAD, (body - COVER) * 0.5f, COVER);
		float textX = COVER_PAD + cover + 6;
		String source = track.sourceLabel();
		float sourceW = GuiDraw.smallWidth(font, source);
		float titleMax = width - textX - sourceW - 16;
		String title = ellipsizeCached(font, track.title(), titleMax, false);
		String artist = ellipsizeCached(font, track.artistLine(), width - textX - 12, true);
		GuiDraw.menu(graphics, font, title, textX, 5, Theme.TEXT);
		GuiDraw.small(graphics, font, artist, textX, 15, Theme.MUTED);
		GuiDraw.small(graphics, font, source, width - 8 - sourceW, 5, Theme.ACCENT);
		drawProgress(graphics, font, track, textX, 28, width - textX - 8);
		drawControls(graphics, font, ox, oy, scale, width, HEIGHT, chat, editor, track, true);
	}

	private static void drawCompact(
		GuiGraphicsExtractor graphics,
		Font font,
		float ox,
		float oy,
		float scale,
		float width,
		float body,
		NowPlaying track,
		boolean chat,
		boolean editor
	) {
		float cover = drawCover(graphics, font, track, true, 4, (body - 16) * 0.5f, 16);
		float textX = 4 + cover + 5;
		float reserve = reveal > 0.02f ? 54 * reveal : 0f;
		String title = ellipsizeCached(font, track.title(), width - textX - 10 - reserve, false);
		GuiDraw.menu(graphics, font, title, textX, 4, Theme.TEXT);
		drawBar(graphics, track, 4, body - 5, width - 8, 2f);
		drawControls(graphics, font, ox, oy, scale, width, 7, chat, editor, track, false);
	}

	private static void drawPoster(
		GuiGraphicsExtractor graphics,
		Font font,
		float ox,
		float oy,
		float scale,
		float width,
		float body,
		NowPlaying track,
		boolean chat,
		boolean editor
	) {
		float cover = 88;
		float coverX = (width - cover) * 0.5f;
		drawCover(graphics, font, track, true, coverX, 8, cover);
		float textY = 8 + cover + 6;
		String title = ellipsizeCached(font, track.title(), width - 16, false);
		String artist = ellipsizeCached(font, track.artistLine(), width - 16, true);
		GuiDraw.menu(graphics, font, title, 8, textY, Theme.TEXT);
		GuiDraw.small(graphics, font, artist, 8, textY + 11, Theme.MUTED);
		drawProgress(graphics, font, track, 8, textY + 24, width - 16);
		drawControls(graphics, font, ox, oy, scale, width, layout().bodyHeight(), chat, editor, track, true);
	}

	private static void drawDock(
		GuiGraphicsExtractor graphics,
		Font font,
		float ox,
		float oy,
		float scale,
		float width,
		float body,
		NowPlaying track,
		boolean chat,
		boolean editor
	) {
		float cover = drawCover(graphics, font, track, true, 4, 4, 20);
		float textX = 4 + cover + 6;
		float reserve = reveal > 0.02f ? 58 * reveal : 0f;
		String line = track.title();
		if (!track.artistLine().isBlank()) {
			line = track.title() + "  ·  " + track.artistLine();
		}
		GuiDraw.menu(graphics, font, ellipsizeCached(font, line, width - textX - 12 - reserve, false), textX, 6, Theme.TEXT);
		drawBar(graphics, track, 4, body - 5, width - 8, 2.5f);
		drawControls(graphics, font, ox, oy, scale, width, 8, chat, editor, track, false);
	}

	private static void drawProgress(GuiGraphicsExtractor graphics, Font font, NowPlaying track, float x, float y, float maxW) {
		String clock = clockLine(track);
		float smallClockW = GuiDraw.smallWidth(font, clock);
		float clockW = smallClockW >= 4f ? smallClockW : GuiDraw.menuWidth(font, clock);
		float barW = Math.max(24f, maxW - clockW - 6);
		drawBar(graphics, track, x, y, barW, 3f);
		if (smallClockW >= 4f) {
			GuiDraw.small(graphics, font, clock, x + barW + 5, y - 3, Theme.TEXT);
		} else {
			GuiDraw.menu(graphics, font, clock, x + barW + 5, y - 5, Theme.TEXT);
		}
	}

	private static void drawBar(GuiGraphicsExtractor graphics, NowPlaying track, float x, float y, float w, float h) {
		float progress = progressOf(track);
		HudChrome.rounded(graphics, x, y, w, h, h * 0.5f, Theme.HUD_TRACK);
		float filled = Math.max(2f, w * progress);
		HudChrome.rounded(graphics, x, y, filled, h, h * 0.5f, Theme.ACCENT);
	}

	private static String clockLine(NowPlaying track) {
		SpotifySmtc.Track smtc = SpotifySmtc.live();
		boolean forageClock = StrayConfig.get().spotifyEnabled && smtc.active();
		return forageClock ? smtc.timeLabel() : track.clockLine();
	}

	private static float progressOf(NowPlaying track) {
		SpotifySmtc.Track smtc = SpotifySmtc.live();
		if (StrayConfig.get().spotifyEnabled && smtc.active()) {
			return smtc.progress();
		}
		return track.progress();
	}

	private static void drawControls(
		GuiGraphicsExtractor graphics,
		Font font,
		float ox,
		float oy,
		float scale,
		float width,
		float cy,
		boolean chat,
		boolean editor,
		NowPlaying track,
		boolean below
	) {
		if (reveal < 0.02f && !editor) {
			clearControlHits();
			return;
		}
		float playX;
		float prevX;
		float nextX;
		float y = cy;
		if (below) {
			y = cy + (CONTROL_H - 10) * 0.5f;
			playX = (width - 10) * 0.5f;
			prevX = playX - 26;
			nextX = playX + 26;
		} else {
			nextX = width - 16;
			playX = nextX - 18;
			prevX = playX - 18;
		}
		int control = Anim.fade(Theme.TEXT, reveal);
		int active = Anim.fade(Theme.ACCENT, reveal);
		GuiDraw.icon(graphics, font, PREV, prevX, y, control);
		GuiDraw.icon(graphics, font, track.playing() ? PAUSE : PLAY, playX, y, active);
		GuiDraw.icon(graphics, font, NEXT, nextX, y, control);
		if (reveal >= 0.85f && (chat || editor)) {
			prevHit = screenRect(ox, oy, scale, prevX - 4, y - 2, 18, 16);
			playHit = screenRect(ox, oy, scale, playX - 4, y - 2, 18, 16);
			nextHit = screenRect(ox, oy, scale, nextX - 4, y - 2, 18, 16);
		} else {
			clearControlHits();
		}
	}

	private static boolean mouseOver(float x, float y, float scale, float w, float h) {
		Minecraft client = Minecraft.getInstance();
		double mx = client.mouseHandler.getScaledXPos(client.getWindow());
		double my = client.mouseHandler.getScaledYPos(client.getWindow());
		return mx >= x && mx <= x + w * scale && my >= y && my <= y + h * scale;
	}

	private static float drawCover(
		GuiGraphicsExtractor graphics,
		Font font,
		NowPlaying track,
		boolean live,
		float x,
		float y,
		float size
	) {
		CoverArt.bind(track);
		HudChrome.rounded(graphics, x, y, size, size, Math.min(5, size * 0.2f), Theme.HUD_CARD);
		if (CoverArt.ready()) {
			int tex = CoverArt.size();
			if (HudChrome.click()) {
				if (!GuiDraw.coverBlit(graphics, CoverArt.id(), x, y, size, 0f)) {
					GuiDraw.blit(graphics, CoverArt.id(), x, y, size, size, 0f, 0f, tex, tex, tex, tex);
				}
			} else if (!GuiDraw.coverBlit(graphics, CoverArt.id(), x, y, size, Math.min(5, size * 0.2f))) {
				GuiDraw.roundedBlit(graphics, CoverArt.id(), x, y, size, size, Math.min(5, size * 0.2f), tex, Theme.HUD_CARD);
			}
			return size;
		}
		GuiDraw.icon(graphics, font, ICON, x + size * 0.5f - 5f, y + size * 0.5f - 5f, live ? Theme.ACCENT : Theme.MUTED);
		return size;
	}

	// ---- Bar: the Hyprland watermark's pill bar, for sitting next to it

	private static final float BAR_H = 18f;
	private static final float BAR_PAD = 3f;
	private static final float BAR_PILL = BAR_H - BAR_PAD * 2f;
	private static final float BAR_TEXT_MAX = 150f;
	private static final float BAR_CONTROLS = 46f;
	private static final String NOTE = "\uE405";

	/**
	 * Bar layout next to a Hyprland watermark: it takes the watermark's place
	 * and scale and shares its background, so the two read as one bar.
	 */
	public static boolean attachedToWatermark() {
		StrayConfig config = StrayConfig.get();
		if (!config.musicHudEnabled || !config.watermarkEnabled || !config.watermarkHypr() || layout() != Layout.BAR) {
			return false;
		}
		return drawHeight() > 0f;
	}

	/** Where the attached bar starts, in watermark units: right after its last pill. */
	public static float attachOffset(float watermarkWidth) {
		return watermarkWidth - BAR_PAD;
	}

	/** Extra width the attached bar adds to the shared background, in watermark units. */
	public static float attachedWidth() {
		return barWidth(Minecraft.getInstance().font, resolveTrack());
	}

	private static Component barText(String value) {
		return Component.literal(value == null ? "" : value).withStyle(MenuFont.hyprStyle(0));
	}

	private static String barLine(NowPlaying track, boolean live) {
		if (!live) {
			return HudLayout.editorOpen() ? "Music" : "Nothing playing";
		}
		String line = track.title();
		if (!track.artistLine().isBlank()) {
			line = line + "  ·  " + track.artistLine();
		}
		return line;
	}

	private static float barWidth(Font font, NowPlaying track) {
		boolean live = track.present();
		float text = Math.min(BAR_TEXT_MAX, font.width(barText(barLine(track, live))));
		float trackPill = 4f + BAR_PILL - 2f + 5f + text + 7f + BAR_CONTROLS * reveal;
		return BAR_PAD + BAR_PILL + 3f + trackPill + BAR_PAD;
	}

	private static void drawBarLayout(
		GuiGraphicsExtractor graphics,
		Font font,
		float ox,
		float oy,
		float scale,
		NowPlaying track,
		boolean live,
		boolean chat,
		boolean editor
	) {
		MaterialPalette.refresh();
		float w = barWidth(font, track);
		float r = BAR_H * 0.5f;
		if (!attachedToWatermark()) {
			GuiFrostBlur.blitWindow(graphics, 0, 0, w, BAR_H, r);
			GuiDraw.roundedFine(graphics, 0, 0, w, BAR_H, r, MaterialPalette.withAlpha(MaterialPalette.surface, 0xD8));
		}
		float pr = BAR_PILL * 0.5f;

		// note pill
		GuiDraw.roundedFine(graphics, BAR_PAD, BAR_PAD, BAR_PILL, BAR_PILL, pr, MaterialPalette.primary);
		float iconY = Math.round(BAR_PAD + (BAR_PILL - font.lineHeight) * 0.5f);
		GuiDraw.text(graphics, font, Component.literal(track.playing() ? NOTE : PAUSE).withStyle(MenuFont.ICON), BAR_PAD + 1.5f, iconY, MaterialPalette.onPrimary, false);

		// track pill: cover, text, progress
		float px = BAR_PAD + BAR_PILL + 3f;
		float pw = w - px - BAR_PAD;
		GuiDraw.roundedFine(graphics, px, BAR_PAD, pw, BAR_PILL, pr, MaterialPalette.surfaceLow);
		float cover = BAR_PILL - 2f;
		float cx = px + 2f;
		CoverArt.bind(track);
		if (live && CoverArt.ready()) {
			int tex = CoverArt.size();
			if (!GuiDraw.coverBlit(graphics, CoverArt.id(), cx, BAR_PAD + 1f, cover, cover * 0.5f)) {
				GuiDraw.roundedBlit(graphics, CoverArt.id(), cx, BAR_PAD + 1f, cover, cover, cover * 0.5f, tex, MaterialPalette.surfaceLow);
			}
		} else {
			GuiDraw.roundedFine(graphics, cx, BAR_PAD + 1f, cover, cover, cover * 0.5f, MaterialPalette.surfaceHighest);
		}
		float tx = cx + cover + 5f;
		float textMax = pw - (tx - px) - 7f - BAR_CONTROLS * reveal;
		String line = GuiDraw.ellipsize(font, barLine(track, live), textMax, false);
		// Text sits a little high so the progress line under it has room.
		float textY = Math.round(GuiDraw.middle(BAR_PAD, BAR_PILL) - (live ? 1.5f : 0f));
		GuiDraw.text(graphics, font, barText(line), tx, textY, live ? MaterialPalette.onSurface : MaterialPalette.onSurfaceVariant, false);
		if (live) {
			float progress = progressOf(track);
			float lineW = pw - (tx - px) - 8f;
			float lineY = BAR_PAD + BAR_PILL - 2.5f;
			GuiDraw.roundedFine(graphics, tx, lineY, lineW, 1f, 0.5f, MaterialPalette.outlineVariant);
			GuiDraw.roundedFine(graphics, tx, lineY, Math.max(1f, lineW * progress), 1f, 0.5f, MaterialPalette.primary);
		}
		if (live) {
			drawControls(graphics, font, ox, oy, scale, w - 2f, BAR_PAD + (BAR_PILL - 10f) * 0.5f, chat, editor, track, false);
		} else {
			clearControlHits();
		}
	}

	private static Rect screenRect(float originX, float originY, float scale, float lx, float ly, float lw, float lh) {
		return new Rect(originX + lx * scale, originY + ly * scale, lw * scale, lh * scale);
	}

	private static void clearHits() {
		clearControlHits();
	}

	private static void clearControlHits() {
		prevHit = Rect.EMPTY;
		playHit = Rect.EMPTY;
		nextHit = Rect.EMPTY;
	}

	private static Layout layout() {
		return Layout.from(StrayConfig.get().musicHudLayout);
	}

	private static String ellipsize(Font font, String value, float max, boolean small) {
		return GuiDraw.ellipsize(font, value, max, small);
	}

	private static String ellipsizeCached(Font font, String value, float max, boolean small) {
		if (small) {
			if (value == cachedArtistSrc && max == cachedArtistMax) {
				return cachedArtistOut;
			}
			cachedArtistSrc = value;
			cachedArtistMax = max;
			return cachedArtistOut = GuiDraw.ellipsize(font, value, max, true);
		}
		if (value == cachedTitleSrc && max == cachedTitleMax) {
			return cachedTitleOut;
		}
		cachedTitleSrc = value;
		cachedTitleMax = max;
		return cachedTitleOut = GuiDraw.ellipsize(font, value, max, false);
	}

	public enum Layout {
		CARD("Card", 248, 46, 36, true),
		COMPACT("Compact", 196, 26, 22, false),
		POSTER("Poster", 132, 148, 120, true),
		DOCK("Dock", 300, 30, 26, false),
		/** Watermark-height bar of pills (Hyprland look); width follows the track. */
		BAR("Bar", 200, 18, 18, false);

		private final String label;
		private final float width;
		private final float body;
		private final float idle;
		private final boolean expand;

		Layout(String label, float width, float body, float idle, boolean expand) {
			this.label = label;
			this.width = width;
			this.body = body;
			this.idle = idle;
			this.expand = expand;
		}

		public String label() {
			return label;
		}

		public float width() {
			return width;
		}

		public float bodyHeight() {
			return body;
		}

		public float idleHeight() {
			return idle;
		}

		public boolean expandControls() {
			return expand;
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public Layout next() {
			Layout[] all = values();
			return all[(ordinal() + 1) % all.length];
		}

		public static Layout from(String value) {
			if (value == null || value.isBlank()) {
				return CARD;
			}
			String key = value.trim().toLowerCase(java.util.Locale.ROOT);
			for (Layout layout : values()) {
				if (layout.id().equals(key) || layout.label.equalsIgnoreCase(key)) {
					return layout;
				}
			}
			return CARD;
		}
	}

	private record Rect(float x, float y, float w, float h) {
		private static final Rect EMPTY = new Rect(0, 0, 0, 0);

		boolean contains(double mx, double my) {
			return w > 0 && h > 0 && mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
