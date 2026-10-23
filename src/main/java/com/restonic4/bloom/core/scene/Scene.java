package com.restonic4.bloom.core.scene;

import com.restonic4.bloom.core.math.Transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What gets drawn. Instances are kept grouped by mesh and material, so adding and removing are constant time
 * and the renderers never have to sort anything.
 */
public final class Scene {
    private final Map<Key, DrawBatch> batchByKey = new HashMap<>();
    private final List<DrawBatch> batches = new ArrayList<>();
    private final List<DrawBatch> batchesView = Collections.unmodifiableList(batches);

    private int instanceCount;
    private long version;

    public MeshInstance add(MeshData mesh, Material material, Transform transform) {
        MeshInstance instance = new MeshInstance(mesh, material, transform);

        DrawBatch batch = batchByKey.computeIfAbsent(new Key(mesh, material), key -> {
            DrawBatch created = new DrawBatch(mesh, material);
            batches.add(created);
            return created;
        });

        batch.add(instance);
        instanceCount++;
        version++;

        return instance;
    }

    public void remove(MeshInstance instance) {
        Key key = new Key(instance.getMesh(), instance.getMaterial());
        DrawBatch batch = batchByKey.get(key);
        if (batch == null) throw new IllegalArgumentException("The instance is not in this scene");

        batch.remove(instance);

        if (batch.isEmpty()) {
            batchByKey.remove(key);
            batches.remove(batch);
        }

        instanceCount--;
        version++;
    }

    /** Never contains an empty batch. The order can change when instances are added or removed. */
    public List<DrawBatch> batches() { return batchesView; }

    public int instanceCount() { return instanceCount; }

    /** Changes whenever an instance is added or removed, so a backend can tell its draw list is out of date. */
    public long version() { return version; }

    private record Key(MeshData mesh, Material material) {}
}
