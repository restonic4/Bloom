#version 450

layout(location = 0) in vec2 inPosition;

layout(push_constant) uniform PushConstants {
    mat4 viewProj;
    vec4 color;
} pushConstants;

void main() {
    gl_Position = pushConstants.viewProj * vec4(inPosition, 0.0, 1.0);
}