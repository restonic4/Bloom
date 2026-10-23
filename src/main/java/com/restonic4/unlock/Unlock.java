package com.restonic4.unlock;

import com.restonic4.bloom.core.graphics.GraphicsApi;

import javax.swing.JOptionPane;

public class Unlock {
    public static void main(String[] args) {
        System.setProperty("org.lwjgl.util.Debug", "true");
        System.setProperty("org.lwjgl.util.DebugAllocator", "true");
        System.setProperty("org.lwjgl.util.DebugStack", "true");

        GraphicsApi api = askGraphicsApi();
        if (api == null) return;

        if (api == GraphicsApi.VULKAN) {
            VulkanTest.init();
        } else if (api == GraphicsApi.OPENGL) {
            OpenGlTest.init();
        }
    }

    private static GraphicsApi askGraphicsApi() {
        GraphicsApi[] options = GraphicsApi.values();

        int choice = JOptionPane.showOptionDialog(
                null,
                "Graphics API",
                "Unlock",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        return choice == JOptionPane.CLOSED_OPTION ? null : options[choice];
    }
}
