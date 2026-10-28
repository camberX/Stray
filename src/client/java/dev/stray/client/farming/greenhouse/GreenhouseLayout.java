package dev.stray.client.farming.greenhouse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Reads a SkyMutations or SkyShards Greenhouse layout into a 10×10 grid.
 * Cells are -1 where the layout says nothing, 0 for a mutation slot that
 * should stay empty, or a crop id.
 */
public final class GreenhouseLayout {
	private static final int GRID = 10;
	private static final int CELLS = GRID * GRID;
	private static final int LETTERS = 26;
	private static final int MAX_DECOMPRESSED = 4096;

	/** SkyShards lists plantable crops first, in its own order, then mutations 1–40. */
	private static final String[] SKYSHARDS_CROPS = {
		"Wheat Seeds",
		"Potato",
		"Carrot",
		"Pumpkin Seeds",
		"Melon Seeds",
		"Cocoa Beans",
		"Sugar Cane",
		"Cactus",
		"Nether Wart",
		"Red Mushroom",
		"Brown Mushroom",
		"Moonflower",
		"Sunflower",
		"Wild Rose",
		"Fire",
		"Dead Plants",
		"Fermento",
	};
	private static final int MUTATIONS = 40;

	private GreenhouseLayout() {
	}

	public static int[][] empty() {
		int[][] grid = new int[GRID][GRID];
		for (int[] row : grid) {
			Arrays.fill(row, -1);
		}
		return grid;
	}

	public static boolean occupied(int[][] grid) {
		if (grid == null) {
			return false;
		}
		for (int x = 0; x < GRID; x++) {
			for (int z = 0; z < GRID; z++) {
				if (grid[x][z] != -1) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Pulls the layout code out of a designer link, a share link, or a bare code.
	 * Both sites put the code in a {@code layout} query parameter.
	 */
	public static String extractCode(String clipboard) {
		if (clipboard == null) {
			return "";
		}
		String trimmed = clipboard.strip();
		int layoutIndex = trimmed.indexOf("layout=");
		if (layoutIndex >= 0) {
			return endOfCode(trimmed.substring(layoutIndex + "layout=".length()));
		}
		int shareIndex = trimmed.indexOf("/share/");
		if (shareIndex >= 0) {
			return endOfCode(trimmed.substring(shareIndex + "/share/".length()));
		}
		return trimmed;
	}

	public static int[][] decode(String clipboard) {
		String encoded = extractCode(clipboard);
		if (encoded.isEmpty()) {
			return null;
		}
		if (encoded.indexOf('%') >= 0) {
			encoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
		}
		return switch (siteOf(clipboard)) {
			case SKY_SHARDS -> skyShards(encoded);
			case SKY_MUTATIONS -> skyMutations(encoded);
			case UNKNOWN -> {
				int[][] shards = skyShards(encoded);
				yield shards != null ? shards : skyMutations(encoded);
			}
		};
	}

	private static Site siteOf(String clipboard) {
		String host = clipboard == null ? "" : clipboard.strip().toLowerCase(Locale.ROOT);
		int schemeEnd = host.indexOf("://");
		if (schemeEnd >= 0) {
			host = host.substring(schemeEnd + "://".length());
		}
		for (int i = 0; i < host.length(); i++) {
			char c = host.charAt(i);
			if (c == '/' || c == '?' || c == '#') {
				host = host.substring(0, i);
				break;
			}
		}
		if (host.contains("skyshards")) {
			return Site.SKY_SHARDS;
		}
		if (host.contains("skymutations")) {
			return Site.SKY_MUTATIONS;
		}
		return Site.UNKNOWN;
	}

	private static int[][] skyMutations(String encoded) {
		try {
			String json = LzString.decompressFromEncodedURIComponent(encoded);
			if (json == null || json.isBlank() || json.charAt(0) != '[') {
				return null;
			}
			JsonArray array = JsonParser.parseString(json).getAsJsonArray();
			int[][] target = empty();
			for (JsonElement element : array) {
				JsonArray entry = element.getAsJsonArray();
				int x = entry.get(0).getAsInt();
				int z = entry.get(1).getAsInt();
				String cropName = entry.get(2).getAsString();
				int value = entry.get(3).getAsInt();
				if (x < 0 || x >= GRID || z < 0 || z >= GRID) {
					continue;
				}
				// The site's x axis is flipped relative to the greenhouse corner.
				int gx = GRID - 1 - x;
				if (value == 0) {
					target[gx][z] = 0;
					continue;
				}
				GreenhouseCrops.Crop crop = GreenhouseCrops.byName(cropName);
				if (crop != null) {
					target[gx][z] = crop.id();
				}
			}
			return occupied(target) ? target : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static int[][] skyShards(String code) {
		try {
			String gridString = inflate(code);
			if (gridString == null) {
				return null;
			}
			String[] parts = gridString.split("\\|", -1);
			if (parts.length != 3) {
				return null;
			}
			int[] inputs = palette(parts[0]);
			int[] targets = palette(parts[1]);
			if (inputs == null || targets == null) {
				return null;
			}
			String grid = parts[2];
			int width = grid.length() == CELLS ? 1 : grid.length() == CELLS * 2 ? 2 : 0;
			if (width == 0) {
				return null;
			}
			int[][] screen = new int[GRID][GRID];
			for (int[] row : screen) {
				Arrays.fill(row, -1);
			}
			for (int cell = 0; cell < CELLS; cell++) {
				String chars = grid.substring(cell * width, (cell + 1) * width);
				int index = paletteIndex(chars);
				if (index < 0) {
					continue;
				}
				boolean target = chars.equals(chars.toUpperCase(Locale.ROOT));
				int[] table = target ? targets : inputs;
				if (index >= table.length) {
					continue;
				}
				int cropId = table[index];
				int value = target ? 0 : cropId;
				int row = cell / GRID;
				int col = cell % GRID;
				int size = sizeOf(cropId);
				for (int dr = 0; dr < size && row + dr < GRID; dr++) {
					for (int dc = 0; dc < size && col + dc < GRID; dc++) {
						screen[row + dr][col + dc] = value;
					}
				}
			}
			int[][] laid = new int[GRID][GRID];
			for (int row = 0; row < GRID; row++) {
				for (int col = 0; col < GRID; col++) {
					laid[col][row] = screen[row][col];
				}
			}
			return occupied(laid) ? laid : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static String inflate(String code) {
		byte[] compressed;
		try {
			compressed = Base64.getUrlDecoder().decode(code);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
		try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed), new Inflater(true))) {
			byte[] decompressed = in.readNBytes(MAX_DECOMPRESSED);
			if (in.read() != -1) {
				return null;
			}
			if (decompressed.length == 0) {
				return null;
			}
			return new String(decompressed, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static int[] palette(String palette) {
		if (palette.isEmpty()) {
			return new int[0];
		}
		String[] indices = palette.split(",", -1);
		int[] ids = new int[indices.length];
		for (int i = 0; i < indices.length; i++) {
			int index;
			try {
				index = Integer.parseInt(indices[i], 36);
			} catch (NumberFormatException ignored) {
				return null;
			}
			if (index < 0 || index >= SKYSHARDS_CROPS.length + MUTATIONS) {
				return null;
			}
			if (index < SKYSHARDS_CROPS.length) {
				GreenhouseCrops.Crop crop = GreenhouseCrops.byName(SKYSHARDS_CROPS[index]);
				if (crop == null) {
					return null;
				}
				ids[i] = crop.id();
			} else {
				ids[i] = index - SKYSHARDS_CROPS.length + 1;
			}
		}
		return ids;
	}

	private static int paletteIndex(String chars) {
		int index = 0;
		for (int i = 0; i < chars.length(); i++) {
			char c = Character.toLowerCase(chars.charAt(i));
			if (c < 'a' || c > 'z') {
				return -1;
			}
			index = index * LETTERS + (c - 'a');
		}
		if (!chars.equals(chars.toUpperCase(Locale.ROOT)) && !chars.equals(chars.toLowerCase(Locale.ROOT))) {
			return -1;
		}
		return index;
	}

	private static int sizeOf(int cropId) {
		return switch (cropId) {
			case 23, 37 -> 3;
			case 22, 26, 36 -> 2;
			default -> 1;
		};
	}

	private static String endOfCode(String code) {
		for (int i = 0; i < code.length(); i++) {
			char c = code.charAt(i);
			boolean valid = c >= 'a' && c <= 'z'
				|| c >= 'A' && c <= 'Z'
				|| c >= '0' && c <= '9'
				|| c == '-' || c == '_' || c == '=' || c == '+' || c == '%';
			if (!valid) {
				return code.substring(0, i);
			}
		}
		return code;
	}

	private enum Site {
		SKY_SHARDS,
		SKY_MUTATIONS,
		UNKNOWN
	}
}
