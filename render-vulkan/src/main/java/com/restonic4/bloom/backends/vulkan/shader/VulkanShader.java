package com.restonic4.bloom.backends.vulkan.shader;

import com.restonic4.bloom.backends.vulkan.device.VulkanLogicalDevice;
import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.graphics.ShaderSource;

public final class VulkanShader implements Disposable {
    private final ShaderSource source;
    private final VulkanShaderModule module;

    private VulkanShader(ShaderSource source, VulkanShaderModule module) {
        this.source = source;
        this.module = module;
    }

    public static VulkanShader create(VulkanLogicalDevice device, VulkanGlslCompiler compiler, ShaderSource source) {
        byte[] spirv = compiler.compile(source);
        VulkanShaderModule module = VulkanShaderModule.create(device, spirv);
        return new VulkanShader(source, module);
    }

    public ShaderSource source() { return source; }
    public VulkanShaderModule module() { return module; }

    @Override
    public void dispose() {
        module.dispose();
    }
}