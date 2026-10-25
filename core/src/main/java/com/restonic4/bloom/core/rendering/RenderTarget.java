package com.restonic4.bloom.core.rendering;

public abstract class RenderTarget {
    protected int width, height;

    public RenderTarget(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public float getAspectRatio() { return (float) width / (float) height; }
}
