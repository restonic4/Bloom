package com.restonic4.bloom.backends.vulkan.image;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.memory.VulkanMemory;
import com.restonic4.bloom.backends.vulkan.swapchain.VulkanImageView;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanDepthImage implements Disposable {
    // The spec requires every device to support this format as a depth attachment
    public static final int FORMAT = VK_FORMAT_D32_SFLOAT;

    private final VkDevice device;
    private final long imageHandle;
    private final long memory;
    private final VulkanImageView view;

    private VulkanDepthImage(VkDevice device, long imageHandle, long memory, VulkanImageView view) {
        this.device = device;
        this.imageHandle = imageHandle;
        this.memory = memory;
        this.view = view;
    }

    public static VulkanDepthImage create(VulkanLogicalDevice logicalDevice, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Depth image size must be positive");
        }

        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Image: only a handle with a size, format and usage, no memory behind it yet

            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(FORMAT)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().width(width).height(height).depth(1);

            LongBuffer pImage = stack.mallocLong(1);

            int result = vkCreateImage(device, imageInfo, null, pImage);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create depth image: " + result);
            }

            long image = pImage.get(0);

            // Memory: the same create -> requirements -> allocate -> bind steps as a buffer, in GPU-only memory

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(device, image, requirements);

            int memoryTypeIndex;
            try {
                memoryTypeIndex = VulkanMemory.findMemoryType(device.getPhysicalDevice(), requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, stack);
            } catch (RuntimeException e) {
                vkDestroyImage(device, image, null);
                throw e;
            }

            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryTypeIndex);

            LongBuffer pMemory = stack.mallocLong(1);

            result = vkAllocateMemory(device, allocateInfo, null, pMemory);
            if (result != VK_SUCCESS) {
                vkDestroyImage(device, image, null);
                throw new IllegalStateException("Failed to allocate depth image memory: " + result);
            }

            long memory = pMemory.get(0);

            result = vkBindImageMemory(device, image, memory, 0);
            if (result != VK_SUCCESS) {
                vkFreeMemory(device, memory, null);
                vkDestroyImage(device, image, null);
                throw new IllegalStateException("Failed to bind depth image memory: " + result);
            }

            VulkanImageView view;
            try {
                view = VulkanImageView.create(logicalDevice, image, FORMAT, VK_IMAGE_ASPECT_DEPTH_BIT);
            } catch (RuntimeException e) {
                vkFreeMemory(device, memory, null);
                vkDestroyImage(device, image, null);
                throw e;
            }

            return new VulkanDepthImage(device, image, memory, view);
        }
    }

    public long getImageHandle() { return imageHandle; }
    public long getViewHandle() { return view.getHandle(); }

    @Override
    public void dispose() {
        view.dispose();
        vkDestroyImage(device, imageHandle, null);
        vkFreeMemory(device, memory, null);
    }
}
