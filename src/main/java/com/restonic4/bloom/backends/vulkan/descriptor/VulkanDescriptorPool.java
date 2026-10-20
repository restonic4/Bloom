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
     * A pool for sets that each hold one descriptor of every listed type.
     *
     * @param maxSets how many sets can be allocated from the pool, and so how many descriptors of each type it reserves
     * @param descriptorTypes VK_DESCRIPTOR_TYPE_*, each type once
     */
    public static VulkanDescriptorPool create(VulkanLogicalDevice logicalDevice, int maxSets, int... descriptorTypes) {
        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(descriptorTypes.length, stack);
            for (int i = 0; i < descriptorTypes.length; i++) {
                poolSizes.get(i)
                        .type(descriptorTypes[i])
                        .descriptorCount(maxSets);
            }

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
     * Allocates {@code count} sets, all shaped like {@code setLayout}. They are empty: no resource is attached yet.
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
