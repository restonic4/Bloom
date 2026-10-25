package com.restonic4.bloom.core.math;

public final class Time {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /**
     * Maximum amount of time the game simulation is allowed to advance
     * in a single frame.
     */
    private static final long DEFAULT_MAX_DELTA_NANOS = 250_000_000L;

    private final long startTimeNanos;
    private final long maxDeltaNanos;

    private long lastTimeNanos;

    private long deltaNanos;
    private long frameCount;

    public Time() {
        this(DEFAULT_MAX_DELTA_NANOS);
    }

    public Time(long maxDeltaNanos) {
        if (maxDeltaNanos <= 0) {
            throw new IllegalArgumentException("maxDeltaNanos must be > 0");
        }

        long now = System.nanoTime();

        this.startTimeNanos = now;
        this.lastTimeNanos = now;
        this.maxDeltaNanos = maxDeltaNanos;
    }

    public void update() {
        final long now = System.nanoTime();

        long rawDelta = now - lastTimeNanos;
        if (rawDelta < 0) rawDelta = 0;

        deltaNanos = Math.min(rawDelta, maxDeltaNanos);

        lastTimeNanos = now;
        frameCount++;
    }

    public double delta() {
        return deltaNanos / (double) NANOS_PER_SECOND;
    }

    public double elapsedSeconds() {
        return elapsedNanos() / (double) NANOS_PER_SECOND;
    }

    public double elapsedMillis() {
        return elapsedNanos() / 1_000_000.0;
    }

    public long elapsedNanos() {
        return System.nanoTime() - startTimeNanos;
    }

    public long frameCount() {
        return frameCount;
    }

    public long maxDeltaNanos() {
        return maxDeltaNanos;
    }

    public double maxDeltaSeconds() {
        return maxDeltaNanos / (double) NANOS_PER_SECOND;
    }
}