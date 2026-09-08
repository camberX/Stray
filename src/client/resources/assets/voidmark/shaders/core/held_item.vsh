#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

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
out vec3 viewNormal;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    viewNormal = normalize(mat3(ModelViewMat) * Normal);

    float rim = pow(clamp(1.0 - abs(viewNormal.z), 0.0, 1.0), 0.85);
    float thickness = 0.0075 * max(ModelOffset.x, 0.15);
    vec4 clip = ProjMat * viewPos;
    vec4 clipN = ProjMat * vec4(viewNormal, 0.0);
    vec2 n2 = clipN.xy;
    float nlen = length(n2);
    if (nlen > 1.0e-5) {
        n2 /= nlen;
        clip.xy += n2 * rim * thickness * abs(clip.w);
    }
    gl_Position = clip;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color) * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
