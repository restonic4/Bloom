package com.restonic4.bloom.backends.vulkan.memory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VulkanDeferredDisposerTest {
    private final VulkanDeferredDisposer disposer = new VulkanDeferredDisposer();
    private final List<String> freed = new ArrayList<>();

    private void retire(String name, long value) {
        disposer.retire(() -> freed.add(name), value);
    }

    @Test
    void nothingIsFreedBeforeTheGpuReachesItsValue() {
        retire("a", 5);

        disposer.collect(4);

        assertEquals(List.of(), freed);
        assertEquals(1, disposer.pendingCount());
    }

    @Test
    void resourceIsFreedOnceTheCompletedValueReachesIt() {
        retire("a", 5);

        disposer.collect(5);

        assertEquals(List.of("a"), freed);
        assertEquals(0, disposer.pendingCount());
    }

    @Test
    void onlyTheResourcesThatAreSafeAreFreedAndInOrder() {
        retire("a", 3);
        retire("b", 4);
        retire("c", 7);

        disposer.collect(4);

        assertEquals(List.of("a", "b"), freed);
        assertEquals(1, disposer.pendingCount());

        disposer.collect(10);
        assertEquals(List.of("a", "b", "c"), freed);
    }

    @Test
    void resourcesRetiredAtTheSameValueAreFreedTogether() {
        retire("a", 2);
        retire("b", 2);

        disposer.collect(2);

        assertEquals(List.of("a", "b"), freed);
    }

    @Test
    void aResourceIsNeverFreedTwice() {
        retire("a", 1);

        disposer.collect(1);
        disposer.collect(9);

        assertEquals(List.of("a"), freed);
    }

    @Test
    void retiringWithAnOlderValueThanBeforeIsRejected() {
        retire("a", 5);

        assertThrows(IllegalArgumentException.class, () -> retire("b", 4));
        assertEquals(1, disposer.pendingCount());
    }

    @Test
    void disposeFreesEverythingStillPending() {
        retire("a", 100);
        retire("b", 200);

        disposer.dispose();

        assertEquals(List.of("a", "b"), freed);
        assertEquals(0, disposer.pendingCount());
    }
}
