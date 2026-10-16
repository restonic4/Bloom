package com.restonic4.bloom.backends.vulkan.memory;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;

public final class VulkanMemory {
    private VulkanMemory() {}

    public static int findMemoryType(VkPhysicalDevice physicalDevice, int allowedTypeBits, int requiredProperties, MemoryStack stack) {
        VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.malloc(stack);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);

        for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
            boolean allowedForResource = (allowedTypeBits & (1 << i)) != 0;
            boolean hasProperties = (memoryProperties.memoryTypes(i).propertyFlags() & requiredProperties) == requiredProperties;

            if (allowedForResource && hasProperties) {
                return i;
            }
        }

        throw new IllegalStateException("No memory type matches the resource requirements and properties " + requiredProperties);
    }
}
