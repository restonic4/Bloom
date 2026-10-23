package com.restonic4.unlock;

import com.restonic4.bloom.api.events.WindowEvents;
import com.restonic4.bloom.backends.glfw.GlfwWindow;
import com.restonic4.bloom.events.EventResult;
import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;
import com.restonic4.bloom.backends.vulkan.camera.VulkanClipSpace;
import com.restonic4.bloom.backends.vulkan.command.VulkanCommandBufferRecorder;
import com.restonic4.bloom.backends.vulkan.command.VulkanCommandPool;
import com.restonic4.bloom.backends.vulkan.command.VulkanMeshDraw;
import com.restonic4.bloom.backends.vulkan.descriptor.VulkanDescriptorBinding;
import com.restonic4.bloom.backends.vulkan.descriptor.VulkanDescriptorPool;
import com.restonic4.bloom.backends.vulkan.descriptor.VulkanDescriptorSetLayout;
import com.restonic4.bloom.backends.vulkan.descriptor.VulkanDescriptorWriter;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.image.VulkanDepthImage;
import com.restonic4.bloom.backends.vulkan.device.VulkanPhysicalDevice;
import com.restonic4.bloom.backends.vulkan.instance.VulkanInstance;
import com.restonic4.bloom.backends.vulkan.material.VulkanMaterialLibrary;
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
import com.restonic4.bloom.core.graphics.GraphicsApi;
import com.restonic4.bloom.core.graphics.ShaderSource;
import com.restonic4.bloom.core.graphics.ShaderStage;
import com.restonic4.bloom.core.platform.Version;
import com.restonic4.bloom.core.platform.WindowSystem;
import com.restonic4.bloom.core.math.Time;
import com.restonic4.bloom.core.scene.MeshData;
import com.restonic4.bloom.core.scene.MeshInstance;
import org.joml.Matrix4f;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
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

    private static final Matrix4f viewProjection = new Matrix4f();

    public static void init() {
        GlfwWindow window;
        if ("x11".equalsIgnoreCase(System.getProperty("engine.windowing", ""))) {
            window = new GlfwWindow(GraphicsApi.VULKAN, 500, 500, WindowSystem.X11);
        } else {
            window = new GlfwWindow(GraphicsApi.VULKAN, 500, 500);
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

        VulkanDepthImage depthImage = VulkanDepthImage.create(
                logicalDevice,
                swapChainTarget.getSwapChain().getImageWidth(),
                swapChainTarget.getSwapChain().getImageHeight()
        );

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

        VulkanCommandPool commandPool = VulkanCommandPool.create(logicalDevice);

        // Set 0 (per frame slot): the camera buffer. Set 1 (per material): the texture, owned by the material library
        VulkanDescriptorSetLayout cameraSetLayout = VulkanDescriptorSetLayout.create(
                logicalDevice,
                new VulkanDescriptorBinding(0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT)
        );
        VulkanMaterialLibrary materials = new VulkanMaterialLibrary(logicalDevice, commandPool);
        VulkanPipelineLayout pipelineLayout = VulkanPipelineLayout.create(logicalDevice, VK_SHADER_STAGE_FRAGMENT_BIT, 4 * Float.BYTES, cameraSetLayout, materials.getSetLayout());

        // One set per frame slot, each will point at that slot's camera buffer
        VulkanDescriptorPool descriptorPool = VulkanDescriptorPool.create(logicalDevice, FRAMES_IN_FLIGHT, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
        long[] cameraSets = descriptorPool.allocateSets(cameraSetLayout, FRAMES_IN_FLIGHT);

        // One camera buffer per frame slot (the CPU rewrites it every frame), each plugged into its slot's set
        long cameraBufferSize = 16 * Float.BYTES;
        List<VulkanBuffer> cameraBuffers = new ArrayList<>(FRAMES_IN_FLIGHT);
        for (int i = 0; i < FRAMES_IN_FLIGHT; i++) {
            VulkanBuffer cameraBuffer = VulkanBuffer.create(
                    logicalDevice,
                    cameraBufferSize,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );
            cameraBuffers.add(cameraBuffer);
            VulkanDescriptorWriter.writeUniformBuffer(logicalDevice, cameraSets[i], 0, cameraBuffer, cameraBufferSize);
        }
        VulkanGraphicsPipeline graphicsPipeline = VulkanGraphicsPipeline.create(logicalDevice, pipelineLayout, swapChainTarget.getSwapChain().getImageFormat(), VulkanDepthImage.FORMAT, vertexStage, fragmentStage);

        TestScene scene = new TestScene(window);

        // All instance matrices live in one buffer: the cubes first, then the plane
        int cubeCount = scene.instances().size();
        int planeCount = scene.planeInstances().size();

        List<VulkanMeshDraw> draws = List.of(
                uploadMesh(logicalDevice, commandPool, scene.mesh(), 0, cubeCount, materials.setFor(scene.cubeMaterial())),
                uploadMesh(logicalDevice, commandPool, scene.planeMesh(), cubeCount, planeCount, materials.setFor(scene.planeMaterial()))
        );

        // One instance buffer per frame slot: the CPU rewrites it every frame while the GPU may still read the other slot's
        List<VulkanBuffer> instanceBuffers = new ArrayList<>(FRAMES_IN_FLIGHT);
        for (int i = 0; i < FRAMES_IN_FLIGHT; i++) {
            instanceBuffers.add(VulkanBuffer.create(
                    logicalDevice,
                    (long) (cubeCount + planeCount) * 16 * Float.BYTES,
                    VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            ));
        }

        Time time = new Time();

        List<VkCommandBuffer> commandBuffers = commandPool.allocatePrimaryCommandBuffers(FRAMES_IN_FLIGHT);

        VulkanFrameSync frameSync = VulkanFrameSync.create(logicalDevice, swapChainTarget.getSwapChain().getImageHandles().length, FRAMES_IN_FLIGHT);

        WindowEvents.RESIZED.register((w, oldWidth, oldHeight, newWidth, newHeight) -> {
            resizeRequested = true;
            return EventResult.CONTINUE;
        });

        int frameIndex = 0;

        while (!window.shouldClose()) {
            window.pollEvents();

            time.update();
            scene.update(time.delta());

            if (resizeRequested) {
                window.waitForNonZeroFramebuffer();
                if (window.shouldClose()) break;

                boolean formatChanged = swapChainTarget.recreate(window.getHandle());

                depthImage.dispose();
                depthImage = VulkanDepthImage.create(
                        logicalDevice,
                        swapChainTarget.getSwapChain().getImageWidth(),
                        swapChainTarget.getSwapChain().getImageHeight()
                );

                if (formatChanged) {
                    graphicsPipeline.dispose();
                    graphicsPipeline = VulkanGraphicsPipeline.create(logicalDevice, pipelineLayout, swapChainTarget.getSwapChain().getImageFormat(), VulkanDepthImage.FORMAT, vertexStage, fragmentStage);
                }
                frameSync.recreateRenderFinished(swapChainTarget.getSwapChain().getImageHandles().length);
                resizeRequested = false;
            }

            if (!drawFrame(logicalDevice, swapChainTarget, graphicsPipeline, depthImage, draws, instanceBuffers, cameraBuffers, cameraSets, scene, time.elapsedSeconds(), commandBuffers, frameSync, frameIndex)) {
                resizeRequested = true;
            }

            frameIndex = (frameIndex + 1) % FRAMES_IN_FLIGHT;
        }

        int idleResult = vkDeviceWaitIdle(logicalDevice.getHandle());
        if (idleResult != VK_SUCCESS) {
            throw new IllegalStateException("Failed to wait for device idle: " + idleResult);
        }

        instanceBuffers.forEach(VulkanBuffer::dispose);
        cameraBuffers.forEach(VulkanBuffer::dispose);
        for (VulkanMeshDraw draw : draws) {
            draw.indexBuffer().dispose();
            draw.vertexBuffer().dispose();
        }
        depthImage.dispose();

        graphicsPipeline.dispose();
        pipelineLayout.dispose();
        materials.dispose();
        descriptorPool.dispose();
        cameraSetLayout.dispose();

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

    // Mesh buffers are device-local, filled through staging
    private static VulkanMeshDraw uploadMesh(VulkanLogicalDevice logicalDevice, VulkanCommandPool commandPool, MeshData mesh, int firstInstance, int instanceCount, long materialSet) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            float[] vertices = mesh.interleavedVertices();
            ByteBuffer vertexData = stack.malloc(vertices.length * Float.BYTES);
            vertexData.asFloatBuffer().put(vertices);
            VulkanBuffer vertexBuffer = VulkanBuffer.createDeviceLocal(logicalDevice, commandPool, vertexData, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);

            ByteBuffer indexData = stack.malloc(mesh.indices().length * Integer.BYTES);
            indexData.asIntBuffer().put(mesh.indices());
            VulkanBuffer indexBuffer = VulkanBuffer.createDeviceLocal(logicalDevice, commandPool, indexData, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);

            return new VulkanMeshDraw(vertexBuffer, indexBuffer, mesh.indexCount(), firstInstance, instanceCount, materialSet);
        }
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
            VulkanDepthImage depthImage,
            List<VulkanMeshDraw> draws,
            List<VulkanBuffer> instanceBuffers,
            List<VulkanBuffer> cameraBuffers,
            long[] cameraSets,
            TestScene scene,
            double elapsedSeconds,
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

            // Write this frame's model matrices into the slot's instance buffer
            // (free to overwrite: waitForFrameSlot() waited for the last frame that used it)

            List<MeshInstance> instances = new ArrayList<>(scene.instances());
            instances.addAll(scene.planeInstances());
            ByteBuffer instanceData = stack.malloc(instances.size() * 16 * Float.BYTES);
            FloatBuffer instanceFloats = instanceData.asFloatBuffer();
            for (int i = 0; i < instances.size(); i++) {
                instances.get(i).getTransform().getModelMatrix().get(i * 16, instanceFloats);
            }
            instanceBuffers.get(frameIndex).write(instanceData);

            float aspectRatio = (float) swapChain.getImageWidth() / swapChain.getImageHeight();
            VulkanClipSpace.viewProjection(scene.camera(), aspectRatio, viewProjection);

            // Write it into this slot's camera buffer (same reasoning as the instance buffer: the slot is free to overwrite)
            ByteBuffer cameraData = stack.malloc(16 * Float.BYTES);
            viewProjection.get(0, cameraData);
            cameraBuffers.get(frameIndex).write(cameraData);

            VulkanCommandBufferRecorder.record(
                    commandBuffer,
                    swapChainImages[imageIndex],
                    imageViews.get(imageIndex).getHandle(),
                    swapChain.getImageWidth(),
                    swapChain.getImageHeight(),
                    graphicsPipeline,
                    depthImage,
                    draws,
                    instanceBuffers.get(frameIndex),
                    cameraSets[frameIndex],
                    scene.color(elapsedSeconds)
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
