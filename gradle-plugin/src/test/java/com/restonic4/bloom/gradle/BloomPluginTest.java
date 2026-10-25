package com.restonic4.bloom.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the plugin in a real Gradle build against a tiny file-based Maven repository, so no network is involved. */
class BloomPluginTest {
    @TempDir
    Path directory;

    Path repository;
    Path project;

    @BeforeEach
    void createRepository() throws IOException {
        repository = directory.resolve("repo");
        project = directory.resolve("project");
        Files.createDirectories(project);

        publish("com.restonic4.bloom", "bloom-bom", "0.1.0", "pom", """
                <dependencyManagement><dependencies>
                  <dependency><groupId>com.restonic4.bloom</groupId><artifactId>bloom-core</artifactId><version>0.1.0</version></dependency>
                </dependencies></dependencyManagement>
                """, List.of());
        publish("com.restonic4.bloom", "bloom-core", "0.1.0", "jar", """
                <dependencies>
                  <dependency><groupId>org.lwjgl</groupId><artifactId>lwjgl</artifactId><version>3.4.3</version><scope>runtime</scope></dependency>
                  <dependency><groupId>org.lwjgl</groupId><artifactId>lwjgl-vulkan</artifactId><version>3.4.3</version><scope>runtime</scope></dependency>
                </dependencies>
                """, List.of(""));
        publish("org.lwjgl", "lwjgl", "3.4.3", "jar", "", List.of("", "natives-linux", "natives-windows", "natives-macos"));
        // like the real library: natives for macOS only
        publish("org.lwjgl", "lwjgl-vulkan", "3.4.3", "jar", "", List.of("", "natives-macos"));
    }

    @Test
    void addsNativesOfEachRequestedSystem() throws IOException {
        List<String> jars = runtimeJars("""
                version '0.1.0'
                modules 'core'
                natives 'linux', 'windows'
                """);

        assertTrue(jars.contains("lwjgl-3.4.3-natives-linux.jar"), jars.toString());
        assertTrue(jars.contains("lwjgl-3.4.3-natives-windows.jar"), jars.toString());
        assertFalse(jars.contains("lwjgl-3.4.3-natives-macos.jar"), jars.toString());
    }

    @Test
    void skipsLibrariesWithoutNativesForASystem() throws IOException {
        List<String> jars = runtimeJars("""
                version '0.1.0'
                modules 'core'
                natives 'linux', 'macos'
                """);

        assertTrue(jars.contains("lwjgl-vulkan-3.4.3-natives-macos.jar"), jars.toString());
        assertFalse(jars.contains("lwjgl-vulkan-3.4.3-natives-linux.jar"), jars.toString());
        assertTrue(jars.contains("lwjgl-3.4.3-natives-linux.jar"), jars.toString());
    }

    @Test
    void usesTheHostWhenNoSystemIsGiven() throws IOException {
        List<String> jars = runtimeJars("""
                version '0.1.0'
                modules 'core'
                natives HOST
                """.replace("natives HOST", ""));

        String host = "natives-" + HostNatives.detect();
        assertTrue(jars.contains("lwjgl-3.4.3-" + host + ".jar"), jars.toString());
    }

    @Test
    void rejectsAnUnknownSystem() throws IOException {
        String output = failingBuild("""
                version '0.1.0'
                modules 'core'
                natives 'amiga'
                """);

        assertTrue(output.contains("LWJGL has no 'natives-amiga' natives"), output);
    }

    @Test
    void requiresAVersion() throws IOException {
        String output = failingBuild("""
                modules 'core'
                """);

        assertTrue(output.contains("bloom { version '...' } is required"), output);
    }

    private List<String> runtimeJars(String bloomBlock) throws IOException {
        BuildResult result = runner(bloomBlock).build();
        return result.getOutput().lines().filter(line -> line.startsWith("jar:")).map(line -> line.substring(4)).toList();
    }

    private String failingBuild(String bloomBlock) throws IOException {
        return runner(bloomBlock).buildAndFail().getOutput();
    }

    private GradleRunner runner(String bloomBlock) throws IOException {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.restonic4.bloom'
                }

                repositories {
                    maven { url = uri('%s') }
                }

                bloom {
                %s
                }

                tasks.register('printRuntime') {
                    def runtime = configurations.runtimeClasspath
                    doLast { runtime.files.each { println 'jar:' + it.name } }
                }
                """.formatted(repository.toUri(), bloomBlock));

        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments("printRuntime", "--offline", "-q");
    }

    private void publish(String group, String artifact, String version, String packaging, String pomBody, List<String> classifiers) throws IOException {
        Path folder = repository.resolve(group.replace('.', '/')).resolve(artifact).resolve(version);
        Files.createDirectories(folder);

        Files.writeString(folder.resolve(artifact + "-" + version + ".pom"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version><packaging>%s</packaging>
                  %s
                </project>
                """.formatted(group, artifact, version, packaging, pomBody));

        for (String classifier : classifiers) {
            String suffix = classifier.isEmpty() ? "" : "-" + classifier;
            try (OutputStream out = Files.newOutputStream(folder.resolve(artifact + "-" + version + suffix + ".jar"));
                 JarOutputStream jar = new JarOutputStream(out)) {
                jar.putNextEntry(new ZipEntry("empty.txt"));
                jar.closeEntry();
            }
        }
    }
}
