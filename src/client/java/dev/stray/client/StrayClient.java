package dev.stray.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.client.combat.AutoClicker;
import dev.stray.client.combat.AutoClickerCommands;
import dev.stray.client.combat.AutoExperiments;
import dev.stray.client.combat.AutoRogue;
import dev.stray.client.combat.Hitmarker;
import dev.stray.client.combat.Hitsound;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.combat.Triggerbot;
import dev.stray.client.debug.StrayDebug;
import dev.stray.client.dungeon.PartyFinderStats;
import dev.stray.client.farming.AutoDna;
import dev.stray.client.farming.PestCooldown;
import dev.stray.client.farming.PestEsp;
import dev.stray.client.farming.GardenPlots;
import dev.stray.client.menu.DisabledPotions;
import dev.stray.client.menu.NoCursorReset;
import dev.stray.client.farming.FarmKeys;
import dev.stray.client.fairy.FairySoulCommands;
import dev.stray.client.fairy.FairySoulRenderer;
import dev.stray.client.fairy.FairySoulTracker;
import dev.stray.client.farming.FarmingHud;
import dev.stray.client.farming.ComposterTracker;
import dev.stray.client.farming.GardenHud;
import dev.stray.client.farming.JacobContestTracker;
import dev.stray.client.skill.SkillProgressTracker;
import dev.stray.client.account.AccountStore;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.update.UpdateCommands;
import dev.stray.update.AutoUpdate;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.net.ConnectionPing;
import dev.stray.client.net.LiveCommands;
import dev.stray.client.net.StrayLive;
import dev.stray.client.node.EnderNodeTracker;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.item.ItemIds;
import dev.stray.client.item.ItemStorage;
import dev.stray.client.item.RawmatsCommands;
import dev.stray.client.item.RawmatsTracker;
import dev.stray.client.item.SkyblockItems;
import dev.stray.client.item.StoragePreview;
import dev.stray.client.item.SkyblockProfileApi;
import dev.stray.client.item.SkyblockRecipes;
import dev.stray.client.media.MediaChat;
import dev.stray.client.media.MediaSession;
import dev.stray.client.media.SpotifySmtc;
import dev.stray.client.mining.ChestAimer;
import dev.stray.client.mining.ChestEsp;
import dev.stray.client.mining.CrystalHollows;
import dev.stray.client.mining.CrystalHollowsRenderer;
import dev.stray.client.mining.CrystalHollowsMap;
import dev.stray.client.mining.MetalDetector;
import dev.stray.client.mining.NucleusAlerts;
import dev.stray.client.movement.AotvSim;
import dev.stray.client.movement.CommandRingCommands;
import dev.stray.client.movement.CommandRings;
import dev.stray.client.movement.MovementRingCommands;
import dev.stray.client.movement.MovementRings;
import dev.stray.client.movement.PathCommands;
import dev.stray.client.movement.PathRecorder;
import dev.stray.client.movement.PathWalker;
import dev.stray.client.pip.PipCapture;
import dev.stray.client.mining.MiningTracker;
import dev.stray.client.mining.TitaniumTracker;
import dev.stray.client.render.PestEspRenderer;
import dev.stray.client.render.ChestEspRenderer;
import dev.stray.client.render.ComposterHudRenderer;
import dev.stray.client.render.GardenHudRenderer;
import dev.stray.client.render.InventoryHudRenderer;
import dev.stray.client.render.JacobContestHudRenderer;
import dev.stray.client.render.SkillProgressHudRenderer;
import dev.stray.client.render.MusicHudRenderer;
import dev.stray.client.render.PipHudRenderer;
import dev.stray.client.render.EntityHealthBars;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.render.NodeHudRenderer;
import dev.stray.client.render.PickupLogRenderer;
import dev.stray.client.render.RawmatsHudRenderer;
import dev.stray.client.render.MiningHudRenderer;
import dev.stray.client.render.MiningWorldRenderer;
import dev.stray.client.render.BlockMarks;
import dev.stray.client.render.BlockOutlineGlow;
import dev.stray.client.render.EspCommands;
import dev.stray.client.render.MobGlowRenderer;
import dev.stray.client.render.StarMobEsp;
import dev.stray.client.render.NodeWorldRenderer;
import dev.stray.client.render.VanillaHud;
import dev.stray.client.render.WatermarkRenderer;
import dev.stray.client.ui.CommandShortcutScreen;
import dev.stray.client.ui.CommandShortcuts;
import dev.stray.client.ui.HudEditorScreen;
import dev.stray.client.ui.ItemEditScreen;
import dev.stray.client.ui.LoadoutsCommands;
import dev.stray.client.ui.LoadoutSwap;
import dev.stray.client.ui.LoadoutsScreen;
import dev.stray.client.ui.ProfileCommands;
import dev.stray.client.ui.ProfileViewerScreen;
import dev.stray.client.ui.WardrobeCommands;
import dev.stray.client.ui.WardrobeScreen;
import dev.stray.client.ui.Theme;
import dev.stray.client.ui.UiFontPack;
import dev.stray.client.ui.StrayScreen;
import dev.stray.client.update.UpdateNotifier;
import dev.stray.client.visual.CustomCape;
import dev.stray.client.visual.EndSkyDecor;
import dev.stray.client.visual.NickSteal;
import dev.stray.client.visual.ShopCape;
import dev.stray.client.visual.motionblur.MotionBlurShaders;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class StrayClient implements ClientModInitializer {
	private static boolean itemAppearancesLoaded;
	private static boolean wasGui;
	private static boolean wasLoadouts;
	private static boolean wasLoadoutSwap;
	private static boolean wasWardrobe;
	private static boolean wasProfile;
	private static boolean wasPing;

	public static boolean loadoutsKey(KeyEvent event) {
		return menuKeyMatches(StrayConfig.get().openLoadoutsKey, event);
	}

	public static boolean wardrobeKey(KeyEvent event) {
		return menuKeyMatches(StrayConfig.get().openWardrobeKey, event);
	}

	public static boolean profileKey(KeyEvent event) {
		return menuKeyMatches(StrayConfig.get().openProfileKey, event);
	}

	public static boolean strayHotkeys(Minecraft client) {
		return client != null && client.screen == null;
	}

	public static boolean menuKeyHeld(String keyName) {
		return OdinClicks.isPressed(OdinClicks.parseKey(keyName));
	}

	public static void syncMenuBindEdges() {
		StrayConfig config = StrayConfig.get();
		wasGui = menuKeyHeld(config.openGuiKey);
		wasLoadouts = menuKeyHeld(config.openLoadoutsKey);
		wasLoadoutSwap = menuKeyHeld(config.loadoutSwapKey);
		wasWardrobe = menuKeyHeld(config.openWardrobeKey);
		wasProfile = menuKeyHeld(config.openProfileKey);
		wasPing = menuKeyHeld(config.strayPingKey);
	}

	public static boolean menuKeyMatches(String keyName, KeyEvent event) {
		if (event == null) {
			return false;
		}
		InputConstants.Key mapped = OdinClicks.parseKey(keyName);
		if (!OdinClicks.bound(mapped) || mapped.getType() != InputConstants.Type.KEYSYM) {
			return false;
		}
		return event.key() == mapped.getValue();
	}

	@Override
	public void onInitializeClient() {
		StrayConfig.load();
		AccountStore.load();
		AutoUpdate.clientLaunchCheck(StrayConfig.get().autoUpdate, StrayConfig.get().updateNotify);
		Theme.refresh();
		SkyblockItems.load();
		SkyblockRecipes.load();
		RawmatsTracker.init();
		CustomCape.init();
		NickSteal.init();
		NodeWorldRenderer.init();
		MobGlowRenderer.init();
		BlockOutlineGlow.init();
		BlockMarks.init();
		StrayLive.init();
		PathRecorder.init();
		PathWalker.init();
		CommandRings.init();
		MovementRings.init();
		MiningWorldRenderer.init();
		ChestEspRenderer.init();
		PestEspRenderer.init();
		FairySoulRenderer.init();
		FairySoulTracker.init();
		CrystalHollowsRenderer.init();
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			MetalDetector.onMessage(message, overlay);
			if (overlay) {
				SkillProgressTracker.onActionBar(message);
			}
			return CrystalHollows.allowChat(message, overlay);
		});
		ChestAimer.init();
		Hitmarker.init();
		FarmingHud.init();
		JacobContestHudRenderer.init();
		SkillProgressHudRenderer.init();
		ComposterHudRenderer.init();
		GardenHudRenderer.init();
		WatermarkRenderer.init();
		InventoryHudRenderer.init();
		NodeHudRenderer.init();
		PickupLogRenderer.init();
		MusicHudRenderer.init();
		PipHudRenderer.init();
		RawmatsHudRenderer.init();
		MiningHudRenderer.init();
		NametagRenderer.init();
		EntityHealthBars.init();
		EndSkyDecor.init();
		VanillaHud.init();
		MediaSession.init();
		AutoExperiments.init();
		AutoDna.init();
		GardenPlots.init();
		StoragePreview.init();
		DisabledPotions.init();
		UpdateNotifier.init();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var root = ClientCommands.literal("stray").executes(context -> openScreen());
			root.then(ClientCommands.literal("toggle").executes(context -> {
				StrayConfig config = StrayConfig.get();
				config.markersEnabled = !config.markersEnabled;
				config.save();
				Minecraft client = Minecraft.getInstance();
				if (client.player != null) {
					client.player.sendSystemMessage(
						Component.literal("Stray markers " + (config.markersEnabled ? "enabled" : "disabled"))
					);
				}
				return Command.SINGLE_SUCCESS;
			}));
			root.then(StrayDebug.command());
			root.then(ClientCommands.literal("edit").executes(context -> openItemEdit()));
			root.then(ClientCommands.literal("farmkeys").executes(context -> FarmKeys.toggle()));
			root.then(ClientCommands.literal("fk").executes(context -> FarmKeys.toggle()));
			root.then(musicCommand());
			root.then(RawmatsCommands.command());
			root.then(EspCommands.command());
			root.then(FairySoulCommands.command());
			root.then(FairySoulCommands.shortCommand());
			root.then(LoadoutsCommands.command());
			root.then(WardrobeCommands.command());
			root.then(ProfileCommands.command());
			root.then(PathCommands.command());
			root.then(CommandRingCommands.command());
			root.then(MovementRingCommands.command());
			root.then(ClientCommands.literal("shortcuts").executes(context -> CommandShortcuts.open()));
			root.then(LiveCommands.irc());
			root.then(LiveCommands.ping());
			root.then(UpdateCommands.command());
			root.then(stealCommand());
			root.then(PartyFinderStats.command());
			var brand = dispatcher.register(root);
			dispatcher.register(ClientCommands.literal("st").redirect(brand));
			dispatcher.register(ClientCommands.literal("voidmark").redirect(brand));
			dispatcher.register(ClientCommands.literal("eisenmann").redirect(brand));
			var vm = ClientCommands.literal("vm").executes(context -> openScreen());
			vm.then(ClientCommands.literal("edit").executes(context -> openItemEdit()));
			vm.then(ClientCommands.literal("farmkeys").executes(context -> FarmKeys.toggle()));
			vm.then(ClientCommands.literal("fk").executes(context -> FarmKeys.toggle()));
			vm.then(musicCommand());
			vm.then(RawmatsCommands.command());
			vm.then(EspCommands.command());
			vm.then(FairySoulCommands.command());
			vm.then(FairySoulCommands.shortCommand());
			vm.then(LoadoutsCommands.command());
			vm.then(WardrobeCommands.command());
			vm.then(ProfileCommands.command());
			vm.then(PathCommands.command());
			vm.then(CommandRingCommands.command());
			vm.then(MovementRingCommands.command());
			vm.then(ClientCommands.literal("shortcuts").executes(context -> CommandShortcuts.open()));
			vm.then(LiveCommands.irc());
			vm.then(LiveCommands.ping());
			vm.then(UpdateCommands.command());
			vm.then(stealCommand());
			vm.then(PartyFinderStats.command());
			dispatcher.register(vm);
			dispatcher.register(ClientCommands.literal("loadouts").executes(context -> LoadoutsCommands.open()));
			dispatcher.register(ClientCommands.literal("loadout").executes(context -> LoadoutsCommands.open()));
			dispatcher.register(ClientCommands.literal("ld").executes(context -> LoadoutsCommands.open()));
			dispatcher.register(ClientCommands.literal("wardrobe").executes(context -> WardrobeCommands.open()));
			dispatcher.register(ClientCommands.literal("wd").executes(context -> WardrobeCommands.open()));
			dispatcher.register(ProfileCommands.command());
			dispatcher.register(ClientCommands.literal("profile")
				.executes(context -> ProfileCommands.open(""))
				.then(ClientCommands.argument("player", StringArgumentType.greedyString())
					.executes(context -> ProfileCommands.open(StringArgumentType.getString(context, "player")))));
			dispatcher.register(AutoClickerCommands.command());
			dispatcher.register(LiveCommands.irc());
			dispatcher.register(LiveCommands.ping());
			CommandShortcuts.register(dispatcher);
		});

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			AutoRogue.tick(client);
			FarmKeys.tick(client);
			ChestAimer.tick(client);
			AutoClicker.tick(client);
			AutoExperiments.tick(client);
			AutoDna.tick(client);
			if (itemAppearancesLoaded) {
				return;
			}
			if (!ItemIds.componentsReady()) {
				return;
			}
			try {
				ItemAppearance.reload();
				itemAppearancesLoaded = true;
			} catch (RuntimeException ignored) {
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			pollMenuKeys(client);
			StrayConfig running = StrayConfig.get();
			SpotifySmtc.tick(running.musicHudEnabled && running.spotifyEnabled);
			SkyblockLocation.tick(client);
			ItemStorage.tick(client);
			StoragePreview.tick(client);
			LoadoutsScreen.tickSwap(client);
			WardrobeScreen.tickSwap(client);
			NoCursorReset.tick(client);
			Hitsound.tick(client);
			PickupLogRenderer.tick(client);
			JacobContestTracker.tick(client);
			SkillProgressTracker.tick(client);
			ComposterTracker.tick(client);
			GardenHud.tick(client);
			PestCooldown.tick(client);
			StrayDebug.tick(client);
			EnderNodeTracker.get().tick(client);
			ConnectionPing.tick(client);
			RawmatsTracker.tick(client);
			MiningTracker.tick(client);
			TitaniumTracker.get().tick(client);
			ChestEsp.get().tick(client);
			PestEsp.tick(client);
			FairySoulTracker.tick(client);
			CrystalHollows.tick(client);
			CrystalHollowsMap.tick(client);
			MetalDetector.tick(client);
			NucleusAlerts.tick(client);
			BlockMarks.tick(client);
			PathRecorder.tick(client);
			CommandRings.tick(client);
			MovementRings.tick(client);
			PathWalker.tick(client);
			PipCapture.tick(client);
			AotvSim.tick();
			ShopCape.tick();
			StrayLive.tick(client);
			UiFontPack.tick(client);
			UpdateNotifier.tick();
		});

		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
			AutoRogue.onWorldChange();
			FarmKeys.onWorldChange();
			MobGlowRenderer.reset();
			StarMobEsp.reset();
			ChestAimer.stop();
			ChestEsp.get().clear();
			PestEsp.reset();
			PestCooldown.reset();
			CrystalHollows.onWorldChange();
			MetalDetector.onWorldChange();
			BlockMarks.onWorldChange();
			CommandRings.onWorldChange();
			MovementRings.onWorldChange();
			PathWalker.stop(false);
			NucleusAlerts.reset();
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			AutoRogue.onWorldChange();
			CrystalHollows.onWorldChange();
			MetalDetector.onWorldChange();
			MobGlowRenderer.reset();
			StarMobEsp.reset();
			Hitsound.reset();
			Hitmarker.reset();
			Triggerbot.reset();
			AutoClicker.reset();
			AutoExperiments.reset();
			AutoDna.reset();
			PestEsp.reset();
			PestCooldown.reset();
			DisabledPotions.reset();
			PickupLogRenderer.clear();
			JacobContestTracker.reset();
			SkillProgressTracker.reset();
			ComposterTracker.reset();
			GardenHud.reset();
			SkyblockProfileApi.refresh();
			ShopCape.onJoin();
			CommandRings.onWorldChange();
			MovementRings.onWorldChange();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			FarmKeys.restore();
			SkyblockLocation.reset();
			Hitsound.reset();
			Hitmarker.reset();
			Triggerbot.reset();
			AutoClicker.reset();
			AutoExperiments.reset();
			AutoDna.reset();
			PestEsp.reset();
			PestCooldown.reset();
			DisabledPotions.reset();
			PickupLogRenderer.clear();
			JacobContestTracker.reset();
			SkillProgressTracker.reset();
			ComposterTracker.reset();
			GardenHud.reset();
			CommandRings.onWorldChange();
			MovementRings.onWorldChange();
			EnderNodeTracker.get().clear();
			ConnectionPing.reset();
			MiningTracker.reset();
			TitaniumTracker.get().clear();
			ChestEsp.get().clear();
			PestEsp.reset();
			PestCooldown.reset();
			ChestAimer.stop();
			MobGlowRenderer.reset();
			StarMobEsp.reset();
			LoadoutsScreen.resetPending();
			WardrobeScreen.resetPending();
			MotionBlurShaders.invalidate();
			CrystalHollows.reset();
			CrystalHollowsMap.close();
			MetalDetector.reset();
			StrayLive.disconnect();
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			FarmKeys.restore();
			PipCapture.stop();
			StrayLive.disconnect();
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> stealCommand() {
		return ClientCommands.literal("steal")
			.executes(context -> {
				stealChat(NickSteal.statusLabel());
				return Command.SINGLE_SUCCESS;
			})
			.then(ClientCommands.literal("off").executes(context -> {
				NickSteal.stop();
				stealChat("Stopped stealing.");
				return Command.SINGLE_SUCCESS;
			}))
			.then(ClientCommands.literal("debug").executes(context -> {
				String seen = NickSteal.lastSeen();
				stealChat(seen.isEmpty() ? "No chat line with your name seen yet." : "Last line: " + seen);
				return Command.SINGLE_SUCCESS;
			}))
			.then(ClientCommands.argument("player", StringArgumentType.word()).executes(context -> {
				String name = StringArgumentType.getString(context, "player");
				NickSteal.steal(name);
				stealChat("Stealing " + name + "…");
				return Command.SINGLE_SUCCESS;
			}));
	}

	private static void stealChat(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(Component.literal("Stray | " + text));
		}
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> musicCommand() {
		return ClientCommands.literal("music")
			.executes(context -> MediaChat.nowPlaying())
			.then(ClientCommands.literal("play").executes(context -> MediaChat.toggle()))
			.then(ClientCommands.literal("pause").executes(context -> MediaChat.toggle()))
			.then(ClientCommands.literal("next").executes(context -> MediaChat.skip(true)))
			.then(ClientCommands.literal("prev").executes(context -> MediaChat.skip(false)))
			.then(ClientCommands.literal("np").executes(context -> MediaChat.nowPlaying()));
	}

	private static void pollMenuKeys(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		boolean gui = menuKeyHeld(config.openGuiKey);
		boolean loadouts = menuKeyHeld(config.openLoadoutsKey);
		boolean swapLoadout = menuKeyHeld(config.loadoutSwapKey);
		boolean wardrobe = menuKeyHeld(config.openWardrobeKey);
		boolean profile = menuKeyHeld(config.openProfileKey);
		boolean ping = menuKeyHeld(config.strayPingKey);
		if (!ignoreMenuBinds(client)) {
			if (gui && !wasGui) {
				handleOpenGui(client);
			}
			if (loadouts && !wasLoadouts) {
				if (client.screen instanceof LoadoutsScreen screen) {
					screen.onClose();
				} else {
					LoadoutsCommands.open();
				}
			}
			if (swapLoadout && !wasLoadoutSwap) {
				LoadoutSwap.toggle();
			}
			if (wardrobe && !wasWardrobe) {
				if (client.screen instanceof WardrobeScreen screen) {
					screen.onClose();
				} else {
					WardrobeCommands.open();
				}
			}
			if (profile && !wasProfile && StrayConfig.get().profileViewerEnabled) {
				if (client.screen instanceof ProfileViewerScreen screen) {
					screen.onClose();
				} else {
					ProfileCommands.open("");
				}
			}
			if (ping && !wasPing && strayHotkeys(client) && config.strayPingEnabled) {
				LiveCommands.ping("");
			}
		}
		wasGui = gui;
		wasLoadouts = loadouts;
		wasLoadoutSwap = swapLoadout;
		wasWardrobe = wardrobe;
		wasProfile = profile;
		wasPing = ping;
	}

	private static boolean ignoreMenuBinds(Minecraft client) {
		var screen = client.screen;
		if (screen == null) {
			return false;
		}
		if (screen instanceof ChatScreen) {
			return true;
		}
		if (screen instanceof StrayScreen stray && stray.shouldIgnoreMenuBinds()) {
			return true;
		}
		if (screen instanceof ProfileViewerScreen viewer && viewer.queryFocused()) {
			return true;
		}
		if (screen.getFocused() instanceof EditBox) {
			return true;
		}
		return !(screen instanceof StrayScreen
			|| screen instanceof LoadoutsScreen
			|| screen instanceof WardrobeScreen
			|| screen instanceof ProfileViewerScreen
			|| screen instanceof HudEditorScreen
			|| screen instanceof ItemEditScreen
			|| screen instanceof CommandShortcutScreen);
	}

	private static void handleOpenGui(Minecraft client) {
		if (client.screen instanceof HudEditorScreen) {
			client.setScreen(new StrayScreen());
		} else if (client.screen instanceof CommandShortcutScreen screen) {
			screen.onClose();
		} else if (client.screen instanceof StrayScreen screen) {
			screen.requestClose();
		} else if (client.screen instanceof ItemEditScreen) {
			client.setScreen(null);
		} else {
			openScreen();
		}
	}

	private static int openScreen() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.screen instanceof StrayScreen screen) {
				screen.requestClose();
			} else {
				client.setScreen(new StrayScreen());
			}
		});
		return Command.SINGLE_SUCCESS;
	}

	private static int openItemEdit() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new ItemEditScreen()));
		return Command.SINGLE_SUCCESS;
	}
}
