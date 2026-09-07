# Eisenmann

A client-side Fabric mod for Minecraft 26.1.2 focused on Hypixel SkyBlock quality-of-life features and visual customization.

Eisenmann includes Ender Node highlighting, mining and farming tools, configurable HUD elements, custom menus, entity ESP, capes, media controls, and a compact in-game settings interface.

## Requirements

- Minecraft 26.1.2
- Java 25
- [Fabric Loader](https://fabricmc.net/use/installer/)
- [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2)

## Installation

1. Install Fabric Loader for Minecraft 26.1.2.
2. Add Fabric API to your `mods` folder.
3. Download Eisenmann from [eisenmann.lol](https://eisenmann.lol) and place the JAR in the same folder.
4. Remove older `voidmark-*.jar` or `eisenmann-*.jar` files, then launch the Fabric profile.

You can also build the mod yourself and use the JAR generated in `build/libs`.

- Right Shift is the default keybind (Controls → Eisenmann). Press it again to close (the menu eases out).
- `/eisenmann toggle` or `/voidmark toggle` flips node markers without opening the menu.
- `/vm farmkeys` (or `/vm fk`) swaps the Attack/Destroy and Jump bindings, makes the swapped Attack/Destroy key toggle held attack, and lowers sensitivity to Minecraft's minimum. Run it again to restore your exact bindings and sensitivity.
- Toolbar **HUD** opens the HUD editor: drag any overlay (inventory, watermark, nodes, music, raw mats, mining) and every custom vanilla HUD piece (hotbar, bars, scoreboard, boss, effects, held item). They snap to screen axes and to each other; hold **Shift** to move freely. Click a panel, then drag the **Scale** bar or scroll the mouse wheel (50%–200%). **Reset** on the Bars tab restores default positions.
- A cog next to a feature toggle opens that feature’s subsettings in a side window.
- The toolbar gear opens **Theme**: **Accent**, **Pane** color, pane **Opacity**, **Font** (Minecraft, bundled Nunito, or any installed TrueType family — applied to every menu and HUD label except the watermark EISENMANN logo and the EISENMANN Dev nametag). Minecraft is drawn at Nunito's body/small/title sizes so it does not sit oversized next to those fonts. **Scale** (100% / 90% / 75% / 50%), **HUD** opacity for overlay panes, **Menu stars** for the click GUI, and **HUD stars**. Theme and feature panes stay more opaque than the main window so the text stays readable. Animation toggle is there too. **Auto update** (off by default) makes the next launch wait on eisenmann.lol; if a newer jar is published it replaces the one in `mods` and exits so the updated game can be started normally.
- The bell opens **What’s new**: a versioned changelog. An accent dot on the bell means there are notes you have not opened yet.
- Search (`Ctrl+F` or the magnifier) jumps to a setting.
- **Bars** (Bars tab) restyles vanilla HUD layers in the same pane look as the click GUI. Compact pieces (hotbar, health, hunger, armor, air, experience, mount health, boss bar, status effects, held item) have no accent rail. Overlay panes (music, nodes, mining, raw mats, inventory) and the scoreboard have no rail either — only the watermark keeps one. Each piece has its own switch. Turning one on hides that vanilla layer so they do not stack. Turn a switch off to get the original Minecraft HUD back. Drag and scale each piece in the HUD editor. The inventory HUD on Overlay is a separate overlay, not the hotbar replacement.
- **Inventory HUD** (Overlay tab cog) draws your armor, storage, and hotbar on-screen. Hotbar, armor/offhand, and the `n/41` count can each be toggled there. Move and scale it in the HUD editor. It reads the live inventory every frame. Hide it with F1; it also hides while a chest or the vanilla inventory is open.
- **Pickup log** (Overlay tab) shows items added to your inventory and their actual net quantities in a movable HUD list. This includes ground pickups and rewards that spawn directly in inventory. Repeated additions merge, slot moves do not count, and entries fade after five seconds.
- Click your **skull or name** in the sidebar for a 3D rotatable preview of your skin with nick and custom cape. The model fills the You card, follows the menu scale, and is drawn without armor or held items. A vanilla Minecraft nametag sits above the head. The Cape card stays locked until your UUID is on the shop list. Then paste a PNG URL, click **Local file...**, or **Create cape...** to crop any photo (PNG or JPEG) onto the 10×16 cape face: drag to pan, scroll to zoom, then Apply. The crop is baked into a vanilla cape atlas and published so everyone sees the same cut. **Refresh capes** (everyone can use it, even if the card is locked) pulls the latest shop capes and head tags for nearby players, at most once every 5 minutes. Other Eisenmann users also pick them up when they join a world. Vanilla **64×32** (and 128×64, 256×128, …) cape templates skip the cropper and are used as-is. **Nick** replaces your username in chat, tab, the scoreboard, and nametags. `&6` `&l` `&r` (and the rest of the legacy codes) work in the input; the preview under it is what other HUD text will look like.
- **Nametags** (ESP tab cog) default to Eisenmann-styled name plates in the Minecraft font that keep drawing past vanilla’s 64-block cutoff (range 64–256m). The Theme font picker does not apply to player names, distance, or head tags — Unicode in those plates uses vanilla so it actually draws, and Hypixel `§` color codes stay colors instead of leftover digits. **Style** on that cog switches to **Vanilla** chrome (Minecraft’s background box) while keeping the same range, Size slider (50–200%), Opacity, optional distance text, through-walls, and distance scaling. **Own nametag** is its own switch on ESP (off in first person either way) and shows your plate in F5; **EISENMANN Dev** only draws when that is on. UUID v2 entities (Hypixel NPCs) are skipped; only UUID v4 players get a plate. The Dev line uses the menu font and sits in the same plate as the name. Vanilla Minecraft tags are hidden while Eisenmann nametags are on so they do not stack. Your nick replaces your own name in F5.

### SkyBlock

- Ender Node boxes, outlines, tracers, particles, and a nearby-node HUD
- Custom Loadouts and Wardrobe menus with 3D equipment previews
- Mining commission progress and pickaxe ability cooldowns
- Titanium ESP that follows active commission locations
- Raw material tracking for SkyBlock recipes and storage
- Farming yaw and pitch display while holding a Farming Tool
- Farm Keys mode for swapping controls, toggling attack, and lowering sensitivity
- Chest ESP with configurable range, color, and aim assistance

### Visuals and ESP

- World, skybox, and fog tinting
- Custom aspect ratios
- Entity and nametag-based ESP
- Configurable block outlines and player nametags
- Hitsounds and hitmarkers for melee and ranged attacks
- Client-side item appearance overrides
- Custom capes and nicknames

### HUD and interface

- Movable and scalable HUD editor
- Watermark, inventory, music, mining, node, raw-material, and pickup overlays
- Restyled hotbar, health, armor, hunger, experience, scoreboard, and other vanilla HUD elements
- Pickup log for inventory gains, including direct rewards
- Custom title screen and consistent menu styling
- Configurable colors, fonts, opacity, scale, and animations

## Controls and commands

The default menu key is **Right Shift**. Keybinds can be changed under **Controls → Eisenmann**.

| Command | Description |
| --- | --- |
| `/eisenmann`, `/voidmark`, or `/vm` | Open the Eisenmann menu |
| `/eisenmann toggle` or `/voidmark toggle` | Toggle Ender Node markers |
| `/vm edit` | Open the held-item appearance editor |
| `/vm loadouts` | Open the custom Loadouts menu |
| `/vm wardrobe` | Open the custom Wardrobe menu |
| `/vm rawmats [item]` | Track raw materials for a SkyBlock item |
| `/vm rawmats refresh` | Refresh profile storage data |
| `/vm rawmats clear` | Hide the material tracker |
| `/vm esp <name>` | Add a nametag ESP filter |
| `/vm esp clear [name]` | Remove nametag ESP filters |
| `/vm farmkeys` or `/vm fk` | Toggle farming controls |
| `/vm music` | Show music integration status |

Music controls are also available through `.np`, `.play`, `.pause`, `.skip`, and `.prev`. These messages are handled locally and are not sent to the server.

## Configuration

**Jacob contest HUD** reads Hypixel's live player-list widget during a contest. It shows the current crop and bracket, predicts the finishing bracket and crop total from recent rates, and reports average crops per second and per score update. Move and scale it in the HUD editor.

## Settings

Settings are saved in:

```text
.minecraft/config/voidmark.json
```

## Building from source

Clone [camberX/Eisenmann](https://github.com/camberX/Eisenmann) and run:

```bash
./gradlew build
```

Java 25 is required. The built JAR is written to `build/libs`, and the website release files are synchronized under `web/public/mod`.

To launch a development client:

```bash
./gradlew runClient
```

## Website and cape service

The `web` directory contains the Eisenmann download site and cape service. Local development can be started with:

```bash
node web/server.mjs
```

The local site runs at `http://127.0.0.1:43150` by default. See [web/CLOUDFLARE.md](web/CLOUDFLARE.md) for deployment instructions.

## License

Eisenmann is released under the [CC0 1.0 Universal](LICENSE) public-domain dedication.
