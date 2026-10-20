package com.restonic4.bloom.backends.vulkan.descriptor;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

// The shape of a descriptor set: which slots exist, what type each holds and which stages read it. No buffers yet.
public final class VulkanDescriptorSetLayout implements Disposable {
    private final VkDevice device;
    private final long handle;

    private VulkanDescriptorSetLayout(VkDevice device, long handle) {
        this.device = device;
        this.handle = handle;
    }

    /**
     * A layout with a single slot.
     *
     * @param binding the slot number, matches {@code layout(binding = N)} in the shader
     * @param descriptorType VK_DESCRIPTOR_TYPE_*
     * @param stages shader stages that read the slot (VK_SHADER_STAGE_*)
     */
    public static VulkanDescriptorSetLayout create(VulkanLogicalDevice logicalDevice, int binding, int descriptorType, int stages) {
        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.get(0)
                    .binding(binding)
                    .descriptorType(descriptorType)
                    .descriptorCount(1)
                    .stageFlags(stages);

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
