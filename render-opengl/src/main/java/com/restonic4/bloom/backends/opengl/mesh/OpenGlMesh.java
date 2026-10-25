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
    private static final int STRIDE = MeshData.FLOATS_PER_VERTEX * Float.BYTES;
    private static final int MATRIX_BYTES = 16 * Float.BYTES;

    private final int vao;

    private final int vertexVbo;
    private final int indexVbo;
    private final int instanceVbo;

    private final MeshData data;

    public OpenGlMesh(MeshData data) {
        this.data = data;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vertexVbo = createVertexVbo(data.interleavedVertices());
        indexVbo = createIndexVbo(data.indices());
        instanceVbo = createInstanceVbo();

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public static int createVertexVbo(float[] interleavedVertices) {
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(interleavedVertices.length);
        vertexBuffer.put(interleavedVertices).flip();

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

        glBufferData(GL_ARRAY_BUFFER, MATRIX_BYTES, GL_STREAM_DRAW);

        pointInstanceAttributes(0);

        return vbo;
    }

    // mat4 occupies 4 attribute locations: 4,5,6,7, OpenGL supports at max 4 floats, we need to split it.
    // Reads from the buffer bound to GL_ARRAY_BUFFER, starting at byteOffset, and stays tied to it in the bound VAO
    private static void pointInstanceAttributes(long byteOffset) {
        for (int i = 0; i < 4; i++) {
            int location = 4 + i;

            glEnableVertexAttribArray(location);
            glVertexAttribPointer(location, 4, GL_FLOAT, false, MATRIX_BYTES, byteOffset + i * 4L * Float.BYTES);
            glVertexAttribDivisor(location, 1); // Change the attribute each instance, not per vertex
        }
    }

    public void bind() {
        glBindVertexArray(vao);
    }

    public void unbind() {
        glBindVertexArray(0);
    }

    /** Fills this mesh's own instance buffer and makes the draw read from it. The mesh must be bound. */
    public void uploadInstances(FloatBuffer data) {
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
        pointInstanceAttributes(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /** Makes the draw read matrices from another buffer, starting at the given instance. The mesh must be bound. */
    public void useInstanceBuffer(int vbo, int firstInstance) {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        pointInstanceAttributes((long) firstInstance * MATRIX_BYTES);
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
