package io.github.ocularminds.blazra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import io.github.ocularminds.blazra.model.RegistryRepository;
import io.github.ocularminds.blazra.registry.RegistryClient;

class RegistryImageResolverTest {
    @Test
    void selectsTheHighestNewerNumericTagFromTheParsedRegistry() throws Exception {
        AtomicReference<RegistryRepository> requestedRepository = new AtomicReference<>();
        RegistryClient registry = repository -> {
            requestedRepository.set(repository);
            return List.of("latest", "1.9", "1.10", "2.0.1", "2.0.0-rc1");
        };
        RegistryImageResolver resolver = new RegistryImageResolver(registry, UpdatePolicy.MAJOR);

        Optional<String> latest = resolver.latestImage("ghcr.io/team/api:1.8");

        assertEquals(Optional.of("ghcr.io/team/api:2.0.1"), latest);
        assertEquals(new RegistryRepository("ghcr.io", "team/api"), requestedRepository.get());
    }

    @Test
    void preservesCurrentImageWhenNoCompatibleUpgradeExists() throws Exception {
        RegistryClient registry = repository -> List.of("latest", "1.0", "1.2-rc1");
        RegistryImageResolver resolver = new RegistryImageResolver(registry, UpdatePolicy.MAJOR);

        assertTrue(resolver.latestImage("team/api:1.2").isEmpty());
        assertTrue(resolver.latestImage("team/api:latest").isEmpty());
        assertTrue(resolver.latestImage("team/api@sha256:1234").isEmpty());
    }

    @Test
    void limitsUpdatesToTheConfiguredVersionScope() throws Exception {
        RegistryClient registry = repository -> List.of(
                "1.2.4", "1.3.0", "2.0.0", "v9.9.9");

        assertEquals(
                Optional.of("team/api:1.2.4"),
                new RegistryImageResolver(registry, UpdatePolicy.PATCH)
                        .latestImage("team/api:1.2.3"));
        assertEquals(
                Optional.of("team/api:1.3.0"),
                new RegistryImageResolver(registry, UpdatePolicy.MINOR)
                        .latestImage("team/api:1.2.3"));
        assertEquals(
                Optional.of("team/api:2.0.0"),
                new RegistryImageResolver(registry, UpdatePolicy.MAJOR)
                        .latestImage("team/api:1.2.3"));
    }

    @Test
    void preservesTheVersionPrefixConvention() throws Exception {
        RegistryClient registry = repository -> List.of("1.2.4", "v1.2.5");
        RegistryImageResolver resolver = new RegistryImageResolver(
                registry,
                UpdatePolicy.PATCH);

        assertEquals(Optional.of("team/api:v1.2.5"), resolver.latestImage("team/api:v1.2.3"));
        assertEquals(Optional.of("team/api:1.2.4"), resolver.latestImage("team/api:1.2.3"));
    }

    @Test
    void rejectsMissingDependencies() {
        RegistryClient registry = repository -> List.of();

        assertThrows(
                NullPointerException.class,
                () -> new RegistryImageResolver(null, UpdatePolicy.PATCH));
        assertThrows(
                NullPointerException.class,
                () -> new RegistryImageResolver(registry, null));
    }
}
