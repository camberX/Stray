#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec4 vertexColor;
in vec3 viewDir;
in vec2 texCoord0;

out vec4 fragColor;

// Space Roof rising debris: small stone pieces with edges glowing in the sky's colour.
// UV0 = position on the face (0..1). Vertex colour: rgb = sky colour, a = face shade.

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

void main() {
    vec2 uv = texCoord0;
    float e = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float w = fwidth(e) * 1.5 + 0.06;
    float edge = 1.0 - smoothstep(0.0, w, e);
    float speck = 0.85 + 0.3 * hash13(vec3(floor(uv * 4.0), floor(vertexColor.a * 50.0)));
    vec3 stone = vec3(0.42, 0.42, 0.45) * vertexColor.a * speck;
    vec3 color = mix(stone, vertexColor.rgb * 1.4 + 0.12, edge * 0.85);
    fragColor = vec4(color, 1.0);
}
