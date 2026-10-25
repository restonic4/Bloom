package com.restonic4.bloom.core;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

public class Resource {
    private static final int INITIAL_BUFFER_SIZE = 8 * 1024;

    public static InputStream open(String path) {
        String resourcePath = normalize(path);

        InputStream input = Resource.class.getResourceAsStream(resourcePath);
        if (input == null) throw new RuntimeException("Resource not found: " + resourcePath);

        return input;
    }

    public static byte[] loadBytes(String path) {
        try (InputStream input = open(path)) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }

    public static String loadString(String path) {
        return new String(loadBytes(path), StandardCharsets.UTF_8);
    }

    // NEDS MANUAL MEMORY FREEING
    public static ByteBuffer loadBuffer(String path) {
        ByteBuffer buffer = MemoryUtil.memAlloc(INITIAL_BUFFER_SIZE);

        try (InputStream input = open(path);
             ReadableByteChannel channel = Channels.newChannel(input)) {

            while (true) {
                if (!buffer.hasRemaining()) {
                    int oldCapacity = buffer.capacity();

                    if (oldCapacity > Integer.MAX_VALUE / 2) throw new RuntimeException("Resource too large: " + path);

                    int position = buffer.position();

                    buffer = MemoryUtil.memRealloc(buffer, oldCapacity * 2);
                    buffer.position(position);
                }

                int read = channel.read(buffer);
                if (read == -1) break;
            }

            int size = buffer.position();

            // Avoid retaining a huge unused native allocation.
            if (size == 0) {
                MemoryUtil.memFree(buffer);
                return MemoryUtil.memAlloc(1).limit(0);
            }

            if (size < buffer.capacity() / 2) {
                int position = buffer.position();

                buffer = MemoryUtil.memRealloc(buffer, size);
                buffer.position(position);
            }

            buffer.flip();
            return buffer;

        } catch (IOException | RuntimeException e) {
            MemoryUtil.memFree(buffer);
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }

    private static String normalize(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
}
