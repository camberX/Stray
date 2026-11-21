package dev.stray.client.render.overlay.window;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stray.client.render.overlay.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * The overlay window's own little 2D renderer (GL 3.3 core in its own context, so it works
 * whether the game itself runs on OpenGL or Vulkan). Textures it needs (checkmarks, player
 * skins) are copied in from the CPU side. Premultiplied alpha, since compositors expect that.
 */
final class OverlayGl implements Canvas {
	private static final int FLOATS = 8;
	private static final int MAX_VERTS = 6 * 4096;
	private static final float ATLAS_EM = 48f;
	/** GUI units per em: close to the game font's size. */
	private static final float TEXT_EM = 9.5f;
	private static final float BASELINE = 7f;
	private static final int FIRST = 32;
	private static final int COUNT = 224;

	private int program;
	private int vao;
	private int vbo;
	private int uScreen;
	private int uMode;
	private int uScreenSize;
	private int uRr;
	private int uRp;
	private int samplerNearest;
	private int samplerLinear;
	private final FloatBuffer verts = BufferUtils.createFloatBuffer(MAX_VERTS * FLOATS);
	private int count;
	private int mode = -1;
	private int texture;
	private boolean linear;
	private final Matrix3x2fStack pose = new Matrix3x2fStack(32);
	private final Map<Identifier, Integer> own = new HashMap<>();
	/** Skin copies: which game texture object each was made from, so a newly downloaded skin replaces it. */
	private final Map<Identifier, Object> sources = new HashMap<>();
	private final Map<Identifier, Long> retryAt = new HashMap<>();
	private int fontTexture;
	private STBTTPackedchar.Buffer chars;
	private float ascent;
	private int width;
	private int height;
	private final Vector2f tmp = new Vector2f();

	void init() {
		String vs = """
			#version 330 core
			layout(location = 0) in vec2 pos;
			layout(location = 1) in vec2 uv;
			layout(location = 2) in vec4 col;
			uniform vec2 screen;
			out vec2 vUv;
			out vec4 vCol;
			void main() {
				vUv = uv;
				vCol = col;
				gl_Position = vec4(pos.x / screen.x * 2.0 - 1.0, 1.0 - pos.y / screen.y * 2.0, 0.0, 1.0);
			}
			""";
		String fs = """
			#version 330 core
			in vec2 vUv;
			in vec4 vCol;
			uniform sampler2D tex;
			uniform int mode;
			uniform vec2 screenSize;
			uniform vec4 rr;
			uniform vec3 rp;
			out vec4 outColor;
			void main() {
				vec4 c = vCol;
				if (mode == 1) {
					vec4 t = texture(tex, vUv);
					c *= t;
				} else if (mode == 2) {
					c.a *= texture(tex, vUv).r;
				} else if (mode == 3) {
					// Rounded rect by distance: rr = centre and half size, rp = radius, feather, ring width.
					vec2 p = vec2(gl_FragCoord.x, screenSize.y - gl_FragCoord.y) - rr.xy;
					vec2 q = abs(p) - rr.zw + rp.x;
					float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rp.x;
					float a = rp.z > 0.0 ? clamp(rp.z - abs(d + rp.z * 0.5) + 0.5 - rp.z * 0.5, 0.0, 1.0) : clamp(0.5 - d / rp.y, 0.0, 1.0);
					c.a *= a;
				}
				outColor = vec4(c.rgb * c.a, c.a);
			}
			""";
		program = GL33C.glCreateProgram();
		int v = shader(GL33C.GL_VERTEX_SHADER, vs);
		int f = shader(GL33C.GL_FRAGMENT_SHADER, fs);
		GL33C.glAttachShader(program, v);
		GL33C.glAttachShader(program, f);
		GL33C.glLinkProgram(program);
		if (GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == 0) {
			throw new IllegalStateException("overlay shader link: " + GL33C.glGetProgramInfoLog(program));
		}
		GL33C.glDeleteShader(v);
		GL33C.glDeleteShader(f);
		uScreen = GL33C.glGetUniformLocation(program, "screen");
		uMode = GL33C.glGetUniformLocation(program, "mode");
		uScreenSize = GL33C.glGetUniformLocation(program, "screenSize");
		uRr = GL33C.glGetUniformLocation(program, "rr");
		uRp = GL33C.glGetUniformLocation(program, "rp");
		vao = GL33C.glGenVertexArrays();
		vbo = GL33C.glGenBuffers();
		GL33C.glBindVertexArray(vao);
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
		GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, (long) MAX_VERTS * FLOATS * 4, GL33C.GL_STREAM_DRAW);
		GL33C.glVertexAttribPointer(0, 2, GL33C.GL_FLOAT, false, FLOATS * 4, 0);
		GL33C.glVertexAttribPointer(1, 2, GL33C.GL_FLOAT, false, FLOATS * 4, 8);
		GL33C.glVertexAttribPointer(2, 4, GL33C.GL_FLOAT, false, FLOATS * 4, 16);
		GL33C.glEnableVertexAttribArray(0);
		GL33C.glEnableVertexAttribArray(1);
		GL33C.glEnableVertexAttribArray(2);
		samplerNearest = sampler(GL33C.GL_NEAREST, GL33C.GL_NEAREST);
		samplerLinear = sampler(GL33C.GL_LINEAR_MIPMAP_LINEAR, GL33C.GL_LINEAR);
		GL33C.glUseProgram(program);
		GL33C.glUniform1i(GL33C.glGetUniformLocation(program, "tex"), 0);
		bakeFont();
	}

	private static int sampler(int min, int mag) {
		int s = GL33C.glGenSamplers();
		GL33C.glSamplerParameteri(s, GL33C.GL_TEXTURE_MIN_FILTER, min);
		GL33C.glSamplerParameteri(s, GL33C.GL_TEXTURE_MAG_FILTER, mag);
		GL33C.glSamplerParameteri(s, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
		GL33C.glSamplerParameteri(s, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
		return s;
	}

	private static int shader(int type, String source) {
		int id = GL33C.glCreateShader(type);
		GL33C.glShaderSource(id, source);
		GL33C.glCompileShader(id);
		if (GL33C.glGetShaderi(id, GL33C.GL_COMPILE_STATUS) == 0) {
			throw new IllegalStateException("overlay shader: " + GL33C.glGetShaderInfoLog(id));
		}
		return id;
	}

	private static ByteBuffer resource(String path) throws Exception {
		try (InputStream in = OverlayGl.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("missing " + path);
			}
			byte[] bytes = in.readAllBytes();
			ByteBuffer buf = BufferUtils.createByteBuffer(bytes.length);
			buf.put(bytes).flip();
			return buf;
		}
	}

	/** Baked faces: 0 Google Sans Flex (Shadow, Expressive), 1 DejaVu Sans, the uid? menu's. */
	private final STBTTPackedchar.Buffer[] faceChars = new STBTTPackedchar.Buffer[2];
	private final int[] faceTexture = new int[2];
	private final float[] faceAscent = new float[2];
	private final boolean[] faceTried = new boolean[2];
	private int face = 0;

	/** Makes the look's face the one text draws and measures with (baked the first time). */
	private void useFace() {
		int want = dev.stray.client.ui.Looks.uid() ? 1 : 0;
		if (want == 1 && !faceTried[1]) {
			faceTried[1] = true;
			STBTTPackedchar.Buffer keepChars = chars;
			int keepTex = fontTexture;
			float keepAscent = ascent;
			bakeFont("/assets/stray/font/uid_sans.ttf");
			faceChars[1] = chars;
			faceTexture[1] = fontTexture;
			faceAscent[1] = ascent;
			chars = keepChars;
			fontTexture = keepTex;
			ascent = keepAscent;
		}
		if (faceChars[0] == null && chars != null && face == 0) {
			faceChars[0] = chars;
			faceTexture[0] = fontTexture;
			faceAscent[0] = ascent;
		}
		if (want == 1 && faceChars[1] == null) {
			want = 0;
		}
		if (want != face && faceChars[want] != null) {
			face = want;
			chars = faceChars[want];
			fontTexture = faceTexture[want];
			ascent = faceAscent[want];
		}
	}

	private void bakeFont() {
		bakeFont("/assets/stray/font/googlesansflex_medium.ttf");
	}

	private void bakeFont(String file) {
		try {
			ByteBuffer ttf = resource(file);
			int size = 1024;
			ByteBuffer bitmap = BufferUtils.createByteBuffer(size * size);
			chars = STBTTPackedchar.malloc(COUNT);
			try (STBTTPackContext pack = STBTTPackContext.malloc()) {
				STBTruetype.stbtt_PackBegin(pack, bitmap, size, size, 0, 2, 0L);
				STBTruetype.stbtt_PackSetOversampling(pack, 2, 2);
				chars.position(0);
				STBTruetype.stbtt_PackFontRange(pack, ttf, 0, ATLAS_EM, FIRST, chars);
				STBTruetype.stbtt_PackEnd(pack);
			}
			chars.clear();
			org.lwjgl.stb.STBTTFontinfo info = org.lwjgl.stb.STBTTFontinfo.malloc();
			STBTruetype.stbtt_InitFont(info, ttf);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer a = stack.mallocInt(1);
				IntBuffer d = stack.mallocInt(1);
				IntBuffer g = stack.mallocInt(1);
				STBTruetype.stbtt_GetFontVMetrics(info, a, d, g);
				ascent = a.get(0) * STBTruetype.stbtt_ScaleForMappingEmToPixels(info, ATLAS_EM);
			}
			info.free();
			fontTexture = GL33C.glGenTextures();
			GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, fontTexture);
			GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT, 1);
			GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_R8, size, size, 0, GL33C.GL_RED, GL33C.GL_UNSIGNED_BYTE, bitmap);
			GL33C.glGenerateMipmap(GL33C.GL_TEXTURE_2D);
		} catch (Exception e) {
			chars = null;
		}
	}

	/** A texture of ours (checkmarks) or a copy of one of the game's (skins). */
	private int textureFor(Identifier id) {
		Integer known = own.get(id);
		if ("stray".equals(id.getNamespace())) {
			if (known != null) {
				return known;
			}
			int tex = 0;
			try {
				tex = upload(resource("/assets/stray/" + id.getPath()));
			} catch (Exception ignored) {
			}
			own.put(id, tex);
			return tex;
		}
		AbstractTexture texture;
		try {
			texture = Minecraft.getInstance().getTextureManager().getTexture(id);
		} catch (RuntimeException e) {
			texture = null;
		}
		if (known != null && known != 0 && sources.get(id) == texture) {
			return known;
		}
		long now = System.currentTimeMillis();
		if (known != null && known == 0 && now < retryAt.getOrDefault(id, 0L) && sources.get(id) == texture) {
			return 0;
		}
		int tex = 0;
		try {
			if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
				tex = upload(dynamic.getPixels());
			} else {
				try (InputStream in = Minecraft.getInstance().getResourceManager().open(id)) {
					byte[] bytes = in.readAllBytes();
					ByteBuffer buf = BufferUtils.createByteBuffer(bytes.length);
					buf.put(bytes).flip();
					tex = upload(buf);
				}
			}
		} catch (Exception ignored) {
		}
		if (known != null && known != 0) {
			GL33C.glDeleteTextures(known);
		}
		own.put(id, tex);
		sources.put(id, texture);
		retryAt.put(id, now + 1000L);
		return tex;
	}

	private static int upload(ByteBuffer png) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			IntBuffer c = stack.mallocInt(1);
			ByteBuffer pixels = STBImage.stbi_load_from_memory(png, w, h, c, 4);
			if (pixels == null) {
				return 0;
			}
			int tex = upload(pixels, w.get(0), h.get(0));
			STBImage.stbi_image_free(pixels);
			return tex;
		}
	}

	private static int upload(NativeImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		// ABGR ints are R, G, B, A bytes in little-endian memory: plain RGBA.
		int[] abgr = image.getPixelsABGR();
		ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
		buf.asIntBuffer().put(abgr);
		return upload(buf, w, h);
	}

	private static int upload(ByteBuffer rgba, int w, int h) {
		int tex = GL33C.glGenTextures();
		GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, tex);
		GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT, 4);
		GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, w, h, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, rgba);
		GL33C.glGenerateMipmap(GL33C.GL_TEXTURE_2D);
		return tex;
	}

	void begin(int width, int height, float scale) {
		this.width = width;
		this.height = height;
		GL33C.glViewport(0, 0, width, height);
		GL33C.glDisable(GL33C.GL_DEPTH_TEST);
		GL33C.glDisable(GL33C.GL_CULL_FACE);
		GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
		GL33C.glEnable(GL33C.GL_BLEND);
		GL33C.glBlendFunc(GL33C.GL_ONE, GL33C.GL_ONE_MINUS_SRC_ALPHA);
		GL33C.glClearColor(0f, 0f, 0f, 0f);
		GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT);
		GL33C.glUseProgram(program);
		GL33C.glUniform2f(uScreen, width, height);
		GL33C.glUniform2f(uScreenSize, width, height);
		GL33C.glBindVertexArray(vao);
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
		GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
		pose.clear();
		pose.scale(scale);
		mode = -1;
		count = 0;
	}

	void end() {
		flush();
	}

	/**
	 * Darkens what was drawn as if it sat under a menu's dark background: colour becomes
	 * colour * (1 - d) + shade * d where something was drawn; transparent pixels stay clear.
	 */
	void dim(float d, int shade) {
		flush();
		GL33C.glBlendFuncSeparate(GL33C.GL_DST_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA, GL33C.GL_ZERO, GL33C.GL_ONE);
		pose.pushMatrix();
		pose.identity();
		int a = Math.round(Math.max(0f, Math.min(1f, d)) * 255f);
		fill(0, 0, width, height, (a << 24) | (shade & 0xFFFFFF));
		pose.popMatrix();
		flush();
		GL33C.glBlendFunc(GL33C.GL_ONE, GL33C.GL_ONE_MINUS_SRC_ALPHA);
	}

	/** Clears a window-pixel rect back to see-through (the game's GUI shows there). */
	void erase(int x, int y, int w, int h) {
		flush();
		if (w <= 0 || h <= 0) {
			return;
		}
		GL33C.glEnable(GL33C.GL_SCISSOR_TEST);
		GL33C.glScissor(x, height - y - h, w, h);
		GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT);
		GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
	}

	private void state(int newMode, int tex, boolean lin) {
		if (newMode != mode || tex != texture || lin != linear || count + 6 > MAX_VERTS) {
			flush();
			mode = newMode;
			texture = tex;
			linear = lin;
		}
	}

	private void flush() {
		if (count == 0) {
			return;
		}
		verts.flip();
		GL33C.glBufferSubData(GL33C.GL_ARRAY_BUFFER, 0, verts);
		GL33C.glUniform1i(uMode, mode);
		if (mode != 0) {
			GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
			GL33C.glBindSampler(0, linear ? samplerLinear : samplerNearest);
		}
		GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, count);
		verts.clear();
		count = 0;
	}

	private void vertex(float x, float y, float u, float v, int argb) {
		pose.transformPosition(x, y, tmp);
		verts.put(tmp.x).put(tmp.y).put(u).put(v)
			.put(((argb >> 16) & 0xFF) / 255f).put(((argb >> 8) & 0xFF) / 255f).put((argb & 0xFF) / 255f).put((argb >>> 24) / 255f);
		count++;
	}

	private void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
		vertex(x0, y0, u0, v0, argb);
		vertex(x0, y1, u0, v1, argb);
		vertex(x1, y1, u1, v1, argb);
		vertex(x0, y0, u0, v0, argb);
		vertex(x1, y1, u1, v1, argb);
		vertex(x1, y0, u1, v0, argb);
	}

	@Override
	public void push() {
		pose.pushMatrix();
	}

	@Override
	public void pop() {
		pose.popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		pose.translate(x, y);
	}

	@Override
	public void scale(float s) {
		pose.scale(s);
	}

	@Override
	public void rotate(float radians) {
		pose.rotate(radians);
	}

	@Override
	public void fill(float x, float y, float w, float h, int argb) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) {
			return;
		}
		state(0, 0, false);
		quad(x, y, x + w, y + h, 0, 0, 0, 0, argb);
	}

	/** One rounded-rect pass (its own draw call; a map panel needs three). */
	private void rounded(float x, float y, float w, float h, float radius, float feather, float ring, int argb) {
		if ((argb >>> 24) == 0 || w <= 0 || h <= 0) {
			return;
		}
		flush();
		Vector2f a = pose.transformPosition(x, y, new Vector2f());
		Vector2f b = pose.transformPosition(x + w, y + h, new Vector2f());
		float scale = Math.abs(b.x - a.x) / w;
		float hw = Math.abs(b.x - a.x) * 0.5f;
		float hh = Math.abs(b.y - a.y) * 0.5f;
		float cx = (a.x + b.x) * 0.5f;
		float cy = (a.y + b.y) * 0.5f;
		float r = Math.min(radius * scale, Math.min(hw, hh));
		float f = Math.max(1f, feather * scale);
		GL33C.glUniform4f(uRr, cx, cy, hw, hh);
		GL33C.glUniform3f(uRp, r, f, ring);
		mode = 3;
		float pad = f + 2f;
		pose.pushMatrix();
		pose.identity();
		quad(cx - hw - pad, cy - hh - pad, cx + hw + pad, cy + hh + pad, 0, 0, 0, 0, argb);
		pose.popMatrix();
		flush();
		mode = -1;
	}

	private static int mixArgb(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	@Override
	public void panel(float x, float y, float w, float h, float radius, int fill, int edge) {
		if (dev.stray.client.ui.Looks.uid()) {
			// uid?: square, the watermark's banded grey frame (same greys, same screen-pixel bands)
			// and the dark inside. One unit here is several screen pixels, so bands are measured in
			// pixels through the pose.
			Vector2f o = pose.transformPosition(0f, 0f, new Vector2f());
			Vector2f one = pose.transformPosition(1f, 0f, new Vector2f());
			float px = 1f / Math.max(0.0001f, Math.abs(one.x - o.x));
			float band = px * dev.stray.client.render.WatermarkRenderer.uidBandPx();
			int[] greys = {0x0A, 0x3C, 0x28, 0x28, 0x28, 0x3C, 0x14};
			float in = 0f;
			for (int g : greys) {
				rounded(x + in, y + in, w - in * 2f, h - in * 2f, 0f, 0.01f, 0f, 0xFF000000 | (g << 16) | (g << 8) | g);
				in += band;
			}
			rounded(x + in, y + in, w - in * 2f, h - in * 2f, 0f, 0.01f, 0f, 0xFF171717);
			if (dev.stray.client.config.StrayConfig.get().uidBarHud) {
				// The colour strip, in slices (the overlay has no gradient fill).
				float iw = w - in * 2f;
				float barPx = px * dev.stray.client.render.WatermarkRenderer.uidBarPx();
				int slices = Math.max(8, Math.round(iw / 3f));
				float sw = iw / slices;
				for (int i = 0; i < slices; i++) {
					float t = i / (float) (slices - 1);
					int c = t < 0.5f ? mixArgb(0xFF37B1DA, 0xFFCC46CD, t * 2f) : mixArgb(0xFFCC46CD, 0xFFCCE335, (t - 0.5f) * 2f);
					rounded(x + in + i * sw, y + in, sw + px * 0.5f, 2f * barPx, 0f, 0.01f, 0f, c);
				}
				// Its lower half darker, as on the watermark.
				rounded(x + in, y + in + barPx, iw, barPx, 0f, 0.01f, 0f, 0x77000000);
			}
			return;
		}
		// Shadow's look: a soft shadow under it, the translucent black, a 1 px hairline edge.
		rounded(x, y + 1.5f, w, h, radius, 10f, 0f, 0x60000000);
		// No frosted blur behind it out here, so it needs to be more solid to read the same.
		int alpha = Math.max(fill >>> 24, 0xE6);
		rounded(x, y, w, h, radius, 0.01f, 0f, (alpha << 24) | (fill & 0xFFFFFF));
		rounded(x, y, w, h, radius, 0.01f, 1f, edge);
	}

	@Override
	public void texture(Identifier id, float x, float y, float w, float h, float u, float v, float regionW, float regionH, float texW, float texH, int tint) {
		int tex = textureFor(id);
		if (tex == 0) {
			return;
		}
		boolean lin = "stray".equals(id.getNamespace()) && regionW > 16;
		state(1, tex, lin);
		quad(x, y, x + w, y + h, u / texW, v / texH, (u + regionW) / texW, (v + regionH) / texH, tint);
	}

	@Override
	public void line(float x0, float y0, float x1, float y1, float width, int argb) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1.0E-4f) {
			return;
		}
		float nx = -dy / len * width * 0.5f;
		float ny = dx / len * width * 0.5f;
		state(0, 0, false);
		vertex(x0 + nx, y0 + ny, 0, 0, argb);
		vertex(x0 - nx, y0 - ny, 0, 0, argb);
		vertex(x1 - nx, y1 - ny, 0, 0, argb);
		vertex(x0 + nx, y0 + ny, 0, 0, argb);
		vertex(x1 - nx, y1 - ny, 0, 0, argb);
		vertex(x1 + nx, y1 + ny, 0, 0, argb);
	}

	@Override
	public void text(String text, float x, float y, int argb, boolean shadow) {
		if (shadow) {
			int dark = (argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2);
			glyphs(text, x + 0.75f, y + 0.75f, dark, true);
		}
		glyphs(text, x, y, argb, false);
	}

	private void glyphs(String text, float x, float y, int argb, boolean shadow) {
		useFace();
		if (chars == null || text == null) {
			return;
		}
		state(2, fontTexture, true);
		float s = TEXT_EM / ATLAS_EM;
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(s);
		int color = argb;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer px = stack.floats(0f);
			// Baseline where the game font has it (7 units down), so text sits like vanilla text.
			FloatBuffer py = stack.floats(BASELINE / s);
			STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
			for (int i = 0; i < text.length(); i++) {
				char ch = text.charAt(i);
				if (ch == '§' && i + 1 < text.length()) {
					int code = "0123456789abcdef".indexOf(Character.toLowerCase(text.charAt(++i)));
					if (code >= 0) {
						int rgb = FORMAT[code];
						color = shadow ? (argb & 0xFF000000) | ((rgb & 0xFCFCFC) >> 2) : (argb & 0xFF000000) | rgb;
					} else if (Character.toLowerCase(text.charAt(i)) == 'r') {
						color = argb;
					}
					continue;
				}
				int index = ch - FIRST;
				if (index < 0 || index >= COUNT) {
					index = '?' - FIRST;
				}
				STBTruetype.stbtt_GetPackedQuad(chars, 1024, 1024, index, px, py, q, false);
				if (count + 6 > MAX_VERTS) {
					flush();
				}
				quad(q.x0(), q.y0(), q.x1(), q.y1(), q.s0(), q.t0(), q.s1(), q.t1(), color);
			}
		}
		pose.popMatrix();
	}

	private static final int[] FORMAT = {
		0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
	};

	@Override
	public float width(String text) {
		useFace();
		if (chars == null || text == null) {
			return 0f;
		}
		float w = 0f;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == '§') {
				i++;
				continue;
			}
			int index = ch - FIRST;
			if (index < 0 || index >= COUNT) {
				index = '?' - FIRST;
			}
			w += chars.get(index).xadvance();
		}
		return w * TEXT_EM / ATLAS_EM;
	}

	int width() {
		return width;
	}

	int height() {
		return height;
	}
}
