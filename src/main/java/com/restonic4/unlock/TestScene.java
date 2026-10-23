package com.restonic4.unlock;

import com.restonic4.bloom.core.input.Combo;
import com.restonic4.bloom.core.input.CursorMode;
import com.restonic4.bloom.core.input.Input;
import com.restonic4.bloom.core.input.Key;
import com.restonic4.bloom.core.input.MouseButton;
import com.restonic4.bloom.core.math.Transform;
import com.restonic4.bloom.core.scene.Camera;
import com.restonic4.bloom.core.scene.DrawBatch;
import com.restonic4.bloom.core.scene.FlyCameraController;
import com.restonic4.bloom.core.scene.Material;
import com.restonic4.bloom.core.scene.MeshData;
import com.restonic4.bloom.core.scene.MeshInstance;
import com.restonic4.bloom.core.scene.Texture;
import com.restonic4.bloom.core.scene.WrapMode;
import com.restonic4.bloom.core.window.Window;

import java.util.ArrayList;
import java.util.List;

public final class TestScene {
    private static final float SPIN_SPEED = 1.0f;
    private static final String TOGGLE_SPIN = "toggle-spin";

    private final MeshData mesh = MeshData.load("Cube.glb").getFirst();
    private final MeshData planeMesh = createPlane(10.0f, 10.0f);
    private final Camera camera = new Camera();
    private final FlyCameraController flyCamera = new FlyCameraController();
    private final Window window;
    private boolean spinning = true;

    private final Texture checker = Texture.load("checker.png");
    private final Material cubeMaterial = new Material(checker);
    private final Texture grid = Texture.load("grid.png", WrapMode.REPEAT);
    private final Material planeMaterial = new Material(grid);

    private final List<MeshInstance> instances = new ArrayList<>();
    private final List<MeshInstance> spinningInstances = new ArrayList<>();
    private final List<DrawBatch> batches;

    public TestScene(Window window) {
        this.window = window;
        window.getInput().register(TOGGLE_SPIN, Combo.of(Key.LEFT_CONTROL, Key.LEFT_ALT, Key.K, Key.O));

        spinningInstances.add(addCube(0, 0, -10, cubeMaterial));
        spinningInstances.add(addCube(5, 0, -10, cubeMaterial));
        spinningInstances.add(addCube(-5, 0, -10, cubeMaterial));
        spinningInstances.add(addCube(0, 5, -10, planeMaterial));

        // Behind the cube at (5, 0, -10), offset so it is only partly hidden: shows the depth test
        addCube(7.5f, 1.5f, -14, cubeMaterial);

        // Below the centre cube. One-sided: only its top is a front face, so it vanishes when seen from below or with the wrong winding
        addInstance(planeMesh, planeMaterial, new Transform().setPosition(0, -3, -10));
        addInstance(planeMesh, planeMaterial, new Transform().setPosition(0, 7, -20).setRotationXYZDegrees(90, 0, 0));

        // The scene does not change shape, so group once
        batches = DrawBatch.group(instances);
    }

    // A flat square on the XZ plane, facing up (+Y). Vertices go counter-clockwise when seen from above (OpenGL convention)
    private static MeshData createPlane(float halfSize, float uvRepeat) {
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
                0, uvRepeat,
                uvRepeat, uvRepeat,
                uvRepeat, 0
        };
        int[] indices = { 0, 1, 2, 0, 2, 3 };

        return new MeshData(vertices, normals, tangents, uvs, indices);
    }

    private MeshInstance addCube(float x, float y, float z, Material material) {
        return addInstance(mesh, material, new Transform().setPosition(x, y, z));
    }

    private MeshInstance addInstance(MeshData mesh, Material material, Transform transform) {
        MeshInstance instance = new MeshInstance(mesh, material, transform);
        instances.add(instance);
        return instance;
    }

    public void update(double delta) {
        Input input = window.getInput();

        updateCameraControl(input, delta);

        if (input.justPressed(TOGGLE_SPIN)) spinning = !spinning;
        if (!spinning) return;

        float angle = (float) (SPIN_SPEED * delta);

        for (MeshInstance instance : spinningInstances) {
            instance.getTransform().rotateXYZRadians(angle, angle, angle);
        }
    }

    // Click the window to fly, Escape to get the cursor back
    private void updateCameraControl(Input input, double delta) {
        if (window.getCursorMode() == CursorMode.LOCKED) {
            if (input.justPressed(Key.ESCAPE)) {
                window.setCursorMode(CursorMode.NORMAL);
            } else {
                flyCamera.update(input, camera, delta);
            }
        } else if (input.justPressed(MouseButton.LEFT)) {
            window.setCursorMode(CursorMode.LOCKED);
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

    public Camera camera() { return camera; }
    public List<MeshInstance> instances() { return instances; }
    public List<DrawBatch> batches() { return batches; }
}
