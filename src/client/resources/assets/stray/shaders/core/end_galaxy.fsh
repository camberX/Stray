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

vec4 gargantua(vec2 uv, float spin) {
    vec3 pos = vec3(0.0, 0.38, -4.7);
    vec3 rd = normalize(vec3(uv.x, uv.y, 1.18));
    float rs = 0.5;
    float closest = 100.0;

    for (int i = 0; i < 40; i++) {
        float r = length(pos);
        closest = min(closest, r);
        if (r < rs) {
            return vec4(0.0, 0.0, 0.0, 1.0);
        }
        if (r > 13.5) {
            break;
        }

        float rho = length(pos.xz);
        float thick = 0.011 * max(rho, 0.8);
        if (abs(pos.y) < thick && rho > rs * 1.82 && rho < rs * 10.8) {
            float t = clamp((rho - rs * 1.82) / (rs * 8.8), 0.0, 1.0);
            float ang = atan(pos.z, pos.x);
            float kepler = spin * pow(2.3 / max(rho, 0.25), 1.5);
            float n1 = fbm(vec3(ang * 2.1 - kepler, rho * 0.78, 2.4));
            float n2 = fbm(vec3(ang * 5.4 - kepler * 1.7, rho * 2.3, 8.1));
            float lanes = smoothstep(0.25, 0.82, n1) * (0.5 + 0.5 * n2);
            float inner = exp(-t * 4.4);
            float fall = pow(max(1.0 - t, 0.0), 2.15) * inner * 3.6 + 0.07;
            vec3 vel = normalize(vec3(-pos.z, 0.0, pos.x));
            float dopp = pow(clamp(1.0 + 1.05 * dot(vel, -rd), 0.18, 2.6), 2.7);
            vec3 cold = vec3(0.28, 0.12, 0.05);
            vec3 ember = vec3(0.95, 0.48, 0.16);
            vec3 white = vec3(1.0, 0.97, 0.90);
            vec3 heat = mix(cold, ember, clamp(fall * 0.42, 0.0, 1.0));
            heat = mix(heat, white, pow(clamp(fall * dopp * 0.2, 0.0, 1.0), 0.62));
            vec3 col = heat * fall * dopp * (0.5 + 0.75 * lanes);
            float alpha = clamp(0.28 + fall * 0.95, 0.0, 1.0);
            return vec4(col, alpha);
        }

        float dt = clamp(r * 0.06, 0.028, 0.26);
        pos += rd * dt;
        rd = normalize(rd - 1.5 * rs * pos * dt / (r * r * r));
    }

    float photon = exp(-pow((closest - rs * 1.5) * 16.0, 2.0));
    if (photon > 0.03) {
        return vec4(vec3(1.0, 0.93, 0.78) * photon * 2.0, clamp(photon * 1.2, 0.0, 1.0));
    }
    return vec4(0.0);
}

void main() {
    vec3 dir = normalize(worldDir);
    vec3 hole = normalize(vec3(0.68, 0.12, 0.72));
    vec3 holeUp = normalize(vec3(0.08, 0.99, 0.06));
    vec3 holeX = normalize(cross(holeUp, hole));
    vec3 holeY = cross(hole, holeX);
    float spin = ModelOffset.x;

    float toward = clamp(dot(dir, hole), -1.0, 1.0);
    vec3 radial = dir - hole * toward;
    float radialLen = length(radial);
    vec3 away = radialLen > 1.0e-5 ? radial / radialLen : holeX;
    float ang = acos(toward);
    float bend = 0.02 / max(pow(max(ang, 0.001), 1.35), 0.0005);
    vec3 view = normalize(dir + away * bend * smoothstep(0.5, 0.08, ang));

    float skyA = 0.0;
    vec3 col = skyColor(view, skyA);
    float alpha = skyA;

    if (toward > 0.28) {
        vec2 uv = vec2(dot(dir, holeX), dot(dir, holeY)) * (0.52 / max(toward, 0.32));
        if (length(uv) < 1.85) {
            vec4 bh = gargantua(uv, spin);
            col = mix(col, bh.rgb, bh.a);
            alpha = max(alpha * (1.0 - bh.a), bh.a);
        }
    }

    fragColor = vec4(col * ColorModulator.rgb, alpha);
}
