package com.restonic4.unlock;

import com.restonic4.bloom.core.input.Combo;
import com.restonic4.bloom.core.input.CursorMode;
import com.restonic4.bloom.core.input.Input;
import com.restonic4.bloom.core.input.Key;
import com.restonic4.bloom.core.input.MouseButton;
import com.restonic4.bloom.core.math.FrameStats;
import com.restonic4.bloom.core.math.FrameProfiler;
import com.restonic4.bloom.assets.MeshLoader;
import com.restonic4.bloom.assets.TextureLoader;
import com.restonic4.bloom.core.math.ProfileLog;
import com.restonic4.bloom.core.math.Transform;
import com.restonic4.bloom.core.scene.Camera;
import com.restonic4.bloom.core.scene.FlyCameraController;
import com.restonic4.bloom.core.scene.Material;
import com.restonic4.bloom.core.scene.MeshData;
import com.restonic4.bloom.core.scene.MeshInstance;
import com.restonic4.bloom.core.scene.Mobility;
import com.restonic4.bloom.core.scene.Scene;
import com.restonic4.bloom.core.scene.Texture;
import com.restonic4.bloom.core.scene.WrapMode;
import com.restonic4.bloom.core.window.Window;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public final class TestScene {
    private static final float SPIN_SPEED = 1.0f;
    private static final String TOGGLE_SPIN = "toggle-spin";
    private static final String SPAWN_BIG_STATIC = "spawn-big-static";
    private static final String SPAWN_BIG_DYNAMIC = "spawn-big-dynamic";
    private static final float SPAWN_DISTANCE = 4.0f;
    private static final int STRESS_COUNT = 1000;
    private static final int BIG_STRESS_COUNT = 400_000;
    private static final float STRESS_RANGE = 200.0f;

    private final MeshData mesh = MeshLoader.load("Cube.glb").getFirst();
    private final MeshData planeMesh = createPlane(10.0f, 10.0f);
    private final Camera camera = new Camera();
    private final FlyCameraController flyCamera = new FlyCameraController();
    private final Window window;
    private final ProfileLog profileLog;
    private double profiledSeconds;
    private boolean spinning = true;

    private final Texture checker = TextureLoader.load("checker.png");
    private final Material cubeMaterial = new Material(checker);
    private final Texture grid = TextureLoader.load("grid.png", WrapMode.REPEAT);
    private final Material planeMaterial = new Material(grid);

    private final Scene renderScene = new Scene();
    private final List<MeshInstance> spinningInstances = new ArrayList<>();

    private final Deque<MeshInstance> spawned = new ArrayDeque<>();
    private final Random random = new Random(1234);
    private final Quaternionf scratchRotation = new Quaternionf();
    private final Vector3f scratchForward = new Vector3f();
    private final Vector3f scratchPosition = new Vector3f();

    public TestScene(Window window, String label) {
        this.window = window;
        this.profileLog = new ProfileLog(Path.of("build", "profile", label + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log"));
        window.getInput().register(TOGGLE_SPIN, Combo.of(Key.LEFT_CONTROL, Key.LEFT_ALT, Key.K, Key.O));
        window.getInput().register(SPAWN_BIG_STATIC, Combo.of(Key.LEFT_CONTROL, Key.LEFT_ALT, Key.R));
        window.getInput().register(SPAWN_BIG_DYNAMIC, Combo.of(Key.LEFT_CONTROL, Key.LEFT_ALT, Key.Y));

        spinningInstances.add(addCube(0, 0, -10, cubeMaterial));
        spinningInstances.add(addCube(5, 0, -10, cubeMaterial));
        spinningInstances.add(addCube(-5, 0, -10, cubeMaterial));
        spinningInstances.add(addCube(0, 5, -10, planeMaterial));

        // Behind the cube at (5, 0, -10), offset so it is only partly hidden: shows the depth test
        addCube(7.5f, 1.5f, -14, cubeMaterial);

        // Below the centre cube. One-sided: only its top is a front face, so it vanishes when seen from below or with the wrong winding
        addInstance(planeMesh, planeMaterial, new Transform().setPosition(0, -3, -10));
        addInstance(planeMesh, planeMaterial, new Transform().setPosition(0, 7, -20).setRotationXYZDegrees(90, 0, 0));
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
        return renderScene.add(mesh, material, transform);
    }

    public void update(double delta) {
        Input input = window.getInput();

        updateCameraControl(input, delta);
        updateSpawning(input);

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

    // E: one cube in front of the camera. Q: remove the last spawned. R: a thousand random dynamic cubes, Ctrl+Alt+R: 400 thousand static, Ctrl+Alt+Y: 400 thousand dynamic. C: remove all spawned
    private void updateSpawning(Input input) {
        if (input.justPressed(Key.E)) spawnInFrontOfCamera();

        if (input.justPressed(Key.Q) && !spawned.isEmpty()) renderScene.remove(spawned.removeLast());

        if (input.justPressed(SPAWN_BIG_STATIC)) {
            spawnRandom(BIG_STRESS_COUNT, Mobility.STATIC);
        } else if (input.justPressed(SPAWN_BIG_DYNAMIC)) {
            spawnRandom(BIG_STRESS_COUNT, Mobility.DYNAMIC);
        } else if (input.justPressed(Key.R)) {
            spawnRandom(STRESS_COUNT, Mobility.DYNAMIC);
        }

        if (input.justPressed(Key.C)) {
            for (MeshInstance instance : spawned) renderScene.remove(instance);
            spawned.clear();
        }
    }

    private void spawnInFrontOfCamera() {
        camera.getTransform().getRotation(scratchRotation).transform(scratchForward.set(0, 0, -1));
        camera.getTransform().getPosition(scratchPosition).fma(SPAWN_DISTANCE, scratchForward);

        spawn(scratchPosition.x, scratchPosition.y, scratchPosition.z, Mobility.DYNAMIC);
    }

    private void spawnRandom(int count, Mobility mobility) {
        for (int i = 0; i < count; i++) spawn(randomCoordinate(), randomCoordinate(), randomCoordinate(), mobility);
    }

    private float randomCoordinate() {
        return (random.nextFloat() - 0.5f) * STRESS_RANGE;
    }

    private void spawn(float x, float y, float z, Mobility mobility) {
        spawned.add(renderScene.add(mesh, cubeMaterial, new Transform().setPosition(x, y, z), mobility));
    }

    public void showStats(Window window, FrameStats stats, FrameProfiler profiler, double delta) {
        profiler.endFrame();

        if (stats.update(delta)) {
            window.setTitle(String.format("Unlock | %d instances | %.0f fps | %.2f ms", renderScene.instanceCount(), stats.fps(), stats.averageFrameMillis()));

            profiledSeconds += 1.0;
            profileLog.append(String.format("t=%.0fs | %d instances | %.0f fps | %.2f ms | %s", profiledSeconds, renderScene.instanceCount(), stats.fps(), stats.averageFrameMillis(), profiler.report()));
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
    public Scene renderScene() { return renderScene; }
}
