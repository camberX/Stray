package dev.stray.client.dungeon.map;

import dev.stray.client.location.SkyblockLocation;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Where the dungeon sits on the map item, found from the green entrance room. */
public final class MapUtils {
	public static int startX = 5;
	public static int startZ = 5;
	public static int mapRoomSize = 16;
	public static double coordMultiplier = 0.625;
	public static boolean calibrated;

	private MapUtils() {
	}

	public static void reset() {
		startX = 5;
		startZ = 5;
		mapRoomSize = 16;
		coordMultiplier = 0.625;
		calibrated = false;
		HotbarScanner.calibrate();
	}

	/** The map's start corner, nudged like NoammAddons does for the default. */
	public static float drawStartX() {
		return startX == 5 ? 6f : startX;
	}

	public static float drawStartZ() {
		return startZ == 5 ? 6f : startZ;
	}

	/** [mapX, mapZ] of a world position. */
	public static float[] toMap(double x, double z) {
		return new float[]{
			(float) ((x - Scanner.START_X + 15) * coordMultiplier + drawStartX()),
			(float) ((z - Scanner.START_Z + 15) * coordMultiplier + drawStartZ())
		};
	}

	public static boolean calibrate(MapItemSavedData data) {
		if (calibrated) {
			return true;
		}
		byte[] colors = data.colors;
		int length = 0;
		int start = 0;
		int foundStart = 0;
		int foundLength = 0;
		for (int i = 0; i < colors.length; i++) {
			if (colors[i] == 30) {
				if (length == 0) {
					start = i;
				}
				length++;
			} else {
				if (length >= 16) {
					foundStart = start;
					foundLength = length;
					break;
				}
				length = 0;
			}
		}
		if (foundLength == 0) {
			foundStart = start;
			foundLength = length;
		}
		if (foundLength != 16 && foundLength != 18) {
			return false;
		}
		mapRoomSize = foundLength;
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
				startX = (foundStart & 127) % (mapRoomSize + 4);
				startZ = (foundStart >> 7) % (mapRoomSize + 4);
			}
		}
		coordMultiplier = (mapRoomSize + 4.0) / Scanner.ROOM_SIZE;
		HotbarScanner.calibrate();
		calibrated = true;
		return true;
	}
}
