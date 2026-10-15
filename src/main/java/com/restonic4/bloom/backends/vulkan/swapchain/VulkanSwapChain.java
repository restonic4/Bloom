package com.restonic4.bloom.backends.vulkan.swapchain;

import com.restonic4.bloom.backends.vulkan.device.QueueFamilyIndices;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.device.VulkanPhysicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class VulkanSwapChain implements Disposable {
    private final VkDevice device;
    private final long handle;

    private final long[] imagehandles;
    private final int imageFormat;
    private final int imageWidth;
    private final int imageHeight;

    private VulkanSwapChain(
            VkDevice device, long handle,
            long[] imagehandles, int imageFormat,
            int imageWidth, int imageHeight
    ) {
        this.device = device;
        this.handle = handle;

        this.imagehandles = imagehandles;
        this.imageFormat = imageFormat;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    public static VulkanSwapChain create(VulkanPhysicalDevice physicalDevice, VulkanLogicalDevice logicalDevice, long surface, long window) {
        return create(physicalDevice, logicalDevice, surface, window, VK_NULL_HANDLE);
    }

    public static VulkanSwapChain create(VulkanPhysicalDevice physicalDevice, VulkanLogicalDevice logicalDevice, long surface, long window, long oldSwapChain) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SwapChainSupportDetails support = SwapChainSupportDetails.querySwapChainSupport(physicalDevice.getHandle(), surface);

            SwapChainSupportDetails.VkSurfaceFormat surfaceFormat = chooseSurfaceFormat(support.formats());

            int presentMode = choosePresentMode(support.presentModes());

            VkExtent2D extent = chooseExtent(support.capabilities(), window, stack);

            int imageCount = support.capabilities().minImageCount() + 1;

            if (support.capabilities().maxImageCount() > 0 && imageCount > support.capabilities().maxImageCount()) {
                imageCount = support.capabilities().maxImageCount();
            }

            QueueFamilyIndices queueFamilies = QueueFamilyIndices.findQueueFamilies(physicalDevice.getHandle(), surface);

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(surfaceFormat.format())
                    .imageColorSpace(surfaceFormat.colorSpace())
                    .imageExtent(extent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .preTransform(support.capabilities().currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(presentMode)
                    .clipped(true)
                    .oldSwapchain(oldSwapChain);

            if (queueFamilies.graphics() != queueFamilies.present()) {
                createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                        .queueFamilyIndexCount(2)
                        .pQueueFamilyIndices(stack.ints(queueFamilies.graphics(), queueFamilies.present()));
            } else {
                createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }

            LongBuffer pSwapChain = stack.longs(VK_NULL_HANDLE);

            int result = vkCreateSwapchainKHR(logicalDevice.getHandle(), createInfo, null, pSwapChain);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create swap chain: " + result);
            }

            long swapChain = pSwapChain.get(0);

            IntBuffer imageCountBuffer = stack.ints(0);

            result = vkGetSwapchainImagesKHR(logicalDevice.getHandle(), swapChain, imageCountBuffer, null);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to get swap chain image count: " + result);
            }

            long[] images = new long[imageCountBuffer.get(0)];

            LongBuffer pImages = stack.mallocLong(images.length);

            result = vkGetSwapchainImagesKHR(logicalDevice.getHandle(), swapChain, imageCountBuffer, pImages);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to get swap chain images: " + result);
            }

            pImages.get(0, images);

            return new VulkanSwapChain(logicalDevice.getHandle(), swapChain, images, surfaceFormat.format(), extent.width(), extent.height());
        }
    }

    private static SwapChainSupportDetails.VkSurfaceFormat chooseSurfaceFormat(List<SwapChainSupportDetails.VkSurfaceFormat> availableFormats) {
        for (SwapChainSupportDetails.VkSurfaceFormat format : availableFormats) {
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }

        return availableFormats.getFirst();
    }

    private static int choosePresentMode(List<Integer> availablePresentModes) {
        for (int presentMode : availablePresentModes) {
            if (presentMode == VK_PRESENT_MODE_MAILBOX_KHR) {
                return presentMode;
            }
        }

        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private static VkExtent2D chooseExtent(SwapChainSupportDetails.VkSurfaceCapabilities capabilities, long window, MemoryStack stack) {
        if (capabilities.currentWidth() != -1) {
            return VkExtent2D.calloc(stack).width(capabilities.currentWidth()).height(capabilities.currentHeight());
        }

        IntBuffer width = stack.ints(0);
        IntBuffer height = stack.ints(0);

        glfwGetFramebufferSize(window, width, height);

        int actualWidth = width.get(0);
        int actualHeight = height.get(0);

        actualWidth = Math.max(
                capabilities.minWidth(),
                Math.min(actualWidth, capabilities.maxWidth())
        );

        actualHeight = Math.max(
                capabilities.minHeight(),
                Math.min(actualHeight, capabilities.maxHeight())
        );

        return VkExtent2D.calloc(stack).width(actualWidth).height(actualHeight);
    }

    public long getHandle() { return handle; }
    public long[] getImageHandles() { return imagehandles; }
    public int getImageFormat() { return imageFormat; }
    public int getImageWidth() { return imageWidth; }
    public int getImageHeight() { return imageHeight; }

    @Override
    public void dispose() {
        vkDestroySwapchainKHR(device, handle, null);
    }
}