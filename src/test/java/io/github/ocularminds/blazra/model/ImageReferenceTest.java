package io.github.ocularminds.blazra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImageReferenceTest {
    @Test
    void parsesDockerHubReferencesWithoutBreakingRegistryPorts() {
        assertReference("nginx:1.25", "nginx", "library/nginx", "1.25");
        assertReference("example/api:v1.2.3", "example/api", "example/api", "v1.2.3");
        assertReference("docker.io/library/alpine:3.20", "docker.io/library/alpine", "library/alpine", "3.20");
        assertReference("registry-1.docker.io/team/app:01.04", "registry-1.docker.io/team/app", "team/app", "01.04");
    }

    @Test
    void rejectsImmutableUnsupportedAndMalformedReferences() {
        for (String candidate : new String[]{
                null,
                "",
                "nginx",
                "nginx:",
                "nginx@sha256:1234",
                "ghcr.io/team/app:1.2",
                "localhost:5000/team/app:1.2",
                "docker.io/team/nested/app:1.2",
                "Docker.IO/team/app:1.2",
                "team/Bad_Name:1.2",
                "team/app:bad tag"
        }) {
            assertTrue(ImageReference.parse(candidate).isEmpty(), () -> "accepted " + candidate);
        }
    }

    @Test
    void replacesOnlyTheTag() {
        ImageReference reference = ImageReference.parse("docker.io/team/app:1.2").orElseThrow();
        assertEquals("docker.io/team/app:2.0", reference.withTag("2.0"));
        assertThrows(IllegalArgumentException.class, () -> reference.withTag("bad tag"));
    }

    private static void assertReference(
            String source,
            String sourceRepository,
            String dockerHubRepository,
            String tag) {
        Optional<ImageReference> parsed = ImageReference.parse(source);
        assertTrue(parsed.isPresent());
        assertEquals(sourceRepository, parsed.get().sourceRepository());
        assertEquals(dockerHubRepository, parsed.get().dockerHubRepository());
        assertEquals(tag, parsed.get().tag());
    }
}
