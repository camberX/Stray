#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 local;
layout(location = 1) flat out vec2 extent;
layout(location = 2) out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    // Every vertex sits on a corner, so |UV| is the quad's half size (see RoundRect).
    extent = abs(UV0);
    vertexColor = Color;
}
