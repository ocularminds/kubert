package io.github.ocularminds.blazra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegistryRepositoryTest {
    @Test
    void validatesDockerHubAndOciRepositories() {
        RegistryRepository dockerHub = RegistryRepository.dockerHub("library/nginx");
        RegistryRepository oci = new RegistryRepository(
                "registry.example.com:5443",
                "division/team/app");

        assertEquals(RegistryRepository.DOCKER_HUB_HOST, dockerHub.host());
        assertEquals("library/nginx", dockerHub.path());
        assertTrue(dockerHub.isDockerHub());
        assertFalse(oci.isDockerHub());
        assertEquals(
                "team/my--app",
                new RegistryRepository("registry.example", "team/my--app").path());
    }

    @Test
    void rejectsInvalidHosts() {
        assertThrows(NullPointerException.class, () -> new RegistryRepository(null, "team/app"));
        for (String host : new String[]{
                "",
                "Registry.example",
                "registry_example",
                "-registry.example",
                "registry-.example",
                "registry..example",
                "registry.example:",
                "registry.example:0",
                "registry.example:65536",
                "registry.example:abc",
                "registry.example:+1",
                "registry:5000",
                "localhost:5000",
                "127.0.0.1:5000",
                "127.1:5000",
                "registry.cluster.local",
                "registry.service.internal",
                "[::1]:5000",
                "a".repeat(260)
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RegistryRepository(host, "team/app"),
                    () -> "accepted host " + host);
        }
    }

    @Test
    void rejectsInvalidPathsAndDockerHubNesting() {
        assertThrows(NullPointerException.class, () -> new RegistryRepository("ghcr.io", null));
        for (String path : new String[]{
                "",
                "/team/app",
                "team/app/",
                "team//app",
                "team/Bad",
                "team/bad_name-",
                "a".repeat(256)
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RegistryRepository("ghcr.io", path),
                    () -> "accepted path " + path);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> RegistryRepository.dockerHub("nginx"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RegistryRepository.dockerHub("team/nested/app"));
    }
}
