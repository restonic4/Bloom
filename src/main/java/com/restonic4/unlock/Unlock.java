package com.restonic4.unlock;

import com.restonic4.bloom.core.graphics.GraphicsApi;
import com.restonic4.bloom.core.math.Transform;
import com.restonic4.bloom.core.platform.SystemDetails;
import com.restonic4.bloom.core.scene.Mesh;
import com.restonic4.bloom.core.scene.MeshInstance;

public class Unlock {
    public static final GraphicsApi API = GraphicsApi.VULKAN;

    public static void main(String[] args) {
        System.setProperty("org.lwjgl.util.Debug", "true");
        System.setProperty("org.lwjgl.util.DebugAllocator", "true");
        System.setProperty("org.lwjgl.util.DebugStack", "true");

        if (API == GraphicsApi.VULKAN) {
            VulkanTest.init();
        } else if (API == GraphicsApi.OPENGL) {
            OpenGlTest.init();
        }
    }

    public static MeshInstance createCube(Mesh mesh, int x, int y, int z) {
        Transform transform = new Transform();
        transform.setPosition(x, y, z);

        return new MeshInstance(mesh, transform);
    }
}
