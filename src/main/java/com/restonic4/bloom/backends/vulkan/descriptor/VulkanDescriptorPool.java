package com.restonic4.bloom.backends.vulkan.descriptor;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

// Memory reserved up front for descriptor sets. Sets are not freed one by one: destroying the pool frees all of them.
public final class VulkanDescriptorPool implements Disposable {
    private final VkDevice device;
    private final long handle;

    private VulkanDescriptorPool(VkDevice device, long handle) {
        this.device = device;
        this.handle = handle;
    }

    /**
     * A pool for sets that each hold one descriptor of the same type.
     *
     * @param descriptorType VK_DESCRIPTOR_TYPE_*
     * @param maxSets how many sets can be allocated from the pool, and so how many descriptors of that type it reserves
     */
    public static VulkanDescriptorPool create(VulkanLogicalDevice logicalDevice, int descriptorType, int maxSets) {
        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0)
                    .type(descriptorType)
                    .descriptorCount(maxSets);

            VkDescriptorPoolCreateInfo createInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .maxSets(maxSets)
                    .pPoolSizes(poolSizes);

            LongBuffer pPool = stack.mallocLong(1);

            int result = vkCreateDescriptorPool(device, createInfo, null, pPool);
            if (result != VK_SUCCESS) throw new IllegalStateException("Failed to create descriptor pool: " + result);

            return new VulkanDescriptorPool(device, pPool.get(0));
        }
    }

    /**
     * Allocates {@code count} sets, all shaped like {@code setLayout}. They are empty: no buffer is attached yet.
     */
    public long[] allocateSets(VulkanDescriptorSetLayout setLayout, int count) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer layouts = stack.mallocLong(count);
            for (int i = 0; i < count; i++) {
                layouts.put(i, setLayout.getHandle());
            }

            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(handle)
                    .pSetLayouts(layouts);

            LongBuffer pSets = stack.mallocLong(count);

            int result = vkAllocateDescriptorSets(device, allocateInfo, pSets);
            if (result != VK_SUCCESS) throw new IllegalStateException("Failed to allocate descriptor sets: " + result);

            long[] sets = new long[count];
            pSets.get(sets);
            return sets;
        }
    }

    public long getHandle() { return handle; }

    @Override
    public void dispose() {
        vkDestroyDescriptorPool(device, handle, null);
    }
}
