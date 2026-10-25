package com.restonic4.bloom.assets;

import com.restonic4.bloom.core.Resource;
import com.restonic4.bloom.core.scene.MeshData;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class MeshLoader {
    private MeshLoader() {}

    public static List<MeshData> load(String resourcePath) {
        ByteBuffer data = Resource.loadBuffer(resourcePath);

        try {
            String extension = getExtension(resourcePath);

            AIScene scene = Assimp.aiImportFileFromMemory(data, getImportFlags(), extension);
            if (scene == null) throw new RuntimeException( "Failed to load model: " + resourcePath + "\nAssimp: " + Assimp.aiGetErrorString() );

            try {
                return convertScene(scene);
            } finally {
                Assimp.aiReleaseImport(scene);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static int getImportFlags() {
        return Assimp.aiProcess_Triangulate
                | Assimp.aiProcess_GenSmoothNormals
                | Assimp.aiProcess_CalcTangentSpace
                | Assimp.aiProcess_JoinIdenticalVertices
                | Assimp.aiProcess_ImproveCacheLocality;
    }

    private static String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot == -1 || dot == path.length() - 1) return null;
        return path.substring(dot + 1);
    }

    private static List<MeshData> convertScene(AIScene scene) {
        List<MeshData> meshes = new ArrayList<>(scene.mNumMeshes());
        PointerBuffer meshPointers = scene.mMeshes();

        for (int i = 0; i < scene.mNumMeshes(); i++) {
            AIMesh aiMesh = AIMesh.create(meshPointers.get(i));
            meshes.add(convertMesh(aiMesh));
        }

        return meshes;
    }

    private static MeshData convertMesh(AIMesh aiMesh) {
        int vertexCount = aiMesh.mNumVertices();

        float[] vertices = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] tangents = new float[vertexCount * 3];
        float[] uvs = new float[vertexCount * 2];

        AIVector3D.Buffer aiVertices = aiMesh.mVertices();
        AIVector3D.Buffer aiNormals = aiMesh.mNormals();
        AIVector3D.Buffer aiTangents = aiMesh.mTangents();
        AIVector3D.Buffer aiUVs = aiMesh.mTextureCoords(0);

        for (int i = 0; i < vertexCount; i++) {
            AIVector3D vertex = aiVertices.get(i);

            vertices[i * 3] = vertex.x();
            vertices[i * 3 + 1] = vertex.y();
            vertices[i * 3 + 2] = vertex.z();

            if (aiNormals != null) {
                AIVector3D normal = aiNormals.get(i);

                normals[i * 3] = normal.x();
                normals[i * 3 + 1] = normal.y();
                normals[i * 3 + 2] = normal.z();
            }

            if (aiTangents != null) {
                AIVector3D tangent = aiTangents.get(i);

                tangents[i * 3] = tangent.x();
                tangents[i * 3 + 1] = tangent.y();
                tangents[i * 3 + 2] = tangent.z();
            }

            if (aiUVs != null) {
                AIVector3D uv = aiUVs.get(i);

                uvs[i * 2] = uv.x();
                uvs[i * 2 + 1] = uv.y();
            }
        }

        int indexCount = aiMesh.mNumFaces() * 3;
        int[] indices = new int[indexCount];

        AIFace.Buffer faces = aiMesh.mFaces();

        int index = 0;

        for (int i = 0; i < aiMesh.mNumFaces(); i++) {
            AIFace face = faces.get(i);

            for (int j = 0; j < face.mNumIndices(); j++) {
                indices[index++] = face.mIndices().get(j);
            }
        }

        return new MeshData(vertices, normals, tangents, uvs, indices);
    }
}
