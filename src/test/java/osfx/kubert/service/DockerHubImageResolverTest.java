package osfx.kubert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import osfx.kubert.registry.RegistryClient;

class DockerHubImageResolverTest {
    @Test
    void selectsTheHighestNewerNumericTag() throws Exception {
        AtomicReference<String> requestedRepository = new AtomicReference<>();
        RegistryClient registry = repository -> {
            requestedRepository.set(repository);
            return List.of("latest", "1.9", "1.10", "2.0.1", "2.0.0-rc1");
        };
        DockerHubImageResolver resolver = new DockerHubImageResolver(registry, UpdatePolicy.MAJOR);

        Optional<String> latest = resolver.latestImage("team/api:1.8");

        assertEquals(Optional.of("team/api:2.0.1"), latest);
        assertEquals("team/api", requestedRepository.get());
    }

    @Test
    void preservesCurrentImageWhenNoCompatibleUpgradeExists() throws Exception {
        RegistryClient registry = repository -> List.of("latest", "1.0", "1.2-rc1");
        DockerHubImageResolver resolver = new DockerHubImageResolver(registry, UpdatePolicy.MAJOR);

        assertTrue(resolver.latestImage("team/api:1.2").isEmpty());
        assertTrue(resolver.latestImage("team/api:latest").isEmpty());
        assertTrue(resolver.latestImage("ghcr.io/team/api:1.2").isEmpty());
    }

    @Test
    void limitsUpdatesToTheConfiguredVersionScope() throws Exception {
        RegistryClient registry = repository -> List.of(
                "1.2.4", "1.3.0", "2.0.0", "v9.9.9");

        assertEquals(
                Optional.of("team/api:1.2.4"),
                new DockerHubImageResolver(registry, UpdatePolicy.PATCH)
                        .latestImage("team/api:1.2.3"));
        assertEquals(
                Optional.of("team/api:1.3.0"),
                new DockerHubImageResolver(registry, UpdatePolicy.MINOR)
                        .latestImage("team/api:1.2.3"));
        assertEquals(
                Optional.of("team/api:2.0.0"),
                new DockerHubImageResolver(registry, UpdatePolicy.MAJOR)
                        .latestImage("team/api:1.2.3"));
    }

    @Test
    void preservesTheVersionPrefixConvention() throws Exception {
        RegistryClient registry = repository -> List.of("1.2.4", "v1.2.5");
        DockerHubImageResolver resolver = new DockerHubImageResolver(
                registry,
                UpdatePolicy.PATCH);

        assertEquals(Optional.of("team/api:v1.2.5"), resolver.latestImage("team/api:v1.2.3"));
        assertEquals(Optional.of("team/api:1.2.4"), resolver.latestImage("team/api:1.2.3"));
    }
}
