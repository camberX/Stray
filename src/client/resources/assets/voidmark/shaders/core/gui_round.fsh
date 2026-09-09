#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Analytic rounded-rectangle coverage for GUI chrome. Each quad is one
// quadrant (or a slice of one): texCoord0 is the distance from the quad's two
// outer edges measured in corner radii, so the corner circle is centred on
// (1, 1) and the silhouette is resolution independent at any radius or scale.
//
// Variants (shader defines):
//   RING_GUI_PX  draw only a band of that many GUI pixels inside the edge
//   INVERT       paint the outside of the silhouette instead (corner "ears")
//   FROST        take RGB from Sampler0 by screen position (frost blit)

float roundedDistance(vec2 uv) {
    vec2 q = vec2(1.0) - uv;
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - 1.0;
}

void main() {
    vec2 uv = texCoord0;
    float d = roundedDistance(uv);
    float px = max(max(fwidth(uv.x), fwidth(uv.y)), 1.0e-5);
    float cover = clamp(0.5 - d / px, 0.0, 1.0);
#ifdef RING_GUI_PX
    // The GUI projection maps [0, guiWidth] to [-1, 1], so its first diagonal
    // entry recovers the GUI scale without a dedicated uniform.
    float guiScale = clamp(ScreenSize.x * abs(ProjMat[0][0]) * 0.5, 1.0, 8.0);
    float band = RING_GUI_PX * guiScale * px;
    cover = max(cover - clamp(0.5 - (d + band) / px, 0.0, 1.0), 0.0);
#endif
#ifdef INVERT
    cover = 1.0 - cover;
#endif
    vec4 color = vertexColor;
#ifdef FROST
    color = vec4(texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb, 1.0) * vertexColor;
#endif
    color.a *= cover;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
