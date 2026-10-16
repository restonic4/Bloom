package com.restonic4.bloom.backends.vulkan.camera;

import com.restonic4.bloom.core.scene.Camera;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class VulkanClipSpace {
    // TODO: Camera builds OpenGL clip space (depth -1..1, y up) but Vulkan wants depth 0..1 and y down.
    //  The correction lives here only because the OpenGL/Vulkan abstraction does not exist yet.
    //  When it does, move it into Camera (a clip-space setting) and delete this class.
    //  The y flip reverses triangle winding: remember it when turning on face culling.
    private static final Matrix4fc OPENGL_TO_VULKAN = new Matrix4f()
            .m11(-1.0f)
            .m22(0.5f)
            .m32(0.5f);

    private VulkanClipSpace() {}

    public static Matrix4f viewProjection(Camera camera, float aspectRatio, Matrix4f dest) {
        return dest.set(OPENGL_TO_VULKAN)
                .mul(camera.getProjectionMatrix(aspectRatio))
                .mul(camera.getViewMatrix());
    }
}
