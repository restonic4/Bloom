package com.restonic4.unlock;

import com.restonic4.bloom.core.graphics.GraphicsApi;
import com.restonic4.bloom.core.launch.LaunchOptions;

import javax.swing.JOptionPane;

public class Unlock {
    public static void main(String[] args) {
        System.setProperty("org.lwjgl.util.Debug", "true");
        System.setProperty("org.lwjgl.util.DebugAllocator", "true");
        System.setProperty("org.lwjgl.util.DebugStack", "true");

        LaunchOptions options;
        try {
            options = LaunchOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(LaunchOptions.USAGE);
            System.exit(2);
            return;
        }

        if (options.help()) {
            System.out.println(LaunchOptions.USAGE);
            return;
        }

        GraphicsApi api = options.backend().orElseGet(Unlock::askGraphicsApi);
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
