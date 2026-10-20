package com.restonic4.unlock;

import com.restonic4.bloom.backends.glfw.GlfwWindow;
import com.restonic4.bloom.backends.opengl.mesh.OpenGlMesh;
import com.restonic4.bloom.backends.opengl.rendering.OpenGlRenderer;
import com.restonic4.bloom.backends.opengl.texture.OpenGlTexture;
import com.restonic4.bloom.core.math.Time;
import com.restonic4.bloom.core.scene.ImageData;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB;

public class OpenGlTest {
    public static void init() {
        GlfwWindow window = new GlfwWindow(500, 500);

        TestScene scene = new TestScene();
        OpenGlMesh mesh = new OpenGlMesh(scene.mesh());
        OpenGlMesh planeMesh = new OpenGlMesh(scene.planeMesh());
        OpenGlRenderer renderer = new OpenGlRenderer();
        OpenGlTexture texture = new OpenGlTexture(ImageData.load("checker.png"));

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_FRAMEBUFFER_SRGB);

        Time time = new Time();

        while (!window.shouldClose()) {
            time.update();
            scene.update(time.delta());

            window.makeContextCurrent();

            glfwPollEvents();

            glClearColor(0.02f, 0.02f, 0.03f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            texture.bind(0);

            renderer.render(scene.camera(), window, mesh, scene.instances(), scene.color(time.elapsedSeconds()));
            renderer.render(scene.camera(), window, planeMesh, scene.planeInstances(), scene.color(time.elapsedSeconds()));

            glfwSwapBuffers(window.getHandle());
        }

        texture.dispose();
        planeMesh.dispose();
        mesh.dispose();
        window.dispose();
        glfwTerminate();
    }
}
