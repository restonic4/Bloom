package com.restonic4.bloom.backends.vulkan.image;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;
import com.restonic4.bloom.backends.vulkan.command.VulkanCommandPool;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.memory.VulkanMemory;
import com.restonic4.bloom.backends.vulkan.swapchain.VulkanImageView;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

public final class VulkanImage implements Disposable {
    private final VkDevice device;
    private final int width;
    private final int height;
    private final long imageHandle;
    private final long memory;
    private final VulkanImageView view;

    private VulkanImage(VkDevice device, int width, int height, long imageHandle, long memory, VulkanImageView view) {
        this.device = device;
        this.width = width;
        this.height = height;
        this.imageHandle = imageHandle;
        this.memory = memory;
        this.view = view;
    }

    public static VulkanImage create(VulkanLogicalDevice logicalDevice, int width, int height, int format, int usage, int aspectMask) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image size must be positive");
        }

        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Image: only a handle with a size, format and usage, no memory behind it yet

            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().width(width).height(height).depth(1);

            LongBuffer pImage = stack.mallocLong(1);

            int result = vkCreateImage(device, imageInfo, null, pImage);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create image: " + result);
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
                throw new IllegalStateException("Failed to allocate image memory: " + result);
            }

            long memory = pMemory.get(0);

            result = vkBindImageMemory(device, image, memory, 0);
            if (result != VK_SUCCESS) {
                vkFreeMemory(device, memory, null);
                vkDestroyImage(device, image, null);
                throw new IllegalStateException("Failed to bind image memory: " + result);
            }

            VulkanImageView view;
            try {
                view = VulkanImageView.create(logicalDevice, image, format, aspectMask);
            } catch (RuntimeException e) {
                vkFreeMemory(device, memory, null);
                vkDestroyImage(device, image, null);
                throw e;
            }

            return new VulkanImage(device, width, height, image, memory, view);
        }
    }

    // Fills a colour image through a staging buffer: UNDEFINED -> copy destination, copy, -> readable by shaders.
    // Blocks until the GPU is done (see VulkanCommandPool.submitOneTime), so use it for startup data.
    public void upload(VulkanLogicalDevice logicalDevice, VulkanCommandPool commandPool, ByteBuffer pixels) {
        VulkanBuffer staging = VulkanBuffer.createHostVisible(logicalDevice, pixels, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);

        try {
            commandPool.submitOneTime(logicalDevice.getGraphicsQueue(), commandBuffer -> {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    VulkanImageBarrier.transition(
                            commandBuffer, imageHandle, VK_IMAGE_ASPECT_COLOR_BIT,
                            VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            VK_PIPELINE_STAGE_2_NONE, VK_ACCESS_2_NONE,
                            VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
                            stack
                    );

                    // bufferRowLength and bufferImageHeight 0 = the pixels are tightly packed
                    VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack)
                            .bufferOffset(0)
                            .bufferRowLength(0)
                            .bufferImageHeight(0);
                    region.imageSubresource()
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(0)
                            .baseArrayLayer(0)
                            .layerCount(1);
                    region.imageOffset().set(0, 0, 0);
                    region.imageExtent().set(width, height, 1);

                    vkCmdCopyBufferToImage(commandBuffer, staging.getHandle(), imageHandle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

                    VulkanImageBarrier.transition(
                            commandBuffer, imageHandle, VK_IMAGE_ASPECT_COLOR_BIT,
                            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                            VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
                            VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT, VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                            stack
                    );
                }
            });
        } finally {
            staging.dispose();
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
