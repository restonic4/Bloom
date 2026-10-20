package com.restonic4.bloom.backends.vulkan.descriptor;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.vulkan.VK10.*;

// Fills slots of an already allocated descriptor set
public final class VulkanDescriptorWriter {
    private VulkanDescriptorWriter() {}

    /**
     * Points a uniform buffer slot of {@code set} at the first {@code size} bytes of {@code buffer}.
     */
    public static void writeUniformBuffer(VulkanLogicalDevice logicalDevice, long set, int binding, VulkanBuffer buffer, long size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.get(0)
                    .buffer(buffer.getHandle())
                    .offset(0)
                    .range(size);

            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(binding)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(bufferInfo);

            vkUpdateDescriptorSets(logicalDevice.getHandle(), write, null);
        }
    }

    /**
     * Points an image + sampler slot of {@code set} at {@code imageView}, read through {@code sampler}.
     * The image must be in SHADER_READ_ONLY_OPTIMAL whenever the set is used.
     */
    public static void writeCombinedImageSampler(VulkanLogicalDevice logicalDevice, long set, int binding, long imageView, long sampler) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo.get(0)
                    .sampler(sampler)
                    .imageView(imageView)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(binding)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo);

            vkUpdateDescriptorSets(logicalDevice.getHandle(), write, null);
        }
    }
}
