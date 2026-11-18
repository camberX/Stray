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
// circle center at (0,0). Rings (1px outlines) are flagged by u shifted up by 4;
// a sign test missed rows where the coordinates reach 0, filling a stray line.
void main() {
    // u shifted by 4 * n: a ring n screen pixels wide (n = 1 the hairline below).
    float band = texCoord0.x > 2.5 ? floor((texCoord0.x + 2.0) / 4.0) : 0.0;
    bool ring = band > 0.5;
    vec2 p = ring ? vec2(texCoord0.x - band * 4.0, texCoord0.y) : texCoord0;
    float d = length(abs(p));
    float fw = max(length(vec2(dFdx(d), dFdy(d))), 1e-5);
    float mask;
    if (band > 1.5) {
        // A band of whole pixels, soft on both edges.
        mask = clamp((1.0 - d) / fw + 0.5, 0.0, 1.0) - clamp((1.0 - band * fw - d) / fw + 0.5, 0.0, 1.0);
    } else if (ring) {
        // Measured in whole pixel steps (the larger of the x and y change), a 1px ring lights one
        // pixel per row or column on the curve, like the straight sides, instead of a two-pixel
        // smear where it runs diagonally.
        vec2 g = abs(vec2(dFdx(d), dFdy(d)));
        float px = max(max(g.x, g.y), 1e-5);
        // A touch thinner where it runs diagonally, which still reads heavier than the sides.
        float diag = min(g.x, g.y) / px;
        float w = px * (1.0 - 0.6 * diag);
        mask = clamp((1.0 - d) / px + 0.5, 0.0, 1.0) - clamp((1.0 - w - d) / px + 0.5, 0.0, 1.0);
    } else {
        mask = clamp((1.0 - d) / fw + 0.5, 0.0, 1.0);
    }
    if (mask < 0.004) {
        discard;
    }
    vec4 white = texture(Sampler0, vec2(0.5));
    fragColor = vec4(vertexColor.rgb, vertexColor.a * mask) * white * ColorModulator;
}
