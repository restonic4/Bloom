package com.restonic4.bloom.backends.vulkan.descriptor;

/**
 * One slot of a descriptor set layout.
 *
 * @param binding the slot number, matches {@code layout(binding = N)} in the shader
 * @param descriptorType VK_DESCRIPTOR_TYPE_*
 * @param stages shader stages that read the slot (VK_SHADER_STAGE_*)
 */
public record VulkanDescriptorBinding(int binding, int descriptorType, int stages) {}
