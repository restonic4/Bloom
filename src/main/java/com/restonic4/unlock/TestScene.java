package com.restonic4.unlock;

import com.restonic4.bloom.core.math.Transform;
import com.restonic4.bloom.core.scene.Camera;
import com.restonic4.bloom.core.scene.MeshData;
import com.restonic4.bloom.core.scene.MeshInstance;

import java.util.ArrayList;
import java.util.List;

public final class TestScene {
    private static final float SPIN_SPEED = 1.0f;

    private final MeshData mesh = MeshData.load("Cube.glb").getFirst();
    private final Camera camera = new Camera();

    private final List<MeshInstance> instances = new ArrayList<>();
    private final List<MeshInstance> spinningInstances = new ArrayList<>();

    public TestScene() {
        spinningInstances.add(addCube(0, 0, -10));
        spinningInstances.add(addCube(5, 0, -10));
        spinningInstances.add(addCube(-5, 0, -10));
        spinningInstances.add(addCube(0, 5, -10));

        // Behind the cube at (5, 0, -10), offset so it is only partly hidden: shows the depth test
        addCube(7.5f, 1.5f, -14);
    }

    private MeshInstance addCube(float x, float y, float z) {
        MeshInstance instance = new MeshInstance(mesh, new Transform().setPosition(x, y, z));
        instances.add(instance);
        return instance;
    }

    public void update(double delta) {
        float angle = (float) (SPIN_SPEED * delta);

        for (MeshInstance instance : spinningInstances) {
            instance.getTransform().rotateXYZ(angle, angle, angle);
        }
    }

    public float[] color(double elapsedSeconds) {
        float time = (float) elapsedSeconds;

        return new float[] {
                0.5f + 0.5f * (float) Math.sin(time),
                0.5f + 0.5f * (float) Math.sin(time + 2.0f),
                0.5f + 0.5f * (float) Math.sin(time + 4.0f),
                1.0f
        };
    }

    public MeshData mesh() { return mesh; }
    public Camera camera() { return camera; }
    public List<MeshInstance> instances() { return instances; }
}
