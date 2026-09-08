#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;

    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec4 clipC = ProjMat * ModelViewMat * vec4(8.0, 8.0, 8.0, 1.0);
    vec2 ndc = clip.xy / clip.w;
    vec2 ndcC = clipC.xy / clipC.w;
    vec2 dir = ndc - ndcC;
    float len = length(dir);
    vec2 radial = len > 1.0e-5 ? dir / len : vec2(0.0);
    float pixels = mix(2.0, 10.0, clamp((ModelOffset.x - 0.15) / 1.35, 0.0, 1.0));
    vec2 ndcPixel = vec2(2.0 / max(ScreenSize.x, 1.0), 2.0 / max(ScreenSize.y, 1.0));
    ndc += radial * pixels * ndcPixel;
    clip.xy = ndc * clip.w;
    gl_Position = clip;
}
