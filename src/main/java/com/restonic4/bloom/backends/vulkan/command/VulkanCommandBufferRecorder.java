package com.restonic4.bloom.backends.vulkan.command;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;
import com.restonic4.bloom.backends.vulkan.image.VulkanDepthImage;
import com.restonic4.bloom.backends.vulkan.image.VulkanImageBarrier;
import com.restonic4.bloom.backends.vulkan.pipeline.VulkanGraphicsPipeline;
import com.restonic4.bloom.backends.vulkan.pipeline.VulkanPipelineLayout;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

public final class VulkanCommandBufferRecorder {
    private static final long DEPTH_TEST_STAGES = VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT;

    private VulkanCommandBufferRecorder() {}

    public static void record(
            VkCommandBuffer commandBuffer,
            long image,
            long imageView,
            int width,
            int height,
            VulkanGraphicsPipeline pipeline,
            VulkanDepthImage depthImage,
            List<VulkanMeshDraw> draws,
            VulkanBuffer instanceBuffer,
            long descriptorSet,
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

            VulkanImageBarrier.transition(
                    commandBuffer,
                    image,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    stack
            );

            // Transition the depth image. The same image is shared by every frame in flight, so the source
            // is a depth write (not NONE): the previous frame's depth work must finish before this frame's starts.

            VulkanImageBarrier.transition(
                    commandBuffer,
                    depthImage.getImageHandle(),
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                    DEPTH_TEST_STAGES,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    DEPTH_TEST_STAGES,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
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

            // Describe the depth attachment: cleared to 1.0 (the far plane), not needed after rendering

            VkClearValue depthClearValue = VkClearValue.calloc(stack);
            depthClearValue.depthStencil()
                    .depth(1.0f)
                    .stencil(0);

            VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(stack)
                    .sType$Default()
                    .imageView(depthImage.getViewHandle())
                    .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .clearValue(depthClearValue);

            VkRect2D renderArea = VkRect2D.calloc(stack);
            renderArea.offset().set(0, 0);
            renderArea.extent().set(width, height);

            VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(stack)
                    .sType$Default()
                    .renderArea(renderArea)
                    .layerCount(1)
                    .pColorAttachments(colorAttachment)
                    .pDepthAttachment(depthAttachment);
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

            // Push constants: the colour (4 floats), same as the fragment shader block

            FloatBuffer pushData = stack.mallocFloat(4);
            pushData.put(0, color);

            VulkanPipelineLayout layout = pipeline.getLayout();
            vkCmdPushConstants(commandBuffer, layout.getHandle(), layout.getPushConstantStages(), 0, pushData);

            // Bind the descriptor set (camera buffer) to set 0 of the pipeline layout

            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, layout.getHandle(), 0, stack.longs(descriptorSet), null);

            for (VulkanMeshDraw draw : draws) {
                // Bind the mesh's vertex buffer to binding 0 and the shared instance buffer to binding 1 of the pipeline's vertex input

                vkCmdBindVertexBuffers(
                        commandBuffer,
                        0,
                        stack.longs(draw.vertexBuffer().getHandle(), instanceBuffer.getHandle()),
                        stack.longs(0L, 0L)
                );

                // Bind the index buffer: each index is a 32-bit unsigned int, reading starts at byte 0

                vkCmdBindIndexBuffer(commandBuffer, draw.indexBuffer().getHandle(), 0, VK_INDEX_TYPE_UINT32);

                // Draw the triangles described by the indices. firstInstance skips to this mesh's matrices in the instance buffer

                vkCmdDrawIndexed(commandBuffer, draw.indexCount(), draw.instanceCount(), 0, 0, draw.firstInstance());
            }

            // End dynamic rendering

            vkCmdEndRendering(commandBuffer);

            // Prepare the image for presentation

            VulkanImageBarrier.transition(
                    commandBuffer,
                    image,
                    VK_IMAGE_ASPECT_COLOR_BIT,
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
}
