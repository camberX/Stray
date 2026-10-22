#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// One rounded-rect corner, drawn analytically so it stays smooth at any
// size and GUI scale. texCoord0 is the position in radius units with the
// circle center at (0,0); a negative range means "ring" (a 1px outline).
void main() {
    bool ring = (texCoord0.x + texCoord0.y) < 0.0;
    float d = length(abs(texCoord0));
    float fw = max(fwidth(d), 1e-5);
    float mask = clamp((1.0 - d) / fw + 0.5, 0.0, 1.0);
    if (ring) {
        mask -= clamp((1.0 - fw - d) / fw + 0.5, 0.0, 1.0);
    }
    if (mask < 0.004) {
        discard;
    }
    vec4 white = texture(Sampler0, vec2(0.5));
    fragColor = vec4(vertexColor.rgb, vertexColor.a * mask) * white * ColorModulator;
}
