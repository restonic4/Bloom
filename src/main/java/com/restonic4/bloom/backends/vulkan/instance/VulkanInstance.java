package com.restonic4.bloom.backends.vulkan.instance;

import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.platform.Version;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK14.*;

public class VulkanInstance implements Disposable {
    private final VkInstance vkInstance;
    private final long debugMessenger;
    private final VkDebugUtilsMessengerCallbackEXT debugCallback;

    private VulkanInstance(VkInstance vkInstance, long debugMessenger, VkDebugUtilsMessengerCallbackEXT debugCallback) {
        this.vkInstance = vkInstance;
        this.debugMessenger = debugMessenger;
        this.debugCallback = debugCallback;
    }

    public static VulkanInstance create(String engineName, Version engineVersion, String appName, Version appVersion, boolean enableValidationLayers) {
        if (!glfwVulkanSupported()) throw new IllegalStateException("Vulkan is not supported");

        int vkEngineVersion = VK_MAKE_VERSION(engineVersion.getMajor(), engineVersion.getMinor(), engineVersion.getPatch());
        int vkAppVersion = VK_MAKE_VERSION(appVersion.getMajor(), appVersion.getMinor(), appVersion.getPatch());

        // On Arch Linux we need vulkan-validation-layers
        if (enableValidationLayers && !VulkanDebug.checkValidationLayerSupport()) {
            throw new RuntimeException("Validation requested but not supported");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer loaderVersion = stack.ints(0);

            int result = vkEnumerateInstanceVersion(loaderVersion);
            if (result != VK_SUCCESS) throw new IllegalStateException("Failed to query Vulkan loader version: " + result);
            if (loaderVersion.get(0) < VK_API_VERSION_1_4) throw new IllegalStateException("Vulkan 1.4 is required, but the loader does not support it");

            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(stack.UTF8(appName))
                    .applicationVersion(vkAppVersion)
                    .pEngineName(stack.UTF8(engineName))
                    .engineVersion(vkEngineVersion)
                    .apiVersion(VK_API_VERSION_1_4);


            PointerBuffer requiredExtensions = getRequiredExtensions(stack, enableValidationLayers);

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(requiredExtensions);

            VkDebugUtilsMessengerCallbackEXT debugCallback = enableValidationLayers ? VulkanDebug.createCallback() : null;
            try {
                if (enableValidationLayers) {
                    createInfo.ppEnabledLayerNames(VulkanDebug.validationLayersAsPointerBuffer(stack));
                    VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VulkanDebug.createMessengerCreateInfo(stack, debugCallback);
                    createInfo.pNext(debugCreateInfo.address());
                }

                PointerBuffer pInstance = stack.mallocPointer(1);

                result = vkCreateInstance(createInfo, null, pInstance);
                if (result != VK_SUCCESS) throw new IllegalStateException("Failed to create Vulkan instance: " + result);

                VkInstance rawVkInstance = new VkInstance(pInstance.get(0), createInfo);

                long debugMessenger = VK_NULL_HANDLE;
                if (enableValidationLayers) {
                    debugMessenger = VulkanDebug.createMessenger(rawVkInstance, stack, debugCallback);
                }

                return new VulkanInstance(rawVkInstance, debugMessenger, debugCallback);
            } catch (RuntimeException e) {
                if (debugCallback != null) debugCallback.free();
                throw e;
            }
        }
    }

    private static PointerBuffer getRequiredExtensions(MemoryStack stack, boolean enabledValidationLayers) {
        PointerBuffer glfwExtensions = glfwGetRequiredInstanceExtensions();
        if (glfwExtensions == null) throw new IllegalStateException("Failed to get GLFW Vulkan extensions");

        if (!enabledValidationLayers) {
            return glfwExtensions;
        }

        PointerBuffer extensions = stack.mallocPointer(glfwExtensions.capacity() + 1);
        extensions.put(glfwExtensions);
        extensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
        return extensions.flip();
    }

    @Override
    public void dispose() {
        VulkanDebug.destroyMessenger(vkInstance, debugMessenger);
        vkDestroyInstance(vkInstance, null);
        if (debugCallback != null) debugCallback.free();
    }

    public VkInstance getInstance() { return vkInstance; }
    public long getDebugMessenger() { return debugMessenger; }
}
