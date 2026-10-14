#version 150

// Ki energy (the ki attack rework). The mode comes in UV1.x: 0 the shell, 1 the core, 2 plasma strands, 3 a soft halo,
// 4 a ribbon (soft across, bright down the middle).

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewNormal;
out vec3 viewPos;
flat out int mode;

void main() {
    vec4 p = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * p;
    viewPos = p.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    vertexColor = Color;
    texCoord0 = UV0;
    mode = UV1.x;
}
