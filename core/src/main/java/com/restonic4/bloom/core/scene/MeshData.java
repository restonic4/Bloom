package com.restonic4.bloom.core.scene;

public record MeshData(float[] vertices, float[] normals, float[] tangents, float[] uvs, int[] indices) {
    public MeshData {
        if (vertices.length / 3 != normals.length / 3 || vertices.length / 3 != tangents.length / 3 || vertices.length / 3 != uvs.length / 2) {
            throw new IllegalArgumentException("Vertex attribute arrays have different sizes");
        }
    }

    public static final int FLOATS_PER_VERTEX = 3 + 3 + 3 + 2; // P + N + T + UV
    public static final int UV_OFFSET_FLOATS = 9;

    public float[] interleavedVertices() {
        float[] packed = new float[vertexCount() * FLOATS_PER_VERTEX];

        for (int i = 0; i < vertexCount(); i++) {
            int out = i * FLOATS_PER_VERTEX;

            System.arraycopy(vertices, i * 3, packed, out, 3);
            System.arraycopy(normals, i * 3, packed, out + 3, 3);
            System.arraycopy(tangents, i * 3, packed, out + 6, 3);
            System.arraycopy(uvs, i * 2, packed, out + UV_OFFSET_FLOATS, 2);
        }

        return packed;
    }

    public int vertexCount() {
        return vertices.length / 3;
    }

    public int indexCount() {
        return indices.length;
    }
}
