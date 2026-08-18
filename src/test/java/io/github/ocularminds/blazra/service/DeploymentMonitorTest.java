package io.github.ocularminds.blazra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.ocularminds.blazra.model.DeploymentSnapshot;
import io.github.ocularminds.blazra.model.DeploymentTarget;
import io.github.ocularminds.blazra.registry.RegistryException;
import io.github.ocularminds.blazra.repository.DeploymentRepository;
import io.github.ocularminds.blazra.repository.DeploymentRepositoryException;

class DeploymentMonitorTest {
    private DeploymentTarget target;
    private FakeDeploymentRepository repository;

    @BeforeEach
    void setUp() {
        target = new DeploymentTarget("default", "api", "web");
        repository = new FakeDeploymentRepository();
    }

    @Test
    void reportsMissingAndCurrentDeployments() throws Exception {
        DeploymentMonitor missing = new DeploymentMonitor(
                repository,
                current -> Optional.of("team/api:2.0"),
                target,
                false);
        assertEquals(UpdateResult.DEPLOYMENT_NOT_FOUND, missing.check());

        repository.snapshot = snapshot("team/api:1.0");
        DeploymentMonitor current = new DeploymentMonitor(
                repository,
                image -> Optional.empty(),
                target,
                false);
        assertEquals(UpdateResult.NO_UPDATE, current.check());
    }

    @Test
    void supportsDryRunsWithoutMutatingKubernetes() throws Exception {
        repository.snapshot = snapshot("team/api:1.0");
        DeploymentMonitor monitor = new DeploymentMonitor(
                repository,
                image -> Optional.of("team/api:2.0"),
                target,
                true);

        assertEquals(UpdateResult.DRY_RUN, monitor.check());
        assertNull(repository.updatedImage);
    }

    @Test
    void updatesOnlyAfterResolvingANewerImage() throws Exception {
        repository.snapshot = snapshot("team/api:1.0");
        DeploymentMonitor monitor = new DeploymentMonitor(
                repository,
                image -> Optional.of("team/api:2.0"),
                target,
                false);

        assertEquals(UpdateResult.UPDATED, monitor.check());
        assertEquals("team/api:2.0", repository.updatedImage);
    }

    @Test
    void wrapsRepositoryAndRegistryFailures() {
        repository.failure = new DeploymentRepositoryException("forbidden");
        DeploymentMonitor repositoryFailure = new DeploymentMonitor(
                repository,
                image -> Optional.empty(),
                target,
                false);
        assertThrows(MonitoringException.class, repositoryFailure::check);

        repository.failure = null;
        repository.snapshot = snapshot("team/api:1.0");
        DeploymentMonitor registryFailure = new DeploymentMonitor(
                repository,
                image -> {
                    throw new RegistryException("unavailable");
                },
                target,
                false);
        assertThrows(MonitoringException.class, registryFailure::check);
    }

    private DeploymentSnapshot snapshot(String image) {
        return new DeploymentSnapshot(target, image, "10");
    }

    private static final class FakeDeploymentRepository implements DeploymentRepository {
        private DeploymentSnapshot snapshot;
        private DeploymentRepositoryException failure;
        private String updatedImage;

        @Override
        public Optional<DeploymentSnapshot> find(DeploymentTarget ignored)
                throws DeploymentRepositoryException {
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(snapshot);
        }

        @Override
        public void updateImage(DeploymentSnapshot ignored, String newImage)
                throws DeploymentRepositoryException {
            if (failure != null) {
                throw failure;
            }
            updatedImage = newImage;
        }
    }
}
