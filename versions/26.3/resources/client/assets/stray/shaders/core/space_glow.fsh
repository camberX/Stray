#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 viewDir;
layout(location = 2) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

#ifdef CRACK_TEX
uniform sampler2D Sampler0;
#endif

// Space Roof block effects, on the blocks under the sky (drawn under it, so they show where it
// thins out). UV0: x = depth under the sky layer in blocks, y = the fade (whole blocks) plus how
// close to the layer sideways (fraction). Vertex colour: rgb = sky colour, a = effect bits
// (1 nebula light, 2 glowing seams, 4 space cracks). Premultiplied: light adds, cracks paint over.

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

// Distances to the nearest and second nearest cell point: their difference is small on cell borders.
vec2 cells(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    float d1 = 8.0;
    float d2 = 8.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                vec3 o = vec3(float(x), float(y), float(z));
                vec3 r = o + hash33(i + o) - f;
                float d = dot(r, r);
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
    }
    return vec2(sqrt(d1), sqrt(d2));
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

void main() {
    int flags = int(vertexColor.a * 255.0 + 0.5);
    float depth = texCoord0.x;
    float fade = floor(texCoord0.y);
    float near = clamp(fract(texCoord0.y) / 0.999, 0.0, 1.0);
    if (near <= 0.002 || flags == 0) {
        discard;
    }
    // Below every effect that's on: nothing to do here.
    float reach = max((flags & 1) != 0 ? fade + 8.0 : 0.0, max((flags & 2) != 0 ? fade + 5.0 : 0.0, (flags & 4) != 0 ? fade + 3.0 : 0.0));
    if (depth >= reach) {
        discard;
    }
    vec3 world = viewDir + vec3(CameraBlockPos) - CameraOffset;
    // The face's direction from how the position changes across the screen; where that's almost
    // nothing (edge-on, far away) normalizing would give NaN and glitch, so fall back to up.
    vec3 n = cross(dFdx(world), dFdy(world));
    float nl = length(n);
    n = nl > 1e-12 ? n / nl : vec3(0.0, 1.0, 0.0);
    vec3 an = abs(n);
    // Face coordinates: across and up for sides, x and z for tops and undersides.
    vec2 uv = an.y > 0.5 ? world.xz : (an.x > 0.5 ? world.zy : world.xy);
    float t = GameTime * 1200.0;
    vec3 tint = vertexColor.rgb;
    vec3 add = vec3(0.0);
    vec3 paint = vec3(0.0);
    float alpha = 0.0;

    // 1: the nebula lights the stone in its colour, strongest near the sky and fading smoothly
    // down, drifting slowly.
    if ((flags & 1) != 0) {
        float start = fade * 0.5;
        float k = clamp(1.0 - max(depth - start, 0.0) / (fade + 8.0 - start), 0.0, 1.0);
        k *= k;
        if (k > 0.0) {
            // Slow soft blobs drifting through (a few waves, cheaper than noise).
            float drift = 0.7 + 0.3 * (0.5 + 0.5 * sin(world.x * 0.23 + t * 0.21) * sin(world.z * 0.19 - t * 0.17) * sin(world.y * 0.31 + world.x * 0.07 + t * 0.13));
            add += tint * k * drift * 0.32;
        }
    }

    // 2: glowing seams: block edges and brick joints (two rows per block, staggered) glow in the
    // sky's colour, pulsing slowly block by block, fading a few blocks under the sky.
    if ((flags & 2) != 0) {
        float k = clamp(1.0 - depth / (fade + 5.0), 0.0, 1.0);
        if (k > 0.0) {
            float row = floor(uv.y * 2.0);
            float fy = fract(uv.y * 2.0);
            float fx = fract(uv.x + mod(row, 2.0) * 0.5);
            float e = min(min(fx, 1.0 - fx), min(fy, 1.0 - fy) * 0.5);
            float w = fwidth(e) + 0.012;
            float line = 1.0 - smoothstep(0.0, w, e);
            float pulse = 0.65 + 0.35 * sin(t * 0.7 + dot(floor(world), vec3(0.7, 0.3, 0.5)));
            add += (tint * 1.5 + 0.25) * line * pulse * k;
        }
    }

    // 3: where the sky thins out, cracks eat into the stone with space showing through, rimmed
    // in its colour; wide there, thin and gone a few blocks lower.
    if ((flags & 4) != 0) {
        float band = 1.0 - smoothstep(fade * 0.5, fade + 3.0, depth);
        if (band > 0.01) {
            // Where the mask hides the crack anyway, skip working out the cells (the costly part).
            float wobble = smoothstep(0.3, 0.7, valueNoise(world * 0.5));
            float width = band * band * 0.16 * wobble;
            if (width > 0.002) {
#ifdef CRACK_TEX
                // The pattern worked out once (CrackCache), by the face's own coordinates: 18 cells
                // across the texture, 1.1 cells a block as below.
                float edge = texture(Sampler0, uv * (1.1 / 18.0)).r * 0.5;
#else
                vec2 c = cells(world * 1.1);
                float edge = c.y - c.x;
#endif
                float crack = 1.0 - smoothstep(width * 0.6, width, edge);
                float rim = (1.0 - smoothstep(width, width * 2.6 + 0.02, edge)) * band * wobble;
                add += tint * rim * 0.6 * (1.0 - crack);
                paint = space(viewDir, tint, t);
                alpha = crack;
            }
        }
    }

    // Fade into the fog like the blocks under it.
    float clear = 1.0 - total_fog_value(length(viewDir), max(length(viewDir.xz), abs(viewDir.y)),
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd) * FogColor.a;
    add *= near * clear;
    alpha *= near * clear;
    // Nothing to add here: skip the blend.
    if (alpha <= 0.001 && max(add.r, max(add.g, add.b)) <= 0.001) {
        discard;
    }
    fragColor = vec4(add + paint * alpha, alpha);
}
