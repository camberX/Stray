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

    float t = GameTime * 380.0 * (0.50 + smokeAmount * 0.50);
    float ang = 0.17453292;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 glintUv = mat2(ca, -sa, sa, ca) * (gl_FragCoord.xy * 0.010);
    glintUv += vec2(-t * 0.55, t * 0.22);
    float glint = pow(clamp(texture(Sampler1, glintUv).r, 0.0, 1.0), 2.4);

    vec2 smokeUv = gl_FragCoord.xy * 0.020 + vec2(t * 2.2, -t * 3.4);
    float n1 = fbm(smokeUv);
    float n2 = fbm(smokeUv * 1.55 + vec2(-t * 1.6, t * 1.1) + n1 * 0.70);
    float ridge = 1.0 - abs(n2 * 2.0 - 1.0);
    float filaments = pow(smoothstep(0.78, 0.98, ridge), 2.8);
    float sheets = pow(smoothstep(0.62, 0.90, n1), 3.2) * 0.18;
    float wisps = clamp(filaments + sheets, 0.0, 1.0);
    float spark = glint * mix(0.15, 1.0, filaments);

    vec3 wispColor = mix(fill * 1.08, vec3(1.0), 0.38 + spark * 0.50);
    float strength = mix(0.28, 0.78, (smokeAmount - 0.10) / 1.40);
    vec3 body = mix(tinted, wispColor, wisps * strength);
    body += wispColor * spark * mix(0.12, 0.40, smokeAmount / 1.50);

    fragColor = vec4(body, tex.a);
}
