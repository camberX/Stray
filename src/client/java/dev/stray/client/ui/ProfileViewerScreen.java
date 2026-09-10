package dev.stray.client.ui;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.StrayClient;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.item.ItemText;
import dev.stray.client.item.SkyblockLore;
import dev.stray.client.item.SkyblockPetLore;
import dev.stray.client.profile.ProfileViewer;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.render.PlayerPreview;
import dev.stray.client.render.Starfield;
import dev.stray.client.visual.NickHider;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stray Skyblock profile viewer. Tabs and chrome are ours; the numbers come
 * from the public Hypixel profile host.
 */
public class ProfileViewerScreen extends Screen {
	private static final float MENU_W = 720;
	private static final float MENU_H = 348;
	private static final float RAIL = 84;
	private static final float ROW = 16;
	private static final float CHIP_H = 18;
	private static final float SLOT_GAP = 3;
	private static final String GLASS = "#";
	private static final String[][] HOTM_TREE = {
		{null, "gemstone_infusion", "crystalline", "gifts_from_the_departed", "mining_master", "hungry_for_more", "vanguard_seeker", "sheer_force", null},
		{null, null, "metal_head", GLASS, "rags_to_riches", GLASS, "eager_adventurer", null, null},
		{null, "miners_blessing", "no_stone_unturned", "strong_arm", "steady_hand", "warm_hearted", "surveyor", "mineshaft_mayhem", null},
		{null, null, "mining_speed_2", GLASS, "powder_buff", GLASS, "mining_fortune_2", null, null},
		{null, "anomalous_desire", "blockhead", "subterranean_fisher", "keep_it_cool", "lonesome_miner", "great_explorer", "maniac_miner", null},
		{null, null, "daily_grind", GLASS, "special_0", GLASS, "daily_powder", null, null},
		{null, "daily_effect", "old_school", "professional", "mole", "fortunate", "mining_experience", "front_loaded", null},
		{null, null, "random_event", GLASS, "efficient_miner", GLASS, "forge_time", null, null},
		{null, null, "mining_speed_boost", "precision_mining", "mining_fortune", "titanium_insanium", "pickaxe_toss", null, null},
		{null, null, null, null, "mining_speed", null, null, null, null}
	};

	private enum Tab {
		HOME("Home", MenuFont.PERSON),
		ITEMS("Items", MenuFont.BAG),
		DUNGEONS("Dungeons", MenuFont.SWORD),
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
	private int selectedPet = -1;
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
		float titleX = windowX + 12;
		ProfileViewer.Snapshot snap = ProfileViewer.snapshot();
		ProfileViewer.Profile profile = snap.current();
		if (ironman(profile)) {
			paintItem(graphics, font, new ItemStack(Items.IRON_INGOT), titleX, windowY + 6, 12, false);
			titleX += 16;
		}
		GuiDraw.title(graphics, font, "PROFILE", titleX, windowY + 8, Theme.TEXT);
		String sub = snap.name().isBlank() ? "Skyblock" : snap.name();
		if (!profile.cuteName().isBlank()) {
			sub = sub + " · " + profile.cuteName();
		}
		GuiDraw.small(
			graphics,
			font,
			sub,
			titleX + GuiDraw.titleWidth(font, "PROFILE") + 6,
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
			boolean mode = ironman(next);
			float w = GuiDraw.smallWidth(font, label) + 12 + (mode ? 12 : 0);
			boolean on = i == snap.selected();
			boolean over = GuiDraw.hovered(mouseX, mouseY, chipX, chipY, w, 14);
			GuiDraw.panel(graphics, chipX, chipY, w, 14, 5, on || over ? Theme.CARD_HOVER : Theme.CARD, on ? Theme.ACCENT : Theme.LINE);
			float textX = chipX + 6;
			if (mode) {
				paintItem(graphics, font, new ItemStack(Items.IRON_INGOT), chipX + 3, chipY + 1, 11, false);
				textX += 12;
			}
			GuiDraw.small(graphics, font, label, textX, GuiDraw.middle(chipY, 14), on ? Theme.ACCENT : Theme.TEXT);
			int index = i;
			hits.add(new Hit(chipX, chipY, w, 14, () -> {
				ProfileViewer.select(index);
				listScroll = 0f;
				itemPage = 0;
				selectedPet = -1;
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
			case DUNGEONS -> drawDungeons(graphics, font, mouseX, mouseY, x, y, w, h, profile);
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
		float playerW = Mth.clamp(innerW * 0.40f, 180f, 270f);
		float sideW = (innerW - playerW - 16f) * 0.5f;
		if (sideW < 140f) {
			playerW = Math.max(160f, innerW * 0.36f);
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
		infoRow(graphics, font, mouseX, mouseY, x, ry, w, new ItemStack(Items.EMERALD), "Networth", compact(profile.networth()),
			"Purse " + prettyCoins(profile.purse())
				+ "\nBank " + prettyCoins(profile.bank())
				+ "\nItems " + prettyCoins(profile.itemWorth())
				+ "\nMarket value of gear, storage, accessories, pets, sacks, and coins.");
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
		float stageH = Math.min(h - 2, w * 1.55f);
		GuiDraw.panel(graphics, x, y, w, stageH, 8, Theme.CARD, Theme.LINE);
		if (ironman(profile)) {
			paintItem(graphics, font, new ItemStack(Items.IRON_INGOT), x + 6, y + 6, 12, false);
		}
		Component tag = nametag(snap, profile);
		PlayerPreview.Drawn drawn = PlayerPreview.drawEquipped(
			graphics,
			x + 2,
			y + 2,
			w - 4,
			stageH - 12,
			0f,
			0f,
			new PlayerPreview.View(viewScale, viewCx, viewCy, viewLift),
			gearOf(profile.armor()),
			74f,
			skinOf(snap)
		);
		if (drawn != null) {
			NametagRenderer.drawVanilla(graphics, font, drawn.nameX(), drawn.nameY(), tag);
		} else {
			String name = snap.name().isBlank() ? "?" : snap.name();
			GuiDraw.title(graphics, font, name.substring(0, 1).toUpperCase(Locale.ROOT), x + (w - 12) * 0.5f - 4, y + stageH * 0.38f, Theme.ACCENT);
			NametagRenderer.drawVanilla(graphics, font, x + w * 0.5f, y + stageH * 0.38f + 22, tag);
		}
	}

	private PlayerSkin skinOf(ProfileViewer.Snapshot snap) {
		if (minecraft == null) {
			return null;
		}
		if (minecraft.player != null && snap.uuid() != null && minecraft.player.getUUID().equals(snap.uuid())) {
			return minecraft.player.getSkin();
		}
		if (snap.uuid() == null) {
			return null;
		}
		String name = snap.name() == null || snap.name().isBlank() ? "Player" : snap.name();
		String value = snap.skinValue();
		if (value != null && !value.isBlank()) {
			String signature = snap.skinSignature();
			Property textures = signature == null || signature.isBlank()
				? new Property("textures", value)
				: new Property("textures", value, signature);
			PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", textures));
			return minecraft.getSkinManager()
				.createLookup(new GameProfile(snap.uuid(), name, properties), signature != null && !signature.isBlank())
				.get();
		}
		return minecraft.getSkinManager().createLookup(new GameProfile(snap.uuid(), name), false).get();
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

	private void drawDungeons(
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
		float top = y + 8;
		float col = (w - 36) / 3f;
		ProfileViewer.Dungeon dungeon = profile.dungeons();
		skillBar(graphics, font, mouseX, mouseY, left, top, w - 24, new ProfileViewer.Skill(
			"Catacombs",
			dungeon.cata(),
			50,
			0,
			dungeon.progress()
		));
		float statsY = top + 30;
		infoRow(graphics, font, mouseX, mouseY, left, statsY, col, new ItemStack(Items.CHEST), "Secrets",
			compact(dungeon.secrets()), prettyNumber(dungeon.secrets()) + " secrets");
		String avg = dungeon.runs() <= 0 ? "—" : trim(dungeon.secrets() / (double) dungeon.runs());
		infoRow(graphics, font, mouseX, mouseY, left + col + 6, statsY, col, new ItemStack(Items.ENDER_EYE), "Secret avg",
			avg, dungeon.runs() <= 0 ? "No runs yet." : avg + " secrets per run");
		infoRow(graphics, font, mouseX, mouseY, left + (col + 6) * 2, statsY, col, new ItemStack(Items.IRON_SWORD), "Runs",
			compact(dungeon.runs()), prettyNumber(dungeon.runs()) + " floor completions");
		float y0 = statsY + 20;
		sectionTitle(graphics, font, left, y0, w - 24, new ItemStack(Items.IRON_CHESTPLATE), "Classes");
		y0 += 13;
		float classW = (w - 32) * 0.5f;
		List<ProfileViewer.Skill> classes = dungeon.classes();
		for (int i = 0; i < classes.size(); i++) {
			float cx = left + (i % 2) * (classW + 8);
			float cy = y0 + (i / 2) * 24f;
			skillBar(graphics, font, mouseX, mouseY, cx, cy, classW, classes.get(i));
		}
		y0 += 24 * ((classes.size() + 1) / 2) + 4;
		float floorW = (w - 36) * 0.5f;
		sectionTitle(graphics, font, left, y0, floorW, sky("GOLD_BONZO_HEAD"), "Catacombs");
		sectionTitle(graphics, font, left + floorW + 12, y0, floorW, sky("DIAMOND_BONZO_HEAD"), "Master");
		y0 += 14;
		drawFloors(graphics, font, mouseX, mouseY, left, y0, floorW, y + h - 6, dungeon.normal(), false);
		drawFloors(graphics, font, mouseX, mouseY, left + floorW + 12, y0, floorW, y + h - 6, dungeon.master(), true);
	}

	private void drawFloors(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		float maxY,
		List<ProfileViewer.Floor> floors,
		boolean master
	) {
		float row = 16;
		for (int i = 0; i < floors.size(); i++) {
			float fy = y + i * row;
			if (fy + row > maxY) {
				break;
			}
			ProfileViewer.Floor floor = floors.get(i);
			boolean hover = GuiDraw.hovered(mouseX, mouseY, x, fy, w, row - 1);
			if (hover) {
				GuiDraw.rounded(graphics, x - 2, fy - 1, w + 4, row - 1, 4, 0x10FFFFFF);
				tooltip = floor.name() + "  " + prettyNumber(floor.completions()) + " runs"
					+ "\nS  " + clock(floor.bestS())
					+ "\nS+  " + clock(floor.bestSPlus());
			}
			paintItem(graphics, font, floorIcon(floor.name(), master), x, fy, 12, false);
			GuiDraw.small(graphics, font, floor.name(), x + 16, GuiDraw.middle(fy, 14), Theme.MUTED);
			GuiDraw.menu(graphics, font, compact(floor.completions()), x + 42, GuiDraw.middle(fy, 14), Theme.TEXT);
			String plus = clock(floor.bestSPlus());
			if ("—".equals(plus)) {
				plus = clock(floor.bestS());
			}
			GuiDraw.small(graphics, font, plus, x + w - GuiDraw.smallWidth(font, plus), GuiDraw.middle(fy, 14), Theme.MUTED);
		}
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
		float left = x + 8;
		float top = y + 6;
		float barW = (w - 28) / 4f;
		ProfileViewer.Mining mining = profile.mining();
		infoRow(graphics, font, mouseX, mouseY, left, top, barW, new ItemStack(Items.DIAMOND_PICKAXE), "HOTM",
			String.valueOf(mining.hotm()), "Heart of the Mountain " + mining.hotm());
		infoRow(graphics, font, mouseX, mouseY, left + barW + 4, top, barW, new ItemStack(Items.PRISMARINE_CRYSTALS), "Mithril",
			compact(mining.mithril()), prettyNumber(mining.mithril()) + " mithril powder");
		infoRow(graphics, font, mouseX, mouseY, left + (barW + 4) * 2, top, barW, new ItemStack(Items.AMETHYST_SHARD), "Gemstone",
			compact(mining.gemstone()), prettyNumber(mining.gemstone()) + " gemstone powder");
		infoRow(graphics, font, mouseX, mouseY, left + (barW + 4) * 3, top, barW, new ItemStack(Items.BLUE_ICE), "Glacite",
			compact(mining.glacite()), prettyNumber(mining.glacite()) + " glacite powder");
		drawHotmTree(graphics, font, mouseX, mouseY, left, top + 20, w - 16, h - 30, mining);
	}

	private void drawHotmTree(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		float h,
		ProfileViewer.Mining mining
	) {
		int rows = HOTM_TREE.length;
		int cols = HOTM_TREE[0].length;
		float node = Math.min(26f, Math.min((w - 2) / cols, (h - 2) / rows));
		float gridW = cols * node;
		float gridH = rows * node;
		float ox = x + Math.max(0f, (w - gridW) * 0.5f);
		float oy = y + Math.max(0f, (h - gridH) * 0.05f);
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				String id = HOTM_TREE[row][col];
				if (id == null) {
					continue;
				}
				float nx = ox + col * node;
				float ny = oy + row * node;
				float size = node - 1;
				if (GLASS.equals(id)) {
					continue;
				}
				int level = mining.perk(id, aliasPerk(id));
				boolean on = level > 0;
				boolean hover = GuiDraw.hovered(mouseX, mouseY, nx, ny, size, size);
				ItemStack icon = perkIcon(id, on, level);
				paintItem(graphics, font, icon, nx + 1, ny + 1, Math.max(8f, size - 2f), true);
				if (level > 99) {
					String text = String.valueOf(level);
					GuiDraw.small(graphics, font, text, nx + size - GuiDraw.smallWidth(font, text) - 1, ny + size - 9, Theme.TEXT);
				}
				if (hover) {
					hoverStack = perkTooltip(id, level, on);
				}
			}
		}
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
		float top = y + 10;
		float col = (w - 32) / 3f;
		ProfileViewer.Skill farming = skill(profile, "Farming");
		infoRow(graphics, font, mouseX, mouseY, left, top, col, new ItemStack(Items.WHEAT), "Farming",
			farming.level() + " / " + farming.cap(), "Farming " + farming.level() + " / " + farming.cap());
		infoRow(graphics, font, mouseX, mouseY, left + col + 8, top, col, new ItemStack(Items.OAK_SAPLING), "Garden",
			String.valueOf(profile.farming().garden()), "Garden level " + profile.farming().garden());
		infoRow(graphics, font, mouseX, mouseY, left + (col + 8) * 2, top, col, new ItemStack(Items.PLAYER_HEAD), "Visitors",
			String.valueOf(profile.farming().visitors()), profile.farming().visitors() + " unique visitors");

		List<ProfileViewer.Crop> crops = profile.farming().crops();
		float gridY = top + 28;
		float gridH = y + h - gridY - 8;
		int columns = 5;
		int rows = 2;
		float cardW = (w - 28 - (columns - 1) * 6f) / columns;
		float cardH = Math.min(72f, (gridH - (rows - 1) * 6f) / rows);
		for (int i = 0; i < crops.size(); i++) {
			int column = i % columns;
			int row = i / columns;
			if (row >= rows) {
				break;
			}
			ProfileViewer.Crop crop = crops.get(i);
			float cx = left + column * (cardW + 6);
			float cy = gridY + row * (cardH + 6);
			boolean hover = GuiDraw.hovered(mouseX, mouseY, cx, cy, cardW, cardH);
			GuiDraw.panel(graphics, cx, cy, cardW, cardH, 7, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
			paintItem(graphics, font, cropIcon(crop.name()), cx + 6, cy + 8, 16, false);
			GuiDraw.small(graphics, font, crop.name(), cx + 26, cy + 8, Theme.MUTED);
			GuiDraw.menu(graphics, font, crop.level() + " / " + crop.cap(), cx + 26, cy + 18, Theme.TEXT);
			GuiDraw.small(graphics, font, compact(crop.amount()), cx + 26, cy + 30, Theme.MUTED);
			float barW = cardW - 16;
			GuiDraw.rounded(graphics, cx + 8, cy + cardH - 12, barW, 4, 2, Theme.TRACK);
			float fill = Math.max(0f, Math.min(1f, crop.progress()));
			if (fill > 0.01f) {
				GuiDraw.rounded(graphics, cx + 8, cy + cardH - 12, Math.max(4f, barW * fill), 4, 2, Theme.ACCENT);
			}
			if (hover) {
				tooltip = crop.name() + " milestone " + crop.level() + " / " + crop.cap() + "\n" + prettyNumber(crop.amount()) + " collected";
			}
		}
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
		ProfileViewer.Pet shown = shownPetResolved(pets);
		float previewW = 168;
		float pad = 10;
		float px = x + pad;
		float py = y + pad;
		float ph = h - pad * 2;
		GuiDraw.panel(graphics, px, py, previewW, ph, 8, Theme.CARD, Theme.LINE);
		if (shown != null) {
			ItemStack icon = petStack(shown);
			paintItem(graphics, font, icon, px + (previewW - 48) * 0.5f, py + 18, 48, false);
			GuiDraw.menu(graphics, font, clip(font, shown.name(), previewW - 16), px + 8, py + 78, Theme.TEXT);
			GuiDraw.small(graphics, font, shown.tier() + "  " + shown.level(), px + 8, py + 92, tierColor(shown.tier()));
			if (shown.active()) {
				GuiDraw.small(graphics, font, "Active", px + 8, py + 106, Theme.ACCENT);
			}
			hoverStack = GuiDraw.hovered(mouseX, mouseY, px, py, previewW, ph) ? icon : hoverStack;
		}

		float gx = px + previewW + 10;
		float gy = py;
		float gw = w - previewW - pad * 3;
		float gh = ph;
		int cols = Math.max(4, (int) (gw / 36f));
		float cell = Math.min(34f, gw / cols);
		int rows = Math.max(1, (int) (gh / cell));
		int first = (int) (listScroll / cell);
		int visible = cols * rows;
		listScroll = Mth.clamp(listScroll, 0f, Math.max(0f, (float) Math.ceil(pets.size() / (double) cols) * cell - gh));
		for (int i = first * cols; i < pets.size() && i < first * cols + visible; i++) {
			int local = i - first * cols;
			int col = local % cols;
			int row = local / cols;
			ProfileViewer.Pet pet = pets.get(i);
			float cx = gx + col * cell;
			float cy = gy + row * cell;
			boolean on = shown == pet;
			boolean hover = GuiDraw.hovered(mouseX, mouseY, cx, cy, cell - 3, cell - 3);
			GuiDraw.panel(
				graphics,
				cx,
				cy,
				cell - 3,
				cell - 3,
				6,
				on || hover ? Theme.CARD_HOVER : Theme.CARD,
				on ? Theme.ACCENT : Theme.LINE
			);
			paintItem(graphics, font, petStack(pet), cx + 4, cy + 3, cell - 14, false);
			GuiDraw.small(graphics, font, String.valueOf(pet.level()), cx + 4, cy + cell - 13, Theme.MUTED);
			if (hover) {
				hoverStack = petStack(pet);
			}
			int index = i;
			hits.add(new Hit(cx, cy, cell - 3, cell - 3, () -> selectedPet = index));
		}
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
		float armorGap = armor ? 10f : 0f;
		float hotbarGap = playerInv && bag.size() >= 36 ? 8f : 0f;
		float byWidth = (availW - armorGap) / (cols + (armor ? 1 : 0));
		float byHeight = (availH - hotbarGap) / rows;
		slot = Mth.clamp(Math.min(byWidth, byHeight), 20f, 36f);

		float step = slot + SLOT_GAP;
		float gridW = (armor ? slot + armorGap : 0f) + cols * slot + (cols - 1) * SLOT_GAP;
		float gridH = rows * slot + (rows - 1) * SLOT_GAP + hotbarGap;
		float gridX = x + (w - gridW) * 0.5f;
		float gridY = top + Math.max(0f, (availH - gridH) * 0.35f);
		if (playerInv && armor) {
			drawArmor(graphics, font, mouseX, mouseY, gridX, gridY + (gridH - 4 * step + SLOT_GAP) * 0.5f, profile.armor());
			gridX += slot + armorGap;
		}
		if (playerInv && bag.size() >= 36) {
			drawGrid(graphics, font, mouseX, mouseY, gridX, gridY, 9, 3, bag, 9);
			drawGrid(graphics, font, mouseX, mouseY, gridX, gridY + 3 * step + hotbarGap - SLOT_GAP, 9, 1, bag, 0);
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
			drawSlot(graphics, font, mouseX, mouseY, x, y + i * (slot + SLOT_GAP), armor.at(index));
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
				drawSlot(graphics, font, mouseX, mouseY, x + col * (slot + SLOT_GAP), y + row * (slot + SLOT_GAP), bag.at(index));
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
		GuiDraw.panel(graphics, x, y, slot, slot, 6, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
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
		List<String> lore = item.lore();
		if (lore != null && !lore.isEmpty()) {
			ItemText.fromLegacy(item.name(), lore).apply(stack);
		} else {
			String loreId = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
			if (!loreId.isBlank()) {
				SkyblockLore.request(loreId);
				ItemText text = SkyblockLore.get(loreId);
				if (text != null && text.present()) {
					text.apply(stack);
				} else if (item.name() != null && !item.name().isBlank()) {
					ItemText.fromLegacy(item.name(), List.of()).apply(stack);
				}
			}
		}
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
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, 22);
		if (hover) {
			tooltip = skill.name() + "  " + skill.level() + (skill.level() > skill.cap() ? "" : " / " + skill.cap())
				+ "\n" + prettyNumber((long) skill.xp()) + " xp";
		}
		paintItem(graphics, font, skillIcon(skill.name()), x, y, 14, false);
		GuiDraw.small(graphics, font, skill.name(), x + 18, y, Theme.MUTED);
		String value = skill.level() > skill.cap() ? String.valueOf(skill.level()) : skill.level() + " / " + skill.cap();
		GuiDraw.menu(graphics, font, value, x + w - GuiDraw.menuWidth(font, value), y, Theme.TEXT);
		GuiDraw.rounded(graphics, x + 18, y + 12, w - 18, 4, 2, Theme.TRACK);
		float fill = Math.max(0f, Math.min(1f, skill.progress()));
		if (fill > 0.01f) {
			GuiDraw.rounded(graphics, x + 18, y + 12, Math.max(4f, (w - 18) * fill), 4, 2, Theme.ACCENT);
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
		selectedPet = -1;
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
			case "Catacombs" -> sky("DUNGEON_STONE");
			case "Healer", "Mage", "Berserk", "Archer", "Tank" -> classIcon(name);
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

	private ProfileViewer.Pet shownPetResolved(List<ProfileViewer.Pet> pets) {
		if (pets == null || pets.isEmpty()) {
			return null;
		}
		if (selectedPet >= 0 && selectedPet < pets.size()) {
			return pets.get(selectedPet);
		}
		for (ProfileViewer.Pet pet : pets) {
			if (pet.active()) {
				return pet;
			}
		}
		return pets.get(0);
	}

	private static String aliasPerk(String id) {
		return ProfileViewer.perkAlias(id);
	}

	private static String perkName(String id) {
		return switch (id == null ? "" : id) {
			case "special_0" -> "Peak of the Mountain";
			case "mining_speed_2" -> "Speedy Mineman";
			case "mining_fortune_2" -> "Fortunate Mineman";
			case "pickaxe_toss" -> "Pickobulus";
			case "daily_effect" -> "Sky Mall";
			case "random_event" -> "Luck of the Cave";
			case "mining_experience" -> "Seasoned Mineman";
			case "fortunate" -> "Gem Lover";
			case "forge_time", "quick_forge" -> "Quick Forge";
			case "gifts_from_the_departed" -> "Gifts from the Departed";
			case "hungry_for_more", "dead_mans_chest" -> "Dead Man's Chest";
			case "warm_hearted", "warm_heart" -> "Warm Hearted";
			default -> prettyPerk(id);
		};
	}

	private static String prettyPerk(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String cleaned = id.replace('_', ' ').trim();
		StringBuilder out = new StringBuilder(cleaned.length());
		boolean cap = true;
		for (int i = 0; i < cleaned.length(); i++) {
			char ch = cleaned.charAt(i);
			if (ch == ' ') {
				out.append(ch);
				cap = true;
				continue;
			}
			out.append(cap ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
			cap = false;
		}
		return out.toString();
	}

	private static boolean pickaxeAbility(String id) {
		return switch (id == null ? "" : id) {
			case "mining_speed_boost", "pickaxe_toss", "pickobulus", "maniac_miner", "anomalous_desire",
				"gemstone_infusion", "sheer_force", "vein_seeker" -> true;
			default -> false;
		};
	}

	private static ItemStack perkIcon(String id, boolean unlocked, int level) {
		String key = id == null ? "" : id;
		ItemStack stack;
		if ("special_0".equals(key) || "core_of_the_mountain".equals(key)) {
			stack = new ItemStack(unlocked ? Items.REDSTONE_BLOCK : Items.BEDROCK);
		} else if (pickaxeAbility(key)) {
			stack = new ItemStack(unlocked ? Items.BLAZE_ROD : Items.COAL_BLOCK);
		} else if (unlocked && "precision_mining".equals(key)) {
			stack = new ItemStack(Items.END_PORTAL_FRAME);
		} else if (unlocked && ("mining_speed".equals(key) || "efficient_miner".equals(key) || "mole".equals(key))) {
			stack = new ItemStack(Items.PRISMARINE_CRYSTALS);
		} else if (unlocked) {
			stack = new ItemStack(Items.EMERALD);
		} else {
			stack = new ItemStack(Items.COAL);
		}
		int count = unlocked ? Math.max(1, level) : ProfileViewer.perkTier(key);
		stack.setCount(Math.max(1, Math.min(99, count)));
		return stack;
	}

	private static ItemStack perkTooltip(String id, int level, boolean on) {
		ItemStack stack = perkIcon(id, on, level);
		String title = (on ? "§a" : "§c") + perkName(id);
		List<String> lore = new ArrayList<>();
		if (on) {
			lore.add("§7Level " + level);
		} else {
			lore.add("§cLocked");
		}
		String desc = perkDesc(id);
		if (!desc.isBlank()) {
			lore.add("");
			lore.add("§7" + desc);
		}
		ItemText.fromLegacy(title, lore).apply(stack);
		return stack;
	}

	private static String perkDesc(String id) {
		return switch (id == null ? "" : id) {
			case "mining_speed" -> "Grants Mining Speed.";
			case "mining_fortune" -> "Grants Mining Fortune.";
			case "titanium_insanium" -> "Chance for Mithril to drop Titanium.";
			case "mining_speed_boost" -> "Pickaxe Ability: temporary Mining Speed.";
			case "precision_mining" -> "Highlights the block you are looking at.";
			case "pickaxe_toss" -> "Pickaxe Ability: throw your pickaxe.";
			case "random_event" -> "Chance to trigger a special mining event.";
			case "efficient_miner" -> "Grants Mining Spread.";
			case "forge_time" -> "Reduces the time items take to forge.";
			case "daily_effect" -> "Sky Mall perk changes every SkyBlock day.";
			case "old_school" -> "Grants Ore Fortune.";
			case "professional" -> "Grants Mining Speed.";
			case "mole" -> "Grants Mining Spread.";
			case "fortunate" -> "Grants Gemstone Fortune.";
			case "mining_experience" -> "Grants Mining Wisdom.";
			case "front_loaded" -> "First ores mined give more Powder and Fortune.";
			case "daily_grind" -> "Daily commissions grant extra Mithril Powder.";
			case "special_0" -> "Unlocks extra tokens, forge slots, and powder.";
			case "daily_powder" -> "Gives Mithril Powder every SkyBlock day.";
			case "anomalous_desire" -> "Pickaxe Ability: extra Powder and loot.";
			case "blockhead" -> "Grants Block Fortune.";
			case "subterranean_fisher" -> "Grants Fishing Fortune in the Glacite Tunnels.";
			case "keep_it_cool" -> "Grants Heat Resistance.";
			case "lonesome_miner" -> "Grants combat stats while in mining islands.";
			case "great_explorer" -> "More treasure chests in the Crystal Hollows.";
			case "maniac_miner" -> "Pickaxe Ability: extra Mining Speed and Fortune.";
			case "powder_buff" -> "Gain more Powder from ores.";
			case "mining_speed_2" -> "Grants more Mining Speed.";
			case "mining_fortune_2" -> "Grants more Mining Fortune.";
			case "miners_blessing" -> "Grants Magic Find while mining.";
			case "no_stone_unturned" -> "Chance to find treasure in the Glacite Mineshafts.";
			case "strong_arm" -> "Grants Mining Speed on Dwarven Metals.";
			case "steady_hand" -> "Grants Gemstone Spread.";
			case "warm_hearted" -> "Grants Cold Resistance.";
			case "surveyor" -> "Increases Mineshaft chance.";
			case "mineshaft_mayhem" -> "Bonus effect when you enter a Mineshaft.";
			case "metal_head" -> "Grants Dwarven Metal Fortune.";
			case "rags_to_riches" -> "Grants Mining Fortune in Mineshafts.";
			case "eager_adventurer" -> "Grants Mining Speed in Mineshafts.";
			case "gemstone_infusion" -> "Pickaxe Ability: stronger gemstone slots.";
			case "crystalline" -> "More Gemstone Crystal Mineshafts.";
			case "gifts_from_the_departed" -> "Extra loot from Frozen Corpses.";
			case "hungry_for_more", "dead_mans_chest" -> "More treasure from Frozen Corpses and chests.";
			case "mining_master" -> "Grants Pristine.";
			case "vanguard_seeker" -> "More Vanguard Corpse Mineshafts.";
			case "sheer_force" -> "Pickaxe Ability: extra Mining Spread.";
			default -> "";
		};
	}

	private static ItemStack cropIcon(String name) {
		return switch (name == null ? "" : name) {
			case "Wheat" -> new ItemStack(Items.WHEAT);
			case "Carrot" -> new ItemStack(Items.CARROT);
			case "Potato" -> new ItemStack(Items.POTATO);
			case "Pumpkin" -> new ItemStack(Items.PUMPKIN);
			case "Melon" -> new ItemStack(Items.MELON_SLICE);
			case "Mushroom" -> new ItemStack(Items.RED_MUSHROOM);
			case "Cocoa" -> new ItemStack(Items.COCOA_BEANS);
			case "Cactus" -> new ItemStack(Items.CACTUS);
			case "Cane" -> new ItemStack(Items.SUGAR_CANE);
			case "Wart" -> new ItemStack(Items.NETHER_WART);
			default -> new ItemStack(Items.WHEAT);
		};
	}

	private static boolean ironman(ProfileViewer.Profile profile) {
		return profile != null && "ironman".equalsIgnoreCase(profile.gameMode());
	}

	private static Component nametag(ProfileViewer.Snapshot snap, ProfileViewer.Profile profile) {
		if (snap != null && snap.taggedName() != null && !snap.taggedName().isBlank()) {
			return NickHider.parseLegacy(snap.taggedName());
		}
		String name = snap != null && snap.name() != null && !snap.name().isBlank() ? snap.name() : "?";
		int level = profile == null ? 0 : profile.skyblockLevel();
		if (level <= 0) {
			return Component.literal(name);
		}
		return NickHider.parseLegacy("§8[" + levelColor(level) + level + "§8] §7" + name);
	}

	private static String levelColor(int level) {
		if (level >= 480) {
			return "§4";
		}
		if (level >= 440) {
			return "§c";
		}
		if (level >= 400) {
			return "§6";
		}
		if (level >= 360) {
			return "§5";
		}
		if (level >= 320) {
			return "§d";
		}
		if (level >= 280) {
			return "§9";
		}
		if (level >= 240) {
			return "§3";
		}
		if (level >= 200) {
			return "§b";
		}
		if (level >= 160) {
			return "§2";
		}
		if (level >= 120) {
			return "§a";
		}
		if (level >= 80) {
			return "§e";
		}
		if (level >= 40) {
			return "§f";
		}
		return "§7";
	}

	private static ItemStack sky(String id) {
		ItemStack stack = ItemIds.resolve("sb:" + id).stack();
		if (stack != null && !stack.isEmpty()) {
			return stack;
		}
		return new ItemStack(Items.PAPER);
	}

	private static ItemStack floorIcon(String name, boolean master) {
		String floor = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
		if (floor.equals("E") || floor.equals("ENTRANCE")) {
			return new ItemStack(Items.OAK_DOOR);
		}
		String boss = switch (floor) {
			case "F1", "M1" -> "BONZO";
			case "F2", "M2" -> "SCARF";
			case "F3", "M3" -> "PROFESSOR";
			case "F4", "M4" -> "THORN";
			case "F5", "M5" -> "LIVID";
			case "F6", "M6" -> "SADAN";
			case "F7", "M7" -> "NECRON";
			default -> "";
		};
		if (boss.isEmpty()) {
			return sky("DUNGEON_STONE");
		}
		return sky((master || floor.startsWith("M") ? "DIAMOND_" : "GOLD_") + boss + "_HEAD");
	}

	private static ItemStack petIcon(ProfileViewer.Pet pet) {
		String type = pet == null || pet.type() == null ? "" : pet.type().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		if (type.isBlank() && pet != null && pet.name() != null) {
			type = pet.name().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		}
		type = switch (type) {
			case "CAT" -> "OCELOT";
			case "DRAGON" -> "ENDER_DRAGON";
			case "IRON_GOLEM" -> "GOLEM";
			case "YETI" -> "BABY_YETI";
			case "WISP" -> "DROPLET_WISP";
			case "MONTEZUMA" -> "FRACTURED_MONTEZUMA_SOUL";
			case "T_REX", "TREX", "T-REX" -> "TYRANNOSAURUS";
			case "COW", "MOOSHROOM" -> "MOOSHROOM_COW";
			case "DOG" -> "WOLF";
			default -> type;
		};
		String hash = PET_HEADS.get(type);
		if (hash != null && !hash.isBlank()) {
			return ItemIds.skull(type, hash);
		}
		return sky("PET");
	}

	private static ItemStack petStack(ProfileViewer.Pet pet) {
		ItemStack stack = petIcon(pet);
		if (stack.isEmpty()) {
			return stack;
		}
		int tier = petTierIndex(pet.tier());
		String loreId = pet.type() == null ? "" : pet.type().trim().toUpperCase(Locale.ROOT) + ";" + tier;
		SkyblockPetLore.request();
		SkyblockLore.Snapshot snapshot = null;
		if (!loreId.equals(";0") && !loreId.startsWith(";")) {
			SkyblockLore.request(loreId);
			snapshot = SkyblockLore.snapshot(loreId);
		}
		SkyblockPetLore.tooltip(
			pet.type(),
			pet.tier(),
			pet.level(),
			pet.name(),
			pet.held(),
			pet.candy(),
			pet.active(),
			snapshot
		).apply(stack);
		return stack;
	}

	private static int petTierIndex(String tier) {
		return switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
			case "uncommon" -> 1;
			case "rare" -> 2;
			case "epic" -> 3;
			case "legendary" -> 4;
			case "mythic" -> 5;
			default -> 0;
		};
	}

	private static String tierCode(String tier) {
		return switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
			case "uncommon" -> "§a";
			case "rare" -> "§9";
			case "epic" -> "§5";
			case "legendary" -> "§6";
			case "mythic" -> "§d";
			default -> "§f";
		};
	}

	private static String petFamily(String type) {
		return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
			case "WOLF", "TIGER", "LION", "ENDERMAN", "ENDER_DRAGON", "GOLDEN_DRAGON", "BLAZE",
				"SKELETON", "ZOMBIE", "SPIDER", "TARANTULA", "HOUND", "PHOENIX", "GRIFFIN",
				"BLACK_CAT", "WITHER_SKELETON", "GOLEM", "IRON_GOLEM", "KUUDRA" -> "Combat";
			case "RABBIT", "CHICKEN", "PIG", "MOOSHROOM_COW", "ELEPHANT", "SLUG", "BEE" -> "Farming";
			case "ARMADILLO", "SILVERFISH", "ROCK", "MITHRIL_GOLEM", "SCATHA", "SNAIL",
				"BAL", "MOLE", "GLACITE_GOLEM", "GOBLIN" -> "Mining";
			case "SQUID", "DOLPHIN", "BLUE_WHALE", "FLYING_FISH", "MEGALODON", "BABY_YETI",
				"AMMONITE", "PENGUIN", "REINDEER", "SPINOSAURUS" -> "Fishing";
			case "OCELOT", "MONKEY", "GIRAFFE" -> "Foraging";
			case "JELLYFISH", "PARROT", "SHEEP" -> "Alchemy";
			case "GUARDIAN" -> "Enchanting";
			case "OWL" -> "Taming";
			default -> "Skyblock";
		};
	}

	private static final Map<String, String> PET_HEADS = petHeads();

	private static Map<String, String> petHeads() {
		Map<String, String> heads = new HashMap<>();
		heads.put("ARMADILLO", "c1eb6df4736ae24dd12a3d00f91e6e3aa7ade6bbefb0978afef2f0f92461018f");
		heads.put("BAT", "382fc3f71b41769376a9e92fe3adbaac3772b999b219c9d6b4680ba9983e527");
		heads.put("BLAZE", "b78ef2e4cf2c41a2d14bfde9caff10219f5b1bf5b35a49eb51c6467882cb5f0");
		heads.put("CHICKEN", "7f37d524c3eed171ce149887ea1dee4ed399904727d521865688ece3bac75e");
		heads.put("HORSE", "36fcd3ec3bc84bafb4123ea479471f9d2f42d8fb9c5f11cf5f4e0d93226");
		heads.put("JERRY", "822d8e751c8f2fd4c8942c44bdb2f5ca4d8ae8e575ed3eb34c18a86e93b");
		heads.put("OCELOT", "5657cd5c2989ff97570fec4ddcdc6926a68a3393250c1be1f0b114a1db1");
		heads.put("PIGMAN", "63d9cb6513f2072e5d4e426d70a5557bc398554c880d4e7b7ec8ef4945eb02f2");
		heads.put("RABBIT", "117bffc1972acd7f3b4a8f43b5b6c7534695b8fd62677e0306b2831574b");
		heads.put("SHEEP", "64e22a46047d272e89a1cfa13e9734b7e12827e235c2012c1a95962874da0");
		heads.put("SILVERFISH", "da91dab8391af5fda54acd2c0b18fbd819b865e1a8f1d623813fa761e924540");
		heads.put("WITHER_SKELETON", "f5ec964645a8efac76be2f160d7c9956362f32b6517390c59c3085034f050cff");
		heads.put("SKELETON_HORSE", "47effce35132c86ff72bcae77dfbb1d22587e94df3cbc2570ed17cf8973a");
		heads.put("WOLF", "dc3dd984bb659849bd52994046964c22725f717e986b12d548fd169367d494");
		heads.put("ENDERMAN", "6eab75eaa5c9f2c43a0d23cfdce35f4df632e9815001850377385f7b2f039ce1");
		heads.put("PHOENIX", "23aaf7b1a778949696cb99d4f04ad1aa518ceee256c72e5ed65bfa5c2d88d9e");
		heads.put("MAGMA_CUBE", "38957d5023c937c4c41aa2412d43410bda23cf79a9f6ab36b76fef2d7c429");
		heads.put("FLYING_FISH", "40cd71fbbbbb66c7baf7881f415c64fa84f6504958a57ccdb8589252647ea");
		heads.put("BLUE_WHALE", "dab779bbccc849f88273d844e8ca2f3a67a1699cb216c0a11b44326ce2cc20");
		heads.put("TIGER", "fc42638744922b5fcf62cd9bf27eeab91b2e72d6c70e86cc5aa3883993e9d84");
		heads.put("LION", "38ff473bd52b4db2c06f1ac87fe1367bce7574fac330ffac7956229f82efba1");
		heads.put("PARROT", "5df4b3401a4d06ad66ac8b5c4d189618ae617f9c143071c8ac39a563cf4e4208");
		heads.put("SNOWMAN", "11136616d8c4a87a54ce78a97b551610c2b2c8f6d410bc38b858f974b113b208");
		heads.put("TURTLE", "212b58c841b394863dbcc54de1c2ad2648af8f03e648988c1f9cef0bc20ee23c");
		heads.put("BEE", "7e941987e825a24ea7baafab9819344b6c247c75c54a691987cd296bc163c263");
		heads.put("ENDER_DRAGON", "aec3ff563290b13ff3bcc36898af7eaa988b6cc18dc254147f58374afe9b21b9");
		heads.put("GUARDIAN", "221025434045bda7025b3e514b316a4b770c6faa4ba9adb4be3809526db77f9d");
		heads.put("SQUID", "01433be242366af126da434b8735df1eb5b3cb2cede39145974e9c483607bac");
		heads.put("GIRAFFE", "176b4e390f2ecdb8a78dc611789ca0af1e7e09229319c3a7aa8209b63b9");
		heads.put("ELEPHANT", "7071a76f669db5ed6d32b48bb2dba55d5317d7f45225cb3267ec435cfa514");
		heads.put("MONKEY", "13cf8db84807c471d7c6922302261ac1b5a179f96d1191156ecf3e1b1d3ca");
		heads.put("SPIDER", "cd541541daaff50896cd258bdbdd4cf80c3ba816735726078bfe393927e57f1");
		heads.put("ENDERMITE", "5a1a0831aa03afb4212adcbb24e5dfaa7f476a1173fce259ef75a85855");
		heads.put("GHOUL", "87934565bf522f6f4726cdfe127137be11d37c310db34d8c70253392b5ff5b");
		heads.put("JELLYFISH", "913f086ccb56323f238ba3489ff2a1a34c0fdceeafc483acff0e5488cfd6c2f1");
		heads.put("PIG", "621668ef7cb79dd9c22ce3d1f3f4cb6e2559893b6df4a469514e667c16aa4");
		heads.put("ROCK", "cb2b5d48e57577563aca31735519cb622219bc058b1f34648b67b8e71bc0fa");
		heads.put("SKELETON", "fca445749251bdd898fb83f667844e38a1dff79a1529f79a42447a0599310ea4");
		heads.put("ZOMBIE", "56fc854bb84cf4b7697297973e02b79bc10698460b51a639c60e5e417734e11");
		heads.put("DOLPHIN", "cefe7d803a45aa2af1993df2544a28df849a762663719bfefc58bf389ab7f5");
		heads.put("BABY_YETI", "ab126814fc3fa846dad934c349628a7a1de5b415021a03ef4211d62514d5");
		heads.put("MEGALODON", "a94ae433b301c7fb7c68cba625b0bd36b0b14190f20e34a7c8ee0d9de06d53b9");
		heads.put("GOLEM", "89091d79ea0f59ef7ef94d7bba6e5f17f2f7d4572c44f90f76c4819a714");
		heads.put("HOUND", "b7c8bef6beb77e29af8627ecdc38d86aa2fea7ccd163dc73c00f9f258f9a1457");
		heads.put("TARANTULA", "8300986ed0a04ea79904f6ae53f49ed3a0ff5b1df62bba622ecbd3777f156df8");
		heads.put("BLACK_CAT", "e4b45cbaa19fe3d68c856cd3846c03b5f59de81a480eec921ab4fa3cd81317");
		heads.put("SPIRIT", "8d9ccc670677d0cebaad4058d6aaf9acfab09abea5d86379a059902f2fe22655");
		heads.put("GRIFFIN", "4c27e3cb52a64968e60c861ef1ab84e0a0cb5f07be103ac78da67761731f00c8");
		heads.put("MITHRIL_GOLEM", "c1b2dfe8ed5dffc5b1687bc1c249c39de2d8a6c3d90305c95f6d1a1a330a0b1");
		heads.put("GRANDMA_WOLF", "4e794274c1bb197ad306540286a7aa952974f5661bccf2b725424f6ed79c7884");
		heads.put("RAT", "a8abb471db0ab78703011979dc8b40798a941f3a4dec3ec61cbeec2af8cffe8");
		heads.put("BAL", "c469ba2047122e0a2de3c7437ad3dd5d31f1ac2d27abde9f8841e1d92a8c5b75");
		heads.put("SCATHA", "df03ad96092f3f789902436709cdf69de6b727c121b3c2daef9ffa1ccaed186c");
		heads.put("GOLDEN_DRAGON", "2e9f9b1fc014166cb46a093e5349b2bf6edd201b680d62e48dbf3af9b0459116");
		heads.put("AMMONITE", "a074a7bd976fe6aba1624161793be547d54c835cf422243a851ba09d1e650553");
		heads.put("BINGO", "d4cd9c707c7092d4759fe2b2b6a713215b6e39919ec4e7afb1ae2b6f8576674c");
		heads.put("MOOSHROOM_COW", "2b52841f2fd589e0bc84cbabf9e1c27cb70cac98f8d6b3dd065e55a4dcb70d77");
		heads.put("SNAIL", "50a9933a3b10489d38f6950c4e628bfcf9f7a27f8d84666f04f14d5374252972");
		heads.put("KUUDRA", "1f0239fb498e5907ede12ab32629ee95f0064574a9ffdff9fc3a1c8e2ec17587");
		heads.put("DROPLET_WISP", "b412e70375ec99ee38ae94b30e9b10752d459662b54794dfe66fe6a183c672d3");
		heads.put("FROST_WISP", "1d8ad9936d758c5ea30b0b7cc7c67c2bfcea829ecf2425c0b50fc92a26ae23d0");
		heads.put("GLACIAL_WISP", "3e2018feebe1a99177b3cb196d4e44521268b4b3eb56e6419cb0253cdbf0456c");
		heads.put("SUBZERO_WISP", "7a0eb37e58c942eca4d33ab44e26eb1910c783788510b0a53b6f4d18881e237e");
		heads.put("REINDEER", "a2df65c6fd19a58bee38252192ac7ce2cf1dc8632c3547a9228b6b697240d098");
		heads.put("RIFT_FERRET", "b6b11399448260185da1d17e54c984515faab6d8585f00972451ec2b43d46f94");
		heads.put("FRACTURED_MONTEZUMA_SOUL", "df656c06e8a5cb4692564ee21748bddec9d785d1834284aaa1439601bba47d6b");
		heads.put("EERIE", "c3af70c6ff76ba48f24ee8a2063a5b50bbfabf409f4795248a292f8289f47c98");
		heads.put("SLUG", "7a79d0fd677b54530961117ef84adc206e2cc5045c1344d61d776bf8ac2fe1ba");
		heads.put("OWL", "da3216da54e7368fb40b721239ad95e07ef4f97d93f1c42ff319bab9a53882af");
		heads.put("TYRANNOSAURUS", "93f28ec96df59c67e9d2fc2e7e3d055fa31646e4111add9fe26a692801964126");
		heads.put("SPINOSAURUS", "d3c9d479471a2f13f22548315159591720992e70c920fef83a901b7186720e3c");
		heads.put("GOBLIN", "7309d8dc35a638a04b915a3b15a1452ceeae0d7ea42bcdadb21b03046987515c");
		heads.put("ANKYLOSAURUS", "c1aa836b9096c417903299a6c5ab41738c19648ac439fed4bcbe6c32605338dc");
		heads.put("PENGUIN", "37534e97f36e5a8335928e171ec99608bee7fb16e260afb301025b3b17eeefc4");
		heads.put("MAMMOTH", "6b10715732cd1fd49fa1b6187947c307dd4687105cf033840607f9d6234743ad");
		heads.put("MOLE", "727baaafc09978d4bda73e16afdde85ec13b0f95ad989524c5fcaa717cf06b4a");
		heads.put("GLACITE_GOLEM", "af132a6593876d3c377d503fd66eca3fb938743251f7b16a9870c60b7388c8a3");
		return Map.copyOf(heads);
	}

	private static int tierColor(String tier) {
		return switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
			case "uncommon" -> 0xFF55FF55;
			case "rare" -> 0xFF5555FF;
			case "epic" -> 0xFFAA00AA;
			case "legendary" -> 0xFFFFAA00;
			case "mythic" -> 0xFFFF55FF;
			default -> Theme.TEXT;
		};
	}

	private static String clock(int ms) {
		if (ms <= 0) {
			return "—";
		}
		int total = ms / 1000;
		int minutes = total / 60;
		int seconds = total % 60;
		return minutes + ":" + (seconds < 10 ? "0" + seconds : String.valueOf(seconds));
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
