package io.github.ocularminds.blazra.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import io.github.ocularminds.blazra.model.RegistryRepository;

class RegistryClientRouterTest {
    @Test
    void isolatesDockerHubFromOtherOciRegistries() throws Exception {
        AtomicInteger dockerHubCalls = new AtomicInteger();
        AtomicInteger ociCalls = new AtomicInteger();
        RegistryClient dockerHub = repository -> {
            dockerHubCalls.incrementAndGet();
            return List.of("docker");
        };
        RegistryClient oci = repository -> {
            ociCalls.incrementAndGet();
            return List.of("oci");
        };
        RegistryClientRouter router = new RegistryClientRouter(dockerHub, oci);

        assertEquals(
                List.of("docker"),
                router.listTags(RegistryRepository.dockerHub("library/nginx")));
        assertEquals(
                List.of("oci"),
                router.listTags(new RegistryRepository("ghcr.io", "team/app")));
        assertEquals(1, dockerHubCalls.get());
        assertEquals(1, ociCalls.get());
    }

    @Test
    void rejectsMissingDependenciesAndRepositories() {
        RegistryClient client = repository -> List.of();
        assertThrows(NullPointerException.class, () -> new RegistryClientRouter(null, client));
        assertThrows(NullPointerException.class, () -> new RegistryClientRouter(client, null));
        RegistryClientRouter router = new RegistryClientRouter(client, client);
        assertThrows(NullPointerException.class, () -> router.listTags(null));
    }
}
