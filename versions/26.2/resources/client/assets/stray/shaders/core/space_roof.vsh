#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec4 vertexColor;
out vec3 viewDir;
out vec2 texCoord0;

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
