package com.restonic4.bloom.core.scene;

// Backends use it as a map key, and a record would hash and compare every pixel
public final class Texture {
    private final ImageData image;
    private final WrapMode wrapMode;

    public Texture(ImageData image, WrapMode wrapMode) {
        this.image = image;
        this.wrapMode = wrapMode;
    }

    public static Texture load(String resourcePath) {
        return load(resourcePath, WrapMode.CLAMP);
    }

    public static Texture load(String resourcePath, WrapMode wrapMode) {
        return new Texture(ImageData.load(resourcePath), wrapMode);
    }

    public ImageData image() { return image; }
    public WrapMode wrapMode() { return wrapMode; }
}
