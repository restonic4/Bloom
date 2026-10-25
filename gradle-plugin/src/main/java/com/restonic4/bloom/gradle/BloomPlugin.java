package com.restonic4.bloom.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class BloomPlugin implements Plugin<Project> {
    private static final String GROUP = "com.restonic4.bloom";
    private static final String LWJGL_GROUP = "org.lwjgl";

    @Override
    public void apply(Project project) {
        BloomExtension bloom = project.getExtensions().create("bloom", BloomExtension.class);

        project.getPlugins().withType(JavaPlugin.class, java -> project.afterEvaluate(p -> configure(p, bloom)));
    }

    private void configure(Project project, BloomExtension bloom) {
        if (!bloom.getVersion().isPresent()) {
            throw new GradleException("bloom { version '...' } is required");
        }

        addBloomDependencies(project, bloom);

        Configuration resolver = createResolver(project);
        List<String> classifiers = classifiers(bloom);
        Supplier<List<Dependency>> natives = memoize(() -> findNatives(project, resolver, classifiers));

        project.getConfigurations().getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)
                .getDependencies().addAllLater(project.provider(natives::get));
        project.getConfigurations().getByName(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME)
                .getDependencies().addAllLater(project.provider(natives::get));
    }

    private void addBloomDependencies(Project project, BloomExtension bloom) {
        String implementation = JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME;

        project.getDependencies().add(implementation,
                project.getDependencies().platform(GROUP + ":bloom-bom:" + bloom.getVersion().get()));
        for (String module : bloom.getModules().get()) {
            project.getDependencies().add(implementation, GROUP + ":bloom-" + module);
        }
    }

    // Resolves what the Bloom modules bring in, to learn which LWJGL libraries need natives
    private Configuration createResolver(Project project) {
        Configuration runtimeClasspath = project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        Configuration implementation = project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);

        return project.getConfigurations().create("bloomNativesResolver", resolver -> {
            resolver.setCanBeConsumed(false);
            resolver.setCanBeResolved(true);
            resolver.extendsFrom(implementation);
            runtimeClasspath.getAttributes().keySet().forEach(key -> copyAttribute(runtimeClasspath, resolver, key));
        });
    }

    private List<String> classifiers(BloomExtension bloom) {
        List<String> systems = bloom.getNatives().get();
        if (systems.isEmpty()) systems = List.of(HostNatives.detect());

        List<String> classifiers = new ArrayList<>();
        for (String system : systems) {
            classifiers.add("natives-" + system);
        }
        return classifiers;
    }

    private List<Dependency> findNatives(Project project, Configuration resolver, List<String> classifiers) {
        Set<String> lwjglModules = new LinkedHashSet<>();
        for (ResolvedArtifactResult artifact : resolver.getIncoming().getArtifacts().getArtifacts()) {
            if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier id
                    && id.getGroup().equals(LWJGL_GROUP)) {
                lwjglModules.add(id.getModule() + ":" + id.getVersion());
            }
        }

        List<Dependency> natives = new ArrayList<>();
        for (String classifier : classifiers) {
            Set<String> existing = modulesWithNatives(project, lwjglModules, classifier);
            if (existing.isEmpty()) {
                throw new GradleException("LWJGL has no '" + classifier + "' natives; check the names in bloom { natives ... }");
            }

            for (String module : existing) {
                natives.add(nativesDependency(project, module, classifier));
            }
        }
        return natives;
    }

    // Not every LWJGL library ships natives for every system (lwjgl-vulkan only does for macOS), so missing ones are skipped
    private Set<String> modulesWithNatives(Project project, Set<String> lwjglModules, String classifier) {
        Dependency[] candidates = lwjglModules.stream()
                .map(module -> nativesDependency(project, module, classifier))
                .toArray(Dependency[]::new);

        Configuration probe = project.getConfigurations().detachedConfiguration(candidates);
        ArtifactCollection collection = probe.getIncoming().artifactView(view -> view.setLenient(true)).getArtifacts();

        for (Throwable failure : collection.getFailures()) {
            if (!isMissingFile(failure)) throw new GradleException("Could not resolve Bloom natives " + classifier, failure);
            project.getLogger().info("Bloom: no {} natives: {}", classifier, failure.getMessage());
        }

        Set<String> found = new LinkedHashSet<>();
        for (ResolvedArtifactResult artifact : collection.getArtifacts()) {
            if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier id) {
                found.add(id.getModule() + ":" + id.getVersion());
            }
        }
        return found;
    }

    private ModuleDependency nativesDependency(Project project, String moduleAndVersion, String classifier) {
        ModuleDependency dependency = (ModuleDependency) project.getDependencies()
                .create(LWJGL_GROUP + ":" + moduleAndVersion + ":" + classifier);
        dependency.setTransitive(false);
        return dependency;
    }

    private boolean isMissingFile(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("Could not find")) return true;
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void copyAttribute(Configuration from, Configuration to, Attribute key) {
        to.getAttributes().attribute(key, from.getAttributes().getAttribute(key));
    }

    private static <T> Supplier<T> memoize(Supplier<T> supplier) {
        return new Supplier<>() {
            private T value;

            @Override
            public synchronized T get() {
                if (value == null) value = supplier.get();
                return value;
            }
        };
    }
}
