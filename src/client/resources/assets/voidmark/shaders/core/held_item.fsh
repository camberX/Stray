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
    float fillOpacity = ColorModulator.a;
    float smokeSpeed = max(ModelOffset.y, 0.05);
    vec3 albedo = tex.rgb * vertexColor.rgb;
    vec3 tinted = albedo * mix(vec3(1.0), fill, 0.82);

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

    vec3 mist = mix(tinted, mix(fill, vec3(1.0), 0.35), wisps);
    vec3 body = mix(tinted, mist, smoke * 0.42);
    body = mix(body, mix(tinted, vec3(1.0), 0.28), glint * 0.35);

    float alpha = tex.a * mix(0.42, 0.92, fillOpacity);
    fragColor = vec4(body, clamp(alpha, 0.0, 1.0));
}
