#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in float rimWeight;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (tex.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    fragColor = vec4(0.95, 0.98, 1.0, rimWeight);
}
