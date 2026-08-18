package io.github.ocularminds.blazra.repository.kubernetes;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.github.ocularminds.blazra.model.DeploymentSnapshot;
import io.github.ocularminds.blazra.model.DeploymentTarget;
import io.github.ocularminds.blazra.repository.DeploymentRepository;
import io.github.ocularminds.blazra.repository.DeploymentRepositoryException;

public final class Fabric8DeploymentRepository implements DeploymentRepository {
    private final KubernetesClient client;

    public Fabric8DeploymentRepository(KubernetesClient client) {
        this.client = Objects.requireNonNull(client, "Kubernetes client is required");
    }

    @Override
    public Optional<DeploymentSnapshot> find(DeploymentTarget target)
            throws DeploymentRepositoryException {
        Objects.requireNonNull(target, "target is required");
        try {
            Deployment deployment = resource(target).get();
            if (deployment == null) {
                return Optional.empty();
            }
            Container container = findContainer(deployment, target.container())
                    .orElseThrow(() -> new DeploymentRepositoryException(
                            "container " + target.container() + " was not found"));
            return Optional.of(new DeploymentSnapshot(
                    target,
                    container.getImage(),
                    deployment.getMetadata().getResourceVersion()));
        } catch (KubernetesClientException | IllegalStateException exception) {
            throw new DeploymentRepositoryException("could not read the target deployment", exception);
        }
    }

    @Override
    public void updateImage(DeploymentSnapshot expected, String newImage)
            throws DeploymentRepositoryException {
        Objects.requireNonNull(expected, "expected deployment is required");
        if (newImage == null || newImage.isBlank()) {
            throw new IllegalArgumentException("new image is required");
        }
        try {
            Resource<Deployment> resource = resource(expected.target());
            Deployment current = resource.get();
            if (current == null) {
                throw new DeploymentRepositoryException("target deployment no longer exists");
            }
            String resourceVersion = current.getMetadata().getResourceVersion();
            if (!expected.resourceVersion().equals(resourceVersion)) {
                throw new DeploymentRepositoryException("target deployment changed during the check");
            }
            Deployment updated = new DeploymentBuilder(current).build();
            Container container = findContainer(updated, expected.target().container())
                    .orElseThrow(() -> new DeploymentRepositoryException(
                            "target container no longer exists"));
            if (!expected.image().equals(container.getImage())) {
                throw new DeploymentRepositoryException("target image changed during the check");
            }
            container.setImage(newImage);
            client.resource(updated)
                    .lockResourceVersion(expected.resourceVersion())
                    .update();
        } catch (KubernetesClientException | IllegalStateException exception) {
            throw new DeploymentRepositoryException("could not update the target deployment", exception);
        }
    }

    private Resource<Deployment> resource(DeploymentTarget target) {
        return client.apps()
                .deployments()
                .inNamespace(target.namespace())
                .withName(target.deployment());
    }

    private static Optional<Container> findContainer(Deployment deployment, String name) {
        if (deployment.getSpec() == null
                || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getSpec() == null) {
            return Optional.empty();
        }
        List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();
        if (containers == null) {
            return Optional.empty();
        }
        return containers.stream().filter(container -> name.equals(container.getName())).findFirst();
    }
}
