package io.github.ocularminds.blazra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImageReferenceTest {
    @Test
    void parsesDockerHubReferencesWithoutBreakingRegistryPorts() {
        assertReference(
                "nginx:1.25",
                "nginx",
                RegistryRepository.DOCKER_HUB_HOST,
                "library/nginx",
                "1.25");
        assertReference(
                "example/api:v1.2.3",
                "example/api",
                RegistryRepository.DOCKER_HUB_HOST,
                "example/api",
                "v1.2.3");
        assertReference(
                "docker.io/library/alpine:3.20",
                "docker.io/library/alpine",
                RegistryRepository.DOCKER_HUB_HOST,
                "library/alpine",
                "3.20");
        assertReference(
                "registry-1.docker.io/team/app:01.04",
                "registry-1.docker.io/team/app",
                RegistryRepository.DOCKER_HUB_HOST,
                "team/app",
                "01.04");
    }

    @Test
    void parsesPublicOciRegistryReferencesAndNestedPaths() {
        assertReference(
                "ghcr.io/team/app:1.2",
                "ghcr.io/team/app",
                "ghcr.io",
                "team/app",
                "1.2");
        assertReference(
                "europe-west1-docker.pkg.dev/project/repository/app:v2.0",
                "europe-west1-docker.pkg.dev/project/repository/app",
                "europe-west1-docker.pkg.dev",
                "project/repository/app",
                "v2.0");
        assertReference(
                "public.ecr.aws/alias/team/app:3.4.5",
                "public.ecr.aws/alias/team/app",
                "public.ecr.aws",
                "alias/team/app",
                "3.4.5");
        assertReference(
                "registry.example:5000/team/app:1.2",
                "registry.example:5000/team/app",
                "registry.example:5000",
                "team/app",
                "1.2");
        assertReference(
                "ghcr.io/team/my--app:1.2",
                "ghcr.io/team/my--app",
                "ghcr.io",
                "team/my--app",
                "1.2");
    }

    @Test
    void rejectsImmutableUnsupportedAndMalformedReferences() {
        for (String candidate : new String[]{
                null,
                "",
                "nginx",
                "nginx:",
                "nginx@sha256:1234",
                "docker.io/team/nested/app:1.2",
                "Docker.IO/team/app:1.2",
                "registry.example:0/team/app:1.2",
                "registry.example:65536/team/app:1.2",
                "registry_example/team/app:1.2",
                "localhost:5000/team/app:1.2",
                "127.0.0.1:5000/team/app:1.2",
                "registry.internal/team/app:1.2",
                "ghcr.io/team//app:1.2",
                "team/Bad_Name:1.2",
                "team/app:bad tag",
                "a".repeat(513)
        }) {
            assertTrue(ImageReference.parse(candidate).isEmpty(), () -> "accepted " + candidate);
        }
    }

    @Test
    void replacesOnlyTheTag() {
        ImageReference reference = ImageReference.parse("docker.io/team/app:1.2").orElseThrow();
        assertEquals("docker.io/team/app:2.0", reference.withTag("2.0"));
        assertThrows(IllegalArgumentException.class, () -> reference.withTag("bad tag"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ImageReference("Bad", reference.registryRepository(), "1.2"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ImageReference("team/app", null, "1.2"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ImageReference("team/app", reference.registryRepository(), "bad tag"));
    }

    private static void assertReference(
            String source,
            String sourceRepository,
            String registryHost,
            String registryPath,
            String tag) {
        Optional<ImageReference> parsed = ImageReference.parse(source);
        assertTrue(parsed.isPresent());
        assertEquals(sourceRepository, parsed.get().sourceRepository());
        assertEquals(registryHost, parsed.get().registryRepository().host());
        assertEquals(registryPath, parsed.get().registryRepository().path());
        assertEquals(tag, parsed.get().tag());
    }
}
