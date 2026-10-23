package com.restonic4.bloom.core.scene;

import com.restonic4.bloom.core.math.Transform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneTest {
    private final Scene scene = new Scene();

    private final MeshData cube = mesh();
    private final MeshData plane = mesh();
    private final Material red = new Material(new Texture(null, WrapMode.CLAMP));
    private final Material blue = new Material(new Texture(null, WrapMode.CLAMP));

    private static MeshData mesh() {
        return new MeshData(new float[0], new float[0], new float[0], new float[0], new int[0]);
    }

    private MeshInstance add(MeshData mesh, Material material) {
        return scene.add(mesh, material, new Transform());
    }

    @Test
    void sameMeshAndMaterialShareABatch() {
        MeshInstance a = add(cube, red);
        MeshInstance b = add(cube, red);

        assertEquals(1, scene.batches().size());
        assertEquals(List.of(a, b), scene.batches().getFirst().instances());
        assertEquals(2, scene.instanceCount());
    }

    @Test
    void sameMeshWithAnotherMaterialIsAnotherBatch() {
        add(cube, red);
        add(cube, blue);

        assertEquals(2, scene.batches().size());
        assertSame(red, scene.batches().get(0).material());
        assertSame(blue, scene.batches().get(1).material());
    }

    @Test
    void laterInstancesJoinTheirExistingBatch() {
        MeshInstance cube1 = add(cube, red);
        MeshInstance planeRed = add(plane, red);
        MeshInstance cube2 = add(cube, red);

        assertEquals(2, scene.batches().size());
        assertEquals(List.of(cube1, cube2), scene.batches().get(0).instances());
        assertEquals(List.of(planeRed), scene.batches().get(1).instances());
    }

    @Test
    void removingTheLastOneOfABatchDropsTheBatch() {
        MeshInstance only = add(cube, red);
        add(plane, red);

        scene.remove(only);

        assertEquals(1, scene.batches().size());
        assertSame(plane, scene.batches().getFirst().mesh());
        assertEquals(1, scene.instanceCount());
    }

    @Test
    void removingFromTheMiddleKeepsTheOthersAndTheBatchPacked() {
        MeshInstance a = add(cube, red);
        MeshInstance b = add(cube, red);
        MeshInstance c = add(cube, red);

        scene.remove(a);

        List<MeshInstance> left = scene.batches().getFirst().instances();
        assertEquals(2, left.size());
        assertTrue(left.contains(b));
        assertTrue(left.contains(c));

        scene.remove(c);
        assertEquals(List.of(b), scene.batches().getFirst().instances());

        scene.remove(b);
        assertTrue(scene.batches().isEmpty());
        assertEquals(0, scene.instanceCount());
    }

    @Test
    void anInstanceCanBeAddedAgainAfterItsBatchWasDropped() {
        scene.remove(add(cube, red));
        MeshInstance again = add(cube, red);

        assertEquals(List.of(again), scene.batches().getFirst().instances());
    }

    @Test
    void versionChangesOnEveryAddAndRemove() {
        long start = scene.version();
        MeshInstance a = add(cube, red);
        long afterAdd = scene.version();
        scene.remove(a);

        assertNotEquals(start, afterAdd);
        assertNotEquals(afterAdd, scene.version());
    }

    @Test
    void removingTwiceOrFromAnotherSceneIsRejected() {
        MeshInstance a = add(cube, red);
        scene.remove(a);
        assertThrows(IllegalArgumentException.class, () -> scene.remove(a));

        Scene other = new Scene();
        MeshInstance inOther = other.add(cube, red, new Transform());
        add(cube, red);
        assertThrows(IllegalArgumentException.class, () -> scene.remove(inOther));
    }

    @Test
    void batchListsCannotBeModifiedFromOutside() {
        add(cube, red);

        assertThrows(UnsupportedOperationException.class, () -> scene.batches().clear());
        assertThrows(UnsupportedOperationException.class, () -> scene.batches().getFirst().instances().clear());
    }
}
