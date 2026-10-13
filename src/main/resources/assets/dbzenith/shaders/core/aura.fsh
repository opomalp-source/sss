#version 150

#moj_import <fog.glsl>

// The aura shell (CX-24). Only the far half of the shell is drawn (the near half is culled), so the fighter inside is
// never tinted. How squarely a point faces the eye says how far in from the outline it is: "depth", 0 on the outline,
// 1 at the middle, as a share of the radius. The colour runs from the edge in to the white-hot core by depth, and the
// outline is cut into a few long flame tongues rising up the edge to pointed tips (flameCut), with vertical streaks of
// energy racing up inside. Mode 3
// draws inner flames: soft ribbons with u across and v from base to tip, the flame's own seed in the vertex red.

uniform vec4 AuraCore;    // rgb, opacity in the middle
uniform vec4 AuraMid;     // rgb
uniform vec4 AuraEdge;    // rgb, opacity at the edge
uniform vec4 AuraRim;     // rgb, strength of the rim tint
uniform vec4 AuraShape;   // spike depth (share of the radius), sharpness 0..1, spikes round, rim width
uniform vec4 AuraMotion;  // time in seconds, scroll speed, streaks, flicker
uniform vec4 AuraMode;    // 0 shell / 1 glow / 2 haze, seed, opacity, lean of the spikes
uniform vec4 AuraBoost;   // extra brightness (charging, a hit, a burst), taller flames near the top, streak speed, stretch
uniform vec4 AuraFlow;    // twist of the flames (domain warp), the bright band's width (0: filled, the older look), unused
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

uniform vec4 AuraCenter;  // the aura's middle at its feet, relative to the eye (xyz), unused

// Where round the aura a point lies, measured from the side facing the eye (0..1, 0 and 1 straight towards it). The
// flames are laid out by this rather than by the shell's own angle, so the outline keeps the same flames as the camera
// moves round instead of sliding and melting as other parts of the shell turn to the edge.
float viewU;

// Flame teeth up the outline (the classic Dragon Ball silhouette): a few long tongues round the aura, each rising up the
// edge from a deep root, leaning out to a pointed tip, then snapping back in just above it. The pattern climbs over
// time, every tongue has its own length that flickers, and nothing about it is round. Returns how far in from the
// outline (share of the radius) the edge lies here.
float flameCut(float y, float depth, float t, float seed) {
    float count = clamp(floor(AuraShape.z + 0.5), 2.0, 8.0);                    // tongues round
    float rows = clamp(AuraBoost.w * 10.0, 1.5, 6.0);                           // tongues up the height
    float ang = viewU * count;
    float sway = AuraFlow.x * (pnoise(vec2(ang, y * 2.0 - t * 0.7 + seed), count) - 0.5);
    float phase = 1.6 * pnoise(vec2(ang, seed * 1.7), count);          // each column out of step with the next
    float v = y * rows + depth * AuraMode.w * 0.5 - t * AuraMotion.y * 0.5 + phase + sway;
    float f = fract(v);
    float tooth = pow(f, mix(1.1, 2.4, AuraShape.y));                         // sharper: a longer, slimmer point
    tooth *= 1.0 - smoothstep(0.88, 1.0, f);                                  // the quick turn back in above the tip
    float len = 0.55 + 0.45 * pnoise(vec2(ang + floor(v) * 3.1, seed + floor(v) * 1.37), count);
    len *= 1.0 + min(AuraMotion.w, 0.5) * 0.6 * (pnoise(vec2(ang, t * 3.0 + floor(v) * 5.3), count) - 0.5);   // flicker
    float foot = smoothstep(0.06, 0.34, y);                           // a clean bowl round the feet, tongues from the knees up
    float tall = 1.0 + 0.5 * AuraBoost.y * y;                         // longer tongues higher up
    return min(AuraShape.x, 0.42) * (1.0 - clamp(tooth * len, 0.0, 1.0)) * foot * tall;
}

// The flame edge smeared upward, the way a drawn aura's tongues streak: how covered this point is when the outline is
// also sampled a little below it (up to AuraFlow.w of the height), the lower samples counting less. A tip then trails
// a soft streak above it instead of ending in a hard cut.
float smearedCover(float depth, float t, float seed, float feather, out float cutHere) {
    cutHere = flameCut(surface.y, depth, t, seed);
    float cover = smoothstep(cutHere, cutHere + feather, depth);
    if (AuraFlow.w <= 0.0) return cover;
    // six samples, each pixel's set nudged by its own small offset, so they blend into one streak instead of
    // showing as separate stepped copies of the edge
    float jitter = hash(gl_FragCoord.xy) / 6.0;
    float total = 1.0;
    for (int k = 1; k <= 6; k++) {
        float q = (float(k) - jitter) / 6.0;
        float w = 0.75 * (1.0 - q);
        float c = flameCut(surface.y - AuraFlow.w * q, depth, t, seed);
        cover += w * smoothstep(c, c + feather * 1.6, depth);
        total += w;
    }
    return cover / total;
}

void main() {
    float facing = abs(dot(normalize(worldNormal), normalize(-worldPos)));
    float depth = 1.0 - sqrt(max(0.0, 1.0 - facing * facing));
    float t = AuraMotion.x;
    float seed = AuraMode.y;
    // no fog: an aura glows, and the fog settings when it is drawn can be the sky's or the clouds' (it vanished there)
    float fade = AuraMode.z * vertexColor.a;
    // drawn on twos: the flames hold each drawing, then jump to the next (AuraFlow.z drawings a second; 0 runs smooth)
    float td = AuraFlow.z > 0.0 ? floor(t * AuraFlow.z) / AuraFlow.z : t;
    vec2 rel = worldPos.xz - AuraCenter.xz;
    vec2 eye = -AuraCenter.xz;
    float a0 = length(eye) > 0.05 ? atan(eye.y, eye.x) : 0.0;
    viewU = fract((atan(rel.y, rel.x) - a0) / 6.2831853 + 1.0);

    if (AuraMode.x > 0.5 && AuraMode.x < 1.5) {
        // the glow: a thin bright band hugging the outline (AuraShape.w: how far in the shell's own outline lies)
        // it follows the flame edge (the shell's outline lies `band` in from the glow's own mesh), so the halo has the
        // same tongues as the flames instead of a smooth oval round them
        float band = max(0.005, AuraShape.w);
        float glowCut = flameCut(surface.y, depth, td, seed);
        if (AuraFlow.w > 0.0) glowCut = 0.5 * glowCut + 0.5 * flameCut(surface.y - AuraFlow.w * 0.5, depth, td, seed);
        float edge = band + glowCut * (1.0 - band);
        float w = max(band, 0.035);
        float g = pow(smoothstep(edge - w, edge, depth), 1.5) * (1.0 - 0.85 * smoothstep(edge, edge + w * 2.5, depth));
        float wobble = 0.8 + 0.2 * pnoise(vec2(viewU * 8.0, surface.y * 1.5 - t * 1.5 + seed), 8.0);
        float a = min(1.0, g * wobble * fade * (1.0 + AuraBoost.x));
        if (a < 0.003) discard;
        fragColor = vec4(mix(AuraRim.rgb, vec3(1.0), 0.35 * smoothstep(edge - w * 0.5, edge, depth)), a);
        return;
    }

    if (AuraMode.x > 2.5) {
        // a flame tongue: a tapered lick, its sides eaten by noise rising up it, white-hot down the middle
        float v = surface.y;
        float u = surface.x * 2.0 - 1.0;
        float ts = vertexColor.r * 97.0 + seed;
        float rise = t * AuraMotion.y * 2.0;
        float wave = (pnoise(vec2(v * 1.5 - rise * 1.3, ts), 64.0) - 0.5) * 0.35 * v;
        float w = pow(max(0.0, 1.0 - v), 1.35) * (0.5 + 0.5 * smoothstep(0.0, 0.2, v));   // slim, drawn to a point
        float n = pnoise(vec2(u * 2.0 + ts * 3.0, v * 3.0 - rise * 2.0), 64.0);
        float d = abs(u - wave) / max(w, 0.001) + (n - 0.5) * 0.25;           // a gentle wobble, no ragged bites
        float body = (1.0 - smoothstep(0.6, 0.95, d)) * (1.0 - smoothstep(0.92, 1.0, v));
        if (body <= 0.003) discard;
        float heat = clamp((1.0 - d / 0.95) * (1.0 - 0.55 * v), 0.0, 1.0);
        vec3 col = mix(AuraEdge.rgb, AuraMid.rgb, smoothstep(0.08, 0.4, heat));
        col = mix(col, AuraCore.rgb, smoothstep(0.4, 0.85, heat));
        col = mix(col, AuraCore.rgb, clamp(AuraBoost.x * 0.25, 0.0, 0.5));
        float alpha = mix(AuraEdge.a, AuraCore.a, heat) * smoothstep(0.0, 0.12, v) * vertexColor.a;
        fragColor = vec4(col, min(1.0, alpha * body * AuraMode.z * (1.0 + 0.25 * AuraBoost.x)));
        return;
    }

    float feather = 0.03 + fwidth(depth) * 1.5;
    float cut;
    float body = smearedCover(depth, td, seed, feather, cut);
    if (body <= 0.003) discard;
    float scroll = t * AuraMotion.y;
    float period = clamp(floor(AuraShape.z + 0.5), 2.0, 8.0);
    float warp = AuraFlow.x * (pnoise(vec2(viewU * period, surface.y * period * 0.2 - t * 0.9 + seed * 1.3), period) - 0.5);

    float e = depth - cut;
    float rimWidth = AuraShape.w;
    float band = AuraFlow.y;
    if (band > 0.0) {
        // The anime look: a thick band of light just behind the flame edge, saturated at the very edge and hotter just
        // inside, streaked with fast upward blur, and the middle left almost clear so the fighter shows through.
        float inBand = 1.0 - smoothstep(band * 0.7, band * 1.5, e);
        vec3 c = mix(AuraEdge.rgb, AuraMid.rgb, smoothstep(0.0, band * 0.55, e));
        float blur = pnoise(vec2(viewU * 64.0 + warp * 4.0, surface.y * 0.9 - td * AuraMotion.y * AuraBoost.z * 0.5 + seed), 64.0);
        c = mix(c, AuraCore.rgb, 0.45 * (1.0 - smoothstep(0.0, band * 0.35, e)));   // a hot highlight hugging the outer edge
        c = mix(c, AuraCore.rgb, AuraMotion.z * smoothstep(0.5, 0.9, blur) * inBand);
        float tintB = AuraRim.a * (0.5 + 0.5 * pnoise(vec2(viewU * 16.0, surface.y * 1.2 - t * 0.8 + seed * 2.0), 16.0));
        c = mix(c, AuraRim.rgb, (1.0 - smoothstep(0.0, rimWidth, e)) * tintB);
        c = mix(c, AuraCore.rgb, clamp(AuraBoost.x * 0.3, 0.0, 0.6) * inBand);
        // the middle keeps a light tint of the form's colour, a little more the more steeply you look down on it, so
        // from above the aura reads as a glowing pool round the fighter instead of vanishing
        float down = smoothstep(0.35, 0.9, normalize(-worldPos).y);
        float middle = AuraCore.a + 0.18 * down;
        float a = mix(middle, AuraEdge.a, inBand) * (0.9 + 0.3 * blur * inBand) * (1.0 + 0.25 * AuraBoost.x);
        fragColor = vec4(c, min(1.0, a * body * fade));
        return;
    }
    float rim = 1.0 - smoothstep(0.0, rimWidth, e);
    vec3 col = mix(AuraEdge.rgb, AuraMid.rgb, smoothstep(rimWidth * 0.5, rimWidth + 0.14, e));
    col = mix(col, AuraCore.rgb, smoothstep(0.16, 0.48, depth));
    // light streaks racing up through it, bending with the flames
    // long thin streaks of energy racing straight up (stretched far up, narrow round)
    float streak = pnoise(vec2(viewU * 48.0 + warp * 4.0, surface.y * 1.1 - t * AuraMotion.y * AuraBoost.z * 0.5 + seed), 48.0);
    col = mix(col, AuraCore.rgb, AuraMotion.z * smoothstep(0.55, 0.95, streak) * smoothstep(0.02, 0.15, e));
    float tint = AuraRim.a * (0.5 + 0.5 * pnoise(vec2(viewU * 16.0, surface.y * 1.2 - t * 0.8 + seed * 2.0), 16.0));
    col = mix(col, AuraRim.rgb, rim * tint);
    col = mix(col, AuraCore.rgb, clamp(AuraBoost.x * 0.3, 0.0, 0.6) * smoothstep(0.0, 0.25, e));
    float alpha = mix(AuraEdge.a, AuraCore.a, smoothstep(0.1, 0.5, depth)) * (1.0 + 0.25 * AuraBoost.x);
    if (AuraMode.x > 1.5) alpha *= smoothstep(0.0, 0.3, e);                 // the inner layer: a soft haze, no hard outline
    fragColor = vec4(col, min(1.0, alpha * body * fade));
}
