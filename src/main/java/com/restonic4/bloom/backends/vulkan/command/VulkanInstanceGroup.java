package com.restonic4.bloom.backends.vulkan.command;

import com.restonic4.bloom.backends.vulkan.buffer.VulkanBuffer;

import java.util.List;

// Draws whose per-instance data lives in one buffer: the dynamic instances, or one static chunk
public record VulkanInstanceGroup(VulkanBuffer instanceBuffer, List<VulkanMeshDraw> draws) {}
