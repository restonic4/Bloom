package com.restonic4.bloom.core.math;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileLogTest {
    @Test
    void appendsLinesAndCreatesMissingFolders(@TempDir Path dir) throws IOException {
        ProfileLog log = new ProfileLog(dir.resolve("a/b/run.log"));

        log.append("first");
        log.append("second");

        assertEquals(List.of("first", "second"), Files.readAllLines(log.file()));
    }
}
