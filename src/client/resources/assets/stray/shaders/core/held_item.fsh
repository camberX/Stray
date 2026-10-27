#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:matrix.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 texProj0;

out vec4 fragColor;

const vec3[] PORTAL_COLORS = vec3[](
    vec3(0.022087, 0.098399, 0.110818),
    vec3(0.011892, 0.095924, 0.089485),
    vec3(0.027636, 0.101689, 0.100326),
    vec3(0.046564, 0.109883, 0.114838),
    vec3(0.064901, 0.117696, 0.097189),
    vec3(0.063761, 0.086895, 0.123646),
    vec3(0.084817, 0.111994, 0.166380),
    vec3(0.097489, 0.154120, 0.091064),
    vec3(0.106152, 0.131144, 0.195191),
    vec3(0.097721, 0.110188, 0.187229),
    vec3(0.133516, 0.138278, 0.148582),
    vec3(0.070006, 0.243332, 0.235792),
    vec3(0.196766, 0.142899, 0.214696),
    vec3(0.047281, 0.315338, 0.321970),
    vec3(0.204675, 0.390010, 0.302066),
    vec3(0.080955, 0.314821, 0.661491)
);

const mat4 PORTAL_SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

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

#ifndef COVERAGE_MASK
    if (style > 2.5) {
        float t = GameTime * 28.0;
        vec2 uv = (gl_FragCoord.xy - 0.5 * ScreenSize) / max(ScreenSize.y, 1.0);
        float spin = t * 0.045;
        float cs = cos(spin);
        float sn = sin(spin);
        uv = vec2(cs * uv.x - sn * uv.y, sn * uv.x + cs * uv.y);
        vec2 hole = vec2(-0.16, 0.03);
        vec2 delta = uv - hole;
        float r = length(delta);
        float rs = 0.052;
        float lens = (rs * rs) / max(r * r, 0.0006);
        vec2 lu = uv + delta * lens * 1.25;
        float plane = lu.x * 0.32 + lu.y * 0.95;
        float band = exp(-plane * plane * 13.0);
        float dust = fbm(lu * 3.1 + vec2(t * 0.035, -t * 0.02));
        float arm = fbm(lu * 1.35 + vec2(-t * 0.018, t * 0.012));
        vec3 milky = vec3(0.58, 0.50, 0.78) * band * (0.22 + dust * 0.95);
        milky += vec3(1.00, 0.74, 0.42) * band * pow(max(arm, 0.0), 2.1) * 0.62;
        milky += vec3(0.32, 0.58, 1.00) * band * dust * 0.28;
        float cluster = pow(max(fbm(lu * 5.8 + 8.0), 0.0), 2.8);
        float field = starLayer(lu * 26.0 + vec2(t * 0.35, 0.0), t, 0.20) * (0.55 + band * 1.8);
        field += starLayer(lu * 44.0 + vec2(-t * 0.62, t * 0.18), t * 1.3, 0.16) * (0.75 + band * 1.3);
        field += starLayer(lu * 70.0 + vec2(t * 0.18, -t * 0.48), t * 1.75, 0.26);
        field += starLayer(lu * 96.0 + vec2(-t * 0.22, t * 0.40), t * 2.2, 0.10) * cluster * 4.2;
        field += starLayer(lu * 120.0 + vec2(t * 0.55, t * 0.12), t * 2.6, 0.34) * (0.35 + cluster * 1.6);
        float density = mix(0.70, 1.45, (amount - 0.10) / 1.40);
        vec3 body = vec3(0.012, 0.014, 0.034) + milky * density;
        body += mix(fill, vec3(0.96, 0.97, 1.0), 0.62) * field * density;
        vec2 disk = vec2(delta.x, delta.y * 2.40);
        float rd = length(disk);
        float ang = atan(disk.y, disk.x) + t * 1.7 / max(rd, 0.035);
        float ring = smoothstep(0.042, 0.068, rd) * smoothstep(0.30, 0.10, rd);
        float swirl = 0.52 + 0.48 * sin(ang * 8.0 + 1.15 / max(rd, 0.02));
        vec3 diskCol = mix(vec3(1.00, 0.88, 0.48), vec3(1.00, 0.22, 0.06), smoothstep(0.07, 0.24, rd));
        diskCol = mix(diskCol, fill, 0.16);
        body += diskCol * ring * swirl * 1.85;
        float holeMask = smoothstep(rs * 1.18, rs * 0.72, r);
        body *= 1.0 - holeMask;
        body += vec3(1.00, 0.78, 0.42) * smoothstep(0.014, 0.0, abs(r - rs * 1.20)) * 1.55;
        body *= max(light, vec3(0.40));
        float fillLuma = max(dot(fill, vec3(0.2126, 0.7152, 0.0722)), 0.08);
        body = mix(body, body * (fill / fillLuma), 0.82);
        float cover = clamp(tintAmount, 0.08, 0.85);
        fragColor = vec4(mix(tinted, body, cover), tex.a * cover);
#ifdef ESP_FILL
        fragColor.a = cover;
#endif
        return;
    }

    if (style > 1.5) {
        vec3 color = textureProj(Sampler3, texProj0).rgb * PORTAL_COLORS[0];
        for (int i = 0; i < PORTAL_LAYERS; i++) {
            float layer = float(i + 1);
            mat4 translate = mat4(
                1.0, 0.0, 0.0, 17.0 / layer,
                0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (GameTime * 1.5),
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0
            );
            mat2 rotate = mat2_rotate_z(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0));
            mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);
            mat4 layerMat = mat4(scale * rotate) * translate * PORTAL_SCALE_TRANSLATE;
            color += textureProj(Sampler4, texProj0 * layerMat).rgb * PORTAL_COLORS[i];
        }
        color *= mix(0.70, 1.20, (amount - 0.10) / 1.40);
        float fillLuma = max(dot(fill, vec3(0.2126, 0.7152, 0.0722)), 0.08);
        color = mix(color, color * (fill / fillLuma), 0.86);
        float cover = clamp(tintAmount, 0.08, 0.85);
        fragColor = vec4(mix(tinted, color, cover), tex.a * cover);
#ifdef ESP_FILL
        fragColor.a = cover;
#endif
        return;
    }
#endif

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
