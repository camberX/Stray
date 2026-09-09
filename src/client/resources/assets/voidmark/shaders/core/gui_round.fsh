#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// texCoord0 is the distance from the two outer edges, in corner radii.
// The corner circle is centred on (1, 1). Coverage is analytic, so the
// silhouette stays smooth at any radius or GUI scale.
//
// RING_GUI_PX, when set, is "how many framebuffer pixels of ring" — Java
// writes the current GUI scale so a 1 GUI-pixel rim is 1*scale FB pixels.
// Band width is RING_GUI_PX * fwidth(uv). Never recover scale from
// Projection: a leftover matrix made the band larger than the corner and
// every rim drew as a filled disc (and the dummy texture was circle.png).

float roundedDistance(vec2 uv) {
    vec2 q = vec2(1.0) - uv;
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - 1.0;
}

void main() {
    vec2 uv = texCoord0;
    float d = roundedDistance(uv);
    float px = max(fwidth(uv.x), fwidth(uv.y));
    px = max(px, 1.0e-5);
    float cover = clamp(0.5 - d / px, 0.0, 1.0);
#ifdef RING_GUI_PX
    float band = RING_GUI_PX * px;
    cover = max(cover - clamp(0.5 - (d + band) / px, 0.0, 1.0), 0.0);
#endif
#ifdef INVERT
    cover = 1.0 - cover;
#endif
    vec4 color = vertexColor;
#ifdef FROST
    vec2 screen = vec2(textureSize(Sampler0, 0));
    color = vec4(texture(Sampler0, gl_FragCoord.xy / max(screen, vec2(1.0))).rgb, 1.0) * vertexColor;
#endif
    color.a *= cover;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
