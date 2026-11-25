package dev.stray.client.visual;

import com.mojang.blaze3d.vertex.QuadInstance;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.mining.FocusMode;
import dev.stray.client.render.StrayUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Monochrome: the world's blocks in black and white, with some of it in one accent colour.
 * Two things get the accent:
 * <ul>
 * <li>Splash: every texture pixel close to a chosen hue (the accent's own by default), so red
 * things stay "the red" while everything else goes grey.</li>
 * <li>Marked blocks: blocks you can use (levers, buttons, doors, crafting stations...) and the
 * blocks on your own list, the whole block.</li>
 * </ul>
 * Accented pixels keep the texture's light and dark detail and the world's lighting: the accent
 * replaces the colour, not the shading. Visual only; the terrain shader does it.
 *
 * <p>Marked blocks carry a marker in their vertex colour (magenta, green 0), set when the chunk
 * is built. Blocks with a biome tint (grass, leaves, water) are never marked, so their tint
 * stays for the grey and splash maths.
 */
public final class Monochrome {
	/** The marker: magenta. Face shade and ambient occlusion scale it, the zero green stays. */
	public static final int MARKER = 0xFF00FF;
	/** Splash hues: the accent's own, or a fixed one (degrees). */
	public static final String[] SPLASH_LABELS = {"Accent colour", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink"};
	private static final float[] SPLASH_HUES = {-1f, 0f, 30f, 55f, 120f, 180f, 220f, 275f, 320f};
	/** Block id parts for blocks you click to use. */
	private static final String[] USABLE = {
		"lever", "_button", "door", "fence_gate", "pressure_plate", "crafting_table", "furnace", "smoker",
		"anvil", "enchanting_table", "brewing_stand", "hopper", "dispenser", "dropper", "note_block", "jukebox",
		"barrel", "lectern", "composter", "beacon", "grindstone", "stonecutter", "loom", "cartography_table",
		"smithing_table", "fletching_table", "cauldron", "repeater", "comparator", "daylight_detector",
		"crafter", "respawn_anchor"
	};

	private static volatile boolean active;
	private static volatile Set<Block> marked = Set.of();
	private static int lastKey = Integer.MIN_VALUE;

	private Monochrome() {
	}

	/** Every client tick: rebuilds the chunks when what gets marked changes. */
	public static void tick() {
		StrayConfig config = StrayConfig.get();
		boolean on = config.monochrome;
		int key = on ? (1 | (config.monochromeUsable ? 2 : 0) | (listOf(config).hashCode() << 2)) : 0;
		if (key == lastKey) {
			return;
		}
		lastKey = key;
		marked = on ? build(config) : Set.of();
		active = on;
		rebuild();
	}

	public static boolean active() {
		return active;
	}

	/** True when this block's quads get the marker. Focus mode's markers win in the Crystal Hollows. */
	public static boolean marks(Block block) {
		return active && !FocusMode.visual() && marked.contains(block);
	}

	/** Vanilla chunk building: marks a quad of a marked block. */
	public static void paint(QuadInstance quad, BlockState state) {
		if (quad == null || state == null || !marks(state.getBlock())) {
			return;
		}
		for (int i = 0; i < 4; i++) {
			int color = quad.getColor(i);
			int alpha = (color >>> 24) == 0 ? 0xFF : color >>> 24;
			// Keep the face shade (it is the same in every channel of an untinted block).
			int shade = Math.max((color >> 16) & 0xFF, color & 0xFF);
			quad.setColor(i, (alpha << 24) | (shade << 16) | shade);
		}
	}

	/** Shader values: the accent, on or off, the splash hue and its width. */
	public static void bind() {
		StrayConfig config = StrayConfig.get();
		boolean on = active && !FocusMode.visual();
		int rgb = config.monochromeRgb & 0xFFFFFF;
		float hue = splashHue(config);
		float width = StrayConfig.clamp(config.monochromeSplashWidth, 5f, 90f) / 360f;
		StrayUniforms.monochrome(
			((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, on ? 1f : 0f,
			hue / 360f, width, config.monochromeSplash ? 1f : 0f, StrayConfig.clamp(config.monochromeContrast, 0.5f, 1.5f)
		);
	}

	private static float splashHue(StrayConfig config) {
		int pick = Math.floorMod(config.monochromeSplashHue, SPLASH_HUES.length);
		if (SPLASH_HUES[pick] >= 0f) {
			return SPLASH_HUES[pick];
		}
		return WorldTint.rgbToHsv(config.monochromeRgb & 0xFFFFFF)[0];
	}

	// ------------------------------------------------------------------ your list

	/** Adds a block to your list. Returns false when it was there already. */
	public static boolean add(Block block) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		List<String> list = listOf(StrayConfig.get());
		if (list.contains(id)) {
			return false;
		}
		list.add(id);
		StrayConfig.get().save();
		return true;
	}

	/** Takes a block off your list. Returns false when it wasn't on it. */
	public static boolean remove(Block block) {
		boolean removed = listOf(StrayConfig.get()).remove(BuiltInRegistries.BLOCK.getKey(block).toString());
		if (removed) {
			StrayConfig.get().save();
		}
		return removed;
	}

	public static void clear() {
		listOf(StrayConfig.get()).clear();
		StrayConfig.get().save();
	}

	public static int listSize() {
		return listOf(StrayConfig.get()).size();
	}

	/** The block under the crosshair, or null. */
	public static Block lookedAt() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
			|| hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return null;
		}
		BlockState state = client.level.getBlockState(hit.getBlockPos());
		return state.isAir() ? null : state.getBlock();
	}

	public static String name(Block block) {
		return block.getName().getString();
	}

	private static List<String> listOf(StrayConfig config) {
		if (config.monochromeBlocks == null) {
			config.monochromeBlocks = new java.util.ArrayList<>();
		}
		return config.monochromeBlocks;
	}

	private static Set<Block> build(StrayConfig config) {
		Set<Block> set = new HashSet<>();
		if (config.monochromeUsable) {
			for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
				String path = id.getPath();
				for (String part : USABLE) {
					if (path.contains(part)) {
						set.add(BuiltInRegistries.BLOCK.getValue(id));
						break;
					}
				}
			}
		}
		for (String raw : listOf(config)) {
			Identifier id = Identifier.tryParse(raw);
			if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
				set.add(BuiltInRegistries.BLOCK.getValue(id));
			}
		}
		return Set.copyOf(set);
	}

	private static void rebuild() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.level == null) {
			return;
		}
		if (WorldTint.sodiumLoaded()) {
			try {
				Class.forName("dev.stray.client.compat.SodiumFocus").getMethod("rebuild").invoke(null);
				return;
			} catch (Throwable exception) {
				Stray.LOGGER.warn("Sodium chunk reload failed, falling back to a full rebuild", exception);
			}
		}
		if (client.levelRenderer != null) {
			client.levelExtractor.allChanged();
		}
	}

	// ------------------------------------------------------------------ shader

	/**
	 * Fragment code: {@code tex} the texture, {@code tint} the flat vertex colour (biome tint
	 * and face shade, or the marker), {@code light} the lightmap, {@code lit} the normal result.
	 */
	public static final String FN = """
		const vec3 STRAY_LUMA = vec3(0.2126, 0.7152, 0.0722);
		vec3 strayHsv(vec3 c) {
		    vec4 k = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
		    vec4 p = mix(vec4(c.bg, k.wz), vec4(c.gb, k.xy), step(c.b, c.g));
		    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
		    float d = q.x - min(q.w, q.y);
		    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + 1e-6)), d / (q.x + 1e-6), q.x);
		}
		// The accent with the texture's detail: dark texels a deep accent, bright ones lifting
		// toward white, so a recoloured block still reads as that block.
		vec3 strayDuo(float l) {
		    vec3 c = u_StrayMono.rgb * clamp(0.25 + 1.1 * l, 0.0, 1.2);
		    return mix(c, vec3(1.0), smoothstep(0.72, 1.0, l) * 0.3);
		}
		float strayContrast(float l) {
		    return clamp((l - 0.5) * u_StrayMono2.w + 0.5, 0.0, 1.0);
		}
		vec4 strayMono(vec4 tex, vec4 tint, vec4 light, vec4 lit) {
		    float lightL = dot(light.rgb, STRAY_LUMA);
		    bool marked = tint.g < 0.004 && tint.r > 0.02 && abs(tint.r - tint.b) < 0.03;
		    if (marked) {
		        float l = strayContrast(dot(tex.rgb, STRAY_LUMA));
		        return vec4(strayDuo(l) * tint.r * lightL, lit.a);
		    }
		    vec3 base = tex.rgb * tint.rgb;
		    float baseL = max(dot(base, STRAY_LUMA), 1e-4);
		    float litL = dot(lit.rgb, STRAY_LUMA);
		    vec3 grey = vec3(strayContrast(litL));
		    if (u_StrayMono2.z < 0.5) {
		        return vec4(grey, lit.a);
		    }
		    vec3 hsv = strayHsv(base);
		    float dist = abs(fract(hsv.x - u_StrayMono2.x + 0.5) - 0.5);
		    float w = (1.0 - smoothstep(u_StrayMono2.y * 0.5, u_StrayMono2.y, dist))
		        * smoothstep(0.22, 0.45, hsv.y) * smoothstep(0.06, 0.18, hsv.z);
		    // Same lighting as the grey: the accent detail times how lit this pixel is.
		    vec3 splash = strayDuo(strayContrast(baseL)) * clamp(litL / baseL, 0.0, 1.5);
		    return vec4(mix(grey, splash, w), lit.a);
		}
		""";
}
