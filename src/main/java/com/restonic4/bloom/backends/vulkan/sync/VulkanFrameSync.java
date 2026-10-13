package com.restonic4.bloom.backends.vulkan.sync;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

public final class VulkanFrameSync implements Disposable {
    private final VkDevice device;
    private final long imageAvailableSemaphore;
    private final long[] renderFinishedSemaphores;
    private final long timelineSemaphore;

    private long lastSubmittedTimelineValue;

    private VulkanFrameSync(
            VkDevice device,
            long imageAvailableSemaphore,
            long[] renderFinishedSemaphores,
            long timelineSemaphore
    ) {
        this.device = device;
        this.imageAvailableSemaphore = imageAvailableSemaphore;
        this.renderFinishedSemaphores = renderFinishedSemaphores;
        this.timelineSemaphore = timelineSemaphore;
    }

    public static VulkanFrameSync create(VulkanLogicalDevice logicalDevice, int swapchainImageCount) {
        if (swapchainImageCount <= 0) {
            throw new IllegalArgumentException("Swapchain image count must be positive");
        }

        VkDevice device = logicalDevice.getHandle();

        long imageAvailable = VK_NULL_HANDLE;
        long timeline = VK_NULL_HANDLE;
        long[] renderFinished = new long[swapchainImageCount];

        try {
            imageAvailable = createBinarySemaphore(device);

            for (int i = 0; i < renderFinished.length; i++) {
                renderFinished[i] = createBinarySemaphore(device);
            }

            timeline = createTimelineSemaphore(device);

            return new VulkanFrameSync(device, imageAvailable, renderFinished, timeline);
        } catch (RuntimeException e) {
            destroyIfCreated(device, timeline);
            destroyIfCreated(device, imageAvailable);

            for (long semaphore : renderFinished) {
                destroyIfCreated(device, semaphore);
            }

            throw e;
        }
    }

    private static long createBinarySemaphore(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo createInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType$Default();

            LongBuffer pSemaphore = stack.mallocLong(1);

            int result = vkCreateSemaphore(device, createInfo, null, pSemaphore);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create binary semaphore: " + result);
            }

            return pSemaphore.get(0);
        }
    }

    private static long createTimelineSemaphore(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreTypeCreateInfo typeInfo = VkSemaphoreTypeCreateInfo.calloc(stack)
                    .sType$Default()
                    .semaphoreType(VK_SEMAPHORE_TYPE_TIMELINE)
                    .initialValue(0L);

            VkSemaphoreCreateInfo createInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(typeInfo.address());

            LongBuffer pSemaphore = stack.mallocLong(1);

            int result = vkCreateSemaphore(device, createInfo, null, pSemaphore);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create timeline semaphore: " + result);
            }

            return pSemaphore.get(0);
        }
    }

    private static void destroyIfCreated(VkDevice device, long semaphore) {
        if (semaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device, semaphore, null);
        }
    }

    public long getImageAvailableSemaphore() { return imageAvailableSemaphore; }
    public long getRenderFinishedSemaphore(int imageIndex) { return renderFinishedSemaphores[imageIndex]; }
    public long getTimelineSemaphore() { return timelineSemaphore; }
    public long nextTimelineValue() { return lastSubmittedTimelineValue + 1; }

    public void markSubmitted(long value) {
        if (value != lastSubmittedTimelineValue + 1) {
            throw new IllegalArgumentException("Timeline values must increase by one");
        }

        lastSubmittedTimelineValue = value;
    }

    public void waitForLastSubmission() {
        if (lastSubmittedTimelineValue == 0) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreWaitInfo waitInfo = VkSemaphoreWaitInfo.calloc(stack)
                    .sType$Default()
                    .semaphoreCount(1)
                    .pSemaphores(stack.longs(timelineSemaphore))
                    .pValues(stack.longs(lastSubmittedTimelineValue));

            // -1L represents UINT64_MAX, wait indefinitely
            int result = vkWaitSemaphores(device, waitInfo, -1L);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed waiting for graphics timeline: " + result);
            }
        }
    }

    @Override
    public void dispose() {
        for (long semaphore : renderFinishedSemaphores) {
            vkDestroySemaphore(device, semaphore, null);
        }

        vkDestroySemaphore(device, imageAvailableSemaphore, null);
        vkDestroySemaphore(device, timelineSemaphore, null);
    }
}