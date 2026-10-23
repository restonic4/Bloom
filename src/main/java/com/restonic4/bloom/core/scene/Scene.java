package com.restonic4.bloom.core.scene;

import com.restonic4.bloom.core.math.Transform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What gets drawn. Instances are kept grouped by mesh and material, so adding and removing are constant time
 * and the renderers never have to sort anything.
 * <p>
 * Dynamic instances may move every frame. Static ones are frozen when added and stored in a grid of
 * {@link StaticChunk}s, so a backend can keep each chunk's data on the GPU and only touch it when that chunk changes.
 */
public final class Scene {
    public static final float DEFAULT_CHUNK_SIZE = 32.0f;

    private final float chunkSize;
    private final BatchSet dynamic = new BatchSet();
    private final Map<ChunkCoord, StaticChunk> chunks = new HashMap<>();
    private final Collection<StaticChunk> chunksView = Collections.unmodifiableCollection(chunks.values());

    private int instanceCount;
    private long version;

    // Temporary flat view for backends that do not treat static chunks separately yet
    private List<DrawBatch> drawBatches = List.of();
    private long drawBatchesVersion = -1;

    public Scene() {
        this(DEFAULT_CHUNK_SIZE);
    }

    /** @param chunkSize edge length of the cells static instances are grouped in, in world units */
    public Scene(float chunkSize) {
        if (!(chunkSize > 0.0f) || Float.isInfinite(chunkSize)) throw new IllegalArgumentException("Chunk size must be positive and finite: " + chunkSize);
        this.chunkSize = chunkSize;
    }

    public MeshInstance add(MeshData mesh, Material material, Transform transform) {
        return add(mesh, material, transform, Mobility.DYNAMIC);
    }

    /** A static instance freezes the transform: it can never be changed again. To move it, remove it and add it as dynamic. */
    public MeshInstance add(MeshData mesh, Material material, Transform transform, Mobility mobility) {
        MeshInstance instance = new MeshInstance(mesh, material, transform, mobility);

        switch (mobility) {
            case DYNAMIC -> dynamic.add(instance);
            case STATIC -> {
                transform.freeze();
                chunks.computeIfAbsent(chunkOf(transform), StaticChunk::new).set().add(instance);
            }
        }

        instanceCount++;
        version++;

        return instance;
    }

    public void remove(MeshInstance instance) {
        switch (instance.getMobility()) {
            case DYNAMIC -> dynamic.remove(instance);
            case STATIC -> {
                ChunkCoord coord = chunkOf(instance.getTransform());
                StaticChunk chunk = chunks.get(coord);
                if (chunk == null) throw new IllegalArgumentException("The instance is not in this scene");

                chunk.set().remove(instance);
                if (chunk.set().isEmpty()) chunks.remove(coord);
            }
        }

        instanceCount--;
        version++;
    }

    private ChunkCoord chunkOf(Transform transform) {
        return new ChunkCoord(cell(transform.getX()), cell(transform.getY()), cell(transform.getZ()));
    }

    private int cell(float coordinate) {
        double cell = Math.floor(coordinate / chunkSize);
        if (!Double.isFinite(cell) || cell < Integer.MIN_VALUE || cell > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Static instance position is outside the chunk grid: " + coordinate);
        }

        return (int) cell;
    }

    /** The dynamic instances, grouped. Never contains an empty batch; the order can change when instances are added or removed. */
    public List<DrawBatch> dynamicBatches() { return dynamic.batches(); }

    /** The non-empty chunks of static instances. The order can change when instances are added or removed. */
    public Collection<StaticChunk> staticChunks() { return chunksView; }

    /** Dynamic batches, then the batches of every static chunk, as one list. Rebuilt only when the scene changed. */
    public List<DrawBatch> drawBatches() {
        if (drawBatchesVersion != version) {
            List<DrawBatch> all = new ArrayList<>(dynamic.batches());
            for (StaticChunk chunk : chunks.values()) all.addAll(chunk.batches());

            drawBatches = Collections.unmodifiableList(all);
            drawBatchesVersion = version;
        }

        return drawBatches;
    }

    public int instanceCount() { return instanceCount; }

    public float chunkSize() { return chunkSize; }

    /** Changes whenever an instance is added or removed, so a backend can tell its draw list is out of date. */
    public long version() { return version; }
}
