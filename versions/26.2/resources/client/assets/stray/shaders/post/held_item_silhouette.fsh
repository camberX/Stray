#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;
uniform sampler2D RowSampler;

in vec2 texCoord;

out vec4 fragColor;

// Second half of the exact separable disc dilation. RowSampler holds the
// per-row horizontal distance to the nearest covered texel (see
// held_item_rowdist), so a single vertical scan reproduces the full
// (2r+1)^2 disc test with 2r+1 reads. The mask is one flat color, so the
// outline color is simply ColorModulator.

bool covered(vec4 sampleColor) {
    return sampleColor.a > 0.04 || max(sampleColor.r, max(sampleColor.g, sampleColor.b)) > 0.04;
}

void main() {
    ivec2 size = textureSize(InSampler, 0);
    ivec2 p = ivec2(gl_FragCoord.xy);
    if (covered(texelFetch(InSampler, p, 0))) {
        discard;
    }
    float radius = mix(1.25, 12.0, clamp((ModelOffset.x - 0.15) / 1.35, 0.0, 1.0));
    int reach = min(12, int(ceil(radius + 0.35)));
    float limit = radius + 0.35;
    float limitSq = limit * limit;
    bool cover = false;
    for (int y = -12; y <= 12; y++) {
        if (y < -reach) {
            continue;
        }
        if (y > reach) {
            break;
        }
        int qy = clamp(p.y + y, 0, size.y - 1);
        float dx = floor(texelFetch(RowSampler, ivec2(p.x, qy), 0).r * 16.0 + 0.5);
        if (dx > 12.5) {
            continue;
        }
        if (dx * dx + float(y * y) <= limitSq) {
            cover = true;
            break;
        }
    }
    if (!cover) {
        discard;
    }
    fragColor = vec4(ColorModulator.rgb, 1.0);
}
