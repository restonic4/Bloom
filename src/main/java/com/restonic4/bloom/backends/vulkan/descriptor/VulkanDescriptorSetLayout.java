package com.restonic4.bloom.backends.vulkan.descriptor;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

// The shape of a descriptor set: which slots exist, what type each holds and which stages read it. No resources yet.
public final class VulkanDescriptorSetLayout implements Disposable {
    private final VkDevice device;
    private final long handle;

    private VulkanDescriptorSetLayout(VkDevice device, long handle) {
        this.device = device;
        this.handle = handle;
    }

    public static VulkanDescriptorSetLayout create(VulkanLogicalDevice logicalDevice, VulkanDescriptorBinding... slots) {
        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(slots.length, stack);
            for (int i = 0; i < slots.length; i++) {
                bindings.get(i)
                        .binding(slots[i].binding())
                        .descriptorType(slots[i].descriptorType())
                        .descriptorCount(1)
                        .stageFlags(slots[i].stages());
            }

            VkDescriptorSetLayoutCreateInfo createInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(bindings);

            LongBuffer pSetLayout = stack.mallocLong(1);

            int result = vkCreateDescriptorSetLayout(device, createInfo, null, pSetLayout);
            if (result != VK_SUCCESS) throw new IllegalStateException("Failed to create descriptor set layout: " + result);

            return new VulkanDescriptorSetLayout(device, pSetLayout.get(0));
        }
    }

    public long getHandle() { return handle; }

    @Override
    public void dispose() {
        vkDestroyDescriptorSetLayout(device, handle, null);
    }
}
