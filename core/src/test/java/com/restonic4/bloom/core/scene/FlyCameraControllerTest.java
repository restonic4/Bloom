package com.restonic4.bloom.core.scene;

import com.restonic4.bloom.core.input.Input;
import com.restonic4.bloom.core.input.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlyCameraControllerTest {
    private static final float EPSILON = 1e-4f;

    private final Input input = new Input();
    private final Camera camera = new Camera();
    private final FlyCameraController controller = new FlyCameraController();

    private void tick(double delta) {
        controller.update(input, camera, delta);
        input.beginFrame();
    }

    private void moveMouse(double dx, double dy) {
        input.onCursorPosition(0, 0);
        input.beginFrame();
        input.onCursorPosition(dx, dy);
    }

    @Test
    void pitchStopsBeforeLookingStraightUpOrDown() {
        moveMouse(0, -1_000_000);
        tick(0.016);
        assertEquals(Math.toRadians(89.0), controller.getPitch(), EPSILON);

        input.onCursorPosition(0, 1_000_000);
        tick(0.016);
        assertEquals(Math.toRadians(-89.0), controller.getPitch(), EPSILON);
    }

    @Test
    void mouseRightTurnsRight() {
        moveMouse(100, 0);
        tick(0.016);
        assertTrue(controller.getYaw() < 0);

        input.onButton(Key.W, true);
        tick(1.0);
        assertTrue(camera.getTransform().getX() > 0, "turned right, so forward now has +X");
    }

    @Test
    void forwardWithoutTurningGoesDownNegativeZ() {
        input.onButton(Key.W, true);
        tick(1.0);

        assertEquals(0.0f, camera.getTransform().getX(), EPSILON);
        assertEquals(-5.0f, camera.getTransform().getZ(), EPSILON);
    }

    @Test
    void diagonalMovementIsNotFaster() {
        input.onButton(Key.W, true);
        input.onButton(Key.D, true);
        tick(1.0);

        float distance = (float) Math.hypot(camera.getTransform().getX(), camera.getTransform().getZ());
        assertEquals(5.0f, distance, EPSILON);
    }

    @Test
    void lookingUpDoesNotSlowForwardMovement() {
        moveMouse(0, -400);
        tick(0.0);
        float x = camera.getTransform().getX();
        float z = camera.getTransform().getZ();

        input.onButton(Key.W, true);
        tick(1.0);

        float distance = (float) Math.hypot(camera.getTransform().getX() - x, camera.getTransform().getZ() - z);
        assertEquals(5.0f, distance, EPSILON);
        assertEquals(0.0f, camera.getTransform().getY(), EPSILON);
    }

    @Test
    void spaceAndShiftMoveOnTheWorldVerticalAxis() {
        input.onButton(Key.SPACE, true);
        tick(1.0);
        assertEquals(5.0f, camera.getTransform().getY(), EPSILON);

        input.onButton(Key.SPACE, false);
        input.onButton(Key.LEFT_SHIFT, true);
        tick(2.0);
        assertEquals(-5.0f, camera.getTransform().getY(), EPSILON);
    }

    @Test
    void scrollChangesSpeedWithinLimits() {
        input.onScroll(0, 1);
        tick(0.0);
        float faster = controller.getSpeed();
        assertTrue(faster > 5.0f);

        input.onScroll(0, -2);
        tick(0.0);
        assertTrue(controller.getSpeed() < 5.0f);

        input.onScroll(0, 10_000);
        tick(0.0);
        assertEquals(200.0f, controller.getSpeed());

        input.onScroll(0, -10_000);
        tick(0.0);
        assertEquals(0.5f, controller.getSpeed());
    }
}
