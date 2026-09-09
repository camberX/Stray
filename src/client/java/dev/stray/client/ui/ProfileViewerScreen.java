package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.StrayClient;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.profile.ProfileViewer;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.render.PlayerPreview;
import dev.stray.client.render.Starfield;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stray Skyblock profile viewer. Tabs and chrome are ours; the numbers come
 * from the public Hypixel profile host.
 */
public class ProfileViewerScreen extends Screen {
	private static final float MENU_W = 560;
	private static final float MENU_H = 312;
	private static final float RAIL = 78;
	private static final float ROW = 16;

	private enum Tab {
		HOME("Home"),
		SKILLS("Skills"),
		COMBAT("Combat"),
		MINING("Mining"),
		FARMING("Farm"),
		PETS("Pets"),
		ITEMS("Items");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private final List<Hit> hits = new ArrayList<>();
	private Tab tab = Tab.HOME;
	private String query;
	private boolean queryFocused;
	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;
	private float viewScale = 1f;
	private float viewCx;
	private float viewCy;
	private float viewLift;
	private boolean placed;
	private long lastNs = System.nanoTime();
	private float dt = 0.016f;
	private float appear;
	private float listScroll;
	private String tooltip = "";

	public ProfileViewerScreen(String name) {
		super(Component.literal("Profile"));
		this.query = name == null ? "" : name.trim();
	}

	public void lookup(String name) {
		query = name == null ? "" : name.trim();
		queryFocused = false;
		listScroll = 0f;
		load();
	}

	public boolean queryFocused() {
		return queryFocused;
	}

	@Override
	protected void init() {
		super.init();
		Theme.refresh();
		placed = false;
		appear = StrayConfig.get().loadoutsOpenAnim ? 0.08f : 1f;
		if (query.isBlank()) {
			if (ProfileViewer.status() == ProfileViewer.Status.IDLE) {
				load();
			} else {
				query = ProfileViewer.query();
			}
		} else if (ProfileViewer.status() == ProfileViewer.Status.IDLE
			|| !query.equalsIgnoreCase(ProfileViewer.query())) {
			load();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level != null) {
			extractBlurredBackground(graphics);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		tickAnim();
		hits.clear();
		tooltip = "";
		Font font = minecraft.font;
		layout();

		int dim = Anim.fade(0x28000000, appear);
		GuiDraw.fill(graphics, 0, 0, width, height, dim);

		float scale = (0.94f + 0.06f * appear) * StrayConfig.normalizeMenuScale(StrayConfig.get().menuScale);
		float lift = (1f - appear) * 10f;
		float cx = windowX + windowW * 0.5f;
		float cy = windowY + windowH * 0.5f;
		viewScale = Math.max(0.35f, scale);
		viewCx = cx;
		viewCy = cy;
		viewLift = lift;
		int localMx = Math.round(localX(mouseX));
		int localMy = Math.round(localY(mouseY));

		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy + lift);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-cx, -cy);

		boolean clip = GuiDraw.scissor(graphics, windowX, windowY, windowW, windowH);
		if (ControlChrome.on()) {
			ControlChrome.glass(graphics, windowX, windowY, windowW, windowH, ControlChrome.WINDOW_R, Theme.WINDOW);
		} else {
			GuiDraw.rounded(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW);
		}
		Starfield.draw(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, appear);
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}

		drawHeader(graphics, font, localMx, localMy);
		drawRail(graphics, font, localMx, localMy);
		drawBody(graphics, font, localMx, localMy);

		graphics.pose().popMatrix();

		if (!tooltip.isBlank()) {
			graphics.setTooltipForNextFrame(font, Component.literal(tooltip), mouseX, mouseY);
		}
	}

	private void drawHeader(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		GuiDraw.title(graphics, font, "PROFILE", windowX + 12, windowY + 8, Theme.TEXT);
		ProfileViewer.Snapshot snap = ProfileViewer.snapshot();
		ProfileViewer.Profile profile = snap.current();
		String sub = snap.name().isBlank() ? "Skyblock" : snap.name();
		if (!profile.cuteName().isBlank()) {
			sub = sub + " · " + profile.cuteName();
		}
		GuiDraw.small(
			graphics,
			font,
			sub,
			windowX + 12 + GuiDraw.titleWidth(font, "PROFILE") + 6,
			windowY + 10,
			Theme.ACCENT
		);

		float closeW = 16;
		float refreshW = 20;
		float fieldW = 96;
		float x = windowX + windowW - 10 - closeW;
		chip(graphics, font, x, windowY + 7, closeW, mouseX, mouseY, "×", this::onClose);
		x -= refreshW + 4;
		chip(graphics, font, x, windowY + 7, refreshW, mouseX, mouseY, "↻", this::load);
		x -= fieldW + 4;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, windowY + 7, fieldW, 16);
		GuiDraw.panel(graphics, x, windowY + 7, fieldW, 16, 5, queryFocused || hover ? Theme.CARD_HOVER : Theme.CARD, queryFocused ? Theme.ACCENT : Theme.LINE);
		String shown = query.isBlank() ? "name" : query;
		GuiDraw.small(graphics, font, clip(font, shown, fieldW - 10), x + 5, GuiDraw.middle(windowY + 7, 16), query.isBlank() ? Theme.OFF : Theme.TEXT);
		hits.add(new Hit(x, windowY + 7, fieldW, 16, () -> queryFocused = true));

		float chipY = windowY + 28;
		float chipX = windowX + RAIL + 10;
		List<ProfileViewer.Profile> profiles = snap.profiles();
		for (int i = 0; i < profiles.size(); i++) {
			ProfileViewer.Profile next = profiles.get(i);
			String label = next.cuteName().isBlank() ? "#" + (i + 1) : next.cuteName();
			float w = GuiDraw.smallWidth(font, label) + 12;
			boolean on = i == snap.selected();
			boolean over = GuiDraw.hovered(mouseX, mouseY, chipX, chipY, w, 14);
			GuiDraw.panel(graphics, chipX, chipY, w, 14, 5, on || over ? Theme.CARD_HOVER : Theme.CARD, on ? Theme.ACCENT : Theme.LINE);
			GuiDraw.small(graphics, font, label, chipX + 6, GuiDraw.middle(chipY, 14), on ? Theme.ACCENT : Theme.TEXT);
			int index = i;
			hits.add(new Hit(chipX, chipY, w, 14, () -> {
				ProfileViewer.select(index);
				listScroll = 0f;
			}));
			chipX += w + 4;
			if (chipX > windowX + windowW - 20) {
				break;
			}
		}
	}

	private void drawRail(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = windowX + 8;
		float y = windowY + 48;
		float w = RAIL - 10;
		for (Tab value : Tab.values()) {
			boolean on = tab == value;
			boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, 22);
			int fill = on ? Theme.withAlpha(Theme.ACCENT, 38) : hover ? Theme.CARD_HOVER : Theme.CARD;
			int line = on ? Theme.ACCENT : Theme.LINE;
			GuiDraw.panel(graphics, x, y, w, 22, 6, fill, line);
			GuiDraw.menu(graphics, font, value.label, x + 8, GuiDraw.middle(y, 22), on ? Theme.ACCENT : Theme.TEXT);
			Tab next = value;
			hits.add(new Hit(x, y, w, 22, () -> {
				tab = next;
				listScroll = 0f;
				queryFocused = false;
			}));
			y += 26;
		}
	}

	private void drawBody(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = windowX + RAIL + 8;
		float y = windowY + 46;
		float w = windowW - RAIL - 18;
		float h = windowH - 56;
		GuiDraw.panel(graphics, x, y, w, h, 8, Theme.PANEL, Theme.LINE);
		ProfileViewer.Status status = ProfileViewer.status();
		if (status == ProfileViewer.Status.LOADING) {
			GuiDraw.menu(graphics, font, "Loading " + (query.isBlank() ? "profile" : query) + "...", x + 12, y + 14, Theme.MUTED);
			return;
		}
		if (status == ProfileViewer.Status.ERROR) {
			GuiDraw.menu(graphics, font, ProfileViewer.error().isBlank() ? "Could not load profile" : ProfileViewer.error(), x + 12, y + 14, Theme.DANGER);
			GuiDraw.small(graphics, font, "Type a name and press Enter, or click ↻", x + 12, y + 30, Theme.MUTED);
			return;
		}
		ProfileViewer.Snapshot snap = ProfileViewer.snapshot();
		ProfileViewer.Profile profile = snap.current();
		switch (tab) {
			case HOME -> drawHome(graphics, font, x, y, w, h, snap, profile);
			case SKILLS -> drawSkills(graphics, font, x, y, w, h, profile);
			case COMBAT -> drawCombat(graphics, font, x, y, w, h, profile);
			case MINING -> drawMining(graphics, font, x, y, w, h, profile);
			case FARMING -> drawFarming(graphics, font, x, y, w, h, profile);
			case PETS -> drawPets(graphics, font, mouseX, mouseY, x, y, w, h, profile);
			case ITEMS -> drawItems(graphics, font, mouseX, mouseY, x, y, w, h, profile);
		}
	}

	private void drawHome(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		float h,
		ProfileViewer.Snapshot snap,
		ProfileViewer.Profile profile
	) {
		float stageW = 150;
		float stageH = h - 16;
		GuiDraw.panel(graphics, x + 8, y + 8, stageW, stageH, 8, Theme.CARD, Theme.LINE);
		boolean self = minecraft.player != null && snap.uuid() != null && minecraft.player.getUUID().equals(snap.uuid());
		if (self) {
			PlayerPreview.Drawn drawn = PlayerPreview.draw(
				graphics,
				x + 12,
				y + 14,
				stageW - 8,
				stageH - 28,
				0f,
				0f,
				new PlayerPreview.View(viewScale, viewCx, viewCy, viewLift)
			);
			if (drawn != null) {
				NametagRenderer.drawVanilla(graphics, font, drawn.nameX(), drawn.nameY(), Component.literal(snap.name()));
			}
		} else {
			String name = snap.name().isBlank() ? "?" : snap.name();
			GuiDraw.title(graphics, font, name.substring(0, 1).toUpperCase(Locale.ROOT), x + 8 + (stageW - 12) * 0.5f - 6, y + stageH * 0.42f, Theme.ACCENT);
			GuiDraw.menu(graphics, font, name, x + 16, y + stageH * 0.42f + 22, Theme.TEXT);
		}

		float rx = x + stageW + 18;
		float ry = y + 12;
		float rw = w - stageW - 28;
		stat(graphics, font, rx, ry, rw, "Skyblock", String.valueOf(profile.skyblockLevel()), profile.skyblockProgress());
		ry += 28;
		stat(graphics, font, rx, ry, rw, "Skill avg", trim(profile.skillAverage()), 1f);
		ry += 28;
		stat(graphics, font, rx, ry, rw, "Purse", compact(profile.purse()), 1f);
		ry += 28;
		stat(graphics, font, rx, ry, rw, "Bank", compact(profile.bank()), 1f);
		ry += 28;
		stat(graphics, font, rx, ry, rw, "Fairy souls", String.valueOf(profile.fairySouls()), 1f);
		ry += 28;
		stat(graphics, font, rx, ry, rw, "Secrets", compact(profile.secrets()), 1f);
		ry += 28;
		GuiDraw.small(graphics, font, prettyMode(profile.gameMode()), rx, ry + 4, Theme.MUTED);
	}

	private void drawSkills(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, ProfileViewer.Profile profile) {
		float left = x + 10;
		float top = y + 10;
		float col = (w - 28) * 0.5f;
		List<ProfileViewer.Skill> skills = profile.skills();
		for (int i = 0; i < skills.size(); i++) {
			float sx = left + (i % 2) * (col + 8);
			float sy = top + (i / 2) * 28f;
			if (sy + 24 > y + h - 8) {
				break;
			}
			ProfileViewer.Skill skill = skills.get(i);
			stat(graphics, font, sx, sy, col, skill.name(), skill.level() + " / " + skill.cap(), skill.progress());
		}
	}

	private void drawCombat(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, ProfileViewer.Profile profile) {
		float left = x + 10;
		float top = y + 10;
		float col = (w - 28) * 0.5f;
		ProfileViewer.Dungeon dungeon = profile.dungeons();
		stat(graphics, font, left, top, col, "Catacombs", String.valueOf(dungeon.cata()), dungeon.progress());
		stat(graphics, font, left + col + 8, top, col, "Secrets", compact(dungeon.secrets()), 1f);
		float y0 = top + 32;
		GuiDraw.small(graphics, font, "Classes", left, y0, Theme.ACCENT);
		y0 += 12;
		List<ProfileViewer.Skill> classes = dungeon.classes();
		for (int i = 0; i < classes.size(); i++) {
			ProfileViewer.Skill skill = classes.get(i);
			stat(graphics, font, left + (i % 2) * (col + 8), y0 + (i / 2) * 26f, col, skill.name(), String.valueOf(skill.level()), skill.progress());
		}
		float slayerY = y0 + 72;
		GuiDraw.small(graphics, font, "Slayers", left, slayerY, Theme.ACCENT);
		slayerY += 12;
		List<ProfileViewer.Slayer> slayers = profile.slayers();
		for (int i = 0; i < slayers.size(); i++) {
			ProfileViewer.Slayer slayer = slayers.get(i);
			float sx = left + (i % 3) * ((w - 28) / 3f);
			float sy = slayerY + (i / 3) * 22f;
			if (sy + 18 > y + h - 6) {
				break;
			}
			GuiDraw.small(graphics, font, slayer.name(), sx, sy, Theme.MUTED);
			GuiDraw.menu(graphics, font, slayer.level() + "  " + compact(slayer.xp()), sx, sy + 8, Theme.TEXT);
		}
	}

	private void drawMining(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, ProfileViewer.Profile profile) {
		float left = x + 10;
		float top = y + 12;
		float col = (w - 28) * 0.5f;
		ProfileViewer.Mining mining = profile.mining();
		stat(graphics, font, left, top, col, "HOTM", String.valueOf(mining.hotm()), 1f);
		stat(graphics, font, left + col + 8, top, col, "Mithril", compact(mining.mithril()), 1f);
		stat(graphics, font, left, top + 32, col, "Gemstone", compact(mining.gemstone()), 1f);
		stat(graphics, font, left + col + 8, top + 32, col, "Glacite", compact(mining.glacite()), 1f);
		GuiDraw.small(graphics, font, "Powder is current plus spent.", left, top + 68, Theme.MUTED);
	}

	private void drawFarming(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, ProfileViewer.Profile profile) {
		float left = x + 10;
		float top = y + 12;
		float col = (w - 28) * 0.5f;
		ProfileViewer.Skill farming = skill(profile, "Farming");
		stat(graphics, font, left, top, col, "Farming", farming.level() + " / " + farming.cap(), farming.progress());
		stat(graphics, font, left + col + 8, top, col, "Garden", String.valueOf(profile.farming().garden()), 1f);
		stat(graphics, font, left, top + 32, col, "Unique visitors", String.valueOf(profile.farming().visitors()), 1f);
		GuiDraw.small(graphics, font, "Garden data is only there if the API sent it.", left, top + 68, Theme.MUTED);
	}

	private void drawPets(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		float h,
		ProfileViewer.Profile profile
	) {
		List<ProfileViewer.Pet> pets = profile.pets();
		if (pets.isEmpty()) {
			GuiDraw.menu(graphics, font, "No pets on this profile.", x + 12, y + 14, Theme.MUTED);
			return;
		}
		drawRows(graphics, font, mouseX, mouseY, x, y, w, h, pets.size(), (index, rx, ry, rw) -> {
			ProfileViewer.Pet pet = pets.get(index);
			String left = (pet.active() ? "● " : "") + pet.name();
			String right = pet.tier() + "  " + pet.level();
			GuiDraw.menu(graphics, font, clip(font, left, rw - 70), rx, GuiDraw.middle(ry, ROW), pet.active() ? Theme.ACCENT : Theme.TEXT);
			GuiDraw.small(graphics, font, right, rx + rw - GuiDraw.smallWidth(font, right), GuiDraw.middle(ry, ROW), Theme.MUTED);
		});
	}

	private void drawItems(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		float h,
		ProfileViewer.Profile profile
	) {
		List<String> rows = new ArrayList<>();
		if (!profile.armor().isEmpty()) {
			rows.add("ARMOR");
			for (ProfileViewer.SlotItem item : profile.armor()) {
				rows.add(itemLine(item));
			}
		}
		if (!profile.inventory().isEmpty()) {
			rows.add("INVENTORY");
			for (ProfileViewer.SlotItem item : profile.inventory()) {
				rows.add(itemLine(item));
			}
		}
		if (!profile.collections().isEmpty()) {
			rows.add("TOP COLLECTIONS");
			int n = Math.min(16, profile.collections().size());
			for (int i = 0; i < n; i++) {
				ProfileViewer.Collection collection = profile.collections().get(i);
				rows.add(collection.name() + "  " + compact(collection.amount()));
			}
		}
		if (rows.isEmpty()) {
			GuiDraw.menu(graphics, font, "Inventory is hidden or empty.", x + 12, y + 14, Theme.MUTED);
			return;
		}
		drawRows(graphics, font, mouseX, mouseY, x, y, w, h, rows.size(), (index, rx, ry, rw) -> {
			String line = rows.get(index);
			boolean head = line.equals("ARMOR") || line.equals("INVENTORY") || line.equals("TOP COLLECTIONS");
			GuiDraw.small(graphics, font, clip(font, line, rw), rx, GuiDraw.middle(ry, ROW), head ? Theme.ACCENT : Theme.TEXT);
		});
	}

	private void drawRows(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		float h,
		int count,
		RowDraw draw
	) {
		float listX = x + 8;
		float listY = y + 8;
		float listW = w - 16;
		float listH = h - 16;
		float max = Math.max(0f, count * ROW - listH);
		listScroll = Mth.clamp(listScroll, 0f, max);
		boolean clip = GuiDraw.scissor(graphics, listX, listY, listW, listH);
		int first = (int) (listScroll / ROW);
		int last = Math.min(count - 1, first + (int) (listH / ROW) + 1);
		for (int i = first; i <= last; i++) {
			float ry = listY + i * ROW - listScroll;
			if (GuiDraw.hovered(mouseX, mouseY, listX, ry, listW, ROW)
				&& GuiDraw.hovered(mouseX, mouseY, listX, listY, listW, listH)) {
				GuiDraw.rounded(graphics, listX, ry, listW, ROW, 4, 0x10FFFFFF);
			}
			draw.draw(i, listX + 4, ry, listW - 8);
		}
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private void stat(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, String value, float progress) {
		GuiDraw.small(graphics, font, label, x, y, Theme.MUTED);
		GuiDraw.menu(graphics, font, value, x + w - GuiDraw.menuWidth(font, value), y, Theme.TEXT);
		float barY = y + 12;
		GuiDraw.rounded(graphics, x, barY, w, 4, 2, Theme.TRACK);
		float fill = Math.max(0f, Math.min(1f, progress));
		if (fill > 0.01f) {
			GuiDraw.rounded(graphics, x, barY, Math.max(4f, w * fill), 4, 2, Theme.ACCENT);
		}
	}

	private void chip(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, Runnable click) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, 16);
		GuiDraw.panel(graphics, x, y, w, 16, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.menu(graphics, font, label, x + (w - GuiDraw.menuWidth(font, label)) * 0.5f, GuiDraw.middle(y, 16), Theme.TEXT);
		hits.add(new Hit(x, y, w, 16, click));
	}

	private void layout() {
		windowW = Math.min(MENU_W, Math.max(360, width - 16));
		windowH = Math.min(MENU_H, Math.max(220, height - 16));
		if (!placed) {
			windowX = (width - windowW) * 0.5f;
			windowY = (height - windowH) * 0.5f;
			placed = true;
		}
		windowX = Mth.clamp(windowX, 4, Math.max(4, width - windowW - 4));
		windowY = Mth.clamp(windowY, 4, Math.max(4, height - windowH - 4));
	}

	private void tickAnim() {
		long now = System.nanoTime();
		dt = Mth.clamp((now - lastNs) / 1_000_000_000f, 0.008f, 0.05f);
		lastNs = now;
		if (StrayConfig.get().loadoutsOpenAnim) {
			if (Math.abs(1f - appear) < 0.003f) {
				appear = 1f;
			} else {
				appear += (1f - appear) * (1f - (float) Math.exp(-14f * dt));
			}
		} else {
			appear = 1f;
		}
	}

	private void load() {
		listScroll = 0f;
		if (query.isBlank()) {
			ProfileViewer.openSelf();
		} else {
			ProfileViewer.load(query);
		}
	}

	private float localX(double mx) {
		return (float) ((mx - viewCx) / viewScale + viewCx);
	}

	private float localY(double my) {
		return (float) ((my - viewCy - viewLift) / viewScale + viewCy);
	}

	private static ProfileViewer.Skill skill(ProfileViewer.Profile profile, String name) {
		for (ProfileViewer.Skill skill : profile.skills()) {
			if (name.equals(skill.name())) {
				return skill;
			}
		}
		return new ProfileViewer.Skill(name, 0, 60, 0, 0f);
	}

	private static String itemLine(ProfileViewer.SlotItem item) {
		if (item.count() > 1) {
			return item.count() + "×  " + item.name();
		}
		return item.name();
	}

	private static String prettyMode(String mode) {
		return switch (mode == null ? "" : mode.toLowerCase(Locale.ROOT)) {
			case "ironman" -> "Ironman";
			case "bingo" -> "Bingo";
			case "island" -> "Stranded";
			default -> "Normal profile";
		};
	}

	private static String compact(double value) {
		double n = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (n >= 1_000_000_000d) {
			return sign + trim(n / 1_000_000_000d) + "b";
		}
		if (n >= 1_000_000d) {
			return sign + trim(n / 1_000_000d) + "m";
		}
		if (n >= 1_000d) {
			return sign + trim(n / 1_000d) + "k";
		}
		if (n == Math.rint(n)) {
			return sign + String.valueOf((long) n);
		}
		return sign + trim(n);
	}

	private static String trim(double value) {
		return String.format(Locale.ROOT, value >= 10 ? "%.0f" : "%.1f", value);
	}

	private static String clip(Font font, String text, float max) {
		if (GuiDraw.smallWidth(font, text) <= max) {
			return text;
		}
		String trimmed = text;
		while (trimmed.length() > 1 && GuiDraw.smallWidth(font, trimmed + "..") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double lx = localX(event.x());
		double ly = localY(event.y());
		queryFocused = false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(lx, ly)) {
				hit.click.run();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			listScroll = Math.max(0f, listScroll - (float) scrollY * 18f);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (queryFocused && event.key() == InputConstants.KEY_BACKSPACE) {
			if (!query.isEmpty()) {
				query = query.substring(0, query.length() - 1);
			}
			return true;
		}
		if (queryFocused && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			queryFocused = false;
			load();
			return true;
		}
		if (event.isEscape() || (minecraft != null && minecraft.options.keyInventory.matches(event)) || StrayClient.profileKey(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (queryFocused && event.isAllowedChatCharacter()) {
			if (query.length() < 16) {
				query += event.codepointAsString();
			}
			return true;
		}
		return super.charTyped(event);
	}

	private record Hit(float x, float y, float w, float h, Runnable click) {
		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}

	@FunctionalInterface
	private interface RowDraw {
		void draw(int index, float x, float y, float w);
	}
}
