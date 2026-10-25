package com.restonic4.bloom.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class BloomExtension {
    /** Release of the Bloom BOM, which fixes the version of every Bloom module. */
    public abstract Property<String> getVersion();

    /** Module directory names without the "bloom-" prefix, e.g. "core" or "window-glfw". */
    public abstract ListProperty<String> getModules();

    /** Operating systems to add LWJGL natives for, e.g. "linux", "windows-arm64". The host when empty. */
    public abstract ListProperty<String> getNatives();

    public void version(String value) {
        getVersion().set(value);
    }

    public void modules(String... names) {
        getModules().addAll(names);
    }

    public void natives(String... names) {
        getNatives().addAll(names);
    }
}
