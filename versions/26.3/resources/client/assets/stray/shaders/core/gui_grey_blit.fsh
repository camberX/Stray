#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

// A texture with its colour taken out: vertexColor.r is how much colour stays (0 grey,
// 1 as it is), vertexColor.a the opacity. Grey is also a little darker, so it reads as "not yet".
void main() {
    vec4 c = texture(Sampler0, texCoord0);
    if (c.a < 0.1) {
        discard;
    }
    float keep = clamp(vertexColor.r, 0.0, 1.0);
    float l = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 rgb = mix(vec3(l), c.rgb, keep) * mix(0.62, 1.0, keep);
    fragColor = vec4(rgb, c.a * vertexColor.a) * ColorModulator;
}
