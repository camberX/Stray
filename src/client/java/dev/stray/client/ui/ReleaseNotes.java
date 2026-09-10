package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ReleaseNotes {
	public record Entry(String version, String[] lines) {
	}

	public static final int VISIBLE = 10;
	public static final Entry[] ENTRIES = {
		new Entry("1.2.163", new String[]{
			"Music HUD has a Song chat toggle for the NOW PLAYING line when the track changes. Existing configs keep it on."
		}),
		new Entry("1.2.162", new String[]{
			"Control tabs go World, Visuals, Combat. Feature cards sit under category names. Star fill lives on Star mobs with its own Glow ESP toggle, so fill still tracks starred mobs when glow is off."
		}),
		new Entry("1.2.161", new String[]{
			"Player fill keeps mob/star glow instead of replacing the outline pass. Fill star mobs is a separate toggle from Fill ESP mobs."
		}),
		new Entry("1.2.160", new String[]{
			"Player fill skips Hypixel NPCs (UUID version 2, same as nametags). The silhouette outline only draws when Through walls is on, so it no longer shows through blocks when that toggle is off."
		}),
		new Entry("1.2.159", new String[]{
			"Stray HUD is one layer after the vanilla bars instead of hanging off chat, hotbar, and scoreboard. Fabric's wrappers for those vanilla extracts no longer include Music, Watermark, and the rest of the overlay pass."
		}),
		new Entry("1.2.158", new String[]{
			"The Mobs catalog is tall enough to show more than one name again after Player fill and Nametags were added above it. Control cards skip per-frame rim slices, and the color picker no longer draws a blit per pixel."
		}),
		new Entry("1.2.157", new String[]{
			"Control frost covers the whole rounded pane again, including the watermark. Vanilla hotbar and scoreboard are no longer wrapped by Stray when those custom HUD pieces are off."
		}),
		new Entry("1.2.156", new String[]{
			"HUD extract and item-model mixins are cheaper: item skins cache NBT and inventory ownership, Ghost/Fill skip mask work when they are off, and short Control HUD bars no longer frost."
		}),
		new Entry("1.2.155", new String[]{
			"Control HUD glass (Music, Watermark, and the rest of the HUD) draws frost as one rounded blit instead of dozens of corner slices, which was the main HUD FPS cost."
		}),
		new Entry("1.2.154", new String[]{
			"Held item and Player fill each have their own outline color picker. Existing configs keep the previous fill-derived outline color until you change it."
		}),
		new Entry("1.2.153", new String[]{
			"Player fill tints the player again instead of replacing the skin. Stars and smoke stay round in screen space and no longer stretch down the body."
		}),
		new Entry("1.2.152", new String[]{
			"Player fill stars and smoke no longer stretch with the skin texture. The pattern stays in screen space on the silhouette."
		}),
		new Entry("1.2.151", new String[]{
			"Farming has a movable Composter overlay based on SkyHanni's composter data: organic matter, fuel, stored compost, next cycle, cycles left, production rate, and estimated empty time.",
			"Open Composter Upgrades once so Stray can read your five upgrade levels and calculate capacities, costs, speed, and Multi Drop."
		}),
		new Entry("1.2.150", new String[]{
			"Player fill stars are denser and the pattern scales with distance, so player models keep visible texture up close and far away."
		}),
		new Entry("1.2.149", new String[]{
			"Player fill has an outline slider and uses world lighting, so faces and caves shade instead of staying fullbright."
		}),
		new Entry("1.2.148", new String[]{
			"Player fill uses the same silhouette outline as the held item shader. The click GUI scrollbar stays inside the rounded window corners."
		}),
		new Entry("1.2.147", new String[]{
			"Profile viewer uses more of the window: bigger chips, icons, HOTM nodes, item slots, and pet cells."
		}),
		new Entry("1.2.146", new String[]{
			"/pv and /pv name always open Stray's profile viewer, even if another mod also uses that command."
		}),
		new Entry("1.2.145", new String[]{
			"HOTM perks use emerald when unlocked and diamond when maxed, like the Skyblock chest."
		}),
		new Entry("1.2.144", new String[]{
			"HOTM perk levels are drawn as text instead of item stack counts, so levels above 99 show correctly."
		}),
		new Entry("1.2.143", new String[]{
			"Profile viewer HOTM reads the selected skill-tree loadout, so unlocked perks and their levels match the in-game tree."
		}),
		new Entry("1.2.142", new String[]{
			"Profile viewer skills use the real XP table and overflow past the cap, Catacombs can show 50+, slayers use cumulative XP, and HOTM no longer jumps a tier just because you have gemstone powder. The tree uses the Skyblock chest items."
		}),
		new Entry("1.2.141", new String[]{
			"Profile viewer networth uses SkyHelper market value, HOTM reads powder when Hypixel omits the tree, other players use their real skin, and the nametag uses rank color plus Skyblock level color."
		}),
		new Entry("1.2.140", new String[]{
			"Profile viewer fills pet stats instead of {STRENGTH} placeholders, reads HOTM 10 and the maxed tree from the real XP table, and prices items from live BIN lists so networth is no longer just purse and bank."
		}),
		new Entry("1.2.139", new String[]{
			"Profile viewer HOTM matches the Skyblock chest tree, with glass connectors and the same perk items. Item and pet hovers use the real lore. Other players show their skin and armor. Networth counts equipment, wardrobe, accessories, sacks, and essence."
		}),
		new Entry("1.2.138", new String[]{
			"Profile viewer is a bit shorter. Home has a larger player, level in the nametag, and the Ironman icon on the window and profile chips. Networth includes item worth. Dungeons use boss heads, typed class names, Catacombs and class bars, and secret average. Pets and HOTM use Skyblock icons."
		}),
		new Entry("1.2.137", new String[]{
			"Profile viewer drops the extra Skills tab. Combat is Dungeons with floors and classes. Mining shows the HOTM tree, Farm shows crop milestones, and Pets is a preview plus grid. Item slots use the menu theme."
		}),
		new Entry("1.2.136", new String[]{
			"Profile viewer uses the same menu size and scale as the other screens. It no longer stretches across the whole game window."
		}),
		new Entry("1.2.135", new String[]{
			"Profile viewer Home uses item icons for level, purse, bank, networth, skills, and slayers. The Items grid fills the pane instead of sitting in the corner."
		}),
		new Entry("1.2.134", new String[]{
			"Profile viewer tabs have icons. Items is the second tab and draws inventory, Ender Chest, and backpacks as slot grids. Skills also sit on Home."
		}),
		new Entry("1.2.133", new String[]{
			"Stray has its own Skyblock profile viewer. /pv or /pv name opens it."
		}),
		new Entry("1.2.132", new String[]{
			"Loadouts and wardrobe models face forward and no longer spin."
		}),
		new Entry("1.2.131", new String[]{
			"The click GUI player faces forward instead of spinning. Menu scale starts at 75%."
		}),
		new Entry("1.2.130", new String[]{
			"Loadouts and wardrobe stay closed after E or 1-9. Late Hypixel chest packets no longer flash the menu."
		}),
		new Entry("1.2.129", new String[]{
			"Wardrobe 1-9 queues the equip click like loadouts, then flushes it on the real chest. Equip uses the Slot 1-9 row."
		}),
		new Entry("1.2.128", new String[]{
			"1-9 on loadouts and wardrobe sends the click first, then closes. Wardrobe clicks the whole set card, not a tiny spot."
		}),
		new Entry("1.2.127", new String[]{
			"Wardrobe armor sets sit in one row. 1-9 still equips a set and closes. Loadouts and wardrobe have a Vanilla button for this chest."
		}),
		new Entry("1.2.126", new String[]{
			"Player fill color, style, and through-walls apply on their own. Duplicate Through walls switches no longer share one animation."
		}),
		new Entry("1.2.125", new String[]{
			"Hotkeys stay off inside chests and inventories. Player fill has its own color, style, and through-walls."
		}),
		new Entry("1.2.124", new String[]{
			"Star mobs have their own glow color, radius, and through-walls. Tabs scroll when cards run off the window."
		}),
		new Entry("1.2.123", new String[]{
			"Star mob ESP glows starred dungeon mobs with the same outline as Mob glow."
		}),
		new Entry("1.2.122", new String[]{
			"The mods-menu icon is the cat with the can."
		}),
		new Entry("1.2.121", new String[]{
			"The Control rail shows a cat above the version."
		}),
		new Entry("1.2.120", new String[]{
			"The client is now called Stray. Capes and updates use https://stray.gay."
		}),
		new Entry("1.2.119", new String[]{
			"Fake boosting bans are gone."
		}),
		new Entry("1.2.118", new String[]{
			"Held item shader sits in Visuals. The ESP category is now called Visuals."
		}),
		new Entry("1.2.117", new String[]{
			"Menu, loadouts, wardrobe, and chest-aim binds are set in Stray instead of vanilla Controls."
		}),
		new Entry("1.2.116", new String[]{
			"Loadouts outline the equipped slot, and 1-9 moves that outline when you pick a loadout."
		}),
		new Entry("1.2.115", new String[]{
			"Auto clicker binds show [ Button 5 ] in accent, and [ ... ] while listening.",
			"Switching categories now slides the page and the selected rail mark."
		}),
		new Entry("1.2.114", new String[]{
			"Auto clicker and Player fill stay inside the menu instead of running off the bottom."
		}),
		new Entry("1.2.113", new String[]{
			"Auto experiments lives in Misc. Auto clicker settings sit on the Combat card."
		}),
		new Entry("1.2.112", new String[]{
			"Launch no longer crashes. Experiment mouse-block uses the same Fabric screen events as OdinClient."
		}),
		new Entry("1.2.111", new String[]{
			"Auto clicker and Auto experiments match OdinClient, including Terminator CPS and experiment slot order."
		}),
		new Entry("1.2.110", new String[]{
			"Player fill ESP covers armor again, without breaking backpacks or glint layers."
		}),
		new Entry("1.2.109", new String[]{
			"Player fill ESP no longer distorts armor, backpacks, or extra model layers."
		}),
		new Entry("1.2.108", new String[]{
			"Mob glow still outlines the body when Player fill is on, not just the held item."
		}),
		new Entry("1.2.107", new String[]{
			"Player fill ESP smoke and stars shrink with distance so they stay visible far away.",
			"Empty second-skin and armor pixels no longer turn the fill black."
		}),
		new Entry("1.2.106", new String[]{
			"Player fill ESP keeps a solid outer shell while still showing through walls."
		}),
		new Entry("1.2.105", new String[]{
			"Player fill ESP shows the outside of the model instead of the hollow inside."
		}),
		new Entry("1.2.104", new String[]{
			"Player fill ESP goes through walls, covers armor, and also fills selected ESP mobs."
		}),
		new Entry("1.2.103", new String[]{
			"Player fill ESP paints other players and their held items with the held-item fill, without the outline."
		}),
		new Entry("1.2.102", new String[]{
			"HUD and bell icons sit in the center of their buttons.",
			"What's new only lists the last 10 updates, as bullets."
		}),
		new Entry("1.2.101", new String[]{
			"Combat is a sword again, and World is back on the globe."
		}),
		new Entry("1.2.100", new String[]{
			"The Control rail now shows the ♲ mark and version at the bottom in the accent color. Sliders and toggles follow the accent, and the search bar and selected category are a bit lighter."
		}),
		new Entry("1.2.99", new String[]{
			"Triggerbot skips entities whose nametag includes CLICK, including hologram plates above them."
		}),
		new Entry("1.2.98", new String[]{
			"Bars sit in HUD, Nodes in Mining, and Menus with Status in Misc. Combat is a sword, farming is wheat, and HUD uses a dashboard. Category names under the rail icons are a bit larger."
		}),
		new Entry("1.2.97", new String[]{
			"Each Control category now has a small name under its rail icon.",
			"Opacity lives on the color picker. Duplicate Opacity sliders next to those colors are gone."
		}),
		new Entry("1.2.96", new String[]{
			"Pills have their own opacity slider, and every color picker now sets opacity as well as hue."
		}),
		new Entry("1.2.95", new String[]{
			"Category icons are a bit smaller than the last build."
		}),
		new Entry("1.2.94", new String[]{
			"Category icons on the click-GUI rail are larger."
		}),
		new Entry("1.2.93", new String[]{
			"Aspect ratio now uses that FOV to cull the world, so terrain and entities outside it are not drawn. The black bars are gone."
		}),
		new Entry("1.2.92", new String[]{
			"A narrower aspect ratio now cuts the world to what you can see and fills the sides with black bars.",
			"The Music HUD is much cheaper: album art no longer rebuilds a huge key every frame, and the glass no longer paints hundreds of frost slices and stars."
		}),
		new Entry("1.2.91", new String[]{
			"Category icons match the page: landscape, bolt, eye, widgets, bars, pin, diamond, tractor, hanger, speed, and palette."
		}),
		new Entry("1.2.90", new String[]{
			"The click GUI has a category for each page — World, Combat, ESP, HUD, Bars, Nodes, Mining, Farming, Menus, Status, and Theme — so the rail is no longer four sparse icons."
		}),
		new Entry("1.2.89", new String[]{
			"The selected Control category is more transparent, so it no longer sits as a solid block on the rail."
		}),
		new Entry("1.2.88", new String[]{
			"The bright dots at rounded-outline joins are gone. Sides and corners no longer overlap, so that pixel is not drawn twice."
		}),
		new Entry("1.2.87", new String[]{
			"Rounded outlines keep one thickness all the way around. Sides and corners now come from the same ring, so the rim no longer steps or thins out at the join."
		}),
		new Entry("1.2.86", new String[]{
			"Menu and HUD corners use the circle textures again. The last two builds tried a custom GUI shader that Minecraft never actually ran, so corners showed filled circles and then the missing-texture tile."
		}),
		new Entry("1.2.85", new String[]{
			"Rounded rims are a one-pixel outline again. The last build recovered GUI scale from the wrong matrix, so every corner drew as a filled circle."
		}),
		new Entry("1.2.84", new String[]{
			"Rounded corners, rims, and frost edges are drawn by an analytic shader instead of scaled circle textures and scanline fills, so they stay smooth at any radius or GUI scale.",
			"Outlines are a consistent one-pixel rim everywhere; the frost under glass uses four quads instead of hundreds of row blits."
		}),
		new Entry("1.2.83", new String[]{
			"Held item shader outline now costs a fraction of before: the full-screen 625-sample search became two exact separable passes bounded by the outline radius. Same pixels, far fewer reads.",
			"Control frost no longer copies, blurs, and restores the whole frame; it blurs straight into its own target and, on the HUD, only under the panes that are actually drawn.",
			"ESP name matching, ESP occlusion raycasts, nametag occlusion, titanium ESP meshing, chest ESP sorting, and shop cape skins are cached per frame or per tick instead of recomputed for every entity."
		}),
		new Entry("1.2.82", new String[]{
			"Control HUD panes use the menu glass color and the same frost strength as the click GUI."
		}),
		new Entry("1.2.81", new String[]{
			"The custom title screen and HUD panes use the same glass, pills, and accent as the GUI theme you pick."
		}),
		new Entry("1.2.80", new String[]{
			"HUD panes, the title screen, and vanilla menus follow the GUI style you pick."
		}),
		new Entry("1.2.79", new String[]{
			"Control feature rows sit a little higher, and the menu corners use a smoother frost clip and matching rim."
		}),
		new Entry("1.2.78", new String[]{
			"Control feature pills use the same inset at the top as the bottom."
		}),
		new Entry("1.2.77", new String[]{
			"Control selected category and search are darker, frost can go stronger, and the first row in a feature pill matches the spacing of the ones after it."
		}),
		new Entry("1.2.76", new String[]{
			"The selected Control category is darker than the pane and uses the same gradient outline."
		}),
		new Entry("1.2.75", new String[]{
			"Control keeps the gradient glass rims. The dotted corner strokes are gone so the curves stay smooth."
		}),
		new Entry("1.2.74", new String[]{
			"Control chrome has a light gradient rim. The category pill is even, selected icons match the pane, search is darker, corners frost, and feature settings sit in the card instead of behind a cog."
		}),
		new Entry("1.2.73", new String[]{
			"The Control category pill sits in a matching inset from the menu and follows the window's left corners."
		}),
		new Entry("1.2.72", new String[]{
			"Control frost is an opaque world blur under the pane only. The last build's framebuffer alpha made every card and control glow."
		}),
		new Entry("1.2.71", new String[]{
			"Theme has a Pills color picker for Control category highlights and feature cards, separate from the glass color."
		}),
		new Entry("1.2.70", new String[]{
			"Control frost blurs the world under the menu only. The slider is blur strength again, not a second opacity layer."
		}),
		new Entry("1.2.69", new String[]{
			"Control frost stays on the menu glass. The world around it is no longer blurred. Feature cards are only a little lighter than the pane."
		}),
		new Entry("1.2.68", new String[]{
			"Rounded glass uses the smooth circle texture again. The last build's slice corners were the dark square bites on every panel."
		}),
		new Entry("1.2.67", new String[]{
			"Frost now blurs the world behind Control, not the glass opacity. Feature cards and the category pill sit lighter than the menu pane. Corners are smooth scanline arcs, and the player head is clipped to a circle."
		}),
		new Entry("1.2.66", new String[]{
			"Control no longer whites out the world. Accent presets stay inside their card instead of stacking over Scale."
		}),
		new Entry("1.2.65", new String[]{
			"Opening the Control GUI no longer crashes. Frost still uses the vanilla blur once, then a glass veil on top."
		}),
		new Entry("1.2.64", new String[]{
			"Control GUI is lighter and much more frosted. Feature gears open a Theme-style page instead of a dropdown, the player head is a real circle that opens capes, the HUD editor sits in the header, and you can pick the glass color."
		}),
		new Entry("1.2.63", new String[]{
			"Control GUI frost is heavier. The menu outline is softer, tab underlines sit under the text, cards have more padding, and the player head is round."
		}),
		new Entry("1.2.62", new String[]{
			"Theme can switch the click GUI between Stray and a frosted Control Center layout with iOS toggles and a blurred background."
		}),
		new Entry("1.2.61", new String[]{
			"Ghost smoke is soft drifting clouds again, not thin scribble lines on the item."
		}),
		new Entry("1.2.60", new String[]{
			"The game no longer goes black after the last Ghost hand change. Style can be Smoke or a drifting starry sky on the item and hand."
		}),
		new Entry("1.2.59", new String[]{
			"Ghost now tints the first-person hand as well as the held item. Smoke is larger moving wisps instead of TV static."
		}),
		new Entry("1.2.58", new String[]{
			"Ghost smoke is much smaller. The wisps are sized on screen so they no longer cover the whole item."
		}),
		new Entry("1.2.57", new String[]{
			"Ghost smoke is smaller and brighter. You see thin moving wisps on the item instead of a large shade change."
		}),
		new Entry("1.2.56", new String[]{
			"Ghost no longer crashes when the silhouette is drawn. The outline uniforms are written before the render pass opens."
		}),
		new Entry("1.2.55", new String[]{
			"Ghost's outline slider changes how thick the silhouette is. The fill slider tints the item instead of fading it, and the smoke is easier to see."
		}),
		new Entry("1.2.54", new String[]{
			"Ghost tints the held item instead of replacing it. The outline sits on the item and uses a lighter version of the color you pick."
		}),
		new Entry("1.2.53", new String[]{
			"Ghost's white silhouette is back. It traces the held item on screen around the transparent fill and smoke."
		}),
		new Entry("1.2.52", new String[]{
			"Ghost outlines the held item as you see it on screen, not each 3D face or texture pixel."
		}),
		new Entry("1.2.51", new String[]{
			"Ghost outlines the held item as a mesh, not each texture pixel. One white rim around the item, fill and smoke inside."
		}),
		new Entry("1.2.50", new String[]{
			"Ghost is one held item again. The white rim is painted on the sprite edges, not extra copies stacked in your FOV."
		}),
		new Entry("1.2.49", new String[]{
			"Ghost keeps its color in caves and fog. World lighting no longer tints the held item, and the outline fade is softer on the corners."
		}),
		new Entry("1.2.48", new String[]{
			"Ghost no longer draws a 3x3 of held items. The white outline stays a tight sticker rim around the one item in your hand."
		}),
		new Entry("1.2.47", new String[]{
			"Ghost's white outline follows the item's pixel edges like a sticker, sitting behind the transparent fill."
		}),
		new Entry("1.2.46", new String[]{
			"The game no longer goes black after the Mojang logo. Ghost still has a separate outline around the held item."
		}),
		new Entry("1.2.45", new String[]{
			"Ghost has a real outline around the held item. The outline slider changes that rim, not the fill color."
		}),
		new Entry("1.2.44", new String[]{
			"Ghost outlines the held item as a whole instead of tracing each texture pixel."
		}),
		new Entry("1.2.43", new String[]{
			"The held item shader is saved as Ghost: a thin white outline and a transparent fill with enchantment-glint smoke moving through it."
		}),
		new Entry("1.2.42", new String[]{
			"The held item shader no longer crashes on launch."
		}),
		new Entry("1.2.41", new String[]{
			"Combat has a Held item shader: a glowing outline, a transparent fill, and a smoke animation through the item you are holding. It stays off until you enable it."
		}),
		new Entry("1.2.40", new String[]{
			"Triggerbot skips dead mobs (death animation, and vanilla health). Skyblock still allows 0-health hologram mobs."
		}),
		new Entry("1.2.39", new String[]{
			"Triggerbot has a Humanize slider: a short reaction wait when a target appears, a little extra delay after hits, and occasional one-tick hesitation."
		}),
		new Entry("1.2.38", new String[]{
			"Triggerbot no longer hits armor stands or mannequins."
		}),
		new Entry("1.2.37", new String[]{
			"Triggerbot left-clicks at the same point in the tick as a normal attack, so moving while it hits no longer sends the attack after movement."
		}),
		new Entry("1.2.36", new String[]{
			"Triggerbot waits for vanilla weapon charge between swings. Skyblock still uses tab Attack Speed."
		}),
		new Entry("1.2.35", new String[]{
			"Triggerbot no longer swings every tick on Skyblock. It waits for tab Attack Speed, and vanilla worlds wait for the real weapon charge."
		}),
		new Entry("1.2.34", new String[]{
			"Triggerbot waits for vanilla attack cooldown, and on Skyblock it waits for tab Attack Speed."
		}),
		new Entry("1.2.33", new String[]{
			"Auto update closes Minecraft after it replaces the jar, so the next launch is the new version."
		}),
		new Entry("1.2.32", new String[]{
			"Combat Triggerbot hits the crosshair entity with a vanilla attack when it is in reach. Players stay off unless you turn that toggle on."
		}),
		new Entry("1.2.31", new String[]{
			"ESP no longer crashes when nametag matching runs while another copy of the same mob is learned."
		}),
		new Entry("1.2.30", new String[]{
			"Stray now uses https://stray.gay for updates, capes, and the download site."
		}),
		new Entry("1.2.29", new String[]{
			"Stray is now Stray. Existing settings and the legacy /stray command keep working.",
			"Auto update installs the new jar and exits cleanly for a normal launcher start; automatic relaunch has been removed."
		}),
		new Entry("1.2.28", new String[]{
			"Auto update can recover the full Java launch command from Windows process data, Linux /proc, or raw process data when the launcher hides ProcessHandle arguments."
		}),
		new Entry("1.2.27", new String[]{
			"Test release for verifying that auto-update installs the new jar and relaunches Minecraft."
		}),
		new Entry("1.2.26", new String[]{
			"Auto update starts the same Minecraft launch again after installing a new jar. If a launcher hides its command, Stray exits with the update installed for a manual start."
		}),
		new Entry("1.2.25", new String[]{
			"Jacob's Contest HUD predicts your finishing bracket and crop total from live tab data, with average crops per second and per score update."
		}),
		new Entry("1.2.24", new String[]{
			"Pickup log now uses actual inventory gains, so direct rewards count, ground pickups count once, slot moves do not count, and quantities are correct."
		}),
		new Entry("1.2.23", new String[]{
			"On Skyblock, hitsound and hitmarker wait follow tab Attack Speed: melee rounds 10 / (1 + AS/100) ticks per mob. Off Skyblock the wait stays 1 tick."
		}),
		new Entry("1.2.22", new String[]{
			"Hitsound and hitmarker delays are per mob. Hitting one no longer silences the rest."
		}),
		new Entry("1.2.21", new String[]{
			"Melee hitsound and hitmarker wait 1 tick between hits on the same target and no longer wait for hurt-time."
		}),
		new Entry("1.2.20", new String[]{
			"The hitmarker is a smooth stroke on the vanilla crosshair center instead of stepped squares."
		}),
		new Entry("1.2.19", new String[]{
			"Melee hitsound and hitmarker wait 3 ticks between hits on the same target."
		}),
		new Entry("1.2.18", new String[]{
			"The hitmarker X is thinner.",
			"Melee hitsound and hitmarker use the 1.8 10-tick hit delay instead of the 1.9 weapon charge."
		}),
		new Entry("1.2.17", new String[]{
			"Pickup log shows recently collected items and quantities in a movable HUD list, then fades them after five seconds."
		}),
		new Entry("1.2.16", new String[]{
			"Chest Aim no longer wipes the next lock after a ding. A newly spawned box is aimed even if it sits near the last one."
		}),
		new Entry("1.2.15", new String[]{
			"Chest Aim forgets finished lock spots when that chest despawns, so the next chest is not treated as already done."
		}),
		new Entry("1.2.14", new String[]{
			"Chest Aim is a hold bind again: it aims only while the key is held and stops when you let go."
		}),
		new Entry("1.2.13", new String[]{
			"Chest Aim keeps turning to a newly spawned lock box instead of freezing after a missed ding or a lagged pile."
		}),
		new Entry("1.2.12", new String[]{
			"Chest Aim ignores lagged extra boxes: it stays on the current lock until the ding, then waits for a fresh mark instead of chasing the pile."
		}),
		new Entry("1.2.11", new String[]{
			"Chest Aim still turns when lock boxes are close together, and finished boxes disappear instead of lingering."
		}),
		new Entry("1.2.10", new String[]{
			"Chest Aim starts when your crosshair is near the first lock box. No keybind."
		}),
		new Entry("1.2.9", new String[]{
			"Chest Aim never looks at a lock box it already went to."
		}),
		new Entry("1.2.8", new String[]{
			"Chest Aim listens for Experience Gained the same way subtitles do, follows the current lock, then looks at the newest next particles."
		}),
		new Entry("1.2.7", new String[]{
			"Chest Aim hears Experience Gained even inside packet bundles, and only then looks at the next lock."
		}),
		new Entry("1.2.6", new String[]{
			"Chest Aim holds on a lock until Hypixel's ding plays, then turns to the next particles."
		}),
		new Entry("1.2.5", new String[]{
			"Chest Aim stays on the current crit cluster until those particles are gone, so lag no longer flicks between two boxes."
		}),
		new Entry("1.2.4", new String[]{
			"Chest ESP keeps chests you already found after you walk out of the 5-block detect range. They drop only when the chest is gone."
		}),
		new Entry("1.2.3", new String[]{
			"Chest Aim no longer freezes on the first crit. It dwells briefly, then turns to the next distinct particle on the chest."
		}),
		new Entry("1.2.2", new String[]{
			"Chest ESP only marks crit particles within 0.3 blocks of a chest, so stray hits are ignored."
		}),
		new Entry("1.2.1", new String[]{
			"Chest Aim tracks each distinct crit box and only turns after that box jumps or vanishes, instead of locking onto the chest block."
		}),
		new Entry("1.2.0", new String[]{
			"Stray 1.2.0 release."
		}),
		new Entry("1.1.223", new String[]{
			"Chest ESP has a Speed slider for how fast Chest Aim turns."
		}),
		new Entry("1.1.222", new String[]{
			"Chest Aim uses a timed ease-in-out look: shortest-path yaw, lerp pitch, then a mouse-step snap."
		}),
		new Entry("1.1.221", new String[]{
			"Chest Aim (Controls) smoothly looks at each crit box on a nearby chest, waits for it to move, then goes to the next. Five locks is one pass; if the chest is still there it keeps going."
		}),
		new Entry("1.1.220", new String[]{
			"Chest ESP marks newly spawned chests within 5 blocks in Dwarven Mines and Crystal Hollows from packets, with tracers and crit-particle boxes even if Sodium hides particles."
		}),
		new Entry("1.1.219", new String[]{
			"Block outline glow is back. Looking at a block uses a small blur instead of the full ESP kernel, so world FPS stays up."
		}),
		new Entry("1.1.218", new String[]{
			"Farm keys uses STRAY chat prefixes, sets sensitivity to minimum, and restores your exact sensitivity when disabled."
		}),
		new Entry("1.1.217", new String[]{
			"/vm farmkeys or /vm fk swaps Attack/Destroy with Jump and makes Attack/Destroy toggle. Run it again to restore your original bindings."
		}),
		new Entry("1.1.216", new String[]{
			"Wardrobe empty slots hide the armor chips, only real locked slots say Locked, and only the equipped set is outlined."
		}),
		new Entry("1.1.215", new String[]{
			"The Music HUD reads Spotify progress from the live SMTC track every frame, the same way ForageKit does. YouTube Music is unchanged."
		}),
		new Entry("1.1.214", new String[]{
			"Spotify now-playing uses the same SMTC helper and wall-clock bar as ForageKit. YouTube Music is unchanged."
		}),
		new Entry("1.1.213", new String[]{
			"Spotify progress uses the first SMTC timestamp as an anchor and adds wall-clock time, so the bar moves every frame."
		}),
		new Entry("1.1.212", new String[]{
			"Spotify on the Music HUD reads the Windows media session. No Spotify login. YouTube Music is unchanged."
		}),
		new Entry("1.1.211", new String[]{
			"If Spotify login works but the bar does not move, the developer app is still in development mode. Add that account under User Management."
		}),
		new Entry("1.1.210", new String[]{
			"Spotify Connected still shows the HUD from the desktop window when the API has no active player, instead of hiding as idle."
		}),
		new Entry("1.1.209", new String[]{
			"Spotify progress keeps moving from the API clock. A brief empty poll no longer swaps in the window title, which has no time."
		}),
		new Entry("1.1.208", new String[]{
			"Spotify Connect ships the shared app ID in the jar, so Connect opens the browser login instead of Need Client ID."
		}),
		new Entry("1.1.207", new String[]{
			"Spotify Connect loads the shared client ID from GitHub instead of the shop worker, so friends are not stuck on Need Client ID when /api/spotify is missing."
		}),
		new Entry("1.1.206", new String[]{
			"Music HUD can read Spotify through the official API. Overlay → Music → Spotify API connects once, then title, artist, album art, and skip controls come from Spotify."
		}),
		new Entry("1.1.205", new String[]{
			"Loadouts and wardrobe verify the menu title before saving a cache."
		}),
		new Entry("1.1.204", new String[]{
			"Picking a local cape no longer freezes Minecraft while the folder dialog is open."
		}),
		new Entry("1.1.203", new String[]{
			"Auto-update removes the old jar from mods. If Windows has it locked, it is moved out of the way and deleted on the next launch."
		}),
		new Entry("1.1.202", new String[]{
			"Only the equipped loadout and wardrobe set are outlined. Wardrobe shows helmet, chest, legs, and boots under each model. New installs start with every optional feature off."
		}),
		new Entry("1.1.201", new String[]{
			"Closing loadouts with the hotkey keeps the last snapshot, so the next open still comes from cache instead of a blank wait."
		}),
		new Entry("1.1.200", new String[]{
			"Switching click-GUI tabs only slides the sidebar pill. Icons and labels stay on their own rows."
		}),
		new Entry("1.1.199", new String[]{
			"Farming tab: Yaw / Pitch sits next to the crosshair while you hold an item whose lore includes FARMING TOOL."
		}),
		new Entry("1.1.198", new String[]{
			"World FPS is back: looking at a block no longer runs the glow shader, and combat no longer scans every nearby entity each tick for the hitmarker."
		}),
		new Entry("1.1.197", new String[]{
			"Shop bump so auto-update can pull the latest build."
		}),
		new Entry("1.1.196", new String[]{
			"The hitmarker is white with a black outline again, like CoD. It still fades out without shifting color."
		}),
		new Entry("1.1.195", new String[]{
			"Hitmarker size is a slider on Combat → Mix. The X stays white and only fades out."
		}),
		new Entry("1.1.194", new String[]{
			"Wardrobe and loadouts no longer flash a lime green plate behind 3D models. Equipped slots use the accent rim instead."
		}),
		new Entry("1.1.193", new String[]{
			"Melee hitsounds wait for the weapon hit delay, not every click on a mob. A CoD-style hitmarker flashes on the crosshair when a hit lands."
		}),
		new Entry("1.1.192", new String[]{
			"The wardrobe menu is a grid of 3D armor models, one per slot, instead of a copy of the loadouts layout."
		}),
		new Entry("1.1.191", new String[]{
			"Auto update replaces the jar and stops this launch so the next start loads the new code."
		}),
		new Entry("1.1.190", new String[]{
			"Shop bump so auto-update can pull the swap-and-continue updater."
		}),
		new Entry("1.1.189", new String[]{
			"Auto update writes the new jar and keeps this launch going. Fabric already loaded the current jar, so the new code is on the next start."
		}),
		new Entry("1.1.188", new String[]{
			"Shop bump so auto-update can pull the relaunch fix."
		}),
		new Entry("1.1.187", new String[]{
			"Auto update actually relaunches Minecraft after it swaps the jar. The last build exited cleanly and never started the new process."
		}),
		new Entry("1.1.186", new String[]{
			"Theme has a Menu stars toggle. Turn it off to hide the drifting stars in the click GUI, loadouts, and wardrobe."
		}),
		new Entry("1.1.185", new String[]{
			"Auto update replaces the jar and relaunches Minecraft on its own, so you do not have to open the launcher again."
		}),
		new Entry("1.1.184", new String[]{
			"The vanilla black block outline stays off while Stray's glow outline is on, so you only see the custom rim."
		}),
		new Entry("1.1.183", new String[]{
			"The click GUI has more sidebar tabs: Combat, Menus, and Status sit next to World, ESP, Overlay, Bars, Nodes, and Mining so the list fills the pane."
		}),
		new Entry("1.1.182", new String[]{
			"Auto update is off by default. Turn it on in the theme settings and the next launch waits for stray.gay: if a newer jar is there it replaces the one in mods and Minecraft closes so you can relaunch."
		}),
		new Entry("1.1.181", new String[]{
			"Hypixel wardrobe ((1/3) Armor Sets) opens a Stray menu with a 3D armor preview and the set slots on the right. Pets and extra gear are left out. Clicks go through the real chest."
		}),
		new Entry("1.1.180", new String[]{
			"The loadouts gear row only shows armor and equipment that is actually there. Empty placeholder slots to the right are gone."
		}),
		new Entry("1.1.179", new String[]{
			"1-9 no longer flashes the loadouts menu after it closes. Late Hypixel chest packets are dropped until you open it again."
		}),
		new Entry("1.1.178", new String[]{
			"The loadouts menu opens instantly from the last snapshot, so Hypixel lag no longer blanks the UI. Clicks you make while it is loading are sent when the real chest arrives. Open animation has its own toggle under Nodes → Menus."
		}),
		new Entry("1.1.177", new String[]{
			"Loadout slots follow Hypixel's 3-wide grid, so slot 1 and 4 match the vanilla chest. The pet is a floating skull with the item's real name colors."
		}),
		new Entry("1.1.176", new String[]{
			"Loadout slots stay inside the slots pane. Press 1-9 to equip that slot and close. Bind Open Loadouts in Controls to open the menu without typing /loadouts."
		}),
		new Entry("1.1.175", new String[]{
			"The custom loadouts menu actually opens. It looks for Hypixel's (1/3) Loadouts title (any page numbers), which the last build missed because it looked for lowercase loadout."
		}),
		new Entry("1.1.174", new String[]{
			"Hypixel /loadouts opens a Stray menu: 3D armor and pet for the selected loadout, and the eight slots on the right. Clicks go through the real chest so the server sees a normal GUI click."
		}),
		new Entry("1.1.173", new String[]{
			"Melee hitsounds wait for Minecraft's attack cooldown. Spam-clicks before the weapon is charged no longer ding."
		}),
		new Entry("1.1.172", new String[]{
			"Shop download version is 1.1.172 so stray.gay can show the new jar name after a git push."
		}),
		new Entry("1.1.171", new String[]{
			"Shop download version is 1.1.171 so stray.gay can show the new jar name after a git push."
		}),
		new Entry("1.1.170", new String[]{
			"Hitsound uses the agpa2 clip, and only plays when you land the hit. Nearby players punching the same mob no longer trigger it."
		}),
		new Entry("1.1.169", new String[]{
			"Ships a verified hitsound jar. 1.1.168 was a truncated zip for some downloads (zip END header not found); delete that file from mods or Fabric will not launch."
		}),
		new Entry("1.1.168", new String[]{
			"Hitsounds actually play on Hypixel. The first version skipped every mob whose client health was 0 (most Skyblock mobs), used the Players slider, and had no backup when an arrow or hologram click did not collide locally."
		}),
		new Entry("1.1.167", new String[]{
			"Theme Font Minecraft is smaller and sits on the same baseline as Nunito. The last pass still left it high and a bit large in the click GUI rows."
		}),
		new Entry("1.1.166", new String[]{
			"Theme Font Minecraft is drawn at the same body, small, and title sizes as Nunito. It no longer fills the click GUI and HUD like vanilla 8px chat text."
		}),
		new Entry("1.1.165", new String[]{
			"Hitsounds play the instant a melee swing or your arrow overlaps a mob on the client, instead of waiting for Hypixel to confirm the hit. Melee and arrows are separate toggles on the World tab; volume and pitch sit on the cog."
		}),
		new Entry("1.1.164", new String[]{
			"HUD panes no longer show seam lines or darker bands at the rounded corners. Item wells and stars stay on the cheap fill path."
		}),
		new Entry("1.1.163", new String[]{
			"The click GUI, title screen, and other menus use the old smooth fills again. HUD panes keep the cheaper integer path while you are in a world."
		}),
		new Entry("1.1.162", new String[]{
			"HUD panes no longer re-read the tab list, scoreboard, and inventory every frame to decide what to show. Mining commissions update a few times a second, and a pane that is off does not keep scanning."
		}),
		new Entry("1.1.161", new String[]{
			"Click GUI cards no longer show faint vertical seams at the rounded corners. HUD fills stay on the cheap integer path."
		}),
		new Entry("1.1.160", new String[]{
			"HUD chrome is cheaper with every pane on. Item wells are flat instead of rounded, fills no longer break the GUI batch, and raw mats / scoreboard stop rebuilding several times a frame. HUD stars skip the tiny bars."
		}),
		new Entry("1.1.159", new String[]{
			"Only the watermark keeps the accent rail. The other HUD panes are outline and fill."
		}),
		new Entry("1.1.158", new String[]{
			"Theme Font includes Minecraft next to Nunito and your installed fonts. The dropdown rows are spaced like the rest of the menu. Nametag ESP lives only on the ESP tab. Raw mats notes say Used in instead of Uses."
		}),
		new Entry("1.1.157", new String[]{
			"Hypixel nametags keep their colors. Ironman plates no longer show leftover 8 / b / 7 from §8 §b §7."
		}),
		new Entry("1.1.156", new String[]{
			"Raw mats still counts Refined Mithril (and other compact forms) toward Enchanted Mithril, and each row shows Uses plus the ingredient the recipe actually wants."
		}),
		new Entry("1.1.155", new String[]{
			"Glow ESP has a Radius slider for how far the halo reaches. The blur no longer fades to a black vignette at the edge."
		}),
		new Entry("1.1.154", new String[]{
			"ESP lists every /vm esp nametag filter with an x to remove it. You can glow more than one word at once. Player nametags stay on Minecraft's font; Unicode that the Theme font is missing falls back to vanilla so it still draws."
		}),
		new Entry("1.1.153", new String[]{
			"/vm esp seer no longer glows Obsidian Defenders. Nametags bind to the mob under them, not every nearby mob, and a known different plate is never treated as a copy."
		}),
		new Entry("1.1.152", new String[]{
			"/vm esp forgets learned mob looks when you change worlds, so you have to see one named copy again. Samples stay in memory for that world only."
		}),
		new Entry("1.1.151", new String[]{
			"/vm esp remembers the type and armor of the first named mob you see, then glows other copies at render distance before their nametag appears."
		}),
		new Entry("1.1.150", new String[]{
			"/vm esp <text> glows the living mob as soon as Hypixel sends the nametag in entity metadata, even if the hologram plate has not rendered yet."
		}),
		new Entry("1.1.149", new String[]{
			"/vm esp <text> reads Hypixel nametags as soon as they exist on the client, including hidden hologram stands and text displays, instead of waiting until the plate is drawn."
		}),
		new Entry("1.1.148", new String[]{
			"Glow ESP no longer covers slayers and other mobs that already have vanilla glow — they keep Minecraft's outline."
		}),
		new Entry("1.1.147", new String[]{
			"/vm esp <text> glows every mob whose nametag contains that word, including holograms above the real mob. Raw mats reads Ender Chest and backpacks from the profile API again — older inventory layouts, nested backpacks, and 1.21 item components included."
		}),
		new Entry("1.1.146", new String[]{
			"Theme has a Font picker for every menu and HUD label. It lists the TrueType fonts installed on your PC. The watermark STRAY logo and the STRAY Dev nametag stay on Nunito."
		}),
		new Entry("1.1.145", new String[]{
			"/vm edit is a visual reskin again — no lore, no Maxed, no worn armor models. Raw mats only counts the materials you actually have, not cobble locked inside minions and other crafts."
		}),
		new Entry("1.1.144", new String[]{
			"Raw mats no longer double-counts items you pull out of a backpack or Ender Chest after the API snapshot. Storage moves while the bag is open are tracked; armor is counted once."
		}),
		new Entry("1.1.143", new String[]{
			"Chest GUIs like Heart of the Mountain no longer hitch. Item reskins never copy NBT or walk lore on menu stacks — only the items in your own inventory."
		}),
		new Entry("1.1.142", new String[]{
			"Glow ESP renders through walls at full range — entities behind geometry are no longer culled. Music HUD is more compact; hover controls (prev/play/next) are visible again."
		}),
		new Entry("1.1.141", new String[]{
			"Chams and the ESP 3D preview are gone. Vanilla nametag style still uses Stray range, size, opacity, through-walls, distance text, and distance scaling — only the chrome is Minecraft's."
		}),
		new Entry("1.1.140", new String[]{
			"Fresh jar. Replace a truncated 1.1.139 download (zip END header not found) with this build."
		}),
		new Entry("1.1.139", new String[]{
			"ESP has a 3D player preview that live-applies glow and chams. Chams has Fill (solid unlit), Default (solid with lighting), and Tint (color wash over the skin), with through-walls, opacity, and its own color."
		}),
		new Entry("1.1.138", new String[]{
			"The Cape card stays inside the Player tab instead of hanging off the bottom of the menu."
		}),
		new Entry("1.1.137", new String[]{
			"Titanium ESP uses SkyHanni's Dwarven Mines area nodes, so named jobs like Rampart's Quarry Titanium only mark ore in that zone. Neighbouring rooms (Forge, Village, Far Reserve, corridors) no longer steal those veins."
		}),
		new Entry("1.1.136", new String[]{
			"/vm edit has a Maxed toggle: copied lore gets recomb, dungeon stars, master stars, gem slots, hot-potato stats, and max enchants — only the pieces that item already has. Copied armor also uses its worn 3D model, including dragon helmets."
		}),
		new Entry("1.1.135", new String[]{
			"Copied Skyblock lore is no longer forced italic. Hypixel tooltip lines stay upright."
		}),
		new Entry("1.1.134", new String[]{
			"/vm edit: type a Skyblock item name (Hyperion) to reskin and copy that item's Hypixel name and lore, color codes included."
		}),
		new Entry("1.1.133", new String[]{
			"Music controls stay hidden until you hover the HUD with chat open, then slide down. /vm edit can copy a Hypixel item's name and lore, color codes included, and replace the one you are holding."
		}),
		new Entry("1.1.132", new String[]{
			"Cape creator crops photos onto the 10×16 cape face in Stray and on the cape desk. Drag to pan, scroll to zoom, then apply. Vanilla 64×32 templates still skip the cropper."
		}),
		new Entry("1.1.131", new String[]{
			"Nametags cog picks Stray plates or vanilla tags. Menu buttons, fields, list rows, and compact HUD pieces no longer draw a left accent rail. Remaining HUD rails flip to the side closer to the screen edge."
		}),
		new Entry("1.1.130", new String[]{
			"Node ESP sits on the Nodes tab without the extra caption text. Server list and world list no longer have dark bands over the header and footer."
		}),
		new Entry("1.1.129", new String[]{
			"YouTube Music HUD no longer guesses a different song from the window title. Artist, cover, and the progress bar come from the API Server on 26538."
		}),
		new Entry("1.1.128", new String[]{
			"Server list and world list keep a single Stray separator. The extra vanilla bar is gone."
		}),
		new Entry("1.1.127", new String[]{
			"YouTube Music now reads the API Server on 26538, asks for access when the plugin requires it, and uses the album cover and artist the API actually returns."
		}),
		new Entry("1.1.126", new String[]{
			"Fake boosting bans last 360 days instead of 180."
		}),
		new Entry("1.1.125", new String[]{
			"Music HUD no longer launches an external Windows shell. Now-playing on Windows uses the player window and local companion apps."
		}),
		new Entry("1.1.124", new String[]{
			"Fake ban Retry also fakes Encrypting before Joining world."
		}),
		new Entry("1.1.123", new String[]{
			"Fake ban Retry never opens a real connection. Vanilla Connecting then Joining world are faked, then the kick."
		}),
		new Entry("1.1.122", new String[]{
			"Fake ban reconnects reach Joining world, sit there for half a second, then kick. Retry actually connects again."
		}),
		new Entry("1.1.121", new String[]{
			"Fake ban actually sends /limbo again. Reconnects never load the world — Retry just refreshes the kick screen."
		}),
		new Entry("1.1.120", new String[]{
			"Fake ban has no confirm popup. After 5 seconds you get a red limbo exception line, then 3 seconds in Limbo before the kick."
		}),
		new Entry("1.1.119", new String[]{
			"Fake ban duration is white. Remaining time stays frozen on the kick screen and only updates when you reconnect."
		}),
		new Entry("1.1.118", new String[]{
			"Fake boosting bans match Hypixel: 180d countdown, Boosting detected on one or multiple SkyBlock profiles, and reconnects skip Limbo."
		}),
		new Entry("1.1.117", new String[]{
			"Admin Fake ban sends you to Limbo for 2 seconds, then a 180-day Hypixel Boosting kick with a random Ban ID."
		}),
		new Entry("1.1.116", new String[]{
			"capeServerUrl is gone from stray.json. Capes still use https://stray.gay."
		}),
		new Entry("1.1.115", new String[]{
			"Cape shop is always https://stray.gay. Old workers.dev and localhost URLs are ignored."
		}),
		new Entry("1.1.114", new String[]{
			"Refresh capes can only run once every 5 minutes so the shop cannot be rate-limited from the button."
		}),
		new Entry("1.1.113", new String[]{
			"Refresh capes on the Cape card pulls the latest shop capes and head tags."
		}),
		new Entry("1.1.112", new String[]{
			"Cape changes are once per 24 hours unless the admin checks Upload bypass.",
			"A cape set in the admin list overrides your local cape."
		}),
		new Entry("1.1.111", new String[]{
			"A cape set in the admin list overrides your local cape.",
			"Shop capes and head tags still refresh when you join a world."
		}),
		new Entry("1.1.110", new String[]{
			"Shop capes and head tags refresh when you join a world, not every couple of seconds."
		}),
		new Entry("1.1.109", new String[]{
			"Admin can set a head tag on the cape list. Color codes work, and it shows above their nametag."
		}),
		new Entry("1.1.108", new String[]{
			"Cape shop defaults to the live Cloudflare host. Localhost configs migrate on launch."
		}),
		new Entry("1.1.107", new String[]{
			"Cape settings stay locked until your UUID is on the shop list.",
			"The shop rejects everyone else with uuid not whitelisted."
		}),
		new Entry("1.1.106", new String[]{
			"Shop capes show for every Stray user, not just you.",
			"Changing the cape in the menu updates it for everyone within a couple of seconds."
		}),
		new Entry("1.1.105", new String[]{
			"Own nametag is its own switch on ESP, not inside the Nametags cog."
		}),
		new Entry("1.1.104", new String[]{
			"Own nametag toggle. STRAY Dev only draws with it, and no longer overlaps the name."
		}),
		new Entry("1.1.103", new String[]{
			"STRAY Dev uses vanilla nametag chrome when custom nametags are off."
		}),
		new Entry("1.1.102", new String[]{
			"STRAY Dev and the name share one plate."
		}),
		new Entry("1.1.101", new String[]{
			"STRAY Dev still shows when custom nametags are off."
		}),
		new Entry("1.1.100", new String[]{
			"STRAY Dev uses the menu font and sits on the nametag.",
			"Nametags no longer fade in or out."
		}),
		new Entry("1.1.99", new String[]{
			"Removed the toolbar Reset button.",
			"Adjacent Titanium ESP ores merge into one outline."
		}),
		new Entry("1.1.98", new String[]{
			"Player preview is larger, still inside the You card."
		}),
		new Entry("1.1.97", new String[]{
			"Player preview uses inventory scale so it no longer fills the You card."
		}),
		new Entry("1.1.96", new String[]{
			"Player preview fits inside the You card instead of clipping the head."
		}),
		new Entry("1.1.95", new String[]{
			"Titanium ESP stays inside the named commission area instead of a huge radius around the emissary."
		}),
		new Entry("1.1.94", new String[]{
			"Titanium ESP follows the commission area; Titanium Miner still marks every vein.",
			"Commission HUD bars are back, colored with the percent.",
			"Player preview fills the card and hides armor."
		}),
		new Entry("1.1.93", new String[]{
			"Rounded corners no longer show black gaps from the fill fast-path."
		}),
		new Entry("1.1.92", new String[]{
			"Nametags use the default Minecraft font.",
			"HUD opacity is separate from the click-GUI pane.",
			"Player preview is smaller, follows menu scale, and uses a vanilla nametag above the model."
		}),
		new Entry("1.1.91", new String[]{
			"Removed Efficient Miner prediction.",
			"HUD stars and GUI fills cost less while looking the same."
		}),
		new Entry("1.1.90", new String[]{
			"Mining Spread extras include face, edge, and corner diagonals."
		}),
		new Entry("1.1.89", new String[]{
			"Efficient Miner uses Hypixel's spread math in a 3×3×3, not a vein fill."
		}),
		new Entry("1.1.88", new String[]{
			"Titanium ESP while a Titanium commission is active.",
			"Efficient Miner overlay on the extra blocks Mining Spread will break."
		}),
		new Entry("1.1.87", new String[]{
			"3D preview head stays locked to the body.",
			"Nametag pills fit the text. Compact Dev badge.",
			"Nametag opacity plus a fade when you enter range.",
			"Nick applies to your own F5 tag.",
			"Menu scale 100/90/75/50%. Optional HUD stars."
		}),
		new Entry("1.1.86", new String[]{
			"Bell opens versioned release notes.",
			"Settings panes stay more opaque.",
			"Category divider uses the accent.",
			"Skull/name opens a 3D skin preview.",
			"Nametags on ESP. Node HUD on Nodes."
		}),
		new Entry("1.1.85", new String[]{
			"Settings sheets use the pane fill instead of an accent wash.",
			"Block outline glow has its own color and opacity.",
			"Mining HUD sits on the Mining tab only."
		}),
		new Entry("1.1.84", new String[]{
			"Theme and subsetting popovers use a hairline outline."
		}),
		new Entry("1.1.83", new String[]{
			"Pickaxe ready alert uses the pane fill and accent text."
		}),
		new Entry("1.1.82", new String[]{
			"Click GUI recategorized. Feature cogs open subsettings.",
			"Mining HUD is compact with commissions and ability cooldown."
		}),
		new Entry("1.1.80", new String[]{
			"Backpack and Ender Chest counts fetch on join and /vm rawmats.",
			"Commissions read from the tab list."
		}),
		new Entry("1.1.76", new String[]{
			"Block outline glow is a filled-face silhouette, not a wire cage."
		})
	};

	private ReleaseNotes() {
	}

	public static String currentVersion() {
		return FabricLoader.getInstance()
			.getModContainer("stray")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse(ENTRIES[0].version);
	}

	public static List<Entry> visible() {
		int n = Math.min(VISIBLE, ENTRIES.length);
		return Arrays.asList(ENTRIES).subList(0, n);
	}

	public static boolean unread() {
		String seen = StrayConfig.get().changelogSeen;
		if (seen == null || seen.isBlank()) {
			return true;
		}
		return !seen.equals(currentVersion());
	}

	public static void markSeen() {
		StrayConfig config = StrayConfig.get();
		config.changelogSeen = currentVersion();
		config.save();
	}

	public static float contentHeight(Font font, float width, float row) {
		float bullet = GuiDraw.menuWidth(font, "• ");
		float wrapW = Math.max(8f, width - bullet);
		float h = 0f;
		for (Entry entry : visible()) {
			h += 12f;
			for (String line : entry.lines()) {
				h += Math.max(1, wrapLine(font, line, wrapW).size()) * row;
			}
			h += 6f;
		}
		return h;
	}

	public static List<String> wrapLine(Font font, String text, float maxW) {
		List<String> out = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return out;
		}
		StringBuilder row = new StringBuilder();
		for (String word : text.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			String next = row.isEmpty() ? word : row + " " + word;
			if (GuiDraw.menuWidth(font, next) <= maxW) {
				row.setLength(0);
				row.append(next);
				continue;
			}
			if (!row.isEmpty()) {
				out.add(row.toString());
				row.setLength(0);
			}
			if (GuiDraw.menuWidth(font, word) <= maxW) {
				row.append(word);
			} else {
				out.add(clipWord(font, word, maxW));
			}
		}
		if (!row.isEmpty()) {
			out.add(row.toString());
		}
		return out;
	}

	private static String clipWord(Font font, String word, float maxW) {
		if (GuiDraw.menuWidth(font, word) <= maxW) {
			return word;
		}
		String trimmed = word;
		while (trimmed.length() > 1 && GuiDraw.menuWidth(font, trimmed + "..") > maxW) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}
}
