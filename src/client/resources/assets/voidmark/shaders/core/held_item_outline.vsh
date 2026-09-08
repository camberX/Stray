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
    vec4 clipN = ProjMat * ModelViewMat * vec4(Position + Normal, 1.0);
    vec2 a = clip.xy / max(abs(clip.w), 1.0e-5);
    vec2 b = clipN.xy / max(abs(clipN.w), 1.0e-5);
    vec2 dir = b - a;
    float len = length(dir);
    vec2 nScreen = len > 1.0e-6 ? dir / len : vec2(0.0);
    float pixels = mix(2.0, 12.0, clamp((ModelOffset.x - 0.15) / 1.35, 0.0, 1.0));
    vec2 ndcPixel = vec2(2.0 / max(ScreenSize.x, 1.0), 2.0 / max(ScreenSize.y, 1.0));
    clip.xy += nScreen * pixels * ndcPixel * clip.w;
    gl_Position = clip;
}
