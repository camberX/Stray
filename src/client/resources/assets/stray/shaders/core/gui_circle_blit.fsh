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

// vertexColor.rg = UV origin, .ba = UV size of the texture region mapped over
// the unit quad; the circle mask lives in quad space.
void main() {
    vec2 origin = vertexColor.rg;
    vec2 span = vertexColor.ba;
    vec2 local = (texCoord0 - origin) / max(span, vec2(0.00001));
    float d = length(local - vec2(0.5)) - 0.5;
    float fw = fwidth(d);
    float mask = 1.0 - smoothstep(-fw, fw, d);
    if (mask < 0.01) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.02) {
        discard;
    }
    fragColor = vec4(color.rgb, color.a * mask) * ColorModulator;
}
