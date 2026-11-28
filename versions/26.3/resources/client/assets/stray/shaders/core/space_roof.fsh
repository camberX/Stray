#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 viewDir;
layout(location = 2) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

#ifdef CACHED_NEBULA
uniform sampler2D Sampler0;
#endif

// Space Roof: a procedural night sky by view direction. Vertex colour: rgb = tint, a = edge fade.

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

// Fractal noise with a given number of octaves (at most 5): fewer where it doesn't show.
float fbm(vec3 p, int octaves) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        if (i >= octaves) {
            break;
        }
        v += a * valueNoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

// One star layer: at most one star per cell, drawn as a soft dot that twinkles.
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

// A rare shooting star: a short bright streak along a random path.
float shootingStar(vec3 d, float t) {
    const float PERIOD = 7.0;
    float slot = floor(t / PERIOD);
    float phase = fract(t / PERIOD) * PERIOD;
    vec3 h = hash33(vec3(slot, 3.1, 7.7));
    if (h.x > 0.7 || phase > 0.9) {
        return 0.0;
    }
    float k = phase / 0.9;
    vec3 a = normalize(vec3(h.y - 0.5, 0.75 + 0.25 * h.z, h.x - 0.35));
    vec3 b = normalize(a + vec3(0.6 * (h.z - 0.5), -0.25, 0.6 * (h.y - 0.5)));
    vec3 head = normalize(mix(a, b, k));
    vec3 tail = normalize(mix(a, b, max(k - 0.18, 0.0)));
    vec3 ab = head - tail;
    float u = clamp(dot(d - tail, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0);
    float dist = length(d - (tail + ab * u));
    float fade = smoothstep(0.0, 0.15, k) * (1.0 - smoothstep(0.7, 1.0, k));
    return (1.0 - smoothstep(0.0, 0.0035, dist)) * u * u * fade;
}

#ifdef CACHED_NEBULA
// A direction to its pixel in NebulaCache's octahedral texture (world y up): no trigonometry.
vec2 octUv(vec3 d) {
    vec3 o = vec3(d.x, d.z, d.y) / (abs(d.x) + abs(d.y) + abs(d.z));
    vec2 p = o.xy;
    if (o.z < 0.0) {
        vec2 s = vec2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        p = (1.0 - abs(p.yx)) * s;
    }
    return p * 0.5 + 0.5;
}
#endif

vec3 spaceColor(vec3 dir, vec3 tint, float t) {
    vec3 d = normalize(dir);
#ifdef CACHED_NEBULA
    // The clouds and the milky band, worked out once (NebulaCache), by direction.
    vec3 cached = texture(Sampler0, octUv(d)).rgb;
    float band = cached.b;
    float mainCloud = cached.r * 2.0;
    float secondCloud = cached.g * 0.8;
#else
    // A faint milky band across the sky.
    float band = exp(-pow(dot(d, normalize(vec3(0.35, 0.15, 1.0))) * 3.2, 2.0));
    vec3 q = d * 2.1 + vec3(0.0, 0.0, t * 0.003);
    float warp = fbm(q * 1.6 + 4.0, 3);
    float n = fbm(q + warp * 0.9, 4);
    float m = fbm(q * 2.7 + 11.0 - warp * 0.5, 3);
    float mainCloud = pow(n, 2.6) * (0.9 + 1.1 * band);
    float secondCloud = pow(m, 4.0) * 0.8;
#endif
    vec3 second = vec3(tint.b, tint.r * 0.6 + tint.b * 0.4, tint.g);
    vec3 color = vec3(0.004, 0.005, 0.014) + tint * 0.05;
    color += tint * mainCloud;
    color += second * secondCloud;
    float stars = starLayer(d, 42.0, 0.11, 0.06, t) * 1.4
        + starLayer(d, 95.0, 0.13, 0.10 + 0.10 * band, t + 3.0)
        + starLayer(d, 190.0, 0.15, 0.12 + 0.18 * band, t + 7.0) * 0.6;
    color += mix(vec3(1.0), tint * 0.5 + 0.5, 0.25) * stars;
#ifdef SHOOTING_STARS
    color += vec3(1.0, 0.97, 0.92) * shootingStar(d, t) * 1.6;
#endif
    return color;
}

void main() {
    // UV0: x = how far inside the sky (0 at its edge, 1 from the edge fade on), y = opacity.
    float opacity = texCoord0.y;
#ifdef SKY_SHEET
    // The flat layer: always fully sky, and no discard anywhere in this shader, so the GPU can
    // skip its pixels hidden behind blocks before running it.
    float cover = 1.0;
#else
    float fade = clamp(texCoord0.x, 0.0, 1.0);
    if (fade <= 0.0) {
        discard;
    }
    // A soft gradient into the bricks, nudged a little by clouds fixed to the blocks (world
    // position, like vanilla terrain) so it isn't a perfectly straight band.
    float cover = 1.0;
    if (fade < 0.999) {
        vec3 world = viewDir + vec3(CameraBlockPos) - CameraOffset;
        float n = fbm(world * 0.32, 3) * 0.85 + valueNoise(world * 2.1) * 0.15;
        float soft = smoothstep(0.0, 1.0, fade);
        cover = clamp(soft + (n - 0.5) * 1.6 * soft * (1.0 - soft), 0.0, 1.0);
        cover = cover * cover * (3.0 - 2.0 * cover);
        if (cover <= 0.003) {
            discard;
        }
    }
#endif
    // GameTime is the day fraction: in seconds, for drift and twinkle.
    float t = GameTime * 1200.0;
    vec3 color = spaceColor(viewDir, vertexColor.rgb, t);
    // A faint glow in the tint through the middle of the gradient.
    float rim = cover * (1.0 - cover) * 4.0;
    color += vertexColor.rgb * rim * 0.12;
    fragColor = vec4(color, cover * opacity * ColorModulator.a);
}
