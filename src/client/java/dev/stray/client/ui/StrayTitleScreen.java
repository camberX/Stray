package dev.stray.client.ui;

import dev.stray.client.account.AccountStore;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.TitleBackdrop;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Musics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stray title screen: dark starfield, tall pane buttons, no dirt background.
 */
public class StrayTitleScreen extends Screen {
	private static final float BUTTON_W = 168;
	private static final float BUTTON_H = 16;
	private static final float BUTTON_GAP = 2;
	private static final float SPLIT_GAP = 2;

	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Float> hovers = new HashMap<>();
	private long lastNs = System.nanoTime();
	private float dt = 0.016f;
	private float appear;
	private String status = "";

	public StrayTitleScreen() {
		super(Component.translatable("narrator.screen.title"));
	}

	@Override
	protected void init() {
		Theme.refresh();
		appear = 1f;
		status = "";
		AccountStore.rememberLauncher(minecraft);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public net.minecraft.sounds.Music getBackgroundMusic() {
		return Musics.MENU;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		TitleBackdrop.draw(graphics, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Theme.refresh();
		tickAnim();
		hits.clear();
		Font font = minecraft.font;
		float fade = appear;

		float colW = BUTTON_W;
		float colX = (width - colW) * 0.5f;
		float stackH = 16 + BUTTON_GAP + BUTTON_H * 3 + BUTTON_GAP * 2 + 6 + BUTTON_H;
		float colY = Mth.clamp((height - stackH) * 0.42f, 48, height - stackH - 40);

		ClickLook.panel(graphics, colX, colY - 18, colW, 16, 0, ClickLook.PANEL, Theme.LINE);
		GuiDraw.text(graphics, font, "STRAY", colX + 4, colY - 18 + (16 - font.lineHeight) * 0.5f, Anim.fade(ClickLook.TEXT, fade), true);
		String ver = "v" + modVersion();
		GuiDraw.text(graphics, font, ver, colX + colW - 4 - font.width(ver), colY - 18 + (16 - font.lineHeight) * 0.5f, Anim.fade(ClickLook.DIM, fade), true);

		float y = colY;
		y = button(graphics, font, mouseX, mouseY, colX, y, colW, "Singleplayer", true, fade, this::openSingleplayer);
		y = button(graphics, font, mouseX, mouseY, colX, y, colW, "Multiplayer", multiplayerOpen(), fade, this::openMultiplayer);
		y = button(graphics, font, mouseX, mouseY, colX, y, colW, "Accounts", true, fade, this::openAccounts);
		y += 4;
		float half = (colW - SPLIT_GAP) * 0.5f;
		button(graphics, font, mouseX, mouseY, colX, y, half, "Options", true, fade, this::openOptions);
		button(graphics, font, mouseX, mouseY, colX + half + SPLIT_GAP, y, half, "Quit", true, fade, this::quit);

		String user = minecraft.getUser() == null ? "" : minecraft.getUser().getName();
		if (!user.isBlank()) {
			float userW = GuiDraw.smallWidth(font, user);
			float userX = width - 12 - userW;
			boolean userHover = GuiDraw.hovered(mouseX, mouseY, userX - 2, 8, userW + 4, 12);
			GuiDraw.small(graphics, font, user, userX, 10, Anim.fade(userHover ? Theme.ACCENT : Theme.MUTED, fade));
			hits.add(new Hit(userX - 2, 8, userW + 4, 12, this::openAccounts));
		}
		if (!status.isBlank()) {
			GuiDraw.small(graphics, font, status, colX, y + BUTTON_H + 10, Anim.fade(Theme.WARN, fade));
		}

		float footY = height - 18;
		link(graphics, font, mouseX, mouseY, 12, footY, "Language", fade, this::openLanguage);
		link(graphics, font, mouseX, mouseY, 12 + GuiDraw.smallWidth(font, "Language") + 14, footY, "Accessibility", fade, this::openAccessibility);

		String mc = "Minecraft " + SharedConstants.getCurrentVersion().name();
		GuiDraw.small(graphics, font, mc, (width - GuiDraw.smallWidth(font, mc)) * 0.5f, footY, Anim.fade(Theme.MUTED, fade));

		String copy = "Copyright Mojang AB. Do not distribute!";
		float copyW = GuiDraw.smallWidth(font, copy);
		float copyX = width - 12 - copyW;
		link(graphics, font, mouseX, mouseY, copyX, footY, copy, fade, this::openCredits);
	}

	private float button(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		String label,
		boolean enabled,
		float fade,
		Runnable action
	) {
		boolean hovered = enabled && GuiDraw.hovered(mouseX, mouseY, x, y, w, BUTTON_H);
		int outline = hovered ? Theme.ACCENT : Theme.LINE;
		int fill = enabled ? ClickLook.FILL : 0x44000000;
		ClickLook.panel(graphics, x, y, w, BUTTON_H, 0, Anim.fade(fill, fade), outline);
		int text = enabled ? (hovered ? ClickLook.TEXT : ClickLook.TEXT) : ClickLook.DIM;
		float textX = x + (w - font.width(label)) * 0.5f;
		GuiDraw.text(graphics, font, label, textX, y + (BUTTON_H - font.lineHeight) * 0.5f, Anim.fade(text, fade), true);
		if (enabled) {
			hits.add(new Hit(x, y, w, BUTTON_H, action));
		}
		return y + BUTTON_H + BUTTON_GAP;
	}

	private void link(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		String label,
		float fade,
		Runnable action
	) {
		float w = GuiDraw.smallWidth(font, label);
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x - 2, y - 2, w + 4, 12);
		int color = hovered ? Theme.ACCENT : Theme.MUTED;
		GuiDraw.small(graphics, font, label, x, y, Anim.fade(color, fade));
		hits.add(new Hit(x - 2, y - 2, w + 4, 12, action));
	}

	private float anim(String key, float target) {
		float current = hovers.getOrDefault(key, 0f);
		float next = Anim.exp(current, target, 18f, dt);
		hovers.put(key, next);
		return next;
	}

	private void tickAnim() {
		long now = System.nanoTime();
		dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		appear = 1f;
	}

	private boolean multiplayerOpen() {
		return minecraft.allowsMultiplayer();
	}

	private void clickSound() {
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
	}

	private void openSingleplayer() {
		clickSound();
		minecraft.setScreen(new SelectWorldScreen(this));
	}

	private void openMultiplayer() {
		if (!multiplayerOpen()) {
			status = "Multiplayer is disabled on this account.";
			return;
		}
		clickSound();
		minecraft.setScreen(new JoinMultiplayerScreen(this));
	}

	private void openAccounts() {
		clickSound();
		minecraft.setScreen(new AccountScreen(this));
	}

	private void openOptions() {
		clickSound();
		minecraft.setScreen(new OptionsScreen(this, minecraft.options, false));
	}

	private void openLanguage() {
		clickSound();
		minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()));
	}

	private void openAccessibility() {
		clickSound();
		minecraft.setScreen(new AccessibilityOptionsScreen(this, minecraft.options));
	}

	private void openCredits() {
		clickSound();
		minecraft.setScreen(new CreditsAndAttributionScreen(this));
	}

	private void quit() {
		clickSound();
		minecraft.stop();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.click.run();
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	private static String modVersion() {
		return FabricLoader.getInstance()
			.getModContainer("stray")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("1.3.33");
	}

	private record Hit(float x, float y, float w, float h, Runnable click) {
		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
