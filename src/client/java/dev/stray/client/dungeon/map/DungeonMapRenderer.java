package dev.stray.client.dungeon.map;

import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudChrome;
import dev.stray.client.render.HudLayout;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.ShadowLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The dungeon map HUD in the Shadow look: the see-through black panel with its soft
 * outward shadow, rooms as soft rounded tiles in muted map colours, Inter for names,
 * heads that glide, and rooms and checkmarks that fade in as they appear. Layout and
 * rules follow NoammAddons' MapRenderer (CC0).
 */
public final class DungeonMapRenderer {
	private static final float PAD = 4f;
	private static final float MAP = 128f;
	private static final float ROOM_RADIUS = 2.5f;
	private static final float FADE_MS = 260f;

	private static final Identifier CHECK_GREEN = Stray.id("textures/gui/dungeonmap/green_check.png");
	private static final Identifier CHECK_WHITE = Stray.id("textures/gui/dungeonmap/white_check.png");
	private static final Identifier CHECK_UNKNOWN = Stray.id("textures/gui/dungeonmap/question.png");
	private static final Identifier CHECK_FAIL = Stray.id("textures/gui/dungeonmap/cross.png");
	/** The game's own map marker for a player (white arrow), 8x8. */
	private static final Identifier MAP_ARROW = Identifier.withDefaultNamespace("textures/map/decorations/player.png");

	private static final int GREEN = 0xFF7DE08E;
	private static final int RED = 0xFFFF6E6E;

	/** When each tile was first drawn, so it fades in. Keyed by the tile object itself. */
	private static final Map<Object, Long> SEEN = new IdentityHashMap<>();
	/** Room checkmarks: the state shown and when it changed, so a new mark pops in. */
	private static final Map<Object, long[]> MARKS = new IdentityHashMap<>();

	private DungeonMapRenderer() {
	}

	/** New run: forget the fade-in times (they hold the last run's tiles). */
	public static void reset() {
		SEEN.clear();
		MARKS.clear();
	}

	public static float drawWidth() {
		return MAP + PAD * 2f;
	}

	public static float drawHeight() {
		return MAP + PAD * 2f;
	}

	/** In a run (unless hidden in boss), while previewing, or in the HUD editor. */
	public static boolean showing() {
		StrayConfig config = StrayConfig.get();
		if (!config.dungeonMapEnabled) {
			return false;
		}
		if (DungeonMap.previewActive() || HudLayout.editorOpen()) {
			return true;
		}
		return SkyblockLocation.inDungeon && !(SkyblockLocation.inBoss && config.dungeonMapHideInBoss);
	}

	private static boolean example() {
		return DungeonMap.previewActive() || !SkyblockLocation.inDungeon;
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden() || !showing()) {
			return;
		}
		HudLayout.apply(graphics, client.font, HudLayout.Id.DUNGEON_MAP, () -> draw(graphics, client.font, 0f, 0f));
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y) {
		long now = System.currentTimeMillis();
		if (HudChrome.background()) {
			ShadowLook.panel(graphics, x, y, drawWidth(), drawHeight(), ShadowLook.HUD_R, ShadowLook.FILL);
		}
		boolean example = example();
		// Map size: the rooms grow inside the panel (around its middle), the panel stays.
		float zoom = Mth.clamp(StrayConfig.get().dungeonMapZoom / 100f, 1f, 2f);
		boolean clip = zoom > 1.001f && GuiDraw.scissor(graphics, x + 1f, y + 1f, drawWidth() - 2f, drawHeight() - 2f);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + PAD, y + PAD);
		if (zoom > 1.001f) {
			graphics.pose().translate(MAP / 2f, MAP / 2f);
			graphics.pose().scale(zoom, zoom);
			graphics.pose().translate(-MAP / 2f, -MAP / 2f);
		}
		float sx = MapCalibration.drawStartX();
		float sz = MapCalibration.drawStartZ();
		graphics.pose().pushMatrix();
		graphics.pose().translate(sx, sz);
		if (example) {
			MapPreview.Built preview = MapPreview.get();
			rooms(graphics, preview.tiles(), MapPreview.CONNECTOR_SIZE, true, preview, now);
			text(graphics, font, preview.rooms(), MapPreview.CONNECTOR_SIZE, MapCalibration.mapRoomSize / 2, true, preview, now);
		} else {
			if (DungeonMap.cheater()) {
				// Like NoammAddons: on the map that shows everything, "unopened" reads as "not found yet".
				for (Tile tile : DungeonScanner.GRID) {
					if (tile.state() == RoomState.UNOPENED) {
						tile.setState(RoomState.UNDISCOVERED);
					}
				}
			}
			int connector = HotbarMapScanner.quarterRoom == -1 ? 4 : HotbarMapScanner.quarterRoom;
			rooms(graphics, DungeonScanner.GRID, connector, false, null, now);
			text(graphics, font, DungeonScanner.UNIQUE_ROOMS.values(), connector, HotbarMapScanner.halfRoom, false, null, now);
		}
		graphics.pose().popMatrix();
		if (example) {
			for (MapPlayer player : MapPreview.get().players()) {
				head(graphics, font, player, now, true);
			}
		} else if (!SkyblockLocation.inBoss) {
			for (MapPlayer player : DungeonMap.heads()) {
				head(graphics, font, player, now, false);
			}
		}
		graphics.pose().popMatrix();
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
	}

	// ------------------------------------------------------------------ rooms

	private static RoomState doorState(DoorTile door) {
		if (door.roomTileIndices.length != 2) {
			return RoomState.UNDISCOVERED;
		}
		for (RoomTile room : door.roomTiles()) {
			if (room.state() == RoomState.UNDISCOVERED) {
				return RoomState.UNDISCOVERED;
			}
		}
		return RoomState.UNOPENED;
	}

	private static void rooms(GuiGraphicsExtractor graphics, Tile[] tiles, int connector, boolean preview, MapPreview.Built built, long now) {
		boolean cheater = DungeonMap.cheater();
		java.util.List<Integer> unknownCells = new java.util.ArrayList<>();
		int size = MapCalibration.mapRoomSize;
		for (int y = 0; y <= 10; y++) {
			for (int x = 0; x <= 10; x++) {
				int index = y * 11 + x;
				Tile tile = tiles[index];
				if (tile instanceof UnknownTile) {
					continue;
				}
				RoomState state = tile.state();
				if (state == RoomState.UNDISCOVERED && !cheater) {
					continue;
				}
				if (tile instanceof DoorTile door && doorState(door) == RoomState.UNDISCOVERED && !cheater) {
					continue;
				}
				int color = tile.color();
				if (cheater && state == RoomState.UNDISCOVERED) {
					color = MapColors.mix(color, 0xFF000000, MapColors.UNDISCOVERED_TINT);
				}
				float fade = fade(tile, now);
				// Opaque: the pieces of a big room overlap a little where they join, and
				// see-through pieces showed those overlaps as darker seams.
				color = MapColors.alpha(color, fade);
				float xo = (x >> 1) * (size + connector);
				float yo = (y >> 1) * (size + connector);
				boolean xEven = (x & 1) == 0;
				boolean yEven = (y & 1) == 0;
				List<RoomType> predicted = predicted(tile, index, preview, built);
				if (xEven && yEven) {
					if (predicted != null) {
						predictedRoom(graphics, xo, yo, predicted, fade);
					} else if (tile instanceof RoomTile) {
						GuiDraw.roundedFine(graphics, xo, yo, size, size, ROOM_RADIUS, color);
					}
				} else if (!xEven && !yEven) {
					// Middle of a 2x2: fills the gap between its four cells.
					GuiDraw.fill(graphics, xo + size - ROOM_RADIUS, yo + size - ROOM_RADIUS, connector + ROOM_RADIUS * 2f, connector + ROOM_RADIUS * 2f, color);
				} else {
					connector(graphics, xo, yo, size, connector, tile instanceof DoorTile, !xEven, color);
				}
				if (tile instanceof RoomTile room && room.data.isUnknown() && predicted == null && xEven && yEven && !room.isSeparator) {
					unknownCells.add(index);
				}
			}
		}
		unknownCheckmarks(graphics, tiles, unknownCells, size, connector, now);
	}

	/**
	 * Rooms only the map item knows (no room data): one mark per room, at the middle of
	 * all its cells (a 2x2 gets it in the centre, a 1x3 on the middle cell, an L on its
	 * corner cell), instead of one per cell.
	 */
	private static void unknownCheckmarks(GuiGraphicsExtractor graphics, Tile[] tiles, java.util.List<Integer> cells, int size, int connector, long now) {
		java.util.Set<Integer> left = new java.util.HashSet<>(cells);
		for (int start : cells) {
			if (!left.remove(start)) {
				continue;
			}
			java.util.List<Integer> group = new java.util.ArrayList<>();
			java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
			queue.add(start);
			while (!queue.isEmpty()) {
				int at = queue.poll();
				group.add(at);
				int x = at % 11;
				int y = at / 11;
				int[][] steps = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
				for (int[] step : steps) {
					int nx = x + step[0];
					int ny = y + step[1];
					int next = ny * 11 + nx;
					if (nx < 0 || nx > 10 || ny < 0 || ny > 10 || !left.contains(next)) {
						continue;
					}
					// Same room only if the piece between them is a join, not a door.
					Tile between = tiles[(y + step[1] / 2) * 11 + x + step[0] / 2];
					if (between instanceof RoomTile join && join.isSeparator) {
						left.remove(next);
						queue.add(next);
					}
				}
			}
			float sumX = 0f;
			float sumY = 0f;
			RoomTile mark = null;
			for (int cell : group) {
				sumX += cell % 11;
				sumY += cell / 11;
				RoomTile tile = (RoomTile) tiles[cell];
				if (mark == null || rank(tile.state()) > rank(mark.state())) {
					mark = tile;
				}
			}
			float gx = sumX / group.size();
			float gy = sumY / group.size();
			// An L's middle is outside it: use the cell closest to the middle.
			if (group.size() == 3 && gx != Math.round(gx) && gy != Math.round(gy)) {
				int best = group.get(0);
				float bestD = Float.MAX_VALUE;
				for (int cell : group) {
					float d = (cell % 11 - gx) * (cell % 11 - gx) + (cell / 11 - gy) * (cell / 11 - gy);
					if (d < bestD) {
						bestD = d;
						best = cell;
					}
				}
				gx = best % 11;
				gy = best / 11;
			}
			float cell = size + connector;
			float appear = pop(tiles[group.get(0)], mark.state(), now);
			checkmark(graphics, mark.state(), gx / 2f * cell + size / 2f, gy / 2f * cell + size / 2f, 10f * (0.7f + 0.3f * appear), appear);
		}
	}

	/** Which cell's state a room shows: the most finished one. */
	private static int rank(RoomState state) {
		return switch (state) {
			case GREEN -> 5;
			case CLEARED -> 4;
			case FAILED -> 3;
			case UNOPENED -> 1;
			default -> 2;
		};
	}

	private static List<RoomType> predicted(Tile tile, int index, boolean preview, MapPreview.Built built) {
		if (!StrayConfig.get().dungeonMapPredict || DungeonMap.cheater()) {
			return null;
		}
		if (!(tile instanceof RoomTile) || tile.state() != RoomState.UNOPENED) {
			return null;
		}
		if (preview) {
			return built == null ? null : built.predictions().get(index);
		}
		return SpecialColumn.predict(index);
	}

	/** An unopened 1x1 that can only be some kinds of room: split into their colours. */
	private static void predictedRoom(GuiGraphicsExtractor graphics, float x, float y, List<RoomType> types, float fade) {
		int size = MapCalibration.mapRoomSize;
		int[] colors = new int[types.size()];
		for (int i = 0; i < colors.length; i++) {
			RoomType type = types.get(i);
			int c = MapColors.room(type == RoomType.CHAMPION || type == RoomType.TRAP ? type : RoomType.PUZZLE);
			colors[i] = MapColors.alpha(colors.length > 1 ? MapColors.darker(c) : c, fade);
		}
		if (colors.length == 1) {
			GuiDraw.roundedFine(graphics, x, y, size, size, ROOM_RADIUS, colors[0]);
		} else if (colors.length == 2) {
			GuiDraw.fill(graphics, x, y, size, size / 2f, colors[0]);
			GuiDraw.fill(graphics, x, y + size / 2f, size, size - size / 2f, colors[1]);
		} else if (colors.length >= 3) {
			float outer = size * 5f / 16f;
			float center = size - outer;
			float inner = center - 1f;
			GuiDraw.fill(graphics, x, y, size, outer, colors[0]);
			GuiDraw.fill(graphics, x, y, outer, inner, colors[0]);
			GuiDraw.fill(graphics, x + inner, y + outer, size - inner, size - outer, colors[1]);
			GuiDraw.fill(graphics, x, y + inner, size, size - inner, colors[1]);
			GuiDraw.fill(graphics, x + outer, y + outer, center - outer, center - outer, colors[2]);
		}
	}

	/**
	 * The piece between two cells: a small rounded door, or (inside one room) a bridge that
	 * reaches a little into both cells so their rounded corners join seamlessly.
	 */
	private static void connector(GuiGraphicsExtractor graphics, float x, float y, int size, int width, boolean doorway, boolean vertical, int color) {
		if (doorway) {
			float door = 6f;
			float offset = (size - door) / 2f;
			float dx = vertical ? x + size : x + offset;
			float dy = vertical ? y + offset : y + size;
			float dw = vertical ? width : door;
			float dh = vertical ? door : width;
			GuiDraw.roundedFine(graphics, dx - (vertical ? 0.5f : 0f), dy - (vertical ? 0f : 0.5f), dw + (vertical ? 1f : 0f), dh + (vertical ? 0f : 1f), 1f, color);
			return;
		}
		float r = ROOM_RADIUS;
		if (vertical) {
			GuiDraw.fill(graphics, x + size - r, y, width + r * 2f, size, color);
		} else {
			GuiDraw.fill(graphics, x, y + size - r, size, width + r * 2f, color);
		}
	}

	// ------------------------------------------------------------------ text and checkmarks

	private static void text(GuiGraphicsExtractor graphics, Font font, Collection<UniqueRoom> rooms, int connector, int halfRoomSize, boolean preview, MapPreview.Built built, long now) {
		StrayConfig config = StrayConfig.get();
		boolean cheater = DungeonMap.cheater();
		float roomSize = MapCalibration.mapRoomSize;
		float gap = connector;
		float half = halfRoomSize;
		float cell = roomSize + gap;
		for (UniqueRoom room : rooms) {
			if (room.data.isUnknown()) {
				continue;
			}
			RoomTile unopened = null;
			for (RoomTile tile : room.tiles) {
				if (!tile.isSeparator && tile.state() == RoomState.UNOPENED) {
					unopened = tile;
					break;
				}
			}
			RoomTile main = room.mainRoom;
			if (!cheater && unopened != null) {
				int row = unopened.row();
				int column = unopened.column();
				if (predicted(unopened, row * 11 + column, preview, built) != null) {
					continue;
				}
				float xo = (column >> 1) * (roomSize + connector);
				float yo = (row >> 1) * (roomSize + connector);
				roomCheckmark(graphics, unopened, xo, yo, now);
				continue;
			}
			if (room.data.type() == RoomType.ENTRANCE || room.data.type() == RoomType.FAIRY) {
				continue;
			}
			if (!cheater && main.state() == RoomState.UNDISCOVERED) {
				continue;
			}
			float cx = (room.checkColumn() / 2f) * cell + half;
			float cy = (room.checkRow() / 2f) * cell + half;
			int color = switch (main.state()) {
				case GREEN -> GREEN;
				case FAILED -> RED;
				case CLEARED -> 0xFFFFFFFF;
				default -> 0xFFD6DBE2;
			};
			int style = config.dungeonMapRoomInfo;
			if (style == 4) {
				style = main.data.type() == RoomType.PUZZLE || main.data.secrets() == 0 ? 2 : 1;
			}
			float appear = pop(room, main.state(), now);
			color = MapColors.alpha(color, appear);
			switch (style) {
				case 2, 3 -> roomName(graphics, font, room, main, style == 3, cx, cy, roomSize, gap, color);
				case 1 -> {
					String secrets = main.data.secrets() == 0 ? "0" : room.foundSecrets() + "/" + main.data.secrets();
					centered(graphics, font, secrets, cx, cy - 4.5f * 0.75f, 0.75f, color);
				}
				default -> checkmark(graphics, main.state(), cx, cy, 10f * (0.7f + 0.3f * appear), appear);
			}
		}
	}

	private static void roomName(GuiGraphicsExtractor graphics, Font font, UniqueRoom room, RoomTile main, boolean showSecrets, float cx, float cy, float roomSize, float gap, int color) {
		showSecrets &= main.data.secrets() > 0;
		String secrets = showSecrets ? room.foundSecrets() + "/" + main.data.secrets() : "";
		room.updateBounds(roomSize, gap);
		float widest = 0f;
		for (String line : room.splitName) {
			widest = Math.max(widest, width(font, line));
		}
		if (showSecrets) {
			widest = Math.max(widest, width(font, secrets));
		}
		int lines = room.splitName.length + (showSecrets ? 1 : 0);
		float lineH = 9f;
		float scale = 0.8f;
		if (widest > 0f) {
			scale = Mth.clamp(Math.min((room.maxWidth - 2f) / widest, room.maxHeight / (lines * lineH)), 0.45f, 0.8f);
		}
		float total = lines * lineH * scale;
		float y = cy - total / 2f;
		for (String line : room.splitName) {
			centered(graphics, font, line, cx, y, scale, color);
			y += total / lines;
		}
		if (showSecrets) {
			centered(graphics, font, secrets, cx, y, scale, color);
		}
	}

	/** The mark on a room only the map item knows about (centred in its cell). */
	private static void roomCheckmark(GuiGraphicsExtractor graphics, Tile tile, float xo, float yo, long now) {
		float appear = pop(tile, tile.state(), now);
		float size = 10f * (0.7f + 0.3f * appear);
		float half = MapCalibration.mapRoomSize / 2f;
		checkmark(graphics, tile.state(), xo + half, yo + half, size, appear);
	}

	private static void checkmark(GuiGraphicsExtractor graphics, RoomState state, float cx, float cy, float size, float alpha) {
		Identifier texture = switch (state) {
			case CLEARED -> CHECK_WHITE;
			case GREEN -> CHECK_GREEN;
			case FAILED -> CHECK_FAIL;
			case UNOPENED -> CHECK_UNKNOWN;
			default -> null;
		};
		if (texture == null) {
			return;
		}
		float x = cx - size / 2f;
		float y = cy - size / 2f;
		// Shadow in the mark's own shape: the same texture in black, a little down-right,
		// drawn a few times with small offsets so its edge is soft.
		float d = Math.max(0.6f, size * 0.07f);
		float[][] passes = {{d, d, 0.34f}, {d * 1.6f, d * 1.6f, 0.18f}, {d * 0.5f, d * 1.2f, 0.16f}, {d * 1.2f, d * 0.5f, 0.16f}};
		for (float[] pass : passes) {
			GuiDraw.blit(graphics, texture, x + pass[0], y + pass[1], size, size, 0f, 0f, 100, 100, 100, 100, MapColors.alpha(0xFF000000, pass[2] * alpha));
		}
		int tint = state == RoomState.UNOPENED ? 0xFFB8C0CA : 0xFFFFFFFF;
		GuiDraw.blit(graphics, texture, x, y, size, size, 0f, 0f, 100, 100, 100, 100, MapColors.alpha(tint, alpha));
	}

	// ------------------------------------------------------------------ heads

	private static void head(GuiGraphicsExtractor graphics, Font font, MapPlayer player, long now, boolean example) {
		StrayConfig config = StrayConfig.get();
		Minecraft client = Minecraft.getInstance();
		float x;
		float z;
		float yaw;
		Player entity = player.entity;
		if (!example && entity != null && entity.isAlive() && !entity.isRemoved()) {
			float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			float[] pos = MapCalibration.coordsToMap(Mth.lerp(partial, entity.xo, entity.getX()), Mth.lerp(partial, entity.zo, entity.getZ()));
			x = pos[0];
			z = pos[1];
			yaw = entity.getViewYRot(partial);
		} else {
			if (!player.placed()) {
				return;
			}
			x = player.mapX(now);
			z = player.mapZ(now);
			yaw = player.yaw(now);
		}
		int classColor = player.clazz == null ? 0xFFAAAAAA : player.clazz.color;
		int ring = config.dungeonMapClassBorders ? classColor : player.self ? ShadowLook.TEXT : 0xFF0B0D10;
		float turn = (float) Math.toRadians(Mth.wrapDegrees(yaw) + 180f);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, z);
		if (player.self && config.dungeonMapSelfArrow) {
			// The game's own map arrow, turned the way you look.
			graphics.pose().rotate(turn);
			GuiDraw.blit(graphics, MAP_ARROW, -3.5f + 0.6f, -3.5f + 0.6f, 7f, 7f, 0f, 0f, 8, 8, 8, 8, 0x66000000);
			GuiDraw.blit(graphics, MAP_ARROW, -3.5f, -3.5f, 7f, 7f, 0f, 0f, 8, 8, 8, 8, 0xFFFFFFFF);
			graphics.pose().popMatrix();
			return;
		}
		ShadowLook.shadow(graphics, -5f, -5f, 10f, 10f, 5f, 4, 0x70);
		graphics.pose().rotate(turn);
		if (config.dungeonMapHeads && player.skin != null) {
			GuiDraw.roundedFine(graphics, -6.5f, -6.5f, 13f, 13f, 3f, ring);
			PlayerFaceExtractor.extractRenderState(graphics, player.skin, -5, -5, 10);
		} else {
			GuiDraw.circle(graphics, 0f, 0f, 4f, ring);
			GuiDraw.circle(graphics, 0f, 0f, 2.6f, 0xFF0B0D10);
		}
		// Where they are looking: a small chevron past the top of the head.
		float tip = config.dungeonMapHeads ? -9.5f : -6.5f;
		GuiDraw.stroke(graphics, -2.2f, tip + 2.2f, 0f, tip, 1f, ring);
		GuiDraw.stroke(graphics, 0f, tip, 2.2f, tip + 2.2f, 1f, ring);
		if (showName(client, example)) {
			graphics.pose().rotate(-turn);
			float scale = 0.55f;
			float w = width(font, player.name) * scale;
			float ny = config.dungeonMapHeads ? 8.5f : 6f;
			GuiDraw.roundedFine(graphics, -w / 2f - 2f, ny - 1f, w + 4f, 9f * scale + 2f, 2.5f, 0xB0050709);
			centered(graphics, font, player.name, 0f, ny, scale, config.dungeonMapClassBorders ? lighten(classColor) : ShadowLook.TEXT);
		}
		graphics.pose().popMatrix();
	}

	private static boolean showName(Minecraft client, boolean example) {
		int mode = StrayConfig.get().dungeonMapPlayerNames;
		if (mode == 2) {
			return true;
		}
		if (mode == 0) {
			return false;
		}
		if (example && HudLayout.editorOpen()) {
			return true;
		}
		if (client.player == null) {
			return false;
		}
		String id = ItemIds.skyblockId(client.player.getMainHandItem());
		return "SPIRIT_LEAP".equals(id) || "INFINITE_SPIRIT_LEAP".equals(id) || "HAUNT_ABILITY".equals(id);
	}

	// ------------------------------------------------------------------ helpers

	private static float fade(Object key, long now) {
		Long first = SEEN.get(key);
		if (first == null) {
			if (SEEN.size() > 4096) {
				SEEN.clear();
			}
			SEEN.put(key, now);
			return 0f;
		}
		return ease((now - first) / FADE_MS);
	}

	/** 0 -> 1 when this room's (or tile's) shown state last changed. */
	private static float pop(Object key, RoomState state, long now) {
		long[] mark = MARKS.get(key);
		if (mark == null || mark[0] != state.ordinal()) {
			if (MARKS.size() > 4096) {
				MARKS.clear();
			}
			MARKS.put(key, new long[] {state.ordinal(), now});
			return 0f;
		}
		return ease((now - mark[1]) / FADE_MS);
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	private static Component inter(String value) {
		return MenuFont.shadow(value);
	}

	private static float width(Font font, String value) {
		return font.width(inter(value));
	}

	private static void centered(GuiGraphicsExtractor graphics, Font font, String value, float cx, float y, float scale, int color) {
		if ((color >>> 24) < 4) {
			return;
		}
		float w = width(font, value) * scale;
		GuiDraw.text(graphics, font, inter(value), cx - w / 2f, y, scale, color, true);
	}

	private static int lighten(int color) {
		return MapColors.mix(color, 0xFFFFFFFF, 0.25f);
	}
}
