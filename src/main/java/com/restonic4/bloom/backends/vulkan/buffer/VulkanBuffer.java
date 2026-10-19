package com.restonic4.bloom.backends.vulkan.buffer;

import com.restonic4.bloom.backends.vulkan.command.VulkanCommandPool;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.memory.VulkanMemory;
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
                memoryTypeIndex = VulkanMemory.findMemoryType(device.getPhysicalDevice(), requirements.memoryTypeBits(), memoryProperties, stack);
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

    // Uploads through a staging buffer: CPU fills a host visible buffer, the GPU copies it into device local memory.
    // Blocks until the copy is done (see VulkanCommandPool.submitOneTime), so use it for startup data.
    public static VulkanBuffer createDeviceLocal(VulkanLogicalDevice logicalDevice, VulkanCommandPool commandPool, ByteBuffer data, int usage) {
        long size = data.remaining();

        VulkanBuffer staging = createHostVisible(logicalDevice, data, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);

        try {
            VulkanBuffer buffer = create(
                    logicalDevice,
                    size,
                    usage | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            );

            try {
                commandPool.submitOneTime(logicalDevice.getGraphicsQueue(), commandBuffer -> {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack)
                                .srcOffset(0)
                                .dstOffset(0)
                                .size(size);

                        vkCmdCopyBuffer(commandBuffer, staging.handle, buffer.handle, region);
                    }
                });
            } catch (RuntimeException e) {
                buffer.dispose();
                throw e;
            }

            return buffer;
        } finally {
            staging.dispose();
        }
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
