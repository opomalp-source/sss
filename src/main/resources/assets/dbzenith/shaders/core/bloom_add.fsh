#version 150

// Ki bloom (CX-45): a layer of light added onto the picture, scaled by Gain.

uniform sampler2D DiffuseSampler;
uniform float Gain;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    fragColor = vec4(c.rgb * Gain, 1.0);
}
