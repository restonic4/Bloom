package com.restonic4.bloom.core.input;

/**
 * A mouse button by index. 0, 1 and 2 are left, right and middle; 3 and up are the extra buttons
 * (side buttons, DPI buttons...). The backend decides how many exist, GLFW reports 8.
 */
public record MouseButton(int index) implements InputButton {
    public static final int MAX_BUTTONS = 8;

    public static final MouseButton LEFT = new MouseButton(0);
    public static final MouseButton RIGHT = new MouseButton(1);
    public static final MouseButton MIDDLE = new MouseButton(2);
    public static final MouseButton BUTTON_4 = new MouseButton(3);
    public static final MouseButton BUTTON_5 = new MouseButton(4);
    public static final MouseButton BUTTON_6 = new MouseButton(5);
    public static final MouseButton BUTTON_7 = new MouseButton(6);
    public static final MouseButton BUTTON_8 = new MouseButton(7);

    private static final MouseButton[] CACHE = { LEFT, RIGHT, MIDDLE, BUTTON_4, BUTTON_5, BUTTON_6, BUTTON_7, BUTTON_8 };

    public MouseButton {
        throwOutOfBounds(index);
    }

    public static MouseButton of(int index) {
        throwOutOfBounds(index);
        return CACHE[index];
    }

    private static void throwOutOfBounds(int index) {
        if (index < 0 || index >= MAX_BUTTONS) {
            throw new IllegalArgumentException("Mouse button index must be in 0.." + (MAX_BUTTONS - 1) + ", got " + index);
        }
    }
}
