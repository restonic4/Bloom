#version 450

layout(location = 0) in float inShade;
layout(location = 1) in vec2 inUv;

layout(location = 0) out vec4 outColor;

// Descriptor set 0, slot 1: the texture and how to sample it
layout(set = 0, binding = 1) uniform sampler2D uTexture;

// Written by the CPU into the command buffer (vkCmdPushConstants), read by this stage only
layout(push_constant) uniform PushConstants {
    vec4 color;
} pushConstants;

void main() {
    outColor = texture(uTexture, inUv);
}