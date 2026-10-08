#version 150

#moj_import <fog.glsl>

// The aura shell (CX-24). Only the far half of the shell is drawn (the near half is culled), so the fighter inside is
// never tinted. How squarely a point faces the eye says how far in from the outline it is: "depth", 0 on the outline,
// 1 at the middle, as a share of the radius. The colour runs from the edge in to the white-hot core by depth, and the
// outline is cut by noise into soft lobes or sharp spikes that stream upward.

uniform vec4 AuraCore;    // rgb, opacity in the middle
uniform vec4 AuraMid;     // rgb
uniform vec4 AuraEdge;    // rgb, opacity at the edge
uniform vec4 AuraRim;     // rgb, strength of the rim tint
uniform vec4 AuraShape;   // spike depth (share of the radius), sharpness 0..1, spikes round, rim width
uniform vec4 AuraMotion;  // time in seconds, scroll speed, streaks, flicker
uniform vec4 AuraMode;    // 0 shell / 1 glow / 2 haze, seed, opacity, lean of the spikes
uniform vec4 AuraBoost;   // extra brightness (charging, a hit, a burst), unused
uniform float FogStart;
uniform float FogEnd;

in vec3 worldPos;
in vec3 worldNormal;
in vec2 surface;
in vec4 vertexColor;
in float vertexDistance;

out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Value noise that wraps round the shell every `period` cells, so there is no seam.
float pnoise(vec2 p, float period) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float x0 = mod(i.x, period);
    float x1 = mod(i.x + 1.0, period);
    float a = hash(vec2(x0, i.y));
    float b = hash(vec2(x1, i.y));
    float c = hash(vec2(x0, i.y + 1.0));
    float d = hash(vec2(x1, i.y + 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    float facing = abs(dot(normalize(worldNormal), normalize(-worldPos)));
    float depth = 1.0 - sqrt(max(0.0, 1.0 - facing * facing));
    float t = AuraMotion.x;
    float seed = AuraMode.y;
    float fade = AuraMode.z * vertexColor.a * linear_fog_fade(vertexDistance, FogStart, FogEnd);

    if (AuraMode.x > 0.5 && AuraMode.x < 1.5) {
        // the glow: a thin bright band hugging the outline (AuraShape.w: how far in the shell's own outline lies)
        float band = max(0.005, AuraShape.w);
        float g = pow(smoothstep(0.0, band, depth), 1.5) * (1.0 - 0.8 * smoothstep(band, band * 4.0, depth));
        float wobble = 0.7 + 0.3 * pnoise(vec2(surface.x * 8.0, surface.y * 3.0 - t * 0.8 + seed), 8.0);
        float a = min(1.0, g * wobble * fade * (1.0 + AuraBoost.x));
        if (a < 0.003) discard;
        fragColor = vec4(mix(AuraRim.rgb, vec3(1.0), 0.35 * smoothstep(band * 0.5, band, depth)), a);
        return;
    }

    float period = max(1.0, floor(AuraShape.z + 0.5));
    float scroll = t * AuraMotion.y;
    // spikes lean upward: deeper in, the same feature sits lower
    vec2 p = vec2(surface.x * period, surface.y * period * 0.35 + depth * AuraMode.w - scroll + seed);
    float n = 0.65 * pnoise(p, period) + 0.35 * pnoise(p * 2.0 + vec2(0.0, 7.3), period * 2.0);
    float ridge = pow(1.0 - abs(2.0 * n - 1.0), 3.0);
    float shaped = mix(n, ridge, AuraShape.y);
    float flicker = 1.0 + AuraMotion.w * (pnoise(vec2(surface.x * period, t * 9.0 + seed * 3.0), period) - 0.5) * 2.0;
    float foot = smoothstep(0.0, 0.2, surface.y);
    float cut = AuraShape.x * (1.0 - shaped) * max(0.0, flicker) * foot;

    float aa = fwidth(depth) * 1.5 + 0.002;
    float body = smoothstep(cut, cut + aa, depth);
    if (body <= 0.003) discard;

    float e = depth - cut;
    float rimWidth = AuraShape.w;
    float rim = 1.0 - smoothstep(0.0, rimWidth, e);
    vec3 col = mix(AuraEdge.rgb, AuraMid.rgb, smoothstep(rimWidth * 0.5, rimWidth + 0.14, e));
    col = mix(col, AuraCore.rgb, smoothstep(0.16, 0.48, depth));
    float streak = pnoise(vec2(surface.x * 40.0, surface.y * 2.5 - scroll * 2.5 + seed), 40.0);
    col = mix(col, AuraCore.rgb, AuraMotion.z * smoothstep(0.55, 0.95, streak) * smoothstep(0.02, 0.15, e));
    float tint = AuraRim.a * (0.5 + 0.5 * pnoise(vec2(surface.x * 6.0, surface.y * 4.0 - t * 0.5 + seed * 2.0), 6.0));
    col = mix(col, AuraRim.rgb, rim * tint);
    col = mix(col, AuraCore.rgb, clamp(AuraBoost.x * 0.3, 0.0, 0.6) * smoothstep(0.0, 0.25, e));
    float alpha = mix(AuraEdge.a, AuraCore.a, smoothstep(0.1, 0.5, depth)) * (1.0 + 0.25 * AuraBoost.x);
    if (AuraMode.x > 1.5) alpha *= smoothstep(0.0, 0.3, e);                 // the inner layer: a soft haze, no hard outline
    fragColor = vec4(col, min(1.0, alpha * body * fade));
}
