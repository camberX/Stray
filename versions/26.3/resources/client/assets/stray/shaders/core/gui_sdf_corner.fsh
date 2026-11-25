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
    // u shifted by 4 * n: a ring n screen pixels wide (n = 1 the hairline below).
    float band = texCoord0.x > 2.5 ? floor((texCoord0.x + 2.0) / 4.0) : 0.0;
    bool ring = band > 0.5;
    vec2 p = ring ? vec2(texCoord0.x - band * 4.0, texCoord0.y) : texCoord0;
    float d = length(abs(p));
    float fw = max(length(vec2(dFdx(d), dFdy(d))), 1e-5);
    float mask;
    if (band > 1.5) {
        // A band of whole pixels, soft on both edges.
        mask = clamp((1.0 - d) / fw + 0.5, 0.0, 1.0) - clamp((1.0 - band * fw - d) / fw + 0.5, 0.0, 1.0);
    } else if (ring) {
        // Exactly one screen pixel wide measured across the line, everywhere: fw is how far d
        // moves per pixel along the curve's normal, so the band is one pixel thick on the
        // straight sides and on the curve alike, with the same total ink per pixel of length.
        // (The old version measured along x or y and thinned the diagonals on purpose, so the
        // corners came out lighter than the sides they join.)
        mask = clamp((1.0 - d) / fw + 0.5, 0.0, 1.0) - clamp((1.0 - fw - d) / fw + 0.5, 0.0, 1.0);
        // A curve spreads its pixel over two half-covered ones; blended in sRGB those read
        // lighter than one full pixel. Lift partial coverage so the curve weighs the same.
        mask = pow(mask, 0.8);
    } else {
        mask = clamp((1.0 - d) / fw + 0.5, 0.0, 1.0);
    }
    if (mask < 0.004) {
        discard;
    }
    vec4 white = texture(Sampler0, vec2(0.5));
    fragColor = vec4(vertexColor.rgb, vertexColor.a * mask) * white * ColorModulator;
}
