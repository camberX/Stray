#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 viewDir;
layout(location = 2) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

// Space Roof stardust: a soft glowing speck with a faint four-point sparkle.
// UV0 = position on the quad (-1..1), vertex colour = colour and brightness. Additive.

void main() {
    vec2 p = texCoord0;
    float r2 = dot(p, p);
    if (r2 >= 1.0) {
        discard;
    }
    float core = exp(-r2 * 30.0);
    float glow = exp(-r2 * 6.0) * 0.35;
    float edge = 1.0 - sqrt(r2);
    float rays = (max(0.0, 1.0 - abs(p.x) * 12.0) + max(0.0, 1.0 - abs(p.y) * 12.0)) * edge * edge * 0.5;
    float a = (core + glow + rays) * vertexColor.a;
    vec3 color = mix(vertexColor.rgb, vec3(1.0), clamp(core * 0.8, 0.0, 1.0));
    fragColor = vec4(color * a, a);
}
