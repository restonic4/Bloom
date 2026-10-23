package com.restonic4.bloom.core.launch;

import com.restonic4.bloom.core.graphics.GraphicsApi;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LaunchOptionsTest {
    private static LaunchOptions parse(String... args) {
        return LaunchOptions.parse(args);
    }

    @Test
    void noArgumentsMeansNothingWasAsked() {
        assertEquals(LaunchOptions.defaults(), parse());
        assertEquals(Optional.empty(), parse().backend());
        assertFalse(parse().help());
    }

    @Test
    void backendIsCaseInsensitive() {
        assertEquals(Optional.of(GraphicsApi.VULKAN), parse("--backend=vulkan").backend());
        assertEquals(Optional.of(GraphicsApi.VULKAN), parse("--backend=VULKAN").backend());
        assertEquals(Optional.of(GraphicsApi.OPENGL), parse("--backend=OpenGL").backend());
    }

    @Test
    void helpIsAFlag() {
        assertTrue(parse("--help").help());
        assertThrows(IllegalArgumentException.class, () -> parse("--help=yes"));
    }

    @Test
    void unknownBackendNamesTheValue() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parse("--backend=directx"));
        assertTrue(error.getMessage().contains("directx"));
    }

    @Test
    void backendWithoutAValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("--backend"));
        assertThrows(IllegalArgumentException.class, () -> parse("--backend="));
    }

    @Test
    void unknownOptionsAreRejectedInsteadOfIgnored() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parse("--backnd=vulkan"));
        assertTrue(error.getMessage().contains("--backnd=vulkan"));
        assertThrows(IllegalArgumentException.class, () -> parse("vulkan"));
        assertThrows(IllegalArgumentException.class, () -> parse("--backend", "vulkan"));
    }

    @Test
    void laterBackendWins() {
        assertEquals(Optional.of(GraphicsApi.OPENGL), parse("--backend=vulkan", "--backend=opengl").backend());
    }

    @Test
    void usageMentionsEveryOption() {
        assertTrue(LaunchOptions.USAGE.contains("--backend"));
        assertTrue(LaunchOptions.USAGE.contains("--help"));
    }
}
