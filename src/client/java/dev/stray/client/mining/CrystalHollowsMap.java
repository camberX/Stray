package dev.stray.client.mining;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudChrome;
import dev.stray.client.render.HudLayout;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

public final class CrystalHollowsMap {
	public static final float WIDTH = 148;
	public static final float HEIGHT = 162;
	static final int WORLD_MIN = 202;
	static final int WORLD_MAX = 823;
	static final int WORLD_SPAN = WORLD_MAX - WORLD_MIN;
	private static final int TEX = 192;
	private static final int MID_WORLD = 513;
	private static final int NUCLEUS_R = 44;
	private static final int MAGMA_Y = 64;
	private static final int ROOF_Y = 118;
	private static final int SCAN_TOP = 186;
	private static final int SCAN_FLOOR = 31;
	private static final int BUDGET = 720;
	private static final float MAP = 136;
	private static final float PAD = 6;
	private static final float HEAD = 12;
	private static final Identifier TEXTURE = Stray.id("dynamic/crystal_hollows_map");
	private static final int PAPER = 0x7C7A70;
	private static final int JUNGLE = 0x3F8F4E;
	private static final int GOBLIN = 0xC47B2C;
	private static final int MITHRIL = 0x3FA8A4;
	private static final int PRECURSOR = 0x3A8FD4;
	private static final int NUCLEUS = 0xB24A4A;
	private static final int MAGMA = 0xB44A2C;
	private static NativeImage image;
	private static DynamicTexture texture;
	private static boolean registered;
	private static boolean dirty;
	private static final int[] raw = new int[TEX * TEX];
	private static final short[] height = new short[TEX * TEX];
	private static final byte[] seen = new byte[TEX * TEX];
	private static int cursor;
	private static String lobby = "";

	private CrystalHollowsMap() {
	}

	public static boolean enabled() {
		return StrayConfig.get().crystalHollowsMap;
	}

	public static boolean showing() {
		return enabled() && (SkyblockLocation.inCrystalHollows() || HudLayout.editorOpen());
	}

	public static float drawWidth() {
		return WIDTH;
	}

	public static float drawHeight() {
		return showing() || HudLayout.editorOpen() ? HEIGHT : 0;
	}

	public static void tick(Minecraft client) {
		if (!enabled()) {
			return;
		}
		ensureTexture(client);
		if (client.player == null || client.level == null || !SkyblockLocation.inCrystalHollows()) {
			return;
		}
		String server = SkyblockLocation.server;
		if (!server.isBlank() && !server.equals(lobby)) {
			lobby = server;
			fillZones();
		}
		scan(client.level, client.player.position());
		if (dirty) {
			shade();
			texture.upload();
			dirty = false;
		}
	}

	public static void reset() {
		lobby = "";
		cursor = 0;
		fillZones();
	}

	public static void close() {
		reset();
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(TEXTURE);
		}
		texture = null;
		image = null;
		registered = false;
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		if (!showing()) {
			return;
		}
		ensureTexture(client);
		HudLayout.Box box = HudLayout.box(HudLayout.Id.CRYSTAL_MAP, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.CRYSTAL_MAP), client);
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, float scale, Minecraft client) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}
		HudChrome.panel(graphics, 0, 0, WIDTH, HEIGHT, 5, Theme.WINDOW, Theme.LINE);
		plain(graphics, font, "Crystal Hollows", PAD, PAD, Theme.TEXT);
		plain(graphics, font, "N", WIDTH - PAD - font.width("N"), PAD, Theme.MUTED);
		float mapX = PAD;
		float mapY = PAD + HEAD;
		GuiDraw.rounded(graphics, mapX - 1, mapY - 1, MAP + 2, MAP + 2, 3, 0x66000000);
		GuiDraw.blit(graphics, TEXTURE, mapX, mapY, MAP, MAP, 0f, 0f, TEX, TEX, TEX, TEX);
		drawWalls(graphics, mapX, mapY);
		drawZoneNames(graphics, font, mapX, mapY);
		for (CrystalHollows.Mark mark : CrystalHollows.mapMarks()) {
			if (mark.nucleus()) {
				drawDot(graphics, mapX, mapY, mark.pos(), mark.rgb(), 1.6f);
				continue;
			}
			if (StrayConfig.get().crystalHollowsMapLabels) {
				drawMark(graphics, font, mapX, mapY, mark);
			} else {
				drawDot(graphics, mapX, mapY, mark.pos(), mark.rgb(), 2.2f);
			}
		}
		if (client.player != null && SkyblockLocation.inCrystalHollows()) {
			drawPlayer(graphics, mapX, mapY, client.player);
		}
		graphics.pose().popMatrix();
	}

	private static void ensureTexture(Minecraft client) {
		if (registered && texture != null && image != null) {
			return;
		}
		image = new NativeImage(TEX, TEX, false);
		texture = new DynamicTexture(() -> "stray-ch-map", image);
		client.getTextureManager().register(TEXTURE, texture);
		registered = true;
		fillZones();
		shade();
		texture.upload();
	}

	private static void fillZones() {
		if (image == null) {
			return;
		}
		for (int pz = 0; pz < TEX; pz++) {
			for (int px = 0; px < TEX; px++) {
				int i = pz * TEX + px;
				seen[i] = 0;
				height[i] = 0;
				raw[i] = zonePaper(pixelWorldX(px), pixelWorldZ(pz));
			}
		}
		dirty = true;
	}

	private static void scan(ClientLevel level, Vec3 feet) {
		int playerPx = worldToPixel(feet.x);
		int playerPz = worldToPixel(feet.z);
		int spent = 0;
		int radius = 18;
		for (int r = 0; r <= radius && spent < BUDGET; r++) {
			for (int dx = -r; dx <= r && spent < BUDGET; dx++) {
				spent += sample(level, playerPx + dx, playerPz - r) ? 1 : 0;
				if (r > 0) {
					spent += sample(level, playerPx + dx, playerPz + r) ? 1 : 0;
				}
			}
			for (int dz = -r + 1; dz <= r - 1 && spent < BUDGET; dz++) {
				spent += sample(level, playerPx - r, playerPz + dz) ? 1 : 0;
				spent += sample(level, playerPx + r, playerPz + dz) ? 1 : 0;
			}
		}
		int guard = TEX * TEX;
		while (spent < BUDGET && guard-- > 0) {
			int i = cursor++ % (TEX * TEX);
			if (seen[i] != 0) {
				continue;
			}
			if (sample(level, i % TEX, i / TEX)) {
				spent++;
			}
		}
	}

	private static boolean sample(ClientLevel level, int px, int pz) {
		if (px < 0 || pz < 0 || px >= TEX || pz >= TEX) {
			return false;
		}
		int worldX = pixelWorldX(px);
		int worldZ = pixelWorldZ(pz);
		if (!level.hasChunk(worldX >> 4, worldZ >> 4)) {
			return false;
		}
		LevelChunk chunk = level.getChunk(worldX >> 4, worldZ >> 4);
		Surface surface = surface(level, chunk, worldX, worldZ);
		int i = pz * TEX + px;
		int color = surface.missing()
			? zonePaper(worldX, worldZ)
			: terrainColor(surface.rgb(), surface.y(), worldX, worldZ);
		if (seen[i] != 0 && raw[i] == color && height[i] == (short) surface.y()) {
			return true;
		}
		raw[i] = color;
		height[i] = (short) surface.y();
		seen[i] = 1;
		dirty = true;
		return true;
	}

	private static Surface surface(ClientLevel level, LevelChunk chunk, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, SCAN_TOP, z);
		int y = SCAN_TOP;
		while (y >= ROOF_Y) {
			pos.setY(y);
			BlockState state = chunk.getBlockState(pos);
			if (state.isAir() || isRoof(state.getBlock())) {
				y--;
				continue;
			}
			break;
		}
		while (y >= SCAN_FLOOR) {
			pos.setY(y);
			BlockState state = chunk.getBlockState(pos);
			if (state.isAir()) {
				y--;
				continue;
			}
			int rgb = mapRgb(state, level, pos);
			if (rgb == 0) {
				y--;
				continue;
			}
			return new Surface(y, rgb);
		}
		return Surface.NONE;
	}

	private static int mapRgb(BlockState state, ClientLevel level, BlockPos pos) {
		if (!state.getFluidState().isEmpty()) {
			MapColor fluid = state.getFluidState().createLegacyBlock().getMapColor(level, pos);
			if (fluid != MapColor.NONE) {
				return fluid.col;
			}
		}
		MapColor color = state.getMapColor(level, pos);
		return color == MapColor.NONE ? 0 : color.col;
	}

	private static boolean isRoof(Block block) {
		return block == Blocks.STONE
			|| block == Blocks.COBBLESTONE
			|| block == Blocks.MOSSY_COBBLESTONE
			|| block == Blocks.ANDESITE
			|| block == Blocks.GRANITE
			|| block == Blocks.DIORITE
			|| block == Blocks.GRAVEL
			|| block == Blocks.INFESTED_STONE
			|| block == Blocks.TUFF
			|| block == Blocks.DEEPSLATE
			|| block == Blocks.COBBLED_DEEPSLATE
			|| block == Blocks.SMOOTH_STONE
			|| block == Blocks.BEDROCK
			|| block == Blocks.BARRIER;
	}

	private static int terrainColor(int rgb, int y, int worldX, int worldZ) {
		int mixed = mix(rgb, zoneTint(worldX, worldZ), 0.10f);
		if (y > 0 && y < MAGMA_Y) {
			mixed = mix(mixed, MAGMA, 0.16f);
		}
		return mixed;
	}

	private static int zonePaper(int worldX, int worldZ) {
		return mix(paperNoise(worldX, worldZ), zoneTint(worldX, worldZ), 0.11f);
	}

	private static int zoneTint(int worldX, int worldZ) {
		if (inNucleus(worldX, worldZ)) {
			return NUCLEUS;
		}
		return zoneOf(worldX, worldZ);
	}

	private static int zoneOf(int worldX, int worldZ) {
		boolean east = worldX >= MID_WORLD;
		boolean south = worldZ >= MID_WORLD;
		if (!east && !south) {
			return JUNGLE;
		}
		if (!east) {
			return GOBLIN;
		}
		if (!south) {
			return MITHRIL;
		}
		return PRECURSOR;
	}

	private static boolean inNucleus(double worldX, double worldZ) {
		double dx = worldX - MID_WORLD;
		double dz = worldZ - MID_WORLD;
		return dx * dx + dz * dz <= NUCLEUS_R * NUCLEUS_R;
	}

	private static int paperNoise(int x, int z) {
		int n = (x * 374761393 + z * 668265263) ^ (x * 127) ^ (z * 311);
		int d = (n >> 9 & 17) - 8;
		return scale(PAPER, 220 + d);
	}

	private static void shade() {
		if (image == null) {
			return;
		}
		for (int pz = 0; pz < TEX; pz++) {
			for (int px = 0; px < TEX; px++) {
				int i = pz * TEX + px;
				int color = raw[i];
				if (seen[i] != 0) {
					int north = pz == 0 ? height[i] : height[(pz - 1) * TEX + px];
					int bright = 220;
					if (seen[i] != 0 && (pz == 0 || seen[(pz - 1) * TEX + px] != 0)) {
						if (height[i] > north) {
							bright = 255;
						} else if (height[i] < north) {
							bright = 172;
						}
					}
					color = scale(color, bright);
				}
				image.setPixel(px, pz, 0xFF000000 | color);
			}
		}
		stampWalls();
	}

	private static void stampWalls() {
		int mid = worldToPixel(MID_WORLD);
		for (int i = 0; i < TEX; i++) {
			int x = pixelWorldX(i);
			int z = pixelWorldZ(i);
			if (!inNucleus(MID_WORLD, z)) {
				if (z < MID_WORLD) {
					stamp(mid - 1, i, JUNGLE);
					stamp(mid, i, MITHRIL);
				} else {
					stamp(mid - 1, i, GOBLIN);
					stamp(mid, i, PRECURSOR);
				}
			}
			if (!inNucleus(x, MID_WORLD)) {
				if (x < MID_WORLD) {
					stamp(i, mid - 1, JUNGLE);
					stamp(i, mid, GOBLIN);
				} else {
					stamp(i, mid - 1, MITHRIL);
					stamp(i, mid, PRECURSOR);
				}
			}
		}
		int cr = Math.max(4, Math.round(NUCLEUS_R * (TEX - 1) / (float) WORLD_SPAN));
		int inner = (cr - 1) * (cr - 1);
		int outer = (cr + 1) * (cr + 1);
		for (int pz = 0; pz < TEX; pz++) {
			for (int px = 0; px < TEX; px++) {
				int dx = px - mid;
				int dz = pz - mid;
				int d2 = dx * dx + dz * dz;
				if (d2 >= inner && d2 <= outer) {
					stamp(px, pz, NUCLEUS);
				}
			}
		}
	}

	private static void stamp(int px, int pz, int rgb) {
		if (px < 0 || pz < 0 || px >= TEX || pz >= TEX || image == null) {
			return;
		}
		int old = image.getPixel(px, pz) & 0xFFFFFF;
		image.setPixel(px, pz, 0xFF000000 | mix(old, rgb, 0.62f));
	}

	private static void drawWalls(GuiGraphicsExtractor graphics, float mapX, float mapY) {
		float mid = mapX + worldToView(MID_WORLD);
		float midY = mapY + worldToView(MID_WORLD);
		float nuc = NUCLEUS_R / (float) WORLD_SPAN * (MAP - 1f);
		float north = midY - mapY - nuc;
		float south = mapY + MAP - midY - nuc;
		float west = mid - mapX - nuc;
		float east = mapX + MAP - mid - nuc;
		wall(graphics, mid - 1, mapY, 1, north, 0x993F8F4E);
		wall(graphics, mid, mapY, 1, north, 0x993FA8A4);
		wall(graphics, mid - 1, midY + nuc, 1, south, 0x99C47B2C);
		wall(graphics, mid, midY + nuc, 1, south, 0x993A8FD4);
		wall(graphics, mapX, midY - 1, west, 1, 0x993F8F4E);
		wall(graphics, mapX, midY, west, 1, 0x99C47B2C);
		wall(graphics, mid + nuc, midY - 1, east, 1, 0x993FA8A4);
		wall(graphics, mid + nuc, midY, east, 1, 0x993A8FD4);
		int steps = 96;
		for (int i = 0; i < steps; i++) {
			double a = i * (Math.PI * 2 / steps);
			float x = mid + (float) Math.cos(a) * nuc;
			float y = midY + (float) Math.sin(a) * nuc;
			wall(graphics, x, y, 1, 1, 0x99B24A4A);
		}
	}

	private static void wall(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		GuiDraw.fill(graphics, Math.round(x), Math.round(y), Math.max(1, Math.round(w)), Math.max(1, Math.round(h)), color);
	}

	private static void drawZoneNames(GuiGraphicsExtractor graphics, Font font, float mapX, float mapY) {
		label(graphics, font, mapX, mapY, 290, 290, "Jungle", 0xAA3F8F4E);
		label(graphics, font, mapX, mapY, 290, 736, "Goblin", 0xAAC47B2C);
		label(graphics, font, mapX, mapY, 736, 290, "Mithril", 0xAA3FA8A4);
		label(graphics, font, mapX, mapY, 736, 736, "Precursor", 0xAA3A8FD4);
		label(graphics, font, mapX, mapY, 513, 513, "Nucleus", 0xBBB24A4A);
	}

	private static void label(GuiGraphicsExtractor graphics, Font font, float mapX, float mapY, int worldX, int worldZ, String text, int color) {
		int x = Math.round(mapX + worldToView(worldX) - font.width(text) * 0.5f);
		int y = Math.round(mapY + worldToView(worldZ) - 4f);
		plain(graphics, font, text, x, y, color);
	}

	private static void drawMark(GuiGraphicsExtractor graphics, Font font, float mapX, float mapY, CrystalHollows.Mark mark) {
		drawDot(graphics, mapX, mapY, mark.pos(), mark.rgb(), 2.2f);
		String name = shortName(mark.label());
		int x = Math.round(mapX + worldToView(mark.pos().getX()) + 4f);
		int y = Math.round(mapY + worldToView(mark.pos().getZ()) - 4f);
		int w = font.width(name);
		GuiDraw.fill(graphics, x - 1, y - 1, w + 2, 10, 0xAA101010);
		plain(graphics, font, name, x, y, 0xFF000000 | (mark.rgb() & 0xFFFFFF));
	}

	private static void drawDot(GuiGraphicsExtractor graphics, float mapX, float mapY, BlockPos pos, int rgb, float size) {
		int x = Math.round(mapX + worldToView(pos.getX()));
		int y = Math.round(mapY + worldToView(pos.getZ()));
		int s = Math.max(2, Math.round(size));
		GuiDraw.fill(graphics, x - s - 1, y - s - 1, s * 2 + 2, s * 2 + 2, 0xEE000000);
		GuiDraw.fill(graphics, x - s, y - s, s * 2, s * 2, 0xFF000000 | (rgb & 0xFFFFFF));
	}

	private static void drawPlayer(GuiGraphicsExtractor graphics, float mapX, float mapY, LocalPlayer player) {
		int x = Math.round(mapX + worldToView(player.getX()));
		int y = Math.round(mapY + worldToView(player.getZ()));
		int dir = Math.floorMod(Math.round(player.getYRot() / 45f), 8);
		int[][] step = {
			{0, 1}, {-1, 1}, {-1, 0}, {-1, -1},
			{0, -1}, {1, -1}, {1, 0}, {1, 1}
		};
		int dx = step[dir][0];
		int dy = step[dir][1];
		GuiDraw.fill(graphics, x - 2, y - 2, 5, 5, 0xFF000000);
		GuiDraw.fill(graphics, x - 1, y - 1, 3, 3, 0xFFFFFFFF);
		GuiDraw.fill(graphics, x + dx * 3 - 1, y + dy * 3 - 1, 3, 3, 0xFFE7C56A);
	}

	private static void plain(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int color) {
		graphics.text(font, text, Math.round(x), Math.round(y), color, false);
	}

	private static String shortName(String label) {
		return switch (label) {
			case "Jungle Temple" -> "Temple";
			case "Mines of Divan" -> "Divan";
			case "Goblin Queen's Den" -> "Queen";
			case "Lost Precursor City" -> "City";
			case "Khazad-dûm" -> "Khazad";
			case "Fairy Grotto" -> "Fairy";
			case "Dragon's Lair" -> "Dragon";
			case "Key Guardian" -> "Key";
			case "King Yolkar" -> "Yolkar";
			default -> label;
		};
	}

	private static float worldToView(double world) {
		return (float) ((Mth.clamp(world, WORLD_MIN, WORLD_MAX) - WORLD_MIN) / WORLD_SPAN * (MAP - 1f));
	}

	private static int worldToPixel(double world) {
		return Mth.clamp((int) Math.round((world - WORLD_MIN) / WORLD_SPAN * (TEX - 1)), 0, TEX - 1);
	}

	private static int pixelWorldX(int px) {
		return WORLD_MIN + Math.round(px * WORLD_SPAN / (float) (TEX - 1));
	}

	private static int pixelWorldZ(int pz) {
		return WORLD_MIN + Math.round(pz * WORLD_SPAN / (float) (TEX - 1));
	}

	private static int mix(int a, int b, float t) {
		int ar = a >> 16 & 255;
		int ag = a >> 8 & 255;
		int ab = a & 255;
		int br = b >> 16 & 255;
		int bg = b >> 8 & 255;
		int bb = b & 255;
		int r = Math.round(ar + (br - ar) * t);
		int g = Math.round(ag + (bg - ag) * t);
		int bl = Math.round(ab + (bb - ab) * t);
		return r << 16 | g << 8 | bl;
	}

	private static int scale(int rgb, int bright) {
		int r = (rgb >> 16 & 255) * bright / 255;
		int g = (rgb >> 8 & 255) * bright / 255;
		int b = (rgb & 255) * bright / 255;
		return r << 16 | g << 8 | b;
	}

	private record Surface(int y, int rgb) {
		static final Surface NONE = new Surface(0, 0);

		boolean missing() {
			return y == 0;
		}
	}
}
