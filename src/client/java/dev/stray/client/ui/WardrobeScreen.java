package dev.stray.client.ui;

import dev.stray.client.StrayClient;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.item.WardrobeMenus;
import dev.stray.client.mixin.AbstractContainerScreenInvoker;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.PlayerPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Stray chrome for Hypixel wardrobe ({@code (1/3) Armor Sets}).
 * One 3D armor model per slot — clicks go through the real chest's {@code slotClicked}.
 */
public class WardrobeScreen extends Screen {
	private static final float MENU_W = 480;
	private static final float MENU_H = 300;
	private static WardrobeMenus.Snapshot cache = WardrobeMenus.Snapshot.empty();
	private static final List<QueuedClick> QUEUE = new ArrayList<>();
	private static final long SUPPRESS_NS = 3_000_000_000L;
	private static boolean silentFlush;
	private static boolean cancelIncoming;
	private static boolean skipCustomThisOpen;
	private static long suppressUntil;

	private AbstractContainerScreen<?> vanilla;
	private AbstractContainerMenu menu;
	private final List<Hit> hits = new ArrayList<>();
	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;
	private float viewScale = 1f;
	private float viewCx;
	private float viewCy;
	private float viewLift;
	private boolean placed;
	private boolean closingMenu;
	private boolean attaching;
	private boolean handingOff;
	private long lastNs = System.nanoTime();
	private float dt = 0.016f;
	private float appear;
	private ItemStack tooltip = ItemStack.EMPTY;
	private WardrobeMenus.Snapshot snapshot = WardrobeMenus.Snapshot.empty();
	private float yaw = -22f;
	private boolean rotating;
	private int hoverIndex = -1;
	private int focusIndex = -1;
	private int shownIndex;
	private float dockTop;

	public WardrobeScreen(AbstractContainerScreen<?> vanilla) {
		super(vanilla.getTitle());
		this.vanilla = vanilla;
		this.menu = vanilla.getMenu();
	}

	private WardrobeScreen(WardrobeMenus.Snapshot cached) {
		super(Component.literal(cached.title().isBlank() ? "Armor Sets" : cached.title()));
		this.vanilla = null;
		this.menu = null;
		this.snapshot = cached;
	}

	public static Screen wrap(Screen screen) {
		if (screen instanceof WardrobeScreen || screen instanceof LoadoutsScreen) {
			return screen;
		}
		if (!(screen instanceof AbstractContainerScreen<?> chest)
			|| !WardrobeMenus.enabled()
			|| !WardrobeMenus.matches(chest.getMenu(), chest.getTitle())) {
			return screen;
		}
		if (skipCustomThisOpen) {
			return screen;
		}
		if (shouldDiscardIncoming()) {
			return discardIncoming(chest);
		}
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() instanceof WardrobeScreen existing) {
			existing.attach(chest);
			return existing;
		}
		return new WardrobeScreen(chest);
	}

	public static boolean hasCache() {
		return cache.hasSets();
	}

	public static WardrobeScreen fromCache() {
		allowReopen();
		return new WardrobeScreen(cache.copy());
	}

	public static void allowReopen() {
		suppressUntil = 0L;
		cancelIncoming = false;
		skipCustomThisOpen = false;
	}

	public static void resetPending() {
		QUEUE.clear();
		silentFlush = false;
		cancelIncoming = false;
		suppressUntil = 0L;
		skipCustomThisOpen = false;
	}

	public static void tickSwap(Minecraft client) {
		if (client == null) {
			return;
		}
		if (skipCustomThisOpen) {
			if (client.gui.screen() instanceof WardrobeScreen wardrobe) {
				wardrobe.followServer();
				return;
			}
			boolean vanillaChest = client.gui.screen() instanceof AbstractContainerScreen<?> chest
				&& WardrobeMenus.matches(chest.getMenu(), chest.getTitle());
			if (!vanillaChest) {
				skipCustomThisOpen = false;
			}
			return;
		}
		if (shouldDiscardIncoming()
			&& client.gui.screen() instanceof AbstractContainerScreen<?> chest
			&& WardrobeMenus.matches(chest.getMenu(), chest.getTitle())) {
			discardIncoming(chest);
			client.gui.setScreen(null);
			return;
		}
		if (client.gui.screen() instanceof WardrobeScreen wardrobe) {
			wardrobe.followServer();
			return;
		}
		if (client.gui.screen() instanceof LoadoutsScreen || !WardrobeMenus.enabled() || shouldDiscardIncoming()) {
			return;
		}
		if (client.gui.screen() instanceof AbstractContainerScreen<?> chest
			&& WardrobeMenus.matches(chest.getMenu(), chest.getTitle())) {
			client.gui.setScreen(new WardrobeScreen(chest));
		}
	}

	private void attach(AbstractContainerScreen<?> chest) {
		attaching = true;
		AbstractContainerScreen<?> prior = this.vanilla;
		this.vanilla = chest;
		this.menu = chest.getMenu();
		if (prior != null && prior != chest) {
			prior.removed();
		}
	}

	private void followServer() {
		if (minecraft == null || minecraft.player == null || vanilla == null || menu == null) {
			return;
		}
		if (minecraft.player.containerMenu == menu) {
			return;
		}
		if (minecraft.player.containerMenu == minecraft.player.inventoryMenu) {
			closingMenu = true;
			rememberCache();
			suppressReopen();
			minecraft.gui.setScreen(null);
		}
	}

	@Override
	protected void init() {
		super.init();
		Theme.refresh();
		if (vanilla != null) {
			vanilla.init(width, height);
		}
		if (menu != null) {
			snapshot = WardrobeMenus.read(menu, vanilla != null ? vanilla.getTitle() : getTitle());
			rememberCache();
		}
		if (attaching) {
			attaching = false;
			flushQueue();
			return;
		}
		placed = false;
		appear = StrayConfig.get().loadoutsOpenAnim ? 0.08f : 1f;
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		if (vanilla != null) {
			vanilla.resize(width, height);
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
		tooltip = ItemStack.EMPTY;
		if (menu != null) {
			snapshot = WardrobeMenus.read(menu, vanilla != null ? vanilla.getTitle() : getTitle());
		}
		Font font = minecraft.font;
		layout();

		GuiDraw.fill(graphics, 0, 0, width, height, Anim.fade(0x30000000, appear));

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

		StageUi.window(graphics, windowX, windowY, windowW, windowH, appear);
		hits.add(new Hit(windowX, windowY, windowW, windowH, -1, false, false));
		hoverIndex = -1;
		drawDock(graphics, font, localMx, localMy);
		drawHeader(graphics, font, localMx, localMy);
		drawStage(graphics, font, localMx, localMy);

		graphics.pose().popMatrix();

		if (!tooltip.isEmpty()) {
			graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
		}
	}

	private void drawHeader(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = windowX + 16;
		float y = windowY + 12;
		StageUi.label(graphics, font, "WARDROBE", x, y, StageUi.MUTED);
		String page = pageLabel(snapshot.page(), snapshot.title());
		if (!page.isBlank()) {
			StageUi.label(graphics, font, page, x + StageUi.labelWidth(font, "WARDROBE") + 6, y, StageUi.FAINT);
		}
		float bx = windowX + windowW - 26;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, bx, windowY + 8, 16, 16);
		if (hover) {
			GuiDraw.circle(graphics, bx + 8, windowY + 16, 8, StageUi.CHIP_HOVER);
		}
		GuiDraw.menu(graphics, font, "×", bx + (16 - GuiDraw.menuWidth(font, "×")) * 0.5f, GuiDraw.middle(windowY + 8, 16), hover ? StageUi.TEXT : StageUi.MUTED);
		hits.add(new Hit(bx, windowY + 8, 16, 16, snapshot.close() == null ? -2 : snapshot.close().slot(), false, snapshot.close() == null));
	}

	static String pageLabel(String page, String title) {
		String src = page == null || page.isBlank() ? (title == null ? "" : title) : page;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(src);
		return m.find() ? m.group(1) + " / " + m.group(2) : "";
	}

	private WardrobeMenus.ArmorSet focusSet() {
		List<WardrobeMenus.ArmorSet> sets = snapshot.sets();
		if (sets.isEmpty()) {
			return null;
		}
		int i = hoverIndex >= 0 ? hoverIndex : focusIndex;
		if (i < 0 || i >= sets.size() || sets.get(i) == null || sets.get(i).locked()) {
			i = -1;
			for (int k = 0; k < sets.size(); k++) {
				if (sets.get(k) != null && sets.get(k).selected()) {
					i = k;
					break;
				}
			}
			if (i < 0) {
				i = 0;
			}
		}
		shownIndex = i;
		return sets.get(i);
	}

	private void drawStage(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float top = windowY + 24;
		float bottom = dockTop - 8;
		float modelX = windowX + 18;
		float modelW = 170;
		WardrobeMenus.ArmorSet set = focusSet();
		PlayerPreview.View view = new PlayerPreview.View(viewScale, viewCx, viewCy, viewLift);
		StageUi.floor(graphics, modelX + modelW * 0.5f, PlayerPreview.equippedFeet(top - 4, bottom - top + 6, 78f), 58);
		PlayerPreview.Gear gear = set == null || set.locked()
			? PlayerPreview.Gear.none()
			: new PlayerPreview.Gear(set.helmet(), set.chest(), set.legs(), set.boots());
		PlayerPreview.drawEquipped(graphics, modelX, top - 4, modelW, bottom - top + 6, yaw, 0f, view, gear, 78f);
		hits.add(new Hit(modelX, top, modelW, bottom - top, -1, true, false));

		float x = modelX + modelW + 18;
		float maxW = windowX + windowW - 20 - x;
		float y = top + 4;
		if (set == null) {
			StageUi.label(graphics, font, "No armor sets", x, y, StageUi.MUTED);
			return;
		}
		int number = shownIndex + 1;
		String status = (set.selected() ? "EQUIPPED · " : "") + "SET " + number;
		if (set.selected()) {
			StageUi.dot(graphics, x + 2, y + 3.5f);
			StageUi.label(graphics, font, status, x + 9, y, StageUi.MUTED);
		} else {
			StageUi.label(graphics, font, status, x, y, StageUi.MUTED);
		}
		y += 13;
		StageUi.title(graphics, font, setName(set, number), x, y, maxW);
		y += 17;
		ItemStack[] pieces = {set.helmet(), set.chest(), set.legs(), set.boots()};
		int count = 0;
		for (ItemStack p : pieces) {
			if (p != null && !p.isEmpty()) {
				count++;
			}
		}
		StageUi.label(graphics, font, set.locked() ? "Locked" : count == 0 ? "Empty slot" : count + (count == 1 ? " piece" : " pieces"), x, y, StageUi.FAINT);
		y += 11;
		for (ItemStack stack : pieces) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			float h = 14f;
			Component name = stack.getHoverName();
			boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, maxW, h);
			StageUi.chip(graphics, x, y, maxW, h, hover);
			drawItem(graphics, stack, x + 4, y + 1.5f, 0.6875f);
			float ts = 0.8f;
			String full = name.getString();
			String plain = full;
			while (plain.length() > 3 && font.width(plain) * ts > maxW - 28) {
				plain = plain.substring(0, plain.length() - 2);
			}
			Component shown = plain.equals(full) ? name : Component.literal(plain.trim() + "…").withStyle(name.getStyle());
			GuiDraw.text(graphics, font, shown, x + 20, y + (h - 8f * ts) * 0.5f, ts, StageUi.TEXT, false);
			hits.add(new Hit(x, y, maxW, h, -1, false, false));
			if (hover) {
				tooltip = stack;
			}
			y += h + 2;
		}

		float by = Math.min(bottom - 18, y + 5);
		float bx = x;
		boolean canEquip = !set.locked() && set.slot() >= 0 && set.hasArmor();
		if (canEquip) {
			String key = MenuSlotBinds.keyName(shownIndex);
			boolean hover = GuiDraw.hovered(mouseX, mouseY, bx, by, StageUi.pillWidth(font, set.selected() ? "Unequip" : "Equip", key), 16);
			float w = StageUi.pill(graphics, font, bx, by, set.selected() ? "Unequip" : "Equip", key, true, hover);
			hits.add(new Hit(bx, by, w, 16, set.slot(), false, false));
			bx += w + 6;
		}
		boolean vHover = GuiDraw.hovered(mouseX, mouseY, bx, by, StageUi.pillWidth(font, "Vanilla", null), 16);
		float vw = StageUi.pill(graphics, font, bx, by, "Vanilla", null, false, vHover);
		hits.add(new Hit(bx, by, vw, 16, -1, false, false, true));
	}

	private static Component setName(WardrobeMenus.ArmorSet set, int number) {
		if (set.name() != null && !set.name().isBlank()
			&& !set.name().matches("(?i)^\\s*(slot|set)\\s*\\d+\\b.*")) {
			return Component.literal(set.name());
		}
		if (set.locked()) {
			return Component.literal("Locked slot");
		}
		ItemStack chest = set.chest() != null && !set.chest().isEmpty() ? set.chest() : set.helmet();
		if (chest != null && !chest.isEmpty()) {
			String n = chest.getHoverName().getString()
				.replaceAll("(?i)\\s*(helmet|chestplate|tunic|leggings|pants|boots|shoes|hat|hood|crown|mask)\\b.*$", "")
				.replaceAll("[✪➊➋➌➍➎]+", "").trim();
			if (!n.isBlank()) {
				return Component.literal(n);
			}
		}
		return Component.literal(set.hasArmor() ? "Set " + number : "Empty");
	}

	private void drawDock(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		List<WardrobeMenus.ArmorSet> sets = snapshot.sets();
		int n = Math.max(1, Math.min(9, sets.size()));
		float tile = 26f;
		float gap = 3f;
		float arrow = 12f;
		float pad = 4f;
		float w = pad * 2 + n * tile + (n - 1) * gap + arrow * 2 + gap * 2;
		float h = tile + pad * 2;
		float x = windowX + (windowW - w) * 0.5f;
		float y = windowY + windowH - 12 - h;
		dockTop = y;
		StageUi.dock(graphics, x, y, w, h);
		hits.add(new Hit(x, y, w, h, -1, false, false));
		arrow(graphics, font, x + pad, y + pad, arrow, tile, "‹", snapshot.prev(), mouseX, mouseY);
		arrow(graphics, font, x + w - pad - arrow, y + pad, arrow, tile, "›", snapshot.next(), mouseX, mouseY);
		float tx = x + pad + arrow + gap;
		for (int i = 0; i < n; i++) {
			WardrobeMenus.ArmorSet set = i < sets.size() ? sets.get(i) : null;
			float sx = tx + i * (tile + gap);
			float sy = y + pad;
			boolean hover = GuiDraw.hovered(mouseX, mouseY, sx, sy, tile, tile);
			boolean locked = set == null || set.locked();
			if (hover && !locked) {
				hoverIndex = i;
			}
			boolean shown = i == (hoverIndex >= 0 ? hoverIndex : focusIndexOr(sets));
			StageUi.tile(graphics, sx, sy, tile, shown && !locked, hover);
			GuiDraw.small(graphics, font, String.valueOf(i + 1), sx + 3, sy + 2, StageUi.FAINT);
			if (locked) {
				GuiDraw.small(graphics, font, "—", sx + (tile - GuiDraw.smallWidth(font, "—")) * 0.5f, sy + tile * 0.5f - 3, StageUi.FAINT);
			} else {
				ItemStack[] pieces = {set.helmet(), set.chest(), set.legs(), set.boots()};
				float icon = 9f;
				float gx = sx + (tile - icon * 2 - 1) * 0.5f;
				float gy = sy + (tile - icon * 2 - 1) * 0.5f + 1.5f;
				for (int k = 0; k < 4; k++) {
					ItemStack p = pieces[k];
					if (p != null && !p.isEmpty()) {
						drawItem(graphics, p, gx + (k % 2) * (icon + 1), gy + (k / 2) * (icon + 1), icon / 16f);
					}
				}
			}
			if (set != null && set.selected() && !set.locked()) {
				StageUi.dot(graphics, sx + tile * 0.5f, y + h + 3.5f);
			}
			hits.add(new Hit(sx, sy, tile, tile, -1, false, false, false, locked ? -1 : i));
		}
	}

	private int focusIndexOr(List<WardrobeMenus.ArmorSet> sets) {
		if (focusIndex >= 0 && focusIndex < sets.size()) {
			return focusIndex;
		}
		return shownIndex;
	}

	private void arrow(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		float h,
		String glyph,
		WardrobeMenus.Piece piece,
		int mouseX,
		int mouseY
	) {
		boolean live = piece != null;
		boolean hover = live && GuiDraw.hovered(mouseX, mouseY, x, y, w, h);
		if (hover) {
			GuiDraw.roundedFine(graphics, x, y, w, h, 6f, 0x10FFFFFF);
		}
		GuiDraw.menu(graphics, font, glyph, x + (w - GuiDraw.menuWidth(font, glyph)) * 0.5f, GuiDraw.middle(y, h), live ? (hover ? StageUi.TEXT : StageUi.MUTED) : 0x40FFFFFF);
		if (live) {
			hits.add(new Hit(x, y, w, h, piece.slot(), false, false));
		}
	}

	private void showVanillaMenu() {
		skipCustomThisOpen = true;
		if (vanilla == null || minecraft == null) {
			return;
		}
		handingOff = true;
		minecraft.gui.setScreen(vanilla);
	}

	private void drawItem(GuiGraphicsExtractor graphics, ItemStack stack, float x, float y, float scale) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		LocalPlayer player = minecraft.player;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1f) {
			graphics.pose().scale(scale, scale);
		}
		if (player == null) {
			graphics.item(stack, 0, 0);
		} else {
			graphics.item(player, stack, 0, 0, 1);
		}
		graphics.pose().popMatrix();
	}

	private void layout() {
		windowW = Math.min(MENU_W, Math.max(360, width - 16));
		windowH = Math.min(MENU_H, Math.max(180, height - 16));
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

	private float localX(double mx) {
		return (float) ((mx - viewCx) / viewScale + viewCx);
	}

	private float localY(double my) {
		return (float) ((my - viewCy - viewLift) / viewScale + viewCy);
	}

	private void clickSlot(int slot, int button) {
		if (slot == -2) {
			onClose();
			return;
		}
		if (slot < 0) {
			return;
		}
		if (!attached()) {
			QUEUE.add(new QueuedClick(slot, button));
			return;
		}
		sendClick(vanilla, menu, slot, button);
	}

	private boolean attached() {
		return vanilla != null
			&& menu != null
			&& minecraft != null
			&& minecraft.player != null
			&& minecraft.player.containerMenu == menu;
	}

	private void flushQueue() {
		if (!attached() || QUEUE.isEmpty()) {
			return;
		}
		List<QueuedClick> pending = new ArrayList<>(QUEUE);
		QUEUE.clear();
		for (QueuedClick click : pending) {
			sendClick(vanilla, menu, click.slot, click.button);
		}
	}

	private static void flushAgainst(AbstractContainerScreen<?> chest) {
		if (chest == null || QUEUE.isEmpty()) {
			QUEUE.clear();
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getWindow() != null) {
			chest.init(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
		}
		AbstractContainerMenu target = chest.getMenu();
		List<QueuedClick> pending = new ArrayList<>(QUEUE);
		QUEUE.clear();
		for (QueuedClick click : pending) {
			sendClick(chest, target, click.slot, click.button);
		}
	}

	private static void sendClick(AbstractContainerScreen<?> screen, AbstractContainerMenu target, int slot, int button) {
		if (screen == null || target == null || !target.isValidSlotIndex(slot)) {
			return;
		}
		Slot clicked = target.getSlot(slot);
		((AbstractContainerScreenInvoker) screen).stray$slotClicked(clicked, slot, button, ContainerInput.PICKUP);
	}

	private static void closeIncoming(AbstractContainerScreen<?> chest) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && chest != null && client.player.containerMenu == chest.getMenu()) {
			client.player.closeContainer();
		}
	}

	private static boolean shouldDiscardIncoming() {
		long now = System.nanoTime();
		if (now >= suppressUntil) {
			cancelIncoming = false;
		}
		return silentFlush || cancelIncoming || now < suppressUntil;
	}

	private static void suppressReopen() {
		suppressUntil = System.nanoTime() + SUPPRESS_NS;
	}

	private static Screen discardIncoming(AbstractContainerScreen<?> chest) {
		if (silentFlush) {
			silentFlush = false;
			flushAgainst(chest);
		}
		closeIncoming(chest);
		suppressReopen();
		return null;
	}

	private void rememberCache() {
		if (vanilla != null && !WardrobeMenus.matches(menu, vanilla.getTitle())) {
			return;
		}
		if (snapshot != null && snapshot.hasSets() && WardrobeMenus.matches(snapshot.title())) {
			cache = snapshot.copy();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double lx = localX(event.x());
		double ly = localY(event.y());
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (!hit.contains(lx, ly)) {
				continue;
			}
			if (hit.close) {
				if (MouseButtons.left(event)) {
					onClose();
				}
				return true;
			}
			if (hit.vanillaMenu) {
				if (MouseButtons.left(event)) {
					showVanillaMenu();
				}
				return true;
			}
			if (hit.dock >= 0) {
				if (MouseButtons.left(event)) {
					focusIndex = hit.dock;
				}
				return true;
			}
			if (hit.rotate) {
				rotating = MouseButtons.left(event);
				return true;
			}
			if (hit.slot >= 0 && (MouseButtons.left(event) || MouseButtons.right(event))) {
				clickSlot(hit.slot, MouseButtons.containerButton(event));
				return true;
			}
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (rotating) {
			yaw -= (float) dx / viewScale * 1.4f;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		rotating = false;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (minecraft != null && (minecraft.options.keyInventory.matches(event) || StrayClient.wardrobeKey(event))) {
			onClose();
			return true;
		}
		int index = MenuSlotBinds.index(event.key());
		if (index >= 0) {
			equipAndClose(index);
			return true;
		}
		return super.keyPressed(event);
	}

	private void equipAndClose(int index) {
		if (menu != null) {
			snapshot = WardrobeMenus.read(menu, vanilla != null ? vanilla.getTitle() : getTitle());
		}
		List<WardrobeMenus.ArmorSet> sets = snapshot.sets();
		if (index < 0 || index >= sets.size()) {
			return;
		}
		WardrobeMenus.ArmorSet set = sets.get(index);
		if (set == null || set.slot() < 0 || set.locked()) {
			return;
		}
		queueClickAndClose(set.slot());
	}

	private void queueClickAndClose(int slot) {
		if (slot < 0) {
			return;
		}
		QUEUE.add(new QueuedClick(slot, 0));
		silentFlush = true;
		suppressReopen();
		rememberCache();
		if (vanilla != null && minecraft != null) {
			handingOff = true;
			minecraft.gui.setScreen(vanilla);
			return;
		}
		super.onClose();
	}

	@Override
	public void onClose() {
		if (closingMenu) {
			super.onClose();
			return;
		}
		rememberCache();
		suppressReopen();
		if (vanilla == null || menu == null) {
			if (!QUEUE.isEmpty()) {
				silentFlush = true;
			} else {
				cancelIncoming = true;
			}
			super.onClose();
			return;
		}
		if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu == menu) {
			closingMenu = true;
			minecraft.player.closeContainer();
			super.onClose();
			return;
		}
		super.onClose();
	}

	@Override
	public void removed() {
		if (attaching || handingOff) {
			return;
		}
		rememberCache();
		if (vanilla != null && !closingMenu) {
			vanilla.removed();
		}
	}

	private record QueuedClick(int slot, int button) {
	}

	private static final class Hit {
		final float x, y, w, h;
		final int slot;
		final boolean rotate;
		final boolean close;
		final boolean vanillaMenu;
		final int dock;

		Hit(float x, float y, float w, float h, int slot, boolean rotate, boolean close) {
			this(x, y, w, h, slot, rotate, close, false, -1);
		}

		Hit(float x, float y, float w, float h, int slot, boolean rotate, boolean close, boolean vanillaMenu) {
			this(x, y, w, h, slot, rotate, close, vanillaMenu, -1);
		}

		Hit(float x, float y, float w, float h, int slot, boolean rotate, boolean close, boolean vanillaMenu, int dock) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.slot = slot;
			this.rotate = rotate;
			this.close = close;
			this.vanillaMenu = vanillaMenu;
			this.dock = dock;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
