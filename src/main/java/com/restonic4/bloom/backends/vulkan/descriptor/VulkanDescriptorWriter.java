package com.restonic4.bloom.backends.vulkan.descriptor;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
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
}
