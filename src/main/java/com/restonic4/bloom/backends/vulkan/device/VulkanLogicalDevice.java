package com.restonic4.bloom.backends.vulkan.device;

import com.restonic4.bloom.core.Disposable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;

public class VulkanLogicalDevice implements Disposable {
    private static final String[] DEVICE_EXTENSIONS = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};

    private final VkDevice handle;
    private final VkQueue graphicsQueue;
    private final VkQueue presentQueue;

    private VulkanLogicalDevice(VkDevice handle, VkQueue graphicsQueue, VkQueue presentQueue) {
        this.handle = handle;
        this.graphicsQueue = graphicsQueue;
        this.presentQueue = presentQueue;
    }

    public static VulkanLogicalDevice create(VulkanPhysicalDevice physicalDevice, long surface) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            QueueFamilyIndices indices = QueueFamilyIndices.findQueueFamilies(physicalDevice.getHandle(), surface);
            if (!indices.isComplete()) throw new IllegalStateException("Physical device has no graphics queue");

            // Queue creation

            int[] uniqueQueueFamilies = indices.unique();
            VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.length, stack);

            int queueIndex = 0;

            for (int family : uniqueQueueFamilies) {
                VkDeviceQueueCreateInfo queue = queueCreateInfo.get(queueIndex);

                queue.sType$Default();
                queue.queueFamilyIndex(family);
                VkDeviceQueueCreateInfo.nqueueCount(queue.address(), 1);
                queue.pQueuePriorities(stack.floats(1.0f));

                queueIndex++;
            }

            // Device extensions

            PointerBuffer deviceExtensions = stack.mallocPointer(DEVICE_EXTENSIONS.length);
            for (String extension : DEVICE_EXTENSIONS) {
                deviceExtensions.put(stack.UTF8(extension));
            }

            deviceExtensions.flip();

            // Device features

            VkPhysicalDeviceVulkan13Features enabled13 = VkPhysicalDeviceVulkan13Features.calloc(stack)
                    .sType$Default()
                    .dynamicRendering(true)
                    .synchronization2(true);

            VkPhysicalDeviceVulkan12Features enabled12 = VkPhysicalDeviceVulkan12Features.calloc(stack)
                    .sType$Default()
                    .timelineSemaphore(true)
                    .pNext(enabled13.address());

            VkPhysicalDeviceFeatures2 enabledFeatures = VkPhysicalDeviceFeatures2.calloc(stack)
                    .sType$Default()
                    .pNext(enabled12.address());

            enabledFeatures.features().geometryShader(true);

            // Logical device

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pNext(enabledFeatures.address())
                    .pQueueCreateInfos(queueCreateInfo)
                    .ppEnabledExtensionNames(deviceExtensions);

            PointerBuffer pDevice = stack.pointers(VK_NULL_HANDLE);

            int result = vkCreateDevice(physicalDevice.getHandle(), createInfo, null, pDevice);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device: " + result);
            }

            VkDevice device = new VkDevice(pDevice.get(0), physicalDevice.getHandle(), createInfo);

            // Retrieve graphics queue

            PointerBuffer pGraphicsQueue = stack.pointers(VK_NULL_HANDLE);

            vkGetDeviceQueue(device, indices.graphics(), 0, pGraphicsQueue);

            VkQueue graphicsQueue = new VkQueue(pGraphicsQueue.get(0), device);

            PointerBuffer pPresentQueue = stack.pointers(VK_NULL_HANDLE);

            vkGetDeviceQueue(device, indices.present(), 0, pPresentQueue);

            VkQueue presentQueue = new VkQueue(pPresentQueue.get(0), device);

            return new VulkanLogicalDevice(device, graphicsQueue, presentQueue);
        }
    }

    public VkDevice getHandle() { return handle; }

    @Override
    public void dispose() {
        vkDestroyDevice(handle, null);
    }
}
