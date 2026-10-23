package com.restonic4.bloom.core.scene;

import com.restonic4.bloom.core.math.Transform;

public class MeshInstance {
    private final MeshData mesh;
    private final Material material;
    private final Transform transform;
    private final Mobility mobility;

    // Where the scene keeps it: its position in the batch and the set holding that batch
    int slot = -1;
    BatchSet owner;

    public MeshInstance(MeshData mesh, Material material, Transform transform, Mobility mobility) {
        this.mesh = mesh;
        this.material = material;
        this.transform = transform;
        this.mobility = mobility;
    }

    public MeshData getMesh() {
        return mesh;
    }
    public Material getMaterial() {
        return material;
    }
    public Transform getTransform() {
        return transform;
    }
    public Mobility getMobility() {
        return mobility;
    }
}
