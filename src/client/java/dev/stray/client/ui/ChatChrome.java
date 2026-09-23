package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.ChatComponentAccessor;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.GuiFrostBlur;
import dev.stray.client.render.HudChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Optional Stray look for vanilla chat: one frost pane instead of per-line
 * black bars, and a glass input when chat is open. Message text stays vanilla
 * so Hypixel colors and clicks still work.
 *
 * Motion matches LiquidBounce HUD chat: new lines ease-out-expo from the left
 * and the stack bumps down then settles. Lines pop off instead of fading.
 */
public final class ChatChrome {
	private static final float PAD = 8f;
	private static final float INPUT_H = 20f;
	private static final float INPUT_MARGIN = 4f;
	private static final int FIELD_H = 12;
	private static final int FADE_START = 180;
	private static final float SLIDE_MS = 400f;
	private static final IdentityHashMap<GuiMessage.Line, Long> BORN = new IdentityHashMap<>();
	private static final IdentityHashMap<FormattedCharSequence, Motion> MOTION = new IdentityHashMap<>();
	private static boolean skipFill;
	private static GuiMessage.Line newest;
	private static long lastMessageMs;
	private static int bumpY;

	private ChatChrome() {
	}

	public static boolean enabled() {
		StrayConfig config = StrayConfig.get();
		return config.themedChatEnabled || config.hudStyleClick();
	}

	private static boolean click() {
		return StrayConfig.get().hudStyleClick();
	}

	public static boolean skipFill() {
		return skipFill && enabled();
	}

	public static int usageLift() {
		if (!enabled()) {
			return 0;
		}
		return Math.round(INPUT_MARGIN + INPUT_H) - 12;
	}

	public static int lineX(FormattedCharSequence text) {
		Motion motion = MOTION.get(text);
		return motion == null ? 0 : motion.shiftX;
	}

	public static int lineShift(FormattedCharSequence text) {
		return bumpY;
	}

	public static float lineAlpha(FormattedCharSequence text) {
		Motion motion = MOTION.get(text);
		return motion == null ? 1f : motion.alpha;
	}

	public static void extract(
		GuiGraphicsExtractor graphics,
		ChatComponent chat,
		int ticks,
		ChatComponent.DisplayMode mode
	) {
		if (!enabled() || graphics == null || chat == null) {
			return;
		}
		Theme.refresh();
		boolean focused = mode != null && mode.foreground;
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.options.hideGui) {
			return;
		}
		ChatComponentAccessor access = (ChatComponentAccessor) chat;
		float scale = (float) Math.max(0.01d, access.stray$chatScale());
		int lineH = Math.max(1, access.stray$lineHeight());
		int page = Math.max(1, chat.getLinesPerPage());
		int live = tickLines(access, ticks, focused, page);
		bumpY = focused || access.stray$scroll() != 0 ? 0 : messageBump(lineH);
		if (live <= 0 && !focused) {
			bumpY = 0;
			return;
		}
		float rows = focused ? page : Math.max(1, live);
		float w = access.stray$chatWidth() + PAD * 2f;
		float h = rows * lineH * scale + PAD * 2f;
		float x = 4f * scale - PAD;
		float y = graphics.guiHeight() - 40f - rows * lineH * scale - PAD + bumpY;
		if (click()) {
			HudChrome.box(graphics, x, y, w, h, 0x99000000);
			return;
		}
		float radius = paneRadius(w, h);
		int fill = chatFill(focused, 1f);
		GuiFrostBlur.blitWindow(graphics, x, y, w, h, radius);
		GuiDraw.roundedFine(graphics, x, y, w, h, radius, fill);
		GuiDraw.roundedOutline(graphics, x, y, w, h, radius, Theme.ACCENT, 1f);
	}

	public static void beginFills() {
		skipFill = enabled();
	}

	public static void endFills() {
		skipFill = false;
	}

	public static void placeInput(EditBox input, Screen screen) {
		if (!enabled() || input == null || screen == null) {
			return;
		}
		int barY = Math.round(screen.height - INPUT_MARGIN - INPUT_H);
		int fieldY = barY + Math.round((INPUT_H - FIELD_H) * 0.5f) + 3;
		int fieldX = 10;
		int fieldW = Math.max(16, screen.width - 20);
		input.setRectangle(fieldW, FIELD_H, fieldX, fieldY);
	}

	public static void inputBar(GuiGraphicsExtractor graphics, Screen screen) {
		if (!enabled() || !(screen instanceof ChatScreen) || graphics == null) {
			return;
		}
		Theme.refresh();
		float x = 2f;
		float y = screen.height - INPUT_MARGIN - INPUT_H;
		float w = screen.width - 4f;
		if (click()) {
			HudChrome.box(graphics, x, y, w, INPUT_H, 0x99000000);
			return;
		}
		float radius = 8f;
		GuiFrostBlur.blitWindow(graphics, x, y, w, INPUT_H, radius);
		GuiDraw.roundedFine(graphics, x, y, w, INPUT_H, radius, ControlChrome.windowFill());
		GuiDraw.roundedOutline(graphics, x, y, w, INPUT_H, radius, Theme.ACCENT, 1f);
	}

	public static void field(GuiGraphicsExtractor graphics, EditBox box) {
		if (!enabled() || graphics == null || box == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || !(client.screen instanceof ChatScreen)) {
			return;
		}
		if (click()) {
			return;
		}
		if (box.isBordered()) {
			MenuChrome.field(graphics, box);
		}
	}

	private static int tickLines(
		ChatComponentAccessor access,
		int ticks,
		boolean focused,
		int page
	) {
		MOTION.clear();
		List<GuiMessage.Line> lines = access.stray$trimmedMessages();
		if (lines == null || lines.isEmpty()) {
			BORN.clear();
			newest = null;
			return 0;
		}
		int scroll = access.stray$scroll();
		int slots = Math.min(Math.max(0, lines.size() - scroll), page);
		int width = Math.max(8, access.stray$chatWidth());
		long now = System.currentTimeMillis();
		IdentityHashMap<GuiMessage.Line, Boolean> seen = new IdentityHashMap<>();
		int count = 0;
		for (int slot = 0; slot < slots; slot++) {
			GuiMessage.Line line = lines.get(slot + scroll);
			int age = ticks - line.addedTime();
			if (!focused && age >= FADE_START) {
				MOTION.put(line.content(), new Motion(0, 0f));
				continue;
			}
			seen.put(line, Boolean.TRUE);
			count++;
			if (slot == 0 && line != newest) {
				newest = line;
				lastMessageMs = now;
			}
			long born = BORN.containsKey(line) ? BORN.get(line) : now;
			BORN.put(line, born);
			float t = focused ? 1f : Mth.clamp((now - born) / SLIDE_MS, 0f, 1f);
			int shiftX = Math.round((easeOutExpo(t) - 1f) * width);
			MOTION.put(line.content(), new Motion(shiftX, 1f));
		}
		Iterator<Map.Entry<GuiMessage.Line, Long>> it = BORN.entrySet().iterator();
		while (it.hasNext()) {
			if (!seen.containsKey(it.next().getKey())) {
				it.remove();
			}
		}
		return count;
	}

	private static int messageBump(int lineH) {
		float t = Mth.clamp((System.currentTimeMillis() - lastMessageMs) / SLIDE_MS, 0f, 1f);
		return Math.round((1f - easeOutExpo(t)) * lineH * 0.8f);
	}

	private static float paneRadius(float w, float h) {
		float max = Math.min(w, h) * 0.5f;
		if (max < 1.5f) {
			return Math.max(0f, max - 0.25f);
		}
		return Math.min(8f, max - 0.5f);
	}

	private static int chatFill(boolean focused, float fade) {
		float opacity = ControlChrome.paneOpacity();
		float t = Mth.clamp(fade, 0f, 1f);
		float alpha = focused ? opacity * 0.48f : opacity * 0.28f * t;
		float lo = focused ? 0.16f : 0.08f * t;
		float hi = focused ? 0.42f : 0.30f;
		alpha = Mth.clamp(alpha, lo, hi);
		return Theme.withAlpha(ControlChrome.paneRgb(), Math.round(alpha * 255f));
	}

	private static float easeOutExpo(float x) {
		if (x <= 0f) {
			return 0f;
		}
		if (x >= 1f) {
			return 1f;
		}
		return 1f - (float) Math.pow(2.0, -10.0 * x);
	}

	private record Motion(int shiftX, float alpha) {
	}
}
