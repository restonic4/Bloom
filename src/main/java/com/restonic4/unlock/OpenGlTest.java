package com.restonic4.unlock;

import com.restonic4.bloom.backends.glfw.GlfwWindow;
import com.restonic4.bloom.backends.opengl.mesh.OpenGlMesh;
import com.restonic4.bloom.backends.opengl.rendering.OpenGlRenderer;
import com.restonic4.bloom.core.graphics.GraphicsApi;
import com.restonic4.bloom.core.math.Time;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB;

public class OpenGlTest {
    public static void init() {
        GlfwWindow window = new GlfwWindow(GraphicsApi.OPENGL, 500, 500);

        TestScene scene = new TestScene(window);
        OpenGlMesh mesh = new OpenGlMesh(scene.mesh());
        OpenGlMesh planeMesh = new OpenGlMesh(scene.planeMesh());
        OpenGlRenderer renderer = new OpenGlRenderer();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glEnable(GL_FRAMEBUFFER_SRGB);

        Time time = new Time();

        while (!window.shouldClose()) {
            window.pollEvents();

            time.update();
            scene.update(time.delta());

            window.makeContextCurrent();

            glClearColor(0.02f, 0.02f, 0.03f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            renderer.render(scene.camera(), window, mesh, scene.cubeMaterial(), scene.instances(), scene.color(time.elapsedSeconds()));
            renderer.render(scene.camera(), window, planeMesh, scene.planeMaterial(), scene.planeInstances(), scene.color(time.elapsedSeconds()));

            glfwSwapBuffers(window.getHandle());
        }

        renderer.dispose();
        planeMesh.dispose();
        mesh.dispose();
        window.dispose();
        glfwTerminate();
    }
}
