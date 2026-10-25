package com.restonic4.bloom.backends.vulkan.surface;

import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkInstance;

import java.nio.LongBuffer;

import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public class VulkanSurface implements Disposable {
    private final VkInstance instance;
    private final long handle;

    private VulkanSurface(VkInstance instance, long handle) {
        this.instance = instance;
        this.handle = handle;
    }

    public static VulkanSurface create(VkInstance instance, long window) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.longs(VK_NULL_HANDLE);

            int result = glfwCreateWindowSurface(instance, window, null, pSurface);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create window surface: " + result);
            }

            return new VulkanSurface(instance, pSurface.get(0));
        }
    }

    public long getHandle() { return handle; }

    @Override
    public void dispose() {
        vkDestroySurfaceKHR(instance, handle, null);
    }
}