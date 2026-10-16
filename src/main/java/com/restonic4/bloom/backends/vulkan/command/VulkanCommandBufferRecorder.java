package com.restonic4.bloom.backends.vulkan.command;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;
import com.restonic4.bloom.backends.vulkan.pipeline.VulkanGraphicsPipeline;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

public final class VulkanCommandBufferRecorder {
    private VulkanCommandBufferRecorder() {}

    public static void record(
            VkCommandBuffer commandBuffer,
            long image,
            long imageView,
            int width,
            int height,
            VulkanGraphicsPipeline pipeline,
            VulkanBuffer vertexBuffer,
            VulkanBuffer indexBuffer,
            int indexCount,
            float[] color
    ) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Rendering extent must be positive");
        }

        if (color.length != 4) {
            throw new IllegalArgumentException("Color must be RGBA");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Begin recording

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default();

            int result = vkBeginCommandBuffer(commandBuffer, beginInfo);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to begin command buffer: " + result);
            }

            // Transition the swapchain image for color rendering

            transitionImage(
                    commandBuffer,
                    image,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    stack
            );

            // Describe the color attachment

            VkClearValue clearValue = VkClearValue.calloc(stack);
            clearValue.color()
                    .float32(0, 0.02f)
                    .float32(1, 0.02f)
                    .float32(2, 0.03f)
                    .float32(3, 1.00f);

            VkRenderingAttachmentInfo.Buffer colorAttachment = VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachment.get(0)
                    .sType$Default()
                    .imageView(imageView)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .clearValue(clearValue);

            VkRect2D renderArea = VkRect2D.calloc(stack);
            renderArea.offset().set(0, 0);
            renderArea.extent().set(width, height);

            VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(stack)
                    .sType$Default()
                    .renderArea(renderArea)
                    .layerCount(1)
                    .pColorAttachments(colorAttachment);
            VkRenderingInfo.ncolorAttachmentCount(renderingInfo.address(), 1);

            // Begin dynamic rendering.

            vkCmdBeginRendering(commandBuffer, renderingInfo);

            // Bind the graphics pipeline

            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getHandle());

            // Set the dynamic viewport

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0)
                    .x(0.0f)
                    .y(0.0f)
                    .width((float) width)
                    .height((float) height)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);

            vkCmdSetViewport(commandBuffer, 0, viewport);

            // Set the dynamic scissor

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);

            scissor.get(0).offset().set(0, 0);
            scissor.get(0).extent().set(width, height);

            vkCmdSetScissor(commandBuffer, 0, scissor);

            // Send the colour to the fragment shader through a push constant

            vkCmdPushConstants(commandBuffer, pipeline.getLayout().getHandle(), VK_SHADER_STAGE_FRAGMENT_BIT, 0, color);

            // Bind the vertex buffer to binding slot 0 of the pipeline's vertex input

            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(vertexBuffer.getHandle()), stack.longs(0L));

            // Bind the index buffer: each index is a 32-bit unsigned int, reading starts at byte 0

            vkCmdBindIndexBuffer(commandBuffer, indexBuffer.getHandle(), 0, VK_INDEX_TYPE_UINT32);

            // Draw the triangles described by the indices

            vkCmdDrawIndexed(commandBuffer, indexCount, 1, 0, 0, 0);

            // End dynamic rendering

            vkCmdEndRendering(commandBuffer);

            // Prepare the image for presentation

            transitionImage(
                    commandBuffer,
                    image,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_NONE,
                    VK_ACCESS_2_NONE,
                    stack
            );

            // Finish recording

            result = vkEndCommandBuffer(commandBuffer);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to end command buffer: " + result);
            }
        }
    }

    private static void transitionImage(
            VkCommandBuffer commandBuffer,
            long image,
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
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
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