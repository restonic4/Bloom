package com.restonic4.bloom.core.scene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Instances that share a mesh and a material, so a backend can draw them with one instanced call. */
public record DrawBatch(MeshData mesh, Material material, List<MeshInstance> instances) {
    /** Batches come out in the order their first instance appears. */
    public static List<DrawBatch> group(List<MeshInstance> instances) {
        Map<Key, List<MeshInstance>> grouped = new LinkedHashMap<>();

        for (MeshInstance instance : instances) {
            grouped.computeIfAbsent(new Key(instance.getMesh(), instance.getMaterial()), key -> new ArrayList<>()).add(instance);
        }

        List<DrawBatch> batches = new ArrayList<>(grouped.size());
        grouped.forEach((key, group) -> batches.add(new DrawBatch(key.mesh, key.material, group)));
        return batches;
    }

    private record Key(MeshData mesh, Material material) {}
}
