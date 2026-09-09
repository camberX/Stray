#version 330

uniform sampler2D InSampler;

out vec4 fragColor;

void main() {
    vec4 color = texelFetch(InSampler, ivec2(gl_FragCoord.xy), 0);
    if (color.a < 0.04) {
        discard;
    }
    fragColor = color;
}
