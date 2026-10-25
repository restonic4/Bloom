package com.restonic4.bloom.core.input;

public enum CursorMode {
    /** Visible, moves freely and stops at the window edge. */
    NORMAL,

    /** Hidden and held on the window, movement is reported without limits. For first person cameras. */
    LOCKED
}
