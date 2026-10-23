package com.restonic4.bloom.core.math;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Appends one line per call to a file. Nothing stays open between calls, so a killed process loses nothing. */
public final class ProfileLog {
    private final Path file;

    public ProfileLog(Path file) {
        this.file = file;
    }

    public void append(String line) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write profile log " + file, e);
        }
    }

    public Path file() { return file; }
}
