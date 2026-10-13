package com.restonic4.unlock;

import com.restonic4.bloom.backends.glfw.GlfwWindow;
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
import com.restonic4.bloom.backends.vulkan.swapchain.VulkanSwapChainImageViews;
import com.restonic4.bloom.backends.vulkan.sync.VulkanFrameSync;
import com.restonic4.bloom.core.graphics.ShaderSource;
import com.restonic4.bloom.core.graphics.ShaderStage;
import com.restonic4.bloom.core.platform.Version;
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

    public static void init() {
        GlfwWindow window = new GlfwWindow(500, 500);

        VulkanInstance vkInstance = VulkanInstance.create(
                "Bloom", new Version(0, 0, 0),
                "Unlock", new Version(0, 0, 0),
                ENABLE_VALIDATION_LAYERS
        );

        VulkanSurface surface = VulkanSurface.create(vkInstance.getInstance(), window.getHandle());
        VulkanPhysicalDevice physicalDevice = VulkanPhysicalDevice.pickBestDevice(vkInstance.getInstance(), surface.getHandle());
        VulkanLogicalDevice logicalDevice = VulkanLogicalDevice.create(physicalDevice, surface.getHandle());

        VulkanSwapChain swapChain = VulkanSwapChain.create(physicalDevice, logicalDevice, surface.getHandle(), window.getHandle());
        VulkanSwapChainImageViews imageViews = VulkanSwapChainImageViews.create(logicalDevice, swapChain);

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

        VulkanPipelineLayout pipelineLayout = VulkanPipelineLayout.create(logicalDevice);
        VulkanGraphicsPipeline graphicsPipeline = VulkanGraphicsPipeline.create(logicalDevice, pipelineLayout, swapChain.getImageFormat(), vertexStage, fragmentStage);

        VulkanCommandPool commandPool = VulkanCommandPool.create(logicalDevice);
        List<VkCommandBuffer> commandBuffers = commandPool.allocatePrimaryCommandBuffers(imageViews.size());

        long[] swapChainImages = swapChain.getImageHandles();

        if (swapChainImages.length != commandBuffers.size() || imageViews.size() != commandBuffers.size()) {
            throw new IllegalStateException("Swapchain image, view, and command buffer counts differ");
        }

        for (int i = 0; i < commandBuffers.size(); i++) {
            VulkanCommandBufferRecorder.record(
                    commandBuffers.get(i),
                    swapChainImages[i],
                    imageViews.get(i).getHandle(),
                    swapChain.getImageWidth(),
                    swapChain.getImageHeight(),
                    graphicsPipeline
            );
        }

        VulkanFrameSync frameSync = VulkanFrameSync.create(logicalDevice, swapChain.getImageHandles().length);

        while (!window.shouldClose()) {
            glfwPollEvents();

            if (!drawFrame(logicalDevice, swapChain, commandBuffers, frameSync)) {
                System.out.println("Swapchain recreation is required; stopping renderer.");
                window.setShouldClose(true);
            }
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

        imageViews.dispose();
        swapChain.dispose();

        logicalDevice.dispose();
        surface.dispose();
        vkInstance.dispose();

        window.dispose();
        glfwTerminate();
    }


    private static boolean drawFrame(
            VulkanLogicalDevice logicalDevice,
            VulkanSwapChain swapChain,
            List<VkCommandBuffer> commandBuffers,
            VulkanFrameSync frameSync
    ) {
        frameSync.waitForLastSubmission();

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Acquire the next image

            IntBuffer pImageIndex = stack.ints(0);

            int acquireResult = vkAcquireNextImageKHR(
                    logicalDevice.getHandle(),
                    swapChain.getHandle(),
                    -1L,
                    frameSync.getImageAvailableSemaphore(),
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

            if (imageIndex < 0 || imageIndex >= commandBuffers.size()) {
                throw new IllegalStateException("Invalid acquired image index: " + imageIndex);
            }

            // Prepare the semaphore and timeline signal values

            long renderFinished = frameSync.getRenderFinishedSemaphore(imageIndex);

            long timelineValue = frameSync.nextTimelineValue();

            // Wait for image acquisition before color attachment access

            VkSemaphoreSubmitInfo.Buffer waitInfos = VkSemaphoreSubmitInfo.calloc(1, stack);
            waitInfos.get(0)
                    .sType$Default()
                    .semaphore(frameSync.getImageAvailableSemaphore())
                    .value(0L)
                    .stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .deviceIndex(0);

            // Specify the recorded command buffer.

            VkCommandBufferSubmitInfo.Buffer commandBufferInfos = VkCommandBufferSubmitInfo.calloc(1, stack);
            commandBufferInfos.get(0)
                    .sType$Default()
                    .commandBuffer(commandBuffers.get(imageIndex))
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

    // Conclusion: I am completely lost at this point
}
