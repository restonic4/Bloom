package com.restonic4.bloom.backends.opengl.rendering;

import com.restonic4.bloom.backends.opengl.mesh.OpenGlMesh;
import com.restonic4.bloom.backends.opengl.shader.OpenGlShaderProgram;
import com.restonic4.bloom.backends.opengl.texture.OpenGlTexture;
import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.rendering.RenderTarget;
import com.restonic4.bloom.core.scene.Camera;
import com.restonic4.bloom.core.scene.Material;
import com.restonic4.bloom.core.scene.Texture;
import com.restonic4.bloom.core.scene.MeshInstance;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;

public class OpenGlRenderer implements Disposable {
    private OpenGlShaderProgram shader;

    // GPU copies of core textures, created the first time a material using them is drawn
    private final Map<Texture, OpenGlTexture> textures = new HashMap<>();

    public OpenGlRenderer() {
        this.shader = OpenGlShaderProgram.fromResources("core.vsh", "core.fsh");;
    }

    // TODO: reuse float buffer
    public void render(Camera mainCamera, RenderTarget renderTarget, OpenGlMesh mesh, Material material, List<MeshInstance> instances, float[] color) {
        shader.bind();
        shader.set("uTexture", 0);
        textures.computeIfAbsent(material.albedo(), OpenGlTexture::new).bind(0);

        float aspectRatio = renderTarget.getAspectRatio();
        Matrix4fc projectionMatrix = mainCamera.getProjectionMatrix(aspectRatio);
        Matrix4fc viewMatrix = mainCamera.getViewMatrix();

        shader.set("uProjection", projectionMatrix);
        shader.set("uView", viewMatrix);
        shader.set("uColor", color[0], color[1], color[2], color[3]);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer instanceBuffer = stack.mallocFloat(instances.size() * 16);

            for (int i = 0; i < instances.size(); i++) {
                instances.get(i).getTransform().getModelMatrix().get(i * 16, instanceBuffer);
            }

            instanceBuffer.limit(instances.size() * 16);
            instanceBuffer.position(0);

            mesh.uploadInstances(instanceBuffer);
        }

        mesh.bind();

        glDrawElementsInstanced(GL_TRIANGLES, mesh.getIndexCount(), GL_UNSIGNED_INT, 0, instances.size());

        mesh.unbind();
        OpenGlShaderProgram.unbind();
    }

    @Override
    public void dispose() {
        textures.values().forEach(OpenGlTexture::dispose);
        shader.dispose();
    }
}
