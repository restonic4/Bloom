package com.restonic4.bloom.core.scene;

/** What a texture does for UV coordinates outside 0..1. */
public enum WrapMode {
    /** The edge pixels stretch forever. */
    CLAMP,

    /** The image starts over. */
    REPEAT,

    /** The image starts over, flipped every other time. */
    MIRROR
}
