#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Same filter as https://github.com/rdev/liquid-glass-react (standard mode).
// The displacement image is a linear gradient (R = x, B = y). feDisplacementMap
// reads R for x and B for y. The filter region is inset -35% and 170% wide, so
// the pane only covers the middle of that gradient. Scales are -70 / -77 / -84
// for the red, green, and blue channels (displacementScale 70, aberration 2).
// vertexColor.rg = corner radius as a fraction of the quad.
// vertexColor.b = framebuffer pixels per local pixel, packed as s * 40.
// vertexColor.a = shine 0-3 in the top two bits, then cursor offset, 3 bits each,
// -100..100 like the library's mouseOffset. Shine 3 is the full specular.

vec3 saturate(vec3 color, float amount) {
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(luma), color, amount);
}

float gradAlpha(float t, float s1, float s2, float a1, float a2) {
    if (t < s1) {
        return mix(0.0, a1, t / max(s1, 0.0001));
    }
    if (t < s2) {
        return mix(a1, a2, (t - s1) / max(s2 - s1, 0.0001));
    }
    return mix(a2, 0.0, (t - s2) / max(1.0 - s2, 0.0001));
}

vec3 screenWhite(vec3 base, float amount) {
    return base + amount * (1.0 - base);
}

vec3 overlayWhite(vec3 base, float amount) {
    vec3 over = mix(clamp(base * 2.0, 0.0, 1.0), vec3(1.0), step(vec3(0.5), base));
    return mix(base, over, amount);
}

void main() {
    vec2 rad = max(vertexColor.rg, vec2(0.001));
    vec2 size = vec2(1.0) / rad;
    vec2 p = (texCoord0 - vec2(0.5)) * size;
    vec2 box = size * 0.5 - vec2(1.0);
    vec2 q = abs(p) - box;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 1.0;
    float fw = fwidth(dist);
    float mask = 1.0 - smoothstep(-fw, fw, dist);
    if (mask < 0.01) {
        discard;
    }

    float pxScale = max(vertexColor.b * (255.0 / 40.0), 0.25);
    float yUp = dFdy(texCoord0.y) < 0.0 ? 1.0 : -1.0;
    vec2 mapUv = (texCoord0 + vec2(0.35)) / 1.70;
    vec2 channel = vec2(1.0 - mapUv.x, 1.0 - mapUv.y) - vec2(0.5);
    vec2 svg = channel * -70.0;
    vec2 offR = vec2(svg.x, -svg.y * yUp) * pxScale;
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy * texel;
    vec3 color = vec3(
        texture(Sampler0, uv + offR * texel).r,
        texture(Sampler0, uv + offR * 1.1 * texel).g,
        texture(Sampler0, uv + offR * 1.2 * texel).b
    );
    color = saturate(color, 1.4);

    float packed = floor(vertexColor.a * 255.0 + 0.5);
    float shine = floor(packed / 64.0) / 3.0;
    float mousePack = mod(packed, 64.0);
    float mouseX = (floor(mousePack / 8.0) / 7.0) * 200.0 - 100.0;
    float mouseY = (mod(mousePack, 8.0) / 7.0) * 200.0 - 100.0;
    float ang = radians(135.0 + mouseX * 1.2);
    vec2 css = vec2(sin(ang), -cos(ang));
    float along = clamp(dot(texCoord0 - vec2(0.5), css) + 0.5, 0.0, 1.0);
    float s1 = clamp(0.33 + mouseY * 0.003, 0.10, 0.90);
    float s2 = clamp(0.66 + mouseY * 0.004, s1 + 0.05, 0.95);
    float rim = 1.0 - smoothstep(0.4, 1.5 * pxScale + 0.6, max(0.0, -dist) / max(length(vec2(dFdx(dist), dFdy(dist))), 0.0001));
    float aScreen = gradAlpha(along, s1, s2, 0.12 + abs(mouseX) * 0.008, 0.40 + abs(mouseX) * 0.012) * 0.2 * rim * shine;
    float aOverlay = gradAlpha(along, s1, s2, 0.32 + abs(mouseX) * 0.008, 0.60 + abs(mouseX) * 0.012) * rim * shine;
    color = screenWhite(color, aScreen);
    color = overlayWhite(color, aOverlay);

    float edge = max(0.0, -dist) / max(length(vec2(dFdx(dist), dFdy(dist))), 0.0001);
    vec2 axis = sign(p);
    vec2 normal = (q.x > 0.0 && q.y > 0.0)
        ? normalize(max(q, vec2(0.0001))) * axis
        : (q.x > q.y ? vec2(axis.x, 0.0) : vec2(0.0, axis.y));
    float top = clamp(-normal.y, 0.0, 1.0);
    float hair = (1.0 - smoothstep(0.0, 0.75 * pxScale, edge)) * 0.5;
    float inset = smoothstep(0.5 * pxScale, 0.0, abs(edge - 2.2 * pxScale)) * (0.35 + 0.65 * top);
    color = mix(color, color * 0.72, inset * 0.35);
    color += hair * (0.55 + 0.45 * top) * shine;

    fragColor = vec4(color, mask) * ColorModulator;
}
