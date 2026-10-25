package com.restonic4.bloom.backends.vulkan.buffer;

import com.restonic4.bloom.backends.vulkan.command.VulkanCommandPool;
import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.backends.vulkan.memory.VulkanMemoryUsage;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

public final class VulkanBuffer implements Disposable {
    private final long allocator;
    private final long handle;
    private final long allocation;
    private final long size;
    private final VulkanMemoryUsage memoryUsage;

    private VulkanBuffer(long allocator, long handle, long allocation, long size, VulkanMemoryUsage memoryUsage) {
        this.allocator = allocator;
        this.handle = handle;
        this.allocation = allocation;
        this.size = size;
        this.memoryUsage = memoryUsage;
    }

    public static VulkanBuffer create(VulkanLogicalDevice logicalDevice, long size, int usage, VulkanMemoryUsage memoryUsage) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }

        long allocator = logicalDevice.getAllocator().getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            // The allocator creates the buffer, finds a memory type for it, allocates and binds, all in one call
            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
            switch (memoryUsage) {
                case DEVICE_LOCAL -> allocationInfo.usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
                // Mapped for its whole life, so writing every frame needs no map and unmap
                case HOST_WRITABLE -> allocationInfo
                        .usage(VMA_MEMORY_USAGE_AUTO)
                        .flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT | VMA_ALLOCATION_CREATE_MAPPED_BIT);
            }

            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);

            int result = vmaCreateBuffer(allocator, bufferInfo, allocationInfo, pBuffer, pAllocation, null);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create buffer: " + result);
            }

            return new VulkanBuffer(allocator, pBuffer.get(0), pAllocation.get(0), size, memoryUsage);
        }
    }

    public static VulkanBuffer createHostVisible(VulkanLogicalDevice logicalDevice, ByteBuffer data, int usage) {
        VulkanBuffer buffer = create(logicalDevice, data.remaining(), usage, VulkanMemoryUsage.HOST_WRITABLE);

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
                    VulkanMemoryUsage.DEVICE_LOCAL
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
        if (memoryUsage != VulkanMemoryUsage.HOST_WRITABLE) {
            throw new IllegalStateException("Only host writable buffers can be written from the CPU");
        }

        if (data.remaining() > size) {
            throw new IllegalArgumentException("Data (" + data.remaining() + " bytes) is bigger than the buffer (" + size + " bytes)");
        }

        // Copies into the mapped memory and flushes it if the memory it landed in is not coherent
        int result = vmaCopyMemoryToAllocation(allocator, data, allocation, 0);
        if (result != VK_SUCCESS) {
            throw new IllegalStateException("Failed to write buffer: " + result);
        }
    }

    public long getHandle() { return handle; }
    public long getSize() { return size; }

    @Override
    public void dispose() {
        vmaDestroyBuffer(allocator, handle, allocation);
    }
}
