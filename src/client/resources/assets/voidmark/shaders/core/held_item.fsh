#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.52;
    mat2 m = mat2(0.80, -0.60, 0.60, 0.80);
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = m * p * 2.05;
        a *= 0.52;
    }
    return v;
}

float texelAlpha(vec2 uv) {
    return texture(Sampler0, uv).a;
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
    float outlineStrength = clamp((ModelOffset.x - 0.15) / 1.35, 0.0, 1.0);
    float smokeSpeed = max(ModelOffset.y, 0.05);

    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    float neighbor = min(
        min(texelAlpha(texCoord0 + vec2(texel.x, 0.0)), texelAlpha(texCoord0 - vec2(texel.x, 0.0))),
        min(texelAlpha(texCoord0 + vec2(0.0, texel.y)), texelAlpha(texCoord0 - vec2(0.0, texel.y)))
    );
    float neighbor2 = min(
        min(texelAlpha(texCoord0 + vec2(texel.x * 2.0, 0.0)), texelAlpha(texCoord0 - vec2(texel.x * 2.0, 0.0))),
        min(texelAlpha(texCoord0 + vec2(0.0, texel.y * 2.0)), texelAlpha(texCoord0 - vec2(0.0, texel.y * 2.0)))
    );
#ifdef ALPHA_CUTOUT
    float cut = ALPHA_CUTOUT;
#else
    float cut = 0.1;
#endif
    float edge1 = 1.0 - step(cut, neighbor);
    float edge2 = 1.0 - step(cut, neighbor2);
    float outline = clamp(mix(edge1, max(edge1, edge2 * 0.85), outlineStrength), 0.0, 1.0);

    float t = GameTime * 420.0 * smokeSpeed;
    vec2 flow = texCoord0 * 6.5;
    float ang = 0.17453292;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 glintUv = mat2(ca, -sa, sa, ca) * flow;
    glintUv += vec2(-t * 0.22, t * 0.08);
    float glint = pow(clamp(texture(Sampler1, glintUv).r, 0.0, 1.0), 1.35);

    vec2 smokeUv = texCoord0 * 5.4 + vec2(t * 0.12, -t * 0.07);
    float n1 = fbm(smokeUv);
    float n2 = fbm(smokeUv * 1.65 + vec2(-t * 0.09, t * 0.11) + n1);
    float smoke = smoothstep(0.22, 0.86, mix(n1, n2, 0.58));
    float wisps = smoothstep(0.52, 0.96, max(glint, n2));

    vec3 body = fill * 0.16;
    vec3 mist = mix(fill * 0.55, mix(fill, vec3(0.85, 0.97, 1.0), 0.55), wisps);
    body = mix(body, mist, smoke * 0.82);
    body = mix(body, mix(fill, vec3(1.0), 0.35), glint * 0.72);

    vec3 rim = vec3(0.95, 0.98, 1.0);
    vec3 color = mix(body, rim, outline);
    float alpha = tex.a * mix(fillOpacity * (0.28 + 0.42 * smoke + 0.22 * glint), 1.0, outline);
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
