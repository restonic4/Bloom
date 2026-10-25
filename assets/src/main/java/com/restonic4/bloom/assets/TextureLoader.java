package com.restonic4.bloom.assets;

import com.restonic4.bloom.core.scene.Texture;
import com.restonic4.bloom.core.scene.WrapMode;

public final class TextureLoader {
    private TextureLoader() {}

    public static Texture load(String resourcePath) {
        return load(resourcePath, WrapMode.CLAMP);
    }

    public static Texture load(String resourcePath, WrapMode wrapMode) {
        return new Texture(ImageLoader.load(resourcePath), wrapMode);
    }
}
