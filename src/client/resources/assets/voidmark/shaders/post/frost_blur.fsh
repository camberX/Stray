#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

in vec2 texCoord;

out vec4 fragColor;

// Same kernel as minecraft:post/box_blur (linear taps at step 2 plus a half-weight
// tail), so three H+V rounds reproduce vanilla's menu blur exactly. Alpha is
// forced opaque because the frost is blitted as solid glass.
void main() {
    vec2 oneTexel = 1.0 / vec2(textureSize(InSampler, 0));
    vec2 sampleStep = oneTexel * BlurDir;

    vec4 blurred = vec4(0.0);
    float actualRadius = round(Radius);
    for (float a = -actualRadius + 0.5; a <= actualRadius; a += 2.0) {
        blurred += texture(InSampler, texCoord + sampleStep * a);
    }
    blurred += texture(InSampler, texCoord + sampleStep * actualRadius) / 2.0;
    fragColor = vec4(blurred.rgb / (actualRadius + 0.5), 1.0);
}
