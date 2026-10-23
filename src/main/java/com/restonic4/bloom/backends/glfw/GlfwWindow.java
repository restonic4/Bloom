package com.restonic4.bloom.backends.glfw;

import com.restonic4.bloom.api.events.WindowEvents;
import com.restonic4.bloom.core.graphics.GraphicsApi;
import com.restonic4.bloom.core.input.CursorMode;
import com.restonic4.bloom.core.input.Key;
import com.restonic4.bloom.core.input.MouseButton;
import com.restonic4.bloom.core.platform.WindowSystem;
import com.restonic4.bloom.core.window.Window;
import com.restonic4.bloom.events.EventResult;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.opengl.GL11.glViewport;

public class GlfwWindow extends Window {
    private final GraphicsApi api;
    private final long handle;

    private GLFWWindowSizeCallback sizeCallback;
    private GLFWWindowPosCallback posCallback;
    private GLFWWindowFocusCallback focusCallback;
    private GLFWWindowIconifyCallback iconifyCallback;
    private GLFWWindowCloseCallback closeCallback;
    private GLFWWindowMaximizeCallback maximizeCallback;
    private GLFWWindowRefreshCallback refreshCallback;
    private GLFWKeyCallback keyCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWCursorPosCallback cursorPosCallback;
    private GLFWScrollCallback scrollCallback;

    private CursorMode cursorMode = CursorMode.NORMAL;

    public GlfwWindow(GraphicsApi api, int width, int height) {
        this(api, width, height, WindowSystem.UNKNOWN);
    }

    public GlfwWindow(GraphicsApi api, int width, int height, WindowSystem windowSystem) {
        super(width, height);
        this.api = api;

        // Print GLFW errors to stderr
        GLFWErrorCallback.createPrint(System.err).set();

        if (windowSystem == WindowSystem.X11) {
            //glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        }

        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        initGraphicsApi(api);

        // Settings
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // Creation
        this.handle = glfwCreateWindow(width, height, "LWJGL3 Window with " + api.name(), MemoryUtil.NULL, MemoryUtil.NULL);
        if (handle == MemoryUtil.NULL) throw new IllegalStateException("Failed to create GLFW window");

        this.centerWindow();

        finalizeGraphicsApi(api);

        this.installEvents();
        this.installInput();

        glfwShowWindow(handle);
    }
    
    private void initGraphicsApi(GraphicsApi api) {
        if (api == GraphicsApi.OPENGL) {
            // OpenGL version
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);

            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

            // macOS
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        } else if (api == GraphicsApi.VULKAN) {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        }
    }

    private void finalizeGraphicsApi(GraphicsApi api) {
        if (api == GraphicsApi.VULKAN) return;

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1); // vsynq for now
        GL.createCapabilities();
    }

    private void installEvents() {
        sizeCallback = glfwSetWindowSizeCallback(this.handle, (window, width, height) -> {
            EventResult eventResult = WindowEvents.RESIZED.invoker().onEvent(this, this.width, this.height, width, height);
            if (eventResult == EventResult.CANCELED) {
                this.setSize(this.width, this.height);
                return;
            }

            this.width = width;
            this.height = height;

            if (api == GraphicsApi.OPENGL) {
                glViewport(0, 0, width, height);
            }
        });

        posCallback = glfwSetWindowPosCallback(this.handle, (window, x, y) -> {
            EventResult eventResult = WindowEvents.MOVED.invoker().onEvent(this, this.x, this.y, x, y);
            if (eventResult == EventResult.CANCELED) {
                this.setPosition(this.x, this.y);
                return;
            }

            this.x = x;
            this.y = y;
        });

        focusCallback = glfwSetWindowFocusCallback(this.handle, (window, focused) -> {
            if (focused) {
                input.rebaseMouse();
                WindowEvents.FOCUSED.invoker().onEvent(this);
            } else {
                input.releaseAll();
                EventResult eventResult = WindowEvents.UNFOCUSED.invoker().onEvent(this);
                if (eventResult == EventResult.CANCELED) this.setFocus();
            }
        });

        iconifyCallback = glfwSetWindowIconifyCallback(this.handle, (window, iconified) -> {
            if (iconified) {
                WindowEvents.ICONIFIED.invoker().onEvent(this);
            } else {
                WindowEvents.RESTORED.invoker().onEvent(this);
            }
        });

        closeCallback = glfwSetWindowCloseCallback(this.handle, window -> {
            EventResult eventResult = WindowEvents.CLOSE_ATTEMPT.invoker().onEvent(this);
            if (eventResult != EventResult.CANCELED) this.setShouldClose(true);
        });

        maximizeCallback = glfwSetWindowMaximizeCallback(this.handle, (window, maximized) -> {
            if (maximized) {
                WindowEvents.MAXIMIZED.invoker().onEvent(this);
            } else {
                WindowEvents.UNMAXIMIZED.invoker().onEvent(this);
            }
        });

        refreshCallback = glfwSetWindowRefreshCallback(this.handle, window -> WindowEvents.REFRESH_REQUIRED.invoker().onEvent(this));
    }

    private void installInput() {
        keyCallback = glfwSetKeyCallback(this.handle, (window, key, scancode, action, mods) -> {
            if (action == GLFW_REPEAT) return;

            Key mapped = GlfwKeys.fromGlfw(key);
            if (mapped != null) input.onButton(mapped, action == GLFW_PRESS);
        });

        mouseButtonCallback = glfwSetMouseButtonCallback(this.handle, (window, button, action, mods) -> {
            input.onButton(MouseButton.of(button), action == GLFW_PRESS);
        });

        cursorPosCallback = glfwSetCursorPosCallback(this.handle, (window, x, y) -> input.onCursorPosition(x, y));

        scrollCallback = glfwSetScrollCallback(this.handle, (window, x, y) -> input.onScroll(x, y));

        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.doubles(0);
            DoubleBuffer y = stack.doubles(0);

            glfwGetCursorPos(this.handle, x, y);
            input.onCursorPosition(x.get(0), y.get(0));
        }
    }

    @Override
    public void pollEvents() {
        input.beginFrame();
        glfwPollEvents();
    }

    @Override
    public void setCursorMode(CursorMode mode) {
        if (this.cursorMode == mode) return;

        // Raw motion (no OS acceleration) can only be on while the cursor is disabled
        boolean rawSupported = glfwRawMouseMotionSupported();
        if (mode == CursorMode.LOCKED) {
            glfwSetInputMode(this.handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            if (rawSupported) glfwSetInputMode(this.handle, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        } else {
            if (rawSupported) glfwSetInputMode(this.handle, GLFW_RAW_MOUSE_MOTION, GLFW_FALSE);
            glfwSetInputMode(this.handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }

        this.cursorMode = mode;
        input.rebaseMouse();
    }

    @Override
    public CursorMode getCursorMode() { return this.cursorMode; }

    @Override
    public void centerWindow() {
        GLFWVidMode videoMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (videoMode != null) {
            int x = (videoMode.width() - getWidth()) / 2;
            int y = (videoMode.height() - getHeight()) / 2;
            this.setPosition(x, y);
        }
    }

    @Override
    public void setPosition(int x, int y) {
        if (glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) return;
        glfwSetWindowPos(this.handle, x, y);
    }

    @Override
    public void setSize(int width, int height) {
        glfwSetWindowSize(this.handle, width, height);
    }

    @Override
    public void setFocus() {
        if (glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) return;
        glfwFocusWindow(this.handle);
    }

    @Deprecated(forRemoval = true) // OpenGL only
    public void makeContextCurrent() {
        glfwMakeContextCurrent(handle);
    }

    @Deprecated(forRemoval = true) // OpenGL only
    public void swapBuffers() {
        glfwSwapBuffers(handle);
    }

    @Deprecated(forRemoval = true) // OpenGL only
    public void setSwapInterval(int interval) {
        glfwSwapInterval(interval);
    }

    public void waitForNonZeroFramebuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.ints(0);
            IntBuffer height = stack.ints(0);

            glfwGetFramebufferSize(this.handle, width, height);
            while (!shouldClose() && (width.get(0) == 0 || height.get(0) == 0)) {
                glfwWaitEvents();
                glfwGetFramebufferSize(this.handle, width, height);
            }
        }
    }

    public long getHandle() { return this.handle; }

    @Override
    public void dispose() {
        if (sizeCallback != null) sizeCallback.free();
        if (posCallback != null) posCallback.free();
        if (focusCallback != null) focusCallback.free();
        if (iconifyCallback != null) iconifyCallback.free();
        if (closeCallback != null) closeCallback.free();
        if (maximizeCallback != null) maximizeCallback.free();
        if (refreshCallback != null) refreshCallback.free();
        if (keyCallback != null) keyCallback.free();
        if (mouseButtonCallback != null) mouseButtonCallback.free();
        if (cursorPosCallback != null) cursorPosCallback.free();
        if (scrollCallback != null) scrollCallback.free();

        Callbacks.glfwFreeCallbacks(handle);
        glfwDestroyWindow(this.handle);
        glfwTerminate();

        GLFWErrorCallback errorCallback = GLFW.glfwSetErrorCallback(null);
        if (errorCallback != null) {
            errorCallback.free();
        }
    }
}
