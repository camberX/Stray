#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

vec4 maskAt(vec2 uv) {
    return texture(InSampler, uv);
}

bool covered(vec4 sampleColor) {
    return sampleColor.a > 0.04 || max(sampleColor.r, max(sampleColor.g, sampleColor.b)) > 0.04;
}

void main() {
    float radius = mix(1.25, 12.0, clamp((ModelOffset.x - 0.15) / 1.35, 0.0, 1.0));
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    vec4 insideSample = maskAt(texCoord);
    bool inside = covered(insideSample);
    vec3 outline = ColorModulator.rgb;
    float cover = 0.0;
    for (int y = -12; y <= 12; y++) {
        for (int x = -12; x <= 12; x++) {
            if (x == 0 && y == 0) {
                continue;
            }
            float dist = length(vec2(float(x), float(y)));
            if (dist > radius + 0.35) {
                continue;
            }
            vec4 neighbor = maskAt(texCoord + texel * vec2(float(x), float(y)));
            if (covered(neighbor)) {
                cover = 1.0;
                outline = max(outline, neighbor.rgb);
            }
        }
    }
    if (inside || cover < 0.04) {
        discard;
    }
    fragColor = vec4(outline, 1.0);
}
