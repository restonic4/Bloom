package com.restonic4.bloom.core.scene;

import com.restonic4.bloom.core.input.Input;
import com.restonic4.bloom.core.input.Key;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First person free flight: the mouse looks around, WASD moves on the horizontal plane,
 * Space goes up and Left Shift goes down. The mouse wheel changes the speed.
 * It only reads input, so the caller decides when it is active (usually: cursor locked).
 */
public final class FlyCameraController {
    private static final float MAX_PITCH = (float) Math.toRadians(89.0);

    private static final float MIN_SPEED = 0.5f;
    private static final float MAX_SPEED = 200.0f;
    private static final float SPEED_PER_NOTCH = 1.15f;

    /** Radians turned per pixel of mouse movement. */
    private float sensitivity = 0.0025f;
    private float speed = 5.0f;

    private float yaw;
    private float pitch;

    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f movement = new Vector3f();

    public void update(Input input, Camera camera, double delta) {
        look(input, camera);
        changeSpeed(input);
        move(input, camera, (float) delta);
    }

    private void look(Input input, Camera camera) {
        yaw -= (float) input.getMouseDeltaX() * sensitivity;
        pitch = Math.clamp(pitch - (float) input.getMouseDeltaY() * sensitivity, -MAX_PITCH, MAX_PITCH);

        camera.getTransform().setRotation(rotation.rotationYXZ(yaw, pitch, 0.0f));
    }

    private void changeSpeed(Input input) {
        float scroll = (float) input.getScrollY();
        if (scroll == 0.0f) return;

        speed = Math.clamp(speed * (float) Math.pow(SPEED_PER_NOTCH, scroll), MIN_SPEED, MAX_SPEED);
    }

    // Forward and right ignore the pitch, so looking up does not change how fast you walk
    private void move(Input input, Camera camera, float delta) {
        float forward = axis(input, Key.W, Key.S);
        float right = axis(input, Key.D, Key.A);
        float up = axis(input, Key.SPACE, Key.LEFT_SHIFT);

        float sin = (float) Math.sin(yaw);
        float cos = (float) Math.cos(yaw);

        movement.set(
                right * cos - forward * sin,
                up,
                -right * sin - forward * cos
        );

        if (movement.lengthSquared() == 0.0f) return;

        movement.normalize(speed * delta);
        camera.getTransform().translate(movement.x, movement.y, movement.z);
    }

    private static float axis(Input input, Key positive, Key negative) {
        return (input.isDown(positive) ? 1.0f : 0.0f) - (input.isDown(negative) ? 1.0f : 0.0f);
    }

    public float getSpeed() { return speed; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
