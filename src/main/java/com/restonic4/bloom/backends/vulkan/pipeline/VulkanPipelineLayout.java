package com.restonic4.bloom.backends.vulkan.pipeline;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanPipelineLayout implements Disposable {
    private final VkDevice device;
    private final long handle;
    private final int pushConstantStages;

    private VulkanPipelineLayout(VkDevice device, long handle, int pushConstantStages) {
        this.device = device;
        this.handle = handle;
        this.pushConstantStages = pushConstantStages;
    }

    /**
     * @param pushConstantStages shader stages that read the push constants (VK_SHADER_STAGE_*)
     * @param pushConstantSize size in bytes of the push constant block, 0 for none
     */
    public static VulkanPipelineLayout create(VulkanLogicalDevice logicalDevice, int pushConstantStages, int pushConstantSize) {
        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default();

            if (pushConstantSize > 0) {
                VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
                pushConstantRanges.get(0)
                        .stageFlags(pushConstantStages)
                        .offset(0)
                        .size(pushConstantSize);

                createInfo.pPushConstantRanges(pushConstantRanges);
            }

            LongBuffer pPipelineLayout = stack.mallocLong(1);

            int result = vkCreatePipelineLayout(device, createInfo, null, pPipelineLayout);
            if (result != VK_SUCCESS) throw new IllegalStateException("Failed to create pipeline layout: " + result);

            return new VulkanPipelineLayout(device, pPipelineLayout.get(0), pushConstantStages);
        }
    }

    public long getHandle() { return handle; }
    public int getPushConstantStages() { return pushConstantStages; }

    @Override
    public void dispose() {
        vkDestroyPipelineLayout(device, handle, null);
    }
}