#version 450

layout(location = 0) out vec4 outColor;

// Written by the CPU into the command buffer (vkCmdPushConstants), same block as the vertex shader
layout(push_constant) uniform PushConstants {
    mat4 viewProj;
    vec4 color;
} pushConstants;

void main() {
    outColor = pushConstants.color;
}