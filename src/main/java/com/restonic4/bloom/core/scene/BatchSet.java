package com.restonic4.bloom.core.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Instances grouped by mesh and material, with constant time add and remove. Owned by a {@link Scene} or a {@link StaticChunk}. */
final class BatchSet {
    private final Map<Key, DrawBatch> batchByKey = new HashMap<>();
    private final List<DrawBatch> batches = new ArrayList<>();
    private final List<DrawBatch> batchesView = Collections.unmodifiableList(batches);

    private int instanceCount;
    private long version;

    void add(MeshInstance instance) {
        DrawBatch batch = batchByKey.computeIfAbsent(new Key(instance.getMesh(), instance.getMaterial()), key -> {
            DrawBatch created = new DrawBatch(key.mesh(), key.material());
            batches.add(created);
            return created;
        });

        batch.add(instance);
        instance.owner = this;
        instanceCount++;
        version++;
    }

    void remove(MeshInstance instance) {
        Key key = new Key(instance.getMesh(), instance.getMaterial());
        DrawBatch batch = batchByKey.get(key);
        if (batch == null || instance.owner != this) throw new IllegalArgumentException("The instance is not in this scene");

        batch.remove(instance);
        instance.owner = null;

        if (batch.isEmpty()) {
            batchByKey.remove(key);
            batches.remove(batch);
        }

        instanceCount--;
        version++;
    }

    boolean isEmpty() { return instanceCount == 0; }

    List<DrawBatch> batches() { return batchesView; }
    int instanceCount() { return instanceCount; }
    long version() { return version; }

    private record Key(MeshData mesh, Material material) {}
}
