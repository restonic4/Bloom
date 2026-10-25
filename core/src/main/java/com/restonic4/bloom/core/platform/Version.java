package com.restonic4.bloom.core.platform;

public class Version {
    private final int major, minor, patch;
    private final String string;

    public Version(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.string = major + "." + minor + "." + patch;
    }

    public Version(String string) {
        if (string == null) throw new IllegalArgumentException("Version string cannot be null");

        String[] parts = string.split("\\.", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Invalid version: '" + string + "'. Expected major.minor.patch");

        try {
            this.major = Integer.parseInt(parts[0]);
            this.minor = Integer.parseInt(parts[1]);
            this.patch = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version: '" + string + "'. Version components must be integers", e);
        }

        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Invalid version: '" + string + "'. Version components cannot be negative");
        }

        this.string = major + "." + minor + "." + patch;
    }

    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getPatch() { return patch; }
    public String getString() { return string; }
}
