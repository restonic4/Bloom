package com.restonic4.bloom.core.launch;

import com.restonic4.bloom.core.graphics.GraphicsApi;

import java.util.Optional;

/** What the command line asked for. Only {@code --name=value} is understood, and anything unknown is an error. */
public record LaunchOptions(Optional<GraphicsApi> backend, boolean help) {
    public static final String USAGE = """
            Usage: Unlock [options]
              --backend=opengl|vulkan   Skip the graphics API dialog
              --help                    Show this text""";

    public static LaunchOptions defaults() {
        return new LaunchOptions(Optional.empty(), false);
    }

    /** @throws IllegalArgumentException with a message fit to print, for an argument that is unknown or has a bad value */
    public static LaunchOptions parse(String[] args) {
        Optional<GraphicsApi> backend = Optional.empty();
        boolean help = false;

        for (String argument : args) {
            String name = nameOf(argument);
            String value = valueOf(argument);

            switch (name) {
                case "--backend" -> backend = Optional.of(parseBackend(value));
                case "--help" -> {
                    requireNoValue(name, value);
                    help = true;
                }
                default -> throw new IllegalArgumentException("Unknown option: " + argument);
            }
        }

        return new LaunchOptions(backend, help);
    }

    private static String nameOf(String argument) {
        int equals = argument.indexOf('=');
        return equals < 0 ? argument : argument.substring(0, equals);
    }

    // null when the argument has no '=' at all, so "--backend" and "--backend=" can be told apart
    private static String valueOf(String argument) {
        int equals = argument.indexOf('=');
        return equals < 0 ? null : argument.substring(equals + 1);
    }

    private static void requireNoValue(String name, String value) {
        if (value != null) throw new IllegalArgumentException(name + " does not take a value");
    }

    private static GraphicsApi parseBackend(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("--backend needs a value: opengl or vulkan");

        for (GraphicsApi api : GraphicsApi.values()) {
            if (api.name().equalsIgnoreCase(value)) return api;
        }

        throw new IllegalArgumentException("Unknown backend: " + value + " (expected opengl or vulkan)");
    }
}
