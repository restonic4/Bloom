package com.restonic4.bloom.core.window;

import com.restonic4.bloom.core.Disposable;
import com.restonic4.bloom.core.input.CursorMode;
import com.restonic4.bloom.core.input.Input;
import com.restonic4.bloom.core.rendering.RenderTarget;

public abstract class Window extends RenderTarget implements Disposable {
    protected int x;
    protected int y;
    protected final Input input = new Input();
    private boolean shouldClose;

    protected Window(int width, int height) {
        super(width, height);
        this.shouldClose = false;
    }

    public boolean shouldClose() { return shouldClose; }
    public void setShouldClose(boolean shouldClose) { this.shouldClose = shouldClose; }

    public Input getInput() { return input; }

    /** Clears the per-frame input state, then processes pending events. Call once per frame. */
    public abstract void pollEvents();

    public abstract void setTitle(String title);

    public abstract void setCursorMode(CursorMode mode);
    public abstract CursorMode getCursorMode();

    public abstract void centerWindow();

    @Deprecated(forRemoval = true) // TODO: We cant on Wayland, so we should not rely on this.
    public abstract void setPosition(int x, int y);

    public abstract void setSize(int width, int height);

    @Deprecated(forRemoval = true) // TODO: We cant on Wayland, so we should not rely on this.
    public abstract void setFocus();

    public abstract void makeContextCurrent();
}
