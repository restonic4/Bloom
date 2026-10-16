#version 450

layout(location = 0) in vec3 inPosition;

layout(location = 0) out float outShade;

layout(push_constant) uniform PushConstants {
    mat4 viewProj;
    vec4 color;
} pushConstants;

void main() {
    gl_Position = pushConstants.viewProj * vec4(inPosition, 1.0);

    // Test only: dims geometry that is further back (lower z) so overlapping depth is visible
    outShade = 1.0 + inPosition.z * 0.5;
}