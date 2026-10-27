#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Albedo and material (r = roughness, g = metalness, b = ambient occlusion) baked from the CS2 finish.
uniform sampler2D Sampler0;
uniform sampler2D Sampler3;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;
in float diffuseLight;

out vec4 fragColor;

// A soft studio environment: bright overhead, a horizon band, darker floor.
vec3 environment(vec3 r, float rough) {
    float up = r.y;
    vec3 floorColor = vec3(0.10, 0.10, 0.11);
    vec3 horizon = vec3(0.55, 0.56, 0.58);
    vec3 top = vec3(1.05, 1.05, 1.08);
    vec3 env = up < 0.0 ? mix(horizon, floorColor, smoothstep(0.0, 0.6, -up)) : mix(horizon, top, smoothstep(0.0, 0.7, up));
    // Two soft box lights so polished metal shows highlights as it turns.
    float key = pow(max(dot(r, normalize(vec3(0.45, 0.75, 0.5))), 0.0), mix(180.0, 6.0, rough));
    float rim = pow(max(dot(r, normalize(vec3(-0.7, 0.2, -0.4))), 0.0), mix(90.0, 4.0, rough));
    env += vec3(1.6) * key * (1.0 - rough * 0.7) + vec3(0.6) * rim * (1.0 - rough * 0.8);
    // Rough surfaces see a blurrier, flatter environment.
    return mix(env, vec3(0.45), rough * rough * 0.85);
}

void main() {
    vec4 albedo = texture(Sampler0, texCoord0) * vertexColor;
    vec3 material = texture(Sampler3, texCoord0).rgb;
    float rough = clamp(material.r, 0.04, 1.0);
    float metal = material.g;
    float ao = mix(0.55, 1.0, material.b);

    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    if (dot(n, v) < 0.0) {
        n = -n;
    }
    vec3 r = reflect(-v, n);
    float nv = max(dot(n, v), 0.0);

    vec3 f0 = mix(vec3(0.04), albedo.rgb, metal);
    vec3 fresnel = f0 + (vec3(1.0) - f0) * pow(1.0 - nv, 5.0) * (1.0 - rough);
    vec3 specular = environment(r, rough) * fresnel;
    vec3 diffuse = albedo.rgb * (1.0 - metal) * diffuseLight;

    vec3 color = (diffuse + specular) * ao;
    color *= lightMapColor.rgb;
    vec4 outColor = vec4(color, 1.0) * ColorModulator;

    fragColor = apply_fog(outColor, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
