package com.restonic4.bloom.core.scene;

import com.restonic4.bloom.core.math.Transform;

public class MeshInstance {
    private final MeshData mesh;
    private final Material material;
    private final Transform transform;

    // Position in its batch, kept by the scene that owns it
    int slot = -1;

    public MeshInstance(MeshData mesh, Material material, Transform transform) {
        this.mesh = mesh;
        this.material = material;
        this.transform = transform;
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
}
