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
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Optional Stray look for vanilla chat: one frost pane instead of per-line
 * black bars, and a glass input when chat is open. Message text stays vanilla
 * so Hypixel colors and clicks still work.
 */
public final class ChatChrome {
	private static final float PAD = 6f;
	private static final float INPUT_H = 16f;
	private static final int FADE_TICKS = 200;
	private static boolean skipFill;

	private ChatChrome() {
	}

	public static boolean enabled() {
		return StrayConfig.get().themedChatEnabled;
	}

	public static boolean skipFill() {
		return skipFill && enabled();
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
		Visible visible = visible(access, ticks, focused, page);
		if (visible.lines <= 0 && !focused) {
			return;
		}
		int rows = focused ? page : visible.lines;
		float w = access.stray$chatWidth() + PAD * 2f;
		float h = rows * lineH * scale + PAD * 2f;
		float x = 4f * scale - PAD;
		float y = graphics.guiHeight() - 40f - rows * lineH * scale - PAD;
		float radius = Math.min(12f, Math.min(w, h) * 0.12f);
		int fill = chatFill(focused, visible.alpha);
		if (focused) {
			GuiFrostBlur.blitWindow(graphics, x, y, w, h, radius);
		}
		GuiDraw.roundedFine(graphics, x, y, w, h, radius, fill);
		GuiDraw.roundedOutline(graphics, x, y, w, h, radius, Theme.withAlpha(Theme.LINE, focused ? 180 : 110), 1f);
		if (focused) {
			scrollbar(graphics, access, x, y, w, h, page);
		}
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

	private static void scrollbar(
		GuiGraphicsExtractor graphics,
		ChatComponentAccessor access,
		float x,
		float y,
		float w,
		float h,
		int page
	) {
		int total = access.stray$trimmedMessages().size();
		if (total <= page) {
			return;
		}
		float track = h - 8f;
		float bar = Math.max(10f, track * page / (float) total);
		float maxScroll = total - page;
		float t = maxScroll <= 0 ? 0f : Mth.clamp(access.stray$scroll() / maxScroll, 0f, 1f);
		float by = y + 4f + (track - bar) * (1f - t);
		GuiDraw.fill(graphics, x + w - 4f, y + 4f, 2f, track, Theme.withAlpha(Theme.LINE, 90));
		GuiDraw.fill(graphics, x + w - 4f, by, 2f, bar, Theme.ACCENT);
	}

	private static int chatFill(boolean focused, float fade) {
		float opacity = ControlChrome.paneOpacity();
		float alpha = focused ? opacity * 0.48f : opacity * 0.28f * Mth.clamp(fade, 0f, 1f);
		alpha = Mth.clamp(alpha, focused ? 0.16f : 0.08f, focused ? 0.42f : 0.30f);
		return Theme.withAlpha(ControlChrome.paneRgb(), Math.round(alpha * 255f));
	}

	private static Visible visible(ChatComponentAccessor access, int ticks, boolean focused, int page) {
		List<GuiMessage.Line> lines = access.stray$trimmedMessages();
		if (lines == null || lines.isEmpty()) {
			return Visible.NONE;
		}
		int scroll = access.stray$scroll();
		int slots = Math.min(Math.max(0, lines.size() - scroll), page);
		int count = 0;
		float alpha = 0f;
		for (int slot = 0; slot < slots; slot++) {
			GuiMessage.Line line = lines.get(slot + scroll);
			float next = focused ? 1f : timeAlpha(ticks - line.addedTime());
			if (next > 1.0E-5f) {
				count++;
				alpha = Math.max(alpha, next);
			}
		}
		return count == 0 ? Visible.NONE : new Visible(count, alpha);
	}

	private static float timeAlpha(int age) {
		if (age >= FADE_TICKS) {
			return 0f;
		}
		double fade = 1.0 - age / (double) FADE_TICKS;
		fade = Mth.clamp(fade * 10.0, 0.0, 1.0);
		return (float) (fade * fade);
	}

	private record Visible(int lines, float alpha) {
		private static final Visible NONE = new Visible(0, 0f);
	}
}
