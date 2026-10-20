package com.restonic4.bloom.backends.opengl.texture;

import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.scene.ImageData;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL21.GL_SRGB8_ALPHA8;

public class OpenGlTexture implements Disposable {
    private final int id;

    public OpenGlTexture(ImageData image) {
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);

        // The default minification filter expects mipmaps; without them the texture is incomplete and samples black
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_SRGB8_ALPHA8, image.width(), image.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, image.pixels());

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    @Override
    public void dispose() {
        glDeleteTextures(id);
    }
}
