#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

// Album art drawn small: a 4x4 box filter over this pixel's footprint in the
// texture (there are no mipmaps), masked to a rounded square.
// vertexColor.r = corner radius as a fraction of the side (0.5 = circle).
void main() {
    float radius = clamp(vertexColor.r, 0.0, 0.5);
    vec2 p = texCoord0 - vec2(0.5);
    vec2 q = abs(p) - vec2(0.5 - radius);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float fw = max(fwidth(d), 1e-5);
    float mask = clamp(-d / fw + 0.5, 0.0, 1.0);
    if (mask < 0.004) {
        discard;
    }
    vec2 dx = dFdx(texCoord0);
    vec2 dy = dFdy(texCoord0);
    vec4 sum = vec4(0.0);
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            vec2 o = (vec2(float(i), float(j)) + 0.5) / 4.0 - 0.5;
            sum += texture(Sampler0, texCoord0 + dx * o.x + dy * o.y);
        }
    }
    vec4 color = sum / 16.0;
    fragColor = vec4(color.rgb, color.a * mask * vertexColor.a) * ColorModulator;
}
