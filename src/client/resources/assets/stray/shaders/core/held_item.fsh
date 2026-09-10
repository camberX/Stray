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
#ifdef ESP_FILL
    float core = smoothstep(0.08, 0.0, d);
    float glow = smoothstep(0.22, 0.0, d) * 0.42;
#else
    float core = smoothstep(0.055, 0.0, d);
    float glow = smoothstep(0.16, 0.0, d) * 0.35;
#endif
    float present = smoothstep(threshold, 1.0, n);
    return (core + glow) * tw * present;
}

// Screen-space smoke/stars look right up close, but a distant player only
// covers a few pixels of that same large pattern. Scale frequency with
// camera distance so the animation stays readable far away.
float animationScale() {
#ifdef ESP_FILL
    // Keep a similar amount of features on the player at any range.
    float dist = max(sphericalVertexDistance, 0.35);
    return clamp(1.25 + dist * 0.45, 1.25, 28.0);
#else
    return clamp(max(sphericalVertexDistance, 0.25) / 6.0, 1.0, 16.0);
#endif
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (tex.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
#ifdef ESP_FILL
    if (tex.a < 0.5) {
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
    vec3 light = max(vertexColor.rgb, vec3(0.02));
    vec3 albedo = tex.rgb * light;
#ifdef ESP_FILL
    // Unused overlay/armor texels are often RGB 0 with high alpha. Keep the
    // fill color there instead of multiplying into a black shell.
    float albedoPeak = max(tex.r, max(tex.g, tex.b));
    albedo = mix(fill * light, albedo, step(0.04, albedoPeak));
#endif
    vec3 tinted = mix(albedo, albedo * fill, tintAmount);
#ifdef ESP_FILL
    // Constant pixel frequency. Scaling by interpolated camera distance
    // stretched the field from head to toe.
    vec2 screen = gl_FragCoord.xy;
    float glint = 0.0;
#else
    float distScale = animationScale();
    vec2 screen = gl_FragCoord.xy * distScale;
    float glint = pow(clamp(texture(Sampler1, screen * 0.010).r, 0.0, 1.0), 2.4);
#endif

    if (style > 1.5) {
        float t = GameTime * 18.0;
        vec2 ndc = screen;
#ifdef ESP_FILL
        ndc *= 0.62;
#else
        ndc *= 0.48;
#endif
        ndc /= max(ScreenSize.y, 1.0);
        vec3 acc = vec3(0.008, 0.018, 0.024);
        for (int i = 0; i < 12; i++) {
            float layer = float(i + 1);
            float ang = (layer * layer * 4321.0 + layer * 9.0) * 0.034906 + t * (0.09 + layer * 0.007);
            float cs = cos(ang);
            float sn = sin(ang);
            float sc = (4.5 - layer * 0.22) * 1.18;
            vec2 uv = vec2(cs * ndc.x - sn * ndc.y, sn * ndc.x + cs * ndc.y) * sc;
            uv.y += (2.0 + layer / 1.5) * t * 0.20;
            uv.x += 17.0 / layer * 0.035;
            float n = hash(floor(uv * 9.0));
            vec2 f = fract(uv * 9.0) - 0.5;
            vec2 jitter = 0.28 * (vec2(hash(floor(uv * 9.0) + 3.1), hash(floor(uv * 9.0) + 7.7)) - 0.5);
            float star = smoothstep(0.16, 0.0, length(f - jitter)) * step(0.76, n);
            float fog = noise(uv * 2.6 + vec2(t * 0.04, -t * 0.03));
            vec3 layerCol = vec3(
                0.018 + 0.11 * fract(layer * 0.17),
                0.075 + 0.09 * fract(layer * 0.31),
                0.088 + 0.12 * fract(layer * 0.53)
            );
            acc += layerCol * (0.20 + fog * 0.50) / (0.48 + layer * 0.11);
            acc += vec3(0.48, 0.82, 0.58) * star * (1.55 / layer);
        }
        float density = mix(0.52, 1.18, (amount - 0.10) / 1.40);
        acc *= density;
        acc = mix(acc, acc * (fill * 0.82 + vec3(0.18)), 0.40);
        vec3 body = acc * max(light, vec3(0.42));
        body += fill * light * glint * 0.08;
        fragColor = vec4(body, tex.a);
#ifdef ESP_FILL
        fragColor.a = 1.0;
#endif
        return;
    }

    if (style > 0.5) {
        float t = GameTime * 90.0;
#ifdef ESP_FILL
        vec3 night = tinted;
        float far = starLayer(screen * 0.030 + vec2(t * 0.55, -t * 0.22), t, 0.50);
        float mid = starLayer(screen * 0.050 + vec2(-t * 0.90, t * 0.40), t * 1.25, 0.44);
        float near = starLayer(screen * 0.078 + vec2(t * 1.10, t * 0.16), t * 1.65, 0.54);
        float dust = starLayer(screen * 0.118 + vec2(-t * 0.35, t * 0.80), t * 2.10, 0.34);
        float spark = starLayer(screen * 0.168 + vec2(t * 1.40, -t * 0.55), t * 2.55, 0.58);
        float field = far + mid * 0.92 + near * 0.82 + dust * 0.58 + spark * 0.72;
        float nebula = pow(fbm(screen * 0.012 + vec2(t * 0.10, -t * 0.07)), 2.2);
        float density = mix(0.58, 1.0, (amount - 0.10) / 1.40);
#else
        vec3 night = mix(tinted, (fill * 0.16 + tex.rgb * 0.10) * light, tintAmount * 0.78);
        float far = starLayer(screen * 0.016 + vec2(t * 0.55, -t * 0.22), t, 0.84);
        float mid = starLayer(screen * 0.028 + vec2(-t * 0.90, t * 0.40), t * 1.25, 0.78);
        float near = starLayer(screen * 0.044 + vec2(t * 1.10, t * 0.16), t * 1.65, 0.90);
        float field = far + mid * 0.85 + near;
        float nebula = pow(fbm(screen * 0.0075 + vec2(t * 0.10, -t * 0.07)), 2.4);
        float density = mix(0.32, 1.0, (amount - 0.10) / 1.40);
#endif
        vec3 starCol = mix(fill, vec3(0.96, 0.97, 1.0), 0.72) * light;
        vec3 body = night + fill * light * nebula * mix(0.06, 0.22, density);
        body += starCol * field * density;
        body += light * near * near * 0.55 * density;
        body += starCol * glint * near * 0.20;
        fragColor = vec4(body, tex.a);
#ifdef ESP_FILL
        fragColor.a = 1.0;
#endif
        return;
    }

    float t = GameTime * 140.0 * (0.55 + amount * 0.45);
#ifdef ESP_FILL
    vec2 flowA = screen * 0.022 + vec2(t * 1.10, -t * 1.65);
    vec2 flowB = screen * 0.034 + vec2(-t * 0.80, t * 1.20);
#else
    vec2 flowA = screen * 0.012 + vec2(t * 1.10, -t * 1.65);
    vec2 flowB = screen * 0.018 + vec2(-t * 0.80, t * 1.20);
#endif
    float warp = fbm(flowA);
    float cloud = fbm(flowA + vec2(warp * 0.90, -warp * 0.55));
    float haze = fbm(flowB + vec2(warp * 0.35, cloud * 0.25));
    float smoke = smoothstep(0.22, 0.80, cloud * 0.70 + haze * 0.40);
    smoke = smoke * smoke * (3.0 - 2.0 * smoke);
    float bloom = smoothstep(0.55, 0.88, cloud);
    float strength = mix(0.24, 0.68, (amount - 0.10) / 1.40);
    vec3 smokeColor = mix(fill * 0.78, fill * 1.18, haze) * light;
    smokeColor = mix(smokeColor, light, bloom * 0.22);
    smokeColor += fill * light * glint * bloom * 0.12;
    vec3 body = mix(tinted, smokeColor, smoke * strength);
    body += smokeColor * bloom * mix(0.04, 0.16, amount / 1.50);

    fragColor = vec4(body, tex.a);
#ifdef ESP_FILL
    fragColor.a = 1.0;
#endif
}
