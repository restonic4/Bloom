package com.restonic4.bloom.backends.vulkan.image;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;
import com.restonic4.bloom.backends.vulkan.command.VulkanCommandPool;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.swapchain.VulkanImageView;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

public final class VulkanImage implements Disposable {
    private final long allocator;
    private final int width;
    private final int height;
    private final int mipLevels;
    private final int format;
    private final long imageHandle;
    private final long allocation;
    private final VulkanImageView view;

    private VulkanImage(long allocator, int width, int height, int mipLevels, int format, long imageHandle, long allocation, VulkanImageView view) {
        this.allocator = allocator;
        this.width = width;
        this.height = height;
        this.mipLevels = mipLevels;
        this.format = format;
        this.imageHandle = imageHandle;
        this.allocation = allocation;
        this.view = view;
    }

    public static VulkanImage create(VulkanLogicalDevice logicalDevice, int width, int height, int mipLevels, int format, int usage, int aspectMask) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image size must be positive");
        }
        if (mipLevels < 1 || mipLevels > mipLevelsFor(width, height)) {
            throw new IllegalArgumentException("Mip level count must be between 1 and " + mipLevelsFor(width, height));
        }

        long allocator = logicalDevice.getAllocator().getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(mipLevels)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().width(width).height(height).depth(1);

            // The allocator creates the image, finds a memory type for it, allocates and binds, all in one call
            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);

            LongBuffer pImage = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);

            int result = vmaCreateImage(allocator, imageInfo, allocationInfo, pImage, pAllocation, null);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create image: " + result);
            }

            long image = pImage.get(0);
            long allocation = pAllocation.get(0);

            VulkanImageView view;
            try {
                view = VulkanImageView.create(logicalDevice, image, format, aspectMask, mipLevels);
            } catch (RuntimeException e) {
                vmaDestroyImage(allocator, image, allocation);
                throw e;
            }

            return new VulkanImage(allocator, width, height, mipLevels, format, image, allocation, view);
        }
    }

    // Each level is half the size of the one before it, down to 1x1
    public static int mipLevelsFor(int width, int height) {
        return 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
    }

    // Fills a colour image through a staging buffer: UNDEFINED -> copy destination, copy level 0, shrink it into
    // every smaller level, -> readable by shaders. Needs TRANSFER_SRC usage when there is more than one level.
    // Blocks until the GPU is done (see VulkanCommandPool.submitOneTime), so use it for startup data.
    public void upload(VulkanLogicalDevice logicalDevice, VulkanCommandPool commandPool, ByteBuffer pixels) {
        if (mipLevels > 1) {
            requireLinearBlit(logicalDevice);
        }

        VulkanBuffer staging = VulkanBuffer.createHostVisible(logicalDevice, pixels, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);

        try {
            commandPool.submitOneTime(logicalDevice.getGraphicsQueue(), commandBuffer -> {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    VulkanImageBarrier.transition(
                            commandBuffer, imageHandle, VK_IMAGE_ASPECT_COLOR_BIT, 0, mipLevels,
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

                    int levelWidth = width;
                    int levelHeight = height;

                    for (int level = 1; level < mipLevels; level++) {
                        // The level just written becomes the blit source for the next one
                        VulkanImageBarrier.transition(
                                commandBuffer, imageHandle, VK_IMAGE_ASPECT_COLOR_BIT, level - 1, 1,
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
                                VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_READ_BIT,
                                stack
                        );

                        int nextWidth = Math.max(1, levelWidth / 2);
                        int nextHeight = Math.max(1, levelHeight / 2);

                        VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
                        blit.srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level - 1).baseArrayLayer(0).layerCount(1);
                        blit.srcOffsets(0).set(0, 0, 0);
                        blit.srcOffsets(1).set(levelWidth, levelHeight, 1);
                        blit.dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level).baseArrayLayer(0).layerCount(1);
                        blit.dstOffsets(0).set(0, 0, 0);
                        blit.dstOffsets(1).set(nextWidth, nextHeight, 1);

                        vkCmdBlitImage(
                                commandBuffer,
                                imageHandle, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                imageHandle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                blit, VK_FILTER_LINEAR
                        );

                        levelWidth = nextWidth;
                        levelHeight = nextHeight;
                    }

                    // Every level but the last was turned into a blit source; the last is still a copy destination
                    if (mipLevels > 1) {
                        VulkanImageBarrier.transition(
                                commandBuffer, imageHandle, VK_IMAGE_ASPECT_COLOR_BIT, 0, mipLevels - 1,
                                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                                VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_READ_BIT,
                                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT, VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                                stack
                        );
                    }

                    VulkanImageBarrier.transition(
                            commandBuffer, imageHandle, VK_IMAGE_ASPECT_COLOR_BIT, mipLevels - 1, 1,
                            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                            VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
                            VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT, VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                            stack
                    );
                }
            });
        } finally {
            staging.dispose();
        }
    }

    private void requireLinearBlit(VulkanLogicalDevice logicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.malloc(stack);
            vkGetPhysicalDeviceFormatProperties(logicalDevice.getHandle().getPhysicalDevice(), format, properties);

            int required = VK_FORMAT_FEATURE_BLIT_SRC_BIT | VK_FORMAT_FEATURE_BLIT_DST_BIT | VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
            if ((properties.optimalTilingFeatures() & required) != required) {
                throw new IllegalStateException("Format " + format + " does not support linear blits, so it cannot generate mipmaps");
            }
        }
    }

    public long getImageHandle() { return imageHandle; }
    public long getViewHandle() { return view.getHandle(); }

    @Override
    public void dispose() {
        view.dispose();
        vmaDestroyImage(allocator, imageHandle, allocation);
    }
}
