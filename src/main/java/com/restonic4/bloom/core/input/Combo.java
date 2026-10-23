package com.restonic4.bloom.core.input;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A set of buttons that count as one input, like Ctrl + Alt + K + O or Ctrl + left click.
 * Order does not matter: the combo is active while every button in it is held.
 */
public final class Combo {
    private final Set<InputButton> buttons;

    private Combo(Set<InputButton> buttons) {
        this.buttons = buttons;
    }

    public static Combo of(InputButton... buttons) {
        if (buttons.length == 0) throw new IllegalArgumentException("A combo needs at least one button");
        return new Combo(Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(buttons))));
    }

    public Set<InputButton> buttons() { return buttons; }

    @Override
    public String toString() {
        return "Combo" + buttons;
    }
}
