package com.restonic4.bloom.backends.vulkan.image;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanSampler implements Disposable {
    private final VkDevice device;
    private final long handle;

    private VulkanSampler(VkDevice device, long handle) {
        this.device = device;
        this.handle = handle;
    }

    public static VulkanSampler create(VulkanLogicalDevice logicalDevice) {
        VkDevice device = logicalDevice.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo createInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .anisotropyEnable(logicalDevice.getMaxAnisotropy() > 1.0f)
                    .maxAnisotropy(logicalDevice.getMaxAnisotropy())
                    .compareEnable(false)
                    .minLod(0.0f)
                    .maxLod(VK_LOD_CLAMP_NONE) // The sampler is shared by all textures; the image view limits it to the levels each one has
                    .unnormalizedCoordinates(false);

            LongBuffer pSampler = stack.mallocLong(1);

            int result = vkCreateSampler(device, createInfo, null, pSampler);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create sampler: " + result);
            }

            return new VulkanSampler(device, pSampler.get(0));
        }
    }

    public long getHandle() { return handle; }

    @Override
    public void dispose() {
        vkDestroySampler(device, handle, null);
    }
}
