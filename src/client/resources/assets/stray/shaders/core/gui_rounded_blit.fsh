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
    vec2 rad = max(vertexColor.rg, vec2(0.001));
    vec2 size = vec2(1.0) / rad;
    vec2 p = (texCoord0 - vec2(0.5)) * size;
    vec2 b = size * 0.5 - vec2(1.0);
    vec2 q = abs(p) - b;
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 1.0;
    float fw = fwidth(d);
    float mask = 1.0 - smoothstep(-fw, fw, d);
    if (mask < 0.01) {
        discard;
    }
    vec4 color = texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0);
    fragColor = vec4(color.rgb, color.a * mask * vertexColor.a) * ColorModulator;
}
