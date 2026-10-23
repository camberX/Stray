#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

#ifdef ESP_FILL
uniform sampler2D Sampler2;
#endif

#ifdef CS2_GPU_SKIN
// CS2 knives: the mesh sits on the GPU in its bind pose; the bone index is in Color.r and
// each bone's matrix (pose, view and skinning combined) comes in per draw.
layout(std140) uniform KnifeBones {
    mat4 Bones[16];
    ivec4 KnifeLight;
};
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 texProj0;

void main() {
#ifdef CS2_GPU_SKIN
    mat4 bone = Bones[clamp(int(Color.r * 255.0 + 0.5), 0, 15)];
    vec3 position = (bone * vec4(Position, 1.0)).xyz;
    vec3 normal = normalize(mat3(bone) * Normal);
    ivec2 lightUv = KnifeLight.xy;
    vec4 color = vec4(1.0);
#else
    vec3 position = Position;
    vec3 normal = Normal;
    ivec2 lightUv = UV2;
    vec4 color = Color;
#endif
    sphericalVertexDistance = fog_spherical_distance(position);
    cylindricalVertexDistance = fog_cylindrical_distance(position);
#ifdef ESP_FILL
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, color) * sample_lightmap(Sampler2, lightUv);
#else
    vertexColor = color;
#endif
    texCoord0 = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    texProj0 = projection_from_position(gl_Position);
}
