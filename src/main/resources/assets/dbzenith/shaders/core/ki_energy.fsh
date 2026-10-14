#version 150

// Ki energy (the ki attack rework): glowing, additive, never lit by the world. Sampler0 is ki_noise.png (red billows,
// green ridges for crackling strands, blue a second billow).

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewNormal;
in vec3 viewPos;
flat in int mode;

out vec4 fragColor;

void main() {
    float t = GameTime * 1200.0;                                    // seconds
    vec3 col = vertexColor.rgb;
    float a = vertexColor.a;
    vec3 v = normalize(-viewPos);
    vec3 n = normalize(viewNormal);
    float facing = abs(dot(n, v));
    float rim = 1.0 - facing;
    vec4 outc;
    if (mode == 0) {                                                // the shell: bright at the rim, plasma boiling over it
        float b1 = texture(Sampler0, texCoord0 * vec2(2.0, 1.0) + vec2(t * 0.31, -t * 0.17)).r;
        float b2 = texture(Sampler0, texCoord0 * vec2(3.0, 2.0) - vec2(t * 0.23, t * 0.41)).b;
        float boil = b1 * b2 * 2.2;
        float g = pow(rim, 2.0) * 0.85 + 0.1 + boil * 0.35;
        outc = vec4(col * g, a * clamp(g, 0.0, 1.0));
    } else if (mode == 1) {                                         // the core: white-hot where it faces you
        float c = pow(facing, 2.6);
        outc = vec4(mix(col, vec3(1.0), 0.7) * c * 1.05, a * c);
    } else if (mode == 2) {                                         // plasma strands: thin bright creases crawling over it
        float r = texture(Sampler0, texCoord0 * vec2(3.0, 1.5) + vec2(t * 0.55, t * 0.12)).g;
        float s = smoothstep(0.55, 0.85, r) * (0.4 + 0.6 * rim);
        outc = vec4(mix(col, vec3(1.0), 0.35) * s * 1.1, a * s);
    } else if (mode == 3) {                                         // a halo: soft, round
        float d = length(texCoord0 - 0.5) * 2.0;
        float h = pow(clamp(1.0 - d, 0.0, 1.0), 2.4);
        outc = vec4(col * h, a * h);
    } else if (mode == 4) {                                         // a ribbon: soft across, bright down the middle, flowing
        float across = 1.0 - abs(texCoord0.x * 2.0 - 1.0);
        float flow = texture(Sampler0, vec2(texCoord0.x * 0.5, texCoord0.y * 0.5 - t * 0.9)).r;
        float h = pow(across, 1.8) * (0.7 + 0.6 * flow);
        outc = vec4(mix(col, vec3(1.0), pow(across, 6.0)) * h, a * h);
    } else {                                                        // the tint: a see-through body in its colour, thickest where it faces you
        outc = vec4(col, a * (0.3 + 0.7 * pow(facing, 0.7)));
    }
    fragColor = outc * ColorModulator;
}
