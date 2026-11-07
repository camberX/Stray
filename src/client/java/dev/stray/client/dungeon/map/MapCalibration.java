package dev.stray.client.dungeon.map;

import dev.stray.client.location.SkyblockLocation;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Where the dungeon sits on the 128 x 128 map item, and how big a room is there. From NoammAddons (CC0). */
public final class MapCalibration {
	public static int startX = 5;
	public static int startZ = 5;
	public static int mapRoomSize = 16;
	public static double coordMultiplier = 0.625;
	public static boolean calibrated;

	private MapCalibration() {
	}

	public static void reset() {
		startX = 5;
		startZ = 5;
		mapRoomSize = 16;
		coordMultiplier = 0.625;
		calibrated = false;
	}

	/** Start corner as drawn (5 on the map means the dungeon really starts at 6). */
	public static float drawStartX() {
		return startX == 5 ? 6f : startX;
	}

	public static float drawStartZ() {
		return startZ == 5 ? 6f : startZ;
	}

	/** World position to map position. */
	public static float[] coordsToMap(double x, double z) {
		float mx = (float) ((x - DungeonScanner.START_X + 15) * coordMultiplier + drawStartX());
		float mz = (float) ((z - DungeonScanner.START_Z + 15) * coordMultiplier + drawStartZ());
		return new float[] {mx, mz};
	}

	public static boolean calibrate(MapItemSavedData data) {
		if (calibrated) {
			return true;
		}
		// Floor 0 is both Entrance and "sidebar not read yet": wait until it is known, or
		// the whole run would be locked to Entrance's layout.
		if (!dev.stray.client.location.SkyblockLocation.dungeonFloorKnown) {
			return false;
		}
		int[] found = entranceCorner(data);
		int start = found[0];
		int size = found[1];
		if (size != 16 && size != 18) {
			return false;
		}
		mapRoomSize = size;
		int floor = SkyblockLocation.dungeonFloor;
		switch (floor) {
			case 0 -> {
				startX = 22;
				startZ = 22;
			}
			case 1 -> {
				startX = 22;
				startZ = 11;
			}
			case 2, 3 -> {
				startX = 11;
				startZ = 11;
			}
			default -> {
				int sx = start & 127;
				int sz = start >> 7;
				startX = sx % (mapRoomSize + 4);
				startZ = sz % (mapRoomSize + 4);
			}
		}
		coordMultiplier = (mapRoomSize + 4.0) / DungeonScanner.ROOM_SIZE;
		HotbarMapScanner.calibrate();
		calibrated = true;
		return true;
	}

	/** First run of 16+ entrance-green pixels: where the entrance room starts and how wide it is. */
	private static int[] entranceCorner(MapItemSavedData data) {
		int length = 0;
		int start = 0;
		byte[] colors = data.colors;
		for (int i = 0; i < colors.length; i++) {
			if (colors[i] == 30) {
				if (length == 0) {
					start = i;
				}
				length++;
			} else {
				if (length >= 16) {
					return new int[] {start, length};
				}
				length = 0;
			}
		}
		return new int[] {start, length};
	}
}
