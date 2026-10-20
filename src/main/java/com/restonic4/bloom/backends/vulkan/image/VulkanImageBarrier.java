package com.restonic4.bloom.backends.vulkan.image;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

public final class VulkanImageBarrier {
    private VulkanImageBarrier() {}

    // Changes an image's layout and orders the work around it: everything in (srcStage, srcAccess) finishes
    // before anything in (dstStage, dstAccess) starts
    public static void transition(
            VkCommandBuffer commandBuffer,
            long image,
            int aspectMask,
            int oldLayout,
            int newLayout,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess,
            MemoryStack stack
    ) {

        VkImageSubresourceRange.Buffer subresourceRange = VkImageSubresourceRange.calloc(1, stack);
        subresourceRange.get(0)
                .aspectMask(aspectMask)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);

        VkImageMemoryBarrier2.Buffer imageBarrier = VkImageMemoryBarrier2.calloc(1, stack);
        imageBarrier.get(0)
                .sType$Default()
                .srcStageMask(srcStage)
                .srcAccessMask(srcAccess)
                .dstStageMask(dstStage)
                .dstAccessMask(dstAccess)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(subresourceRange.get(0));

        VkDependencyInfo dependencyInfo = VkDependencyInfo.calloc(stack)
                .sType$Default()
                .pImageMemoryBarriers(imageBarrier);

        vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);
    }
}
