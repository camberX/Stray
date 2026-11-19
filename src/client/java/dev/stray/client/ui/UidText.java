package dev.stray.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.Stray;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * uid?'s 9 px text the way a desktop draws it: FreeType with the font's own hinting at the
 * exact pixel size, whole-pixel advances, every glyph placed on a whole screen pixel and
 * sampled 1:1, instead of the game's font path (unhinted fractional advances, smoothed atlas).
 */
public final class UidText {
	/** The game font's baseline, 7 units under the text's y. */
	private static final float BASELINE = 7f;
	private static final float EM = 9f;
	private static final int ATLAS = 1024;
	/** Light text on dark looks thin without a little gamma, as browsers also apply. */
	private static final float GAMMA = 1.3f;
	private static final int[] LUT = new int[256];
	private static long library;
	private static final Face[] FACES = new Face[2];
	private static boolean failed;

	static {
		for (int i = 0; i < 256; i++) {
			LUT[i] = Math.round((float) Math.pow(i / 255.0, 1.0 / GAMMA) * 255f);
		}
	}

	private record Glyph(int u, int v, int w, int h, int left, int top, int advance) {
	}

	private static final class Face {
		ByteBuffer data;
		FT_Face face;
		final Map<Integer, Atlas> sizes = new HashMap<>();
	}

	private static final class Atlas {
		int px;
		int ascender;
		Identifier id;
		NativeImage image;
		DynamicTexture texture;
		int penX = 1;
		int penY = 1;
		int rowH;
		boolean dirty;
		final Map<Integer, Glyph> glyphs = new HashMap<>();
	}

	private UidText() {
	}

	/** Usable at all (FreeType and the fonts loaded). */
	public static boolean ready() {
		if (failed) {
			return false;
		}
		if (library != 0L) {
			return true;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer lib = stack.mallocPointer(1);
			if (FreeType.FT_Init_FreeType(lib) != 0) {
				failed = true;
				return false;
			}
			library = lib.get(0);
			String[] files = {"uid_sans.ttf", "uid_sans_bold.ttf"};
			for (int i = 0; i < 2; i++) {
				Face f = new Face();
				try (InputStream in = UidText.class.getResourceAsStream("/assets/stray/font/" + files[i])) {
					byte[] bytes = in.readAllBytes();
					f.data = BufferUtils.createByteBuffer(bytes.length);
					f.data.put(bytes).flip();
				}
				PointerBuffer facePtr = stack.mallocPointer(1);
				if (FreeType.FT_New_Memory_Face(library, f.data, 0L, facePtr) != 0) {
					failed = true;
					return false;
				}
				f.face = FT_Face.create(facePtr.get(0));
				FACES[i] = f;
			}
			return true;
		} catch (Throwable t) {
			failed = true;
			return false;
		}
	}

	/** Screen pixels per menu unit. */
	private static double dpi() {
		return UidMenu.dpiScale();
	}

	private static Atlas atlas(int family) {
		return atlas(family, dpi());
	}

	private static Atlas atlas(int family, double scale) {
		Face f = FACES[family];
		int px = Math.max(6, (int) Math.round(EM * scale));
		Atlas a = f.sizes.get(px);
		if (a == null) {
			a = new Atlas();
			a.px = px;
			FreeType.FT_Set_Pixel_Sizes(f.face, 0, px);
			a.ascender = (int) (f.face.size().metrics().ascender() >> 6);
			a.image = new NativeImage(ATLAS, ATLAS, true);
			a.id = Stray.id("dynamic/uid_text_" + family + "_" + px);
			a.texture = new DynamicTexture(() -> "Stray uid text", a.image);
			Minecraft.getInstance().getTextureManager().register(a.id, a.texture);
			f.sizes.put(px, a);
		}
		return a;
	}

	private static Glyph glyph(int family, Atlas a, int codepoint) {
		Glyph g = a.glyphs.get(codepoint);
		if (g != null) {
			return g;
		}
		FT_Face face = FACES[family].face;
		FreeType.FT_Set_Pixel_Sizes(face, 0, a.px);
		int index = FreeType.FT_Get_Char_Index(face, codepoint);
		// FT_LOAD_RENDER with the default (full) hinting: DejaVu's own bytecode hints.
		if (index == 0 || FreeType.FT_Load_Glyph(face, index, FreeType.FT_LOAD_RENDER) != 0) {
			g = new Glyph(0, 0, 0, 0, 0, 0, (int) Math.round(a.px * 0.32));
			a.glyphs.put(codepoint, g);
			return g;
		}
		FT_GlyphSlot slot = face.glyph();
		FT_Bitmap bitmap = slot.bitmap();
		int w = bitmap.width();
		int h = bitmap.rows();
		int advance = (int) Math.round(slot.advance().x() / 64.0);
		if (w == 0 || h == 0) {
			g = new Glyph(0, 0, 0, 0, 0, 0, advance);
			a.glyphs.put(codepoint, g);
			return g;
		}
		if (a.penX + w + 1 > ATLAS) {
			a.penX = 1;
			a.penY += a.rowH + 1;
			a.rowH = 0;
		}
		if (a.penY + h + 1 > ATLAS) {
			// Full (never with menu text); start over rather than draw garbage.
			a.glyphs.clear();
			a.penX = 1;
			a.penY = 1;
			a.rowH = 0;
		}
		ByteBuffer buf = bitmap.buffer(Math.abs(bitmap.pitch()) * h);
		int pitch = bitmap.pitch();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int cov = buf.get(y * pitch + x) & 0xFF;
				int alpha = LUT[cov];
				// ABGR: white with the coverage as alpha (tinted by the draw colour).
				a.image.setPixelABGR(a.penX + x, a.penY + y, (alpha << 24) | 0xFFFFFF);
			}
		}
		g = new Glyph(a.penX, a.penY, w, h, slot.bitmap_left(), slot.bitmap_top(), advance);
		a.glyphs.put(codepoint, g);
		a.penX += w + 1;
		a.rowH = Math.max(a.rowH, h);
		a.dirty = true;
		return g;
	}

	/** Width in menu units. */
	public static float width(int family, String text) {
		return width(family, text, dpi());
	}

	/** Capital height ("H") in screen pixels at {@code scale}: for centring text exactly. */
	public static int capHeight(int family, double scale) {
		if (!ready()) {
			return (int) Math.round(6.5 * scale);
		}
		int fam = family == 8 ? 1 : 0;
		return glyph(fam, atlas(fam, scale), 'H').top;
	}

	/** Width in units of {@code scale} screen pixels. */
	public static float width(int family, String text, double scale) {
		if (text == null || text.isEmpty() || !ready()) {
			return 0f;
		}
		int fam = family == 8 ? 1 : 0;
		Atlas a = atlas(fam, scale);
		int px = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			px += glyph(fam, a, cp).advance;
		}
		return (float) (px / scale);
	}

	/** Draws at (x, y) in the current pose's units (menu units), snapped to screen pixels. */
	static void draw(GuiGraphicsExtractor graphics, int family, String text, float x, float y, int color) {
		draw(graphics, family, text, x, y, color, dpi());
	}

	/** The same, rasterized for {@code scale} screen pixels per unit (the HUD's own scale). */
	public static void draw(GuiGraphicsExtractor graphics, int family, String text, float x, float y, int color, double scale) {
		if (text == null || text.isEmpty() || !ready()) {
			return;
		}
		int fam = family == 8 ? 1 : 0;
		Atlas a = atlas(fam, scale);
		Minecraft client = Minecraft.getInstance();
		double gui = client.getWindow().getGuiScale();
		Vector2f origin = graphics.pose().transformPosition(x, y + BASELINE, new Vector2f());
		int penX = (int) Math.round(origin.x * gui);
		int baseline = (int) Math.round(origin.y * gui);
		// Collect first so newly rasterized glyphs are uploaded before they're drawn.
		int n = text.codePointCount(0, text.length());
		Glyph[] glyphs = new Glyph[n];
		int k = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			glyphs[k++] = glyph(fam, a, cp);
		}
		if (a.dirty) {
			a.texture.upload();
			a.dirty = false;
		}
		graphics.pose().pushMatrix();
		graphics.pose().identity();
		float inv = (float) (1.0 / gui);
		graphics.pose().scale(inv, inv);
		for (Glyph g : glyphs) {
			if (g.w > 0) {
				GuiDraw.blit(graphics, a.id, penX + g.left, baseline - g.top, g.w, g.h, g.u, g.v, g.w, g.h, ATLAS, ATLAS, color);
			}
			penX += g.advance;
		}
		graphics.pose().popMatrix();
	}
}
