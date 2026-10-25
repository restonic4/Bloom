package com.restonic4.bloom.assets;

import com.restonic4.bloom.core.scene.ImageData;
import com.restonic4.bloom.core.scene.Texture;
import com.restonic4.bloom.core.scene.WrapMode;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageLoaderTest {
    @Test
    void decodesPixelsAsRgbaInRowOrder() {
        ImageData image = ImageLoader.load("pixels-2x2.png");

        assertEquals(2, image.width());
        assertEquals(2, image.height());

        // red, green / blue, half-transparent white
        byte[] expected = {
                (byte) 255, 0, 0, (byte) 255,
                0, (byte) 255, 0, (byte) 255,
                0, 0, (byte) 255, (byte) 255,
                (byte) 255, (byte) 255, (byte) 255, (byte) 128
        };

        ByteBuffer pixels = image.pixels();
        byte[] actual = new byte[pixels.remaining()];
        pixels.duplicate().get(actual);

        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "byte " + i);
        }
    }

    @Test
    void failsOnMissingResource() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> ImageLoader.load("does-not-exist.png"));
        assertTrue(e.getMessage().contains("does-not-exist.png"));
    }

    @Test
    void failsOnDataThatIsNotAnImage() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> ImageLoader.load("not-an-image.txt"));
        assertTrue(e.getMessage().startsWith("Failed to load image"));
    }

    @Test
    void textureDefaultsToClampAndKeepsRequestedWrap() {
        Texture clamped = TextureLoader.load("pixels-2x2.png");
        Texture repeated = TextureLoader.load("pixels-2x2.png", WrapMode.REPEAT);

        assertSame(WrapMode.CLAMP, clamped.wrapMode());
        assertSame(WrapMode.REPEAT, repeated.wrapMode());
        assertEquals(2, repeated.image().width());
    }
}
