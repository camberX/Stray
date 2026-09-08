#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

float covered(vec2 uv) {
    vec4 sampleColor = texture(InSampler, uv);
    return (sampleColor.a > 0.04 || max(sampleColor.r, max(sampleColor.g, sampleColor.b)) > 0.04) ? 1.0 : 0.0;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float inside = covered(texCoord);
    float cover = 0.0;
    for (int y = -5; y <= 5; y++) {
        for (int x = -5; x <= 5; x++) {
            if (x == 0 && y == 0) {
                continue;
            }
            if (length(vec2(float(x), float(y))) > 5.5) {
                continue;
            }
            cover = max(cover, covered(texCoord + texel * vec2(float(x), float(y))));
        }
    }
    float rim = max(cover - inside, 0.0);
    if (rim < 0.04) {
        discard;
    }
    fragColor = vec4(0.95, 0.98, 1.0, 1.0);
}
