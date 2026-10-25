package com.restonic4.bloom.backends.vulkan.shader;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;

public record VulkanShaderStage(VulkanShader shader, int stage) {
    public VkPipelineShaderStageCreateInfo createInfo(MemoryStack stack) {
        return VkPipelineShaderStageCreateInfo.calloc(stack)
                .sType$Default()
                .stage(stage)
                .module(shader.module().getHandle())
                .pName(stack.UTF8("main"));
    }
}