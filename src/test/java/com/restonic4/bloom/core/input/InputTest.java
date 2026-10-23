package com.restonic4.bloom.core.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputTest {
    private final Input input = new Input();

    private void frame() { input.beginFrame(); }

    @Test
    void pressedIsOnlyTrueForTheFrameItHappened() {
        frame();
        input.onButton(Key.W, true);
        assertTrue(input.justPressed(Key.W));
        assertTrue(input.isDown(Key.W));

        frame();
        assertFalse(input.justPressed(Key.W));
        assertTrue(input.isDown(Key.W));
    }

    @Test
    void releasedIsOnlyTrueForTheFrameItHappened() {
        input.onButton(Key.W, true);
        frame();
        input.onButton(Key.W, false);
        assertTrue(input.justReleased(Key.W));
        assertFalse(input.isDown(Key.W));

        frame();
        assertFalse(input.justReleased(Key.W));
    }

    @Test
    void aTapInsideOneFrameIsNotLost() {
        frame();
        input.onButton(MouseButton.LEFT, true);
        input.onButton(MouseButton.LEFT, false);

        assertTrue(input.justPressed(MouseButton.LEFT));
        assertTrue(input.justReleased(MouseButton.LEFT));
        assertFalse(input.isDown(MouseButton.LEFT));
    }

    @Test
    void pressingAnAlreadyHeldButtonDoesNotCountAgain() {
        input.onButton(Key.W, true);
        frame();
        input.onButton(Key.W, true);
        assertFalse(input.justPressed(Key.W));
    }

    @Test
    void comboFiresOnTheFrameItsLastButtonGoesDown() {
        Combo combo = Combo.of(Key.LEFT_CONTROL, Key.LEFT_ALT, Key.K, Key.O);

        input.onButton(Key.LEFT_CONTROL, true);
        input.onButton(Key.LEFT_ALT, true);
        input.onButton(Key.K, true);
        frame();
        assertFalse(input.isDown(combo));

        input.onButton(Key.O, true);
        assertTrue(input.isDown(combo));
        assertTrue(input.justPressed(combo));

        frame();
        assertTrue(input.isDown(combo));
        assertFalse(input.justPressed(combo));
    }

    @Test
    void comboDoesNotCareAboutOrder() {
        Combo combo = Combo.of(Key.LEFT_CONTROL, Key.K);

        frame();
        input.onButton(Key.K, true);
        frame();
        input.onButton(Key.LEFT_CONTROL, true);

        assertTrue(input.justPressed(combo));
    }

    @Test
    void comboReleasesWhenAnyButtonLetsGo() {
        Combo combo = Combo.of(Key.LEFT_CONTROL, Key.K);
        input.onButton(Key.LEFT_CONTROL, true);
        input.onButton(Key.K, true);

        frame();
        input.onButton(Key.LEFT_CONTROL, false);
        assertTrue(input.justReleased(combo));
        assertFalse(input.isDown(combo));

        frame();
        assertFalse(input.justReleased(combo));
    }

    @Test
    void comboCanMixKeyboardAndMouse() {
        Combo combo = Combo.of(Key.LEFT_CONTROL, MouseButton.LEFT);

        frame();
        input.onButton(MouseButton.LEFT, true);
        assertFalse(input.isDown(combo));

        input.onButton(Key.LEFT_CONTROL, true);
        assertTrue(input.justPressed(combo));
    }

    @Test
    void namedComboRoundTrip() {
        input.register("save", Combo.of(Key.LEFT_CONTROL, Key.S));

        frame();
        input.onButton(Key.LEFT_CONTROL, true);
        input.onButton(Key.S, true);

        assertTrue(input.justPressed("save"));
        assertTrue(input.isDown("save"));
    }

    @Test
    void namedComboErrors() {
        input.register("save", Combo.of(Key.S));

        assertThrows(IllegalStateException.class, () -> input.register("save", Combo.of(Key.A)));
        assertThrows(IllegalArgumentException.class, () -> input.isDown("missing"));
    }

    @Test
    void emptyComboIsRejected() {
        assertThrows(IllegalArgumentException.class, Combo::of);
    }

    @Test
    void mouseButtonRange() {
        assertEquals(MouseButton.of(7), MouseButton.BUTTON_8);
        assertThrows(IllegalArgumentException.class, () -> MouseButton.of(8));
        assertThrows(IllegalArgumentException.class, () -> MouseButton.of(-1));
    }

    @Test
    void mouseDeltaAccumulatesInAFrameAndResets() {
        input.onCursorPosition(100, 100);
        frame();
        input.onCursorPosition(110, 95);
        input.onCursorPosition(115, 90);

        assertEquals(15, input.getMouseDeltaX());
        assertEquals(-10, input.getMouseDeltaY());
        assertEquals(115, input.getMouseX());

        frame();
        assertEquals(0, input.getMouseDeltaX());
        assertEquals(115, input.getMouseX());
    }

    @Test
    void rebasingSwallowsTheJumpButKeepsThePosition() {
        input.onCursorPosition(100, 100);
        frame();

        input.rebaseMouse();
        input.onCursorPosition(400, 300);
        assertEquals(0, input.getMouseDeltaX());
        assertEquals(400, input.getMouseX());

        input.onCursorPosition(405, 300);
        assertEquals(5, input.getMouseDeltaX());
    }

    @Test
    void scrollAccumulatesInAFrameAndResets() {
        frame();
        input.onScroll(0, 1);
        input.onScroll(0, 2);
        assertEquals(3, input.getScrollY());

        frame();
        assertEquals(0, input.getScrollY());
    }

    @Test
    void releaseAllReleasesEverythingHeld() {
        input.onButton(Key.W, true);
        input.onButton(MouseButton.RIGHT, true);
        frame();

        input.releaseAll();

        assertFalse(input.isDown(Key.W));
        assertTrue(input.justReleased(Key.W));
        assertTrue(input.justReleased(MouseButton.RIGHT));
    }
}
