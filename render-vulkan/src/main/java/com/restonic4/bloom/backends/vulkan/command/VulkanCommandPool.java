package com.restonic4.bloom.backends.vulkan.command;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

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

    // Records commands into a throwaway command buffer, submits them and blocks the CPU until the GPU is done.
    // Shortcut for startup uploads: it stalls the queue, so do not call it every frame.
    public void submitOneTime(VkQueue queue, Consumer<VkCommandBuffer> recorder) {
        VkCommandBuffer commandBuffer = allocatePrimaryCommandBuffers(1).get(0);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            int result = vkBeginCommandBuffer(commandBuffer, beginInfo);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to begin one-time command buffer: " + result);
            }

            recorder.accept(commandBuffer);

            result = vkEndCommandBuffer(commandBuffer);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to end one-time command buffer: " + result);
            }

            VkCommandBufferSubmitInfo.Buffer commandBufferInfo = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(commandBuffer);

            VkSubmitInfo2.Buffer submitInfo = VkSubmitInfo2.calloc(1, stack)
                    .sType$Default()
                    .pCommandBufferInfos(commandBufferInfo);

            result = vkQueueSubmit2(queue, submitInfo, VK_NULL_HANDLE);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to submit one-time command buffer: " + result);
            }

            // The CPU waits here until the GPU has executed everything on this queue
            result = vkQueueWaitIdle(queue);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to wait for the queue: " + result);
            }
        } finally {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                vkFreeCommandBuffers(device, handle, stack.pointers(commandBuffer));
            }
        }
    }

    @Override
    public void dispose() {
        vkDestroyCommandPool(device, handle, null);
    }
}