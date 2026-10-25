package com.restonic4.bloom.backends.vulkan.shader;

import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.graphics.ShaderSource;
import com.restonic4.bloom.core.graphics.ShaderStage;

import java.nio.ByteBuffer;

import static org.lwjgl.util.shaderc.Shaderc.*;

public final class VulkanGlslCompiler implements Disposable {
    private final long compiler;

    public VulkanGlslCompiler() {
        compiler = shaderc_compiler_initialize();
        if (compiler == 0) throw new IllegalStateException("Failed to initialize Shaderc");
    }

    public byte[] compile(ShaderSource source) {
        String sourceCode = source.loadSource();

        int shaderKind = toShadercKind(source.stage());

        long options = shaderc_compile_options_initialize();
        if (options == 0) throw new IllegalStateException("Failed to create Shaderc compile options");

        try {
            shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_0);
            shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_zero);

            long result = shaderc_compile_into_spv(compiler, sourceCode, shaderKind, source.path(), "main", options);
            if (result == 0) throw new IllegalStateException("Shaderc returned a null compilation result: " + source.path());

            try {
                int status = shaderc_result_get_compilation_status(result);

                if (status != shaderc_compilation_status_success) {
                    String message = shaderc_result_get_error_message(result);
                    throw new IllegalStateException("Failed to compile shader: " + source.path() + "\n" + message);
                }

                long length = shaderc_result_get_length(result);

                ByteBuffer bytes = shaderc_result_get_bytes(result, length);

                byte[] spirv = new byte[(int) length];
                bytes.get(spirv);

                return spirv;

            } finally {
                shaderc_result_release(result);
            }

        } finally {
            shaderc_compile_options_release(options);
        }
    }

    private static int toShadercKind(ShaderStage stage) {
        return switch (stage) {
            case VERTEX -> shaderc_glsl_vertex_shader;
            case FRAGMENT -> shaderc_glsl_fragment_shader;
            case GEOMETRY -> shaderc_glsl_geometry_shader;
            case TESSELLATION_CONTROL -> shaderc_glsl_tess_control_shader;
            case TESSELLATION_EVALUATION -> shaderc_glsl_tess_evaluation_shader;
            case COMPUTE -> shaderc_glsl_compute_shader;
        };
    }

    @Override
    public void dispose() {
        if (compiler != 0) {
            shaderc_compiler_release(compiler);
        }
    }
}