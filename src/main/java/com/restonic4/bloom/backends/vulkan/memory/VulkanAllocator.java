package com.restonic4.bloom.backends.vulkan.memory;

import com.restonic4.bloom.core.Disposable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;

import static org.lwjgl.util.vma.Vma.vmaCreateAllocator;
import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;

// Vulkan Memory Allocator: hands out pieces of a few big device allocations instead of one allocation per buffer or image.
// Every buffer and image created through it must be destroyed before dispose().
public final class VulkanAllocator implements Disposable {
    private final long handle;

    private VulkanAllocator(long handle) {
        this.handle = handle;
    }

    public static VulkanAllocator create(VkInstance instance, VkPhysicalDevice physicalDevice, VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // VMA needs the addresses of the Vulkan functions it calls; LWJGL already loaded them for this instance and device
            VmaVulkanFunctions functions = VmaVulkanFunctions.calloc(stack).set(instance, device);

            // VMA is asked for 1.3: it uses nothing newer, and a VMA build only knows the versions it was written for
            VmaAllocatorCreateInfo createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(instance)
                    .physicalDevice(physicalDevice)
                    .device(device)
                    .pVulkanFunctions(functions)
                    .vulkanApiVersion(VK_API_VERSION_1_3);

            PointerBuffer pAllocator = stack.mallocPointer(1);

            int result = vmaCreateAllocator(createInfo, pAllocator);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create memory allocator: " + result);
            }

            return new VulkanAllocator(pAllocator.get(0));
        }
    }

    public long getHandle() { return handle; }

    @Override
    public void dispose() {
        vmaDestroyAllocator(handle);
    }
}
