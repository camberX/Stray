#version 330

// ModelViewMat carries the full projection * view for one cubemap face, so the
// bake does not depend on the world Projection uniform.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec3 Position;

out vec3 worldDir;

void main() {
    worldDir = Position;
    gl_Position = ModelViewMat * vec4(Position, 1.0);
}
