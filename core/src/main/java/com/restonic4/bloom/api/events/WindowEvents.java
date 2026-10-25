package com.restonic4.bloom.api.events;

import com.restonic4.bloom.events.EventResult;
import com.restonic4.bloom.events.processor.Event;
import com.restonic4.bloom.core.window.Window;

public class WindowEvents extends WindowEventsBase {
    @Event(cancellable = true)
    public interface Moved { EventResult onEvent(Window window, int oldX, int oldY, int newX, int newY); }

    @Event(cancellable = true)
    public interface Resized { EventResult onEvent(Window window, int oldWidth, int oldHeight, int newWidth, int newHeight); }

    @Event
    public interface Focused { void onEvent(Window window); }

    @Event (cancellable = true)
    public interface Unfocused { EventResult onEvent(Window window); }

    @Event
    public interface Iconified { void onEvent(Window window); }

    @Event
    public interface Restored { void onEvent(Window window); }

    @Event(cancellable = true)
    public interface CloseAttempt { EventResult onEvent(Window window); }

    @Event
    public interface Maximized { void onEvent(Window window); }

    @Event
    public interface Unmaximized { void onEvent(Window window); }

    @Event
    public interface RefreshRequired { void onEvent(Window window); }


}
