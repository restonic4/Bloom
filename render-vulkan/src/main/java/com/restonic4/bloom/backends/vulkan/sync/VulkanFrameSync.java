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
    private final long[] imageAvailableSemaphores; // One per frame in flight
    private long[] renderFinishedSemaphores; // One per swapchain image
    private final long timelineSemaphore;

    private long lastSubmittedTimelineValue;

    private VulkanFrameSync(
            VkDevice device,
            long[] imageAvailableSemaphores,
            long[] renderFinishedSemaphores,
            long timelineSemaphore
    ) {
        this.device = device;
        this.imageAvailableSemaphores = imageAvailableSemaphores;
        this.renderFinishedSemaphores = renderFinishedSemaphores;
        this.timelineSemaphore = timelineSemaphore;
    }

    public static VulkanFrameSync create(VulkanLogicalDevice logicalDevice, int swapchainImageCount, int framesInFlight) {
        if (swapchainImageCount <= 0) {
            throw new IllegalArgumentException("Swapchain image count must be positive");
        }

        if (framesInFlight <= 0) {
            throw new IllegalArgumentException("Frames in flight must be positive");
        }

        VkDevice device = logicalDevice.getHandle();

        long timeline = VK_NULL_HANDLE;
        long[] imageAvailable = new long[framesInFlight];
        long[] renderFinished = new long[swapchainImageCount];

        try {
            for (int i = 0; i < imageAvailable.length; i++) {
                imageAvailable[i] = createBinarySemaphore(device);
            }

            for (int i = 0; i < renderFinished.length; i++) {
                renderFinished[i] = createBinarySemaphore(device);
            }

            timeline = createTimelineSemaphore(device);

            return new VulkanFrameSync(device, imageAvailable, renderFinished, timeline);
        } catch (RuntimeException e) {
            destroyIfCreated(device, timeline);

            for (long semaphore : imageAvailable) {
                destroyIfCreated(device, semaphore);
            }

            for (long semaphore : renderFinished) {
                destroyIfCreated(device, semaphore);
            }

            throw e;
        }
    }

    public void recreateRenderFinished(int swapchainImageCount) {
        if (swapchainImageCount <= 0) {
            throw new IllegalArgumentException("Swapchain image count must be positive");
        }

        long[] created = new long[swapchainImageCount];

        try {
            for (int i = 0; i < created.length; i++) {
                created[i] = createBinarySemaphore(device);
            }
        } catch (RuntimeException e) {
            for (long semaphore : created) {
                destroyIfCreated(device, semaphore);
            }

            throw e;
        }

        for (long semaphore : renderFinishedSemaphores) {
            vkDestroySemaphore(device, semaphore, null);
        }

        renderFinishedSemaphores = created;
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

    public long getImageAvailableSemaphore(int frameIndex) { return imageAvailableSemaphores[frameIndex]; }
    public long getRenderFinishedSemaphore(int imageIndex) { return renderFinishedSemaphores[imageIndex]; }
    public long getTimelineSemaphore() { return timelineSemaphore; }
    public long nextTimelineValue() { return lastSubmittedTimelineValue + 1; }

    public long getLastSubmittedValue() { return lastSubmittedTimelineValue; }

    /** The newest timeline value the GPU has reached: every frame up to it is finished. */
    public long getCompletedValue() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pValue = stack.mallocLong(1);

            int result = vkGetSemaphoreCounterValue(device, timelineSemaphore, pValue);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to read the graphics timeline: " + result);
            }

            return pValue.get(0);
        }
    }

    public void markSubmitted(long value) {
        if (value != lastSubmittedTimelineValue + 1) {
            throw new IllegalArgumentException("Timeline values must increase by one");
        }

        lastSubmittedTimelineValue = value;
    }

    /**
     * Blocks until the GPU finished the frame that last used the slot we are about to reuse.
     * With N frames in flight, that is the frame submitted N frames ago, so we wait for the
     * timeline to reach (last submitted value - (N - 1)). With N = 1 this waits for the last submission.
     */
    public void waitForFrameSlot(int framesInFlight) {
        long targetValue = lastSubmittedTimelineValue - (framesInFlight - 1);

        // Nothing to wait for during the first frames
        if (targetValue <= 0) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreWaitInfo waitInfo = VkSemaphoreWaitInfo.calloc(stack)
                    .sType$Default()
                    .semaphoreCount(1)
                    .pSemaphores(stack.longs(timelineSemaphore))
                    .pValues(stack.longs(targetValue));

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

        for (long semaphore : imageAvailableSemaphores) {
            vkDestroySemaphore(device, semaphore, null);
        }

        vkDestroySemaphore(device, timelineSemaphore, null);
    }
}