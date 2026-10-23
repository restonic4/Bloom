package com.restonic4.bloom.core.scene;

import com.restonic4.bloom.core.math.Transform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DrawBatchTest {
    private final MeshData cube = mesh();
    private final MeshData plane = mesh();
    private final Material red = new Material(new Texture(null, WrapMode.CLAMP));
    private final Material blue = new Material(new Texture(null, WrapMode.CLAMP));

    private static MeshData mesh() {
        return new MeshData(new float[0], new float[0], new float[0], new float[0], new int[0]);
    }

    private MeshInstance instance(MeshData mesh, Material material) {
        return new MeshInstance(mesh, material, new Transform());
    }

    @Test
    void sameMeshAndMaterialShareABatch() {
        MeshInstance a = instance(cube, red);
        MeshInstance b = instance(cube, red);

        List<DrawBatch> batches = DrawBatch.group(List.of(a, b));

        assertEquals(1, batches.size());
        assertEquals(List.of(a, b), batches.getFirst().instances());
    }

    @Test
    void sameMeshWithAnotherMaterialIsAnotherBatch() {
        List<DrawBatch> batches = DrawBatch.group(List.of(instance(cube, red), instance(cube, blue)));

        assertEquals(2, batches.size());
        assertSame(cube, batches.get(0).mesh());
        assertSame(cube, batches.get(1).mesh());
        assertSame(red, batches.get(0).material());
        assertSame(blue, batches.get(1).material());
    }

    @Test
    void batchesFollowFirstAppearanceAndGatherLaterInstances() {
        MeshInstance cubeRed1 = instance(cube, red);
        MeshInstance planeRed = instance(plane, red);
        MeshInstance cubeRed2 = instance(cube, red);

        List<DrawBatch> batches = DrawBatch.group(List.of(cubeRed1, planeRed, cubeRed2));

        assertEquals(2, batches.size());
        assertSame(cube, batches.get(0).mesh());
        assertEquals(List.of(cubeRed1, cubeRed2), batches.get(0).instances());
        assertEquals(List.of(planeRed), batches.get(1).instances());
    }

    @Test
    void noInstancesNoBatches() {
        assertTrue(DrawBatch.group(List.of()).isEmpty());
    }
}
