package com.restonic4.bloom.gradle;

import org.gradle.api.GradleException;

final class HostNatives {
    private HostNatives() {}

    static String detect() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        boolean arm64 = arch.startsWith("aarch64") || arch.startsWith("arm64");

        if (os.contains("linux")) return arm64 ? "linux-arm64" : "linux";
        if (os.contains("freebsd")) return "freebsd";
        if (os.contains("mac")) return arm64 ? "macos-arm64" : "macos";
        if (os.contains("windows")) return arm64 ? "windows-arm64" : "windows";

        throw new GradleException("Unsupported host OS: " + os);
    }
}
