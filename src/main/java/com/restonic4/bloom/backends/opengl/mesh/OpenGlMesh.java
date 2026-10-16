package com.restonic4.bloom.backends.opengl.mesh;

import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.scene.MeshData;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

public class OpenGlMesh implements Disposable {
    private static final int FLOATS_PER_VERTEX = 3 + 3 + 3 + 2; // P + N + T + UV
    private static final int STRIDE = FLOATS_PER_VERTEX * Float.BYTES;

    private final int vao;

    private final int vertexVbo;
    private final int indexVbo;
    private final int instanceVbo;

    private final MeshData data;

    public OpenGlMesh(MeshData data) {
        this.data = data;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vertexVbo = createVertexVbo(data.vertexCount(), data.vertices(), data.normals(), data.tangents(), data.uvs());
        indexVbo = createIndexVbo(data.indices());
        instanceVbo = createInstanceVbo();

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public static int createVertexVbo(int vertexCount, float[] vertices, float[] normals, float[] tangents, float[] uvs) {
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertexCount * FLOATS_PER_VERTEX);

        for (int i = 0; i < vertexCount; i++) {
            // Position
            vertexBuffer.put(vertices[i * 3]);
            vertexBuffer.put(vertices[i * 3 + 1]);
            vertexBuffer.put(vertices[i * 3 + 2]);

            // Normal
            vertexBuffer.put(normals[i * 3]);
            vertexBuffer.put(normals[i * 3 + 1]);
            vertexBuffer.put(normals[i * 3 + 2]);

            // Tangent
            vertexBuffer.put(tangents[i * 3]);
            vertexBuffer.put(tangents[i * 3 + 1]);
            vertexBuffer.put(tangents[i * 3 + 2]);

            // UV
            vertexBuffer.put(uvs[i * 2]);
            vertexBuffer.put(uvs[i * 2 + 1]);
        }

        vertexBuffer.flip();

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        // position
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, STRIDE, 0);

        // normal
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, STRIDE, 3L * Float.BYTES);

        // tangent
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, STRIDE, 6L * Float.BYTES);

        // uv
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 2, GL_FLOAT, false, STRIDE, 9L * Float.BYTES);

        return vbo;
    }

    public static int createIndexVbo(int[] indices) {
        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices.length);
        indexBuffer.put(indices).flip();

        int vbo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);

        return vbo;
    }

    public static int createInstanceVbo() {
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        glBufferData(GL_ARRAY_BUFFER, 16L * Float.BYTES, GL_STREAM_DRAW);

        // mat4 occupies 4 attribute locations: 4,5,6,7, OpenGL supports at max 4 floats, we need to split it.
        for (int i = 0; i < 4; i++) {
            int location = 4 + i;

            glEnableVertexAttribArray(location);
            glVertexAttribPointer(location, 4, GL_FLOAT, false, 16 * Float.BYTES, i * 4L * Float.BYTES);
            glVertexAttribDivisor(location, 1); // Change the attribute each instance, not per vertex
        }

        return vbo;
    }

    public void bind() {
        glBindVertexArray(vao);
    }

    public void unbind() {
        glBindVertexArray(0);
    }

    public void uploadInstances(FloatBuffer data) {
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void dispose() {
        glDeleteBuffers(vertexVbo);
        glDeleteBuffers(indexVbo);
        glDeleteBuffers(instanceVbo);
        glDeleteVertexArrays(vao);
    }

    public static List<OpenGlMesh> load(String resourcePath) {
        return MeshData.load(resourcePath).stream().map(OpenGlMesh::new).toList();
    }

    public MeshData getData() { return data; }
    public int getIndexCount() { return data.indexCount(); }
}
