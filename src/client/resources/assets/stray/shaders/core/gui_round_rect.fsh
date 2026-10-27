#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 local;
flat in vec2 extent;
in vec4 vertexColor;

out vec4 fragColor;

// Rounded rectangle from RoundRect: the color is the interpolated corner colors (the
// gradient), the shape a signed distance. extent.y is 1 + radius (fill) or 3 + radius
// (1px outline), in half-height units; dividing by it gives the position in those units.
void main() {
    bool ring = extent.y > 2.5;
    float k = extent.y;
    float r = k - (ring ? 3.0 : 1.0);
    vec2 p = local / k;
    vec2 hs = extent / k;
    vec2 q = abs(p) - (hs - vec2(r));
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float fw = max(fwidth(d), 1e-5);
    float mask = clamp(0.5 - d / fw, 0.0, 1.0);
    if (ring) {
        mask -= clamp(0.5 - (d + fw) / fw, 0.0, 1.0);
    }
    if (mask < 0.004) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * mask) * ColorModulator;
}
