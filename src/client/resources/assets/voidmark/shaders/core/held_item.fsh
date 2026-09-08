#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewNormal;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    mat2 m = mat2(0.80, -0.60, 0.60, 0.80);
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = m * p * 2.07;
        a *= 0.55;
    }
    return v;
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (tex.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    vec3 fill = ColorModulator.rgb;
    float fillOpacity = ColorModulator.a;
    float outlineStrength = max(ModelOffset.x, 0.0);
    float smokeSpeed = max(ModelOffset.y, 0.05);

    float t = GameTime * 480.0 * smokeSpeed;
    vec2 uv = texCoord0 * 4.2;
    float n1 = fbm(uv + vec2(t * 0.35, t * 0.18));
    float n2 = fbm(uv * 1.7 - vec2(t * 0.22, -t * 0.31) + n1);
    float smoke = smoothstep(0.18, 0.88, mix(n1, n2, 0.62));
    float wisps = smoothstep(0.45, 0.95, n2) * 0.55;

    float lum = dot(tex.rgb, vec3(0.30, 0.54, 0.16));
    vec3 dark = fill * 0.28;
    vec3 mid = fill * 0.78;
    vec3 bright = mix(fill, vec3(1.0), 0.42);
    vec3 body = mix(dark, mid, smoke);
    body = mix(body, bright, wisps * 0.65);
    body *= mix(0.72, 1.18, lum);
    body *= mix(0.85, 1.12, vertexColor.r);

    float edge = length(vec2(dFdx(tex.a), dFdy(tex.a)));
    float texOutline = smoothstep(0.04, 0.28, edge);
    float fresnel = pow(1.0 - abs(normalize(viewNormal).z), 2.4);
    float outline = clamp(max(texOutline * 1.35, fresnel * 0.85) * outlineStrength, 0.0, 1.0);

    vec3 color = mix(body, mix(fill, vec3(1.0), 0.55), outline);
    float alpha = tex.a * mix(fillOpacity * (0.42 + 0.58 * smoke), mix(fillOpacity, 0.95, 0.55), outline);
    alpha *= vertexColor.a;

    vec4 outColor = vec4(color, clamp(alpha, 0.0, 1.0));
    fragColor = apply_fog(outColor, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
