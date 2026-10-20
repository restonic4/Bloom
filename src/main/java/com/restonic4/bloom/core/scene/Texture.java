package com.restonic4.bloom.core.scene;

// Backends use it as a map key, and a record would hash and compare every pixel
public final class Texture {
    private final ImageData image;

    public Texture(ImageData image) {
        this.image = image;
    }

    public static Texture load(String resourcePath) {
        return new Texture(ImageData.load(resourcePath));
    }

    public ImageData image() { return image; }
}
