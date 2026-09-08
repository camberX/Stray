#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float inside = texture(InSampler, texCoord).a;
    float cover = 0.0;
    for (int y = -6; y <= 6; y++) {
        for (int x = -6; x <= 6; x++) {
            if (x == 0 && y == 0) {
                continue;
            }
            float d = length(vec2(float(x), float(y)));
            if (d > 6.5) {
                continue;
            }
            cover = max(cover, texture(InSampler, texCoord + texel * vec2(float(x), float(y))).a);
        }
    }
    float rim = max(cover - inside, 0.0);
    if (rim < 0.04) {
        discard;
    }
    fragColor = vec4(0.95, 0.98, 1.0, clamp(rim, 0.0, 1.0));
}
