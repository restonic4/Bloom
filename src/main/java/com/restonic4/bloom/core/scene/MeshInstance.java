package com.restonic4.bloom.core.scene;

import com.restonic4.bloom.core.math.Transform;

public class MeshInstance {
    private final MeshData mesh;
    private final Transform transform;

    public MeshInstance(MeshData mesh, Transform transform) {
        this.mesh = mesh;
        this.transform = transform;
    }

    public MeshData getMesh() {
        return mesh;
    }
    public Transform getTransform() {
        return transform;
    }
}
