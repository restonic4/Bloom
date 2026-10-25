package com.restonic4.bloom.backends.opengl.shader;

import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.graphics.ShaderSource;
import com.restonic4.bloom.core.graphics.ShaderStage;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL40.GL_TESS_CONTROL_SHADER;
import static org.lwjgl.opengl.GL40.GL_TESS_EVALUATION_SHADER;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;

public final class OpenGlShaderStage implements Disposable {
    private final ShaderStage stage;
    private final int id;

    private OpenGlShaderStage(ShaderStage stage, int id) {
        this.stage = stage;
        this.id = id;
    }

    public static OpenGlShaderStage create(ShaderSource source) {
        return create(source.stage(), source.loadSource(), source.path());
    }

    public static OpenGlShaderStage create(ShaderStage stage, String source) {
        return create(stage, source, "<memory>");
    }

    private static OpenGlShaderStage create(ShaderStage stage, String source, String debugName) {
        int shaderType = toGlShaderType(stage);

        int shader = glCreateShader(shaderType);
        if (shader == 0) {
            throw new IllegalStateException("Failed to create OpenGL shader object for " + debugName);
        }

        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);

            throw new IllegalStateException(
                    "Failed to compile "
                            + stage.name().toLowerCase()
                            + " shader '"
                            + debugName
                            + "':\n"
                            + log
            );
        }

        return new OpenGlShaderStage(stage, shader);
    }

    private static int toGlShaderType(ShaderStage stage) {
        return switch (stage) {
            case VERTEX -> GL_VERTEX_SHADER;
            case FRAGMENT -> GL_FRAGMENT_SHADER;
            case GEOMETRY -> GL_GEOMETRY_SHADER;
            case TESSELLATION_CONTROL -> GL_TESS_CONTROL_SHADER;
            case TESSELLATION_EVALUATION -> GL_TESS_EVALUATION_SHADER;
            case COMPUTE -> GL_COMPUTE_SHADER;
        };
    }

    public ShaderStage stage() { return stage; }

    public int getId() { return id; }

    @Override
    public void dispose() {
        glDeleteShader(id);
    }
}