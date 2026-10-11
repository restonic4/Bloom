package com.restonic4.bloom.backends.vulkan.device;

import com.restonic4.bloom.backends.vulkan.swapchain.SwapChainSupportDetails;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;

public class VulkanPhysicalDevice {
    private static final Set<String> REQUIRED_DEVICE_EXTENSIONS = Set.of(VK_KHR_SWAPCHAIN_EXTENSION_NAME);

    private final VkPhysicalDevice handle;
    private final ScoreData scoreData;

    private VulkanPhysicalDevice(VkPhysicalDevice handle, ScoreData scoreData) {
        this.handle = handle;
        this.scoreData = scoreData;
    }

    public static VulkanPhysicalDevice pickBestDevice(VkInstance instance, long surface) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer physicalDevices = enumeratePhysicalDevices(instance, stack);

            VulkanPhysicalDevice bestDevice = null;
            ScoreData bestScore = null;

            for (int i = 0; i < physicalDevices.capacity(); i++) {
                VkPhysicalDevice device = new VkPhysicalDevice(physicalDevices.get(i), instance);

                ScoreData scoreData = rateDeviceSuitability(device, surface);
                if (scoreData == null) continue;

                if (bestScore == null || scoreData.score() > bestScore.score()) {
                    bestScore = scoreData;
                    bestDevice = new VulkanPhysicalDevice(device, scoreData);
                }
            }

            if (bestDevice == null) throw new RuntimeException("Failed to find a suitable GPU");
            return bestDevice;
        }
    }

    private static PointerBuffer enumeratePhysicalDevices(VkInstance instance, MemoryStack stack) {
        IntBuffer deviceCount = stack.ints(0);

        vkEnumeratePhysicalDevices(instance, deviceCount, null);
        if (deviceCount.get(0) == 0) throw new RuntimeException("Failed to find GPUs with Vulkan support");

        PointerBuffer physicalDevices = stack.mallocPointer(deviceCount.get(0));
        vkEnumeratePhysicalDevices(instance, deviceCount, physicalDevices);

        return physicalDevices;
    }

    // TODO: Improve scoring
    private static ScoreData rateDeviceSuitability(VkPhysicalDevice device, long surface) {
        int score = 0;
        Type type;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);

            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);

            vkGetPhysicalDeviceProperties(device, properties);

            vkGetPhysicalDeviceFeatures(device, features);

            // Hard requirement: must have graphics queue
            QueueFamilyIndices indices = QueueFamilyIndices.findQueueFamilies(device, surface);
            if (!indices.isComplete()) {
                return null;
            }

            // Hard requirement: extensions support
            if (!checkDeviceExtensionSupport(device)) {
                return null;
            }

            // Hard requirement: Swap Chain
            SwapChainSupportDetails swapChainSupport = SwapChainSupportDetails.querySwapChainSupport(device, surface);
            if (swapChainSupport.formats().isEmpty() || swapChainSupport.presentModes().isEmpty()) {
                return null;
            }

            // Hard requirement: geometry shaders
            if (!features.geometryShader()) {
                return null;
            }

            // Prefer discrete GPUs
            if (properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                score += 1000;
                type = Type.DISCRETE;
            } else {
                type = Type.INTEGRATED;
            }

            // Prefer GPUs with greater texture dimensions
            score += properties.limits().maxImageDimension2D();

            return new ScoreData(score, type);
        }
    }

    private static boolean checkDeviceExtensionSupport(VkPhysicalDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer extensionCount = stack.ints(0);

            int result = vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, null);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to enumerate device extension count: " + result);
            }

            int count = extensionCount.get(0);

            // Don't allocate this potentially large buffer on MemoryStack. - restonic4: It crashes with my GPU
            VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.malloc(count);

            try {
                result = vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, availableExtensions);
                if (result != VK_SUCCESS) {
                    throw new IllegalStateException("Failed to enumerate device extensions: " + result);
                }

                Set<String> availableExtensionNames = availableExtensions.stream()
                        .map(VkExtensionProperties::extensionNameString)
                        .collect(toSet());

                return availableExtensionNames.containsAll(REQUIRED_DEVICE_EXTENSIONS);
            } finally {
                availableExtensions.free();
            }
        }
    }

    public VkPhysicalDevice getHandle() { return handle; }
    public int getScore() { return scoreData.score(); }
    public Type getType() { return scoreData.type(); }

    public enum Type { DISCRETE, INTEGRATED, UNKNOWN }
    public record ScoreData(int score, Type type) {}
}
