package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.ChatComponentAccessor;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.GuiFrostBlur;
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
 * Motion matches LiquidBounce HUD chat: new lines ease-out-expo from the left,
 * the stack bumps down then settles, and fading lines slide back out.
 */
public final class ChatChrome {
	private static final float PAD = 8f;
	private static final float INPUT_H = 20f;
	private static final float INPUT_MARGIN = 4f;
	private static final int FIELD_H = 12;
	private static final int FADE_START = 180;
	private static final int LEAVE_TICKS = 6;
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
		return StrayConfig.get().themedChatEnabled;
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
		Motion motion = MOTION.get(text);
		return (motion == null ? 0 : motion.shiftY) + bumpY;
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
		int live = tickLines(access, ticks, focused, page, lineH);
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
		float radius = paneRadius(w, h);
		float paneFade = focused ? 1f : visibleAlpha(access, ticks, page);
		int fill = chatFill(focused, paneFade);
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
		if (box.isBordered()) {
			MenuChrome.field(graphics, box);
		}
	}

	private static int tickLines(
		ChatComponentAccessor access,
		int ticks,
		boolean focused,
		int page,
		int lineH
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
			float leave = focused ? 0f : leaveAmount(age);
			if (leave >= 1f) {
				continue;
			}
			seen.put(line, Boolean.TRUE);
			if (leave <= 0f) {
				count++;
			}
			if (slot == 0 && line != newest) {
				newest = line;
				lastMessageMs = now;
			}
			long born = BORN.containsKey(line) ? BORN.get(line) : now;
			BORN.put(line, born);
			float t = focused ? 1f : Mth.clamp((now - born) / SLIDE_MS, 0f, 1f);
			float in = easeOutExpo(t);
			float out = easeOutExpo(leave);
			int shiftX = Math.round((in - 1f) * width - out * width);
			int shiftY = Math.round(out * lineH * 0.35f);
			float alpha = 1f - out;
			MOTION.put(line.content(), new Motion(shiftX, shiftY, alpha));
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

	private static float visibleAlpha(ChatComponentAccessor access, int ticks, int page) {
		List<GuiMessage.Line> lines = access.stray$trimmedMessages();
		if (lines == null || lines.isEmpty()) {
			return 0f;
		}
		int scroll = access.stray$scroll();
		int slots = Math.min(Math.max(0, lines.size() - scroll), page);
		float alpha = 0f;
		for (int slot = 0; slot < slots; slot++) {
			int age = ticks - lines.get(slot + scroll).addedTime();
			if (leaveAmount(age) > 0f) {
				continue;
			}
			alpha = Math.max(alpha, timeAlpha(age));
		}
		return alpha;
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

	private static float timeAlpha(int age) {
		if (age >= FADE_START) {
			return 0f;
		}
		return 1f;
	}

	private static float leaveAmount(int age) {
		if (age < FADE_START) {
			return 0f;
		}
		return Mth.clamp((age - FADE_START) / (float) LEAVE_TICKS, 0f, 1f);
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

	private record Motion(int shiftX, int shiftY, float alpha) {
	}
}
