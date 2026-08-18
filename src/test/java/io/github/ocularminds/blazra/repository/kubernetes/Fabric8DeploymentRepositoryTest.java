package io.github.ocularminds.blazra.repository.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.ocularminds.blazra.model.DeploymentSnapshot;
import io.github.ocularminds.blazra.model.DeploymentTarget;
import io.github.ocularminds.blazra.repository.DeploymentRepositoryException;

@EnableKubernetesMockClient(crud = true)
class Fabric8DeploymentRepositoryTest {
    KubernetesClient client;

    private DeploymentTarget target;
    private Fabric8DeploymentRepository repository;

    @BeforeEach
    void setUp() {
        target = new DeploymentTarget("default", "api", "web");
        repository = new Fabric8DeploymentRepository(client);
    }

    @Test
    void findsAndUpdatesTheNamedContainer() throws Exception {
        createDeployment("10", "team/api:1.0");

        DeploymentSnapshot snapshot = repository.find(target).orElseThrow();
        assertEquals("team/api:1.0", snapshot.image());

        repository.updateImage(snapshot, "team/api:2.0");

        Deployment updated = client.apps().deployments()
                .inNamespace("default")
                .withName("api")
                .get();
        assertEquals(
                "team/api:2.0",
                updated.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    }

    @Test
    void returnsEmptyForMissingDeployment() throws Exception {
        assertEquals(Optional.empty(), repository.find(target));
    }

    @Test
    void rejectsMissingContainersAndConcurrentChanges() throws Exception {
        createDeployment("10", "team/api:1.0");
        DeploymentTarget missingContainer = new DeploymentTarget("default", "api", "worker");
        assertThrows(DeploymentRepositoryException.class, () -> repository.find(missingContainer));

        DeploymentSnapshot current = repository.find(target).orElseThrow();
        DeploymentSnapshot stale = new DeploymentSnapshot(target, "team/api:1.0", "stale-version");
        assertThrows(
                DeploymentRepositoryException.class,
                () -> repository.updateImage(stale, "team/api:2.0"));

        DeploymentSnapshot staleImage = new DeploymentSnapshot(
                target,
                "team/api:0.9",
                current.resourceVersion());
        assertThrows(
                DeploymentRepositoryException.class,
                () -> repository.updateImage(staleImage, "team/api:2.0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.updateImage(stale, ""));

        client.apps().deployments().inNamespace("default").withName("api").delete();
        assertThrows(
                DeploymentRepositoryException.class,
                () -> repository.updateImage(current, "team/api:2.0"));
    }

    @Test
    void handlesDeploymentsWithoutPodSpecifications() throws Exception {
        Deployment incomplete = new DeploymentBuilder()
                .withNewMetadata()
                .withNamespace("default")
                .withName("api")
                .withResourceVersion("10")
                .endMetadata()
                .build();
        client.apps().deployments().inNamespace("default").resource(incomplete).create();

        DeploymentRepositoryException exception = assertThrows(
                DeploymentRepositoryException.class,
                () -> repository.find(target));
        assertTrue(exception.getMessage().contains("container"));
    }

    @Test
    void wrapsKubernetesClientFailures() {
        client.close();
        assertThrows(DeploymentRepositoryException.class, () -> repository.find(target));
    }

    private void createDeployment(String resourceVersion, String image) {
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withNamespace("default")
                .withName("api")
                .withResourceVersion(resourceVersion)
                .endMetadata()
                .withSpec(new DeploymentSpecBuilder()
                        .withTemplate(new PodTemplateSpecBuilder()
                                .withSpec(new PodSpecBuilder()
                                        .withContainers(new ContainerBuilder()
                                                .withName("web")
                                                .withImage(image)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();
        client.apps().deployments().inNamespace("default").resource(deployment).create();
    }
}
