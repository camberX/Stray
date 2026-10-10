#version 330
#extension GL_ARB_separate_shader_objects : require

// Liquid marble from the menu backdrop. Loop length and time are seconds.
// Accent recolors only the magenta veins, splashes, and flecks.
layout(std140) uniform MarbleConfig {
    vec2 Resolution;
    float Time;
    float Loop;
    vec4 Accent;
};

layout(location = 0) out vec4 fragColor;

float A;

vec2 orb(float k, float ph, float rad) {
    float a = k * A + ph;
    return rad * vec2(cos(a), sin(a));
}

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float a = 0.5;
    float s = 0.0;
    mat2 m = mat2(1.7, 1.1, -1.1, 1.7);
    for (int i = 0; i < 4; i++) {
        s += a * noise(p);
        p = m * p;
        a *= 0.5;
    }
    return s;
}

vec2 heightField(vec2 p) {
    vec2 w1 = vec2(
        fbm(p * 1.6 + orb(1.0, 0.0, 0.16)),
        fbm(p * 1.6 + vec2(5.2, 1.3) + orb(1.0, 2.1, 0.16))
    );
    vec2 w2 = vec2(
        fbm(p * 3.2 + 3.2 * w1 + vec2(1.7, 9.2) + orb(1.0, 4.0, 0.20)),
        fbm(p * 3.2 + 3.2 * w1 + vec2(8.3, 2.8) + orb(1.0, 1.0, 0.20))
    );
    float mm = fbm(p * 5.0 + 4.0 * w2 + vec2(3.0, 7.0) + orb(1.0, 3.0, 0.24));
    return vec2(fbm(p * 4.0 + 3.6 * w2), mm);
}

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * Resolution) / Resolution.y;
    vec2 c = uv;
    float r = length(c);
    float ang = atan(c.y, c.x);

    float tw = 0.35 / (r * 2.2 + 0.5);
    float ca = cos(tw);
    float sa = sin(tw);
    vec2 q = mat2(ca, -sa, sa, ca) * c;
    q *= 1.0 + 0.8 / (r * 2.6 + 0.55);

    A = 6.28318530718 * mod(Time, Loop) / Loop;
    float eps = 1.5 / Resolution.y;
    vec2 h = heightField(q);
    vec2 hx = heightField(q + vec2(eps, 0.0));
    vec2 hy = heightField(q + vec2(0.0, eps));
    float h0 = h.x;
    float m0 = h.y;
    float m1 = hx.y;
    float m2 = hy.y;

    vec2 g = vec2(hx.x - h0, hy.x - h0) / eps;
    float slope = length(g);
    vec2 L = normalize(vec2(-0.6, 0.8));
    float lit = dot(normalize(g + 1e-5), L) * 0.5 + 0.5;
    float edge = smoothstep(0.9, 3.6, slope) * pow(lit, 2.2);
    float iso = 1.0 - abs(fract(h0 * 7.0) * 2.0 - 1.0);
    iso = pow(iso, 14.0) * smoothstep(0.4, 2.0, slope);

    float xdir = abs(sin(2.0 * ang + 1.2));
    float mask = smoothstep(0.95, 0.08, r);
    mask *= 0.5 + 0.5 * smoothstep(0.2, 0.9, fbm(c * 2.6 + orb(1.0, 1.0, 0.14)));
    mask *= 0.75 + 0.5 * pow(xdir, 1.5);

    vec3 base = vec3(0.035, 0.074, 0.078);
    vec3 grey = vec3(0.62, 0.64, 0.62);
    float sheen = (edge * 0.8 + iso * 0.7) * mask;
    sheen += smoothstep(0.45, 0.75, h0) * 0.06 * mask;
    vec3 col = base + grey * sheen;
    col = mix(col, col * vec3(0.9, 0.95, 1.1), 0.3);

    vec3 accent = clamp(Accent.rgb, vec3(0.0), vec3(1.0));
    vec2 hs1 = vec2(0.78 * sin(A + 0.3), 0.40 * sin(2.0 * A + 1.3));
    vec2 hs2 = vec2(0.70 * sin(A + 2.6), 0.38 * cos(A + 0.4));
    vec2 hs3 = vec2(0.80 * cos(A + 4.1), 0.42 * sin(2.0 * A + 2.2));
    vec2 hs4 = vec2(0.65 * sin(A + 5.0), 0.40 * cos(2.0 * A + 3.5));
    float cl = 1.0 * exp(-dot(c - hs1, c - hs1) * 14.0) + 0.9 * exp(-dot(c - hs2, c - hs2) * 26.0)
        + 0.8 * exp(-dot(c - hs3, c - hs3) * 12.0) + 0.5 * exp(-dot(c - hs4, c - hs4) * 20.0)
        + 0.12 * exp(-r * r * 1.5);
    for (int i = 0; i < 10; i++) {
        float fi = float(i);
        vec2 hp = vec2(0.88 * sin(A + fi * 2.39), 0.48 * cos(A * (1.0 + mod(fi, 2.0)) + fi * 1.71));
        cl += 0.20 * exp(-dot(c - hp, c - hp) * (60.0 + 5.0 * fi));
    }
    cl += 0.03;
    float score = m0 * 0.5 + smoothstep(0.8, 4.0, slope) * 0.55 + (h0 - 0.5) * 0.3;
    float th = 1.09 - 0.30 * clamp(cl, 0.0, 1.2);
    float blob = smoothstep(th, th + 0.025, score);
    vec3 mag = mix(accent * 0.72, mix(accent, vec3(1.0), 0.22), smoothstep(th, th + 0.2, score));
    col = mix(col, mag, blob);
    col += accent * pow(blob, 2.0) * 0.08;

    float vf = 1.0 - abs(fract(h0 * 11.0 + m0 * 2.0) * 2.0 - 1.0);
    float vein = pow(vf, 22.0) * smoothstep(0.35, 1.6, slope);
    float vzone = smoothstep(0.58, 0.72, fbm(c * 2.4 + vec2(7.0, 3.0) + orb(1.0, 2.0, 0.18)));
    vein *= vzone;
    vec3 vcol = mix(accent, vec3(1.0), 0.16);
    col = mix(col, vcol, clamp(vein * 1.6, 0.0, 1.0) * 0.9);

    float fleck = smoothstep(0.84, 0.855, m1 * 0.6 + m2 * 0.4 + smoothstep(0.6, 2.5, slope) * 0.12 + 0.1 * cl);
    col = mix(col, mix(accent, vec3(1.0), 0.32), fleck * (1.0 - blob) * 0.9);

    col *= 0.96 + 0.04 * sin(gl_FragCoord.y * 1.7);
    col *= smoothstep(1.55, 0.25, r);
    col += (hash(gl_FragCoord.xy + floor(mod(Time, Loop) * 30.0)) - 0.5) * 0.012;

    fragColor = vec4(col, 1.0);
}
