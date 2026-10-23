package com.restonic4.bloom.backends.vulkan.memory;

import com.restonic4.bloom.core.Disposable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Frees GPU resources only once the GPU is done with every frame that could still use them.
 * Values are the graphics timeline's: a resource retired at value N is freed when the completed value reaches N.
 */
public final class VulkanDeferredDisposer implements Disposable {
    private record Retired(Disposable resource, long freeAtCompletedValue) {}

    // Retired values never decrease, so the oldest entry is always at the front
    private final Deque<Retired> retired = new ArrayDeque<>();

    /** @param lastUsingValue the value of the last submitted frame, the latest one that may still read the resource */
    public void retire(Disposable resource, long lastUsingValue) {
        if (!retired.isEmpty() && lastUsingValue < retired.peekLast().freeAtCompletedValue()) {
            throw new IllegalArgumentException("Values must not go backwards: " + lastUsingValue);
        }

        retired.addLast(new Retired(resource, lastUsingValue));
    }

    public void collect(long completedValue) {
        while (!retired.isEmpty() && retired.peekFirst().freeAtCompletedValue() <= completedValue) {
            retired.removeFirst().resource().dispose();
        }
    }

    public int pendingCount() { return retired.size(); }

    /** Frees everything. Only call it after the device is idle. */
    @Override
    public void dispose() {
        while (!retired.isEmpty()) retired.removeFirst().resource().dispose();
    }
}
