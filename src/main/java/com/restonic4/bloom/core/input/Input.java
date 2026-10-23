package com.restonic4.bloom.core.input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keyboard and mouse state for one window. The window backend feeds it from its events and calls
 * {@link #beginFrame()} once per frame right before polling; the game only reads.
 *
 * "Just pressed" and "just released" mean: it happened during the events polled for the current frame.
 */
public final class Input {
    private final Set<InputButton> down = new HashSet<>();
    private final Set<InputButton> previousDown = new HashSet<>();
    private final Set<InputButton> pressed = new HashSet<>();
    private final Set<InputButton> released = new HashSet<>();

    private final Map<String, Combo> namedCombos = new HashMap<>();

    private double mouseX, mouseY;
    private double mouseDeltaX, mouseDeltaY;
    private double scrollX, scrollY;

    private boolean rebaseMouse = true;

    // Buttons

    public boolean isDown(InputButton button) { return down.contains(button); }
    public boolean justPressed(InputButton button) { return pressed.contains(button); }
    public boolean justReleased(InputButton button) { return released.contains(button); }

    // Combos

    public boolean isDown(Combo combo) { return allIn(down, combo); }

    /** The frame the last missing button of the combo went down. */
    public boolean justPressed(Combo combo) { return allIn(down, combo) && !allIn(previousDown, combo); }

    /** The frame the combo stopped being fully held. */
    public boolean justReleased(Combo combo) { return !allIn(down, combo) && allIn(previousDown, combo); }

    public void register(String name, Combo combo) {
        if (namedCombos.putIfAbsent(name, combo) != null) {
            throw new IllegalStateException("A combo named '" + name + "' is already registered");
        }
    }

    public boolean isDown(String name) { return isDown(named(name)); }
    public boolean justPressed(String name) { return justPressed(named(name)); }
    public boolean justReleased(String name) { return justReleased(named(name)); }

    private Combo named(String name) {
        Combo combo = namedCombos.get(name);
        if (combo == null) throw new IllegalArgumentException("No combo named '" + name + "'");
        return combo;
    }

    private static boolean allIn(Set<InputButton> held, Combo combo) {
        return held.containsAll(combo.buttons());
    }

    // Mouse

    /** Position in window coordinates. While the cursor is locked it is a virtual position that keeps growing. */
    public double getMouseX() { return mouseX; }
    public double getMouseY() { return mouseY; }

    /** Movement during this frame. Works in both cursor modes. */
    public double getMouseDeltaX() { return mouseDeltaX; }
    public double getMouseDeltaY() { return mouseDeltaY; }

    /** Scroll during this frame, in wheel notches. Positive Y is away from the user. */
    public double getScrollX() { return scrollX; }
    public double getScrollY() { return scrollY; }

    // Fed by the window backend

    public void beginFrame() {
        previousDown.clear();
        previousDown.addAll(down);
        pressed.clear();
        released.clear();
        mouseDeltaX = 0;
        mouseDeltaY = 0;
        scrollX = 0;
        scrollY = 0;
    }

    public void onButton(InputButton button, boolean isPressed) {
        if (isPressed) {
            if (down.add(button)) pressed.add(button);
        } else {
            if (down.remove(button)) released.add(button);
        }
    }

    public void onCursorPosition(double x, double y) {
        if (rebaseMouse) {
            rebaseMouse = false;
        } else {
            mouseDeltaX += x - mouseX;
            mouseDeltaY += y - mouseY;
        }

        mouseX = x;
        mouseY = y;
    }

    public void onScroll(double x, double y) {
        scrollX += x;
        scrollY += y;
    }

    /**
     * The next cursor position only sets where we are, it adds no movement. Needed when the position
     * jumps without the user moving the mouse: switching cursor mode, regaining focus.
     */
    public void rebaseMouse() {
        rebaseMouse = true;
    }

    /** Anything still held when the window loses focus would never see its release. */
    public void releaseAll() {
        for (InputButton button : Set.copyOf(down)) {
            onButton(button, false);
        }
    }
}
