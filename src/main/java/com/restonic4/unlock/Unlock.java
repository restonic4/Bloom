package com.restonic4.unlock;

import com.restonic4.bloom.core.graphics.GraphicsApi;

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
}
