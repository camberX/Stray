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

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (tex.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

#ifdef COVERAGE_MASK
    fragColor = vec4(ColorModulator.rgb, 1.0);
    return;
#endif

    vec3 fill = ColorModulator.rgb;
    float tintAmount = ColorModulator.a;
    float smokeAmount = clamp(ModelOffset.y, 0.10, 1.50);
    vec3 albedo = tex.rgb * vertexColor.rgb;
    vec3 tinted = mix(albedo, albedo * fill, tintAmount);

    float t = GameTime * 1400.0 * (0.35 + smokeAmount);
    vec2 flow = texCoord0 * 4.2;
    float ang = 0.17453292;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 glintUv = mat2(ca, -sa, sa, ca) * flow;
    glintUv += vec2(-t * 0.38, t * 0.16);
    float glint = pow(clamp(texture(Sampler1, glintUv).r, 0.0, 1.0), 0.85);

    vec2 smokeUv = texCoord0 * 3.2 + vec2(t * 0.22, -t * 0.14);
    float n1 = fbm(smokeUv);
    float n2 = fbm(smokeUv * 1.85 + vec2(-t * 0.18, t * 0.21) + n1 * 1.4);
    float bands = fbm(smokeUv * 0.55 + vec2(t * 0.09, t * 0.05));
    float smoke = smoothstep(0.18, 0.58, mix(n1, n2, 0.62) + bands * 0.22);
    float wisps = smoothstep(0.28, 0.82, max(glint, n2));

    vec3 mist = mix(fill * 0.35, mix(fill, vec3(1.0), 0.55), wisps);
    float smokeMix = smoke * mix(0.28, 0.92, (smokeAmount - 0.10) / 1.40);
    vec3 body = mix(tinted, mist, smokeMix);
    body = mix(body, mix(tinted, vec3(1.0), 0.45), glint * mix(0.20, 0.70, smokeAmount / 1.50));

    fragColor = vec4(body, tex.a);
}
