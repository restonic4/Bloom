package com.restonic4.bloom.backends.vulkan.memory;

/** Who touches a resource, which is how the allocator decides where to put it. */
public enum VulkanMemoryUsage {
    /** Only the GPU uses it. Filled through a staging buffer. Fastest for the GPU. */
    DEVICE_LOCAL,

    /** The CPU writes it, often every frame, and the GPU reads it. */
    HOST_WRITABLE
}
