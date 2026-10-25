package com.restonic4.bloom.core.scene;

import java.nio.ByteBuffer;

public record ImageData(int width, int height, ByteBuffer pixels) {
    public static final int CHANNELS = 4;

    public ImageData {
        if (pixels.remaining() != width * height * CHANNELS) {
            throw new IllegalArgumentException("Pixel data does not match " + width + "x" + height + " RGBA");
        }
    }
}
