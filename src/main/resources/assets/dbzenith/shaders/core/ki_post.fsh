#version 150

// Ki post-processing (the ki attack rework, phase 9), over the finished picture before the bloom is added:
// heat shimmer where there is ki light (the blurred glow is the mask), a red/blue split toward the screen's edges
// after big blows (Aberration), and the anime impact frame: a stark black-and-white negative (Impact).

uniform sampler2D DiffuseSampler;   // a copy of the picture
uniform sampler2D GlowSampler;      // the bloom's blurred glow, half size
uniform sampler2D NoiseSampler;     // ki_noise.png
uniform float Time;                 // seconds
uniform float Haze;
uniform float Aberration;
uniform float Impact;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 g = texture(GlowSampler, texCoord).rgb;
    float mask = clamp(max(g.r, max(g.g, g.b)) * 1.6, 0.0, 1.0);
    vec2 n1 = texture(NoiseSampler, fract(texCoord * vec2(5.0, 3.0) + vec2(Time * 0.07, -Time * 0.19))).rb - 0.5;
    vec2 n2 = texture(NoiseSampler, fract(texCoord * vec2(9.0, 6.0) - vec2(Time * 0.13, Time * 0.31))).rb - 0.5;
    vec2 uv = texCoord + (n1 + 0.5 * n2) * Haze * mask * 0.014;
    vec2 c = uv - 0.5;
    vec2 off = c * Aberration * 0.025 * (0.25 + dot(c, c) * 2.5);
    vec3 col = vec3(texture(DiffuseSampler, uv + off).r, texture(DiffuseSampler, uv).g, texture(DiffuseSampler, uv - off).b);
    if (Impact > 0.0) {
        float l = dot(col, vec3(0.299, 0.587, 0.114));
        vec3 neg = vec3(1.0 - smoothstep(0.3, 0.55, l));
        col = mix(col, neg, Impact);
    }
    fragColor = vec4(col, 1.0);
}
