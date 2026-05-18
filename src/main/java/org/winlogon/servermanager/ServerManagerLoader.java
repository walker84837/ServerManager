package org.winlogon.servermanager;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;

@SuppressWarnings("UnstableApiUsage")
public class ServerManagerLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        var resolver = new MavenLibraryResolver();

        var repositories = new RemoteRepository[] {
            repo("central", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR),
            repo("jitpack", "https://jitpack.io"),
            repo("winlogon", "https://maven.winlogon.org/releases")
        };

        for (var repository : repositories) {
            resolver.addRepository(repository);
        }

        var dependencies = new Dependency[] {
            dependency("com.github.walker84837:JResult:1.4.0"),
            dependency("de.exlll:configlib-paper:4.6.3"),
            dependency("com.github.oshi:oshi-core:6.4.0"),
            dependency("org.quartz-scheduler:quartz:2.3.2"),
            dependency("org.winlogon:asynccraftr:0.2.0"),
            dependency("net.thisptr:jackson-jq:1.6.1")
        };

        for (var dependency : dependencies) {
            resolver.addDependency(dependency);
        }

        classpathBuilder.addLibrary(resolver);
    }

    private Dependency dependency(String s) {
        return new Dependency(new DefaultArtifact(s), null, null);
    }

    private RemoteRepository repo(String name, String url) {
        return new RemoteRepository.Builder(name, "default", url).build();
    }
}
