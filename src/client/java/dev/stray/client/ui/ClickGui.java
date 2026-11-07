package dev.stray.client.ui;

import dev.stray.Stray;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.EntityKind;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.config.UnloadState;
import dev.stray.client.render.ArrayListHud;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.MobCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The click GUI's modules, categories and per-module settings. {@link ShadowMenu} draws the
 * menu itself; this class holds what it shows and the settings widgets it reuses.
 */
public final class ClickGui {
	private static final float CARD_R = 4f;
	static final String[] ORDER = {"Dungeons", "Slayer", "Mining", "Farming", "Foraging", "World", "Entities", "Mobs", "HUD", "Chat", "Menus", "Player", "Keys", "Client"};
	static final int[] BIND_WHICH = dev.stray.Edition.LEGIT
		? new int[] {3, 7, 11, 12, 17}
		: new int[] {3, 4, 13, 5, 7, 11, 12, 14, 16, 17};
	static final String[] BIND_LABEL = dev.stray.Edition.LEGIT
		? new String[] {"Open menu", "Profile", "Chat peek", "Lobby ping", "World editor"}
		: new String[] {"Open menu", "Loadouts", "Swap loadouts", "Wardrobe", "Profile", "Chat peek", "Lobby ping", "Empty bag", "Freecam", "World editor"};
	/** Command list under the key binds. */
	static final String[] COMMAND_LINES = dev.stray.Edition.LEGIT
		? new String[] {"/pv  /profile"}
		: new String[] {"/loadouts  /ld", "/wardrobe  /wd", "/pv  /profile", "/autoclicker add left", ""};

	private static double pointerX;
	private static double pointerY;
	private static String searchQuery = "";
	private static boolean searchFocused;
	private static float searchX;
	private static float searchY;
	private static float searchW;
	private static float searchH;
	private static String mobQuery = "";
	private static boolean mobSearchFocused;
	private static float mobSearchX;
	private static float mobSearchY;
	private static float mobSearchW;
	private static float mobSearchH;

	private ClickGui() {
	}

	static void pointer(double x, double y) {
		pointerX = x;
		pointerY = y;
	}

	static void extract(StrayScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		pointer(mouseX, mouseY);
		ShadowMenu.draw(screen, graphics, font, mouseX, mouseY);
		ShadowMenu.drawPicker(screen, graphics, font);
	}

	static boolean searchFocused() {
		return searchFocused;
	}

	static void focusSearch() {
		searchFocused = true;
		mobSearchFocused = false;
	}

	static void blurSearch() {
		searchFocused = false;
	}

	static void clearSearch() {
		searchQuery = "";
		searchFocused = false;
	}

	static boolean mobSearchFocused() {
		return mobSearchFocused;
	}

	static void focusMobSearch() {
		mobSearchFocused = true;
		searchFocused = false;
	}

	static void blurMobSearch() {
		mobSearchFocused = false;
	}

	static final TextKeys.Field SEARCH_FIELD = new TextKeys.Field("click-search", () -> searchQuery, v -> searchQuery = v, 32);
	static final TextKeys.Field MOB_SEARCH_FIELD = new TextKeys.Field("click-mob-search", () -> mobQuery, v -> mobQuery = v, 32);

	static boolean mobSearchContains(double x, double y) {
		return contains(x, y, mobSearchX, mobSearchY, mobSearchW, mobSearchH);
	}

	static String mobQuery() {
		return mobQuery;
	}

	static void mobSearchBox(float x, float y, float w, float h) {
		mobSearchX = x;
		mobSearchY = y;
		mobSearchW = w;
		mobSearchH = h;
	}

	static boolean searchContains(double x, double y) {
		return contains(x, y, searchX, searchY, searchW, searchH);
	}

	static void rightClick(double x, double y) {
		ShadowMenu.rightClick(x, y);
	}

	static boolean scroll(double x, double y, double wheel) {
		return ShadowMenu.scroll(x, y, wheel);
	}

	public static List<String> enabledLabels() {
		List<String> labels = new ArrayList<>();
		StrayConfig config = StrayConfig.get();
		for (Mod mod : modules()) {
			if (mod.list && mod.on.getAsBoolean() && config.arrayListShows(mod.name)) {
				labels.add(mod.name);
			}
		}
		return labels;
	}

	public static int colorOf(String name) {
		int hash = name.hashCode();
		float hue = ((hash & 0xFFFF) % 360) / 360f;
		int rgb = hsb(hue, 0.62f, 1f);
		return 0xFF000000 | rgb;
	}

	private static void drawArrayList(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		drawChoice(screen, graphics, font, x, y, w, "Accent Color", config.arrayListAccent, () -> {
			StrayConfig.get().arrayListAccent = !StrayConfig.get().arrayListAccent;
			UnloadState.markDirty();
		});
		y += stride();
		for (Mod mod : modules()) {
			if (!mod.list) {
				continue;
			}
			boolean shown = config.arrayListShows(mod.name);
			String name = mod.name;
			drawChoice(screen, graphics, font, x, y, w, fit(font, display(name), Math.round(w) - 4), shown, () -> {
				StrayConfig.get().toggleArrayListShown(name);
				UnloadState.markDirty();
			});
			y += stride();
		}
	}

	private static int arrayListRows() {
		int count = 1;
		for (Mod mod : modules()) {
			if (mod.list) {
				count++;
			}
		}
		return count;
	}

	private static void drawHud(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		drawChoice(screen, graphics, font, x, y, w, "Accent Outlines", config.accentOutlines, () -> {
			StrayConfig current = StrayConfig.get();
			current.accentOutlines = !current.accentOutlines;
			Theme.refresh();
			UnloadState.markDirty();
		});
		y += stride();
		drawButton(screen, graphics, font, x, y, w, "HUD Editor", () -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen()));
	}

	/** Boolean subsetting: the same row and switch the feature panels use. */
	private static void drawChoice(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, boolean on, Runnable pick) {
		screen.clickToggle(graphics, font, x, y, w, label, on, pick);
	}

	/** Action row: label and a chevron, as in the feature panels. */
	private static void drawButton(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, Runnable pick) {
		screen.clickAction(graphics, font, x, y, w, label, pick);
	}

	static final String CUSTOMIZE = "Customize item";
	static final String CS2 = "CS2 knife";
	static final String HUD_LOOK = "Look";
	static final String LEAP = "Leap menu";
	static final String ALIGN = "Align triggerbot";
	static final String TERMINALS = "Terminal GUI";
	static final String TITLES = "F7 titles";
	static final String LEVER = "Lever triggerbot";
	static final String MAP = "Dungeon map";

	/** CS2 knives (local build): pick the knife, finish, seed and wear. */
	private static void drawCs2(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		drawButton(screen, graphics, font, x, y, w, "Choose knife & skin", () -> {
			Minecraft client = Minecraft.getInstance();
			client.gui.setScreen(new Cs2KnifeScreen(client.gui.screen()));
		});
	}

	/** Item customization: open the editor for the held item, or reset it. */
	private static void drawCustomize(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		drawButton(screen, graphics, font, x, y, w, "Edit held item", CustomizeScreen::openForHeld);
		drawButton(screen, graphics, font, x, y + stride(), w, "Reset held item", CustomizeScreen::resetHeld);
	}

	private static final int[] TIMEOUTS = {50, 100, 150, 300, 500, 1000};

	private static void drawTimeout(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		drawChoice(screen, graphics, font, x, y, w, "Skip leap menus", StrayConfig.get().noCursorResetSkipLeap, () -> {
			StrayConfig.get().noCursorResetSkipLeap = !StrayConfig.get().noCursorResetSkipLeap;
			UnloadState.markDirty();
		});
		y += stride();
		int current = StrayConfig.get().noCursorResetTimeout;
		screen.clickCycle(graphics, font, x, y, w, "Timeout", current + "ms", () -> {
			int next = TIMEOUTS[0];
			for (int i = 0; i < TIMEOUTS.length; i++) {
				if (TIMEOUTS[i] == current) {
					next = TIMEOUTS[(i + 1) % TIMEOUTS.length];
				}
			}
			StrayConfig.get().noCursorResetTimeout = next;
			UnloadState.markDirty();
		});
	}

	static void toggle(Mod mod) {
		mod.set.accept(!mod.on.getAsBoolean());
		UnloadState.markDirty();
	}

	private static float stackH(int count) {
		return Math.max(0, count) * stride();
	}

	private static float settingsFull(StrayScreen screen, Mod mod) {
		if ("Array list".equals(mod.name)) {
			return stackH(arrayListRows());
		}
		if (CUSTOMIZE.equals(mod.name)) {
			return stackH(2);
		}
		if (CS2.equals(mod.name)) {
			return stackH(1);
		}
		if (mod.menuStyle) {
			if (HUD_LOOK.equals(mod.name)) {
				return stackH(2);
			}
			return stackH(1);
		}
		if (mod.timeout) {
			return stackH(2);
		}
		if (mod.feature == null) {
			return 0f;
		}
		return mod.feature.rows() * screen.clickSettingRow();
	}

	public static String display(String label) {
		if (label == null || label.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(label.length());
		boolean cap = true;
		for (int i = 0; i < label.length(); i++) {
			char c = label.charAt(i);
			if (Character.isWhitespace(c)) {
				cap = true;
				out.append(c);
				continue;
			}
			if (cap && Character.isLetter(c)) {
				out.append(Character.toUpperCase(c));
			} else {
				out.append(c);
			}
			cap = false;
		}
		return out.toString();
	}

	static String bindKey(int which) {
		StrayConfig config = StrayConfig.get();
		return switch (which) {
			case 3 -> config.openGuiKey;
			case 4 -> config.openLoadoutsKey;
			case 5 -> config.openWardrobeKey;
			case 7 -> config.openProfileKey;
			case 11 -> config.chatPeekKey;
			case 12 -> config.strayPingKey;
			case 13 -> config.loadoutSwapKey;
			case 14 -> config.emptyVacuumKey;
			case 15 -> config.pestPlotKey;
			case 16 -> config.freecamKey;
			case 17 -> config.editorKey;
			default -> "";
		};
	}

	/** Row height of the feature panels, refreshed whenever settings are measured or drawn. */
	private static float rowH = 16f;

	private static float stride() {
		return rowH;
	}

	private static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		card(graphics, x, y, w, h, fill, 1f);
	}

	private static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill, float shine) {
		float r = Math.min(CARD_R, Math.min(w, h) * 0.5f);
		ControlChrome.lens(graphics, x, y, w, h, r, shine);
		if ((fill & 0xFF000000) != 0) {
			GuiDraw.roundedFine(graphics, x, y, w, h, r, fill);
		}
	}

	private static String fit(Font font, String label, int max) {
		if (font.width(label) <= max) {
			return label;
		}
		String trimmed = label;
		while (trimmed.length() > 1 && font.width(trimmed + ".") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + ".";
	}

	private static boolean contains(double x, double y, float rx, float ry, float rw, float rh) {
		return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
	}

	private static int hsb(float hue, float sat, float bri) {
		int sector = (int) (hue * 6f);
		float f = hue * 6f - sector;
		float p = bri * (1f - sat);
		float q = bri * (1f - f * sat);
		float t = bri * (1f - (1f - f) * sat);
		float r;
		float g;
		float b;
		switch (sector % 6) {
			case 0 -> { r = bri; g = t; b = p; }
			case 1 -> { r = q; g = bri; b = p; }
			case 2 -> { r = p; g = bri; b = t; }
			case 3 -> { r = p; g = q; b = bri; }
			case 4 -> { r = t; g = p; b = bri; }
			default -> { r = bri; g = p; b = q; }
		}
		return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
	}

	static String filterText() {
		return searchQuery;
	}

	static void searchBox(float x, float y, float w, float h) {
		searchX = x;
		searchY = y;
		searchW = w;
		searchH = h;
	}

	static double pointerX() {
		return pointerX;
	}

	static double pointerY() {
		return pointerY;
	}

	private static final java.util.Map<String, String> DESCRIPTIONS = java.util.Map.ofEntries(
		java.util.Map.entry("World tint", "Tints the world's lighting in a colour of your choice."),
		java.util.Map.entry("Skybox", "Recolours the sky, or swaps in the End sky."),
		java.util.Map.entry("Ambience", "Sets your own time of day and weather, client side."),
		java.util.Map.entry("Fog", "Custom fog colour and distance."),
		java.util.Map.entry("Aspect", "Stretches the view to another aspect ratio, like 4:3."),
		java.util.Map.entry("Motion blur", "Blurs the screen while you move and turn."),
		java.util.Map.entry("Skull hold", "Holds skulls and heads the 1.8 way."),
		java.util.Map.entry("Backwards walk", "1.8 style body turn when walking backwards."),
		java.util.Map.entry("Player glow", "Outlines other players so you can see them through walls."),
		java.util.Map.entry("Nametags", "Stray's own nametags for players, with distance."),
		java.util.Map.entry("Health bar", "Health bars over players."),
		java.util.Map.entry("2D box", "Flat boxes around players on screen."),
		java.util.Map.entry("Shader", "Fills players with a see-through shader."),
		java.util.Map.entry("Mob glow", "Outlines mobs you pick from the list or by name."),
		java.util.Map.entry("Mob tags", "Stray's own nametags for mobs."),
		java.util.Map.entry("Mob health", "Health bars over mobs."),
		java.util.Map.entry("Mob box", "Flat boxes around mobs on screen."),
		java.util.Map.entry("Mob shader", "Fills mobs with a see-through shader."),
		java.util.Map.entry("Star mobs", "Highlights starred mobs in dungeons."),
		java.util.Map.entry("CS2 knife", "Replaces your sword with a CS2 knife, with skins, inspect and swing animations."),
		java.util.Map.entry("Held item", "A shader on the item in your hand."),
		java.util.Map.entry("Block outline", "A glowing outline on the block you look at."),
		java.util.Map.entry("Fairy souls", "Shows fairy souls you have not collected yet."),
		java.util.Map.entry("Auto clicker", "Clicks for you while you hold the bind."),
		java.util.Map.entry("Party stats", "Shows a stats card when someone joins from the party finder."),
		java.util.Map.entry("Lever triggerbot", "Right clicks a lever once when your crosshair lands on it (after the delay). Look away and back to flip it again."),
		java.util.Map.entry("Terminal GUI", "F7 terminals in a clean Shadow window: only the slots left to click light up, and a click anywhere on one goes to that slot. Try it with /stray debug dev terminal."),
		java.util.Map.entry("Terminal highlight", "F7 phase 3: a glow on the command block of every terminal in your section that is not done yet, with its number above it."),
		java.util.Map.entry("Mask timers", "Bonzo Mask, Spirit Mask and Phoenix Pet cooldowns, always listed with Ready when off cooldown. From NoammAddons (Zyryon style)."),
		java.util.Map.entry("F7 titles", "Short titles in the F7 boss: crystals, wither phases, Storm's lightning timer and who did which terminal. Also shown over the leap menu."),
		java.util.Map.entry("Align triggerbot", "F7 arrow align: while you look at an arrow that is not right yet, it right clicks it with your delay until it is. Clicks go through the normal use key."),
		java.util.Map.entry("Own nametag", "Shows your own name above your head in third person (F5)."),
		java.util.Map.entry("Dungeon map", "NoammAddons' dungeon map in the Shadow look: rooms, doors, checkmarks or names, and where your teammates are. Move and scale it in the HUD editor."),
		java.util.Map.entry("Leap menu", "Spirit Leap as four big panels, optionally showing what each teammate sees. Leap logic from NoammAddons."),
		java.util.Map.entry("Array list", "Lists the modules you have turned on."),
		java.util.Map.entry("Watermark", "The Stray watermark in a corner of the screen."),
		java.util.Map.entry("Music", "What is playing right now, with controls."),
		java.util.Map.entry("Picture in picture", "A small second view of the world."),
		java.util.Map.entry("Raw mats", "Tracks raw materials you gather."),
		java.util.Map.entry("Pickup log", "Shows the items you just picked up."),
		java.util.Map.entry("Skill progress", "Skill XP gains and progress."),
		java.util.Map.entry("Inventory", "Your inventory on the HUD."),
		java.util.Map.entry("Custom title", "Changes the game window's title."),
		java.util.Map.entry("Scoreboard", "Stray's restyled sidebar."),
		java.util.Map.entry("Boss bar", "Stray's restyled boss bar."),
		java.util.Map.entry("Effects", "Your active potion effects."),
		java.util.Map.entry("Item name", "The name of the item you switch to."),
		java.util.Map.entry("Mining HUD", "Mining stats and ability cooldown."),
		java.util.Map.entry("Titanium ESP", "Highlights titanium ore near you."),
		java.util.Map.entry("CH waypoints", "Waypoints for the Crystal Hollows."),
		java.util.Map.entry("Route miner", "Follows a saved mining route."),
		java.util.Map.entry("Powder Mining", "Helps with powder mining chests."),
		java.util.Map.entry("CH map", "A map of the Crystal Hollows."),
		java.util.Map.entry("Scatha timer", "Times Scatha and worm spawns."),
		java.util.Map.entry("Metal detector", "Solves the metal detector in the Mines of Divan."),
		java.util.Map.entry("Robot parts", "Alerts for the Nucleus robot parts."),
		java.util.Map.entry("Divan tools", "Alerts for the Mines of Divan tools."),
		java.util.Map.entry("Nodes", "Marks ore nodes."),
		java.util.Map.entry("Node ESP", "Fills the marked nodes so you can see them through walls."),
		java.util.Map.entry("Top down", "A top-down view of your plot."),
		java.util.Map.entry("Yaw / Pitch", "Shows your yaw and pitch for farming."),
		java.util.Map.entry("Jacob contest", "Your Jacob's contest progress."),
		java.util.Map.entry("Composter", "Composter levels and timers."),
		java.util.Map.entry("Garden plots", "A quick plot overview on the Garden."),
		java.util.Map.entry("Shopping list", "What you need for the Garden visitors."),
		java.util.Map.entry("Next contest", "When the next Jacob's contest starts."),
		java.util.Map.entry("Visitors", "Garden visitors waiting for you."),
		java.util.Map.entry("Hoe level", "Your farming tool's level."),
		java.util.Map.entry("Crop milestone", "Progress to your next crop milestone."),
		java.util.Map.entry("Crop fever", "The Crop Fever timer."),
		java.util.Map.entry("Pest ESP", "Highlights pests on the Garden."),
		java.util.Map.entry("Pest cooldown", "When the next pest can spawn."),
		java.util.Map.entry("Lasso", "Shows the lasso's pull bar and reels in for you."),
		java.util.Map.entry("Auto DNA", "Solves the DNA puzzle."),
		java.util.Map.entry("Hide tree blocks", "Hides the floating blocks when you break a tree on Galatea or in Torrhus Canyon."),
		java.util.Map.entry("Compact sweep", "Folds the Sweep Details messages into one line. Hover for the breakdown."),
		java.util.Map.entry("Compact tree gift", "Folds the Tree Gift message into one line plus its bonus drops."),
		java.util.Map.entry("Experiments", "Solves the experimentation table."),
		java.util.Map.entry("Book combine", "Combines enchanted books for you."),
		java.util.Map.entry("Loadouts", "A cleaner equipment loadouts menu."),
		java.util.Map.entry("Wardrobe", "A cleaner wardrobe menu."),
		java.util.Map.entry("Profile viewer", "Look at anyone's SkyBlock profile."),
		java.util.Map.entry("Storage preview", "Preview storage pages without opening them."),
		java.util.Map.entry("Themed GUIs", "Stray's look for chest menus."),
		java.util.Map.entry("Themed chat", "Stray's look for chat."),
		java.util.Map.entry("Compact stash", "Collapses stash pickup spam into one line."),
		java.util.Map.entry("Cleaner NPC", "Shortens NPC chat lines."),
		java.util.Map.entry("No cursor reset", "Keeps the cursor where it was between menus."),
		java.util.Map.entry("Disabled potions", "Highlights disabled potion effects."),
		java.util.Map.entry("Sack recipe", "Recipes that pull from your sacks."),
		java.util.Map.entry("Global IRC", "Chat with other Stray users."),
		java.util.Map.entry("Lobby pings", "Ping spots for other Stray users in your lobby."),
		java.util.Map.entry("Block marks", "Mark blocks with colours."),
		java.util.Map.entry("Command rings", "Rings on the ground that run a command when you walk in."),
		java.util.Map.entry("Paths", "Record and show walking paths."),
		java.util.Map.entry("Movement rings", "Movement guide rings (unfinished)."),
		java.util.Map.entry("Accent", "The accent colour, and the opacity and blur of every Shadow panel."),
		java.util.Map.entry(HUD_LOOK, "Accent outlines and the HUD editor."),
		java.util.Map.entry("Auto update", "Updates Stray by itself."),
		java.util.Map.entry("Auto close", "Closes the game after updating."),
		java.util.Map.entry("Update notify", "Tells you when an update is out."),
		java.util.Map.entry("Customize item", "Rename and restyle your items, client side."),
		java.util.Map.entry("Nick hider", "Shows a different name for you, client side."),
		java.util.Map.entry("Skin", "A skin only you see, from a PNG on your computer."),
		java.util.Map.entry("Slayer HUD", "Your slayer quest, boss health, timer and phase."),
		java.util.Map.entry("Slayer profit", "What your slayer drops are worth, per boss and per hour."),
		java.util.Map.entry("RNG meter", "Your slayer RNG meter and how many bosses are left."),
		java.util.Map.entry("Remaining kills", "How many mobs are left until your boss spawns."),
		java.util.Map.entry("Slayer alerts", "Boss soon, wrong quest, cocoon, kill times and personal bests."),
		java.util.Map.entry("Slayer ESP", "Highlights your boss, mini-bosses, egg sacs and valuable drops."),
		java.util.Map.entry("Voidgloom", "Beacons, Nukekubi skulls and particle hiding for Voidgloom Seraph."),
		java.util.Map.entry("Inferno", "Hellion shields, daggers, fire pits and fire pillars for Inferno Demonlord."),
		java.util.Map.entry("Bloodfiend", "Boss highlights, steak and twinclaws alerts, ichors and springs."),
		java.util.Map.entry("Slayer cleanup", "Hides clutter while you slay.")
	);

	/** One line on what a module does, shown under its settings. */
	static String description(Mod mod) {
		return mod == null ? "" : DESCRIPTIONS.getOrDefault(mod.name, "");
	}

	static boolean moduleHasSettings(Mod mod) {
		return mod.feature != null
			|| mod.timeout
			|| mod.menuStyle
			|| "Array list".equals(mod.name)
			|| CUSTOMIZE.equals(mod.name)
			|| CS2.equals(mod.name)
			|| "Custom title".equals(mod.name);
	}

	static float moduleSettingsHeight(StrayScreen screen, Mod mod) {
		rowH = screen.clickSettingRow();
		if ("Custom title".equals(mod.name)) {
			return stride();
		}
		return settingsFull(screen, mod);
	}

	static void drawModuleSettings(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		Mod mod,
		float x,
		float y,
		float w
	) {
		rowH = screen.clickSettingRow();
		if ("Custom title".equals(mod.name)) {
			drawButton(screen, graphics, font, x, y, w, "Edit title", () -> {
				Minecraft client = Minecraft.getInstance();
				if (client != null) {
					client.gui.setScreen(new CustomTitleScreen(client.gui.screen()));
				}
			});
			return;
		}
		if ("Array list".equals(mod.name)) {
			drawArrayList(screen, graphics, font, x, y, w);
			return;
		}
		if (CUSTOMIZE.equals(mod.name)) {
			drawCustomize(screen, graphics, font, x, y, w);
			return;
		}
		if (CS2.equals(mod.name)) {
			drawCs2(screen, graphics, font, x, y, w);
			return;
		}
		if (mod.menuStyle) {
			drawHud(screen, graphics, font, x, y, w);
			return;
		}
		if (mod.timeout) {
			drawTimeout(screen, graphics, font, x, y, w);
			return;
		}
		if (mod.feature != null) {
			screen.clickVisuals(mod.kind);
			screen.clickSettings(graphics, font, mouseX, mouseY, x, y, w, mod.feature);
		}
	}

	/** Modules Stray Legit leaves out: they automate input or are the loadouts / wardrobe menus. */
	private static final java.util.Set<String> LEGIT_REMOVED = java.util.Set.of(
		"Auto clicker", "Experiments", "Book combine", "Loadouts", "Wardrobe", "Auto DNA",
		"Command rings", "Movement rings", "Align triggerbot", "Terminal GUI", "Lever triggerbot"
	);

	static List<Mod> modules() {
		List<Mod> all = allModules();
		if (!dev.stray.Edition.LEGIT) {
			return all;
		}
		List<Mod> kept = new ArrayList<>(all.size());
		for (Mod mod : all) {
			if (!LEGIT_REMOVED.contains(mod.name)) {
				kept.add(mod);
			}
		}
		return kept;
	}

	private static List<Mod> allModules() {
		StrayConfig config = StrayConfig.get();
		List<Mod> mods = new ArrayList<>();
		mods.add(mod(LEAP, "Dungeons", StrayScreen.Feature.LEAP_MENU, null, () -> config.leapMenuEnabled, v -> config.leapMenuEnabled = v, true, false));
		mods.add(mod(TERMINALS, "Dungeons", StrayScreen.Feature.TERMINALS, null, () -> config.terminalGuiEnabled, v -> config.terminalGuiEnabled = v, true, false));
		mods.add(mod(MAP, "Dungeons", StrayScreen.Feature.DUNGEON_MAP, null, () -> config.dungeonMapEnabled, v -> config.dungeonMapEnabled = v, true, false));
		mods.add(mod(TITLES, "Dungeons", StrayScreen.Feature.F7_TITLES, null, () -> config.f7TitlesEnabled, v -> config.f7TitlesEnabled = v, true, false));
		mods.add(mod("Terminal highlight", "Dungeons", StrayScreen.Feature.TERM_HIGHLIGHT, null, () -> config.terminalHighlight, v -> config.terminalHighlight = v, true, false));
		mods.add(mod("Mask timers", "Dungeons", StrayScreen.Feature.MASK_TIMERS, null, () -> config.maskTimers, v -> config.maskTimers = v, true, false));
		mods.add(mod(ALIGN, "Dungeons", StrayScreen.Feature.ALIGN_BOT, null, () -> config.alignTriggerbot, v -> config.alignTriggerbot = v, true, false));
		mods.add(mod(LEVER, "Dungeons", StrayScreen.Feature.LEVER_BOT, null, () -> config.leverTriggerbot, v -> config.leverTriggerbot = v, true, false));
		mods.add(mod("Star mobs", "Dungeons", StrayScreen.Feature.STAR, EntityKind.STAR, () -> config.starVisuals.glowEnabled, v -> {
			config.starVisuals.glowEnabled = v;
			config.starMobEsp = v;
		}, true, false));
		mods.add(mod("Party stats", "Dungeons", null, null, () -> config.partyFinderStats, v -> config.partyFinderStats = v, true, false));

		addSlayer(mods, config);

		mods.add(mod("Mining HUD", "Mining", StrayScreen.Feature.MINING, null, () -> config.miningHudEnabled, v -> config.miningHudEnabled = v, true, false));
		mods.add(mod("Titanium ESP", "Mining", StrayScreen.Feature.TITANIUM, null, () -> config.titaniumEsp, v -> config.titaniumEsp = v, true, false));
		mods.add(mod("CH waypoints", "Mining", StrayScreen.Feature.CRYSTAL, null, () -> config.crystalHollowsWaypoints, v -> config.crystalHollowsWaypoints = v, true, false));
		mods.add(mod("Route miner", "Mining", StrayScreen.Feature.ROUTE, null, () -> config.routeMiner, v -> config.routeMiner = v, true, false));
		mods.add(mod("Powder Mining", "Mining", StrayScreen.Feature.CHEST, null, () -> config.powderMiningEnabled, v -> config.powderMiningEnabled = v, true, false));
		mods.add(mod("CH map", "Mining", StrayScreen.Feature.CH_MAP, null, () -> config.crystalHollowsMap, v -> config.crystalHollowsMap = v, true, false));
		mods.add(mod("Scatha timer", "Mining", null, null, () -> config.scathaHudEnabled, v -> config.scathaHudEnabled = v, true, false));
		mods.add(mod("Metal detector", "Mining", StrayScreen.Feature.METAL, null, () -> config.metalDetectorSolver, v -> config.metalDetectorSolver = v, true, false));
		mods.add(mod("Robot parts", "Mining", StrayScreen.Feature.NUCLEUS, null, () -> config.nucleusAlertParts, v -> config.nucleusAlertParts = v, true, false));
		mods.add(mod("Divan tools", "Mining", null, null, () -> config.nucleusAlertTools, v -> config.nucleusAlertTools = v, true, false));
		mods.add(mod("Nodes", "Mining", StrayScreen.Feature.NODES, null, () -> config.markersEnabled, v -> config.markersEnabled = v, true, false));
		mods.add(mod("Node ESP", "Mining", StrayScreen.Feature.NODE_ESP, null, () -> config.boxFill, v -> config.boxFill = v, true, false));

		mods.add(mod("Top down", "Farming", StrayScreen.Feature.TOP_DOWN, null, () -> config.topDownView, v -> config.topDownView = v, true, false));
		mods.add(mod("Yaw / Pitch", "Farming", StrayScreen.Feature.FARMING, null, () -> config.farmingYawPitch, v -> config.farmingYawPitch = v, true, false));
		mods.add(mod("Jacob contest", "Farming", null, null, () -> config.jacobContestHudEnabled, v -> config.jacobContestHudEnabled = v, true, false));
		mods.add(mod("Composter", "Farming", null, null, () -> config.composterHudEnabled, v -> config.composterHudEnabled = v, true, false));
		mods.add(mod("Garden plots", "Farming", StrayScreen.Feature.PLOTS, null, () -> config.gardenPlotsWidget, v -> config.gardenPlotsWidget = v, true, false));
		mods.add(mod("Shopping list", "Farming", StrayScreen.Feature.SHOPPING, null, () -> config.gardenShoppingHudEnabled, v -> config.gardenShoppingHudEnabled = v, true, false));
		mods.add(mod("Next contest", "Farming", null, null, () -> config.gardenContestHudEnabled, v -> config.gardenContestHudEnabled = v, true, false));
		mods.add(mod("Visitors", "Farming", null, null, () -> config.gardenVisitorHudEnabled, v -> config.gardenVisitorHudEnabled = v, true, false));
		mods.add(mod("Hoe level", "Farming", null, null, () -> config.gardenHoeHudEnabled, v -> config.gardenHoeHudEnabled = v, true, false));
		mods.add(mod("Crop milestone", "Farming", null, null, () -> config.gardenMilestoneHudEnabled, v -> config.gardenMilestoneHudEnabled = v, true, false));
		mods.add(mod("Crop fever", "Farming", null, null, () -> config.cropFeverHudEnabled, v -> config.cropFeverHudEnabled = v, true, false));
		mods.add(mod("Pest ESP", "Farming", StrayScreen.Feature.PEST, null, () -> config.pestEspEnabled, v -> config.pestEspEnabled = v, true, false));
		mods.add(mod("Pest cooldown", "Farming", StrayScreen.Feature.PEST_COOLDOWN, null, () -> config.pestCooldownHudEnabled, v -> config.pestCooldownHudEnabled = v, true, false));
		mods.add(mod("Lasso", "Farming", null, null, () -> config.lassoDisplay, v -> config.lassoDisplay = v, true, false));
		mods.add(mod("Auto DNA", "Farming", StrayScreen.Feature.AUTO_DNA, null, () -> config.autoDnaEnabled, v -> config.autoDnaEnabled = v, true, false));

		mods.add(mod("Hide tree blocks", "Foraging", null, null, () -> config.foragingHideTreeBlocks, v -> config.foragingHideTreeBlocks = v, true, false));
		mods.add(mod("Compact sweep", "Foraging", null, null, () -> config.foragingCompactSweep, v -> config.foragingCompactSweep = v, true, false));
		mods.add(mod("Compact tree gift", "Foraging", null, null, () -> config.foragingCompactTreeGift, v -> config.foragingCompactTreeGift = v, true, false));

		mods.add(mod("World tint", "World", StrayScreen.Feature.WORLD, null, () -> config.worldTintEnabled, v -> config.worldTintEnabled = v, true, false));
		mods.add(mod("Skybox", "World", StrayScreen.Feature.SKY, null, () -> config.skyTintEnabled, v -> config.skyTintEnabled = v, true, false));
		mods.add(mod("Ambience", "World", StrayScreen.Feature.AMBIENCE, null, () -> config.ambienceEnabled, v -> config.ambienceEnabled = v, true, false));
		mods.add(mod("Fog", "World", StrayScreen.Feature.FOG, null, () -> config.fogEnabled, v -> config.fogEnabled = v, true, false));
		mods.add(mod("Aspect", "World", StrayScreen.Feature.VIEW, null, () -> config.aspectEnabled, v -> config.aspectEnabled = v, true, false));
		mods.add(mod("Motion blur", "World", StrayScreen.Feature.MOTION, null, () -> config.motionBlurEnabled, v -> config.motionBlurEnabled = v, true, false));
		mods.add(mod("Block outline", "World", StrayScreen.Feature.BLOCK, null, () -> config.blockOutlineGlow, v -> config.blockOutlineGlow = v, true, false));
		mods.add(mod("Held item", "World", StrayScreen.Feature.HELD_ITEM, null, () -> config.heldItemShaderEnabled, v -> config.heldItemShaderEnabled = v, true, false));
		mods.add(mod(CS2, "World", null, null, () -> config.cs2KnifeEnabled, v -> config.cs2KnifeEnabled = v, true, false));
		mods.add(mod("Skull hold", "World", null, null, () -> config.legacySkullHold, v -> config.legacySkullHold = v, true, false));
		mods.add(mod("Backwards walk", "World", null, null, () -> config.legacyBackwardsWalk, v -> config.legacyBackwardsWalk = v, true, false));
		mods.add(mod("Fairy souls", "World", dev.stray.Edition.LEGIT ? null : StrayScreen.Feature.FAIRY, null, () -> config.fairySoulEsp, v -> config.fairySoulEsp = v, true, false));
		mods.add(mod("Block marks", "World", StrayScreen.Feature.MARKS, null, () -> config.blockMarksEnabled, v -> config.blockMarksEnabled = v, true, false));
		mods.add(mod("Paths", "World", StrayScreen.Feature.PATHS, null, () -> config.pathsEnabled, v -> config.pathsEnabled = v, true, false));
		mods.add(mod("Command rings", "World", StrayScreen.Feature.RINGS, null, () -> config.commandRingsEnabled, v -> config.commandRingsEnabled = v, true, false));

		mods.add(mod("Player glow", "Entities", StrayScreen.Feature.MOB, EntityKind.PLAYER, () -> config.playerVisuals.glowEnabled, v -> config.playerVisuals.glowEnabled = v, true, false));
		mods.add(mod("Own nametag", "Entities", null, null, () -> config.nametagSelf, v -> config.nametagSelf = v, true, false));
		mods.add(mod("Nametags", "Entities", StrayScreen.Feature.NAMETAGS, EntityKind.PLAYER, () -> config.playerVisuals.nametagsEnabled, v -> config.playerVisuals.nametagsEnabled = v, true, false));
		mods.add(mod("Health bar", "Entities", StrayScreen.Feature.HEALTH, EntityKind.PLAYER, () -> config.playerVisuals.healthEnabled, v -> config.playerVisuals.healthEnabled = v, true, false));
		mods.add(mod("2D box", "Entities", StrayScreen.Feature.BOX, EntityKind.PLAYER, () -> config.playerVisuals.boxEnabled, v -> config.playerVisuals.boxEnabled = v, true, false));
		mods.add(mod("Shader", "Entities", StrayScreen.Feature.FILL, EntityKind.PLAYER, () -> config.playerVisuals.shaderEnabled, v -> config.playerVisuals.shaderEnabled = v, true, false));
		mods.add(mod("Mob glow", "Entities", StrayScreen.Feature.MOB, EntityKind.MOB, () -> config.mobVisuals.glowEnabled, v -> config.mobVisuals.glowEnabled = v, true, false));
		mods.add(mod("Mob tags", "Entities", StrayScreen.Feature.NAMETAGS, EntityKind.MOB, () -> config.mobVisuals.nametagsEnabled, v -> config.mobVisuals.nametagsEnabled = v, true, false));
		mods.add(mod("Mob health", "Entities", StrayScreen.Feature.HEALTH, EntityKind.MOB, () -> config.mobVisuals.healthEnabled, v -> config.mobVisuals.healthEnabled = v, true, false));
		mods.add(mod("Mob box", "Entities", StrayScreen.Feature.BOX, EntityKind.MOB, () -> config.mobVisuals.boxEnabled, v -> config.mobVisuals.boxEnabled = v, true, false));
		mods.add(mod("Mob shader", "Entities", StrayScreen.Feature.FILL, EntityKind.MOB, () -> config.mobVisuals.shaderEnabled, v -> config.mobVisuals.shaderEnabled = v, true, false));

		mods.add(mod("Array list", "HUD", null, null, () -> config.arrayList, v -> config.arrayList = v, false, false));
		mods.add(mod("Watermark", "HUD", StrayScreen.Feature.WATERMARK, null, () -> config.watermarkEnabled, v -> config.watermarkEnabled = v, true, false));
		mods.add(mod("Music", "HUD", StrayScreen.Feature.MUSIC, null, () -> config.musicHudEnabled, v -> config.musicHudEnabled = v, true, false));
		mods.add(mod("Picture in picture", "HUD", StrayScreen.Feature.PIP, null, () -> config.pipEnabled, v -> config.pipEnabled = v, true, false));
		mods.add(mod("Raw mats", "HUD", StrayScreen.Feature.RAWMATS, null, () -> config.rawmatsHudEnabled, v -> config.rawmatsHudEnabled = v, true, false));
		mods.add(mod("Pickup log", "HUD", null, null, () -> config.pickupLogEnabled, v -> config.pickupLogEnabled = v, true, false));
		mods.add(mod("Skill progress", "HUD", StrayScreen.Feature.SKILL, null, () -> config.skillProgressHudEnabled, v -> config.skillProgressHudEnabled = v, true, false));
		mods.add(mod("Inventory", "HUD", StrayScreen.Feature.INVENTORY, null, () -> config.inventoryHudEnabled, v -> config.inventoryHudEnabled = v, true, false));
		mods.add(mod("Custom title", "HUD", null, null, () -> config.customTitleEnabled, v -> config.customTitleEnabled = v, false, false));
		mods.add(mod("Scoreboard", "HUD", null, null, () -> config.hudScoreboard, v -> config.hudScoreboard = v, true, false));
		mods.add(mod("Boss bar", "HUD", null, null, () -> config.hudBossBar, v -> config.hudBossBar = v, true, false));
		mods.add(mod("Effects", "HUD", null, null, () -> config.hudEffects, v -> config.hudEffects = v, true, false));
		mods.add(mod("Item name", "HUD", null, null, () -> config.hudHeldItem, v -> config.hudHeldItem = v, true, false));

		mods.add(mod("Themed chat", "Chat", null, null, () -> config.themedChatEnabled, v -> config.themedChatEnabled = v, true, false));
		mods.add(mod("Compact stash", "Chat", null, null, () -> config.stashChatCompact, v -> config.stashChatCompact = v, true, false));
		mods.add(mod("Cleaner NPC", "Chat", null, null, () -> config.npcChatClean, v -> config.npcChatClean = v, true, false));
		mods.add(mod("Global IRC", "Chat", null, null, () -> config.strayIrcEnabled, v -> config.strayIrcEnabled = v, true, false));
		mods.add(mod("Lobby pings", "Chat", null, null, () -> config.strayPingEnabled, v -> config.strayPingEnabled = v, true, false));

		mods.add(mod("Experiments", "Menus", StrayScreen.Feature.AUTO_EXPERIMENTS, null, () -> config.autoExperimentsEnabled, v -> config.autoExperimentsEnabled = v, true, false));
		mods.add(mod("Book combine", "Menus", StrayScreen.Feature.AUTO_BOOKS, null, () -> config.autoBookCombineEnabled, v -> config.autoBookCombineEnabled = v, true, false));
		mods.add(mod("Loadouts", "Menus", StrayScreen.Feature.LOADOUTS, null, () -> config.loadoutsMenuEnabled, v -> config.loadoutsMenuEnabled = v, true, false));
		mods.add(mod("Wardrobe", "Menus", StrayScreen.Feature.WARDROBE, null, () -> config.wardrobeMenuEnabled, v -> config.wardrobeMenuEnabled = v, true, false));
		mods.add(mod("Profile viewer", "Menus", null, null, () -> config.profileViewerEnabled, v -> config.profileViewerEnabled = v, true, false));
		mods.add(mod("Storage preview", "Menus", null, null, () -> config.storagePreviewEnabled, v -> config.storagePreviewEnabled = v, true, false));
		mods.add(mod("Themed GUIs", "Menus", null, null, () -> config.themedGuisEnabled, v -> config.themedGuisEnabled = v, true, false));
		mods.add(mod("No cursor reset", "Menus", null, null, () -> config.noCursorReset, v -> config.noCursorReset = v, true, true));
		mods.add(mod("Disabled potions", "Menus", null, null, () -> config.disabledPotionsHighlight, v -> config.disabledPotionsHighlight = v, true, false));
		mods.add(mod("Sack recipe", "Menus", null, null, () -> config.sackRecipe, v -> config.sackRecipe = v, true, false));

		mods.add(new Mod(CUSTOMIZE, "Player", null, null, () -> true, v -> { }, false, false, false, true));
		mods.add(hold("Cosmetics", "Player", StrayScreen.Feature.COSMETICS));
		mods.add(mod("Nick hider", "Player", StrayScreen.Feature.NICK, null, () -> config.nickEnabled, v -> config.nickEnabled = v, true, false));
		mods.add(mod("Skin", "Player", StrayScreen.Feature.SKIN, null, () -> config.skinEnabled, v -> config.skinEnabled = v, true, false));
		mods.add(mod("Auto clicker", "Player", StrayScreen.Feature.AUTO_CLICKER, null, () -> config.autoClickerEnabled, v -> config.autoClickerEnabled = v, true, false));

		mods.add(hold("Accent", "Client", StrayScreen.Feature.ACCENT));
		mods.add(new Mod(HUD_LOOK, "Client", null, null, () -> true, v -> { }, false, false, true, true));
		mods.add(mod("Auto update", "Client", null, null, () -> config.autoUpdate, v -> config.autoUpdate = v, false, false));
		mods.add(mod("Auto close", "Client", null, null, () -> config.updateAutoClose, v -> config.updateAutoClose = v, false, false));
		mods.add(mod("Update notify", "Client", null, null, () -> config.updateNotify, v -> {
			config.updateNotify = v;
			config.save();
		}, false, false));
		return mods;
	}

	private static void addSlayer(List<Mod> mods, StrayConfig config) {
		var s = config.slayer;
		mods.add(mod("Slayer HUD", "Slayer", StrayScreen.Feature.SLAYER_HUD, null, () -> s.hud, v -> s.hud = v, true, false));
		mods.add(mod("Slayer profit", "Slayer", StrayScreen.Feature.SLAYER_PROFIT, null, () -> s.profit, v -> s.profit = v, true, false));
		mods.add(mod("RNG meter", "Slayer", StrayScreen.Feature.SLAYER_RNG, null, () -> s.rng, v -> s.rng = v, true, false));
		mods.add(mod("Remaining kills", "Slayer", StrayScreen.Feature.SLAYER_KILLS, null, () -> s.kills, v -> s.kills = v, true, false));
		mods.add(mod("Slayer alerts", "Slayer", StrayScreen.Feature.SLAYER_ALERTS, null, () -> s.alerts, v -> s.alerts = v, true, false));
		mods.add(mod("Slayer ESP", "Slayer", StrayScreen.Feature.SLAYER_ESP, null, () -> s.esp, v -> s.esp = v, true, false));
		mods.add(mod("Voidgloom", "Slayer", StrayScreen.Feature.SLAYER_VOID, null, () -> s.voidgloom, v -> s.voidgloom = v, true, false));
		mods.add(mod("Inferno", "Slayer", StrayScreen.Feature.SLAYER_INFERNO, null, () -> s.inferno, v -> s.inferno = v, true, false));
		mods.add(mod("Bloodfiend", "Slayer", StrayScreen.Feature.SLAYER_VAMPIRE, null, () -> s.bloodfiend, v -> s.bloodfiend = v, true, false));
		mods.add(mod("Slayer cleanup", "Slayer", StrayScreen.Feature.SLAYER_CLEANUP, null, () -> s.cleanup, v -> s.cleanup = v, true, false));
	}

	private static Mod mod(
		String name,
		String column,
		StrayScreen.Feature feature,
		EntityKind kind,
		BooleanSupplier on,
		Consumer<Boolean> set,
		boolean list,
		boolean timeout
	) {
		return mod(name, column, feature, kind, on, set, list, timeout, false);
	}

	private static Mod mod(
		String name,
		String column,
		StrayScreen.Feature feature,
		EntityKind kind,
		BooleanSupplier on,
		Consumer<Boolean> set,
		boolean list,
		boolean timeout,
		boolean menuStyle
	) {
		return new Mod(name, column, feature, kind, on, set, list, timeout, menuStyle, false);
	}

	private static Mod hold(String name, String column, StrayScreen.Feature feature) {
		return hold(name, column, feature, () -> true);
	}

	private static Mod hold(String name, String column, StrayScreen.Feature feature, BooleanSupplier on) {
		return new Mod(name, column, feature, null, on, v -> {
		}, false, false, false, true);
	}

	record Mod(
		String name,
		String column,
		StrayScreen.Feature feature,
		EntityKind kind,
		BooleanSupplier on,
		Consumer<Boolean> set,
		boolean list,
		boolean timeout,
		boolean menuStyle,
		boolean hold
	) {
	}
}
