#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec4 vertexColor;
in vec3 viewDir;
in vec2 texCoord0;

out vec4 fragColor;

// Space Roof footstep ripple: a soft ring of light with a faint inner wash, spreading from a
// step. UV0 = position on the quad (-1..1), vertex colour = sky colour and strength. Additive.

void main() {
    float r = length(texCoord0);
    if (r >= 1.0) {
        discard;
    }
    float ring = exp(-pow((r - 0.86) / 0.06, 2.0));
    float wash = (1.0 - smoothstep(0.0, 0.86, r)) * 0.12;
    float a = (ring + wash) * vertexColor.a;
    vec3 color = mix(vertexColor.rgb, vec3(1.0), ring * 0.35);
    fragColor = vec4(color * a, a);
}
