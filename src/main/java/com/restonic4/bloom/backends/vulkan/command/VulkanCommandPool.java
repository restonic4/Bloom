package com.restonic4.bloom.backends.vulkan.command;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanCommandPool implements Disposable {
    private final VkDevice device;
    private final long handle;

    private VulkanCommandPool(VkDevice device, long handle) {
        this.device = device;
        this.handle = handle;
    }

    public static VulkanCommandPool create(VulkanLogicalDevice logicalDevice) {
        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(logicalDevice.getGraphicsQueueFamilyIndex());

            LongBuffer pCommandPool = stack.mallocLong(1);

            int result = vkCreateCommandPool(device, createInfo, null, pCommandPool);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create command pool: " + result);
            }

            return new VulkanCommandPool(device, pCommandPool.get(0));
        }
    }

    public List<VkCommandBuffer> allocatePrimaryCommandBuffers(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Command buffer count must be positive");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(handle)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(count);

            PointerBuffer pCommandBuffers = stack.mallocPointer(count);

            int result = vkAllocateCommandBuffers(device, allocateInfo, pCommandBuffers);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to allocate command buffers: " + result);
            }

            List<VkCommandBuffer> commandBuffers = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                commandBuffers.add(new VkCommandBuffer(pCommandBuffers.get(i), device));
            }

            return List.copyOf(commandBuffers);
        }
    }

    @Override
    public void dispose() {
        vkDestroyCommandPool(device, handle, null);
    }
}