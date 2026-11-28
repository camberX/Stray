#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 viewDir;
layout(location = 2) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

// Space Roof floor effects: the starlit floor, the sky mirrored in the floor's tops (real block
// shapes). UV0: x = which way the face points (1 up plus the block's reflectivity 0..1, -1 down,
// 0 a side), y = height over the floor of its column. Vertex colour: rgb = sky colour, a = bit 0
// the starlit floor, bits 1 to 7 its strength in 32nds. Premultiplied: the reflection paints over.

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash13(i);
    float b = hash13(i + vec3(1.0, 0.0, 0.0));
    float c = hash13(i + vec3(0.0, 1.0, 0.0));
    float d = hash13(i + vec3(1.0, 1.0, 0.0));
    float e = hash13(i + vec3(0.0, 0.0, 1.0));
    float g = hash13(i + vec3(1.0, 0.0, 1.0));
    float h = hash13(i + vec3(0.0, 1.0, 1.0));
    float k = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y), mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}

float starLayer(vec3 d, float scale, float size, float density, float t) {
    vec3 p = d * scale;
    vec3 c = floor(p);
    vec3 h = hash33(c);
    if (h.x > density) {
        return 0.0;
    }
    vec3 at = c + 0.25 + 0.5 * hash33(c + 17.0);
    float dist = length(p - at);
    float twinkle = 0.65 + 0.35 * sin(t * (0.8 + 2.5 * h.y) + h.z * 6.2831);
    return (1.0 - smoothstep(0.0, size, dist)) * twinkle * (0.5 + 0.5 * h.z);
}

// A cheap look into space for inside the cracks (by view direction, like the sky).
vec3 space(vec3 dir, vec3 tint, float t) {
    vec3 d = normalize(dir);
    float n = valueNoise(d * 5.0 + vec3(0.0, 0.0, t * 0.01)) * 0.6 + valueNoise(d * 12.0) * 0.4;
    vec3 color = vec3(0.006, 0.007, 0.02) + tint * 0.05 + tint * pow(n, 2.2) * 0.9;
    float stars = starLayer(d, 70.0, 0.12, 0.10, t) + starLayer(d, 150.0, 0.14, 0.14, t + 5.0) * 0.7;
    return color + vec3(1.0) * stars;
}


// Layers stacked over each other (premultiplied).
vec4 over(vec4 below, vec4 above) {
    return above + below * (1.0 - above.a);
}

void main() {
    int flags = int(vertexColor.a * 255.0 + 0.5);
    float facing = texCoord0.x;
    float h = texCoord0.y;
    bool floorTop = facing > 0.5 && h < 0.61;
    vec3 world = viewDir + vec3(CameraBlockPos) - CameraOffset;
    vec3 view = normalize(viewDir);
    float t = GameTime * 1200.0;
    vec3 tint = vertexColor.rgb;
    vec4 color = vec4(0.0);

    if (floorTop) {
        // Starlit floor: the sky mirrored in it, faint looking down, strong at low angles.
        float shine = clamp((facing - 1.0) / 0.998, 0.0, 1.0);
        float fresnel = 0.05 + 0.95 * pow(1.0 - abs(view.y), 5.0);
        // Only where it shows at all (carpets and the like reflect nothing).
        // Reflection strength slider: bits 1 to 7 in 32nds (1 = as made).
        float strength = float(flags >> 1) / 32.0;
        if ((flags & 1) != 0 && fresnel * shine * 0.75 * strength > 0.004) {
            vec3 r = vec3(view.x, -view.y, view.z);
            r.xz += (vec2(valueNoise(world * 1.7), valueNoise(world * 1.7 + 9.0)) - 0.5) * 0.03;
            // How reflective this block is (polished stone a lot, carpet not at all).
            float s = clamp(fresnel * 0.75, 0.0, 0.8) * shine * strength;
            color = over(color, vec4(space(r, tint, t) * s, min(s * 0.55, 0.92)));
        }
    }

    if (color.a <= 0.0 && dot(color.rgb, vec3(1.0)) <= 0.001) {
        discard;
    }
    // Fade into the fog like the blocks under it.
    float clear = 1.0 - total_fog_value(length(viewDir), max(length(viewDir.xz), abs(viewDir.y)),
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd) * FogColor.a;
    color *= clear;
    if (color.a <= 0.001 && max(color.r, max(color.g, color.b)) <= 0.001) {
        discard;
    }
    fragColor = color;
}
