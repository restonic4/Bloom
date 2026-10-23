package com.restonic4.unlock;

import com.restonic4.bloom.backends.glfw.GlfwWindow;
import com.restonic4.bloom.backends.opengl.rendering.OpenGlRenderer;
import com.restonic4.bloom.core.graphics.GraphicsApi;
import com.restonic4.bloom.core.math.FrameStats;
import com.restonic4.bloom.core.math.FrameProfiler;
import com.restonic4.bloom.core.math.Time;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB;

public class OpenGlTest {
    public static void init() {
        GlfwWindow window = new GlfwWindow(GraphicsApi.OPENGL, 500, 500);

        TestScene scene = new TestScene(window, "opengl");
        FrameProfiler profiler = new FrameProfiler();
        OpenGlRenderer renderer = new OpenGlRenderer(profiler);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glEnable(GL_FRAMEBUFFER_SRGB);

        Time time = new Time();
        FrameStats stats = new FrameStats();

        while (!window.shouldClose()) {
            window.pollEvents();

            time.update();
            long updateStart = profiler.start();
            scene.update(time.delta());
            profiler.stop("update", updateStart);
            scene.showStats(window, stats, profiler, time.delta());

            window.makeContextCurrent();

            glClearColor(0.02f, 0.02f, 0.03f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            renderer.render(scene.camera(), window, scene.renderScene().batches(), scene.color(time.elapsedSeconds()));

            long swapStart = profiler.start();
            glfwSwapBuffers(window.getHandle());
            profiler.stop("swap", swapStart);
        }

        renderer.dispose();
        window.dispose();
        glfwTerminate();
    }
}
