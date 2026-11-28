#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec3 viewDir;
layout(location = 2) out vec2 texCoord0;

void main() {
    // ModelOffset: cached meshes are stored round their own origin and moved to the camera here
    // (zero for geometry built each frame, which is already camera-relative).
    vec3 pos = Position + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
    // Camera-relative and world-aligned (like vanilla's fog distance): the sky is looked up by
    // direction only, so it seems infinitely far away instead of painted on the ceiling.
    viewDir = pos;
}
