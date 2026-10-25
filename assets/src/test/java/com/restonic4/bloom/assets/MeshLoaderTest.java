package com.restonic4.bloom.assets;

import com.restonic4.bloom.core.scene.MeshData;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshLoaderTest {
    @Test
    void loadsTheTriangleWithItsPositionsAndIndices() {
        List<MeshData> meshes = MeshLoader.load("triangle.gltf");

        assertEquals(1, meshes.size());
        MeshData mesh = meshes.getFirst();
        assertEquals(3, mesh.vertexCount());
        assertEquals(3, mesh.indexCount());

        Set<String> positions = new HashSet<>();
        for (int index : mesh.indices()) {
            positions.add(mesh.vertices()[index * 3] + "," + mesh.vertices()[index * 3 + 1] + "," + mesh.vertices()[index * 3 + 2]);
        }
        assertEquals(Set.of("0.0,0.0,0.0", "1.0,0.0,0.0", "0.0,1.0,0.0"), positions);
    }

    @Test
    void generatesNormalsAndTangentsWhenTheFileHasNone() {
        MeshData mesh = MeshLoader.load("triangle.gltf").getFirst();

        assertEquals(mesh.vertexCount() * 3, mesh.normals().length);
        assertEquals(mesh.vertexCount() * 3, mesh.tangents().length);

        // The triangle lies in the XY plane, so its normal is along Z
        assertEquals(1.0f, Math.abs(mesh.normals()[2]), 1e-5f);
    }

    @Test
    void failsOnMissingResource() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> MeshLoader.load("does-not-exist.glb"));
        assertTrue(e.getMessage().contains("does-not-exist.glb"));
    }

    @Test
    void failsOnDataThatIsNotAModel() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> MeshLoader.load("not-an-image.txt"));
        assertTrue(e.getMessage().startsWith("Failed to load model"));
    }
}
