package com.restonic4.bloom.core.scene;

/** Whether an instance may move after it was added. Static ones are frozen, which lets backends keep them on the GPU untouched. */
public enum Mobility {
    STATIC,
    DYNAMIC
}
