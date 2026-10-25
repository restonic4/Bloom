package com.restonic4.bloom.backends.opengl.shader;

import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.graphics.ShaderSource;
import com.restonic4.bloom.core.graphics.ShaderStage;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

public final class OpenGlShaderProgram implements Disposable {
    private final int program;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    private OpenGlShaderProgram(int program) {
        this.program = program;
    }

    public static OpenGlShaderProgram create(OpenGlShaderStage... stages) {
        if (stages.length == 0) {
            throw new IllegalArgumentException("Shader program requires at least one shader stage");
        }

        int program = glCreateProgram();
        if (program == 0) {
            throw new IllegalStateException("Failed to create OpenGL shader program");
        }

        try {
            for (OpenGlShaderStage stage : stages) {
                glAttachShader(program, stage.getId());
            }

            glLinkProgram(program);

            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                String log = glGetProgramInfoLog(program);
                throw new IllegalStateException("Failed to link OpenGL shader program:\n" + log);
            }

            return new OpenGlShaderProgram(program);

        } catch (RuntimeException e) {
            glDeleteProgram(program);
            throw e;
        }
    }

    public static OpenGlShaderProgram fromSources(ShaderSource... sources) {
        OpenGlShaderStage[] stages = new OpenGlShaderStage[sources.length];

        try {
            for (int i = 0; i < sources.length; i++) {
                stages[i] = OpenGlShaderStage.create(sources[i]);
            }

            return create(stages);
        } finally {
            for (OpenGlShaderStage stage : stages) {
                if (stage != null) {
                    stage.dispose();
                }
            }
        }
    }

    public static OpenGlShaderProgram fromResources(String vertexPath, String fragmentPath) {
        return fromSources(
                new ShaderSource(vertexPath, ShaderStage.VERTEX),
                new ShaderSource(fragmentPath, ShaderStage.FRAGMENT)
        );
    }

    public static OpenGlShaderProgram fromStrings(String vertexSource, String fragmentSource) {
        OpenGlShaderStage vertex = OpenGlShaderStage.create(ShaderStage.VERTEX, vertexSource);
        OpenGlShaderStage fragment = OpenGlShaderStage.create(ShaderStage.FRAGMENT, fragmentSource);

        try {
            return create(vertex, fragment);
        } finally {
            vertex.dispose();
            fragment.dispose();
        }
    }

    public void bind() {
        glUseProgram(program);
    }

    public static void unbind() {
        glUseProgram(0);
    }

    public int getId() {
        return program;
    }

    // Uniforms

    public int getUniformLocation(String name) {
        return uniformLocations.computeIfAbsent(name, n -> glGetUniformLocation(program, n));
    }

    public void set(String name, int value) {
        int location = getUniformLocation(name);
        if (location != -1) glUniform1i(location, value);
    }

    public void set(String name, float value) {
        int location = getUniformLocation(name);
        if (location != -1) glUniform1f(location, value);
    }

    public void set(String name, boolean value) {
        set(name, value ? 1 : 0);
    }

    public void set(String name, float x, float y) {
        int location = getUniformLocation(name);
        if (location != -1) glUniform2f(location, x, y);
    }

    public void set(String name, float x, float y, float z) {
        int location = getUniformLocation(name);
        if (location != -1) glUniform3f(location, x, y, z);
    }

    public void set(String name, float x, float y, float z, float w) {
        int location = getUniformLocation(name);
        if (location != -1) glUniform4f(location, x, y, z, w);
    }

    public void set(String name, Vector2fc value) {
        set(name, value.x(), value.y());
    }

    public void set(String name, Vector3fc value) {
        set(name, value.x(), value.y(), value.z());
    }

    public void set(String name, Vector4fc value) {
        set(name, value.x(), value.y(), value.z(), value.w());
    }

    // TODO: reuse float buffer
    public void set(String name, Matrix4fc value) {
        int location = getUniformLocation(name);
        if (location == -1) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = value.get(stack.mallocFloat(16));
            glUniformMatrix4fv(location, false, buffer);
        }
    }

    @Override
    public void dispose() {
        glDeleteProgram(program);
    }
}