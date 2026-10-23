package com.restonic4.bloom.core.input;

/**
 * Anything that is either held or not: a keyboard key or a mouse button.
 * Sharing one type is what lets a {@link Combo} mix both, like Ctrl + left click.
 */
public sealed interface InputButton permits Key, MouseButton {}
