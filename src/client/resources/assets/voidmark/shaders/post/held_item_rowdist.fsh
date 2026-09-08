#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

// First half of the exact separable disc dilation used by held_item_silhouette:
// per pixel, the horizontal distance to the nearest covered mask texel on the
// same row (capped at the outline reach). Encoded as distance / 16 in red.

bool covered(vec4 sampleColor) {
    return sampleColor.a > 0.04 || max(sampleColor.r, max(sampleColor.g, sampleColor.b)) > 0.04;
}

bool coveredAt(ivec2 size, int x, int y) {
    return covered(texelFetch(InSampler, ivec2(clamp(x, 0, size.x - 1), y), 0));
}

void main() {
    float radius = mix(1.25, 12.0, clamp((ModelOffset.x - 0.15) / 1.35, 0.0, 1.0));
    int reach = min(12, int(ceil(radius + 0.35)));
    ivec2 size = textureSize(InSampler, 0);
    ivec2 p = ivec2(gl_FragCoord.xy);
    int best = 16;
    if (coveredAt(size, p.x, p.y)) {
        best = 0;
    } else {
        for (int d = 1; d <= 12; d++) {
            if (d > reach) {
                break;
            }
            if (coveredAt(size, p.x - d, p.y) || coveredAt(size, p.x + d, p.y)) {
                best = d;
                break;
            }
        }
    }
    fragColor = vec4(float(best) / 16.0, 0.0, 0.0, 1.0);
}
