package com.restonic4.unlock;

import com.restonic4.bloom.core.math.Transform;
import com.restonic4.bloom.core.scene.Camera;
import com.restonic4.bloom.core.scene.Material;
import com.restonic4.bloom.core.scene.MeshData;
import com.restonic4.bloom.core.scene.MeshInstance;
import com.restonic4.bloom.core.scene.Texture;

import java.util.ArrayList;
import java.util.List;

public final class TestScene {
    private static final float SPIN_SPEED = 1.0f;

    private final MeshData mesh = MeshData.load("Cube.glb").getFirst();
    private final MeshData planeMesh = createPlane(10.0f);
    private final Camera camera = new Camera();

    private final Texture checker = Texture.load("checker.png");
    private final Material cubeMaterial = new Material(checker);
    private final Material planeMaterial = new Material(checker);

    private final List<MeshInstance> instances = new ArrayList<>();
    private final List<MeshInstance> spinningInstances = new ArrayList<>();
    private final List<MeshInstance> planeInstances = new ArrayList<>();

    public TestScene() {
        spinningInstances.add(addCube(0, 0, -10));
        spinningInstances.add(addCube(5, 0, -10));
        spinningInstances.add(addCube(-5, 0, -10));
        spinningInstances.add(addCube(0, 5, -10));

        // Behind the cube at (5, 0, -10), offset so it is only partly hidden: shows the depth test
        addCube(7.5f, 1.5f, -14);

        // Below the centre cube. One-sided: only its top is a front face, so it vanishes when seen from below or with the wrong winding
        planeInstances.add(new MeshInstance(planeMesh, new Transform().setPosition(0, -3, -10)));
    }

    // A flat square on the XZ plane, facing up (+Y). Vertices go counter-clockwise when seen from above (OpenGL convention)
    private static MeshData createPlane(float halfSize) {
        float[] vertices = {
                -halfSize, 0, -halfSize,
                -halfSize, 0,  halfSize,
                 halfSize, 0,  halfSize,
                 halfSize, 0, -halfSize
        };
        float[] normals = {
                0, 1, 0,
                0, 1, 0,
                0, 1, 0,
                0, 1, 0
        };
        float[] tangents = {
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
                1, 0, 0
        };
        float[] uvs = {
                0, 0,
                0, 1,
                1, 1,
                1, 0
        };
        int[] indices = { 0, 1, 2, 0, 2, 3 };

        return new MeshData(vertices, normals, tangents, uvs, indices);
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
    public MeshData planeMesh() { return planeMesh; }
    public Material cubeMaterial() { return cubeMaterial; }
    public Material planeMaterial() { return planeMaterial; }
    public Camera camera() { return camera; }
    public List<MeshInstance> instances() { return instances; }
    public List<MeshInstance> planeInstances() { return planeInstances; }
}
