#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform MonoConfig {
    vec4 Accent;
    vec4 Params;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// Monochrome over the finished frame. Accent.rgb the accent, Accent.a splash on;
// Params: splash hue, splash range (both in turns), contrast, the accent's hue.
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec3 hsv(vec3 c) {
    vec4 k = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, k.wz), vec4(c.gb, k.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + 1e-6)), d / (q.x + 1e-6), q.x);
}

float near(float hue, float target, float range, vec3 c) {
    float dist = abs(fract(hue - target + 0.5) - 0.5);
    return (1.0 - smoothstep(range * 0.5, range, dist)) * smoothstep(0.22, 0.45, c.y) * smoothstep(0.06, 0.18, c.z);
}

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float l = clamp((dot(c, LUMA) - 0.5) * Params.z + 0.5, 0.0, 1.0);
    vec3 grey = vec3(l);
    vec3 h = hsv(c);
    // The accent at this pixel's brightness, lifting toward white past full.
    vec3 tinted = Accent.rgb * (l / max(dot(Accent.rgb, LUMA), 0.05));
    float over = max(max(tinted.r, tinted.g), tinted.b);
    if (over > 1.0) {
        tinted = mix(tinted / over, vec3(1.0), clamp(over - 1.0, 0.0, 1.0));
    }
    vec3 outColor = grey;
    if (Accent.a > 0.5) {
        outColor = mix(outColor, tinted, near(h.x, Params.x, Params.y, h));
    }
    // What is already in the accent colour (marked blocks, accent UI) stays as it is.
    outColor = mix(outColor, c, near(h.x, Params.w, 0.04, h));
    fragColor = vec4(outColor, 1.0);
}
