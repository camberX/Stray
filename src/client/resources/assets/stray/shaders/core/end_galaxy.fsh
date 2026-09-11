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

    float field = starLayer(dir, 520.0, 0.976, 0.085);
    float mid = starLayer(dir, 210.0, 0.988, 0.11);
    float bright = starLayer(dir, 78.0, 0.995, 0.16);
    vec3 cool = vec3(0.78, 0.86, 1.0);
    vec3 warm = vec3(1.0, 0.90, 0.72);
    vec3 starTint = mix(cool, warm, hash13(floor(dir * 210.0)));
    col += field * cool * 0.55;
    col += mid * starTint * 0.95;
    col += bright * vec3(1.0, 0.97, 0.92) * 1.35;

    alpha = clamp(nebula * 1.55 + field * 0.45 + mid * 0.7 + bright, 0.0, 1.0);
    return col;
}

void main() {
    vec3 dir = normalize(worldDir);
    vec3 hole = normalize(vec3(0.74, 0.16, 0.65));
    vec3 holeUp = normalize(vec3(0.26, 0.94, -0.22));
    vec3 holeX = normalize(cross(holeUp, hole));
    vec3 holeY = cross(hole, holeX);
    float spin = ModelOffset.x;

    float toward = clamp(dot(dir, hole), -1.0, 1.0);
    float ang = acos(toward);
    vec3 radial = dir - hole * toward;
    float radialLen = length(radial);
    vec3 away = radialLen > 1.0e-5 ? radial / radialLen : holeX;
    float bend = 0.028 / max(pow(max(ang, 0.001), 1.45), 0.0005);
    vec3 view = normalize(dir + away * bend * smoothstep(0.46, 0.05, ang));

    float skyA = 0.0;
    vec3 col = skyColor(view, skyA);

    float horizon = 0.062;
    float holeMask = 1.0 - smoothstep(horizon * 0.96, horizon * 1.14, ang);
    float ring = exp(-pow((ang - horizon * 1.20) * 92.0, 2.0));
    float halo = exp(-pow((ang - horizon * 1.55) * 18.0, 2.0)) * 0.22;

    float height = dot(dir, holeUp);
    float diskThin = exp(-pow(height * 20.0, 2.0));
    float diskInner = horizon * 1.28;
    float diskOuter = 0.26;
    float diskRad = smoothstep(diskInner, diskInner + 0.018, ang) * (1.0 - smoothstep(diskOuter - 0.05, diskOuter, ang));
    float az = atan(dot(dir, holeX), dot(dir, holeY));
    float swirl = 0.42 + 0.58 * (0.5 + 0.5 * sin(az * 6.0 - spin * 7.5 + fbm(dir * 9.0) * 5.0));
    float heat = exp(-pow((ang - diskInner * 1.35) * 9.0, 2.0));
    float disk = diskThin * diskRad * swirl;
    vec3 diskCol = mix(vec3(1.0, 0.28, 0.06), vec3(1.0, 0.84, 0.42), heat);
    float approaching = 0.55 + 0.45 * clamp(dot(away, holeX), -1.0, 1.0);
    diskCol *= approaching;

    col += disk * diskCol * 1.15;
    col += ring * vec3(1.0, 0.78, 0.42) * 1.7;
    col += halo * vec3(1.0, 0.55, 0.22);

    col *= 1.0 - holeMask;
    col = mix(col, vec3(0.0), holeMask);

    float alpha = max(skyA, max(disk * 1.1, max(ring, halo)));
    alpha = mix(alpha, 1.0, holeMask);

    fragColor = vec4(col * ColorModulator.rgb, alpha);
}
