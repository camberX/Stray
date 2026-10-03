package dev.stray.client.mining;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.QuadInstance;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.visual.WorldTint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Route miner focus. Terrain other than glass and cobblestone is pushed to a
 * dark gray, cobblestone is bright pink, and stained glass that is not the
 * selected gemstone is desaturated. The same gemstone is what the ruby route
 * looks for inside each waypoint's 3-block cube.
 */
public final class FocusMode {
	private static final int WHITE = 0xFFFFFF;
	private static final int DESATURATE = 0x00FFFF;
	private static final int PINK = 0xFF00FF;
	private static final int GRAY = 0x202020;
	private static final Gem[] GEMS = Gem.values();
	private static final String FOCUS_FN = """
		vec4 strayFocus(vec4 tex, vec4 marker, vec4 light) {
		    float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
		    vec3 rgb;
		    if (marker.r > 0.97 && marker.g > 0.97 && marker.b > 0.97) {
		        rgb = tex.rgb;
		    } else if (marker.r < 0.05 && marker.g > 0.97 && marker.b > 0.97) {
		        rgb = vec3(luma);
		    } else if (marker.r > 0.97 && marker.g < 0.05 && marker.b > 0.97) {
		        rgb = vec3(1.0, 0.22, 0.78) * (0.65 + 0.55 * luma);
		    } else {
		        rgb = vec3(0.10 + luma * 0.12);
		    }
		    vec3 lit = (marker.r > 0.97 && marker.g < 0.05 && marker.b > 0.97) ? max(light.rgb, vec3(0.62)) : light.rgb;
		    return vec4(rgb * lit, tex.a);
		}
		""";
	private static final Map<Integer, Integer> LOCATIONS = new HashMap<>();
	private static volatile boolean visual;
	private static volatile int gemIndex;
	private static volatile Set<Block> cobble;
	private static volatile Set<Block> stained;

	private FocusMode() {
	}

	public static void tick() {
		StrayConfig config = StrayConfig.get();
		int gem = indexOf(config.routeMinerGem);
		boolean gemChanged = gem != gemIndex;
		if (gemChanged) {
			gemIndex = gem;
			RouteMiner.invalidate();
		}
		boolean next = config.routeMiner && config.routeMinerFocus && SkyblockLocation.inCrystalHollows();
		if (next != visual || (gemChanged && (next || visual))) {
			visual = next;
			rebuild();
		}
	}

	public static boolean visual() {
		return visual;
	}

	public static boolean matchesGem(Block block) {
		Gem gem = GEMS[gemIndex];
		return block == gem.glass || block == gem.pane;
	}

	public static String label(String id) {
		return GEMS[indexOf(id)].label;
	}

	public static String cycle(String id) {
		return GEMS[(indexOf(id) + 1) % GEMS.length].id;
	}

	public static String normalize(String id) {
		return GEMS[indexOf(id)].id;
	}

	public static void paint(QuadInstance quad, BlockState state) {
		if (!visual || quad == null || state == null) {
			return;
		}
		int marker = marker(state.getBlock());
		for (int i = 0; i < 4; i++) {
			int alpha = (quad.getColor(i) >>> 24) & 0xFF;
			if (alpha == 0) {
				alpha = 0xFF;
			}
			quad.setColor(i, (alpha << 24) | marker);
		}
	}

	public static int marker(Block block) {
		if (block == Blocks.GLASS || block == Blocks.GLASS_PANE || block == Blocks.TINTED_GLASS || matchesGem(block)) {
			return WHITE;
		}
		if (stained().contains(block)) {
			return DESATURATE;
		}
		if (cobble().contains(block)) {
			return PINK;
		}
		return GRAY;
	}

	public static void bind(GlRenderPipeline pipeline) {
		if (pipeline == null || pipeline.program() == null) {
			return;
		}
		int program = pipeline.program().getProgramId();
		if (program <= 0) {
			return;
		}
		int location = LOCATIONS.computeIfAbsent(program, FocusMode::find);
		if (location < 0) {
			return;
		}
		int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		GL20.glUseProgram(program);
		GL20.glUniform1f(location, visual ? 1f : 0f);
		GL20.glUseProgram(previous);
	}

	public static String patchVanilla(String source) {
		if (source == null || source.contains("strayTint")) {
			return source;
		}
		if (source.contains("vertexColor = Color * sample_lightmap(Sampler2, UV2);")) {
			return source
				.replace("out vec4 vertexColor;", "out vec4 vertexColor;\nflat out vec4 strayTint;\nout vec4 strayLight;")
				.replace(
					"vertexColor = Color * sample_lightmap(Sampler2, UV2);",
					"strayLight = sample_lightmap(Sampler2, UV2);\n    strayTint = Color;\n    vertexColor = Color * strayLight;"
				);
		}
		if (!source.contains("vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;")) {
			return source;
		}
		String withIns = source.replace(
			"in vec4 vertexColor;",
			"in vec4 vertexColor;\nflat in vec4 strayTint;\nin vec4 strayLight;\nuniform float u_StrayFocus;"
		);
		String withFn = withIns.replace("void main() {", FOCUS_FN + "void main() {");
		return withFn.replace(
			"vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;",
			"vec4 strayTex = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize));\n    vec4 color = u_StrayFocus > 0.5 ? strayFocus(strayTex, strayTint, strayLight) : strayTex * vertexColor;"
		);
	}

	public static String patchSodiumVertex(String source) {
		if (source == null || source.contains("strayTint") || !source.contains("v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);")) {
			return source;
		}
		return source
			.replace("out vec4 v_Color;", "out vec4 v_Color;\nflat out vec4 strayTint;\nout vec4 strayLight;")
			.replace(
				"v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);",
				"strayLight = texture(u_LightTex, _vert_tex_light_coord);\n    strayTint = _vert_color;\n    v_Color = _vert_color * strayLight;"
			);
	}

	public static String patchSodiumFragment(String source) {
		if (source == null || source.contains("u_StrayFocus") || !source.contains("color *= v_Color;")) {
			return source;
		}
		String withIns = source.contains("in vec4 v_Color; // The interpolated vertex color")
			? source.replace(
				"in vec4 v_Color; // The interpolated vertex color",
				"in vec4 v_Color; // The interpolated vertex color\nflat in vec4 strayTint;\nin vec4 strayLight;\nuniform float u_StrayFocus;"
			)
			: source.replace("in vec4 v_Color;", "in vec4 v_Color;\nflat in vec4 strayTint;\nin vec4 strayLight;\nuniform float u_StrayFocus;");
		String withFn = withIns.replace("void main() {", FOCUS_FN + "void main() {");
		return withFn.replace(
			"color *= v_Color;",
			"vec4 strayTex = color;\n    if (u_StrayFocus > 0.5) {\n        color = strayFocus(strayTex, strayTint, strayLight);\n    } else {\n        color *= v_Color;\n    }"
		);
	}

	private static void rebuild() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.level == null) {
			return;
		}
		if (WorldTint.sodiumLoaded()) {
			try {
				Class.forName("dev.stray.client.mixin.sodium.SodiumFocus")
					.getMethod("rebuild")
					.invoke(null);
				return;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		if (client.levelRenderer != null) {
			client.levelRenderer.allChanged();
		}
	}

	private static int find(int program) {
		return GlStateManager._glGetUniformLocation(program, "u_StrayFocus");
	}

	private static int indexOf(String id) {
		if (id != null) {
			for (int i = 0; i < GEMS.length; i++) {
				if (GEMS[i].id.equalsIgnoreCase(id)) {
					return i;
				}
			}
		}
		return 0;
	}

	private static Set<Block> cobble() {
		Set<Block> set = cobble;
		if (set != null) {
			return set;
		}
		set = blocksContaining("cobblestone");
		cobble = set;
		return set;
	}

	private static Set<Block> stained() {
		Set<Block> set = stained;
		if (set != null) {
			return set;
		}
		Set<Block> built = new HashSet<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			String path = id.getPath();
			if (path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane")) {
				built.add(BuiltInRegistries.BLOCK.getValue(id));
			}
		}
		stained = built;
		return built;
	}

	private static Set<Block> blocksContaining(String needle) {
		Set<Block> built = new HashSet<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (id.getPath().contains(needle)) {
				built.add(BuiltInRegistries.BLOCK.getValue(id));
			}
		}
		return built;
	}

	private enum Gem {
		RUBY("Ruby", "ruby", Blocks.RED_STAINED_GLASS, Blocks.RED_STAINED_GLASS_PANE),
		AMBER("Amber", "amber", Blocks.ORANGE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS_PANE),
		TOPAZ("Topaz", "topaz", Blocks.YELLOW_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS_PANE),
		JADE("Jade", "jade", Blocks.LIME_STAINED_GLASS, Blocks.LIME_STAINED_GLASS_PANE),
		SAPPHIRE("Sapphire", "sapphire", Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE),
		AMETHYST("Amethyst", "amethyst", Blocks.PURPLE_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS_PANE),
		JASPER("Jasper", "jasper", Blocks.MAGENTA_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS_PANE),
		OPAL("Opal", "opal", Blocks.WHITE_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS_PANE);

		final String label;
		final String id;
		final Block glass;
		final Block pane;

		Gem(String label, String id, Block glass, Block pane) {
			this.label = label;
			this.id = id;
			this.glass = glass;
			this.pane = pane;
		}
	}
}
