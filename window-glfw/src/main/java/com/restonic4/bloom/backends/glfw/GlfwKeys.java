package com.restonic4.bloom.backends.glfw;

import com.restonic4.bloom.core.input.Key;

import java.util.EnumSet;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/** GLFW key code to {@link Key}. Keys GLFW reports that we do not name map to null. */
final class GlfwKeys {
    private static final Key[] BY_CODE = new Key[GLFW_KEY_LAST + 1];

    static {
        for (int i = 0; i < 26; i++) bind(GLFW_KEY_A + i, Key.valueOf(String.valueOf((char) ('A' + i))));
        for (int i = 0; i < 10; i++) bind(GLFW_KEY_0 + i, Key.valueOf("NUM_" + i));
        for (int i = 0; i < 10; i++) bind(GLFW_KEY_KP_0 + i, Key.valueOf("KP_" + i));
        for (int i = 0; i < 12; i++) bind(GLFW_KEY_F1 + i, Key.valueOf("F" + (i + 1)));

        bind(GLFW_KEY_SPACE, Key.SPACE);
        bind(GLFW_KEY_ENTER, Key.ENTER);
        bind(GLFW_KEY_TAB, Key.TAB);
        bind(GLFW_KEY_BACKSPACE, Key.BACKSPACE);
        bind(GLFW_KEY_ESCAPE, Key.ESCAPE);

        bind(GLFW_KEY_INSERT, Key.INSERT);
        bind(GLFW_KEY_DELETE, Key.DELETE);
        bind(GLFW_KEY_HOME, Key.HOME);
        bind(GLFW_KEY_END, Key.END);
        bind(GLFW_KEY_PAGE_UP, Key.PAGE_UP);
        bind(GLFW_KEY_PAGE_DOWN, Key.PAGE_DOWN);

        bind(GLFW_KEY_UP, Key.UP);
        bind(GLFW_KEY_DOWN, Key.DOWN);
        bind(GLFW_KEY_LEFT, Key.LEFT);
        bind(GLFW_KEY_RIGHT, Key.RIGHT);

        bind(GLFW_KEY_LEFT_SHIFT, Key.LEFT_SHIFT);
        bind(GLFW_KEY_RIGHT_SHIFT, Key.RIGHT_SHIFT);
        bind(GLFW_KEY_LEFT_CONTROL, Key.LEFT_CONTROL);
        bind(GLFW_KEY_RIGHT_CONTROL, Key.RIGHT_CONTROL);
        bind(GLFW_KEY_LEFT_ALT, Key.LEFT_ALT);
        bind(GLFW_KEY_RIGHT_ALT, Key.RIGHT_ALT);
        bind(GLFW_KEY_LEFT_SUPER, Key.LEFT_SUPER);
        bind(GLFW_KEY_RIGHT_SUPER, Key.RIGHT_SUPER);

        bind(GLFW_KEY_CAPS_LOCK, Key.CAPS_LOCK);
        bind(GLFW_KEY_NUM_LOCK, Key.NUM_LOCK);
        bind(GLFW_KEY_SCROLL_LOCK, Key.SCROLL_LOCK);
        bind(GLFW_KEY_PRINT_SCREEN, Key.PRINT_SCREEN);
        bind(GLFW_KEY_PAUSE, Key.PAUSE);
        bind(GLFW_KEY_MENU, Key.MENU);

        bind(GLFW_KEY_APOSTROPHE, Key.APOSTROPHE);
        bind(GLFW_KEY_COMMA, Key.COMMA);
        bind(GLFW_KEY_MINUS, Key.MINUS);
        bind(GLFW_KEY_PERIOD, Key.PERIOD);
        bind(GLFW_KEY_SLASH, Key.SLASH);
        bind(GLFW_KEY_SEMICOLON, Key.SEMICOLON);
        bind(GLFW_KEY_EQUAL, Key.EQUAL);
        bind(GLFW_KEY_LEFT_BRACKET, Key.LEFT_BRACKET);
        bind(GLFW_KEY_BACKSLASH, Key.BACKSLASH);
        bind(GLFW_KEY_RIGHT_BRACKET, Key.RIGHT_BRACKET);
        bind(GLFW_KEY_GRAVE_ACCENT, Key.GRAVE_ACCENT);

        bind(GLFW_KEY_KP_DECIMAL, Key.KP_DECIMAL);
        bind(GLFW_KEY_KP_DIVIDE, Key.KP_DIVIDE);
        bind(GLFW_KEY_KP_MULTIPLY, Key.KP_MULTIPLY);
        bind(GLFW_KEY_KP_SUBTRACT, Key.KP_SUBTRACT);
        bind(GLFW_KEY_KP_ADD, Key.KP_ADD);
        bind(GLFW_KEY_KP_ENTER, Key.KP_ENTER);
        bind(GLFW_KEY_KP_EQUAL, Key.KP_EQUAL);

        verifyEveryKeyIsBound();
    }

    private GlfwKeys() {}

    static Key fromGlfw(int code) {
        return code >= 0 && code < BY_CODE.length ? BY_CODE[code] : null;
    }

    private static void bind(int code, Key key) {
        if (BY_CODE[code] != null) throw new IllegalStateException("GLFW key " + code + " bound to both " + BY_CODE[code] + " and " + key);
        BY_CODE[code] = key;
    }

    private static void verifyEveryKeyIsBound() {
        Set<Key> bound = EnumSet.noneOf(Key.class);
        for (Key key : BY_CODE) if (key != null) bound.add(key);

        for (Key key : Key.values()) {
            if (!bound.contains(key)) throw new IllegalStateException("Key " + key + " has no GLFW code");
        }
    }
}
