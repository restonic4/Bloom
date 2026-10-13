package com.restonic4.unlock;

import com.restonic4.bloom.backends.glfw.GlfwWindow;
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
import com.restonic4.bloom.core.graphics.ShaderSource;
import com.restonic4.bloom.core.graphics.ShaderStage;
import com.restonic4.bloom.core.platform.Version;
import org.lwjgl.system.Configuration;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;

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

        while (!window.shouldClose()) {
            glfwPollEvents();
        }

        graphicsPipeline.dispose();
        vertexShader.dispose();
        fragmentShader.dispose();
        pipelineLayout.dispose();
        imageViews.dispose();
        swapChain.dispose();
        logicalDevice.dispose();
        surface.dispose();
        vkInstance.dispose();

        window.dispose();
        glfwTerminate();
    }
}
