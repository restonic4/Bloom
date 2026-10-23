package com.restonic4.bloom.backends.opengl.texture;

import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.graphics.GraphicsSettings;
import com.restonic4.bloom.core.scene.ImageData;
import com.restonic4.bloom.core.scene.Texture;

import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL21.GL_SRGB8_ALPHA8;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

public class OpenGlTexture implements Disposable {
    private final int id;

    public OpenGlTexture(Texture texture) {
        ImageData image = texture.image();

        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        applyAnisotropy();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_SRGB8_ALPHA8, image.width(), image.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, image.pixels());
        glGenerateMipmap(GL_TEXTURE_2D);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    // Not part of core OpenGL 3.3: a driver without the extension keeps plain trilinear filtering
    private static void applyAnisotropy() {
        if (!GL.getCapabilities().GL_EXT_texture_filter_anisotropic) return;

        float level = Math.min(GraphicsSettings.MAX_ANISOTROPY, glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT));
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, level);
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
