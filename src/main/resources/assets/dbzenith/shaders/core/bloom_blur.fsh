#version 150

// Ki bloom (CX-45): one direction of a Gaussian blur, nine taps {@code Direction} texels apart.

uniform sampler2D DiffuseSampler;
uniform vec2 Direction;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float w[5] = float[](0.2270270, 0.1945946, 0.1216216, 0.0540541, 0.0162162);
    vec4 sum = texture(DiffuseSampler, texCoord) * w[0];
    for (int i = 1; i < 5; i++) {
        sum += texture(DiffuseSampler, texCoord + Direction * float(i)) * w[i];
        sum += texture(DiffuseSampler, texCoord - Direction * float(i)) * w[i];
    }
    fragColor = sum;
}
