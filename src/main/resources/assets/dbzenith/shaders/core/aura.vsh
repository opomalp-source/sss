#version 150

#moj_import <fog.glsl>

// The aura shell (CX-24). Positions come camera-relative and unrotated, so the fragment stage can work out how
// squarely each point faces the eye straight from them.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out vec3 worldPos;
out vec3 worldNormal;
out vec2 surface;
out vec4 vertexColor;
out float vertexDistance;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    worldPos = Position;
    worldNormal = Normal;
    surface = UV0;
    vertexColor = Color;
    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);
}
