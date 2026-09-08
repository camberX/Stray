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

    float t = GameTime * 1650.0 * (0.40 + smokeAmount * 0.55);
    float ang = 0.17453292;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 glintUv = mat2(ca, -sa, sa, ca) * (texCoord0 * 9.5);
    glintUv += vec2(-t * 0.42, t * 0.18);
    float glint = pow(clamp(texture(Sampler1, glintUv).r, 0.0, 1.0), 1.35);

    vec2 smokeUv = texCoord0 * 14.5 + vec2(t * 0.16, -t * 0.34);
    float n1 = fbm(smokeUv);
    float n2 = fbm(smokeUv * 2.35 + vec2(-t * 0.28, t * 0.17) + n1 * 0.55);
    float n3 = fbm(smokeUv * 4.1 + vec2(t * 0.13, -t * 0.22));
    float ridge = 1.0 - abs(n2 * 2.0 - 1.0);
    float filaments = pow(smoothstep(0.52, 0.88, ridge * mix(0.82, 1.12, n3)), 2.15);
    float dust = pow(smoothstep(0.62, 0.98, n1 * n3), 2.8) * 0.22;
    float wisps = clamp(filaments + dust, 0.0, 1.0);
    float spark = glint * mix(0.25, 1.0, filaments);

    vec3 wispColor = mix(fill * 1.05, vec3(1.0), 0.42 + spark * 0.48);
    float strength = mix(0.38, 0.92, (smokeAmount - 0.10) / 1.40);
    vec3 body = mix(tinted, wispColor, wisps * strength);
    body += wispColor * spark * mix(0.18, 0.55, smokeAmount / 1.50);

    fragColor = vec4(body, tex.a);
}
