#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec2 local;
layout(location = 1) flat in vec2 extent;
layout(location = 2) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

// Rounded rectangle from RoundRect: the color is the interpolated corner colors (the
// gradient), the shape a signed distance. extent.y is 1 + radius (fill) or 3 + radius
// (1px outline), in half-height units; dividing by it gives the position in those units.
void main() {
    bool ring = extent.y > 2.5;
    float k = extent.y;
    float r = k - (ring ? 3.0 : 1.0);
    vec2 p = local / k;
    vec2 hs = extent / k;
    vec2 q = abs(p) - (hs - vec2(r));
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float fw = max(length(vec2(dFdx(d), dFdy(d))), 1e-5);
    float mask;
    if (ring) {
        // Whole pixel steps, so a 1px outline lights one pixel per row or column on the curve too.
        vec2 g = abs(vec2(dFdx(d), dFdy(d)));
        float px = max(max(g.x, g.y), 1e-5);
        float w = px * (1.0 - 0.6 * min(g.x, g.y) / px);
        mask = clamp(0.5 - d / px, 0.0, 1.0) - clamp(0.5 - (d + w) / px, 0.0, 1.0);
    } else {
        mask = clamp(0.5 - d / fw, 0.0, 1.0);
    }
    if (mask < 0.004) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * mask) * ColorModulator;
}
