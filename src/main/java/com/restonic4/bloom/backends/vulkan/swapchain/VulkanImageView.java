package com.restonic4.bloom.backends.vulkan.swapchain;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK10.*;

public final class VulkanImageView implements Disposable {
    private final VkDevice device;
    private final long handle;

    private VulkanImageView(VkDevice device, long handle) {
        this.device = device;
        this.handle = handle;
    }

    public static VulkanImageView create(VulkanLogicalDevice logicalDevice, long image, int format) {
        return create(logicalDevice, image, format, VK_IMAGE_ASPECT_COLOR_BIT);
    }

    public static VulkanImageView create(VulkanLogicalDevice logicalDevice, long image, int format, int aspectMask) {
        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = stackPush()) {
            VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(format)
                    .subresourceRange(range -> range
                            .aspectMask(aspectMask)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    );

            LongBuffer imageViewHandle = stack.mallocLong(1);

            int result = vkCreateImageView(device, createInfo, null, imageViewHandle);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create Vulkan image view: " + result);
            }

            return new VulkanImageView(device, imageViewHandle.get(0));
        }
    }

    public long getHandle() { return handle; }

    @Override
    public void dispose() {
        vkDestroyImageView(device, handle, null);
    }
}