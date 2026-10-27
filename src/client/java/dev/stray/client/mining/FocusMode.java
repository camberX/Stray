package dev.stray.client.mining;

import net.minecraft.world.item.DyeColor;
import com.mojang.blaze3d.vertex.QuadInstance;
import dev.stray.client.render.StrayUniforms;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.visual.WorldTint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
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
	// Near-black so a shaded copy stays below the white marker, whose darkest
	// unoccluded face is still about half brightness.
	private static final int GRAY = 0x010101;
	private static final Gem[] GEMS = Gem.values();
	// Face shade is multiplied into the marker (top 1.0, sides 0.6–0.8, bottom 0.5),
	// so markers are matched by channel ratio instead of an absolute 0.97 cutoff.
	private static final String FOCUS_FN = """
		vec4 strayFocus(vec4 tex, vec4 marker, vec4 light) {
		    float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
		    float r = marker.r;
		    float g = marker.g;
		    float b = marker.b;
		    float peak = max(r, max(g, b));
		    float low = min(r, min(g, b));
		    bool neutral = peak - low < max(peak * 0.12, 0.015);
		    bool pink = r > g * 2.5 && b > g * 2.5 && peak > 0.04;
		    bool cyan = g > r * 2.5 && b > r * 2.5 && peak > 0.04;
		    vec3 rgb;
		    if (pink) {
		        rgb = vec3(1.0, 0.22, 0.78) * (0.65 + 0.55 * luma);
		    } else if (cyan) {
		        rgb = vec3(luma);
		    } else if (neutral && peak > 0.04) {
		        rgb = tex.rgb;
		    } else {
		        rgb = vec3(0.10 + luma * 0.12);
		    }
		    vec3 lit = pink ? max(light.rgb, vec3(0.85)) : light.rgb;
		    return vec4(rgb * lit, tex.a);
		}
		""";
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

	public static void bind() {
		StrayUniforms.focus(visual ? 1f : 0f);
	}

	public static String patchVanilla(String source) {
		if (source == null || source.contains("strayTint")) {
			return source;
		}
		if (source.contains("ChunkPosition") && source.contains("vertexColor = Color * sample_lightmap(Sampler2, UV2);")) {
			return source
				.replace("out vec4 vertexColor;", "out vec4 vertexColor;\nlayout(location = 5) flat out vec4 strayTint;\nlayout(location = 6) out vec4 strayLight;")
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
			"in vec4 vertexColor;\nlayout(location = 5) flat in vec4 strayTint;\nlayout(location = 6) in vec4 strayLight;"
		);
		String withFn = StrayUniforms.insertBlock(withIns).replace("void main() {", FOCUS_FN + "void main() {");
		return withFn.replace(
			"vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;",
			"vec4 strayTex = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize));\n    vec4 color = u_StrayMisc.x > 0.5 ? strayFocus(strayTex, strayTint, strayLight) : strayTex * vertexColor;"
		);
	}

	public static String patchSodiumVertex(String source) {
		if (source == null || source.contains("strayTint") || !source.contains("v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);")) {
			return source;
		}
		return source
			.replace("out vec4 v_Color;", "out vec4 v_Color;\nlayout(location = 5) flat out vec4 strayTint;\nlayout(location = 6) out vec4 strayLight;")
			.replace(
				"v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);",
				"strayLight = texture(u_LightTex, _vert_tex_light_coord);\n    strayTint = _vert_color;\n    v_Color = _vert_color * strayLight;"
			);
	}

	public static String patchSodiumFragment(String source) {
		if (source == null || source.contains("strayTint") || !source.contains("color *= v_Color;")) {
			return source;
		}
		String withIns = source.contains("in vec4 v_Color; // The interpolated vertex color")
			? source.replace(
				"in vec4 v_Color; // The interpolated vertex color",
				"in vec4 v_Color; // The interpolated vertex color\nlayout(location = 5) flat in vec4 strayTint;\nlayout(location = 6) in vec4 strayLight;"
			)
			: source.replace("in vec4 v_Color;", "in vec4 v_Color;\nlayout(location = 5) flat in vec4 strayTint;\nlayout(location = 6) in vec4 strayLight;");
		String withFn = StrayUniforms.insertBlock(withIns).replace("void main() {", FOCUS_FN + "void main() {");
		return withFn.replace(
			"color *= v_Color;",
			"vec4 strayTex = color;\n    if (u_StrayMisc.x > 0.5) {\n        color = strayFocus(strayTex, strayTint, strayLight);\n    } else {\n        color *= v_Color;\n    }"
		);
	}

	private static void rebuild() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.level == null) {
			return;
		}
		if (WorldTint.sodiumLoaded()) {
			try {
				Class.forName("dev.stray.client.compat.SodiumFocus")
					.getMethod("rebuild")
					.invoke(null);
				return;
			} catch (Throwable exception) {
				Stray.LOGGER.warn("Sodium chunk reload failed, falling back to a full rebuild", exception);
			}
		}
		if (client.levelRenderer != null) {
			client.levelExtractor.allChanged();
		}
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
		RUBY("Ruby", "ruby", Blocks.STAINED_GLASS.pick(DyeColor.RED), Blocks.STAINED_GLASS_PANE.pick(DyeColor.RED)),
		AMBER("Amber", "amber", Blocks.STAINED_GLASS.pick(DyeColor.ORANGE), Blocks.STAINED_GLASS_PANE.pick(DyeColor.ORANGE)),
		TOPAZ("Topaz", "topaz", Blocks.STAINED_GLASS.pick(DyeColor.YELLOW), Blocks.STAINED_GLASS_PANE.pick(DyeColor.YELLOW)),
		JADE("Jade", "jade", Blocks.STAINED_GLASS.pick(DyeColor.LIME), Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIME)),
		SAPPHIRE("Sapphire", "sapphire", Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE), Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE)),
		AMETHYST("Amethyst", "amethyst", Blocks.STAINED_GLASS.pick(DyeColor.PURPLE), Blocks.STAINED_GLASS_PANE.pick(DyeColor.PURPLE)),
		JASPER("Jasper", "jasper", Blocks.STAINED_GLASS.pick(DyeColor.MAGENTA), Blocks.STAINED_GLASS_PANE.pick(DyeColor.MAGENTA)),
		OPAL("Opal", "opal", Blocks.STAINED_GLASS.pick(DyeColor.WHITE), Blocks.STAINED_GLASS_PANE.pick(DyeColor.WHITE));

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
