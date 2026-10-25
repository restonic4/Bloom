package com.restonic4.bloom.backends.vulkan.swapchain;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public record SwapChainSupportDetails(
        VkSurfaceCapabilities capabilities,
        List<VkSurfaceFormat> formats,
        List<Integer> presentModes
) {
    public record VkSurfaceCapabilities(
            int minImageCount,
            int maxImageCount,
            int currentWidth,
            int currentHeight,
            int minWidth,
            int minHeight,
            int maxWidth,
            int maxHeight,
            int currentTransform
    ) {}

    public record VkSurfaceFormat(
            int format,
            int colorSpace
    ) {}

    public static SwapChainSupportDetails querySwapChainSupport(VkPhysicalDevice device, long surface) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Capabilities
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);

            int result = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, capabilities);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to query surface capabilities: " + result);
            }

            SwapChainSupportDetails.VkSurfaceCapabilities caps = new SwapChainSupportDetails.VkSurfaceCapabilities(
                    capabilities.minImageCount(),
                    capabilities.maxImageCount(),
                    capabilities.currentExtent().width(),
                    capabilities.currentExtent().height(),
                    capabilities.minImageExtent().width(),
                    capabilities.minImageExtent().height(),
                    capabilities.maxImageExtent().width(),
                    capabilities.maxImageExtent().height(),
                    capabilities.currentTransform()
            );

            // Formats
            IntBuffer formatCount = stack.ints(0);

            vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, formatCount, null);

            List<SwapChainSupportDetails.VkSurfaceFormat> formats = new ArrayList<>();

            if (formatCount.get(0) > 0) {
                VkSurfaceFormatKHR.Buffer surfaceFormats = VkSurfaceFormatKHR.malloc(formatCount.get(0), stack);

                vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, formatCount, surfaceFormats);

                for (int i = 0; i < surfaceFormats.capacity(); i++) {
                    VkSurfaceFormatKHR format = surfaceFormats.get(i);
                    formats.add(new SwapChainSupportDetails.VkSurfaceFormat(format.format(), format.colorSpace()));
                }
            }

            // Present modes
            IntBuffer presentModeCount = stack.ints(0);

            vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, presentModeCount, null);

            List<Integer> presentModes = new ArrayList<>();

            if (presentModeCount.get(0) > 0) {
                IntBuffer modes = stack.mallocInt(presentModeCount.get(0));

                vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, presentModeCount, modes);

                for (int i = 0; i < modes.capacity(); i++) {
                    presentModes.add(modes.get(i));
                }
            }

            return new SwapChainSupportDetails(caps, formats, presentModes);
        }
    }
}