package com.restonic4.unlock;

import com.restonic4.bloom.api.events.WindowEvents;
import com.restonic4.bloom.backends.glfw.GlfwWindow;
import com.restonic4.bloom.events.EventResult;
import com.restonic4.bloom.backends.vulkan.command.VulkanCommandBufferRecorder;
import com.restonic4.bloom.backends.vulkan.command.VulkanCommandPool;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.device.VulkanPhysicalDevice;
import com.restonic4.bloom.backends.vulkan.instance.VulkanInstance;
import com.restonic4.bloom.backends.vulkan.pipeline.VulkanGraphicsPipeline;
import com.restonic4.bloom.backends.vulkan.pipeline.VulkanPipelineLayout;
import com.restonic4.bloom.backends.vulkan.shader.VulkanGlslCompiler;
import com.restonic4.bloom.backends.vulkan.shader.VulkanShader;
import com.restonic4.bloom.backends.vulkan.shader.VulkanShaderStage;
import com.restonic4.bloom.backends.vulkan.surface.VulkanSurface;
import com.restonic4.bloom.backends.vulkan.swapchain.VulkanSwapChain;
import com.restonic4.bloom.backends.vulkan.swapchain.VulkanSwapChainTarget;
import com.restonic4.bloom.backends.vulkan.swapchain.VulkanSwapChainImageViews;
import com.restonic4.bloom.backends.vulkan.sync.VulkanFrameSync;
import com.restonic4.bloom.core.graphics.ShaderSource;
import com.restonic4.bloom.core.graphics.ShaderStage;
import com.restonic4.bloom.core.platform.Version;
import com.restonic4.bloom.core.platform.WindowSystem;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

public class VulkanTest {
    private static final boolean ENABLE_VALIDATION_LAYERS = Configuration.DEBUG.get(true);
    private static final int FRAMES_IN_FLIGHT = 2;

    // Set by the window resize event, read by the render loop.
    private static boolean resizeRequested = false;

    public static void init() {
        GlfwWindow window;
        if ("x11".equalsIgnoreCase(System.getProperty("engine.windowing", ""))) {
            window = new GlfwWindow(500, 500, WindowSystem.X11);
        } else {
            window = new GlfwWindow(500, 500);
        }

        VulkanInstance vkInstance = VulkanInstance.create(
                "Bloom", new Version(0, 0, 0),
                "Unlock", new Version(0, 0, 0),
                ENABLE_VALIDATION_LAYERS
        );

        VulkanSurface surface = VulkanSurface.create(vkInstance.getInstance(), window.getHandle());
        VulkanPhysicalDevice physicalDevice = VulkanPhysicalDevice.pickBestDevice(vkInstance.getInstance(), surface.getHandle());
        VulkanLogicalDevice logicalDevice = VulkanLogicalDevice.create(physicalDevice, surface.getHandle());

        VulkanSwapChainTarget swapChainTarget = VulkanSwapChainTarget.create(physicalDevice, logicalDevice, surface.getHandle(), window.getHandle());

        VulkanGlslCompiler compiler = new VulkanGlslCompiler();

        VulkanShader vertexShader = null;
        VulkanShader fragmentShader = null;

        try {
            vertexShader = VulkanShader.create(logicalDevice, compiler, ShaderSource.from("triangle.vert", ShaderStage.VERTEX));
            fragmentShader = VulkanShader.create(logicalDevice, compiler, ShaderSource.from("triangle.frag", ShaderStage.FRAGMENT));
        } catch (RuntimeException e) {
            if (fragmentShader != null) fragmentShader.dispose();
            if (vertexShader != null) vertexShader.dispose();
            throw e;
        } finally {
            compiler.dispose();
        }

        VulkanShaderStage vertexStage = new VulkanShaderStage(vertexShader, VK_SHADER_STAGE_VERTEX_BIT);
        VulkanShaderStage fragmentStage = new VulkanShaderStage(fragmentShader, VK_SHADER_STAGE_FRAGMENT_BIT);

        VulkanPipelineLayout pipelineLayout = VulkanPipelineLayout.create(logicalDevice, VK_SHADER_STAGE_FRAGMENT_BIT, 4 * Float.BYTES);
        VulkanGraphicsPipeline graphicsPipeline = VulkanGraphicsPipeline.create(logicalDevice, pipelineLayout, swapChainTarget.getSwapChain().getImageFormat(), vertexStage, fragmentStage);

        VulkanCommandPool commandPool = VulkanCommandPool.create(logicalDevice);
        List<VkCommandBuffer> commandBuffers = commandPool.allocatePrimaryCommandBuffers(FRAMES_IN_FLIGHT);

        VulkanFrameSync frameSync = VulkanFrameSync.create(logicalDevice, swapChainTarget.getSwapChain().getImageHandles().length, FRAMES_IN_FLIGHT);

        WindowEvents.RESIZED.register((w, oldWidth, oldHeight, newWidth, newHeight) -> {
            resizeRequested = true;
            return EventResult.CONTINUE;
        });

        int frameIndex = 0;

        while (!window.shouldClose()) {
            glfwPollEvents();

            if (resizeRequested) {
                window.waitForNonZeroFramebuffer();
                if (window.shouldClose()) break;

                boolean formatChanged = swapChainTarget.recreate(window.getHandle());
                if (formatChanged) {
                    graphicsPipeline.dispose();
                    graphicsPipeline = VulkanGraphicsPipeline.create(logicalDevice, pipelineLayout, swapChainTarget.getSwapChain().getImageFormat(), vertexStage, fragmentStage);
                }
                frameSync.recreateRenderFinished(swapChainTarget.getSwapChain().getImageHandles().length);
                resizeRequested = false;
            }

            if (!drawFrame(logicalDevice, swapChainTarget, graphicsPipeline, commandBuffers, frameSync, frameIndex)) {
                resizeRequested = true;
            }

            frameIndex = (frameIndex + 1) % FRAMES_IN_FLIGHT;
        }

        int idleResult = vkDeviceWaitIdle(logicalDevice.getHandle());
        if (idleResult != VK_SUCCESS) {
            throw new IllegalStateException("Failed to wait for device idle: " + idleResult);
        }

        graphicsPipeline.dispose();
        pipelineLayout.dispose();

        vertexShader.dispose();
        fragmentShader.dispose();

        commandPool.dispose();
        frameSync.dispose();

        swapChainTarget.dispose();

        logicalDevice.dispose();
        surface.dispose();
        vkInstance.dispose();

        window.dispose();
        glfwTerminate();
    }

    /**
     * <p>An example run (the image numbers are made up, since the system chooses them):</p>
     *
     * <table style="border-collapse: collapse;">
     *   <tr>
     *     <th style="border: 1px solid gray; padding: 4px 8px; text-align: left;">frame</th>
     *     <th style="border: 1px solid gray; padding: 4px 8px; text-align: left;">frame index</th>
     *     <th style="border: 1px solid gray; padding: 4px 8px; text-align: left;">image index from acquire</th>
     *   </tr>
     *   <tr><td style="border: 1px solid gray; padding: 4px 8px;">0</td><td style="border: 1px solid gray; padding: 4px 8px;">0</td><td style="border: 1px solid gray; padding: 4px 8px;">2</td></tr>
     *   <tr><td style="border: 1px solid gray; padding: 4px 8px;">1</td><td style="border: 1px solid gray; padding: 4px 8px;">1</td><td style="border: 1px solid gray; padding: 4px 8px;">0</td></tr>
     *   <tr><td style="border: 1px solid gray; padding: 4px 8px;">2</td><td style="border: 1px solid gray; padding: 4px 8px;">0</td><td style="border: 1px solid gray; padding: 4px 8px;">1</td></tr>
     * </table>
     */
    private static boolean drawFrame(
            VulkanLogicalDevice logicalDevice,
            VulkanSwapChainTarget swapChainTarget,
            VulkanGraphicsPipeline graphicsPipeline,
            List<VkCommandBuffer> commandBuffers,
            VulkanFrameSync frameSync,
            int frameIndex
    ) {
        frameSync.waitForFrameSlot(FRAMES_IN_FLIGHT);

        VulkanSwapChain swapChain = swapChainTarget.getSwapChain();
        long[] swapChainImages = swapChain.getImageHandles();
        VulkanSwapChainImageViews imageViews = swapChainTarget.getImageViews();

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Acquire the next image

            IntBuffer pImageIndex = stack.ints(0);

            int acquireResult = vkAcquireNextImageKHR(
                    logicalDevice.getHandle(),
                    swapChain.getHandle(),
                    -1L,
                    frameSync.getImageAvailableSemaphore(frameIndex),
                    VK_NULL_HANDLE,
                    pImageIndex
            );

            if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                return false;
            }

            if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
                throw new IllegalStateException("Failed to acquire swapchain image: " + acquireResult);
            }

            int imageIndex = pImageIndex.get(0);

            if (imageIndex < 0 || imageIndex >= swapChainImages.length) {
                throw new IllegalStateException("Invalid acquired image index: " + imageIndex);
            }

            // Record this frame's commands. The slot's command buffer is free again, because
            // waitForFrameSlot() above waited for the frame that last used this slot.
            // vkBeginCommandBuffer resets it, since the pool has RESET_COMMAND_BUFFER_BIT.

            VkCommandBuffer commandBuffer = commandBuffers.get(frameIndex);

            // A colour that changes over time: proves the commands are re-recorded every frame
            float time = (float) glfwGetTime();
            float[] color = {
                    0.5f + 0.5f * (float) Math.sin(time),
                    0.5f + 0.5f * (float) Math.sin(time + 2.0f),
                    0.5f + 0.5f * (float) Math.sin(time + 4.0f),
                    1.0f
            };

            VulkanCommandBufferRecorder.record(
                    commandBuffer,
                    swapChainImages[imageIndex],
                    imageViews.get(imageIndex).getHandle(),
                    swapChain.getImageWidth(),
                    swapChain.getImageHeight(),
                    graphicsPipeline,
                    color
            );

            // Prepare the semaphore and timeline signal values

            long renderFinished = frameSync.getRenderFinishedSemaphore(imageIndex);

            long timelineValue = frameSync.nextTimelineValue();

            // Wait for image acquisition before color attachment access

            VkSemaphoreSubmitInfo.Buffer waitInfos = VkSemaphoreSubmitInfo.calloc(1, stack);
            waitInfos.get(0)
                    .sType$Default()
                    .semaphore(frameSync.getImageAvailableSemaphore(frameIndex))
                    .value(0L)
                    .stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .deviceIndex(0);

            // Specify the recorded command buffer.

            VkCommandBufferSubmitInfo.Buffer commandBufferInfos = VkCommandBufferSubmitInfo.calloc(1, stack);
            commandBufferInfos.get(0)
                    .sType$Default()
                    .commandBuffer(commandBuffer)
                    .deviceMask(0);

            // Signal presentation readiness and GPU completion

            VkSemaphoreSubmitInfo.Buffer signalInfos = VkSemaphoreSubmitInfo.calloc(2, stack);
            signalInfos.get(0)
                    .sType$Default()
                    .semaphore(renderFinished)
                    .value(0L) // Binary semaphore: value must be zero.
                    .stageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                    .deviceIndex(0);

            signalInfos.get(1)
                    .sType$Default()
                    .semaphore(frameSync.getTimelineSemaphore())
                    .value(timelineValue)
                    .stageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                    .deviceIndex(0);

            VkSubmitInfo2.Buffer submitInfos = VkSubmitInfo2.calloc(1, stack);
            submitInfos.get(0)
                    .sType$Default()
                    .pWaitSemaphoreInfos(waitInfos)
                    .pCommandBufferInfos(commandBufferInfos)
                    .pSignalSemaphoreInfos(signalInfos);

            int submitResult = vkQueueSubmit2(logicalDevice.getGraphicsQueue(), submitInfos, VK_NULL_HANDLE);
            if (submitResult != VK_SUCCESS) {
                throw new IllegalStateException("Failed to submit graphics commands: " + submitResult);
            }

            frameSync.markSubmitted(timelineValue);

            // Present the image, waiting for rendering to finish

            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(renderFinished))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapChain.getHandle()))
                    .pImageIndices(stack.ints(imageIndex));

            int presentResult = vkQueuePresentKHR(logicalDevice.getPresentQueue(), presentInfo);
            if (presentResult == VK_ERROR_OUT_OF_DATE_KHR
                    || presentResult == VK_SUBOPTIMAL_KHR
                    || acquireResult == VK_SUBOPTIMAL_KHR) {
                return false;
            }

            if (presentResult != VK_SUCCESS) {
                throw new IllegalStateException("Failed to present swapchain image: " + presentResult);
            }

            return true;
        }
    }
}
