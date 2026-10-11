package com.restonic4.bloom.backends.vulkan.instance;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT;
import static org.lwjgl.vulkan.VK10.*;

public class VulkanDebug {
    static final Set<String> VALIDATION_LAYERS = Set.of("VK_LAYER_KHRONOS_validation");

    static boolean checkValidationLayerSupport() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer layerCount = stack.ints(0);

            vkEnumerateInstanceLayerProperties(layerCount, null);

            VkLayerProperties.Buffer availableLayers = VkLayerProperties.malloc(layerCount.get(0), stack);

            vkEnumerateInstanceLayerProperties(layerCount, availableLayers);

            Set<String> availableLayerNames = availableLayers.stream()
                    .map(VkLayerProperties::layerNameString)
                    .collect(toSet());

            return availableLayerNames.containsAll(VALIDATION_LAYERS);
        }
    }

    static PointerBuffer validationLayersAsPointerBuffer(MemoryStack stack) {
        PointerBuffer buffer = stack.mallocPointer(VALIDATION_LAYERS.size());

        VALIDATION_LAYERS.stream()
                .map(stack::UTF8)
                .forEach(buffer::put);

        return buffer.rewind();
    }

    static VkDebugUtilsMessengerCreateInfoEXT createMessengerCreateInfo(MemoryStack stack) {
        VkDebugUtilsMessengerCreateInfoEXT createInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack);
        createInfo.sType$Default();
        createInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
        createInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);
        createInfo.pfnUserCallback(VulkanDebug::debugCallback);
        return createInfo;
    }

    public static long createMessenger(VkInstance rawVkInstance, MemoryStack stack) {
        VkDebugUtilsMessengerCreateInfoEXT createInfo = createMessengerCreateInfo(stack);

        LongBuffer pDebugMessenger = stack.longs(VK_NULL_HANDLE);

        int result = createDebugUtilsMessengerEXT(rawVkInstance, createInfo, null, pDebugMessenger);
        if (result != VK_SUCCESS) throw new IllegalStateException("Failed to create Vulkan debug messenger: " + result);
        return pDebugMessenger.get(0);
    }

    static void destroyMessenger(VkInstance instance, long messenger) {
        if (messenger == VK_NULL_HANDLE) return;

        if (vkGetInstanceProcAddr(instance, "vkDestroyDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
            vkDestroyDebugUtilsMessengerEXT(instance, messenger, null);
        }
    }

    private static int debugCallback(int messageSeverity, int messageType, long pCallbackData, long pUserData) {
        VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);

        String message = callbackData.pMessageString();

        if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
            System.err.println("[VulkanDebug ERROR] " + message);
        } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
            System.err.println("[VulkanDebug WARNING] " + message);
        } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
            System.out.println("[VulkanDebug INFO] " + message);
        } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT) != 0) {
            System.out.println("[VulkanDebug VERBOSE] " + message);
        }

        return VK_FALSE;
    }

    private static int createDebugUtilsMessengerEXT(
            VkInstance instance,
            VkDebugUtilsMessengerCreateInfoEXT createInfo,
            VkAllocationCallbacks allocationCallbacks,
            LongBuffer pDebugMessenger
    ) {
        if (vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
            return vkCreateDebugUtilsMessengerEXT(instance, createInfo, allocationCallbacks, pDebugMessenger);
        }
        return VK_ERROR_EXTENSION_NOT_PRESENT;
    }
}
