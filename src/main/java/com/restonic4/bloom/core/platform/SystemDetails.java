package com.restonic4.bloom.core.platform;

import org.lwjgl.system.Platform;

import java.util.Locale;

public class SystemDetails {
    private final OperatingSystem os;
    private final String osName;
    private final String osVersion;
    private final String osArchitecture;
    private final WindowSystem windowServer;
    private final String displayName;
    private final boolean is64Bit;

    private SystemDetails(OperatingSystem os, String osName, String osVersion, String osArchitecture, WindowSystem windowServer, String displayName, boolean is64Bit) {
        this.os = os;
        this.osName = osName;
        this.osVersion = osVersion;
        this.osArchitecture = osArchitecture;
        this.windowServer = windowServer;
        this.displayName = displayName;
        this.is64Bit = is64Bit;
    }

    public static SystemDetails checkAndGet() {
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "unknown");

        WindowSystem windowSystem = detectWindowSystem();
        String displayName = getDisplayName(windowSystem);

        return new SystemDetails(detectOperatingSystem(), osName, osVersion, getOsArchitecture(), windowSystem, displayName, is64Bit());
    }

    private static OperatingSystem detectOperatingSystem() {
        Platform platform = Platform.get();

        return switch (platform) {
            case Platform.WINDOWS -> OperatingSystem.WINDOWS;
            case Platform.LINUX -> OperatingSystem.LINUX;
            case Platform.MACOSX -> OperatingSystem.MACOS;
            case Platform.FREEBSD -> OperatingSystem.FREEBSD;
            default -> OperatingSystem.UNKNOWN;
        };
    }

    private static String getOsArchitecture() {
        return System.getProperty("os.arch", "unknown");
    }

    private static boolean is64Bit() {
        String arch = getOsArchitecture().toLowerCase(Locale.ROOT).trim();

        return switch (arch) {
            case "x86_64", "amd64",
                 "aarch64", "arm64",
                 "ppc64", "ppc64le",
                 "s390x", "riscv64",
                 "loongarch64",
                 "mips64", "mips64el",
                 "sparcv9", "ia64" -> true;
            default -> false;
        };
    }

    private static WindowSystem detectWindowSystem() {
        return switch (detectOperatingSystem()) {
            case WINDOWS -> WindowSystem.WIN32;
            case MACOS -> WindowSystem.COCOA;
            case LINUX, FREEBSD -> detectUnixWindowSystem();
            case UNKNOWN -> WindowSystem.UNKNOWN;
        };
    }

    private static WindowSystem detectUnixWindowSystem() {
        String sessionType = getEnvironment("XDG_SESSION_TYPE").toLowerCase(Locale.ROOT);

        if (sessionType.equals("wayland")) {
            return WindowSystem.WAYLAND;
        }

        if (sessionType.equals("x11")) {
            return WindowSystem.X11;
        }

        // Fallback for sessions without XDG_SESSION_TYPE
        String waylandDisplay = getEnvironment("WAYLAND_DISPLAY");
        String x11Display = getEnvironment("DISPLAY");

        if (!waylandDisplay.isEmpty()) {
            return WindowSystem.WAYLAND;
        }

        if (!x11Display.isEmpty()) {
            return WindowSystem.X11;
        }

        return WindowSystem.UNKNOWN;
    }

    private static String getDisplayName(WindowSystem system) {
        return switch (system) {
            case X11 -> getEnvironment("DISPLAY");
            case WAYLAND -> getEnvironment("WAYLAND_DISPLAY");
            case WIN32, COCOA, UNKNOWN -> "";
        };
    }

    private static String getEnvironment(String name) {
        return System.getenv().getOrDefault(name, "").trim();
    }
}
