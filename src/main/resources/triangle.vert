#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in mat4 inModel;

layout(location = 0) out float outShade;

layout(push_constant) uniform PushConstants {
    mat4 viewProj;
    vec4 color;
} pushConstants;

void main() {
    gl_Position = pushConstants.viewProj * inModel * vec4(inPosition, 1.0);

    // Test only, same formula as the OpenGL shader: further from the camera is darker
    outShade = max(1.0 - 0.03 * gl_Position.w, 0.1);
}