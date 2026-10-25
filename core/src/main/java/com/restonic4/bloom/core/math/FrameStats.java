package com.restonic4.bloom.core.math;

/** Frame rate averaged over one second windows. */
public final class FrameStats {
    private double elapsedSeconds;
    private int frames;

    private double fps;
    private double averageFrameMillis;

    /** Returns true once per second, when new numbers are ready. */
    public boolean update(double deltaSeconds) {
        elapsedSeconds += deltaSeconds;
        frames++;

        if (elapsedSeconds < 1.0) return false;

        fps = frames / elapsedSeconds;
        averageFrameMillis = elapsedSeconds * 1000.0 / frames;

        elapsedSeconds = 0.0;
        frames = 0;
        return true;
    }

    public double fps() { return fps; }
    public double averageFrameMillis() { return averageFrameMillis; }
}
