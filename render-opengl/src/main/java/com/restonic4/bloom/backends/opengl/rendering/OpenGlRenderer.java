package com.restonic4.bloom.backends.opengl.rendering;

import com.restonic4.bloom.backends.opengl.mesh.OpenGlMesh;
import com.restonic4.bloom.backends.opengl.shader.OpenGlShaderProgram;
import com.restonic4.bloom.backends.opengl.texture.OpenGlTexture;
import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.math.FrameProfiler;
import com.restonic4.bloom.core.rendering.RenderTarget;
import com.restonic4.bloom.core.scene.Camera;
import com.restonic4.bloom.core.scene.DrawBatch;
import com.restonic4.bloom.core.scene.MeshData;
import com.restonic4.bloom.core.scene.MeshInstance;
import com.restonic4.bloom.core.scene.Scene;
import com.restonic4.bloom.core.scene.StaticChunk;
import com.restonic4.bloom.core.scene.Texture;
import org.joml.Matrix4fc;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;

public class OpenGlRenderer implements Disposable {
    private static final int FLOATS_PER_MATRIX = 16;

    private record GpuChunk(long version, int instanceVbo) {}

    private OpenGlShaderProgram shader;
    private final FrameProfiler profiler;

    // GPU copies of core meshes and textures, created the first time a batch using them is drawn
    private final Map<MeshData, OpenGlMesh> meshes = new HashMap<>();
    private final Map<Texture, OpenGlTexture> textures = new HashMap<>();

    // One buffer of matrices per static chunk, uploaded once and again only when that chunk changes
    private final Map<StaticChunk, GpuChunk> staticChunks = new HashMap<>();
    private long syncedStaticVersion = -1;

    // CPU copy of the model matrices being uploaded, reused. Not a MemoryStack: that is 64 KB, about 1000 matrices
    private FloatBuffer instanceData = memAllocFloat(1024 * FLOATS_PER_MATRIX);

    public OpenGlRenderer(FrameProfiler profiler) {
        this.profiler = profiler;
        this.shader = OpenGlShaderProgram.fromResources("core.vsh", "core.fsh");;
    }

    public void render(Camera mainCamera, RenderTarget renderTarget, Scene scene, float[] color) {
        shader.bind();
        shader.set("uTexture", 0);

        float aspectRatio = renderTarget.getAspectRatio();
        Matrix4fc projectionMatrix = mainCamera.getProjectionMatrix(aspectRatio);
        Matrix4fc viewMatrix = mainCamera.getViewMatrix();

        shader.set("uProjection", projectionMatrix);
        shader.set("uView", viewMatrix);
        shader.set("uColor", color[0], color[1], color[2], color[3]);

        for (DrawBatch batch : scene.dynamicBatches()) {
            drawDynamic(batch);
        }

        syncStatic(scene);

        long drawStart = profiler.start();
        for (StaticChunk chunk : scene.staticChunks()) {
            drawStatic(chunk, staticChunks.get(chunk).instanceVbo());
        }
        profiler.stop("draw", drawStart);

        OpenGlShaderProgram.unbind();
    }

    private void drawDynamic(DrawBatch batch) {
        OpenGlMesh mesh = meshFor(batch);
        List<MeshInstance> instances = batch.instances();

        textureFor(batch).bind(0);

        long fillStart = profiler.start();
        fillInstanceMatrices(instances);
        profiler.stop("fill", fillStart);

        mesh.bind();

        long uploadStart = profiler.start();
        mesh.uploadInstances(instanceData);
        profiler.stop("upload", uploadStart);

        long drawStart = profiler.start();
        glDrawElementsInstanced(GL_TRIANGLES, mesh.getIndexCount(), GL_UNSIGNED_INT, 0, instances.size());
        profiler.stop("draw", drawStart);

        mesh.unbind();
    }

    private void drawStatic(StaticChunk chunk, int instanceVbo) {
        int firstInstance = 0;

        for (DrawBatch batch : chunk.batches()) {
            OpenGlMesh mesh = meshFor(batch);
            int batchSize = batch.instances().size();

            textureFor(batch).bind(0);

            mesh.bind();
            mesh.useInstanceBuffer(instanceVbo, firstInstance);
            glDrawElementsInstanced(GL_TRIANGLES, mesh.getIndexCount(), GL_UNSIGNED_INT, 0, batchSize);
            mesh.unbind();

            firstInstance += batchSize;
        }
    }

    // New or changed chunks get a fresh buffer; deleting the old one right away is fine in OpenGL, the driver keeps it until queued draws are done
    private void syncStatic(Scene scene) {
        if (scene.staticVersion() == syncedStaticVersion) return;

        long start = profiler.start();

        Set<StaticChunk> inScene = new HashSet<>(scene.staticChunks());
        staticChunks.entrySet().removeIf(entry -> {
            if (inScene.contains(entry.getKey())) return false;

            glDeleteBuffers(entry.getValue().instanceVbo());
            return true;
        });

        for (StaticChunk chunk : scene.staticChunks()) {
            GpuChunk gpu = staticChunks.get(chunk);
            if (gpu != null && gpu.version() == chunk.version()) continue;

            if (gpu != null) glDeleteBuffers(gpu.instanceVbo());
            staticChunks.put(chunk, uploadChunk(chunk));
        }

        syncedStaticVersion = scene.staticVersion();
        profiler.stop("static", start);
    }

    private GpuChunk uploadChunk(StaticChunk chunk) {
        fillBatchMatrices(chunk.batches(), chunk.instanceCount());

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, instanceData, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        return new GpuChunk(chunk.version(), vbo);
    }

    private OpenGlMesh meshFor(DrawBatch batch) {
        return meshes.computeIfAbsent(batch.mesh(), OpenGlMesh::new);
    }

    private OpenGlTexture textureFor(DrawBatch batch) {
        return textures.computeIfAbsent(batch.material().albedo(), OpenGlTexture::new);
    }

    // Matrices of the batches' instances, batch after batch: the order the draw calls expect
    private void fillBatchMatrices(List<DrawBatch> batches, int instanceCount) {
        int index = 0;
        prepareInstanceData(instanceCount);

        for (DrawBatch batch : batches) {
            for (MeshInstance instance : batch.instances()) {
                instance.getTransform().getModelMatrix().get(index++ * FLOATS_PER_MATRIX, instanceData);
            }
        }
    }

    private void fillInstanceMatrices(List<MeshInstance> instances) {
        prepareInstanceData(instances.size());

        for (int i = 0; i < instances.size(); i++) {
            instances.get(i).getTransform().getModelMatrix().get(i * FLOATS_PER_MATRIX, instanceData);
        }
    }

    private void prepareInstanceData(int instanceCount) {
        int floats = instanceCount * FLOATS_PER_MATRIX;
        if (instanceData.capacity() < floats) {
            instanceData = memRealloc(instanceData, Math.max(floats, instanceData.capacity() * 2));
        }

        instanceData.clear();
        instanceData.limit(floats);
    }

    @Override
    public void dispose() {
        meshes.values().forEach(OpenGlMesh::dispose);
        textures.values().forEach(OpenGlTexture::dispose);
        staticChunks.values().forEach(chunk -> glDeleteBuffers(chunk.instanceVbo()));
        shader.dispose();
        memFree(instanceData);
    }
}
