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

void main() {
    float t = ModelOffset.x;
    vec2 uv = texCoord0 * 0.90 + 0.05;
    uv += vec2(sin(t * 0.11), cos(t * 0.09)) * 0.018;
    vec2 flow = vec2(
        sin(uv.y * 5.4 + t * 0.62) * 0.012 + sin(uv.x * 2.8 + t * 0.41) * 0.007,
        cos(uv.x * 4.7 + t * 0.53) * 0.011 + cos(uv.y * 3.1 + t * 0.37) * 0.006
    );
    uv += flow;
    uv += vec2(
        sin((uv.x + uv.y) * 3.4 + t * 0.29) * 0.004,
        cos((uv.x - uv.y) * 2.9 + t * 0.33) * 0.004
    );
    vec4 tex = texture(Sampler0, clamp(uv, 0.002, 0.998));
    vec4 color = tex * vertexColor * ColorModulator;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color;
}
