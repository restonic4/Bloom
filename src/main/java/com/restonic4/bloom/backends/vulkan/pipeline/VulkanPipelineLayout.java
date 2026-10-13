package com.restonic4.bloom.backends.vulkan.pipeline;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanPipelineLayout implements Disposable {
    private final VkDevice device;
    private final long handle;

    private VulkanPipelineLayout(VkDevice device, long handle) {
        this.device = device;
        this.handle = handle;
    }

    public static VulkanPipelineLayout create(VulkanLogicalDevice logicalDevice) {
        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default();

            LongBuffer pPipelineLayout = stack.mallocLong(1);

            int result = vkCreatePipelineLayout(device, createInfo, null, pPipelineLayout);
            if (result != VK_SUCCESS) throw new IllegalStateException("Failed to create pipeline layout: " + result);

            return new VulkanPipelineLayout(device, pPipelineLayout.get(0));
        }
    }

    public long getHandle() { return handle; }

    @Override
    public void dispose() {
        vkDestroyPipelineLayout(device, handle, null);
    }
}