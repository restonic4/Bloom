package com.restonic4.bloom.backends.vulkan.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkQueueFamilyProperties2;

import java.nio.IntBuffer;
import java.util.stream.IntStream;

import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceQueueFamilyProperties2;

public class QueueFamilyIndices {
    private Integer graphics, present;

    public static QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device, long surface) {
        QueueFamilyIndices indices = new QueueFamilyIndices();

        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer queueFamilyCount = stack.ints(0);

            vkGetPhysicalDeviceQueueFamilyProperties2(device, queueFamilyCount, null);

            VkQueueFamilyProperties2.Buffer queueFamilies = VkQueueFamilyProperties2.calloc(queueFamilyCount.get(0), stack);

            vkGetPhysicalDeviceQueueFamilyProperties2(device, queueFamilyCount, queueFamilies);

            IntBuffer presentSupport = stack.ints(VK_FALSE);

            IntStream.range(0, queueFamilies.capacity())
                    .forEach(index -> {
                        int flags = queueFamilies.get(index).queueFamilyProperties().queueFlags();

                        if ((flags & VK_QUEUE_GRAPHICS_BIT) != 0) {
                            indices.graphics = index;
                        }

                        vkGetPhysicalDeviceSurfaceSupportKHR(device, index, surface, presentSupport);

                        if (presentSupport.get(0) == VK_TRUE) {
                            indices.present = index;
                        }
                    });

            return indices;
        }
    }

    public boolean isComplete() {
        return graphics != null && present != null;
    }

    public int[] unique() {
        return IntStream.of(graphics, present).distinct().toArray();
    }

    public int graphics() { return graphics; }
    public int present() { return present; }
}
