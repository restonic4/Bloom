package com.restonic4.bloom.backends.vulkan.buffer;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.*;

public final class VulkanBuffer implements Disposable {
    private final VkDevice device;
    private final long handle;
    private final long memory;
    private final long size;
    private final int memoryProperties;

    private VulkanBuffer(VkDevice device, long handle, long memory, long size, int memoryProperties) {
        this.device = device;
        this.handle = handle;
        this.memory = memory;
        this.size = size;
        this.memoryProperties = memoryProperties;
    }

    public static VulkanBuffer create(VulkanLogicalDevice logicalDevice, long size, int usage, int memoryProperties) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }

        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Buffer: only a handle with a size and a usage, no memory behind it yet

            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);

            int result = vkCreateBuffer(device, bufferInfo, null, pBuffer);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create buffer: " + result);
            }

            long buffer = pBuffer.get(0);

            // Memory: the driver tells us how much this buffer needs and which memory types can hold it

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, requirements);

            int memoryTypeIndex;
            try {
                memoryTypeIndex = findMemoryType(device.getPhysicalDevice(), requirements.memoryTypeBits(), memoryProperties, stack);
            } catch (RuntimeException e) {
                vkDestroyBuffer(device, buffer, null);
                throw e;
            }

            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryTypeIndex);

            LongBuffer pMemory = stack.mallocLong(1);

            result = vkAllocateMemory(device, allocateInfo, null, pMemory);
            if (result != VK_SUCCESS) {
                vkDestroyBuffer(device, buffer, null);
                throw new IllegalStateException("Failed to allocate buffer memory: " + result);
            }

            long memory = pMemory.get(0);

            // Bind: attach the memory to the buffer at offset 0

            result = vkBindBufferMemory(device, buffer, memory, 0);
            if (result != VK_SUCCESS) {
                vkFreeMemory(device, memory, null);
                vkDestroyBuffer(device, buffer, null);
                throw new IllegalStateException("Failed to bind buffer memory: " + result);
            }

            return new VulkanBuffer(device, buffer, memory, size, memoryProperties);
        }
    }

    public static VulkanBuffer createHostVisible(VulkanLogicalDevice logicalDevice, ByteBuffer data, int usage) {
        VulkanBuffer buffer = create(
                logicalDevice,
                data.remaining(),
                usage,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );

        try {
            buffer.write(data);
        } catch (RuntimeException e) {
            buffer.dispose();
            throw e;
        }

        return buffer;
    }

    private static int findMemoryType(VkPhysicalDevice physicalDevice, int allowedTypeBits, int requiredProperties, MemoryStack stack) {
        VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.malloc(stack);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);

        for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
            boolean allowedForBuffer = (allowedTypeBits & (1 << i)) != 0;
            boolean hasProperties = (memoryProperties.memoryTypes(i).propertyFlags() & requiredProperties) == requiredProperties;

            if (allowedForBuffer && hasProperties) {
                return i;
            }
        }

        throw new IllegalStateException("No memory type matches the buffer requirements and properties " + requiredProperties);
    }

    public void write(ByteBuffer data) {
        int required = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        if ((memoryProperties & required) != required) {
            throw new IllegalStateException("Only host visible and host coherent buffers can be written from the CPU");
        }

        if (data.remaining() > size) {
            throw new IllegalArgumentException("Data (" + data.remaining() + " bytes) is bigger than the buffer (" + size + " bytes)");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);

            int result = vkMapMemory(device, memory, 0, size, 0, pData);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to map buffer memory: " + result);
            }

            memCopy(memAddress(data), pData.get(0), data.remaining());

            vkUnmapMemory(device, memory);
        }
    }

    public long getHandle() { return handle; }
    public long getSize() { return size; }

    @Override
    public void dispose() {
        vkDestroyBuffer(device, handle, null);
        vkFreeMemory(device, memory, null);
    }
}
