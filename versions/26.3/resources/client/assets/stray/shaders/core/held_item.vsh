#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

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

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out vec4 texProj0;

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
