package com.restonic4.bloom.assets;

import com.restonic4.bloom.core.Resource;
import com.restonic4.bloom.core.scene.ImageData;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class ImageLoader {
    private ImageLoader() {}

    public static ImageData load(String resourcePath) {
        ByteBuffer file = Resource.loadBuffer(resourcePath);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer fileChannels = stack.mallocInt(1);

            ByteBuffer decoded = STBImage.stbi_load_from_memory(file, width, height, fileChannels, ImageData.CHANNELS);
            if (decoded == null) throw new RuntimeException("Failed to load image: " + resourcePath + "\nstb: " + STBImage.stbi_failure_reason());

            try {
                // Copied so the data outlives the stb allocation and needs no manual freeing; duplicate() because stbi_image_free reads the buffer position as part of the address
                ByteBuffer pixels = BufferUtils.createByteBuffer(decoded.remaining());
                pixels.put(decoded.duplicate()).flip();

                return new ImageData(width.get(0), height.get(0), pixels);
            } finally {
                STBImage.stbi_image_free(decoded);
            }
        } finally {
            MemoryUtil.memFree(file);
        }
    }
}
