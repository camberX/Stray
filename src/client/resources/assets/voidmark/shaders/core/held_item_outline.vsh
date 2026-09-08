#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec2 texCoord0;

void main() {
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    texCoord0 = UV0;

    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec2 jump = round(Color.rg * 2.0 - 1.0);
    float pixels = mix(3.0, 16.0, clamp((ModelOffset.x - 0.15) / 1.35, 0.0, 1.0));
    vec2 ndcPixel = vec2(2.0 / max(ScreenSize.x, 1.0), 2.0 / max(ScreenSize.y, 1.0));
    clip.xy += jump * pixels * ndcPixel * clip.w;
    gl_Position = clip;
}
