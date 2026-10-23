package com.restonic4.bloom.backends.vulkan.command;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;

// A mesh on the GPU. Uploaded once, then shared by every draw of that mesh, whatever its material
public record VulkanMeshBuffers(VulkanBuffer vertexBuffer, VulkanBuffer indexBuffer, int indexCount) {}
