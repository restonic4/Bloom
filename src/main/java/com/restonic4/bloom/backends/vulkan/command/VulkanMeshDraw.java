package com.restonic4.bloom.backends.vulkan.command;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;

// One indexed draw: a mesh's buffers plus which slice of the shared instance buffer it uses
public record VulkanMeshDraw(VulkanBuffer vertexBuffer, VulkanBuffer indexBuffer, int indexCount, int firstInstance, int instanceCount) {}
