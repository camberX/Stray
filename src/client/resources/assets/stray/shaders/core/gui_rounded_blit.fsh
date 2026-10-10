#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 rad = max(vertexColor.rg, vec2(0.001));
    vec2 size = vec2(1.0) / rad;
    vec2 p = (texCoord0 - vec2(0.5)) * size;
    vec2 b = size * 0.5 - vec2(1.0);
    vec2 q = abs(p) - b;
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 1.0;
    float fw = fwidth(d);
    float mask = 1.0 - smoothstep(-fw, fw, d);
    if (mask < 0.01) {
        discard;
    }
    vec4 color = texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0);
    fragColor = vec4(color.rgb, color.a * mask * vertexColor.a) * ColorModulator;
}
