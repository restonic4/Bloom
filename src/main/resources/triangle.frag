#version 450

layout(location = 0) out vec4 outColor;

// Small block of data written by the CPU into the command buffer (vkCmdPushConstants)
layout(push_constant) uniform PushConstants {
    vec4 color;
} pushConstants;

void main() {
    outColor = pushConstants.color;
}