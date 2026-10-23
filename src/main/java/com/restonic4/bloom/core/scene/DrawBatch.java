package com.restonic4.bloom.core.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Instances that share a mesh and a material, so a backend can draw them with one instanced call. Owned by a {@link Scene}. */
public final class DrawBatch {
    private final MeshData mesh;
    private final Material material;
    private final List<MeshInstance> instances = new ArrayList<>();
    private final List<MeshInstance> instancesView = Collections.unmodifiableList(instances);

    DrawBatch(MeshData mesh, Material material) {
        this.mesh = mesh;
        this.material = material;
    }

    void add(MeshInstance instance) {
        instance.slot = instances.size();
        instances.add(instance);
    }

    // The last instance takes the freed slot, so nothing else moves
    void remove(MeshInstance instance) {
        int slot = instance.slot;
        if (slot < 0 || slot >= instances.size() || instances.get(slot) != instance) {
            throw new IllegalArgumentException("The instance is not in this scene");
        }

        MeshInstance last = instances.removeLast();
        if (last != instance) {
            instances.set(slot, last);
            last.slot = slot;
        }

        instance.slot = -1;
    }

    boolean isEmpty() { return instances.isEmpty(); }

    public MeshData mesh() { return mesh; }
    public Material material() { return material; }

    /** Live view: it changes when the scene does. */
    public List<MeshInstance> instances() { return instancesView; }
}
