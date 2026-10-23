package com.restonic4.bloom.core.math;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameProfilerTest {
    @Test
    void reportsMeanMillisecondsPerFrameInFirstUseOrder() {
        Locale.setDefault(Locale.ROOT);
        FrameProfiler profiler = new FrameProfiler();

        for (int frame = 0; frame < 4; frame++) {
            profiler.record("fill", 3_000_000);
            profiler.record("write", frame * 1_000_000);
            profiler.endFrame();
        }

        assertEquals("fill 3.00 | write 1.50", profiler.report());
    }

    @Test
    void sectionRunningSeveralTimesInAFrameAddsUp() {
        Locale.setDefault(Locale.ROOT);
        FrameProfiler profiler = new FrameProfiler();

        profiler.record("fill", 1_000_000);
        profiler.record("fill", 2_000_000);
        profiler.endFrame();

        assertEquals("fill 3.00", profiler.report());
    }

    @Test
    void reportStartsANewWindow() {
        Locale.setDefault(Locale.ROOT);
        FrameProfiler profiler = new FrameProfiler();

        profiler.record("fill", 5_000_000);
        profiler.endFrame();
        profiler.report();

        profiler.record("fill", 1_000_000);
        profiler.endFrame();

        assertEquals("fill 1.00", profiler.report());
    }

    @Test
    void emptyWindowReportsNothing() {
        assertEquals("", new FrameProfiler().report());
    }
}
