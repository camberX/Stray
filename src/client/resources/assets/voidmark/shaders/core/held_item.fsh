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

float starLayer(vec2 uv, float t, float threshold) {
    vec2 id = floor(uv);
    vec2 f = fract(uv);
    float n = hash(id);
    vec2 pos = 0.18 + 0.64 * vec2(hash(id + vec2(3.1, 1.7)), hash(id + vec2(7.7, 4.2)));
    float d = length(f - pos);
    float tw = 0.40 + 0.60 * sin(t * (2.2 + n * 3.5) + n * 40.0);
    float core = smoothstep(0.055, 0.0, d);
    float glow = smoothstep(0.16, 0.0, d) * 0.35;
    float present = smoothstep(threshold, 1.0, n);
    return (core + glow) * tw * present;
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
    float amount = clamp(ModelOffset.y, 0.10, 1.50);
    float style = ModelOffset.z;
    vec3 albedo = tex.rgb * vertexColor.rgb;
    vec3 tinted = mix(albedo, albedo * fill, tintAmount);
    float glint = pow(clamp(texture(Sampler1, gl_FragCoord.xy * 0.010).r, 0.0, 1.0), 2.4);

    if (style > 0.5) {
        float t = GameTime * 90.0;
        vec2 screen = gl_FragCoord.xy;
        vec3 night = mix(tinted, fill * 0.16 + albedo * 0.10, tintAmount * 0.78);
        float far = starLayer(screen * 0.016 + vec2(t * 0.55, -t * 0.22), t, 0.84);
        float mid = starLayer(screen * 0.028 + vec2(-t * 0.90, t * 0.40), t * 1.25, 0.78);
        float near = starLayer(screen * 0.044 + vec2(t * 1.10, t * 0.16), t * 1.65, 0.90);
        float field = far + mid * 0.85 + near;
        float nebula = pow(fbm(screen * 0.0075 + vec2(t * 0.10, -t * 0.07)), 2.4);
        float density = mix(0.32, 1.0, (amount - 0.10) / 1.40);
        vec3 starCol = mix(fill, vec3(0.96, 0.97, 1.0), 0.72);
        vec3 body = night + fill * nebula * mix(0.06, 0.22, density);
        body += starCol * field * density;
        body += vec3(1.0) * near * near * 0.55 * density;
        body += starCol * glint * near * 0.20;
        fragColor = vec4(body, tex.a);
        return;
    }

    float t = GameTime * 140.0 * (0.55 + amount * 0.45);
    vec2 flowA = gl_FragCoord.xy * 0.012 + vec2(t * 1.10, -t * 1.65);
    vec2 flowB = gl_FragCoord.xy * 0.018 + vec2(-t * 0.80, t * 1.20);
    float warp = fbm(flowA);
    float cloud = fbm(flowA + vec2(warp * 0.90, -warp * 0.55));
    float haze = fbm(flowB + vec2(warp * 0.35, cloud * 0.25));
    float smoke = smoothstep(0.22, 0.80, cloud * 0.70 + haze * 0.40);
    smoke = smoke * smoke * (3.0 - 2.0 * smoke);
    float bloom = smoothstep(0.55, 0.88, cloud);
    float strength = mix(0.24, 0.68, (amount - 0.10) / 1.40);
    vec3 smokeColor = mix(fill * 0.78, fill * 1.18, haze);
    smokeColor = mix(smokeColor, vec3(1.0), bloom * 0.22);
    smokeColor += fill * glint * bloom * 0.12;
    vec3 body = mix(tinted, smokeColor, smoke * strength);
    body += smokeColor * bloom * mix(0.04, 0.16, amount / 1.50);

    fragColor = vec4(body, tex.a);
}
