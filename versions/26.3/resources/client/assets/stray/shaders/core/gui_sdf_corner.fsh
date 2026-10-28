#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

// One rounded-rect corner, drawn analytically so it stays smooth at any
// size and GUI scale. texCoord0 is the position in radius units with the
// circle center at (0,0). Rings (1px outlines) are flagged by u shifted up by 4;
// a sign test missed rows where the coordinates reach 0, filling a stray line.
void main() {
    bool ring = texCoord0.x > 2.5;
    vec2 p = ring ? vec2(texCoord0.x - 4.0, texCoord0.y) : texCoord0;
    float d = length(abs(p));
    float fw = max(fwidth(d), 1e-5);
    float mask = clamp((1.0 - d) / fw + 0.5, 0.0, 1.0);
    if (ring) {
        mask -= clamp((1.0 - fw - d) / fw + 0.5, 0.0, 1.0);
    }
    if (mask < 0.004) {
        discard;
    }
    vec4 white = texture(Sampler0, vec2(0.5));
    fragColor = vec4(vertexColor.rgb, vertexColor.a * mask) * white * ColorModulator;
}
