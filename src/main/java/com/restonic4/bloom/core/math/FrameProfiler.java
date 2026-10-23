package com.restonic4.bloom.core.math;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mean time per frame spent in named sections, between two reports. Sections are listed in the order they first ran. */
public final class FrameProfiler {
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final Map<String, long[]> totalNanos = new LinkedHashMap<>();
    private int frames;

    public long start() { return System.nanoTime(); }

    public void stop(String section, long startNanos) {
        record(section, System.nanoTime() - startNanos);
    }

    public void record(String section, long nanos) {
        totalNanos.computeIfAbsent(section, name -> new long[1])[0] += nanos;
    }

    public void endFrame() { frames++; }

    /** Like "fill 3.10 | write 0.80", in milliseconds per frame. Starts a new measuring window. */
    public String report() {
        StringBuilder text = new StringBuilder();

        if (frames > 0) {
            for (Map.Entry<String, long[]> entry : totalNanos.entrySet()) {
                if (!text.isEmpty()) text.append(" | ");
                text.append(String.format("%s %.2f", entry.getKey(), entry.getValue()[0] / NANOS_PER_MILLI / frames));
            }
        }

        totalNanos.clear();
        frames = 0;
        return text.toString();
    }
}
