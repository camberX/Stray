package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.StrayClient;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Stray Skyblock profile viewer. Tabs and chrome are ours; the numbers come
 * from the public Hypixel profile host.
 */
public class ProfileViewerScreen extends Screen {
	private static final float MENU_W = 700;
	private static final float MENU_H = 380;
	private static final float RAIL = 84;
	private static final float ROW = 16;
	private static final float CHIP_H = 18;

	private enum Tab {
		HOME("Home", MenuFont.PERSON),
		ITEMS("Items", MenuFont.BAG),
		SKILLS("Skills", MenuFont.BARS),
		COMBAT("Combat", MenuFont.SWORD),
		MINING("Mining", MenuFont.DIAMOND),
		FARMING("Farm", MenuFont.GRAIN),
		PETS("Pets", MenuFont.CAT);

		final String label;
		final String icon;

		Tab(String label, String icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	private enum ItemPane {
		INV, ENDER, BAG
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
	private ItemStack hoverStack = ItemStack.EMPTY;
	private ItemPane itemPane = ItemPane.INV;
	private int itemPage;
	private float slot = 22;

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
		hoverStack = ItemStack.EMPTY;
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

		if (hoverStack != null && !hoverStack.isEmpty()) {
			graphics.setTooltipForNextFrame(font, hoverStack, mouseX, mouseY);
		} else if (!tooltip.isBlank()) {
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
				itemPage = 0;
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
			int color = on ? Theme.ACCENT : Theme.TEXT;
			GuiDraw.icon(graphics, font, value.icon, x + 6, GuiDraw.middle(y, 22) - 1, color);
			GuiDraw.menu(graphics, font, value.label, x + 22, GuiDraw.middle(y, 22), color);
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
			case HOME -> drawHome(graphics, font, mouseX, mouseY, x, y, w, h, snap, profile);
			case ITEMS -> drawItems(graphics, font, mouseX, mouseY, x, y, w, h, profile);
			case SKILLS -> drawSkills(graphics, font, mouseX, mouseY, x, y, w, h, profile);
			case COMBAT -> drawCombat(graphics, font, mouseX, mouseY, x, y, w, h, profile);
			case MINING -> drawMining(graphics, font, mouseX, mouseY, x, y, w, h, profile);
			case FARMING -> drawFarming(graphics, font, mouseX, mouseY, x, y, w, h, profile);
			case PETS -> drawPets(graphics, font, mouseX, mouseY, x, y, w, h, profile);
		}
	}

	private void drawHome(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		float h,
		ProfileViewer.Snapshot snap,
		ProfileViewer.Profile profile
	) {
		float pad = 10;
		float innerW = w - pad * 2;
		float innerH = h - pad * 2;
		float playerW = Mth.clamp(innerW * 0.28f, 140f, 220f);
		float sideW = (innerW - playerW - 16f) * 0.5f;
		if (sideW < 168f) {
			playerW = Math.max(120f, innerW * 0.26f);
			sideW = (innerW - playerW - 16f) * 0.5f;
		}
		float leftX = x + pad;
		float playerX = leftX + sideW + 8f;
		float rightX = playerX + playerW + 8f;
		float top = y + pad;

		drawInfoColumn(graphics, font, mouseX, mouseY, leftX, top, sideW, innerH, profile);
		drawPlayerColumn(graphics, font, playerX, top, playerW, innerH, snap, profile);
		drawChipColumn(graphics, font, mouseX, mouseY, rightX, top, sideW, innerH, profile);
	}

	private void drawInfoColumn(
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
		sectionTitle(graphics, font, x, y, w, new ItemStack(Items.WRITABLE_BOOK), "Info");
		float ry = y + 16;
		float row = 18;
		String sb = skyblockLevel(profile);
		infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.EXPERIENCE_BOTTLE), "Skyblock", sb,
			"Skyblock level " + sb + "  (" + Math.round(profile.skyblockProgress() * 100f) + "% to next)");
		ry += row;
		infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.GOLD_INGOT), "Purse", compact(profile.purse()),
			"Purse  " + prettyCoins(profile.purse()));
		ry += row;
		infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.GOLD_BLOCK), "Bank", compact(profile.bank()),
			"Bank  " + prettyCoins(profile.bank()));
		ry += row;
		infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.EMERALD), "Networth", compact(profile.purse() + profile.bank()),
			"Purse + bank. Item prices are not included.");
		ry += row;
		infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.COOKIE), "Cookie", profile.cookie() ? "Active" : "Inactive",
			profile.cookie() ? "Cookie buff is active." : "Cookie buff is inactive.");
		ry += row;
		infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.DIAMOND), "Skill avg", trim(profile.skillAverage()),
			"Average of the core skills.");
		ry += row;
		infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.SOUL_LANTERN), "Fairy souls", String.valueOf(profile.fairySouls()),
			profile.fairySouls() + " fairy souls collected");
		ry += row;
		infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.CHEST), "Secrets", compact(profile.secrets()),
			prettyNumber(profile.secrets()) + " dungeon secrets");
		ry += row;
		if (profile.firstJoin() > 0L) {
			infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.CLOCK), "First join", date(profile.firstJoin()),
				datetime(profile.firstJoin()));
			ry += row;
		}
		if (profile.kills() > 0L || profile.deaths() > 0L) {
			infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.IRON_SWORD), "K/D", kd(profile),
				prettyNumber(profile.kills()) + " kills / " + prettyNumber(profile.deaths()) + " deaths");
			ry += row;
		}
		if (ry > y + h) {
			return;
		}
	}

	private void drawPlayerColumn(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		float h,
		ProfileViewer.Snapshot snap,
		ProfileViewer.Profile profile
	) {
		float stageH = Math.min(h - 22, w * 1.15f);
		GuiDraw.panel(graphics, x, y, w, stageH, 8, Theme.CARD, Theme.LINE);
		boolean self = minecraft.player != null && snap.uuid() != null && minecraft.player.getUUID().equals(snap.uuid());
		if (self) {
			PlayerPreview.Drawn drawn = PlayerPreview.drawEquipped(
				graphics,
				x + 4,
				y + 8,
				w - 8,
				stageH - 28,
				0f,
				0f,
				new PlayerPreview.View(viewScale, viewCx, viewCy, viewLift),
				gearOf(profile.armor())
			);
			if (drawn != null) {
				NametagRenderer.drawVanilla(graphics, font, drawn.nameX(), drawn.nameY(), Component.literal(snap.name()));
			}
		} else {
			String name = snap.name().isBlank() ? "?" : snap.name();
			GuiDraw.title(graphics, font, name.substring(0, 1).toUpperCase(Locale.ROOT), x + (w - 12) * 0.5f - 4, y + stageH * 0.38f, Theme.ACCENT);
			GuiDraw.menu(graphics, font, clip(font, name, w - 16), x + 8, y + stageH * 0.38f + 22, Theme.TEXT);
		}
		GuiDraw.small(graphics, font, prettyMode(profile.gameMode()), x + 8, y + stageH - 12, Theme.MUTED);
		String badge = "[" + profile.skyblockLevel() + "]  " + (snap.name().isBlank() ? "Player" : snap.name());
		GuiDraw.small(graphics, font, clip(font, badge, w - 12), x + 8, y + stageH + 6, Theme.ACCENT);
	}

	private void drawChipColumn(
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
		float cy = y;
		sectionTitle(graphics, font, x, cy, w, new ItemStack(Items.DIAMOND_SWORD), "Skills");
		cy += 16;
		cy = skillChips(graphics, font, mouseX, mouseY, x, cy, w, y + h, profile.skills());
		cy += 10;
		if (cy + 28 > y + h) {
			return;
		}
		sectionTitle(graphics, font, x, cy, w, new ItemStack(Items.ROTTEN_FLESH), "Slayers");
		cy += 16;
		slayerChips(graphics, font, mouseX, mouseY, x, cy, w, y + h, profile.slayers());
	}

	private void drawSkills(
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
		float left = x + 12;
		float top = y + 12;
		float col = (w - 32) * 0.5f;
		List<ProfileViewer.Skill> skills = profile.skills();
		for (int i = 0; i < skills.size(); i++) {
			float sx = left + (i % 2) * (col + 8);
			float sy = top + (i / 2) * 34f;
			if (sy + 30 > y + h - 8) {
				break;
			}
			ProfileViewer.Skill skill = skills.get(i);
			skillBar(graphics, font, mouseX, mouseY, sx, sy, col, skill);
		}
	}

	private void drawCombat(
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
		float left = x + 12;
		float top = y + 10;
		float col = (w - 32) * 0.5f;
		ProfileViewer.Dungeon dungeon = profile.dungeons();
		infoRow(graphics, font, mouseX, mouseY, left, top, col, new ItemStack(Items.WITHER_SKELETON_SKULL), "Catacombs",
			String.valueOf(dungeon.cata()), "Catacombs " + dungeon.cata());
		infoRow(graphics, font, mouseX, mouseY, left + col + 8, top, col, new ItemStack(Items.CHEST), "Secrets",
			compact(dungeon.secrets()), prettyNumber(dungeon.secrets()) + " secrets");
		float y0 = top + 26;
		sectionTitle(graphics, font, left, y0, w - 24, new ItemStack(Items.IRON_CHESTPLATE), "Classes");
		y0 += 16;
		y0 = classChips(graphics, font, mouseX, mouseY, left, y0, w - 24, y + h, dungeon.classes());
		y0 += 12;
		if (y0 + 24 > y + h) {
			return;
		}
		sectionTitle(graphics, font, left, y0, w - 24, new ItemStack(Items.ROTTEN_FLESH), "Slayers");
		y0 += 16;
		slayerChips(graphics, font, mouseX, mouseY, left, y0, w - 24, y + h, profile.slayers());
	}

	private void drawMining(
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
		float left = x + 12;
		float top = y + 12;
		float col = (w - 32) * 0.5f;
		ProfileViewer.Mining mining = profile.mining();
		infoRow(graphics, font, mouseX, mouseY, left, top, col, new ItemStack(Items.DIAMOND_PICKAXE), "HOTM",
			String.valueOf(mining.hotm()), "Heart of the Mountain " + mining.hotm());
		infoRow(graphics, font, mouseX, mouseY, left + col + 8, top, col, new ItemStack(Items.IRON_INGOT), "Mithril",
			compact(mining.mithril()), prettyNumber(mining.mithril()) + " mithril powder");
		infoRow(graphics, font, mouseX, mouseY, left, top + 22, col, new ItemStack(Items.AMETHYST_SHARD), "Gemstone",
			compact(mining.gemstone()), prettyNumber(mining.gemstone()) + " gemstone powder");
		infoRow(graphics, font, mouseX, mouseY, left + col + 8, top + 22, col, new ItemStack(Items.BLUE_ICE), "Glacite",
			compact(mining.glacite()), prettyNumber(mining.glacite()) + " glacite powder");
		GuiDraw.small(graphics, font, "Powder is current plus spent.", left, top + 52, Theme.MUTED);
	}

	private void drawFarming(
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
		float left = x + 12;
		float top = y + 12;
		float col = (w - 32) * 0.5f;
		ProfileViewer.Skill farming = skill(profile, "Farming");
		infoRow(graphics, font, mouseX, mouseY, left, top, col, new ItemStack(Items.WHEAT), "Farming",
			farming.level() + " / " + farming.cap(), "Farming " + farming.level() + " / " + farming.cap());
		infoRow(graphics, font, mouseX, mouseY, left + col + 8, top, col, new ItemStack(Items.OAK_SAPLING), "Garden",
			String.valueOf(profile.farming().garden()), "Garden level " + profile.farming().garden());
		infoRow(graphics, font, mouseX, mouseY, left, top + 22, col, new ItemStack(Items.PLAYER_HEAD), "Visitors",
			String.valueOf(profile.farming().visitors()), profile.farming().visitors() + " unique visitors");
		GuiDraw.small(graphics, font, "Garden data is only there if the API sent it.", left, top + 52, Theme.MUTED);
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
		if (profile.inventory().vacant() && profile.ender().isEmpty() && profile.backpacks().isEmpty()) {
			GuiDraw.menu(graphics, font, "Inventory is hidden or empty.", x + 12, y + 14, Theme.MUTED);
			return;
		}
		if (itemPane == ItemPane.ENDER && profile.ender().isEmpty()) {
			itemPane = ItemPane.INV;
		}
		if (itemPane == ItemPane.BAG && profile.backpacks().isEmpty()) {
			itemPane = ItemPane.INV;
		}
		float chipY = y + 10;
		float chipX = x + 12;
		chipX = paneChip(graphics, font, mouseX, mouseY, chipX, chipY, "Inventory", itemPane == ItemPane.INV, () -> {
			itemPane = ItemPane.INV;
			itemPage = 0;
		});
		if (!profile.ender().isEmpty()) {
			chipX = paneChip(graphics, font, mouseX, mouseY, chipX, chipY, "Ender Chest", itemPane == ItemPane.ENDER, () -> {
				itemPane = ItemPane.ENDER;
				itemPage = 0;
			});
		}
		if (!profile.backpacks().isEmpty()) {
			paneChip(graphics, font, mouseX, mouseY, chipX, chipY, "Backpacks", itemPane == ItemPane.BAG, () -> {
				itemPane = ItemPane.BAG;
				itemPage = 0;
			});
		}

		List<ProfileViewer.Bag> pages = itemPages(profile);
		float pageY = y + 28;
		boolean paged = pages.size() > 1;
		if (paged) {
			float px = x + 12;
			for (int i = 0; i < pages.size(); i++) {
				String label = pages.get(i).name().isBlank() ? String.valueOf(i + 1) : shortPage(pages.get(i).name(), i);
				float pw = GuiDraw.smallWidth(font, label) + 10;
				boolean on = i == itemPage;
				boolean over = GuiDraw.hovered(mouseX, mouseY, px, pageY, pw, 13);
				GuiDraw.panel(graphics, px, pageY, pw, 13, 4, on || over ? Theme.CARD_HOVER : Theme.CARD, on ? Theme.ACCENT : Theme.LINE);
				GuiDraw.small(graphics, font, label, px + 5, GuiDraw.middle(pageY, 13), on ? Theme.ACCENT : Theme.TEXT);
				int page = i;
				hits.add(new Hit(px, pageY, pw, 13, () -> itemPage = page));
				px += pw + 3;
			}
		}

		ProfileViewer.Bag bag = pages.isEmpty() ? ProfileViewer.Bag.empty("Empty", 9, 0) : pages.get(Math.max(0, Math.min(itemPage, pages.size() - 1)));
		boolean playerInv = itemPane == ItemPane.INV;
		boolean armor = playerInv && !profile.armor().vacant();
		int cols = playerInv ? 9 : Math.max(1, bag.columns());
		int rows = playerInv && bag.size() >= 36 ? 4 : Math.max(1, bag.rows());
		float top = paged ? y + 46 : y + 32;
		float availW = w - 24;
		float availH = y + h - top - 12;
		float armorGap = armor ? 8f : 0f;
		float hotbarGap = playerInv && bag.size() >= 36 ? 8f : 0f;
		float byWidth = (availW - armorGap) / (cols + (armor ? 1 : 0));
		float byHeight = (availH - hotbarGap) / rows;
		slot = Mth.clamp(Math.min(byWidth, byHeight), 20f, 40f);

		float gridW = (armor ? slot + armorGap : 0f) + cols * slot;
		float gridH = rows * slot + hotbarGap;
		float gridX = x + (w - gridW) * 0.5f;
		float gridY = top + Math.max(0f, (availH - gridH) * 0.35f);
		if (playerInv && armor) {
			drawArmor(graphics, font, mouseX, mouseY, gridX, gridY + (gridH - 4 * slot) * 0.5f, profile.armor());
			gridX += slot + armorGap;
		}
		if (playerInv && bag.size() >= 36) {
			drawGrid(graphics, font, mouseX, mouseY, gridX, gridY, 9, 3, bag, 9);
			drawGrid(graphics, font, mouseX, mouseY, gridX, gridY + 3 * slot + hotbarGap, 9, 1, bag, 0);
		} else if (!bag.vacant() || bag.size() > 0) {
			drawGrid(graphics, font, mouseX, mouseY, gridX, gridY, cols, rows, bag, 0);
		} else {
			GuiDraw.small(graphics, font, "Nothing in this bag.", gridX, gridY + 6, Theme.MUTED);
		}
	}

	private List<ProfileViewer.Bag> itemPages(ProfileViewer.Profile profile) {
		return switch (itemPane) {
			case INV -> List.of(profile.inventory());
			case ENDER -> profile.ender();
			case BAG -> profile.backpacks();
		};
	}

	private float paneChip(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		String label,
		boolean on,
		Runnable click
	) {
		float w = GuiDraw.smallWidth(font, label) + 12;
		boolean over = GuiDraw.hovered(mouseX, mouseY, x, y, w, 14);
		GuiDraw.panel(graphics, x, y, w, 14, 5, on || over ? Theme.CARD_HOVER : Theme.CARD, on ? Theme.ACCENT : Theme.LINE);
		GuiDraw.small(graphics, font, label, x + 6, GuiDraw.middle(y, 14), on ? Theme.ACCENT : Theme.TEXT);
		hits.add(new Hit(x, y, w, 14, click));
		return x + w + 4;
	}

	private void drawArmor(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		ProfileViewer.Bag armor
	) {
		int[] order = {3, 2, 1, 0};
		for (int i = 0; i < 4; i++) {
			int index = i < armor.size() ? order[i] : i;
			drawSlot(graphics, font, mouseX, mouseY, x, y + i * slot, armor.at(index));
		}
	}

	private void drawGrid(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		int cols,
		int rows,
		ProfileViewer.Bag bag,
		int start
	) {
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				int index = start + row * cols + col;
				drawSlot(graphics, font, mouseX, mouseY, x + col * slot, y + row * slot, bag.at(index));
			}
		}
	}

	private void drawSlot(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		ProfileViewer.SlotItem item
	) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, slot, slot);
		GuiDraw.well(graphics, x, y, slot, hover ? Theme.CARD_HOVER : Theme.TRACK, hover ? Theme.ACCENT : Theme.LINE);
		if (item == null || item.empty()) {
			return;
		}
		ItemStack stack = stackOf(item);
		if (stack.isEmpty()) {
			return;
		}
		float pad = Math.max(1f, slot * 0.08f);
		paintItem(graphics, font, stack, x + pad, y + pad, slot - pad * 2f, true);
		if (hover) {
			hoverStack = stack;
		}
	}

	private static ItemStack stackOf(ProfileViewer.SlotItem item) {
		if (item == null || item.empty()) {
			return ItemStack.EMPTY;
		}
		String raw = item.id() == null ? "" : item.id().trim();
		ItemIds.Preview preview;
		if (raw.isBlank()) {
			preview = ItemIds.resolve(item.name());
		} else if (raw.contains(":")) {
			preview = ItemIds.resolve(raw);
		} else {
			preview = ItemIds.resolve("sb:" + raw);
		}
		ItemStack stack = preview.stack();
		if (stack == null || stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		stack = stack.copy();
		stack.setCount(Math.max(1, item.count()));
		return stack;
	}

	private PlayerPreview.Gear gearOf(ProfileViewer.Bag armor) {
		if (armor == null || armor.vacant()) {
			return PlayerPreview.Gear.none();
		}
		return new PlayerPreview.Gear(
			stackOf(armor.at(3)),
			stackOf(armor.at(2)),
			stackOf(armor.at(1)),
			stackOf(armor.at(0))
		);
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

	private void sectionTitle(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, ItemStack icon, String title) {
		paintItem(graphics, font, icon, x, y - 1, 12, false);
		GuiDraw.small(graphics, font, title, x + 16, y + 2, Theme.ACCENT);
		float lineX = x + 20 + GuiDraw.smallWidth(font, title);
		if (lineX + 12 < x + w) {
			GuiDraw.rounded(graphics, lineX, y + 6, x + w - lineX, 1, 0.5f, Theme.LINE);
		}
	}

	private void infoRow(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		ItemStack icon,
		String label,
		String value,
		String tip
	) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, 17);
		if (hover) {
			GuiDraw.rounded(graphics, x - 2, y - 1, w + 4, 17, 4, 0x10FFFFFF);
			tooltip = tip == null || tip.isBlank() ? label + "  " + value : tip;
		}
		paintItem(graphics, font, icon, x, y, 14, false);
		GuiDraw.small(graphics, font, label, x + 18, GuiDraw.middle(y, 16), Theme.MUTED);
		GuiDraw.menu(graphics, font, value, x + w - GuiDraw.menuWidth(font, value), GuiDraw.middle(y, 16), Theme.TEXT);
	}

	private void skillBar(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		ProfileViewer.Skill skill
	) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, 28);
		if (hover) {
			tooltip = skill.name() + "  " + skill.level() + " / " + skill.cap() + "\n" + prettyNumber((long) skill.xp()) + " xp";
		}
		paintItem(graphics, font, skillIcon(skill.name()), x, y, 16, false);
		GuiDraw.small(graphics, font, skill.name(), x + 20, y, Theme.MUTED);
		String value = skill.level() + " / " + skill.cap();
		GuiDraw.menu(graphics, font, value, x + w - GuiDraw.menuWidth(font, value), y, Theme.TEXT);
		GuiDraw.rounded(graphics, x + 20, y + 14, w - 20, 5, 2, Theme.TRACK);
		float fill = Math.max(0f, Math.min(1f, skill.progress()));
		if (fill > 0.01f) {
			GuiDraw.rounded(graphics, x + 20, y + 14, Math.max(4f, (w - 20) * fill), 5, 2, Theme.ACCENT);
		}
	}

	private float skillChips(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		float maxY,
		List<ProfileViewer.Skill> skills
	) {
		float cx = x;
		float cy = y;
		for (ProfileViewer.Skill skill : skills) {
			String level = String.valueOf(skill.level());
			float cw = 12 + 4 + GuiDraw.smallWidth(font, level) + 8;
			if (cx + cw > x + w && cx > x) {
				cx = x;
				cy += CHIP_H + 4;
			}
			if (cy + CHIP_H > maxY) {
				return cy;
			}
			boolean hover = GuiDraw.hovered(mouseX, mouseY, cx, cy, cw, CHIP_H);
			GuiDraw.panel(graphics, cx, cy, cw, CHIP_H, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
			paintItem(graphics, font, skillIcon(skill.name()), cx + 3, cy + 3, 12, false);
			GuiDraw.small(graphics, font, level, cx + 17, GuiDraw.middle(cy, CHIP_H), Theme.TEXT);
			if (hover) {
				tooltip = skill.name() + "  " + skill.level() + " / " + skill.cap();
			}
			cx += cw + 5;
		}
		return cy + CHIP_H;
	}

	private float slayerChips(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		float maxY,
		List<ProfileViewer.Slayer> slayers
	) {
		float cx = x;
		float cy = y;
		for (ProfileViewer.Slayer slayer : slayers) {
			String level = String.valueOf(slayer.level());
			float cw = 12 + 4 + GuiDraw.smallWidth(font, level) + 8;
			if (cx + cw > x + w && cx > x) {
				cx = x;
				cy += CHIP_H + 4;
			}
			if (cy + CHIP_H > maxY) {
				return cy;
			}
			boolean hover = GuiDraw.hovered(mouseX, mouseY, cx, cy, cw, CHIP_H);
			GuiDraw.panel(graphics, cx, cy, cw, CHIP_H, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
			paintItem(graphics, font, slayerIcon(slayer.name()), cx + 3, cy + 3, 12, false);
			GuiDraw.small(graphics, font, level, cx + 17, GuiDraw.middle(cy, CHIP_H), Theme.TEXT);
			if (hover) {
				tooltip = slayer.name() + "  " + slayer.level() + "  " + compact(slayer.xp()) + " xp";
			}
			cx += cw + 5;
		}
		return cy + CHIP_H;
	}

	private float classChips(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		float maxY,
		List<ProfileViewer.Skill> classes
	) {
		float cx = x;
		float cy = y;
		for (ProfileViewer.Skill skill : classes) {
			String level = String.valueOf(skill.level());
			float cw = 12 + 4 + GuiDraw.smallWidth(font, level) + 8;
			if (cx + cw > x + w && cx > x) {
				cx = x;
				cy += CHIP_H + 4;
			}
			if (cy + CHIP_H > maxY) {
				return cy;
			}
			boolean hover = GuiDraw.hovered(mouseX, mouseY, cx, cy, cw, CHIP_H);
			GuiDraw.panel(graphics, cx, cy, cw, CHIP_H, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
			paintItem(graphics, font, classIcon(skill.name()), cx + 3, cy + 3, 12, false);
			GuiDraw.small(graphics, font, level, cx + 17, GuiDraw.middle(cy, CHIP_H), Theme.TEXT);
			if (hover) {
				tooltip = skill.name() + "  " + skill.level();
			}
			cx += cw + 5;
		}
		return cy + CHIP_H;
	}

	private void paintItem(GuiGraphicsExtractor graphics, Font font, ItemStack stack, float x, float y, float size, boolean decorations) {
		if (stack == null || stack.isEmpty() || size <= 1f) {
			return;
		}
		float scale = size / 16f;
		LocalPlayer player = minecraft.player;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		if (player == null) {
			graphics.item(stack, 0, 0);
		} else {
			graphics.item(player, stack, 0, 0, 1);
		}
		if (decorations) {
			graphics.itemDecorations(font, stack, 0, 0);
		}
		graphics.pose().popMatrix();
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

	private static ItemStack skillIcon(String name) {
		return switch (name == null ? "" : name) {
			case "Farming" -> new ItemStack(Items.WHEAT);
			case "Mining" -> new ItemStack(Items.DIAMOND_PICKAXE);
			case "Combat" -> new ItemStack(Items.DIAMOND_SWORD);
			case "Foraging" -> new ItemStack(Items.JUNGLE_SAPLING);
			case "Fishing" -> new ItemStack(Items.FISHING_ROD);
			case "Enchanting" -> new ItemStack(Items.ENCHANTING_TABLE);
			case "Alchemy" -> new ItemStack(Items.BREWING_STAND);
			case "Taming" -> new ItemStack(Items.BONE);
			case "Carpentry" -> new ItemStack(Items.CRAFTING_TABLE);
			case "Runecrafting" -> new ItemStack(Items.END_CRYSTAL);
			case "Social" -> new ItemStack(Items.CAKE);
			default -> new ItemStack(Items.PAPER);
		};
	}

	private static ItemStack slayerIcon(String name) {
		return switch (name == null ? "" : name) {
			case "Zombie" -> new ItemStack(Items.ROTTEN_FLESH);
			case "Spider" -> new ItemStack(Items.SPIDER_EYE);
			case "Wolf" -> new ItemStack(Items.BONE);
			case "Enderman" -> new ItemStack(Items.ENDER_PEARL);
			case "Blaze" -> new ItemStack(Items.BLAZE_ROD);
			case "Vampire" -> new ItemStack(Items.REDSTONE);
			default -> new ItemStack(Items.IRON_SWORD);
		};
	}

	private static ItemStack classIcon(String name) {
		return switch (name == null ? "" : name) {
			case "Healer" -> new ItemStack(Items.GOLDEN_APPLE);
			case "Mage" -> new ItemStack(Items.BLAZE_ROD);
			case "Berserk" -> new ItemStack(Items.DIAMOND_AXE);
			case "Archer" -> new ItemStack(Items.BOW);
			case "Tank" -> new ItemStack(Items.DIAMOND_CHESTPLATE);
			default -> new ItemStack(Items.IRON_SWORD);
		};
	}

	private static ProfileViewer.Skill skill(ProfileViewer.Profile profile, String name) {
		for (ProfileViewer.Skill skill : profile.skills()) {
			if (name.equals(skill.name())) {
				return skill;
			}
		}
		return new ProfileViewer.Skill(name, 0, 60, 0, 0f);
	}

	private static String prettyMode(String mode) {
		return switch (mode == null ? "" : mode.toLowerCase(Locale.ROOT)) {
			case "ironman" -> "Ironman";
			case "bingo" -> "Bingo";
			case "island" -> "Stranded";
			default -> "Normal profile";
		};
	}

	private static String skyblockLevel(ProfileViewer.Profile profile) {
		int level = profile.skyblockLevel();
		int progress = Math.max(0, Math.min(99, Math.round(profile.skyblockProgress() * 100f)));
		if (progress <= 0) {
			return String.valueOf(level);
		}
		return level + "." + (progress < 10 ? "0" + progress : String.valueOf(progress));
	}

	private static String kd(ProfileViewer.Profile profile) {
		if (profile.deaths() <= 0L) {
			return compact(profile.kills()) + "/0";
		}
		double ratio = profile.kills() / (double) profile.deaths();
		return compact(profile.kills()) + "/" + compact(profile.deaths()) + "  " + trim(ratio);
	}

	private static String date(long raw) {
		long ms = normalizeTime(raw);
		if (ms <= 0L) {
			return "—";
		}
		return new SimpleDateFormat("yyyy.MM.dd").format(new Date(ms));
	}

	private static String datetime(long raw) {
		long ms = normalizeTime(raw);
		if (ms <= 0L) {
			return "";
		}
		return new SimpleDateFormat("yyyy.MM.dd HH:mm").format(new Date(ms));
	}

	private static long normalizeTime(long raw) {
		if (raw <= 0L) {
			return 0L;
		}
		return raw < 10_000_000_000L ? raw * 1000L : raw;
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

	private static String prettyCoins(double value) {
		return String.format(Locale.ROOT, "%,.0f coins", value);
	}

	private static String prettyNumber(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	private static String trim(double value) {
		return String.format(Locale.ROOT, value >= 10 ? "%.0f" : "%.1f", value);
	}

	private static String shortPage(String name, int index) {
		if (name == null || name.isBlank()) {
			return String.valueOf(index + 1);
		}
		String digits = name.replaceAll("\\D+", "");
		return digits.isBlank() ? String.valueOf(index + 1) : digits;
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
