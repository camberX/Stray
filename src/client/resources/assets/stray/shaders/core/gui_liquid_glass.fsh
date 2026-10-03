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

// Rounded-rect glass over the frost texture.
// vertexColor.rg is the corner radius as a fraction of the quad, same as the plain blit.
// vertexColor.b packs the cursor: high nibble is x, low nibble is y, each 0..1 across the quad.
// The rim refracts along the rounded edge, splits the channels, and brightens toward the cursor.

vec3 saturate(vec3 color, float amount) {
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(luma), color, amount);
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

    vec2 grad = vec2(dFdx(dist), dFdy(dist));
    float gradLen = max(length(grad), 0.0001);
    vec2 outward = grad / gradLen;
    float radiusPx = 1.0 / gradLen;
    float depth = max(0.0, -dist) * radiusPx;
    float bezel = clamp(radiusPx * 0.46, 7.0, 26.0);
    float t = clamp(depth / bezel, 0.0, 1.0);
    float bulge = sin(t * 3.14159265) * (1.0 - smoothstep(0.62, 1.0, t));
    float lip = smoothstep(1.0, 0.12, t);

    float packed = floor(vertexColor.b * 255.0 + 0.5);
    vec2 mouse = vec2(floor(packed / 16.0), mod(packed, 16.0)) / 15.0;
    vec2 toMouse = mouse - texCoord0;
    vec2 uvPerPx = max(vec2(length(dFdx(texCoord0)), length(dFdy(texCoord0))), vec2(0.00001));
    vec2 mousePx = toMouse / uvPerPx;
    float mouseLen = length(mousePx);
    vec2 mouseDir = mouseLen > 0.5 ? mousePx / mouseLen : vec2(0.0);
    float nearCursor = exp(-dot(toMouse, toMouse) * 7.0);

    float bend = bulge * clamp(radiusPx * 0.42, 8.0, 22.0) + lip * 2.5;
    vec2 offset = -outward * bend + mouseDir * nearCursor * min(bezel, 12.0) * 0.55;
    vec2 split = outward * (2.6 * bulge + 0.8 * lip);

    ivec2 texSize = textureSize(Sampler0, 0) - ivec2(1);
    ivec2 base = ivec2(gl_FragCoord.xy);
    ivec2 red = clamp(base + ivec2(int(round(offset.x + split.x)), int(round(offset.y + split.y))), ivec2(0), texSize);
    ivec2 green = clamp(base + ivec2(int(round(offset.x)), int(round(offset.y))), ivec2(0), texSize);
    ivec2 blue = clamp(base + ivec2(int(round(offset.x - split.x)), int(round(offset.y - split.y))), ivec2(0), texSize);

    vec3 color = vec3(
        texelFetch(Sampler0, red, 0).r,
        texelFetch(Sampler0, green, 0).g,
        texelFetch(Sampler0, blue, 0).b
    );
    color = saturate(color, 1.4);

    vec2 axis = sign(p);
    vec2 normal;
    if (q.x > 0.0 && q.y > 0.0) {
        normal = normalize(max(q, vec2(0.0001))) * axis;
    } else if (q.x > q.y) {
        normal = vec2(axis.x, 0.0);
    } else {
        normal = vec2(0.0, axis.y);
    }
    float top = clamp(-normal.y, 0.0, 1.0);
    float bottom = clamp(normal.y, 0.0, 1.0);
    float spec = pow(top, 1.35) * (0.22 + 0.38 * lip) * (0.75 + 0.45 * nearCursor);
    color += spec;
    color *= 1.0 - bottom * lip * 0.16;
    color += smoothstep(0.42, 0.0, texCoord0.y) * lip * 0.06;

    fragColor = vec4(color, mask) * ColorModulator;
}
