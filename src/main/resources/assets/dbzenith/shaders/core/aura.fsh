#version 150

#moj_import <fog.glsl>

// The aura shell (CX-24). Only the far half of the shell is drawn (the near half is culled), so the fighter inside is
// never tinted. How squarely a point faces the eye says how far in from the outline it is: "depth", 0 on the outline,
// 1 at the middle, as a share of the radius. The colour runs from the edge in to the white-hot core by depth, and the
// outline is cut by twisting noise into soft lobes or sharp flames that stream upward, taller near the top. Mode 3
// draws flame tongues: ribbons with u across and v from base to tip, the tongue's own seed in the vertex red.

uniform vec4 AuraCore;    // rgb, opacity in the middle
uniform vec4 AuraMid;     // rgb
uniform vec4 AuraEdge;    // rgb, opacity at the edge
uniform vec4 AuraRim;     // rgb, strength of the rim tint
uniform vec4 AuraShape;   // spike depth (share of the radius), sharpness 0..1, spikes round, rim width
uniform vec4 AuraMotion;  // time in seconds, scroll speed, streaks, flicker
uniform vec4 AuraMode;    // 0 shell / 1 glow / 2 haze, seed, opacity, lean of the spikes
uniform vec4 AuraBoost;   // extra brightness (charging, a hit, a burst), taller flames near the top, streak speed, stretch
uniform vec4 AuraFlow;    // twist of the flames (domain warp), unused
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

    if (AuraMode.x > 2.5) {
        // a flame tongue: a tapered lick, its sides eaten by noise rising up it, white-hot down the middle
        float v = surface.y;
        float u = surface.x * 2.0 - 1.0;
        float ts = vertexColor.r * 97.0 + seed;
        float rise = t * AuraMotion.y * 2.0;
        float wave = (pnoise(vec2(v * 3.0 - rise * 1.3, ts), 64.0) - 0.5) * 0.5 * v;
        float w = pow(max(0.0, 1.0 - v), 0.85) * (0.45 + 0.55 * smoothstep(0.0, 0.25, v));
        float n = pnoise(vec2(u * 2.5 + ts * 3.0, v * 4.0 - rise * 2.0), 64.0);
        float n2 = pnoise(vec2(u * 6.0 + ts, v * 9.0 - rise * 3.0), 64.0);
        float d = abs(u - wave) / max(w, 0.001) + (0.6 * n + 0.4 * n2 - 0.5) * (0.5 + 0.6 * AuraShape.y);
        float aa = fwidth(d) + 0.02;
        float body = 1.0 - smoothstep(0.85 - aa, 0.85 + aa, d);
        if (body <= 0.003) discard;
        float heat = clamp((1.0 - d / 0.85) * (1.0 - 0.55 * v), 0.0, 1.0);
        vec3 col = mix(AuraEdge.rgb, AuraMid.rgb, smoothstep(0.08, 0.4, heat));
        col = mix(col, AuraCore.rgb, smoothstep(0.4, 0.85, heat));
        col = mix(col, AuraCore.rgb, clamp(AuraBoost.x * 0.25, 0.0, 0.5));
        float alpha = mix(AuraEdge.a, AuraCore.a, heat) * smoothstep(0.0, 0.12, v) * vertexColor.a;
        fragColor = vec4(col, min(1.0, alpha * body * AuraMode.z * linear_fog_fade(vertexDistance, FogStart, FogEnd) * (1.0 + 0.25 * AuraBoost.x)));
        return;
    }

    float period = max(1.0, floor(AuraShape.z + 0.5));
    float scroll = t * AuraMotion.y;
    // spikes lean upward: deeper in, the same feature sits lower; the noise twists as it rises
    float warp = AuraFlow.x * (pnoise(vec2(surface.x * period, surface.y * period * 0.2 - t * 0.9 + seed * 1.3), period) - 0.5);
    vec2 p = vec2(surface.x * period + warp * 2.0, surface.y * period * AuraBoost.w + depth * AuraMode.w - scroll + seed);
    float n = 0.65 * pnoise(p, period) + 0.35 * pnoise(p * 2.0 + vec2(0.0, 7.3), period * 2.0);
    float ridge = pow(1.0 - abs(2.0 * n - 1.0), 3.0);
    float shaped = mix(n, ridge, AuraShape.y);
    float flicker = 1.0 + AuraMotion.w * (pnoise(vec2(surface.x * period, t * 9.0 + seed * 3.0), period) - 0.5) * 2.0;
    float foot = smoothstep(0.0, 0.2, surface.y);
    float tall = mix(1.0 - 0.45 * AuraBoost.y, 1.0 + 0.6 * AuraBoost.y, surface.y);   // taller flames near the top
    float cut = AuraShape.x * (1.0 - shaped) * max(0.0, flicker) * foot * tall;

    float aa = fwidth(depth) * 1.5 + 0.002;
    float body = smoothstep(cut, cut + aa, depth);
    if (body <= 0.003) discard;

    float e = depth - cut;
    float rimWidth = AuraShape.w;
    float rim = 1.0 - smoothstep(0.0, rimWidth, e);
    vec3 col = mix(AuraEdge.rgb, AuraMid.rgb, smoothstep(rimWidth * 0.5, rimWidth + 0.14, e));
    col = mix(col, AuraCore.rgb, smoothstep(0.16, 0.48, depth));
    // light streaks racing up through it, bending with the flames
    float streak = pnoise(vec2(surface.x * 40.0 + warp * 6.0, surface.y * 2.5 - t * AuraMotion.y * AuraBoost.z + seed), 40.0);
    col = mix(col, AuraCore.rgb, AuraMotion.z * smoothstep(0.55, 0.95, streak) * smoothstep(0.02, 0.15, e));
    float tint = AuraRim.a * (0.5 + 0.5 * pnoise(vec2(surface.x * 6.0, surface.y * 4.0 - t * 0.5 + seed * 2.0), 6.0));
    col = mix(col, AuraRim.rgb, rim * tint);
    col = mix(col, AuraCore.rgb, clamp(AuraBoost.x * 0.3, 0.0, 0.6) * smoothstep(0.0, 0.25, e));
    float alpha = mix(AuraEdge.a, AuraCore.a, smoothstep(0.1, 0.5, depth)) * (1.0 + 0.25 * AuraBoost.x);
    if (AuraMode.x > 1.5) alpha *= smoothstep(0.0, 0.3, e);                 // the inner layer: a soft haze, no hard outline
    fragColor = vec4(col, min(1.0, alpha * body * fade));
}
