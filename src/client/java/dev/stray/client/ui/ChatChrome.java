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
 * so Hypixel colors and clicks still work. Incoming lines slide up; fading
 * lines slide down with the pane.
 */
public final class ChatChrome {
	private static final float PAD = 6f;
	private static final float INPUT_H = 16f;
	private static final int FADE_TICKS = 200;
	private static final IdentityHashMap<GuiMessage.Line, Float> APPEAR = new IdentityHashMap<>();
	private static final IdentityHashMap<FormattedCharSequence, Motion> MOTION = new IdentityHashMap<>();
	private static boolean skipFill;
	private static float paneRows;
	private static long lastMs;

	private ChatChrome() {
	}

	public static boolean enabled() {
		return StrayConfig.get().themedChatEnabled;
	}

	public static boolean skipFill() {
		return skipFill && enabled();
	}

	public static int lineShift(FormattedCharSequence text) {
		Motion motion = MOTION.get(text);
		return motion == null ? 0 : motion.shift;
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
		float dt = dt();
		int live = tickLines(access, ticks, focused, page, lineH, dt);
		if (live <= 0 && !focused && paneRows < 0.04f) {
			return;
		}
		float targetRows = focused ? page : live;
		paneRows = ease(paneRows, targetRows, live > paneRows ? 16f : 12f, dt);
		float rows = focused ? page : Math.max(live, paneRows);
		float w = access.stray$chatWidth() + PAD * 2f;
		float h = rows * lineH * scale + PAD * 2f;
		float x = 4f * scale - PAD;
		float y = graphics.guiHeight() - 40f - rows * lineH * scale - PAD;
		float radius = Math.min(12f, Math.min(w, h) * 0.12f);
		float paneFade = focused ? 1f : Mth.clamp(Math.max(visibleAlpha(access, ticks, page), paneRows / Math.max(1f, targetRows)), 0f, 1f);
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

	public static void inputBar(GuiGraphicsExtractor graphics, Screen screen) {
		if (!enabled() || !(screen instanceof ChatScreen) || graphics == null) {
			return;
		}
		Theme.refresh();
		float x = 2f;
		float y = screen.height - 2f - INPUT_H;
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
		int lineH,
		float dt
	) {
		MOTION.clear();
		List<GuiMessage.Line> lines = access.stray$trimmedMessages();
		if (lines == null || lines.isEmpty()) {
			APPEAR.clear();
			return 0;
		}
		int scroll = access.stray$scroll();
		int slots = Math.min(Math.max(0, lines.size() - scroll), page);
		IdentityHashMap<GuiMessage.Line, Boolean> live = new IdentityHashMap<>();
		int count = 0;
		for (int slot = 0; slot < slots; slot++) {
			GuiMessage.Line line = lines.get(slot + scroll);
			float vanilla = focused ? 1f : timeAlpha(ticks - line.addedTime());
			if (vanilla <= 1.0E-5f) {
				continue;
			}
			live.put(line, Boolean.TRUE);
			count++;
			float current = APPEAR.containsKey(line) ? APPEAR.get(line) : 0f;
			boolean leaving = !focused && vanilla < 0.999f && ticks - line.addedTime() >= FADE_TICKS - 24;
			float target = leaving ? vanilla : 1f;
			current = ease(current, target, leaving ? 14f : 18f, dt);
			APPEAR.put(line, current);
			int shift = Math.round((1f - current) * lineH);
			MOTION.put(line.content(), new Motion(shift, Mth.clamp(current, 0f, 1f)));
		}
		Iterator<Map.Entry<GuiMessage.Line, Float>> it = APPEAR.entrySet().iterator();
		while (it.hasNext()) {
			if (!live.containsKey(it.next().getKey())) {
				it.remove();
			}
		}
		return count;
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
			alpha = Math.max(alpha, timeAlpha(ticks - lines.get(slot + scroll).addedTime()));
		}
		return alpha;
	}

	private static int chatFill(boolean focused, float fade) {
		float opacity = ControlChrome.paneOpacity();
		float alpha = focused ? opacity * 0.48f : opacity * 0.28f * Mth.clamp(fade, 0f, 1f);
		alpha = Mth.clamp(alpha, focused ? 0.16f : 0.08f, focused ? 0.42f : 0.30f);
		return Theme.withAlpha(ControlChrome.paneRgb(), Math.round(alpha * 255f));
	}

	private static float timeAlpha(int age) {
		if (age >= FADE_TICKS) {
			return 0f;
		}
		double fade = 1.0 - age / (double) FADE_TICKS;
		fade = Mth.clamp(fade * 10.0, 0.0, 1.0);
		return (float) (fade * fade);
	}

	private static float dt() {
		long now = System.nanoTime();
		float seconds = lastMs == 0L ? 0.016f : (now - lastMs) / 1_000_000_000f;
		lastMs = now;
		return Mth.clamp(seconds, 0.004f, 0.05f);
	}

	private static float ease(float current, float target, float speed, float dt) {
		if (Math.abs(target - current) < 0.004f) {
			return target;
		}
		return current + (target - current) * (1f - (float) Math.exp(-speed * dt));
	}

	private record Motion(int shift, float alpha) {
	}
}
