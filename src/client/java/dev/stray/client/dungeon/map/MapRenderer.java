package dev.stray.client.dungeon.map;

import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DungeonParty;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.render.overlay.Canvas;
import dev.stray.client.item.ItemIds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/** NoammAddons' map renderer (ported with noamm's permission), drawing into a {@link Canvas}. */
public final class MapRenderer {
	public static final float SIZE = 128f;
	private static final Identifier CHECK_GREEN = Stray.id("textures/gui/dungeonmap/green_check.png");
	private static final Identifier CHECK_WHITE = Stray.id("textures/gui/dungeonmap/white_check.png");
	private static final Identifier CHECK_UNKNOWN = Stray.id("textures/gui/dungeonmap/question.png");
	private static final Identifier CHECK_FAIL = Stray.id("textures/gui/dungeonmap/cross.png");
	private static final Identifier MARKER = Stray.id("textures/gui/dungeonmap/marker.png");

	private MapRenderer() {
	}

	/** Shown at all: in a run (unless hidden in boss), or as the sample in the HUD editor. */
	public static boolean showing(boolean editor) {
		StrayConfig c = StrayConfig.get();
		if (!c.dungeonMapEnabled) {
			return false;
		}
		if (editor) {
			return true;
		}
		return SkyblockLocation.inDungeon && !(SkyblockLocation.inBoss && c.dungeonMapHideInBoss);
	}

	public static void draw(Canvas c, boolean example, float partialTick) {
		StrayConfig config = StrayConfig.get();
		float sx = MapUtils.drawStartX();
		float sz = MapUtils.drawStartZ();
		if (config.dungeonMapStyle == 0) {
			// Stray's Shadow look: the same rounded, see-through black panel as the HUD.
			c.panel(0, 0, SIZE, SIZE, dev.stray.client.ui.ShadowLook.HUD_R, dev.stray.client.ui.ShadowLook.FILL, dev.stray.client.ui.ShadowLook.edge());
		} else {
			c.fill(0, 0, SIZE, SIZE, config.dungeonMapBackground);
			if (config.dungeonMapBorderWidth > 0) {
				c.border(0, 0, SIZE, SIZE, config.dungeonMapBorderColor, config.dungeonMapBorderWidth);
			}
		}
		c.push();
		c.translate(sx, sz);
		if (example) {
			MapPreview.Built preview = MapPreview.get();
			rooms(c, preview.tiles(), MapPreview.CONNECTOR_SIZE, preview);
			text(c, preview.rooms(), MapPreview.CONNECTOR_SIZE, MapUtils.mapRoomSize / 2, preview);
		} else {
			if (DungeonMap.cheater()) {
				for (Tile tile : Scanner.GRID) {
					if (tile.state() == RoomState.UNOPENED) {
						tile.setState(RoomState.UNDISCOVERED);
					}
				}
			}
			int connector = HotbarScanner.quarterRoom == -1 ? 4 : HotbarScanner.quarterRoom;
			rooms(c, Scanner.GRID, connector, null);
			text(c, Scanner.UNIQUE_ROOMS.values(), connector, HotbarScanner.halfRoom, null);
		}
		c.pop();
		if (example) {
			for (MapPlayer player : MapPreview.get().players()) {
				head(c, player, partialTick);
			}
		} else if (!SkyblockLocation.inBoss) {
			for (MapPlayer player : DungeonMap.heads()) {
				head(c, player, partialTick);
			}
		}
	}

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

	private static void rooms(Canvas c, Tile[] tiles, int connector, MapPreview.Built preview) {
		boolean cheater = DungeonMap.cheater();
		StrayConfig config = StrayConfig.get();
		int roomSize = MapUtils.mapRoomSize;
		for (int y = 0; y <= 10; y++) {
			for (int x = 0; x <= 10; x++) {
				Tile tile = tiles[y * 11 + x];
				if (tile instanceof UnknownTile) {
					continue;
				}
				if (tile.state() == RoomState.UNDISCOVERED && !cheater) {
					continue;
				}
				if (tile instanceof DoorTile door && doorState(door) == RoomState.UNDISCOVERED && !cheater) {
					continue;
				}
				int color = tile.color();
				if (cheater && tile.state() == RoomState.UNDISCOVERED) {
					color = lerp(color, config.dungeonMapColorUndiscovered, config.dungeonMapUndiscoveredTint / 100f);
				}
				int xOffset = (x >> 1) * (roomSize + connector);
				int yOffset = (y >> 1) * (roomSize + connector);
				boolean xEven = (x & 1) == 0;
				boolean yEven = (y & 1) == 0;
				List<RoomType> predicted = predicted(tile, y * 11 + x, preview);
				if (xEven && yEven) {
					if (predicted != null) {
						predictedRoom(c, xOffset, yOffset, predicted);
					} else if (tile instanceof RoomTile) {
						c.fill(xOffset, yOffset, roomSize, roomSize, color);
					}
				} else if (!xEven && !yEven) {
					c.fill(xOffset, yOffset, roomSize + connector, roomSize + connector, color);
				} else {
					connector(c, xOffset, yOffset, connector, tile instanceof DoorTile, !xEven, color);
				}
				if (tile instanceof RoomTile room && room.data.isUnknown() && predicted == null) {
					roomCheckmark(c, tile, xOffset, yOffset);
				}
			}
		}
	}

	private static List<RoomType> predicted(Tile tile, int index, MapPreview.Built preview) {
		if (!StrayConfig.get().dungeonMapPredict || DungeonMap.cheater()) {
			return null;
		}
		if (!(tile instanceof RoomTile) || tile.state() != RoomState.UNOPENED) {
			return null;
		}
		return preview != null ? preview.predictions().get(index) : SpecialColumn.predict(index);
	}

	private static void predictedRoom(Canvas c, int x, int y, List<RoomType> types) {
		StrayConfig config = StrayConfig.get();
		int size = MapUtils.mapRoomSize;
		int[] colors = new int[types.size()];
		for (int i = 0; i < colors.length; i++) {
			int color = switch (types.get(i)) {
				case CHAMPION -> config.dungeonMapColorMiniboss;
				case TRAP -> config.dungeonMapColorTrap;
				default -> config.dungeonMapColorPuzzle;
			};
			colors[i] = colors.length > 1 ? darker(darker(color)) : color;
		}
		c.push();
		c.translate(x, y);
		switch (colors.length) {
			case 1 -> c.fill(0, 0, size, size, colors[0]);
			case 2 -> {
				c.fill(0, 0, size, size / 2, colors[0]);
				c.fill(0, size / 2, size, size - size / 2, colors[1]);
			}
			case 3 -> {
				int outer = size * 5 / 16;
				int center = size - outer;
				int inner = center - 1;
				c.fill(0, 0, size, outer, colors[0]);
				c.fill(0, 0, outer, inner, colors[0]);
				c.fill(inner, outer, size - inner, size - outer, colors[1]);
				c.fill(0, inner, size, size - inner, colors[1]);
				c.fill(outer, outer, center - outer, center - outer, colors[2]);
			}
			default -> {
			}
		}
		c.pop();
	}

	private static void text(Canvas c, Collection<UniqueRoom> rooms, int connector, int halfRoomSize, MapPreview.Built preview) {
		StrayConfig config = StrayConfig.get();
		boolean cheater = DungeonMap.cheater();
		float roomSize = MapUtils.mapRoomSize;
		float gap = connector;
		float half = halfRoomSize;
		float cell = roomSize + gap;
		float textScale = config.dungeonMapTextScale / 100f;
		for (UniqueRoom unique : rooms) {
			if (unique.data.isUnknown()) {
				continue;
			}
			if (config.dungeonMapHideBloodText && unique.data.type() == RoomType.BLOOD) {
				continue;
			}
			RoomTile unopened = null;
			for (RoomTile tile : unique.tiles) {
				if (!tile.isSeparator && tile.state() == RoomState.UNOPENED) {
					unopened = tile;
					break;
				}
			}
			RoomTile main = unique.mainRoom;
			if (!cheater && unopened != null) {
				int row = unopened.row();
				int column = unopened.column();
				if (predicted(unopened, row * 11 + column, preview) != null) {
					continue;
				}
				roomCheckmark(c, unopened, (column >> 1) * (MapUtils.mapRoomSize + connector), (row >> 1) * (MapUtils.mapRoomSize + connector));
				continue;
			}
			if (unique.data.type() == RoomType.ENTRANCE || unique.data.type() == RoomType.FAIRY) {
				continue;
			}
			if (!cheater && main.state() == RoomState.UNDISCOVERED) {
				continue;
			}
			int[] pos = unique.checkmarkPosition();
			float cx = (pos[0] / 2f) * cell + half;
			float cy = (pos[1] / 2f) * cell + half;
			int color = switch (main.state()) {
				case GREEN -> 0xFF55FF55;
				case FAILED -> 0xFFFF0000;
				case CLEARED -> 0xFFFFFFFF;
				default -> 0xFFAAAAAA;
			};
			int style = config.dungeonMapRoomInfo;
			if (style == 4) {
				style = main.data.type() == RoomType.PUZZLE || main.data.secrets() == 0 ? 2 : 1;
			}
			switch (style) {
				case 2, 3 -> {
					float scale = textScale;
					boolean showSecrets = style == 3 && main.data.secrets() > 0;
					String secretsText = unique.foundSecrets() + "/" + main.data.secrets();
					float lh = c.lineHeight();
					if (config.dungeonMapLimitNames) {
						unique.updateBounds(roomSize, gap);
						float maxW = 0f;
						for (String line : unique.nameLines) {
							maxW = Math.max(maxW, c.width(line));
						}
						if (showSecrets) {
							maxW = Math.max(maxW, c.width(secretsText));
						}
						float totalH = unique.nameLines.length * lh + (showSecrets ? lh : 0);
						if (maxW > 0 && totalH > 0) {
							scale = Math.max(0.39f, Math.min(textScale, Math.min(unique.maxWidth / maxW, unique.maxHeight / totalH)));
						}
					}
					int lines = unique.nameLines.length + (showSecrets ? 1 : 0);
					float totalH = lines * lh * scale;
					float y = cy - totalH / 2f;
					for (String line : unique.nameLines) {
						c.centeredText(line, cx, y, color, scale);
						y += totalH / lines;
					}
					if (showSecrets) {
						c.centeredText(secretsText, cx, y, color, scale);
					}
				}
				case 1 -> c.centeredText(main.data.secrets() == 0 ? "0" : unique.foundSecrets() + "/" + main.data.secrets(),
					cx, cy - c.lineHeight() / 2f, color, textScale);
				default -> {
					float size = config.dungeonMapCheckmarkScale / 10f;
					checkmark(c, main, cx - size / 2f, cy - size / 2f, size);
				}
			}
		}
	}

	private static void checkmark(Canvas c, Tile tile, float x, float y, float size) {
		Identifier id = switch (tile.state()) {
			case CLEARED -> CHECK_WHITE;
			case GREEN -> CHECK_GREEN;
			case FAILED -> CHECK_FAIL;
			case UNOPENED -> StrayConfig.get().dungeonMapHideQuestion ? null : CHECK_UNKNOWN;
			default -> null;
		};
		if (id != null) {
			c.texture(id, x, y, size, size, 0, 0, 100, 100, 100, 100, -1);
		}
	}

	private static void roomCheckmark(Canvas c, Tile tile, int xOffset, int yOffset) {
		float size = StrayConfig.get().dungeonMapCheckmarkScale / 10f;
		checkmark(c, tile, xOffset + MapUtils.mapRoomSize / 2f - size / 2f, yOffset + MapUtils.mapRoomSize / 2f - size / 2f, size);
	}

	private static void head(Canvas c, MapPlayer player, float partialTick) {
		StrayConfig config = StrayConfig.get();
		float[] p = player.position(partialTick);
		int border = config.dungeonMapClassBorders ? player.clazz.color : config.dungeonMapHeadBorder;
		int nameColor = config.dungeonMapClassNames && player.clazz != DungeonParty.DungeonClass.EMPTY ? player.clazz.color : 0xFFFFFFFF;
		float yaw = ((p[2] % 360f) + 540f) % 360f - 180f;
		float rad = (float) Math.toRadians(yaw + 180f);
		c.push();
		c.translate(p[0], p[1]);
		c.rotate(rad);
		c.scale(config.dungeonMapHeadScale / 100f);
		if (config.dungeonMapSelfArrow && player.self) {
			c.texture(MARKER, -6, -6, 12, 12, 0, 0, 7, 7, 7, 7, config.dungeonMapArrowColor);
		} else {
			c.fill(-7, -7, 14, 14, border);
			c.head(player.skin, -6, -6, 12);
		}
		int names = config.dungeonMapPlayerNames;
		if (names == 2 || (names == 1 && holdingLeap())) {
			c.rotate(-rad);
			c.translate(0f, 8f);
			c.scale(config.dungeonMapNameScale / 100f);
			c.text(player.name, -c.width(player.name) / 2f, 0f, nameColor, true);
		}
		c.pop();
	}

	private static boolean holdingLeap() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return false;
		}
		ItemStack held = client.player.getMainHandItem();
		String id = ItemIds.skyblockId(held);
		return "SPIRIT_LEAP".equals(id) || "INFINITE_SPIRIT_LEAP".equals(id) || "HAUNT_ABILITY".equals(id);
	}

	private static void connector(Canvas c, int x, int y, int width, boolean doorway, boolean vertical, int color) {
		int doorSize = StrayConfig.get().dungeonMapDoorWidth;
		int roomSize = MapUtils.mapRoomSize;
		int doorwayOffset = (roomSize - doorSize) / 2;
		int height = doorway ? doorSize : roomSize;
		int x1 = vertical ? x + roomSize : x;
		int y1 = vertical ? y : y + roomSize;
		if (doorway) {
			if (vertical) {
				y1 += doorwayOffset;
			} else {
				x1 += doorwayOffset;
			}
		}
		c.fill(x1, y1, vertical ? width : height, vertical ? height : width, color);
	}

	private static int lerp(int a, int b, float t) {
		int r = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			r |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
		}
		return r;
	}

	/** java.awt.Color.darker(): each channel times 0.7. */
	private static int darker(int argb) {
		int r = (int) (((argb >> 16) & 0xFF) * 0.7);
		int g = (int) (((argb >> 8) & 0xFF) * 0.7);
		int b = (int) ((argb & 0xFF) * 0.7);
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}
}
