#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec3 worldDir;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    return mix(mix(nx00, nx10, f.y), mix(nx01, nx11, f.y), f.z);
}

float fbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.52;
    for (int i = 0; i < 5; i++) {
        sum += amp * vnoise(p);
        p = p * 2.11 + 14.7;
        amp *= 0.5;
    }
    return sum;
}

float starLayer(vec3 dir, float scale, float threshold, float radius) {
    vec3 p = dir * scale;
    vec3 id = floor(p);
    vec3 f = fract(p) - 0.5;
    float rnd = hash13(id);
    if (rnd < threshold) {
        return 0.0;
    }
    float dist = length(f);
    float size = radius * (0.35 + 0.85 * hash13(id + 4.2));
    float glow = smoothstep(size, 0.0, dist);
    float core = smoothstep(size * 0.28, 0.0, dist);
    return (glow * 0.55 + core * 1.15) * (0.35 + 0.65 * hash13(id + 9.1));
}

// Small shaded sphere on the sky. Returns coverage in .a.
vec4 planet(vec3 dir, vec3 center, float radius, vec3 lightDir, vec3 dayCol, vec3 nightCol, vec3 rimCol) {
    float c = dot(dir, center);
    if (c < 0.0) {
        return vec4(0.0);
    }
    vec3 off = dir - center * c;
    float d = length(off) / radius;
    if (d > 1.06) {
        return vec4(0.0);
    }
    vec3 up = abs(center.y) > 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    vec3 tx = normalize(cross(up, center));
    vec3 ty = cross(center, tx);
    vec2 uv = vec2(dot(off, tx), dot(off, ty)) / radius;
    float zz = sqrt(max(1.0 - dot(uv, uv), 0.0));
    vec3 n = normalize(tx * uv.x + ty * uv.y - center * zz);
    float ndl = clamp(dot(n, lightDir), 0.0, 1.0);
    float terminator = smoothstep(0.0, 0.35, ndl);
    float bands = 0.75 + 0.25 * fbm(n * 5.0 + 3.3);
    vec3 surf = mix(nightCol, dayCol * bands, terminator);
    float rim = pow(1.0 - zz, 3.0) * (0.35 + 0.65 * ndl);
    surf += rimCol * rim * 1.4;
    float cover = 1.0 - smoothstep(0.985, 1.06, d);
    float atmo = exp(-max(d - 1.0, 0.0) * 22.0) * (0.25 + 0.75 * ndl) * 0.55;
    return vec4(surf * cover + rimCol * atmo, max(cover, atmo));
}

vec3 skyColor(vec3 dir, out float alpha) {
    vec3 pole = normalize(vec3(0.22, 0.86, 0.31));
    float nWide = fbm(dir * 2.4);
    float nMid = fbm(dir * 6.2 + 5.8);
    float nFine = fbm(dir * 14.0 + 19.4);
    float lat = dot(dir, pole) + (nWide - 0.5) * 0.16;
    float band = exp(-pow(lat * 5.4, 2.0));
    float arms = 0.55 + 0.45 * sin(atan(dir.z, dir.x) * 2.0 + nWide * 4.0);
    float dust = smoothstep(0.32, 0.88, nMid);
    float wisps = smoothstep(0.48, 0.96, nFine);
    float lane = exp(-pow(lat * 14.0, 2.0)) * (0.25 + 0.55 * nMid);

    float nebula = band * arms * (0.18 + 0.72 * dust + 0.38 * wisps);
    nebula *= 1.0 - lane * 0.62;

    vec3 blue = vec3(0.28, 0.40, 0.95);
    vec3 rose = vec3(0.78, 0.32, 0.72);
    vec3 gold = vec3(0.95, 0.74, 0.48);
    vec3 mixC = mix(blue, rose, clamp(nWide * 0.85, 0.0, 1.0));
    mixC = mix(mixC, gold, smoothstep(0.62, 1.0, band * nMid));

    vec3 col = mixC * nebula * 0.85;

    // Scattered nebula clouds so the sky away from the band is not empty.
    float c1 = fbm(dir * 3.1 + 41.0);
    float c2 = fbm(dir * 2.3 + 83.0);
    float c3 = fbm(dir * 4.6 + 17.0);
    float teal = smoothstep(0.52, 0.82, c1) * (0.5 + 0.5 * smoothstep(0.4, 0.8, nFine));
    float magenta = smoothstep(0.55, 0.86, c2) * (0.5 + 0.5 * smoothstep(0.35, 0.8, c3));
    float haze = smoothstep(0.42, 0.7, c3) * 0.35;
    col += vec3(0.18, 0.62, 0.78) * teal * 0.55;
    col += vec3(0.82, 0.24, 0.66) * magenta * 0.5;
    col += vec3(0.30, 0.22, 0.55) * haze * 0.45;
    float clouds = clamp(teal * 0.9 + magenta * 0.85 + haze * 0.7, 0.0, 1.0);

    // Faint distant galaxy smudge.
    vec3 galDir = normalize(vec3(-0.62, 0.36, -0.70));
    vec3 galUp = normalize(vec3(0.3, 0.9, 0.2));
    vec3 gx = normalize(cross(galUp, galDir));
    vec3 gy = cross(galDir, gx);
    vec3 goff = dir - galDir * dot(dir, galDir);
    vec2 guv = vec2(dot(goff, gx), dot(goff, gy) * 2.6);
    float gd = length(guv) / 0.075;
    float galaxy = dot(dir, galDir) > 0.0 ? exp(-gd * gd * 1.6) * (0.6 + 0.4 * fbm(dir * 60.0)) : 0.0;
    galaxy += dot(dir, galDir) > 0.0 ? exp(-gd * gd * 12.0) * 0.8 : 0.0;
    col += vec3(0.95, 0.88, 0.80) * galaxy * 0.7;

    float field = starLayer(dir, 520.0, 0.962, 0.085);
    float mid = starLayer(dir, 210.0, 0.982, 0.11);
    float bright = starLayer(dir, 78.0, 0.994, 0.16);
    float giant = starLayer(dir, 34.0, 0.997, 0.20);
    vec3 cool = vec3(0.78, 0.86, 1.0);
    vec3 warm = vec3(1.0, 0.90, 0.72);
    vec3 starTint = mix(cool, warm, hash13(floor(dir * 210.0)));
    vec3 giantTint = mix(vec3(0.72, 0.82, 1.0), vec3(1.0, 0.78, 0.55), hash13(floor(dir * 34.0) + 2.7));
    col += field * cool * 0.55;
    col += mid * starTint * 0.95;
    col += bright * vec3(1.0, 0.97, 0.92) * 1.35;
    col += giant * giantTint * 1.6;

    alpha = clamp(nebula * 1.55 + clouds + galaxy * 0.9 + field * 0.45 + mid * 0.7 + bright + giant, 0.0, 1.0);

    // A shaded planet and its moon, lit from the black hole side.
    vec3 lightDir = normalize(vec3(0.18, 0.86, 0.48));
    vec4 p1 = planet(
        dir,
        normalize(vec3(-0.52, 0.34, 0.78)),
        0.055,
        lightDir,
        vec3(0.42, 0.50, 0.70),
        vec3(0.02, 0.02, 0.04),
        vec3(0.55, 0.72, 1.0)
    );
    vec4 p2 = planet(
        dir,
        normalize(vec3(-0.40, 0.30, 0.86)),
        0.016,
        lightDir,
        vec3(0.62, 0.58, 0.52),
        vec3(0.02, 0.02, 0.02),
        vec3(0.7, 0.65, 0.6)
    );
    col = mix(col, p1.rgb, p1.a);
    alpha = max(alpha, p1.a);
    col = mix(col, p2.rgb, p2.a);
    alpha = max(alpha, p2.a);
    return col;
}

// Schwarzschild units: M = 1, horizon r = 2, photon sphere r = 3.
const float BH_HORIZON = 2.0;
const float DISK_IN = 3.4;
const float DISK_OUT = 14.0;
const float CAM_DIST = 26.0;
const float CAM_HEIGHT = 5.0;
const float VIEW_SCALE = 0.9;
const float CONE_COS = 0.78;
const float BLOOM_UV = VIEW_SCALE / 1.35;

vec3 diskSample(vec3 p, vec3 rd, float spin, out float alphaOut) {
    float rho = length(p.xz);
    float t = clamp((rho - DISK_IN) / (DISK_OUT - DISK_IN), 0.0, 1.0);
    float az = atan(p.z, p.x);
    float kepler = spin * pow(DISK_IN / rho, 1.5) * 2.6;
    float n1 = fbm(vec3(az * 2.6 - kepler, log(rho) * 5.5, 3.1));
    float n2 = fbm(vec3(az * 9.0 - kepler * 1.4, log(rho) * 16.0, 7.7));
    float lanes = 0.55 + 0.45 * smoothstep(0.30, 0.75, n1);
    lanes *= 0.7 + 0.3 * smoothstep(0.35, 0.70, n2);

    float edgeIn = smoothstep(DISK_IN, DISK_IN + 0.9, rho);
    float edgeOut = 1.0 - smoothstep(DISK_OUT - 5.0, DISK_OUT, rho);
    float radial = pow(DISK_IN / rho, 1.9);
    float emit = 9.0 * radial * edgeIn * edgeOut * lanes;

    vec3 vel = normalize(vec3(-p.z, 0.0, p.x));
    float beta = min(sqrt(1.0 / max(rho, 2.5)) * 1.15, 0.78);
    float gamma = 1.0 / sqrt(max(1.0 - beta * beta, 0.05));
    float doppler = 1.0 / (gamma * (1.0 - beta * dot(vel, -rd)));
    float redshift = sqrt(max(1.0 - BH_HORIZON / length(p), 0.05));
    float g = doppler * redshift;
    float boost = pow(g, 3.0);

    float heat = clamp(emit * boost, 0.0, 10.0);
    vec3 ember = vec3(0.95, 0.26, 0.04);
    vec3 amber = vec3(1.0, 0.52, 0.16);
    vec3 white = vec3(1.0, 0.90, 0.78);
    vec3 tint = mix(ember, amber, clamp(heat * 0.45, 0.0, 1.0));
    tint = mix(tint, white, smoothstep(2.2, 6.0, heat));
    vec3 col = tint * heat;

    alphaOut = clamp(emit * 1.2, 0.0, 1.0);
    return col;
}

vec4 gargantua(vec2 uv, float spin, out vec3 escaped, out bool didEscape) {
    vec3 pos = vec3(0.0, CAM_HEIGHT, -CAM_DIST);
    vec3 rd = normalize(vec3(uv.x * VIEW_SCALE, uv.y * VIEW_SCALE, 1.0));
    float tilt = atan(CAM_HEIGHT, CAM_DIST);
    float ct = cos(tilt);
    float st = sin(tilt);
    rd = vec3(rd.x, rd.y * ct - rd.z * st, rd.y * st + rd.z * ct);
    vec3 rd0 = rd;

    vec3 accum = vec3(0.0);
    float trans = 1.0;
    float closest = 1.0e9;
    didEscape = false;
    escaped = rd0;

    vec3 h = cross(pos, rd);
    float h2 = dot(h, h);

    for (int i = 0; i < 110; i++) {
        float r = length(pos);
        closest = min(closest, r);
        if (r < BH_HORIZON) {
            return vec4(accum, 1.0);
        }
        if (r > CAM_DIST + 6.0 && dot(pos, rd) > 0.0) {
            didEscape = true;
            escaped = rd;
            break;
        }
        float dt = clamp((r - 1.6) * 0.09, 0.045, 1.4);
        vec3 next = pos + rd * dt;
        if (pos.y * next.y < 0.0) {
            float f = pos.y / (pos.y - next.y);
            vec3 hit = mix(pos, next, f);
            float rho = length(hit.xz);
            if (rho > DISK_IN && rho < DISK_OUT) {
                float a;
                vec3 c = diskSample(hit, rd, spin, a);
                accum += c * trans * a;
                trans *= 1.0 - a;
                if (trans < 0.03) {
                    return vec4(accum, 1.0 - trans);
                }
            }
        }
        pos = next;
        rd = normalize(rd - 1.5 * h2 * pos / pow(r, 5.0) * dt);
    }

    float ring = exp(-pow((closest - 3.0) * 6.0, 2.0));
    accum += vec3(1.0, 0.72, 0.42) * ring * 0.9 * trans;
    float a = clamp(1.0 - trans + ring * 0.8, 0.0, 1.0);
    return vec4(accum, a);
}

vec3 tonemap(vec3 c) {
    return 1.0 - exp(-c * 0.9);
}

// Movie-style bloom: a soft ring hugging the shadow, a broad warm halo, and a
// smear along the disk plane, all heavier on the approaching (left) side.
float bloom(vec2 uvIn, bool shadow) {
    if (shadow) {
        return 0.0;
    }
    vec2 uv = uvIn * BLOOM_UV;
    float d = length(uv);
    float side = 0.55 + 0.75 * smoothstep(0.18, -0.22, uv.x);
    float ring = exp(-pow(d - 0.13, 2.0) / (2.0 * 0.04 * 0.04)) * 0.55;
    float wide = exp(-max(d - 0.12, 0.0) / 0.14) * 0.32;
    float plane = exp(-(uv.y * uv.y) / (2.0 * 0.035 * 0.035)) * exp(-abs(uv.x) / 0.28) * 0.45;
    return (ring + wide + plane) * side;
}

void main() {
    vec3 dir = normalize(worldDir);
    vec3 hole = normalize(ModelOffset);
    if (length(ModelOffset) < 0.2) {
        hole = normalize(vec3(0.18, 0.86, 0.48));
    }
    vec3 holeUp = vec3(0.0, 1.0, 0.0);
    if (abs(dot(holeUp, hole)) > 0.94) {
        holeUp = vec3(0.0, 0.0, 1.0);
    }
    holeUp = normalize(holeUp - hole * dot(holeUp, hole));
    vec3 holeX = normalize(cross(holeUp, hole));
    vec3 holeY = cross(hole, holeX);
    float spin = ColorModulator.w;

    float toward = clamp(dot(dir, hole), -1.0, 1.0);

    if (toward <= CONE_COS) {
        float skyA = 0.0;
        vec3 col = skyColor(dir, skyA);
        fragColor = vec4(col * ColorModulator.rgb, skyA);
        return;
    }

    vec2 uv = vec2(dot(dir, holeX), dot(dir, holeY)) / toward;
    vec3 escapedRd;
    bool escaped;
    vec4 bh = gargantua(uv, spin, escapedRd, escaped);

    float coneT = clamp((toward - CONE_COS) / (1.0 - CONE_COS), 0.0, 1.0);
    float lensFade = smoothstep(0.0, 0.7, coneT);

    vec3 bg = vec3(0.0);
    float bgA = 0.0;
    if (escaped) {
        // Undo the camera tilt so an unbent ray maps back onto its own sky direction.
        float tilt = -atan(CAM_HEIGHT, CAM_DIST);
        float ct = cos(tilt);
        float st = sin(tilt);
        vec3 rd = vec3(escapedRd.x, escapedRd.y * ct - escapedRd.z * st, escapedRd.y * st + escapedRd.z * ct);
        vec2 ouv = rd.xy / max(rd.z, 0.15) / VIEW_SCALE;
        vec3 lensed = normalize(hole + holeX * ouv.x + holeY * ouv.y);
        vec3 bgDir = normalize(mix(dir, lensed, lensFade));
        bg = skyColor(bgDir, bgA);
    }

    // Fade the End texture out around the hole so it reads as deep space.
    float darkness = smoothstep(0.35, 0.95, coneT) * 0.3;

    bool shadow = !escaped && max(bh.r, max(bh.g, bh.b)) < 0.05;
    float glow = bloom(uv, shadow) * lensFade;
    vec3 glowCol = vec3(1.0, 0.68, 0.36) * glow;

    vec3 col = mix(bg, tonemap(bh.rgb) + bg * (1.0 - bh.a), bh.a);
    col = mix(bg, col, lensFade);
    col += glowCol;
    float alpha = max(bgA, darkness);
    alpha = max(alpha, bh.a * lensFade);
    alpha = max(alpha, clamp(glow * 1.3, 0.0, 1.0));
    if (!escaped) {
        alpha = max(alpha, lensFade);
    }

    fragColor = vec4(col * ColorModulator.rgb, clamp(alpha, 0.0, 1.0));
}
