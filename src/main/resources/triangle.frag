#version 450

layout(location = 0) in float inShade;

layout(location = 0) out vec4 outColor;

// Written by the CPU into the command buffer (vkCmdPushConstants), read by this stage only
layout(push_constant) uniform PushConstants {
    vec4 color;
} pushConstants;

void main() {
    outColor = vec4(pushConstants.color.rgb * inShade, pushConstants.color.a);
}