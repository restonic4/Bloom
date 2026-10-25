package com.restonic4.bloom.backends.vulkan.shader;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;

public final class VulkanShaderModule implements Disposable {
    private final VkDevice device;
    private final long handle;

    private VulkanShaderModule(VkDevice device, long handle) {
        this.device = device;
        this.handle = handle;
    }

    public static VulkanShaderModule create(VulkanLogicalDevice logicalDevice, byte[] spirv) {
        if (spirv.length == 0) throw new IllegalArgumentException("SPIR-V shader is empty");

        if ((spirv.length & 3) != 0) {
            throw new IllegalArgumentException("SPIR-V shader size must be a multiple of 4 bytes: " + spirv.length);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer code = stack.malloc(spirv.length);
            code.put(spirv).flip();

            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);

            LongBuffer pShaderModule = stack.mallocLong(1);

            int result = vkCreateShaderModule(logicalDevice.getHandle(), createInfo, null, pShaderModule);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create Vulkan shader module. VkResult=" + result);
            }

            return new VulkanShaderModule(logicalDevice.getHandle(), pShaderModule.get(0));
        }
    }

    public long getHandle() { return handle; }

    @Override
    public void dispose() {
        vkDestroyShaderModule(device, handle, null);
    }
}