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

        assertEquals(1, scene.dynamicBatches().size());
        assertEquals(List.of(a, b), scene.dynamicBatches().getFirst().instances());
        assertEquals(2, scene.instanceCount());
    }

    @Test
    void sameMeshWithAnotherMaterialIsAnotherBatch() {
        add(cube, red);
        add(cube, blue);

        assertEquals(2, scene.dynamicBatches().size());
        assertSame(red, scene.dynamicBatches().get(0).material());
        assertSame(blue, scene.dynamicBatches().get(1).material());
    }

    @Test
    void laterInstancesJoinTheirExistingBatch() {
        MeshInstance cube1 = add(cube, red);
        MeshInstance planeRed = add(plane, red);
        MeshInstance cube2 = add(cube, red);

        assertEquals(2, scene.dynamicBatches().size());
        assertEquals(List.of(cube1, cube2), scene.dynamicBatches().get(0).instances());
        assertEquals(List.of(planeRed), scene.dynamicBatches().get(1).instances());
    }

    @Test
    void removingTheLastOneOfABatchDropsTheBatch() {
        MeshInstance only = add(cube, red);
        add(plane, red);

        scene.remove(only);

        assertEquals(1, scene.dynamicBatches().size());
        assertSame(plane, scene.dynamicBatches().getFirst().mesh());
        assertEquals(1, scene.instanceCount());
    }

    @Test
    void removingFromTheMiddleKeepsTheOthersAndTheBatchPacked() {
        MeshInstance a = add(cube, red);
        MeshInstance b = add(cube, red);
        MeshInstance c = add(cube, red);

        scene.remove(a);

        List<MeshInstance> left = scene.dynamicBatches().getFirst().instances();
        assertEquals(2, left.size());
        assertTrue(left.contains(b));
        assertTrue(left.contains(c));

        scene.remove(c);
        assertEquals(List.of(b), scene.dynamicBatches().getFirst().instances());

        scene.remove(b);
        assertTrue(scene.dynamicBatches().isEmpty());
        assertEquals(0, scene.instanceCount());
    }

    @Test
    void anInstanceCanBeAddedAgainAfterItsBatchWasDropped() {
        scene.remove(add(cube, red));
        MeshInstance again = add(cube, red);

        assertEquals(List.of(again), scene.dynamicBatches().getFirst().instances());
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

        assertThrows(UnsupportedOperationException.class, () -> scene.dynamicBatches().clear());
        assertThrows(UnsupportedOperationException.class, () -> scene.dynamicBatches().getFirst().instances().clear());
    }

    // Static instances

    private MeshInstance addStatic(MeshData mesh, Material material, float x, float y, float z) {
        return scene.add(mesh, material, new Transform().setPosition(x, y, z), Mobility.STATIC);
    }

    private StaticChunk chunkAt(int x, int y, int z) {
        return scene.staticChunks().stream()
                .filter(chunk -> chunk.coord().equals(new ChunkCoord(x, y, z)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No chunk at " + x + "," + y + "," + z));
    }

    @Test
    void addWithoutMobilityIsDynamic() {
        MeshInstance instance = add(cube, red);

        assertEquals(Mobility.DYNAMIC, instance.getMobility());
        assertFalse(instance.getTransform().isFrozen());
        assertTrue(scene.staticChunks().isEmpty());
    }

    @Test
    void staticInstanceFreezesItsTransformAndStaysOutOfTheDynamicBatches() {
        MeshInstance instance = addStatic(cube, red, 1, 2, 3);

        assertEquals(Mobility.STATIC, instance.getMobility());
        assertTrue(instance.getTransform().isFrozen());
        assertThrows(IllegalStateException.class, () -> instance.getTransform().setPosition(0, 0, 0));
        assertTrue(scene.dynamicBatches().isEmpty());
        assertEquals(1, scene.instanceCount());
    }

    @Test
    void staticInstancesInTheSameCellShareAChunk() {
        MeshInstance a = addStatic(cube, red, 1, 1, 1);
        MeshInstance b = addStatic(cube, red, 31.9f, 0, 0);

        assertEquals(1, scene.staticChunks().size());
        assertEquals(List.of(a, b), chunkAt(0, 0, 0).batches().getFirst().instances());
    }

    @Test
    void cellsStartAtMultiplesOfTheChunkSizeAndNegativesGoBelowZero() {
        addStatic(cube, red, 32, 0, 0);
        addStatic(cube, red, -0.5f, 0, 0);
        addStatic(cube, red, -32, 0, 0);
        addStatic(cube, red, -32.5f, 0, 0);

        assertEquals(3, scene.staticChunks().size());
        assertEquals(1, chunkAt(1, 0, 0).instanceCount());
        assertEquals(2, chunkAt(-1, 0, 0).instanceCount());
        assertEquals(1, chunkAt(-2, 0, 0).instanceCount());
    }

    @Test
    void chunkSizeIsConfigurable() {
        Scene small = new Scene(2.0f);
        small.add(cube, red, new Transform().setPosition(3, 0, 0), Mobility.STATIC);

        assertEquals(new ChunkCoord(1, 0, 0), small.staticChunks().iterator().next().coord());
        assertEquals(2.0f, small.chunkSize());
    }

    @Test
    void invalidChunkSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Scene(0));
        assertThrows(IllegalArgumentException.class, () -> new Scene(-1));
        assertThrows(IllegalArgumentException.class, () -> new Scene(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Scene(Float.POSITIVE_INFINITY));
    }

    @Test
    void staticInstancePositionedOffTheGridIsRejectedAndLeavesNothingBehind() {
        Transform nowhere = new Transform().setPosition(Float.NaN, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> scene.add(cube, red, nowhere, Mobility.STATIC));

        assertEquals(0, scene.instanceCount());
        assertTrue(scene.staticChunks().isEmpty());
    }

    @Test
    void chunksGroupTheirInstancesByMeshAndMaterial() {
        addStatic(cube, red, 1, 0, 0);
        addStatic(cube, blue, 2, 0, 0);
        addStatic(plane, red, 3, 0, 0);
        addStatic(cube, red, 4, 0, 0);

        assertEquals(3, chunkAt(0, 0, 0).batches().size());
        assertEquals(2, chunkAt(0, 0, 0).batches().getFirst().instances().size());
    }

    @Test
    void chunkVersionChangesOnlyForTheChunkThatChanged() {
        MeshInstance a = addStatic(cube, red, 1, 0, 0);
        addStatic(cube, red, 100, 0, 0);
        long untouchedBefore = chunkAt(3, 0, 0).version();
        long touchedBefore = chunkAt(0, 0, 0).version();

        addStatic(cube, red, 2, 0, 0);
        long afterAdd = chunkAt(0, 0, 0).version();
        scene.remove(a);

        assertNotEquals(touchedBefore, afterAdd);
        assertNotEquals(afterAdd, chunkAt(0, 0, 0).version());
        assertEquals(untouchedBefore, chunkAt(3, 0, 0).version());
    }

    @Test
    void removingTheLastStaticInstanceOfAChunkDropsTheChunk() {
        MeshInstance only = addStatic(cube, red, 1, 0, 0);
        addStatic(cube, red, 100, 0, 0);

        scene.remove(only);

        assertEquals(1, scene.staticChunks().size());
        assertEquals(1, scene.instanceCount());
    }

    @Test
    void removingStaticInstancesTwiceOrFromAnotherSceneIsRejected() {
        MeshInstance a = addStatic(cube, red, 1, 0, 0);
        scene.remove(a);
        assertThrows(IllegalArgumentException.class, () -> scene.remove(a));

        Scene other = new Scene();
        MeshInstance inOther = other.add(cube, red, new Transform().setPosition(1, 0, 0), Mobility.STATIC);
        addStatic(cube, red, 1, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> scene.remove(inOther));
    }

    @Test
    void countsAndVersionCoverBothKinds() {
        long start = scene.version();
        MeshInstance dynamic = add(cube, red);
        MeshInstance fixed = addStatic(cube, red, 1, 0, 0);

        assertEquals(2, scene.instanceCount());
        assertNotEquals(start, scene.version());

        long afterAdds = scene.version();
        scene.remove(fixed);
        assertNotEquals(afterAdds, scene.version());
        scene.remove(dynamic);
        assertEquals(0, scene.instanceCount());
    }

    @Test
    void drawBatchesListDynamicThenStaticAndFollowTheScene() {
        MeshInstance dynamic = add(cube, red);
        MeshInstance fixed = addStatic(cube, red, 1, 0, 0);

        List<DrawBatch> all = scene.drawBatches();
        assertEquals(2, all.size());
        assertEquals(List.of(dynamic), all.get(0).instances());
        assertEquals(List.of(fixed), all.get(1).instances());

        scene.remove(dynamic);
        assertEquals(1, scene.drawBatches().size());
        assertThrows(UnsupportedOperationException.class, () -> scene.drawBatches().clear());
    }

    @Test
    void chunkListsCannotBeModifiedFromOutside() {
        addStatic(cube, red, 1, 0, 0);

        assertThrows(UnsupportedOperationException.class, () -> scene.staticChunks().clear());
        assertThrows(UnsupportedOperationException.class, () -> chunkAt(0, 0, 0).batches().clear());
    }
}
