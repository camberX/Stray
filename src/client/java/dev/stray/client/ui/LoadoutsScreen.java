package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.StrayClient;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.item.LoadoutsMenus;
import dev.stray.client.mixin.AbstractContainerScreenInvoker;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.LoadoutPreview;
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
 * Custom Stray chrome for Hypixel {@code /loadouts}. The real chest stays
 * open; every click is sent through that screen's own {@code slotClicked}.
 */
public class LoadoutsScreen extends Screen {
	private static final float MENU_W = 480;
	private static final float MENU_H = 300;
	private static LoadoutsMenus.Snapshot cache = LoadoutsMenus.Snapshot.empty();
	private static final List<QueuedClick> QUEUE = new ArrayList<>();
	private static final long SUPPRESS_NS = 3_000_000_000L;
	private static boolean silentFlush;
	private static boolean cancelIncoming;
	private static boolean skipCustomThisOpen;
	private static boolean silentSwap;
	private static long suppressUntil;
	private static int pendingEquipIndex = -1;

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
	private LoadoutsMenus.Snapshot snapshot = LoadoutsMenus.Snapshot.empty();
	private float yaw = -22f;
	private boolean rotating;
	private float dockTop;
	private int selectedSlot = -1;
	private int pendingSelectSlot = -1;

	public LoadoutsScreen(AbstractContainerScreen<?> vanilla) {
		super(vanilla.getTitle());
		this.vanilla = vanilla;
		this.menu = vanilla.getMenu();
	}

	private LoadoutsScreen(LoadoutsMenus.Snapshot cached) {
		super(Component.literal(cached.title().isBlank() ? "Loadouts" : cached.title()));
		this.vanilla = null;
		this.menu = null;
		this.snapshot = cached;
		syncSelectedSlot();
	}

	public static Screen wrap(Screen screen) {
		if (screen instanceof LoadoutsScreen || screen instanceof WardrobeScreen) {
			return screen;
		}
		if (!(screen instanceof AbstractContainerScreen<?> chest)
			|| !LoadoutsMenus.enabled()
			|| !LoadoutsMenus.matches(chest.getMenu(), chest.getTitle())) {
			return screen;
		}
		if (skipCustomThisOpen || silentSwap) {
			return screen;
		}
		if (shouldDiscardIncoming()) {
			return discardIncoming(chest);
		}
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() instanceof LoadoutsScreen existing) {
			existing.attach(chest);
			return existing;
		}
		return new LoadoutsScreen(chest);
	}

	public static boolean open() {
		return Minecraft.getInstance().gui.screen() instanceof LoadoutsScreen;
	}

	public static boolean hasCache() {
		return cache.hasItems();
	}

	public static LoadoutsScreen fromCache() {
		allowReopen();
		return new LoadoutsScreen(cache.copy());
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
		silentSwap = false;
		pendingEquipIndex = -1;
	}

	public static int selectedIndex() {
		Minecraft client = Minecraft.getInstance();
		LoadoutsMenus.Snapshot snap = cache;
		if (client.gui.screen() instanceof LoadoutsScreen screen && screen.snapshot != null) {
			snap = screen.snapshot;
		}
		if (snap == null || snap.loadouts() == null) {
			return -1;
		}
		for (int i = 0; i < snap.loadouts().size(); i++) {
			LoadoutsMenus.Piece piece = snap.loadouts().get(i);
			if (piece != null && piece.selected()) {
				return i;
			}
		}
		return -1;
	}

	public static void equipIndex(int index) {
		if (index < 0 || index > 8) {
			return;
		}
		pendingEquipIndex = index;
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() instanceof LoadoutsScreen screen) {
			screen.tryPendingEquip();
			return;
		}
		if (client.gui.screen() instanceof AbstractContainerScreen<?> chest
			&& LoadoutsMenus.matches(chest.getMenu(), chest.getTitle())) {
			flushPendingEquip(client);
			return;
		}
		LoadoutsCommands.open();
	}

	/** Keybind swap: click the chest when it arrives and never show the menu. */
	public static void armHidden(int index) {
		if (index < 0 || index > 8) {
			return;
		}
		pendingEquipIndex = index;
		allowReopen();
		pendingEquipIndex = index;
		silentSwap = true;
		skipCustomThisOpen = true;
	}

	public static boolean hidingSwap() {
		return silentSwap && StrayConfig.get().loadoutHideDefault;
	}

	public static boolean hideDefaultChest(Screen screen) {
		if (!hidingSwap() || !(screen instanceof AbstractContainerScreen<?> chest)) {
			return false;
		}
		return LoadoutsMenus.matches(chest.getMenu(), chest.getTitle());
	}

	public static void cancelSilent() {
		silentSwap = false;
		pendingEquipIndex = -1;
		skipCustomThisOpen = false;
		Minecraft client = Minecraft.getInstance();
		if (client.player != null
			&& client.gui.screen() instanceof AbstractContainerScreen<?> chest
			&& LoadoutsMenus.matches(chest.getMenu(), chest.getTitle())) {
			client.player.closeContainer();
		}
	}

	/**
	 * Clicks the armed loadout before this tick's movement packet.
	 * Returns true once the click has been sent.
	 */
	public static boolean clickSilentNow(Minecraft client) {
		if (!silentSwap || pendingEquipIndex < 0 || client == null || client.player == null) {
			return false;
		}
		AbstractContainerScreen<?> chest = null;
		AbstractContainerMenu menu = null;
		if (client.gui.screen() instanceof LoadoutsScreen screen && screen.vanilla != null && screen.menu != null) {
			chest = screen.vanilla;
			menu = screen.menu;
		} else if (client.gui.screen() instanceof AbstractContainerScreen<?> found
			&& LoadoutsMenus.matches(found.getMenu(), found.getTitle())) {
			chest = found;
			menu = found.getMenu();
		}
		if (chest == null || menu == null || client.player.containerMenu != menu) {
			return false;
		}
		LoadoutsMenus.Snapshot snap = LoadoutsMenus.read(menu, chest.getTitle());
		if (snap.loadouts().size() <= pendingEquipIndex) {
			return false;
		}
		LoadoutsMenus.Piece piece = snap.loadouts().get(pendingEquipIndex);
		if (piece == null || piece.slot() < 0) {
			return false;
		}
		int slot = piece.slot();
		pendingEquipIndex = -1;
		silentSwap = false;
		sendClick(chest, menu, slot, 0);
		if (snap.hasItems()) {
			cache = snap.withSelectedSlot(slot);
		}
		suppressReopen();
		skipCustomThisOpen = true;
		client.player.closeContainer();
		if (client.gui.screen() instanceof LoadoutsScreen) {
			client.gui.setScreen(null);
		}
		return true;
	}

	public static void tickSwap(Minecraft client) {
		if (client == null) {
			return;
		}
		if (!silentSwap) {
			flushPendingEquip(client);
		}
		if (skipCustomThisOpen || silentSwap) {
			if (client.gui.screen() instanceof LoadoutsScreen loadouts) {
				loadouts.followServer();
				return;
			}
			boolean vanillaChest = client.gui.screen() instanceof AbstractContainerScreen<?> chest
				&& LoadoutsMenus.matches(chest.getMenu(), chest.getTitle());
			if (!vanillaChest && !silentSwap) {
				skipCustomThisOpen = false;
			}
			return;
		}
		if (shouldDiscardIncoming()
			&& client.gui.screen() instanceof AbstractContainerScreen<?> chest
			&& LoadoutsMenus.matches(chest.getMenu(), chest.getTitle())) {
			discardIncoming(chest);
			client.gui.setScreen(null);
			return;
		}
		if (client.gui.screen() instanceof LoadoutsScreen loadouts) {
			loadouts.followServer();
			return;
		}
		if (!LoadoutsMenus.enabled() || shouldDiscardIncoming()) {
			return;
		}
		if (client.gui.screen() instanceof AbstractContainerScreen<?> chest
			&& LoadoutsMenus.matches(chest.getMenu(), chest.getTitle())) {
			client.gui.setScreen(new LoadoutsScreen(chest));
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
			adoptMenu();
		}
		if (attaching) {
			attaching = false;
			flushQueue();
			tryPendingEquip();
			return;
		}
		placed = false;
		appear = StrayConfig.get().loadoutsOpenAnim ? 0.08f : 1f;
		tryPendingEquip();
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
		adoptMenu();
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
		StageUi.label(graphics, font, "LOADOUTS", x, y, StageUi.MUTED);
		String page = WardrobeScreen.pageLabel(snapshot.page(), snapshot.title());
		if (!page.isBlank()) {
			StageUi.label(graphics, font, page, x + StageUi.labelWidth(font, "LOADOUTS") + 6, y, StageUi.FAINT);
		}
		float bx = windowX + windowW - 26;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, bx, windowY + 8, 16, 16);
		if (hover) {
			GuiDraw.circle(graphics, bx + 8, windowY + 16, 8, StageUi.CHIP_HOVER);
		}
		GuiDraw.menu(graphics, font, "×", bx + (16 - GuiDraw.menuWidth(font, "×")) * 0.5f, GuiDraw.middle(windowY + 8, 16), hover ? StageUi.TEXT : StageUi.MUTED);
		hits.add(new Hit(bx, windowY + 8, 16, 16, snapshot.close() == null ? -2 : snapshot.close().slot(), false, snapshot.close() == null));
	}

	private int shownIndex() {
		List<LoadoutsMenus.Piece> loadouts = snapshot.loadouts();
		for (int i = 0; i < loadouts.size(); i++) {
			if (loadoutSelected(loadouts.get(i))) {
				return i;
			}
		}
		return -1;
	}

	private void drawStage(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float top = windowY + 24;
		float bottom = dockTop - 8;
		float modelX = windowX + 14;
		float modelW = 150;
		PlayerPreview.View view = new PlayerPreview.View(viewScale, viewCx, viewCy, viewLift);
		StageUi.floor(graphics, modelX + modelW * 0.5f, PlayerPreview.equippedFeet(top - 4, bottom - top + 6, 62f), 54);
		LoadoutPreview.player(
			graphics,
			modelX,
			top - 4,
			modelW,
			bottom - top + 6,
			yaw,
			0f,
			view,
			snapshot.helmet(),
			snapshot.chest(),
			snapshot.legs(),
			snapshot.boots()
		);
		hits.add(new Hit(modelX, top, modelW, bottom - top, -1, true, false));
		ItemStack pet = snapshot.pet();
		if (pet != null && !pet.isEmpty()) {
			float size = 26f;
			float bob = (float) Math.sin(System.currentTimeMillis() / 420.0) * 2.5f;
			float px = modelX + modelW - 30;
			float py = top + 34 + bob;
			drawItem(graphics, pet, px, py, size / 16f);
			hits.add(new Hit(px, py, size, size, -1, false, false));
			if (GuiDraw.hovered(mouseX, mouseY, px, py, size, size)) {
				tooltip = pet;
			}
		}

		float x = modelX + modelW + 18;
		float maxW = windowX + windowW - 20 - x;
		float y = top + 4;
		int index = shownIndex();
		LoadoutsMenus.Piece current = index >= 0 ? snapshot.loadouts().get(index) : null;
		if (current == null && !snapshot.hasItems()) {
			StageUi.label(graphics, font, "No loadouts", x, y, StageUi.MUTED);
			return;
		}
		String status = index >= 0 ? "EQUIPPED · LOADOUT " + (index + 1) : "LOADOUT";
		if (index >= 0) {
			StageUi.dot(graphics, x + 2, y + 3.5f);
			StageUi.label(graphics, font, status, x + 9, y, StageUi.MUTED);
		} else {
			StageUi.label(graphics, font, status, x, y, StageUi.MUTED);
		}
		y += 13;
		StageUi.title(graphics, font, loadoutName(current, index), x, y, maxW);
		y += 17;
		Component petName = snapshot.petName();
		if (petName != null && !petName.getString().isBlank()) {
			GuiDraw.text(graphics, font, petName, x, y, 0.8f, StageUi.TEXT, false);
		} else {
			StageUi.label(graphics, font, "No pet", x, y, StageUi.FAINT);
		}
		y += 12;

		float well = 18f;
		float gap = 4f;
		float cx = x;
		LoadoutsMenus.Kind lastGroup = null;
		for (LoadoutsMenus.Piece piece : labeledContents()) {
			LoadoutsMenus.Kind group = groupOf(piece.kind());
			if (lastGroup != null && group != lastGroup) {
				cx = x;
				y += well + gap + 2;
			}
			lastGroup = group;
			if (cx + well > x + maxW) {
				cx = x;
				y += well + gap;
			}
			if (y + well > bottom - 26) {
				break;
			}
			boolean hover = GuiDraw.hovered(mouseX, mouseY, cx, y, well, well);
			StageUi.well(graphics, cx, y, well, hover);
			drawItem(graphics, piece.stack(), cx + 1, y + 1, 1f);
			hits.add(new Hit(cx, y, well, well, piece.slot(), false, false));
			if (hover) {
				tooltip = piece.stack();
			}
			cx += well + gap;
		}

		float by = Math.min(bottom - 18, y + well + 8);
		float bx = x;
		if (current != null) {
			String key = MenuSlotBinds.keyName(index);
			boolean hover = GuiDraw.hovered(mouseX, mouseY, bx, by, StageUi.pillWidth(font, "Equip", key), 16);
			float w = StageUi.pill(graphics, font, bx, by, "Equip", key, true, hover);
			hits.add(new Hit(bx, by, w, 16, -1, false, false, false, index));
			bx += w + 6;
		}
		boolean vHover = GuiDraw.hovered(mouseX, mouseY, bx, by, StageUi.pillWidth(font, "Vanilla", null), 16);
		float vw = StageUi.pill(graphics, font, bx, by, "Vanilla", null, false, vHover);
		hits.add(new Hit(bx, by, vw, 16, -1, false, false, true));
	}

	private static LoadoutsMenus.Kind groupOf(LoadoutsMenus.Kind kind) {
		return switch (kind) {
			case HELMET, CHEST, LEGS, BOOTS -> LoadoutsMenus.Kind.HELMET;
			case NECKLACE, CLOAK, BELT, GLOVES -> LoadoutsMenus.Kind.NECKLACE;
			default -> LoadoutsMenus.Kind.OTHER;
		};
	}

	private static Component loadoutName(LoadoutsMenus.Piece piece, int index) {
		if (piece != null && piece.name() != null && !piece.name().isBlank()) {
			String n = piece.name().replaceFirst("(?i)^\\s*loadout\\s*\\d+\\s*[:\\-–]\\s*", "").trim();
			return Component.literal(n.isBlank() ? piece.name() : n);
		}
		if (piece != null && !piece.stack().isEmpty()) {
			return Component.literal(piece.stack().getHoverName().getString());
		}
		return Component.literal(index >= 0 ? "Loadout " + (index + 1) : "Loadout");
	}

	private void drawDock(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		List<LoadoutsMenus.Piece> loadouts = snapshot.loadouts();
		int n = Math.max(1, Math.min(9, loadouts.size()));
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
			LoadoutsMenus.Piece piece = i < loadouts.size() ? loadouts.get(i) : null;
			float sx = tx + i * (tile + gap);
			float sy = y + pad;
			boolean hover = GuiDraw.hovered(mouseX, mouseY, sx, sy, tile, tile);
			boolean selected = loadoutSelected(piece);
			StageUi.tile(graphics, sx, sy, tile, selected, hover);
			GuiDraw.small(graphics, font, String.valueOf(i + 1), sx + 3, sy + 2, StageUi.FAINT);
			if (piece != null && !piece.stack().isEmpty()) {
				drawItem(graphics, piece.stack(), sx + 5, sy + 6, 1f);
			}
			if (selected) {
				StageUi.dot(graphics, sx + tile * 0.5f, y + h + 3.5f);
			}
			hits.add(new Hit(sx, sy, tile, tile, piece == null ? -1 : piece.slot(), false, false));
			if (hover && piece != null && !piece.stack().isEmpty()) {
				tooltip = piece.stack();
			}
		}
	}

	private void arrow(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		float h,
		String glyph,
		LoadoutsMenus.Piece piece,
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

	private List<LoadoutsMenus.Piece> labeledContents() {
		List<LoadoutsMenus.Piece> out = new ArrayList<>();
		LoadoutsMenus.Kind[] order = {
			LoadoutsMenus.Kind.HELMET,
			LoadoutsMenus.Kind.CHEST,
			LoadoutsMenus.Kind.LEGS,
			LoadoutsMenus.Kind.BOOTS,
			LoadoutsMenus.Kind.NECKLACE,
			LoadoutsMenus.Kind.CLOAK,
			LoadoutsMenus.Kind.BELT,
			LoadoutsMenus.Kind.GLOVES,
			LoadoutsMenus.Kind.PET,
			LoadoutsMenus.Kind.POWER,
			LoadoutsMenus.Kind.TUNING,
			LoadoutsMenus.Kind.HOTM,
			LoadoutsMenus.Kind.HOTF,
			LoadoutsMenus.Kind.OTHER
		};
		for (LoadoutsMenus.Kind kind : order) {
			for (LoadoutsMenus.Piece piece : snapshot.contents()) {
				if (piece.kind() == kind && piece.stack() != null && !piece.stack().isEmpty() && !LoadoutsMenus.isFiller(piece.stack())) {
					out.add(piece);
				}
			}
		}
		return out;
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
		windowW = Math.min(MENU_W, Math.max(280, width - 16));
		windowH = Math.min(MENU_H, Math.max(200, height - 16));
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

	private void adoptMenu() {
		if (menu == null) {
			return;
		}
		LoadoutsMenus.Snapshot next = LoadoutsMenus.read(menu, vanilla != null ? vanilla.getTitle() : getTitle());
		if (next.hasItems() || !snapshot.hasItems()) {
			snapshot = next;
			syncSelectedSlot();
		}
		rememberCache();
		tryPendingEquip();
	}

	private void tryPendingEquip() {
		if (pendingEquipIndex < 0) {
			return;
		}
		if (snapshot.loadouts().size() <= pendingEquipIndex) {
			return;
		}
		int index = pendingEquipIndex;
		pendingEquipIndex = -1;
		equipLoadout(index);
	}

	private static void flushPendingEquip(Minecraft client) {
		if (pendingEquipIndex < 0 || client.gui.screen() == null) {
			return;
		}
		if (client.gui.screen() instanceof LoadoutsScreen screen) {
			screen.tryPendingEquip();
			return;
		}
		if (LoadoutsMenus.enabled()) {
			return;
		}
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> chest)) {
			return;
		}
		if (!LoadoutsMenus.matches(chest.getMenu(), chest.getTitle())) {
			return;
		}
		LoadoutsMenus.Snapshot snap = LoadoutsMenus.read(chest.getMenu(), chest.getTitle());
		if (pendingEquipIndex >= snap.loadouts().size()) {
			return;
		}
		LoadoutsMenus.Piece piece = snap.loadouts().get(pendingEquipIndex);
		if (piece == null || piece.slot() < 0) {
			return;
		}
		int slot = piece.slot();
		pendingEquipIndex = -1;
		sendClick(chest, chest.getMenu(), slot, 0);
		if (client.player != null) {
			client.player.closeContainer();
		}
	}

	private void rememberCache() {
		if (vanilla != null && !LoadoutsMenus.matches(menu, vanilla.getTitle())) {
			return;
		}
		if (snapshot != null && snapshot.hasItems() && LoadoutsMenus.matches(snapshot.title())) {
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
			if (hit.equip >= 0) {
				if (MouseButtons.left(event)) {
					equipLoadout(hit.equip);
				}
				return true;
			}
			if (hit.rotate) {
				rotating = MouseButtons.left(event);
				return true;
			}
			if (hit.slot >= 0 && (MouseButtons.left(event) || MouseButtons.right(event))) {
				if (MouseButtons.left(event)) {
					markSelected(hit.slot);
				}
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
		if (minecraft != null && (minecraft.options.keyInventory.matches(event) || StrayClient.loadoutsKey(event))) {
			onClose();
			return true;
		}
		int index = MenuSlotBinds.index(event.key());
		if (index >= 0) {
			equipLoadout(index);
			return true;
		}
		return super.keyPressed(event);
	}

	private void equipLoadout(int index) {
		LoadoutsMenus.Piece piece = loadoutAt(index);
		if (piece == null) {
			return;
		}
		markSelected(piece.slot());
		queueClickAndClose(piece.slot());
	}

	private LoadoutsMenus.Piece loadoutAt(int index) {
		adoptMenu();
		List<LoadoutsMenus.Piece> loadouts = snapshot.loadouts();
		if (index < 0 || index >= loadouts.size()) {
			return null;
		}
		LoadoutsMenus.Piece piece = loadouts.get(index);
		if (piece == null || piece.slot() < 0) {
			return null;
		}
		return piece;
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

	private boolean loadoutSelected(LoadoutsMenus.Piece piece) {
		if (piece == null) {
			return false;
		}
		if (selectedSlot >= 0) {
			return piece.slot() == selectedSlot;
		}
		return piece.selected();
	}

	private boolean isLoadoutSlot(int slot) {
		for (LoadoutsMenus.Piece piece : snapshot.loadouts()) {
			if (piece != null && piece.slot() == slot) {
				return true;
			}
		}
		return false;
	}

	private void markSelected(int slot) {
		if (slot < 0 || !isLoadoutSlot(slot)) {
			return;
		}
		selectedSlot = slot;
		pendingSelectSlot = slot;
		snapshot = snapshot.withSelectedSlot(slot);
		rememberCache();
	}

	private void syncSelectedSlot() {
		if (snapshot == null || snapshot.loadouts() == null) {
			return;
		}
		if (pendingSelectSlot >= 0) {
			selectedSlot = pendingSelectSlot;
			boolean confirmed = false;
			for (LoadoutsMenus.Piece piece : snapshot.loadouts()) {
				if (piece != null && piece.slot() == pendingSelectSlot && piece.selected()) {
					confirmed = true;
					break;
				}
			}
			if (confirmed) {
				pendingSelectSlot = -1;
			} else {
				snapshot = snapshot.withSelectedSlot(pendingSelectSlot);
			}
			return;
		}
		for (LoadoutsMenus.Piece piece : snapshot.loadouts()) {
			if (piece != null && piece.selected()) {
				selectedSlot = piece.slot();
				return;
			}
		}
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
		LoadoutPreview.clear();
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
		final int equip;

		Hit(float x, float y, float w, float h, int slot, boolean rotate, boolean close) {
			this(x, y, w, h, slot, rotate, close, false, -1);
		}

		Hit(float x, float y, float w, float h, int slot, boolean rotate, boolean close, boolean vanillaMenu) {
			this(x, y, w, h, slot, rotate, close, vanillaMenu, -1);
		}

		Hit(float x, float y, float w, float h, int slot, boolean rotate, boolean close, boolean vanillaMenu, int equip) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.slot = slot;
			this.rotate = rotate;
			this.close = close;
			this.vanillaMenu = vanillaMenu;
			this.equip = equip;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
